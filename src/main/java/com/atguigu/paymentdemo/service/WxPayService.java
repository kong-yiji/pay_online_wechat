package com.atguigu.paymentdemo.service;


import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;


public interface WxPayService {

    Map<String, Object> nativePay(long productId) throws IOException;


    void processOrder(Map<String, Object> bodyMap) throws GeneralSecurityException;

    void cancelOrder(String orderNo) throws IOException;

    String queryOrder(String orderNo) throws IOException;

    void checkOrderStatus(String orderNo) throws IOException;
    void refund(String orderNo,String reason) throws IOException;

    String queryRefund(String refundNo) throws Exception;
     void checkRefundStatus(String refundNo) throws Exception;
     void processRefund(Map<String, Object> bodyMap) throws Exception;
    String downloadBill(String billDate, String type) throws Exception;
    String queryBill(String billDate, String type) throws Exception;
}
