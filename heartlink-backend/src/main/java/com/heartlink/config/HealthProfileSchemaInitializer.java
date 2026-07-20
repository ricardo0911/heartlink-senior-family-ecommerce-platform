package com.heartlink.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthProfileSchemaInitializer implements ApplicationRunner {

    private static final String TABLE_NAME = "hl_health_profile";
    private static final String PARENT_INDEX_NAME = "idx_health_profile_parent_id";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!tableExists(TABLE_NAME)) {
                return;
            }
            ensureColumn(TABLE_NAME, "profile_name", "VARCHAR(50)");
            ensureColumn(TABLE_NAME, "relation", "VARCHAR(30)");
            ensureColumn(TABLE_NAME, "is_primary", "INT DEFAULT 1");
            ensureColumn(TABLE_NAME, "sort_order", "INT DEFAULT 0");
            relaxUniqueParentConstraint();
            ensureIndex(TABLE_NAME, PARENT_INDEX_NAME, "(parent_id)");
            backfillDefaults();
        } catch (Exception e) {
            log.warn("failed to initialize health profile schema compatibility", e);
        }
    }

    private void relaxUniqueParentConstraint() {
        List<String> indexNames = jdbcTemplate.queryForList(
                "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'parent_id' " +
                        "AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'",
                String.class,
                TABLE_NAME
        );
        for (String indexName : indexNames) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " DROP INDEX `" + indexName + "`");
        }
    }

    private void backfillDefaults() {
        jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET profile_name = '本人' WHERE profile_name IS NULL OR TRIM(profile_name) = ''");
        jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET relation = '本人' WHERE relation IS NULL OR TRIM(relation) = ''");
        jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET is_primary = 1 WHERE is_primary IS NULL");
        jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET sort_order = CASE WHEN is_primary = 1 THEN 0 ELSE 1 END WHERE sort_order IS NULL");
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

    private void ensureIndex(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD INDEX " + indexName + " " + ddl);
    }
}
