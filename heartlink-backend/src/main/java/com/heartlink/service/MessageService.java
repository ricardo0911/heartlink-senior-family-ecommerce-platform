package com.heartlink.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.dto.SendMessageDTO;
import com.heartlink.entity.Message;

public interface MessageService extends IService<Message> {
    Message sendMessage(SendMessageDTO dto, Long senderId);
    IPage<Message> getMessageList(Long familyBindId, Integer page, Integer size);
    int getUnreadCount(Long userId);
    void markAsRead(Long familyBindId, Long userId);
}
