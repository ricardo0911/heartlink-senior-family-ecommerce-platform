package com.heartlink.controller.admin;

import com.heartlink.common.Result;
import com.heartlink.dto.AdminFamilyBindingDTO;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.User;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "管理端家庭绑定管理")
@RestController
@RequestMapping("/api/admin/family")
@RequiredArgsConstructor
public class AdminFamilyController {

    private final FamilyBindService familyBindService;
    private final UserService userService;

    @Operation(summary = "获取所有家庭绑定")
    @GetMapping("/list")
    public Result<List<FamilyBind>> list() {
        return Result.success(familyBindService.list());
    }

    @Operation(summary = "按用户查询绑定对象")
    @GetMapping("/user/{userId}/bindings")
    public Result<List<AdminFamilyBindingDTO>> getBindingsByUserId(@PathVariable Long userId) {
        User currentUser = userService.getById(userId);
        if (currentUser == null) {
            return Result.success(Collections.emptyList());
        }

        boolean isChild = "CHILD".equalsIgnoreCase(currentUser.getRole());
        boolean isParent = "PARENT".equalsIgnoreCase(currentUser.getRole());
        if (!isChild && !isParent) {
            return Result.success(Collections.emptyList());
        }

        List<FamilyBind> binds = isChild
                ? familyBindService.getParentsByChildId(userId)
                : familyBindService.getChildrenByParentId(userId);

        Set<Long> relatedUserIds = binds.stream()
                .map(bind -> isChild ? bind.getParentId() : bind.getChildId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> relatedUserMap = relatedUserIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(relatedUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));

        List<AdminFamilyBindingDTO> result = binds.stream()
                .sorted(Comparator.comparing(FamilyBind::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(bind -> toBindingDTO(bind, relatedUserMap.get(isChild ? bind.getParentId() : bind.getChildId())))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.success(result);
    }

    private AdminFamilyBindingDTO toBindingDTO(FamilyBind bind, User user) {
        if (bind == null || user == null) {
            return null;
        }

        AdminFamilyBindingDTO dto = new AdminFamilyBindingDTO();
        dto.setBindId(bind.getId());
        dto.setRelation(bind.getRelation());
        dto.setBindTime(bind.getCreatedAt());
        dto.setUserId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        return dto;
    }
}
