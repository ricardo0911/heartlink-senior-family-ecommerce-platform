package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 * 支持子女(CHILD)和长辈(PARENT)两种角色
 */
@Data
@TableName("hl_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号 */
    private String phone;

    /** 密码(加密) */
    @JsonIgnore
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 角色: CHILD-子女, PARENT-长辈 */
    private String role;

    /** 微信openid */
    @JsonIgnore
    private String openid;

    /** 会员积分 */
    private Integer points;

    /** 退款信用分 */
    private Integer creditScore;

    /** 会员等级: 0-普通, 1-银卡, 2-金卡, 3-钻石 */
    private Integer memberLevel;

    /** 紧急联系人(JSON) */
    private String sosContacts;

    /** 状态: 0-禁用, 1-正常 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
