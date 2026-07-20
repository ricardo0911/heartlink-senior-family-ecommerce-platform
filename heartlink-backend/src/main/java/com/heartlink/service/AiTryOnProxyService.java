package com.heartlink.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AiTryOnProxyService {

    private static final String TRY_ON_UNAVAILABLE_MESSAGE = "AI试穿服务暂不可用，请稍后重试";

    @Value("${ai-service.base-url:http://localhost:8090}")
    private String aiServiceUrl;

    @Value("${ai-service.timeout:30000}")
    private int timeout;

    private OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initHttpClient() {
        long timeoutMs = Math.max(timeout, 1000);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    public Map<String, Object> createTryOnTask(MultipartFile personImage,
                                               String garmentRole,
                                               String garmentImageUrl,
                                               String topGarmentUrl,
                                               String bottomGarmentUrl,
                                               String preserveOriginalOther,
                                               String restoreFace,
                                               String resolution) {
        if (personImage == null || personImage.isEmpty()) {
            throw new RuntimeException("请先上传模特照片");
        }

        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        try {
            String mediaType = StringUtils.hasText(personImage.getContentType())
                    ? personImage.getContentType()
                    : org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
            bodyBuilder.addFormDataPart(
                    "person_image",
                    StringUtils.hasText(personImage.getOriginalFilename()) ? personImage.getOriginalFilename() : "person-image.jpg",
                    RequestBody.create(personImage.getBytes(), MediaType.parse(mediaType))
            );
        } catch (IOException e) {
            throw new RuntimeException("读取模特照片失败", e);
        }

        addFormField(bodyBuilder, "garment_role", garmentRole);
        addFormField(bodyBuilder, "garment_image_url", normalizeMediaSource(garmentImageUrl));
        addFormField(bodyBuilder, "top_garment_url", normalizeMediaSource(topGarmentUrl));
        addFormField(bodyBuilder, "bottom_garment_url", normalizeMediaSource(bottomGarmentUrl));
        addFormField(bodyBuilder, "preserve_original_other", preserveOriginalOther);
        addFormField(bodyBuilder, "restore_face", restoreFace);
        addFormField(bodyBuilder, "resolution", resolution);

        Request request = new Request.Builder()
                .url(joinUrl("/api/tryon/tasks"))
                .post(bodyBuilder.build())
                .build();

        return executeJsonRequest(request);
    }

    public Map<String, Object> getTryOnTask(String taskId, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>(getRawTryOnTask(taskId));
        if (StringUtils.hasText(asText(response.get("resultImageUrl")))) {
            response.put("resultImageUrl", buildPublicResultUrl(request, taskId));
        }
        return response;
    }

    public ProxiedImage findTryOnResult(String taskId) {
        Map<String, Object> response = getRawTryOnTask(taskId);
        String imageUrl = asText(response.get("resultImageUrl"));
        if (!StringUtils.hasText(imageUrl)) {
            imageUrl = asText(response.get("dashscopeImageUrl"));
        }
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        return downloadImage(imageUrl);
    }

    private Map<String, Object> getRawTryOnTask(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new RuntimeException("缺少 taskId");
        }
        Request request = new Request.Builder()
                .url(joinUrl("/api/tryon/tasks/" + taskId.trim()))
                .get()
                .build();
        return executeJsonRequest(request);
    }

    private ProxiedImage downloadImage(String imageUrl) {
        Request request = new Request.Builder()
                .url(imageUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("获取试穿结果图片失败");
            }
            String contentType = response.header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.IMAGE_JPEG_VALUE);
            return new ProxiedImage(response.body().bytes(), contentType);
        } catch (IOException e) {
            throw new RuntimeException(TRY_ON_UNAVAILABLE_MESSAGE, e);
        }
    }

    private Map<String, Object> executeJsonRequest(Request request) {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw buildRequestException(responseBody);
            }
            if (!StringUtils.hasText(responseBody)) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(responseBody, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(TRY_ON_UNAVAILABLE_MESSAGE, e);
        }
    }

    private RuntimeException buildRequestException(String responseBody) {
        String message = extractMessage(responseBody);
        return new RuntimeException(StringUtils.hasText(message) ? message : TRY_ON_UNAVAILABLE_MESSAGE);
    }

    private String extractMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(responseBody, new TypeReference<>() {});
            String detail = asText(payload.get("detail"));
            if (StringUtils.hasText(detail)) {
                return detail;
            }
            return asText(payload.get("message"));
        } catch (Exception e) {
            return "";
        }
    }

    private void addFormField(MultipartBody.Builder bodyBuilder, String fieldName, String value) {
        if (StringUtils.hasText(value)) {
            bodyBuilder.addFormDataPart(fieldName, value.trim());
        }
    }

    private String normalizeMediaSource(String rawValue) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().replace('\\', '/') : "";
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String normalizedLocalPath = extractLocalMediaPath(value);
        if (StringUtils.hasText(normalizedLocalPath)) {
            return normalizedLocalPath;
        }

        try {
            URI uri = URI.create(value);
            String scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme().toLowerCase() : "";
            if ("http".equals(scheme) || "https".equals(scheme)) {
                return value;
            }
        } catch (Exception ignored) {
            // Fall through and return the original raw value.
        }
        return value;
    }

    private String extractLocalMediaPath(String value) {
        for (String marker : new String[]{"/static/", "static/", "/upload/", "upload/", "/generated/", "generated/"}) {
            int markerIndex = value.indexOf(marker);
            if (markerIndex >= 0) {
                return "/" + value.substring(markerIndex).replace('\\', '/').replaceAll("^/+", "");
            }
        }
        return "";
    }

    private String joinUrl(String path) {
        String baseUrl = StringUtils.hasText(aiServiceUrl) ? aiServiceUrl.trim() : "http://localhost:8090";
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private String buildPublicResultUrl(HttpServletRequest request, String taskId) {
        return buildFullUrl(request, "/api/public/ai/tryon/tasks/" + taskId + "/result");
    }

    private String buildFullUrl(HttpServletRequest request, String relativeUrl) {
        String scheme = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }

        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (!StringUtils.hasText(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443);
            if (!defaultPort) {
                host = host + ":" + port;
            }
        }
        return scheme + "://" + host + relativeUrl;
    }

    private String firstHeaderValue(String header) {
        if (!StringUtils.hasText(header)) {
            return "";
        }
        int commaIndex = header.indexOf(',');
        return commaIndex >= 0 ? header.substring(0, commaIndex).trim() : header.trim();
    }

    private String asText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    public org.springframework.http.MediaType resolveMediaType(String contentType) {
        if (StringUtils.hasText(contentType)) {
            try {
                return org.springframework.http.MediaType.parseMediaType(contentType);
            } catch (InvalidMediaTypeException ignored) {
                // Fallback to filename inference below.
            }
        }
        return MediaTypeFactory.getMediaType("result.jpg")
                .orElse(org.springframework.http.MediaType.IMAGE_JPEG);
    }

    public record ProxiedImage(byte[] bytes, String contentType) {
    }
}
