package com.heartlink.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class SaveReminderDTO {
    private Long id;
    private Long parentId;
    private String title;
    private String content;
    @JsonAlias({"remindTime"})
    private String reminderTime;
    private String repeatType;
    @JsonAlias({"remindDate"})
    private String reminderDate;
    private String weekDays;
}
