package com.heartlink.dto;

import lombok.Data;
import java.util.List;

@Data
public class SubmitReviewDTO {
    private Long productId;
    private Long orderId;
    private String content;
    private String voiceUrl;
    private Integer rating;
    private List<String> images;
}
