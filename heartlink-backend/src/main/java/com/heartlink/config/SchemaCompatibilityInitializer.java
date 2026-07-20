package com.heartlink.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaCompatibilityInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn(
                "hl_user",
                "credit_score",
                "ALTER TABLE hl_user ADD COLUMN credit_score INT DEFAULT 100 COMMENT 'refund credit score 0-100' AFTER points"
        );
        jdbcTemplate.execute("UPDATE hl_user SET credit_score = 100 WHERE credit_score IS NULL");

        ensureColumn(
                "hl_order",
                "refund_images",
                "ALTER TABLE hl_order ADD COLUMN refund_images LONGTEXT COMMENT '退款凭证图片JSON' AFTER refund_reason"
        );
        ensureColumn(
                "hl_order",
                "refund_credit_score",
                "ALTER TABLE hl_order ADD COLUMN refund_credit_score INT DEFAULT NULL COMMENT 'credit score at refund request time' AFTER refund_images"
        );
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(alterSql);
        log.info("Schema compatibility applied: {}.{}", tableName, columnName);
    }
}
