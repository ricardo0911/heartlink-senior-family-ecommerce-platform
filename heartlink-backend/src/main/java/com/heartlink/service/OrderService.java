package com.heartlink.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.dto.CreateOrderDTO;
import com.heartlink.dto.PayOrderDTO;
import com.heartlink.entity.Order;

import java.util.List;

public interface OrderService extends IService<Order> {

    Order createOrder(CreateOrderDTO dto, Long childId);

    Order payOrder(Long orderId, Long childId);

    Order payOrder(Long orderId, Long childId, PayOrderDTO dto);

    IPage<Order> getChildOrders(Long childId, Integer page, Integer size, String status);

    IPage<Order> getParentOrders(Long parentId, Integer page, Integer size, String status);

    String generateGreetingCard(Long orderId, Long childId);

    Order confirmReceive(Long orderId, Long parentId);

    Order requestRefund(Long orderId, Long userId, String reason, List<String> images);

    Order approveRefund(Long orderId, Long childId);

    Order approveRefundByAdmin(Long orderId);

    Order rejectRefund(Long orderId, Long childId);
}
