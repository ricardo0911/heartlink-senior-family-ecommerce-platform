package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("hl_user_coupon")
public class UserCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long couponId;
    private Long orderId;
    private String status; // UNUSED, USED, EXPIRED
    private LocalDateTime receivedAt;
    private LocalDateTime usedAt;

    @TableField(exist = false)
    private String name;

    @TableField(exist = false)
    private String type;

    @TableField(exist = false)
    private BigDecimal value;

    @TableField(exist = false)
    private BigDecimal minAmount;

    @TableField(exist = false)
    private Long categoryId;

    @TableField(exist = false)
    private LocalDateTime startTime;

    @TableField(exist = false)
    private LocalDateTime expireTime;
}
