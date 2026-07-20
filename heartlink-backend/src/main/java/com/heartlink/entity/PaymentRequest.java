package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 代付请求实体
 * 长辈请求子女代付
 */
@Data
@TableName("hl_payment_request")
public class PaymentRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联订单ID */
    private Long orderId;

    /** 请求人(长辈)ID */
    private Long parentId;

    /** 付款人(子女)ID */
    private Long childId;

    /** 商品名称 */
    private String productName;

    /** 商品图片 */
    private String productImage;

    /** 付款金额 */
    private BigDecimal amount;

    /** 长辈留言 */
    private String message;

    /** 状态: PENDING-待付款, PAID-已付款, REJECTED-已拒绝, EXPIRED-已过期 */
    private String status;

    /** 付款时间 */
    private LocalDateTime paidAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
