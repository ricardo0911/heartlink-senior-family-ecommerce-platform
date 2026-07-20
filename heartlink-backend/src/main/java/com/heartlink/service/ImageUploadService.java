package com.heartlink.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageUploadService {

    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"
    );
    private static final String DEFAULT_UPLOAD_DIR = "./upload/";
    private static final String FALLBACK_UPLOAD_DIR = "heartlink-upload";

    @Value("${upload.path:" + DEFAULT_UPLOAD_DIR + "}")
    private String uploadPath;

    @Value("${upload.url-prefix:/upload/}")
    private String urlPrefix;

    public Map<String, String> uploadImage(MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("No file selected");
        }

        String originalName = file.getOriginalFilename();
        String ext = getFileExt(originalName);
        if (!ALLOWED_IMAGE_EXT.contains(ext)) {
            throw new RuntimeException("Only jpg/jpeg/png/webp/gif/bmp are supported");
        }

        Path rootDir = resolveWritableUploadDir();
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileName = datePrefix + "_" + UUID.randomUUID().toString().replace("-", "") + ext;
        Path targetPath = rootDir.resolve(fileName).normalize();

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image, upload dir is not writable: " + rootDir, e);
        }

        String relativeUrl = normalizeUrlPrefix(urlPrefix) + fileName;
        String fullUrl = buildFullUrl(request, relativeUrl);

        Map<String, String> data = new HashMap<>();
        data.put("url", fullUrl);
        data.put("relativeUrl", relativeUrl);
        data.put("fileName", fileName);
        data.put("originalName", StringUtils.hasText(originalName) ? originalName : fileName);
        return data;
    }

    private Path resolveWritableUploadDir() {
        Path primaryPath = Paths.get(StringUtils.hasText(uploadPath) ? uploadPath : DEFAULT_UPLOAD_DIR)
                .toAbsolutePath()
                .normalize();
        if (ensureWritableDirectory(primaryPath)) {
            return primaryPath;
        }

        Path fallbackPath = Paths.get(System.getProperty("user.home"), FALLBACK_UPLOAD_DIR)
                .toAbsolutePath()
                .normalize();
        if (ensureWritableDirectory(fallbackPath)) {
            return fallbackPath;
        }

        throw new RuntimeException(
                "No writable upload directory, checked primary=" + primaryPath + ", fallback=" + fallbackPath
        );
    }

    private boolean ensureWritableDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return Files.isDirectory(dir) && Files.isWritable(dir);
        } catch (IOException e) {
            return false;
        }
    }

    private String getFileExt(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String normalizeUrlPrefix(String prefix) {
        String value = StringUtils.hasText(prefix) ? prefix.trim() : "/upload/";
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
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
}
