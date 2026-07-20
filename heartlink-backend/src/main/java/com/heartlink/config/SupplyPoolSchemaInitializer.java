package com.heartlink.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupplyPoolSchemaInitializer implements ApplicationRunner {

    private static final Map<String, DemoSupplierProfile> BROKEN_DEMO_SUPPLIERS = Map.of(
            "SUP-001", new DemoSupplierProfile("康养甄选供应链", "CURATED_SUPPLIER", "张敏", "13810000001", "MONTHLY"),
            "SUP-002", new DemoSupplierProfile("安颐营养品牌", "BRAND_DIRECT", "李雯", "13810000002", "PREPAID"),
            "SUP-003", new DemoSupplierProfile("乐龄生活渠道", "CHANNEL_PARTNER", "王超", "13810000003", "MANUAL"),
            "SUP-004", new DemoSupplierProfile("暖护居家精选", "CURATED_SUPPLIER", "陈洁", "13810000004", "MONTHLY")
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureSupplierTable();
            ensureSupplierColumns();
            repairBrokenDemoSupplierData();
            ensureProductSourceTable();
            ensureProductColumns();
            ensureOrderColumns();
        } catch (Exception e) {
            log.warn("failed to initialize supply pool schema compatibility", e);
        }
    }

    private void ensureSupplierTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS hl_supplier (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "name VARCHAR(100) NOT NULL," +
                        "code VARCHAR(50) NOT NULL," +
                        "type VARCHAR(30) DEFAULT 'CURATED_SUPPLIER'," +
                        "status INT DEFAULT 1," +
                        "contact_name VARCHAR(50)," +
                        "contact_phone VARCHAR(20)," +
                        "settlement_mode VARCHAR(30) DEFAULT 'MANUAL'," +
                        "default_tax_rate DECIMAL(5,2)," +
                        "api_config_json LONGTEXT," +
                        "remark TEXT," +
                        "created_at DATETIME," +
                        "updated_at DATETIME," +
                        "deleted INT DEFAULT 0," +
                        "UNIQUE KEY uk_supplier_code (code)," +
                        "KEY idx_supplier_status (status)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    private void repairBrokenDemoSupplierData() {
        BROKEN_DEMO_SUPPLIERS.forEach((code, profile) -> {
            int updated = jdbcTemplate.update(
                    "UPDATE hl_supplier SET name = ?, type = ?, contact_name = ?, contact_phone = ?, settlement_mode = ?, updated_at = NOW() " +
                            "WHERE code = ? AND deleted = 0 AND (" +
                            "name IS NULL OR TRIM(name) = '' OR REPLACE(name, '?', '') = '' OR " +
                            "contact_name IS NULL OR TRIM(contact_name) = '' OR REPLACE(contact_name, '?', '') = '' OR " +
                            "type IS NULL OR TRIM(type) = '' OR settlement_mode IS NULL OR TRIM(settlement_mode) = '')",
                    profile.name(),
                    profile.type(),
                    profile.contactName(),
                    profile.contactPhone(),
                    profile.settlementMode(),
                    code
            );
            if (updated > 0) {
                log.info("repaired supplier demo row for code={}", code);
            }
        });
    }

    private void ensureSupplierColumns() {
        if (!tableExists("hl_supplier")) {
            return;
        }
        ensureColumn("hl_supplier", "default_tax_rate", "DECIMAL(5,2) NULL COMMENT 'default tax rate'");
    }

    private void ensureProductSourceTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS hl_product_source (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "product_id BIGINT NOT NULL," +
                        "supplier_id BIGINT," +
                        "source_type VARCHAR(30) DEFAULT 'CURATED_SUPPLIER'," +
                        "external_spu_id VARCHAR(100)," +
                        "external_sku_id VARCHAR(100)," +
                        "source_url VARCHAR(500)," +
                        "purchase_price DECIMAL(10,2)," +
                        "commission_rate DECIMAL(10,2)," +
                        "sync_status VARCHAR(30)," +
                        "stock_mode VARCHAR(30)," +
                        "delivery_mode VARCHAR(30)," +
                        "raw_payload_json LONGTEXT," +
                        "created_at DATETIME," +
                        "updated_at DATETIME," +
                        "deleted INT DEFAULT 0," +
                        "KEY idx_product_source_product (product_id)," +
                        "KEY idx_product_source_supplier (supplier_id)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    private void ensureProductColumns() {
        if (!tableExists("hl_product")) {
            return;
        }
        ensureColumn("hl_product", "supplier_id", "BIGINT NULL COMMENT 'supplier id'");
        ensureColumn("hl_product", "source_type", "VARCHAR(30) NULL COMMENT 'source type'");
        ensureColumn("hl_product", "external_sku_id", "VARCHAR(100) NULL COMMENT 'external sku id'");
        ensureColumn("hl_product", "source_status", "VARCHAR(30) NULL COMMENT 'source status'");
        ensureColumn("hl_product", "purchase_price", "DECIMAL(10,2) NULL COMMENT 'purchase price'");
        ensureColumn("hl_product", "gross_margin", "DECIMAL(10,2) NULL COMMENT 'gross margin'");
        ensureColumn("hl_product", "tax_rate_mode", "VARCHAR(30) NULL COMMENT 'tax rate mode'");
        ensureColumn("hl_product", "tax_rate", "DECIMAL(5,2) NULL COMMENT 'tax rate'");
        ensureColumn("hl_product", "delivery_mode", "VARCHAR(30) NULL COMMENT 'delivery mode'");
    }

    private void ensureOrderColumns() {
        if (!tableExists("hl_order")) {
            return;
        }
        ensureColumn("hl_order", "supplier_id", "BIGINT NULL COMMENT 'supplier id'");
        ensureColumn("hl_order", "supplier_name", "VARCHAR(100) NULL COMMENT 'supplier name'");
        ensureColumn("hl_order", "source_type", "VARCHAR(30) NULL COMMENT 'source type'");
        ensureColumn("hl_order", "external_sku_id", "VARCHAR(100) NULL COMMENT 'external sku id'");
        ensureColumn("hl_order", "settlement_mode", "VARCHAR(30) NULL COMMENT 'settlement mode'");
        ensureColumn("hl_order", "settlement_tax_rate_snapshot", "DECIMAL(5,2) NULL COMMENT 'settlement tax rate snapshot'");
        ensureColumn("hl_order", "purchase_price_snapshot", "DECIMAL(10,2) NULL COMMENT 'purchase price snapshot'");
        ensureColumn("hl_order", "settlement_tax_source", "VARCHAR(30) NULL COMMENT 'settlement tax source'");
        ensureColumn("hl_order", "procurement_status", "VARCHAR(30) NULL COMMENT 'procurement status'");
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

    private record DemoSupplierProfile(
            String name,
            String type,
            String contactName,
            String contactPhone,
            String settlementMode
    ) {
    }
}
