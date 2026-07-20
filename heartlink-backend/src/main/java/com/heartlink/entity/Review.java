package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "hl_review", autoResultMap = true)
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long userId;
    private Long orderId;
    private String content;
    private String voiceUrl;
    private Integer rating;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;
    @TableField(exist = false)
    private String userNickname;
    @TableField(exist = false)
    private String userAvatar;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
