package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.common.Result;
import com.heartlink.entity.User;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-用户管理")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public Result<IPage<User>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(role)) wrapper.eq(User::getRole, role);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getPhone, keyword).or().like(User::getNickname, keyword));
        }
        wrapper.orderByDesc(User::getCreatedAt);
        IPage<User> result = userService.page(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::maskNonChildMemberFields);
        return Result.success(result);
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        maskNonChildMemberFields(user);
        return Result.success(user);
    }

    @Operation(summary = "更新用户状态")
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        userService.updateById(user);
        return Result.success(null);
    }

    @Operation(summary = "更新用户信用分")
    @PostMapping("/{id}/credit-score")
    public Result<Void> updateCreditScore(@PathVariable Long id, @RequestParam Integer creditScore) {
        if (creditScore == null || creditScore < 0 || creditScore > 100) {
            return Result.error("信用分必须是 0 到 100 之间的整数");
        }
        User user = new User();
        user.setId(id);
        user.setCreditScore(creditScore);
        userService.updateById(user);
        return Result.success(null);
    }

    private void maskNonChildMemberFields(User user) {
        if (user == null) return;
        if (!"CHILD".equalsIgnoreCase(user.getRole())) {
            user.setPoints(null);
            user.setMemberLevel(null);
        }
    }
}
