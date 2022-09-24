package com.atguigu.paymentdemo.controller;

import com.atguigu.paymentdemo.config.WxPayConfig;
import com.atguigu.paymentdemo.vo.R;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : yin
 * @Date: 2022/9/24
 */
@Api(tags = {"测试配置类"})
@RestController
@RequestMapping("/api/test")
public class TestController {
    @Autowired
    WxPayConfig wxPayConfig;
    @RequestMapping(value = "/testWx",method = RequestMethod.GET)
    public R getWxPay(){
     return R.ok().data("appid",wxPayConfig.getMchId());
    }

    @RequestMapping(value = "getPrivateKey",method = RequestMethod.GET)
    public R getPrivateKey(){
        System.out.println(wxPayConfig.getPrivateKey(wxPayConfig.getPrivateKeyPath()));
        return R.ok().data("key",1);
    }
}
