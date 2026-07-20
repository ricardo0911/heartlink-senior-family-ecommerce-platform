package com.heartlink.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SettlementMetricsService {

    Map<String, Object> buildOverview(LocalDate startDate, LocalDate endDate, String sourceType, Long supplierId);

    List<Map<String, Object>> buildTrend(LocalDate startDate,
                                         LocalDate endDate,
                                         String groupBy,
                                         String sourceType,
                                         Long supplierId);

    IPage<Map<String, Object>> pageOrders(int page,
                                          int size,
                                          LocalDate startDate,
                                          LocalDate endDate,
                                          String sourceType,
                                          Long supplierId,
                                          String keyword,
                                          Boolean refundOnly);
}
