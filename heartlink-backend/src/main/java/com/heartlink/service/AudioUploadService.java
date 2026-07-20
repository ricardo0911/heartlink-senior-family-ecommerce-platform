package com.heartlink.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class AudioUploadService {

    private static final Set<String> ALLOWED_AUDIO_EXT = Set.of(
            ".mp3", ".m4a", ".wav", ".aac", ".amr", ".ogg", ".silk", ".sil", ".pcm", ".caf"
    );
    private static final String DEFAULT_UPLOAD_DIR = "./upload/";
    private static final String FALLBACK_UPLOAD_DIR = "heartlink-upload";

    @Value("${upload.path:" + DEFAULT_UPLOAD_DIR + "}")
    private String uploadPath;

    @Value("${upload.url-prefix:/upload/}")
    private String urlPrefix;

    public Map<String, String> uploadAudio(MultipartFile file,
                                           HttpServletRequest request,
                                           String clientFileName,
                                           String clientExt) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("未选择语音文件");
        }

        String originalName = firstNonBlank(clientFileName, file.getOriginalFilename());
        String ext = resolveAudioExt(originalName, clientExt, file.getContentType());
        if (!ALLOWED_AUDIO_EXT.contains(ext)) {
            log.warn("Unsupported audio upload ext, originalName={}, clientExt={}, contentType={}",
                    originalName, clientExt, file.getContentType());
            throw new RuntimeException("当前录音格式暂不支持，请优先使用真机调试");
        }

        Path rootDir = resolveWritableUploadDir();
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileName = datePrefix + "_" + UUID.randomUUID().toString().replace("-", "") + ext;
        Path targetPath = rootDir.resolve(fileName).normalize();

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("语音保存失败，请检查上传目录权限", e);
        }

        String relativeUrl = normalizeUrlPrefix(urlPrefix) + fileName;
        String fullUrl = buildFullUrl(request, relativeUrl);

        log.info("Audio uploaded, originalName={}, storedFile={}, contentType={}, size={}",
                originalName, fileName, file.getContentType(), file.getSize());

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

        throw new RuntimeException("服务器上传目录不可用");
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

    private String resolveAudioExt(String originalName, String clientExt, String contentType) {
        String ext = normalizeExt(clientExt);
        if (StringUtils.hasText(ext)) {
            return ext;
        }

        ext = getFileExt(originalName);
        if (StringUtils.hasText(ext)) {
            return ext;
        }

        String normalizedType = StringUtils.hasText(contentType)
                ? contentType.trim().toLowerCase(Locale.ROOT)
                : "";
        if (normalizedType.contains("mpeg") || normalizedType.contains("mp3")) {
            return ".mp3";
        }
        if (normalizedType.contains("mp4") || normalizedType.contains("m4a") || normalizedType.contains("aac")) {
            return ".m4a";
        }
        if (normalizedType.contains("wav")) {
            return ".wav";
        }
        if (normalizedType.contains("amr")) {
            return ".amr";
        }
        if (normalizedType.contains("ogg")) {
            return ".ogg";
        }
        if (normalizedType.contains("caf")) {
            return ".caf";
        }
        if (normalizedType.contains("pcm")) {
            return ".pcm";
        }
        if (normalizedType.contains("silk")) {
            return ".silk";
        }
        return "";
    }

    private String normalizeExt(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String ext = value.trim().toLowerCase(Locale.ROOT);
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        return ext;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
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
