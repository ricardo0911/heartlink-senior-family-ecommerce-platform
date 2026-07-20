package com.heartlink.controller;

import com.heartlink.common.Result;
import com.heartlink.dto.LoginDTO;
import com.heartlink.dto.RegisterDTO;
import com.heartlink.dto.ResetPasswordDTO;
import com.heartlink.dto.UpdateCurrentUserDTO;
import com.heartlink.entity.User;
import com.heartlink.service.ImageUploadService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ImageUploadService imageUploadService;
    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterDTO dto) {
        User user = userService.register(dto);
        return Result.success("注册成功", user);
    }

    @Operation(summary = "重置密码")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return Result.success("密码重置成功", null);
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        Map<String, Object> result = userService.login(dto);
        return Result.success("登录成功", result);
    }

    @Operation(summary = "微信登录")
    @PostMapping("/wx-login")
    public Result<Map<String, Object>> wxLogin(
            @RequestParam String code,
            @RequestParam String role,
            @RequestParam(required = false) String mockOpenid) {
        Map<String, Object> result = userService.wxLogin(code, role, mockOpenid);
        return Result.success("登录成功", result);
    }

    @Operation(summary = "绑定当前登录用户微信")
    @PostMapping("/bind-wx")
    public Result<User> bindWx(
            @RequestParam String code,
            @RequestParam(required = false) String mockOpenid) {
        User user = userService.bindCurrentUserWechat(code, mockOpenid);
        return Result.success("绑定成功", user);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/current")
    public Result<User> getCurrentUser() {
        if (!cn.dev33.satoken.stp.StpUtil.isLogin()) {
            return Result.error(401, "未登录");
        }
        User user = userService.getCurrentUser();
        return Result.success(user);
    }

    @Operation(summary = "更新当前登录用户资料")
    @PutMapping("/current")
    public Result<User> updateCurrentUser(@Valid @RequestBody UpdateCurrentUserDTO dto) {
        User user = userService.updateCurrentUserProfile(dto);
        return Result.success("资料更新成功", user);
    }

    @Operation(summary = "上传当前登录用户头像")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        return Result.success("上传成功", imageUploadService.uploadImage(file, request));
    }
}
