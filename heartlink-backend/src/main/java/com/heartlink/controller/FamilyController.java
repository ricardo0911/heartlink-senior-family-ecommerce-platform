package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.User;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 家庭绑定控制器
 */
@Tag(name = "家庭绑定")
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyBindService familyBindService;
    private final UserService userService;

    @Operation(summary = "生成亲情码(子女端)")
    @PostMapping("/generate-code")
    public Result<Map<String, Object>> generateCode(@RequestParam(defaultValue = "妈妈") String relation) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        String code = familyBindService.generateBindCode(childId, relation);

        Map<String, Object> result = new HashMap<>();
        result.put("bindCode", code);
        result.put("expireMinutes", 30);
        result.put("tips", "请让长辈在30分钟内输入此亲情码完成绑定");

        return Result.success(result);
    }

    @Operation(summary = "确认绑定(长辈端)")
    @PostMapping("/confirm-bind")
    public Result<FamilyBind> confirmBind(@RequestParam String bindCode) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        FamilyBind bind = familyBindService.confirmBind(parentId, bindCode);
        return Result.success("绑定成功", bind);
    }

    @Operation(summary = "获取我绑定的长辈列表(子女端)")
    @GetMapping("/my-parents")
    public Result<List<Map<String, Object>>> getMyParents() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "未登录");
        }
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        List<FamilyBind> binds = familyBindService.getParentsByChildId(childId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (FamilyBind bind : binds) {
            Map<String, Object> item = new HashMap<>();
            item.put("bind", bind);
            item.put("parent", userService.getById(bind.getParentId()));
            result.add(item);
        }

        return Result.success(result);
    }

    @Operation(summary = "获取绑定我的子女列表(长辈端)")
    @GetMapping("/my-children")
    public Result<List<Map<String, Object>>> getMyChildren() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "未登录");
        }
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        List<FamilyBind> binds = familyBindService.getChildrenByParentId(parentId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (FamilyBind bind : binds) {
            Map<String, Object> item = new HashMap<>();
            item.put("bind", bind);
            item.put("child", userService.getById(bind.getChildId()));
            result.add(item);
        }

        return Result.success(result);
    }

    private void assertRole(Long userId, String expectedRole) {
        User user = userService.getById(userId);
        if (user == null || !expectedRole.equals(user.getRole())) {
            throw new RuntimeException("无权限访问该接口");
        }
    }
}
