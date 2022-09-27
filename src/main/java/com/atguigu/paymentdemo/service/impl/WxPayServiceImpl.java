package com.atguigu.paymentdemo.service.impl;

import com.atguigu.paymentdemo.config.WxPayConfig;
import com.atguigu.paymentdemo.entity.OrderInfo;
import com.atguigu.paymentdemo.entity.RefundInfo;
import com.atguigu.paymentdemo.enums.OrderStatus;
import com.atguigu.paymentdemo.enums.wxpay.WxApiType;
import com.atguigu.paymentdemo.enums.wxpay.WxNotifyType;
import com.atguigu.paymentdemo.enums.wxpay.WxRefundStatus;
import com.atguigu.paymentdemo.enums.wxpay.WxTradeState;
import com.atguigu.paymentdemo.service.OrderInfoService;
import com.atguigu.paymentdemo.service.PaymentInfoService;
import com.atguigu.paymentdemo.service.RefundInfoService;
import com.atguigu.paymentdemo.service.WxPayService;
import com.atguigu.paymentdemo.util.OrderNoUtils;
import com.google.gson.Gson;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author : yin
 * @return codeurl orderInfo
 * @Date: 2022/9/25
 */
@Slf4j
@Service
public class WxPayServiceImpl implements WxPayService {
    @Resource
    WxPayConfig wxPayConfig;
    @Resource
   private CloseableHttpClient wxPayClient;
    @Autowired
    OrderInfoService orderInfoService;
    @Autowired
    PaymentInfoService paymentInfoService;
    @Autowired
    RefundInfoService refundInfoService;
    @Resource
    private CloseableHttpClient wxPayNoSignClient; //无需应答签名
   private final ReentrantLock lock=new ReentrantLock();
    @Override
    public Map<String, Object> nativePay(long productId) throws IOException {
        log.info("生成订单");
        OrderInfo orderInfo = orderInfoService.createOrderByProductId(productId);
        String codeUrl = orderInfo.getCodeUrl();
        if (!(StringUtils.isEmpty(codeUrl))) {
            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
            return map;
        }
        log.info("调用api生成二维码");
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.NATIVE_PAY.getType()));
        // 请求body参数
        Gson gson = new Gson();
        Map amoutMap = new HashMap();
        amoutMap.put("total", orderInfo.getTotalFee());
        Map paramsMap = new HashMap();
        paramsMap.put("appid", wxPayConfig.getAppid());
        paramsMap.put("mchid", wxPayConfig.getMchId());
        paramsMap.put("description", "yin do it");
        paramsMap.put("out_trade_no", orderInfo.getOrderNo());
        paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));
        paramsMap.put("amount", amoutMap);
        String jsonParams = gson.toJson(paramsMap);
        System.out.println(paramsMap);
        StringEntity entity = new StringEntity(jsonParams, "utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpPost);

        try {
            int statusCode = response.getStatusLine().getStatusCode();
            //返回数据
            String bodyAsString = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) { //处理成功
                log.info("success,return body = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                System.out.println("success");
            } else {
                System.out.println("failed,resp code = " + statusCode + ",return body = " + EntityUtils.toString(response.getEntity()));
                throw new IOException("request failed");
            }
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            codeUrl = resultMap.get("code_url");
            orderInfoService.saveCodeUrl(orderInfo.getOrderNo(),codeUrl);
            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
            return map;
        } finally {
            response.close();
        }
    }

    @Override
    public void processOrder(Map<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("处理订单");
        String plainText = decryptFromResource(bodyMap);
        //将明文转换成map
        Gson gson = new Gson();
        HashMap plainTextMap = gson.fromJson(plainText, HashMap.class);
        String orderNo = (String)plainTextMap.get("out_trade_no");
        System.out.println("---------------------------------------"+orderNo );

        /*在对业务数据进行状态检查和处理之前，
        要采用数据锁进行并发控制，
        以避免函数重入造成的数据混乱*/
        //尝试获取锁：
        // 成功获取则立即返回true，获取失败则立即返回false。不必一直等待锁的释放
        if(lock.tryLock()){
            try {
                //处理重复的通知
                //接口调用的幂等性：无论接口被调用多少次，产生的结果是一致的。
                String orderStatus = orderInfoService.getOrderStatus(orderNo);
                if(!OrderStatus.NOTPAY.getType().equals(orderStatus)){
                    return;
                }

                //更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.SUCCESS);

                //记录支付日志
                paymentInfoService.createPaymentInfo(plainText);
            } finally {
                //要主动释放锁
                lock.unlock();
            }
        }
    }

    @Override
    public void cancelOrder(String orderNo) throws IOException {
    this.closeOrder(orderNo);
    }

    @Override
    public String queryOrder(String orderNo) throws IOException {
        log.info("查询订单--------------"+orderNo);
        String url=String.format(WxApiType.ORDER_QUERY_BY_NO.getType(),orderNo);
         url=wxPayConfig.getDomain().concat(url).concat("?mchid="+wxPayConfig.getMchId());
        HttpGet httpGet=new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        CloseableHttpResponse response = wxPayClient.execute(httpGet);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            //返回数据
            String bodyAsString = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) { //处理成功
                log.info("success,return body = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                System.out.println("success");
            } else {
                System.out.println("failed,resp code = " + statusCode + ",return body = " + EntityUtils.toString(response.getEntity()));
                throw new IOException("request failed");
            }

            return bodyAsString;
        } finally {
            response.close();
        }
    }
