package com.atguigu.paymentdemo.service.impl;

import com.atguigu.paymentdemo.config.WxPayConfig;
import com.atguigu.paymentdemo.entity.OrderInfo;
import com.atguigu.paymentdemo.entity.PaymentInfo;
import com.atguigu.paymentdemo.enums.OrderStatus;
import com.atguigu.paymentdemo.enums.wxpay.WxApiType;
import com.atguigu.paymentdemo.enums.wxpay.WxNotifyType;
import com.atguigu.paymentdemo.service.OrderInfoService;
import com.atguigu.paymentdemo.service.PaymentInfoService;
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
    @Autowired
    CloseableHttpClient httpClient;
    @Autowired
    OrderInfoService orderInfoService;
    @Autowired
    PaymentInfoService paymentInfoService;
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
        CloseableHttpResponse response = httpClient.execute(httpPost);

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
        CloseableHttpResponse response = httpClient.execute(httpGet);
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
        CloseableHttpResponse response = httpClient.execute(httpPost);
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
