package com.atguigu.paymentdemo;

import com.atguigu.paymentdemo.config.WxPayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentDemoApplicationTests {
    @Autowired
   WxPayConfig wxPayConfig;

    @Test
    void getPrivateKey() {
        System.out.println("haha");
        String fileName=wxPayConfig.getPrivateKeyPath();
        System.out.println(wxPayConfig.getPrivateKey(fileName));
    }

}
