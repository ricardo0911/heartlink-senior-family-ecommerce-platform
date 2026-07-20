package com.heartlink.controller.admin;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.heartlink.common.Result;
import com.heartlink.dto.AdminLoginDTO;
import com.heartlink.entity.User;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Admin Auth")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.admin.auto-bootstrap:true}")
    private boolean adminAutoBootstrap;

    @Operation(summary = "Admin login")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody AdminLoginDTO dto) {
        User user = userService.getByPhone(dto.getPhone());
        if (user == null) {
            user = bootstrapAdminIfNeeded(dto);
        }
        if (user == null) {
            return Result.error("账号不存在");
        }

        String md5Pwd = DigestUtil.md5Hex(dto.getPassword());
        if (!md5Pwd.equals(user.getPassword())) {
            return Result.error("密码错误");
        }
        if (!"ADMIN".equals(user.getRole())) {
            return Result.error("无管理员权限");
        }
        if (user.getStatus() != 1) {
            return Result.error("账号已被禁用");
        }

        StpUtil.login(user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", StpUtil.getTokenValue());
        data.put("user", user);
        return Result.success(data);
    }

    private synchronized User bootstrapAdminIfNeeded(AdminLoginDTO dto) {
        if (!adminAutoBootstrap) {
            return null;
        }

        long adminCount = userService.lambdaQuery()
                .eq(User::getRole, "ADMIN")
                .count();
        if (adminCount > 0) {
            return null;
        }

        ensureAdminRoleColumnCompatible();

        User admin = new User();
        admin.setPhone(dto.getPhone());
        admin.setPassword(DigestUtil.md5Hex(dto.getPassword()));
        admin.setNickname("系统管理员");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        admin.setCreditScore(100);
        userService.save(admin);
        return admin;
    }

    private void ensureAdminRoleColumnCompatible() {
        String columnType = jdbcTemplate.queryForObject(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hl_user' AND COLUMN_NAME = 'role'",
                String.class
        );
        if (columnType == null) {
            return;
        }

        String normalized = columnType.toLowerCase();
        if (normalized.startsWith("enum(") && !normalized.contains("admin")) {
            jdbcTemplate.execute(
                    "ALTER TABLE hl_user MODIFY COLUMN role VARCHAR(20) COMMENT 'CHILD-子女, PARENT-长辈, ADMIN-管理员'"
            );
        }
    }

    @Operation(summary = "Current admin info")
    @GetMapping("/info")
    public Result<User> info() {
        StpUtil.checkLogin();
        User user = userService.getById(StpUtil.getLoginIdAsLong());
        return Result.success(user);
    }

    @Operation(summary = "Admin logout")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success(null);
    }
}
