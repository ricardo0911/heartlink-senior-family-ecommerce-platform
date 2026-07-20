package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.entity.HealthProfile;
import com.heartlink.entity.User;
import com.heartlink.service.HealthProfileService;
import com.heartlink.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileMultiProfileController {

    private final HealthProfileService healthProfileService;
    private final UserService userService;

    @GetMapping("/my/list")
    public Result<List<HealthProfile>> getMyProfiles() {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        return Result.success(healthProfileService.listByParentId(parentId));
    }

    @PostMapping("/my/{profileId}/primary")
    public Result<HealthProfile> setMyPrimaryProfile(@PathVariable Long profileId) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        HealthProfile saved = healthProfileService.setPrimaryProfile(parentId, profileId);
        return Result.success("设置成功", saved);
    }

    private void assertRole(Long userId, String expectedRole) {
        User user = userService.getById(userId);
        if (user == null || !expectedRole.equals(user.getRole())) {
            throw new RuntimeException("无权限访问该接口");
        }
    }
}
