package com.atguigu.paymentdemo.config;

import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.exception.HttpCodeException;
import com.wechat.pay.contrib.apache.httpclient.exception.NotFoundException;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import lombok.Data;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;


/**
 * @author : yin
 * @Date: 2022/9/24
 */
@Configuration
@PropertySource("classpath:wxpay.properties")
@ConfigurationProperties(prefix = "wxpay")
@Data
public class WxPayConfig {
    //商户号
    private String mchId;
    //商户证书序列号
    private String mchSerialNo;
    //商户私钥文件
    private String privateKeyPath;
    //APIV3密钥
    private String apiV3Key;
    //appID
    private String appid;
    // 微信服务器地址
    private String domain;
    //接收结果通知地址
    private String notifyDomain;

    public PrivateKey getPrivateKey(String privateKeyPath) {
        try {
            return PemUtil.loadPrivateKey(new FileInputStream(privateKeyPath));
        } catch (Exception e) {
            throw new RuntimeException("私钥不存在", e);
        }
    }

    @Bean
    public Verifier getVerifier() throws NotFoundException, HttpCodeException, GeneralSecurityException, IOException {
        //商户私钥
        PrivateKey privateKey = getPrivateKey(getPrivateKeyPath());
        //私钥签名对象
        PrivateKeySigner privateKeySigner = new PrivateKeySigner(mchSerialNo, privateKey);
        //身份认证对象,确认返回是微信段而非黑客
        WechatPay2Credentials wechatPay2Credentials = new WechatPay2Credentials(mchId, privateKeySigner);
        // 获取证书管理器实例
        CertificatesManager certificatesManager = CertificatesManager.getInstance();

        // 向证书管理器增加需要自动更新平台证书的商户信息
        certificatesManager.putMerchant(mchId, wechatPay2Credentials, apiV3Key.getBytes(StandardCharsets.UTF_8));
        // ... 若有多个商户号，可继续调用putMerchant添加商户信息

        // 从证书管理器中获取verifier
        Verifier verifier = certificatesManager.getVerifier(mchId);
        return verifier;
    }

    @Bean
    public CloseableHttpClient getWxPayClient(Verifier verifier) throws NotFoundException, HttpCodeException, GeneralSecurityException, IOException {

        PrivateKey privateKey = getPrivateKey(privateKeyPath);
        WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                .withMerchant(mchId, mchSerialNo, privateKey)
                .withValidator(new WechatPay2Validator(verifier));

// 通过WechatPayHttpClientBuilder构造的HttpClient，会自动的处理签名和验签，并进行证书自动更新
        CloseableHttpClient httpClient = builder.build();
        return httpClient;

    }
}
