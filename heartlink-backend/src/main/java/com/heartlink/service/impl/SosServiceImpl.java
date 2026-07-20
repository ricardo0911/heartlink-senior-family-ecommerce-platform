package com.heartlink.service.impl;

import com.heartlink.dto.SendMessageDTO;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.Message;
import com.heartlink.entity.User;
import com.heartlink.service.AlertNotificationService;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.MessageService;
import com.heartlink.service.SosService;
import com.heartlink.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SosServiceImpl implements SosService {

    private final UserService userService;
    private final FamilyBindService familyBindService;
    private final MessageService messageService;
    private final AlertNotificationService alertNotificationService;

    @Override
    public void triggerSos(Long parentId) {
        User parent = userService.getById(parentId);
        if (parent == null) {
            throw new RuntimeException("未找到长辈账号");
        }

        List<FamilyBind> binds = familyBindService.getChildrenByParentId(parentId);
        log.warn("长辈触发 SOS：{}（{}），待通知子女数：{}",
                parent.getNickname(), parentId, binds.size());

        String parentName = parent.getNickname() != null ? parent.getNickname() : "家人";
        String content = parentName + "发起了紧急求助，请尽快联系。";

        for (FamilyBind bind : binds) {
            User child = userService.getById(bind.getChildId());
            if (child == null) {
                log.warn("skip SOS notification because child user is missing, bindId={}, childId={}", bind.getId(), bind.getChildId());
                continue;
            }

            SendMessageDTO dto = new SendMessageDTO();
            dto.setFamilyBindId(bind.getId());
            dto.setReceiverId(bind.getChildId());
            dto.setType("SOS");
            dto.setContent(content);

            Message message = messageService.sendMessage(dto, parentId);
            alertNotificationService.notifySos(parent, child, bind.getId(), message);
        }
    }

    @Override
    public String getContacts(Long userId) {
        User user = userService.getById(userId);
        return user != null ? user.getSosContacts() : null;
    }

    @Override
    public void saveContacts(Long userId, String contacts) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("user not found");
        }
        user.setSosContacts(contacts);
        userService.updateById(user);
    }
}
