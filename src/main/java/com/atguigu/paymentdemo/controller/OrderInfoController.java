package com.atguigu.paymentdemo.controller;

import com.atguigu.paymentdemo.entity.OrderInfo;
import com.atguigu.paymentdemo.service.OrderInfoService;
import com.atguigu.paymentdemo.vo.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
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
}
