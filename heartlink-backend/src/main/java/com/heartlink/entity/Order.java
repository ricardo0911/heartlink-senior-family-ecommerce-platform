package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@TableName("hl_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long parentId;

    private Long childId;

    private Long productId;

    private String productName;

    private String productImage;

    private String productSpec;

    private Long supplierId;

    private String supplierName;

    private String sourceType;

    private String externalSkuId;

    private String settlementMode;

    private BigDecimal settlementTaxRateSnapshot;

    private BigDecimal purchasePriceSnapshot;

    private String settlementTaxSource;

    private String procurementStatus;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal totalAmount;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String greetingCard;

    private String status;

    private LocalDateTime paidAt;

    private LocalDateTime shippedAt;

    private String expressCompanyCode;

    private String expressCompanyName;

    private String trackingNo;

    private String logisticsProvider;

    private String logisticsStatus;

    private String logisticsStatusText;

    private String logisticsLastTrace;

    private String logisticsTraceJson;

    private LocalDateTime logisticsUpdatedAt;

    private LocalDateTime completedAt;

    private String refundStatus;

    private String refundReason;

    private String refundImages;

    private Integer refundCreditScore;

    private LocalDateTime refundAt;

    @TableField(exist = false)
    private Integer childCreditScore;

    @TableField(exist = false)
    private String refundCreditRiskLevel;

    @TableField(exist = false)
    private String refundCreditRiskText;

    @TableField(exist = false)
    private String parentNickname;

    @TableField(exist = false)
    private String childNickname;

    @TableField(exist = false)
    private String orderSourceType;

    @TableField(exist = false)
    private String orderSourceText;

    @TableField(exist = false)
    private String paymentRequestStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
