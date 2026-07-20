package com.heartlink.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginDTO {
    
    @NotBlank(message = "手机号不能为空")
    private String phone;
    
    @NotBlank(message = "密码不能为空")
    private String password;
    
    /** 角色: CHILD/PARENT */
    @NotBlank(message = "请选择身份")
    private String role;
}
