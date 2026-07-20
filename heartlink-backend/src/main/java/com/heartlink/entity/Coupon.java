package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("hl_coupon")
public class Coupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type; // FIXED, PERCENT
    private BigDecimal value;
    private BigDecimal minAmount;
    private Long categoryId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalCount;
    private Integer remainCount;
    private Integer status;
    private Integer exchangeEnabled;
    private Integer exchangePoints;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
