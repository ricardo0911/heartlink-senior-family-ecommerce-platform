package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.dto.CreateOrderDTO;
import com.heartlink.entity.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private OrderController orderController;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        StpUtil.logout();
    }

    @Test
    void createOrder_serviceCallShouldSucceedForDemoUser() {
        StpUtil.login(1L);

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setProductId(2L);
        dto.setParentId(2L);
        dto.setQuantity(1);
        dto.setGenerateGreeting(true);

        Result<Order> result = orderController.createOrder(dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getId());
    }

    @Test
    void createOrder_httpEndpointShouldSucceedForDemoUser() throws Exception {
        StpUtil.login(1L);
        String token = StpUtil.getTokenValue();

        mockMvc.perform(post("/api/order/create")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 2,
                                  "parentId": 2,
                                  "quantity": 1,
                                  "generateGreeting": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists());
    }
}
