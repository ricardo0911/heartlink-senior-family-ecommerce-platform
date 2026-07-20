package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.dto.SaveReminderDTO;
import com.heartlink.entity.HealthReminder;
import com.heartlink.entity.User;
import com.heartlink.service.ReminderService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "健康提醒")
@RestController
@RequestMapping("/api/reminder")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;
    private final UserService userService;

    @Operation(summary = "创建/编辑提醒")
    @PostMapping("/save")
    public Result<HealthReminder> save(@RequestBody SaveReminderDTO dto) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        HealthReminder reminder = reminderService.saveReminder(dto, childId);
        return Result.success("保存成功", reminder);
    }

    @Operation(summary = "获取提醒列表")
    @GetMapping("/list/{parentId}")
    public Result<List<HealthReminder>> list(@PathVariable Long parentId) {
        return Result.success(reminderService.getList(parentId));
    }

    @Operation(summary = "删除提醒")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reminderService.deleteReminder(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用提醒")
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        reminderService.toggleReminder(id);
        return Result.success();
    }

    @Operation(summary = "获取今日提醒(长辈端)")
    @GetMapping("/today")
    public Result<List<HealthReminder>> today() {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        return Result.success(reminderService.getTodayReminders(parentId));
    }

    private void assertRole(Long userId, String expectedRole) {
        User user = userService.getById(userId);
        if (user == null || !expectedRole.equals(user.getRole())) {
            throw new RuntimeException("无权限访问该接口");
        }
    }
}
