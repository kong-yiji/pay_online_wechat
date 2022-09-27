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
/***
 * @author yin
 * 取消订单
 *  2022/9/27  10:50
 * @author yin
 * @parans [request, response]
 * @return java.lang.String
 */
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

     @ApiOperation("取消订单")
     @PostMapping("/cancel/{orderNo}")
    public R cancel(@PathVariable String orderNo) throws IOException {
        log.info("取消订单");
        wxPayService.cancelOrder(orderNo);
        return R.ok().setMessage("取消成功");
     }

     /***
      * @author yin
      *  2022/9/27  10:50
      *  查询订单
      * @author yin
      * @parans [orderNo]
      * @return com.atguigu.paymentdemo.vo.R
      */
     @RequestMapping(value = "/query/{orderNo}",method = RequestMethod.GET)
     public R queryOrder(@PathVariable String orderNo) throws IOException {
        log.info("查询订单");
        String result=wxPayService.queryOrder(orderNo);
        return R.ok().setMessage("查询成功").data("result",result);
     }
/***
 * @author yin
 * 申请退款
 *  2022/9/27  10:51
 * @author yin
 * @parans [orderNo, reason]
 * @return com.atguigu.paymentdemo.vo.R
 */
    @ApiOperation("申请退款")
    @PostMapping("/refunds/{orderNo}/{reason}")
    public R refunds(@PathVariable String orderNo, @PathVariable String reason) throws Exception {

        log.info("申请退款");
        wxPayService.refund(orderNo, reason);
        return R.ok();
    }



    /**
     * 查询退款
     * @param refundNo
     * @return
     * @throws Exception
     */
    @ApiOperation("查询退款：测试用")
    @GetMapping("/query-refund/{refundNo}")
    public R queryRefund(@PathVariable String refundNo) throws Exception {

        log.info("查询退款");

        String result = wxPayService.queryRefund(refundNo);
        return R.ok().setMessage("查询成功").data("result", result);
    }


    /**
     * 退款结果通知
     * 退款状态改变后，微信会把相关退款结果发送给商户。
     */
    @ApiOperation("退款结果通知")
    @PostMapping("/refunds/notify")
    public String refundsNotify(HttpServletRequest request, HttpServletResponse response){

        log.info("退款通知执行");
        Gson gson = new Gson();
        Map<String, String> map = new HashMap<>();//应答对象

        try {
            //处理通知参数
            String body = HttpUtils.readData(request);
            Map<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String)bodyMap.get("id");
            log.info("支付通知的id ===> {}", requestId);

            //签名的验证
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, body, requestId);
            if(!wechatPay2ValidatorForRequest.validate(request)){

                log.error("通知验签失败");
                //失败应答
                response.setStatus(500);
                map.put("code", "ERROR");
                map.put("message", "通知验签失败");
                return gson.toJson(map);
            }
            log.info("通知验签成功");

            //处理退款单
            wxPayService.processRefund(bodyMap);

            //成功应答
            response.setStatus(200);
            map.put("code", "SUCCESS");
            map.put("message", "成功");
            return gson.toJson(map);

        } catch (Exception e) {
            e.printStackTrace();
            //失败应答
            response.setStatus(500);
            map.put("code", "ERROR");
            map.put("message", "失败");
            return gson.toJson(map);
        }
    }

    @ApiOperation("下载账单")
    @GetMapping("/downloadbill/{billDate}/{type}")
    public R downloadBill(
            @PathVariable String billDate,
            @PathVariable String type) throws Exception {

        log.info("下载账单");
        String result = wxPayService.downloadBill(billDate, type);

        return R.ok().data("result", result);
    }
}
