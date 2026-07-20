package com.heartlink.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class SaveAddressDTO {
    private Long id;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Integer isDefault = 0;

    @JsonSetter("isDefault")
    public void setIsDefaultFromJson(JsonNode isDefaultNode) {
        if (isDefaultNode == null || isDefaultNode.isNull()) {
            this.isDefault = 0;
            return;
        }

        if (isDefaultNode.isBoolean()) {
            this.isDefault = isDefaultNode.booleanValue() ? 1 : 0;
            return;
        }

        if (isDefaultNode.isNumber()) {
            this.isDefault = isDefaultNode.asInt() == 0 ? 0 : 1;
            return;
        }

        String value = isDefaultNode.asText("").trim();
        if (value.isEmpty() || "false".equalsIgnoreCase(value)) {
            this.isDefault = 0;
            return;
        }
        if ("true".equalsIgnoreCase(value)) {
            this.isDefault = 1;
            return;
        }

        this.isDefault = Integer.parseInt(value) == 0 ? 0 : 1;
    }
}
