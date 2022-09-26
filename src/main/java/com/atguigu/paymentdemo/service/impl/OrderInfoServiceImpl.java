package com.atguigu.paymentdemo.service.impl;

import com.atguigu.paymentdemo.entity.OrderInfo;
import com.atguigu.paymentdemo.entity.Product;
import com.atguigu.paymentdemo.enums.OrderStatus;
import com.atguigu.paymentdemo.mapper.OrderInfoMapper;
import com.atguigu.paymentdemo.mapper.ProductMapper;
import com.atguigu.paymentdemo.service.OrderInfoService;
import com.atguigu.paymentdemo.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private ProductMapper productMapper;

    /*@Resource
    private OrderInfoMapper orderInfoMapper;*/

    @Override
    public OrderInfo createOrderByProductId(Long productId) {
       OrderInfo orderInfo=getNoPayOrderByProductId(productId);
        if(orderInfo!=null){
            return orderInfo;
        }
        //获取商品信息
        Product product = productMapper.selectById(productId);

        //生成订单
         orderInfo = new OrderInfo();
        orderInfo.setTitle(product.getTitle());
        orderInfo.setOrderNo(OrderNoUtils.getOrderNo()); //订单号
        orderInfo.setProductId(productId);
        orderInfo.setTotalFee(product.getPrice()); //分
        orderInfo.setOrderStatus(OrderStatus.NOTPAY.getType());
        baseMapper.insert(orderInfo);
        return orderInfo;
    }

    @Override
    public void saveCodeUrl(String orderNo, String codeUrl) {
        QueryWrapper<OrderInfo>queryWrapper=new QueryWrapper<>();
        queryWrapper.eq("order_no",orderNo);
        OrderInfo orderInfo=new OrderInfo();
        orderInfo.setCodeUrl(codeUrl);
        baseMapper.update(orderInfo,queryWrapper);
    }

    @Override
    public List<OrderInfo> listOrderByCreateTimeDesc() {
       QueryWrapper<OrderInfo>list=new QueryWrapper<OrderInfo>().orderByDesc("create_time");
       return baseMapper.selectList(list);
    }

    @Override
    public String getOrderStatus(String orderNo) {
        QueryWrapper<OrderInfo>queryWrapper=new QueryWrapper<>();
        queryWrapper.eq("order_no",orderNo);
        OrderInfo orderInfo=baseMapper.selectOne(queryWrapper);
        if(orderInfo==null)return null;
            return orderInfo.getOrderStatus();
    }

    @Override
    public void updateStatusByOrderNo(String orderNo, OrderStatus success) {
        //修改订单状态
       QueryWrapper queryWrapper= new QueryWrapper<>();
       queryWrapper.eq("order_no",orderNo);
        OrderInfo orderInfo=new OrderInfo();
        orderInfo.setOrderStatus(success.getType());
       baseMapper.update(orderInfo,queryWrapper);
    }


    /**
     * 根据商品id查询未支付订单
     * 防止重复创建订单对象
     * @param productId
     * @return
     */
    private OrderInfo getNoPayOrderByProductId(Long productId) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("product_id", productId);
        queryWrapper.eq("order_status", OrderStatus.NOTPAY.getType());
//        queryWrapper.eq("user_id", userId);
        OrderInfo orderInfo = baseMapper.selectOne(queryWrapper);
        return orderInfo;
    }
}
