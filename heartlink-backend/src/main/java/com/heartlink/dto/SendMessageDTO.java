package com.heartlink.dto;

import lombok.Data;

@Data
public class SendMessageDTO {
    private Long familyBindId;
    private Long receiverId;
    private String content;
    private String type; // TEXT, VOICE, IMAGE
    private String voiceUrl;
    private String imageUrl;
}
