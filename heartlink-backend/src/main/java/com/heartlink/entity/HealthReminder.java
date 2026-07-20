package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("hl_health_reminder")
public class HealthReminder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private Long childId;
    private String title;
    private String content;
    private LocalTime reminderTime;
    private String repeatType; // DAILY, WEEKLY, ONCE
    private String weekDays;
    @TableField(exist = false)
    private String reminderDate;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
