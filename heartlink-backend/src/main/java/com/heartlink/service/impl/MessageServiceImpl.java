package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.dto.SendMessageDTO;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.Message;
import com.heartlink.mapper.MessageMapper;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.MessageService;
import com.heartlink.websocket.WebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("TEXT", "VOICE", "IMAGE");

    private final FamilyBindService familyBindService;
    private final WebSocketPushService webSocketPushService;

    @Override
    public Message sendMessage(SendMessageDTO dto, Long senderId) {
        if (dto.getFamilyBindId() == null) {
            throw new RuntimeException("familyBindId cannot be null");
        }

        Long receiverId = resolveReceiverId(dto, senderId);

        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setFamilyBindId(dto.getFamilyBindId());
        message.setContent(dto.getContent());
        message.setType(normalizeMessageType(dto.getType()));
        message.setVoiceUrl(dto.getVoiceUrl());
        message.setImageUrl(dto.getImageUrl());
        message.setIsRead(0);
        save(message);

        webSocketPushService.pushChatMessage(message);
        return message;
    }

    @Override
    public IPage<Message> getMessageList(Long familyBindId, Integer page, Integer size) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getFamilyBindId, familyBindId)
                .orderByDesc(Message::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public int getUnreadCount(Long userId) {
        return (int) count(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0));
    }

    @Override
    public void markAsRead(Long familyBindId, Long userId) {
        update(new LambdaUpdateWrapper<Message>()
                .eq(Message::getFamilyBindId, familyBindId)
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
    }

    private Long resolveReceiverId(SendMessageDTO dto, Long senderId) {
        if (dto.getReceiverId() != null) {
            return dto.getReceiverId();
        }

        FamilyBind bind = familyBindService.getById(dto.getFamilyBindId());
        if (bind == null || !"ACTIVE".equals(bind.getStatus())) {
            throw new RuntimeException("family bind is not found or inactive");
        }

        if (senderId.equals(bind.getParentId())) {
            return bind.getChildId();
        }
        if (senderId.equals(bind.getChildId())) {
            return bind.getParentId();
        }
        throw new RuntimeException("sender is not in this family bind");
    }

    private String normalizeMessageType(String rawType) {
        if (rawType == null) {
            return "TEXT";
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            return "TEXT";
        }
        return type;
    }
}
