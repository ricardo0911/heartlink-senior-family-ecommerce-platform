package com.heartlink.controller;

import com.heartlink.service.AiTryOnProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public/ai/tryon")
@RequiredArgsConstructor
public class AiTryOnPublicController {

    private final AiTryOnProxyService aiTryOnProxyService;

    @GetMapping("/tasks/{taskId}/result")
    public ResponseEntity<byte[]> getTryOnResult(@PathVariable String taskId) {
        try {
            AiTryOnProxyService.ProxiedImage image = aiTryOnProxyService.findTryOnResult(taskId);
            if (image == null || image.bytes() == null || image.bytes().length == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                    .contentType(aiTryOnProxyService.resolveMediaType(image.contentType()))
                    .body(image.bytes());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
