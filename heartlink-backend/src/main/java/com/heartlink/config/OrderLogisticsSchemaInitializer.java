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
public class OrderLogisticsSchemaInitializer implements ApplicationRunner {

    private static final String TABLE_NAME = "hl_order";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureColumn("express_company_code", "VARCHAR(50) NULL COMMENT '快递公司编码'");
            ensureColumn("express_company_name", "VARCHAR(100) NULL COMMENT '快递公司名称'");
            ensureColumn("tracking_no", "VARCHAR(100) NULL COMMENT '物流单号'");
            ensureColumn("logistics_provider", "VARCHAR(30) NULL COMMENT '物流服务商'");
            ensureColumn("logistics_status", "VARCHAR(30) NULL COMMENT '物流状态编码'");
            ensureColumn("logistics_status_text", "VARCHAR(100) NULL COMMENT '物流状态描述'");
            ensureColumn("logistics_last_trace", "VARCHAR(500) NULL COMMENT '最新物流轨迹'");
            ensureColumn("logistics_trace_json", "LONGTEXT NULL COMMENT '物流轨迹JSON'");
            ensureColumn("logistics_updated_at", "DATETIME NULL COMMENT '物流更新时间'");
        } catch (Exception e) {
            log.warn("failed to initialize order logistics schema compatibility", e);
        }
    }

    private void ensureColumn(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                TABLE_NAME,
                columnName
        );
        if (count == null || count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + ddl);
    }
}
