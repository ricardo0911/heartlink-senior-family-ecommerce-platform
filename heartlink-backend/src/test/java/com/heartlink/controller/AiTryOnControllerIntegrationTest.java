package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiTryOnControllerIntegrationTest {

    private static HttpServer aiStubServer;
    private static int aiStubPort;
    private static final AtomicReference<String> lastTryOnRequestBody = new AtomicReference<>("");

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void setUpStubServer() throws IOException {
        aiStubServer = HttpServer.create(new InetSocketAddress(0), 0);
        aiStubPort = aiStubServer.getAddress().getPort();

        aiStubServer.createContext("/api/tryon/tasks", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                lastTryOnRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, """
                        {
                          "success": true,
                          "taskId": "test-task",
                          "taskStatus": "PENDING"
                        }
                        """);
                return;
            }
            writeJson(exchange, 405, "{\"message\":\"method not allowed\"}");
        });

        aiStubServer.createContext("/api/tryon/tasks/test-task", exchange -> writeJson(exchange, 200, """
                {
                  "success": true,
                  "taskId": "test-task",
                  "taskStatus": "SUCCEEDED",
                  "resultImageUrl": "http://127.0.0.1:%d/generated/tryon/test-task/result.jpg",
                  "dashscopeImageUrl": "https://example.com/result.jpg"
                }
                """.formatted(aiStubPort)));

        aiStubServer.createContext("/generated/tryon/test-task/result.jpg", exchange -> {
            byte[] body = "fake-image".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.IMAGE_JPEG_VALUE);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });

        aiStubServer.start();
    }

    @AfterAll
    static void tearDownStubServer() {
        if (aiStubServer != null) {
            aiStubServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ai-service.base-url", () -> "http://127.0.0.1:" + aiStubPort);
    }

    @AfterEach
    void tearDownLogin() {
        StpUtil.logout();
    }

    @Test
    void createTryOnTask_shouldProxyMultipartRequest() throws Exception {
        StpUtil.login(1L);
        String token = StpUtil.getTokenValue();

        mockMvc.perform(multipart("/api/ai/tryon/tasks")
                        .file("person_image", "fake-image".getBytes(StandardCharsets.UTF_8))
                        .param("garment_role", "top")
                        .param("garment_image_url", "http://127.0.0.1:8089/upload/demo.jpg")
                        .param("restore_face", "true")
                        .param("resolution", "1024")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("test-task"))
                .andExpect(jsonPath("$.data.taskStatus").value("PENDING"));

        org.junit.jupiter.api.Assertions.assertTrue(lastTryOnRequestBody.get().contains("/upload/demo.jpg"));
        org.junit.jupiter.api.Assertions.assertFalse(lastTryOnRequestBody.get().contains("http://127.0.0.1:8089/upload/demo.jpg"));
    }

    @Test
    void getTryOnTask_shouldRewriteResultUrlToPublicBackendRoute() throws Exception {
        StpUtil.login(1L);
        String token = StpUtil.getTokenValue();

        mockMvc.perform(get("/api/ai/tryon/tasks/test-task")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.resultImageUrl").value("http://localhost/api/public/ai/tryon/tasks/test-task/result"))
                .andExpect(jsonPath("$.data.dashscopeImageUrl").value("https://example.com/result.jpg"));
    }

    @Test
    void publicTryOnResult_shouldStreamImageWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/public/ai/tryon/tasks/test-task/result"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=600")))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes("fake-image".getBytes(StandardCharsets.UTF_8)));
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(statusCode, responseBody.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBody);
        }
    }
}
