package com.heartlink.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminShipOrderDTO {

    @NotBlank(message = "快递公司编码不能为空")
    private String expressCompanyCode;

    @NotBlank(message = "快递公司名称不能为空")
    private String expressCompanyName;

    @NotBlank(message = "运单号不能为空")
    private String trackingNo;
}
