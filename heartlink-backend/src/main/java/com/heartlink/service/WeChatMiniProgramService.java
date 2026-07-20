package com.heartlink.service;

import com.heartlink.entity.Message;
import com.heartlink.entity.User;

public interface WeChatMiniProgramService {

    String resolveOpenid(String code);

    void sendSosSubscribeMessage(User parent, User child, Message message);
}
