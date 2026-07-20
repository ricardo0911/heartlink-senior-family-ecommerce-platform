package com.heartlink.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrderLogisticsDTO {

    private Long orderId;
    private String orderNo;
    private String expressCompanyCode;
    private String expressCompanyName;
    private String trackingNo;
    private String logisticsProvider;
    private String logisticsStatus;
    private String logisticsStatusText;
    private String logisticsLastTrace;
    private LocalDateTime logisticsUpdatedAt;
    private Boolean hasTracking;
    private Boolean queryEnabled;
    private Boolean refreshed;
    private String message;
    private List<LogisticsTraceItemDTO> traces = new ArrayList<>();
}
