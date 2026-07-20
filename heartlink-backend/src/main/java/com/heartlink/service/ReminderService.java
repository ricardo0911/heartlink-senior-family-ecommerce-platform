package com.heartlink.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.dto.SaveReminderDTO;
import com.heartlink.entity.HealthReminder;
import java.util.List;

public interface ReminderService extends IService<HealthReminder> {
    HealthReminder saveReminder(SaveReminderDTO dto, Long childId);
    List<HealthReminder> getList(Long parentId);
    void deleteReminder(Long id);
    void toggleReminder(Long id);
    List<HealthReminder> getTodayReminders(Long parentId);
}
