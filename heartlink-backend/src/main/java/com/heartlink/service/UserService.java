package com.heartlink.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.dto.LoginDTO;
import com.heartlink.dto.RegisterDTO;
import com.heartlink.dto.ResetPasswordDTO;
import com.heartlink.dto.UpdateCurrentUserDTO;
import com.heartlink.entity.User;

import java.util.Map;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {
    
    /**
     * 用户注册
     */
    User register(RegisterDTO dto);

    /**
     * 重置密码
     */
    void resetPassword(ResetPasswordDTO dto);
    
    /**
     * 用户登录
     */
    Map<String, Object> login(LoginDTO dto);
    
    /**
     * 根据手机号查询用户
     */
    User getByPhone(String phone);
    
    /**
     * 微信登录
     */
    Map<String, Object> wxLogin(String code, String role, String mockOpenid);

    /**
     * 绑定当前登录用户的微信 openid
     */
    User bindCurrentUserWechat(String code, String mockOpenid);

    /**
     * 获取当前登录用户
     */
    User getCurrentUser();

    User updateCurrentUserProfile(UpdateCurrentUserDTO dto);
}
