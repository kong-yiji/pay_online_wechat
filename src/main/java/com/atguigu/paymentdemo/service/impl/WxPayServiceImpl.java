package com.atguigu.paymentdemo.service.impl;

import com.atguigu.paymentdemo.config.WxPayConfig;
import com.atguigu.paymentdemo.entity.OrderInfo;
import com.atguigu.paymentdemo.enums.OrderStatus;
import com.atguigu.paymentdemo.enums.wxpay.WxApiType;
import com.atguigu.paymentdemo.enums.wxpay.WxNotifyType;
import com.atguigu.paymentdemo.service.OrderInfoService;
import com.atguigu.paymentdemo.service.WxPayService;
import com.atguigu.paymentdemo.util.OrderNoUtils;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

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
    @Override
    public Map<String, Object> nativePay(long productId) throws IOException {
        log.info("生成订单");
        OrderInfo orderInfo=   orderInfoService.createOrderByProductId(productId);
        String codeUrl=orderInfo.getCodeUrl();
        if(!(StringUtils.isEmpty(codeUrl))){
            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
            return map;
        }
        log.info("调用api生成二维码");
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.NATIVE_PAY.getType()));
        // 请求body参数
       Gson gson=new Gson();
       Map amoutMap=new HashMap();
        amoutMap.put("total",orderInfo.getTotalFee());
       Map paramsMap=new HashMap();
         paramsMap.put("appid",wxPayConfig.getAppid());
         paramsMap.put("mchid",wxPayConfig.getMchId());
         paramsMap.put("description","yin do it");
         paramsMap.put("out_trade_no", UUID.randomUUID().toString().replace("-",""));
         paramsMap.put("notify_url",wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));
          paramsMap.put("amount",amoutMap);
          String jsonParams=gson.toJson(paramsMap);
        System.out.println(paramsMap)   ;
        StringEntity entity = new StringEntity(jsonParams,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = httpClient. execute(httpPost);

        try {
            int statusCode = response.getStatusLine().getStatusCode();
            String bodyAsString=EntityUtils.toString(response.getEntity());
            if (statusCode == 200) { //处理成功
                log.info("success,return body = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                System.out.println("success");
            } else {
                System.out.println("failed,resp code = " + statusCode+ ",return body = " + EntityUtils.toString(response.getEntity()));
                throw new IOException("request failed");
            }
            Map<String,String>resultMap=gson.fromJson(bodyAsString,HashMap.class);
             codeUrl=resultMap.get("code_url");
            Map<String,Object>map=new HashMap<>();
            map.put("codeUrl",codeUrl);
            map.put("orderNo",orderInfo.getOrderNo());
            return  map;
        } finally {
            response.close();
        }
    }
}
