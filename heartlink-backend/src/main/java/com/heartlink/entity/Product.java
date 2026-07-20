package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品实体
 */
@Data
@TableName(value = "hl_product", autoResultMap = true)
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long categoryId;

    private BigDecimal price;

    private BigDecimal originalPrice;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    private String description;

    private String detail;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object specs;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> healthTags;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> warningTags;

    private String reviewsSummary;

    @TableField(exist = false)
    private List<Review> reviewPreview;

    private String voiceDescription;

    private Integer stock;

    private Integer sales;

    private String platformSource;

    private String platformUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object platformStats;

    private Long supplierId;

    private String sourceType;

    private String externalSkuId;

    private String sourceStatus;

    private BigDecimal purchasePrice;

    private BigDecimal grossMargin;

    private String taxRateMode;

    private BigDecimal taxRate;

    private String deliveryMode;

    private String aiRecommendReason;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
