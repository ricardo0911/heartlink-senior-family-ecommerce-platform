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
public class MemberPointsSchemaInitializer implements ApplicationRunner {

    private static final String MEMBER_POINTS_CONFIG_TABLE = "hl_member_points_config";
    private static final String COUPON_TABLE = "hl_coupon";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureMemberPointsConfigTable();
            ensureMemberCheckInLogTable();
            ensureDefaultConfig();
            ensureCouponExchangeColumns();
        } catch (Exception e) {
            log.warn("failed to initialize member points schema compatibility", e);
        }
    }

    private void ensureMemberPointsConfigTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS hl_member_points_config (" +
                        "id BIGINT PRIMARY KEY," +
                        "daily_check_in_points INT DEFAULT 10," +
                        "created_at DATETIME," +
                        "updated_at DATETIME" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    private void ensureDefaultConfig() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + MEMBER_POINTS_CONFIG_TABLE + " WHERE id = 1",
                Integer.class
        );
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO " + MEMBER_POINTS_CONFIG_TABLE + " (id, daily_check_in_points, created_at, updated_at) " +
                            "VALUES (1, 10, NOW(), NOW())"
            );
            return;
        }
        jdbcTemplate.update(
                "UPDATE " + MEMBER_POINTS_CONFIG_TABLE + " " +
                        "SET daily_check_in_points = 10, updated_at = NOW() " +
                        "WHERE id = 1 AND daily_check_in_points IS NULL"
        );
    }

    private void ensureMemberCheckInLogTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS hl_member_check_in_log (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "user_id BIGINT NOT NULL," +
                        "check_in_date DATE NOT NULL," +
                        "earned_points INT DEFAULT 0," +
                        "created_at DATETIME," +
                        "UNIQUE KEY uk_member_check_in (user_id, check_in_date)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    private void ensureCouponExchangeColumns() {
        if (!tableExists(COUPON_TABLE)) {
            return;
        }
        ensureColumn(COUPON_TABLE, "exchange_enabled", "INT DEFAULT 0 COMMENT 'whether coupon can be exchanged with points'");
        ensureColumn(COUPON_TABLE, "exchange_points", "INT DEFAULT 0 COMMENT 'points needed for coupon exchange'");
        jdbcTemplate.update("UPDATE " + COUPON_TABLE + " SET exchange_enabled = 0 WHERE exchange_enabled IS NULL");
        jdbcTemplate.update("UPDATE " + COUPON_TABLE + " SET exchange_points = 0 WHERE exchange_points IS NULL");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + ddl);
    }
}
