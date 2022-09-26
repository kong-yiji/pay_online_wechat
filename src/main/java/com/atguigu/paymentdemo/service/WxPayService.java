package com.atguigu.paymentdemo.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;


public interface WxPayService {

    Map<String, Object> nativePay(long productId) throws IOException;


    void processOrder(Map<String, Object> bodyMap) throws GeneralSecurityException;
}
