package com.heartlink.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.entity.User;
import com.heartlink.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC configuration.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserService userService;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174,http://localhost:5175,http://127.0.0.1:5175,http://localhost:8080,http://127.0.0.1:8080,http://localhost:8081,http://127.0.0.1:8081}")
    private String[] allowedOriginPatterns;

    @Value("${upload.path:./upload/}")
    private String uploadPath;

    /**
     * CORS configuration.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Sa-Token auth interceptor.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            // Admin routes: require login + ADMIN role.
            SaRouter.match("/api/admin/**")
                    .notMatch("/api/admin/auth/login")
                    .check(r -> {
                        StpUtil.checkLogin();
                        User user = userService.getById(StpUtil.getLoginIdAsLong());
                        if (user == null || !"ADMIN".equals(user.getRole())) {
                            throw new RuntimeException("No admin permission");
                        }
                    });

            // Regular routes: require login.
            SaRouter.match("/**")
                    .notMatch("/api/auth/**")
                    .notMatch("/api/admin/auth/login")
                    .notMatch("/api/wx/**")
                    .notMatch("/api/public/**")
                    .notMatch("/ws/**")
                    .notMatch("/api/product/**")
                    .notMatch("/api/category/**")
                    .notMatch("/doc.html")
                    .notMatch("/swagger-resources/**")
                    .notMatch("/webjars/**")
                    .notMatch("/v3/api-docs/**")
                    .notMatch("/upload/**")
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
    }

    /**
     * Static resource mapping.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path configuredUploadPath = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path fallbackUploadPath = Paths.get(System.getProperty("user.home"), "heartlink-upload")
                .toAbsolutePath()
                .normalize();

        registry.addResourceHandler("/upload/**")
                .addResourceLocations(
                        configuredUploadPath.toUri().toString(),
                        fallbackUploadPath.toUri().toString()
                );
    }
}
