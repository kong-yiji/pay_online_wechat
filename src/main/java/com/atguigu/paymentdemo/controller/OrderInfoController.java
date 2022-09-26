package com.atguigu.paymentdemo.controller;

import com.atguigu.paymentdemo.entity.OrderInfo;
import com.atguigu.paymentdemo.enums.OrderStatus;
import com.atguigu.paymentdemo.service.OrderInfoService;
import com.atguigu.paymentdemo.vo.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.websocket.server.PathParam;
import java.util.List;

/**
 * @author : yin
 * @Date: 2022/9/25
 */
@CrossOrigin
@RestController
@RequestMapping("/api/order-info")
@Api(tags = "订单消管理模块")
public class OrderInfoController {
    @Resource
    OrderInfoService orderInfoService;

    @ApiOperation("订单列表")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public R list() {
        List<OrderInfo> list = orderInfoService.listOrderByCreateTimeDesc();
        return R.ok().data("list", list);
    }


@ApiOperation("订单状态")
@RequestMapping(value = "query-order-status/{orderNo}",method = RequestMethod.GET)
    public R queryOrderStatus(@PathVariable String orderNo){
        String orderStatus=orderInfoService.getOrderStatus(orderNo);
        if(orderStatus.equals(OrderStatus.SUCCESS.getType())){
            return R.ok().setMessage("支付成功");
        }
        return R.ok().setCode(101).setMessage("支付中...");
}
}
