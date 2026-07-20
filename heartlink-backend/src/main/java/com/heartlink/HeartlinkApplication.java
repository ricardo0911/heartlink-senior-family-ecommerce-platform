package com.heartlink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 连心选 HeartLink 主启动类
 * 适老化双端电商平台
 */
@SpringBootApplication
@MapperScan("com.heartlink.mapper")
@EnableScheduling
public class HeartlinkApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(HeartlinkApplication.class, args);
        System.out.println("===========================================");
        System.out.println("   连心选 HeartLink 后端服务启动成功！");
        System.out.println("   API文档: http://localhost:8089/doc.html");
        System.out.println("===========================================");
    }
}
