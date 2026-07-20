package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heartlink.common.Result;
import com.heartlink.dto.SendMessageDTO;
import com.heartlink.entity.Message;
import com.heartlink.service.AudioUploadService;
import com.heartlink.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "亲情聊天")
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final AudioUploadService audioUploadService;

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    public Result<Message> send(@RequestBody SendMessageDTO dto) {
        Long senderId = StpUtil.getLoginIdAsLong();
        Message message = messageService.sendMessage(dto, senderId);
        return Result.success("发送成功", message);
    }

    @Operation(summary = "上传语音消息")
    @PostMapping("/upload-voice")
    public Result<Map<String, String>> uploadVoice(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "clientFileName", required = false) String clientFileName,
                                                   @RequestParam(value = "clientExt", required = false) String clientExt,
                                                   HttpServletRequest request) {
        return Result.success("上传成功", audioUploadService.uploadAudio(file, request, clientFileName, clientExt));
    }

    @Operation(summary = "获取消息列表")
    @GetMapping("/list")
    public Result<IPage<Message>> list(
            @RequestParam Long familyBindId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        IPage<Message> result = messageService.getMessageList(familyBindId, page, size);
        return Result.success(result);
    }

    @Operation(summary = "获取未读消息数")
    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount() {
        Long userId = StpUtil.getLoginIdAsLong();
        int count = messageService.getUnreadCount(userId);
        return Result.success(Map.of("count", count));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/read/{familyBindId}")
    public Result<Void> markRead(@PathVariable Long familyBindId) {
        Long userId = StpUtil.getLoginIdAsLong();
        messageService.markAsRead(familyBindId, userId);
        return Result.success();
    }
}
