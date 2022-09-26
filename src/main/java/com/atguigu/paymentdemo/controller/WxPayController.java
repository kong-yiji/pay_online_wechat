package com.atguigu.paymentdemo.controller;

import com.atguigu.paymentdemo.service.WxPayService;
import com.atguigu.paymentdemo.util.HttpUtils;
import com.atguigu.paymentdemo.util.WechatPay2ValidatorForRequest;
import com.atguigu.paymentdemo.vo.R;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : yin
 * @Date: 2022/9/25
 */
@CrossOrigin
@RestController
@RequestMapping("/api/wx-pay")
@Api(tags = "网站微信api支付")
@Slf4j
public class WxPayController {
    @Resource
    private WxPayService wxPayService;
    @Resource
    private Verifier verifier;

    @ApiOperation("统一下单支付，生成二维码")
    @RequestMapping(value = "native/{productId}",method = RequestMethod.POST)
    public R nativePay(@PathVariable long productId) throws IOException {
        log.info("发起支付");
        Map<String, Object> map = wxPayService.nativePay(productId);
        return R.ok().setData(map);
    }

    @PostMapping("/native/notify")
    public String nativeNotify(HttpServletRequest request, HttpServletResponse response){
        Map<String,Object>map=new HashMap<>();
        Gson gson=new Gson();
        try {
            String body= HttpUtils.readData(request);
            Map<String,Object>bodyMap=gson.fromJson(body, HashMap.class);
            String requestId=(String)bodyMap.get("id");
            log.info("完全数据"+bodyMap);
            // 签名认证
            WechatPay2ValidatorForRequest validator=new WechatPay2ValidatorForRequest(verifier,body,requestId);
          if(!(validator.validate(request))){
              response.setStatus(500);
              map.put("code","ERROR");
              map.put("message","签名验证失败");
          }

            //处理订单
          wxPayService.processOrder(bodyMap);
            response.setStatus(200);
            //应答对象
            map.put("code","SUCCESS");
            map.put("message","成功");
            return gson.toJson(map);
        } catch (JsonSyntaxException | IOException | GeneralSecurityException e) {
           response.setStatus(500);
           map.put("code","ERROR");
           map.put("message","失败");
            return gson.toJson(map);
        }
    }
}
