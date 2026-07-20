package com.heartlink.service;

import com.heartlink.dto.OrderLogisticsDTO;
import com.heartlink.entity.Order;

public interface LogisticsService {

    OrderLogisticsDTO getOrderLogistics(Order order, boolean refresh);

    void tryRefreshOrderLogistics(Order order);
}
