package com.heartlink.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaveAddressDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeBooleanFalseAsZero() throws Exception {
        SaveAddressDTO dto = objectMapper.readValue("""
                {"receiverName":"Tester","isDefault":false}
                """, SaveAddressDTO.class);

        assertEquals(0, dto.getIsDefault());
    }

    @Test
    void shouldDeserializeBooleanTrueAsOne() throws Exception {
        SaveAddressDTO dto = objectMapper.readValue("""
                {"receiverName":"Tester","isDefault":true}
                """, SaveAddressDTO.class);

        assertEquals(1, dto.getIsDefault());
    }

    @Test
    void shouldDeserializeNumericDefaultFlag() throws Exception {
        SaveAddressDTO dto = objectMapper.readValue("""
                {"receiverName":"Tester","isDefault":1}
                """, SaveAddressDTO.class);

        assertEquals(1, dto.getIsDefault());
    }
}
