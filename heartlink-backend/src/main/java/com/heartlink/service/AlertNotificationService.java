package com.heartlink.service;

import com.heartlink.entity.Message;
import com.heartlink.entity.User;

public interface AlertNotificationService {

    void notifySos(User parent, User child, Long familyBindId, Message message);
}
