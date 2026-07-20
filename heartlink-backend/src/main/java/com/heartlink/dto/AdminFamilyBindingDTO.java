package com.heartlink.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminFamilyBindingDTO {

    private Long bindId;

    private String relation;

    private LocalDateTime bindTime;

    private Long userId;

    private String nickname;

    private String phone;

    private String role;
}