/***
 * @author yin
 *  2022/9/27  10:04
 *  查询订单，如果支付，更新商户端，未支付调用关单
 * @author yin
 * @parans [orderNo]
 * @return void
 */
    @Override
    public void checkOrderStatus(String orderNo) throws IOException {
      String result=queryOrder(orderNo);
      Gson gson=new Gson();
      Map resultMap=gson.fromJson(result,HashMap.class);
      Object tradeState=resultMap.get("trad_state");
     if(WxTradeState.SUCCESS.getType().equals(tradeState)){
           log.info("订单已经支付修改,记录支付日志");
           orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.SUCCESS);
           paymentInfoService.createPaymentInfo(result);
     }
     else {
         log.warn("订单未支付,调用关单接口");
          this.closeOrder(orderNo);
          orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.CLOSED);
     }
    }
/***
 * @author yin
 * 退款
 *  2022/9/27  10:53
 * @author yin
 * @parans [orderNo, reason]
 * @return void
 */
    @Override
    public void refund(String orderNo, String reason) throws IOException {
            log.info("创建退款单记录");
            //根据订单编号创建退款单
            RefundInfo refundsInfo = refundInfoService.createRefundByOrderNo(orderNo, reason);

            log.info("调用退款API");

            //调用统一下单API
            String url = wxPayConfig.getDomain().concat(WxApiType.DOMESTIC_REFUNDS.getType());
            HttpPost httpPost = new HttpPost(url);

            // 请求body参数
            Gson gson = new Gson();
            Map paramsMap = new HashMap();
            paramsMap.put("out_trade_no", orderNo);//订单编号
            paramsMap.put("out_refund_no", refundsInfo.getRefundNo());//退款单编号
            paramsMap.put("reason",reason);//退款原因
            paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.REFUND_NOTIFY.getType()));//退款通知地址

            Map amountMap = new HashMap();
            amountMap.put("refund", refundsInfo.getRefund());//退款金额
            amountMap.put("total", refundsInfo.getTotalFee());//原订单金额
            amountMap.put("currency", "CNY");//退款币种
            paramsMap.put("amount", amountMap);

            //将参数转换成json字符串
            String jsonParams = gson.toJson(paramsMap);
            log.info("请求参数 ===> {}" + jsonParams);

            StringEntity entity = new StringEntity(jsonParams,"utf-8");
            entity.setContentType("application/json");//设置请求报文格式
            httpPost.setEntity(entity);//将请求报文放入请求对象
            httpPost.setHeader("Accept", "application/json");//设置响应报文格式

            //完成签名并执行请求，并完成验签
            CloseableHttpResponse response = wxPayClient.execute(httpPost);

            try {

                //解析响应结果
                String bodyAsString = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    log.info("成功, 退款返回结果 = " + bodyAsString);
                } else if (statusCode == 204) {
                    log.info("成功");
                } else {
                    throw new RuntimeException("退款异常, 响应码 = " + statusCode+ ", 退款返回结果 = " + bodyAsString);
                }

                //更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_PROCESSING);

                //更新退款单
                refundInfoService.updateRefund(bodyAsString);

            } finally {
                response.close();
            }

    }


    /**
     * 查询退款接口调用
     * @param refundNo
     * @return
     */
    @Override
    public String queryRefund(String refundNo) throws Exception {

        log.info("查询退款接口调用 ===> {}", refundNo);

        String url =  String.format(WxApiType.DOMESTIC_REFUNDS_QUERY.getType(), refundNo);
        url = wxPayConfig.getDomain().concat(url);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpGet);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 查询退款返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("查询退款异常, 响应码 = " + statusCode+ ", 查询退款返回结果 = " + bodyAsString);
            }

            return bodyAsString;

        } finally {
            response.close();
        }
    }

    /**
     * 根据退款单号核实退款单状态
     * @param refundNo
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkRefundStatus(String refundNo) throws Exception {

        log.warn("根据退款单号核实退款单状态 ===> {}", refundNo);

        //调用查询退款单接口
        String result = this.queryRefund(refundNo);

        //组装json请求体字符串
        Gson gson = new Gson();
        Map<String, String> resultMap = gson.fromJson(result, HashMap.class);

        //获取微信支付端退款状态
        String status = resultMap.get("status");

        String orderNo = resultMap.get("out_trade_no");

        if (WxRefundStatus.SUCCESS.getType().equals(status)) {

            log.warn("核实订单已退款成功 ===> {}", refundNo);

            //如果确认退款成功，则更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);

            //更新退款单
            refundInfoService.updateRefund(result);
        }

        if (WxRefundStatus.ABNORMAL.getType().equals(status)) {

            log.warn("核实订单退款异常  ===> {}", refundNo);

            //如果确认退款成功，则更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_ABNORMAL);

            //更新退款单
            refundInfoService.updateRefund(result);
        }
    }

    /**
     * 处理退款单
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processRefund(Map<String, Object> bodyMap) throws Exception {

        log.info("退款单");

        //解密报文
        String plainText = decryptFromResource(bodyMap);

        //将明文转换成map
        Gson gson = new Gson();
        HashMap plainTextMap = gson.fromJson(plainText, HashMap.class);
        String orderNo = (String)plainTextMap.get("out_trade_no");

        if(lock.tryLock()){
            try {

                String orderStatus = orderInfoService.getOrderStatus(orderNo);
                if (!OrderStatus.REFUND_PROCESSING.getType().equals(orderStatus)) {
                    return;
                }

                //更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);

                //更新退款单
                refundInfoService.updateRefund(plainText);

            } finally {
                //要主动释放锁
                lock.unlock();
            }
        }
    }


    /**
     * 申请账单
     * @param billDate
     * @param type
     * @return
     * @throws Exception
     */
    @Override
    public String queryBill(String billDate, String type) throws Exception {
        log.warn("申请账单接口调用 {}", billDate);

        String url = "";
        if("tradebill".equals(type)){
            url =  WxApiType.TRADE_BILLS.getType();
        }else if("fundflowbill".equals(type)){
            url =  WxApiType.FUND_FLOW_BILLS.getType();
        }else{
            throw new RuntimeException("不支持的账单类型");
        }
        url = wxPayConfig.getDomain().concat(url).concat("?bill_date=").concat(billDate);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Accept", "application/json");

        //使用wxPayClient发送请求得到响应
        CloseableHttpResponse response = wxPayClient.execute(httpGet);

        try {

            String bodyAsString = EntityUtils.toString(response.getEntity());

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 申请账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("申请账单异常, 响应码 = " + statusCode+ ", 申请账单返回结果 = " + bodyAsString);
            }

            //获取账单下载地址
            Gson gson = new Gson();
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            return resultMap.get("download_url");

        } finally {
            response.close();
        }
    }

    /**
     * 下载账单
     * @param billDate
     * @param type
     * @return
     * @throws Exception
     */
    @Override
    public String downloadBill(String billDate, String type) throws Exception {
        log.warn("下载账单接口调用 {}, {}", billDate, type);

        //获取账单url地址
        String downloadUrl = this.queryBill(billDate, type);
        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(downloadUrl);
        httpGet.addHeader("Accept", "application/json");

        //使用wxPayClient发送请求得到响应
        CloseableHttpResponse response = wxPayNoSignClient.execute(httpGet);

        try {

            String bodyAsString = EntityUtils.toString(response.getEntity());

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 下载账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("下载账单异常, 响应码 = " + statusCode+ ", 下载账单返回结果 = " + bodyAsString);
            }

            return bodyAsString;

        } finally {
            response.close();
        }
    }






















    private void closeOrder(String orderNo) throws IOException {
        log.info("关闭订单"+orderNo);
        String url=String.format(WxApiType.CLOSE_ORDER_BY_NO.getType(),orderNo);
        url= wxPayConfig.getDomain().concat(url);
        HttpPost httpPost=new HttpPost(url);
        Gson gson=new Gson();
        Map<String,String>paramMap=new HashMap<>();
        paramMap.put("mchid",wxPayConfig.getMchId());
        String jsonParams=gson.toJson(paramMap);
        log.info("请求参数---------------"+jsonParams);
        StringEntity entity = new StringEntity(jsonParams, "utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpPost);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) { //处理成功
                log.info("success,200");
            } else if (statusCode == 204) { //处理成功，无返回Body
                System.out.println("success.204");
            } else {
                System.out.println("failed,resp code = " + statusCode );
                throw new IOException("request failed");
            }

        } finally {
            response.close();
        }
    }

    private String decryptFromResource(Map<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("密文解密");
        //通知数据
        Map<String, String> resourceMap = (Map) bodyMap.get("resource");
        //附加数据
        String associatedData = resourceMap.get("associated_data");
        //随机串
        String nonce = resourceMap.get("nonce");
        //数据密文
        String ciphertext = resourceMap.get("ciphertext");
        AesUtil aesUtil = new AesUtil(wxPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        String plainText = aesUtil.decryptToString(associatedData.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8), ciphertext);
        log.info("明文"+plainText);
        return plainText;
    }
}
