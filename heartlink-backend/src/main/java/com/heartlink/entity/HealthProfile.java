package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 长辈健康档案
 * 由子女或长辈填写，AI根据档案进行商品筛选。
 */
@Data
@TableName(value = "hl_health_profile", autoResultMap = true)
public class HealthProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 长辈用户ID */
    private Long parentId;

    /** 档案名称 */
    private String profileName;

    /** 与账号持有人的关系 */
    private String relation;

    /** 是否主档案: 1-是, 0-否 */
    private Integer isPrimary;

    /** 排序 */
    private Integer sortOrder;

    /** 年龄 */
    private Integer age;

    /** 身高(cm) */
    private Double height;

    /** 体重(kg) */
    private Double weight;

    /** 慢性病列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> chronicDiseases;

    /** 过敏源 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allergies;

    /** 兴趣爱好 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> hobbies;

    /** 衣码: S/M/L/XL/XXL */
    private String clothingSize;

    /** 鞋码 */
    private Integer shoeSize;

    /** 其他偏好备注 */
    private String preferences;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
