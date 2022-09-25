package com.atguigu.paymentdemo.service;

import com.atguigu.paymentdemo.entity.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.stereotype.Service;

import java.util.List;


public interface OrderInfoService extends IService<OrderInfo> {

    OrderInfo createOrderByProductId(Long productId);
    void saveCodeUrl(String orderNo,String codeUrl);

    List<OrderInfo> listOrderByCreateTimeDesc();
}
