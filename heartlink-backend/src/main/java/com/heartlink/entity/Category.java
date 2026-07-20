package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 商品分类
 */
@Data
@TableName("hl_category")
public class Category {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 分类名称 */
    private String name;
    
    /** 分类图标 */
    private String icon;
    
    /** 父分类ID */
    private Long parentId;
    
    /** 排序号 */
    private Integer sort;
    
    /** 状态 */
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
