package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.entity.User;
import com.heartlink.service.AiTryOnProxyService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "AI试穿")
@RestController
@RequestMapping("/api/ai/tryon")
@RequiredArgsConstructor
public class AiTryOnController {

    private final AiTryOnProxyService aiTryOnProxyService;
    private final UserService userService;

    @Operation(summary = "创建 AI 试穿任务")
    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> createTryOnTask(
            @RequestParam("person_image") MultipartFile personImage,
            @RequestParam(name = "garment_role", defaultValue = "top") String garmentRole,
            @RequestParam(name = "garment_image_url", defaultValue = "") String garmentImageUrl,
            @RequestParam(name = "top_garment_url", defaultValue = "") String topGarmentUrl,
            @RequestParam(name = "bottom_garment_url", defaultValue = "") String bottomGarmentUrl,
            @RequestParam(name = "preserve_original_other", defaultValue = "false") String preserveOriginalOther,
            @RequestParam(name = "restore_face", defaultValue = "true") String restoreFace,
            @RequestParam(name = "resolution", defaultValue = "-1") String resolution) {
        assertChildRole();
        return Result.success(aiTryOnProxyService.createTryOnTask(
                personImage,
                garmentRole,
                garmentImageUrl,
                topGarmentUrl,
                bottomGarmentUrl,
                preserveOriginalOther,
                restoreFace,
                resolution
        ));
    }

    @Operation(summary = "查询 AI 试穿任务")
    @GetMapping("/tasks/{taskId}")
    public Result<Map<String, Object>> getTryOnTask(@PathVariable String taskId,
                                                    HttpServletRequest request) {
        assertChildRole();
        return Result.success(aiTryOnProxyService.getTryOnTask(taskId, request));
    }

    private void assertChildRole() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null || !"CHILD".equals(user.getRole())) {
            throw new RuntimeException("no permission to access this api");
        }
    }
}
