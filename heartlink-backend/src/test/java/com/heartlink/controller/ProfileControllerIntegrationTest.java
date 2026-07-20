package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heartlink.common.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileControllerIntegrationTest {

    @Autowired
    private ProfileController profileController;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        StpUtil.logout();
    }

    @Test
    void getHealthAlerts_shouldReturnSummaryForBoundParent() {
        StpUtil.login(1L);

        Result<Map<String, Object>> result = profileController.getHealthAlerts(2L);
        assertDoesNotThrow(() -> objectMapper.writeValueAsString(result));

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(2L, result.getData().get("parentId"));
    }

    @Test
    void getHealthAlerts_httpEndpointShouldRespondSuccessfully() throws Exception {
        StpUtil.login(1L);
        String token = StpUtil.getTokenValue();

        mockMvc.perform(get("/api/profile/2/health-alerts")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.parentId").value(2));
    }
}
