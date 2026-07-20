package com.heartlink.dto;

import lombok.Data;

import java.util.List;

@Data
public class RefundRequestDTO {
    private String reason;
    private List<String> images;
}
