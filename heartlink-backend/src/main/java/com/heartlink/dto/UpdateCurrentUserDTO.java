package com.heartlink.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCurrentUserDTO {

    @Size(max = 30, message = "昵称不能超过30个字符")
    private String nickname;

    @Size(max = 500, message = "头像地址过长")
    private String avatar;
}
