package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.entity.User;
import com.heartlink.service.SosService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "紧急求助")
@RestController
@RequestMapping("/api/sos")
@RequiredArgsConstructor
public class SosController {

    private final SosService sosService;
    private final UserService userService;

    @Operation(summary = "触发SOS求助")
    @PostMapping("/trigger")
    public Result<Void> trigger() {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        sosService.triggerSos(parentId);
        return Result.success();
    }

    @Operation(summary = "获取紧急联系人")
    @GetMapping("/contacts")
    public Result<String> getContacts() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(sosService.getContacts(userId));
    }

    @Operation(summary = "设置紧急联系人")
    @PostMapping("/contacts")
    public Result<Void> saveContacts(@RequestBody String contacts) {
        Long userId = StpUtil.getLoginIdAsLong();
        sosService.saveContacts(userId, contacts);
        return Result.success();
    }

    private void assertRole(Long userId, String expectedRole) {
        User user = userService.getById(userId);
        if (user == null || !expectedRole.equals(user.getRole())) {
            throw new RuntimeException("无权限访问该接口");
        }
    }
}
