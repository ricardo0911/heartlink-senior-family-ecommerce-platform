package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("hl_member_check_in_log")
public class MemberCheckInLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate checkInDate;

    private Integer earnedPoints;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
