package com.heartlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterDTO {
    
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    
    @NotBlank(message = "密码不能为空")
    private String password;
    
    /** 昵称 */
    private String nickname;
    
    /** 角色: CHILD/PARENT */
    @NotBlank(message = "请选择身份")
    private String role;
}
