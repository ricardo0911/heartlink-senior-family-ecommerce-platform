package com.heartlink.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminIssueCouponDTO {

    @NotNull
    private Long userId;
}
