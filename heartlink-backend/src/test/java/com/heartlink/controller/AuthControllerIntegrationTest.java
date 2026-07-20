package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heartlink.common.Result;
import com.heartlink.dto.UpdateCurrentUserDTO;
import com.heartlink.entity.User;
import com.heartlink.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        StpUtil.logout();
    }

    @Test
    void updateCurrentUser_shouldPersistNicknameForCurrentUser() {
        StpUtil.login(1L);
        User originalUser = userService.getById(1L);
        String originalNickname = originalUser.getNickname();

        UpdateCurrentUserDTO dto = new UpdateCurrentUserDTO();
        dto.setNickname("子女端测试" + UUID.randomUUID().toString().substring(0, 6));

        try {
            Result<User> result = authController.updateCurrentUser(dto);
            assertNotNull(result);
            assertEquals(200, result.getCode());
            assertEquals(dto.getNickname(), result.getData().getNickname());
            assertEquals(dto.getNickname(), userService.getById(1L).getNickname());
        } finally {
            User rollback = new User();
            rollback.setId(1L);
            rollback.setNickname(originalNickname);
            userService.updateById(rollback);
        }
    }

    @Test
    void updateCurrentUser_httpEndpointShouldRespondSuccessfully() throws Exception {
        StpUtil.login(1L);
        String token = StpUtil.getTokenValue();
        User originalUser = userService.getById(1L);
        String originalNickname = originalUser.getNickname();
        String newNickname = "子女端HTTP" + UUID.randomUUID().toString().substring(0, 6);

        UpdateCurrentUserDTO dto = new UpdateCurrentUserDTO();
        dto.setNickname(newNickname);

        try {
            mockMvc.perform(put("/api/auth/current")
                            .header("Authorization", token)
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.nickname").value(newNickname));
        } finally {
            User rollback = new User();
            rollback.setId(1L);
            rollback.setNickname(originalNickname);
            userService.updateById(rollback);
        }
    }
}
