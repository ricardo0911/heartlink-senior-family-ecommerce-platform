package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.dto.SaveReminderDTO;
import com.heartlink.entity.HealthReminder;
import com.heartlink.mapper.HealthReminderMapper;
import com.heartlink.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl extends ServiceImpl<HealthReminderMapper, HealthReminder> implements ReminderService {

    @Override
    public HealthReminder saveReminder(SaveReminderDTO dto, Long childId) {
        HealthReminder reminder;
        if (dto.getId() != null) {
            reminder = getById(dto.getId());
            if (reminder == null) {
                throw new RuntimeException("提醒不存在");
            }
        } else {
            reminder = new HealthReminder();
            reminder.setChildId(childId);
        }
        reminder.setParentId(dto.getParentId());
        reminder.setTitle(dto.getTitle());
        reminder.setContent(dto.getContent());
        reminder.setReminderTime(LocalTime.parse(dto.getReminderTime()));
        String repeatType = dto.getRepeatType() != null ? dto.getRepeatType() : "DAILY";
        reminder.setRepeatType(repeatType);
        if ("ONCE".equals(repeatType)) {
            reminder.setWeekDays(normalizeOnceDate(dto.getReminderDate(), dto.getWeekDays()));
        } else {
            reminder.setWeekDays(dto.getWeekDays());
        }
        if (reminder.getEnabled() == null) {
            reminder.setEnabled(1);
        }
        saveOrUpdate(reminder);
        populateReminderDate(reminder);
        return reminder;
    }

    @Override
    public List<HealthReminder> getList(Long parentId) {
        List<HealthReminder> reminders = list(new LambdaQueryWrapper<HealthReminder>()
                .eq(HealthReminder::getParentId, parentId)
                .orderByAsc(HealthReminder::getReminderTime));
        reminders.forEach(this::populateReminderDate);
        return reminders;
    }

    @Override
    public void deleteReminder(Long id) {
        removeById(id);
    }

    @Override
    public void toggleReminder(Long id) {
        HealthReminder reminder = getById(id);
        if (reminder == null) {
            throw new RuntimeException("提醒不存在");
        }
        reminder.setEnabled(reminder.getEnabled() == 1 ? 0 : 1);
        updateById(reminder);
    }

    @Override
    public List<HealthReminder> getTodayReminders(Long parentId) {
        List<HealthReminder> all = list(new LambdaQueryWrapper<HealthReminder>()
                .eq(HealthReminder::getParentId, parentId)
                .eq(HealthReminder::getEnabled, 1)
                .orderByAsc(HealthReminder::getReminderTime));

        int todayDow = LocalDate.now().getDayOfWeek().getValue(); // 1=Monday

        List<HealthReminder> reminders = all.stream().filter(r -> {
            if ("DAILY".equals(r.getRepeatType())) return true;
            if ("WEEKLY".equals(r.getRepeatType()) && r.getWeekDays() != null) {
                return containsWeekday(r.getWeekDays(), todayDow);
            }
            if ("ONCE".equals(r.getRepeatType())) {
                return isToday(r.getWeekDays());
            }
            return false;
        }).collect(Collectors.toList());
        reminders.forEach(this::populateReminderDate);
        return reminders;
    }

    private String normalizeOnceDate(String reminderDate, String weekDays) {
        if (isIsoDate(reminderDate)) {
            return reminderDate;
        }
        if (isIsoDate(weekDays)) {
            return weekDays;
        }
        // Backward-compatible default: if ONCE date is missing, schedule it for today.
        return LocalDate.now().toString();
    }

    private boolean isToday(String value) {
        if (!isIsoDate(value)) {
            return false;
        }
        return LocalDate.now().equals(LocalDate.parse(value));
    }

    private boolean containsWeekday(String weekDays, int todayDow) {
        if (weekDays == null || weekDays.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(weekDays.split(","))
                .map(String::trim)
                .anyMatch(v -> v.equals(String.valueOf(todayDow)));
    }

    private boolean isIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            LocalDate.parse(value.trim());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private void populateReminderDate(HealthReminder reminder) {
        if (reminder == null) {
            return;
        }
        if ("ONCE".equals(reminder.getRepeatType()) && isIsoDate(reminder.getWeekDays())) {
            reminder.setReminderDate(reminder.getWeekDays());
        } else {
            reminder.setReminderDate(null);
        }
    }
}
