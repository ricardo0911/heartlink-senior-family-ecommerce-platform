-- HeartLink 数据库建表脚本
-- 生成时间: 2026-02-22

CREATE DATABASE IF NOT EXISTS heartlink DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE heartlink;

-- 用户表
CREATE TABLE IF NOT EXISTS hl_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20) UNIQUE,
    password VARCHAR(100),
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    role VARCHAR(20) COMMENT 'CHILD-子女, PARENT-长辈, ADMIN-管理员',
    openid VARCHAR(100),
    points INT DEFAULT 0,
    credit_score INT DEFAULT 100 COMMENT '退款信用分 0-100',
    member_level INT DEFAULT 0 COMMENT '0-普通, 1-银卡, 2-金卡, 3-钻石',
    sos_contacts TEXT COMMENT '紧急联系人JSON',
    status INT DEFAULT 1 COMMENT '0-禁用, 1-正常',
    created_at DATETIME,
    updated_at DATETIME,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hl_supplier (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    type VARCHAR(30) DEFAULT 'CURATED_SUPPLIER',
    status INT DEFAULT 1,
    contact_name VARCHAR(50),
    contact_phone VARCHAR(20),
    settlement_mode VARCHAR(30) DEFAULT 'MANUAL',
    default_tax_rate DECIMAL(5,2),
    api_config_json LONGTEXT,
    remark TEXT,
    created_at DATETIME,
    updated_at DATETIME,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_supplier_code (code),
    KEY idx_supplier_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hl_product_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    supplier_id BIGINT,
    source_type VARCHAR(30) DEFAULT 'CURATED_SUPPLIER',
    external_spu_id VARCHAR(100),
    external_sku_id VARCHAR(100),
    source_url VARCHAR(500),
    purchase_price DECIMAL(10,2),
    commission_rate DECIMAL(10,2),
    sync_status VARCHAR(30),
    stock_mode VARCHAR(30),
    delivery_mode VARCHAR(30),
    raw_payload_json LONGTEXT,
    created_at DATETIME,
    updated_at DATETIME,
    deleted INT DEFAULT 0,
    KEY idx_product_source_product (product_id),
    KEY idx_product_source_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商品分类表
CREATE TABLE IF NOT EXISTS hl_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    icon VARCHAR(500),
    parent_id BIGINT DEFAULT 0,
    sort INT DEFAULT 0,
    status INT DEFAULT 1,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商品表
CREATE TABLE IF NOT EXISTS hl_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    category_id BIGINT,
    price DECIMAL(10,2),
    original_price DECIMAL(10,2),
    images JSON COMMENT '图片列表',
    description TEXT,
    detail LONGTEXT COMMENT '富文本详情',
    specs JSON COMMENT '规格参数',
    health_tags JSON COMMENT '健康标签',
    warning_tags JSON COMMENT '警告标签',
    reviews_summary TEXT,
    voice_description TEXT,
    stock INT DEFAULT 0,
    sales INT DEFAULT 0,
    platform_source VARCHAR(20) DEFAULT 'LOCAL',
    platform_url VARCHAR(500),
    platform_stats JSON,
    supplier_id BIGINT,
    source_type VARCHAR(30) DEFAULT 'SELF_OPERATED',
    external_sku_id VARCHAR(100),
    source_status VARCHAR(30) DEFAULT 'ACTIVE',
    purchase_price DECIMAL(10,2),
    gross_margin DECIMAL(10,2),
    tax_rate_mode VARCHAR(30),
    tax_rate DECIMAL(5,2),
    delivery_mode VARCHAR(30) DEFAULT 'PLATFORM_SHIP',
    ai_recommend_reason TEXT,
    status INT DEFAULT 1 COMMENT '0-下架, 1-上架',
    created_at DATETIME,
    updated_at DATETIME,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 家庭绑定表
CREATE TABLE IF NOT EXISTS hl_family_bind (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    child_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL,
    bind_code VARCHAR(10),
    relation VARCHAR(20) COMMENT '妈妈/爸爸/奶奶等',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE',
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收货地址表
CREATE TABLE IF NOT EXISTS hl_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(50),
    receiver_phone VARCHAR(20),
    province VARCHAR(50),
    city VARCHAR(50),
    district VARCHAR(50),
    detail_address VARCHAR(200),
    is_default INT DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单表
CREATE TABLE IF NOT EXISTS hl_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(50) UNIQUE,
    parent_id BIGINT,
    child_id BIGINT,
    product_id BIGINT,
    product_name VARCHAR(200),
    product_image VARCHAR(500),
    product_spec VARCHAR(200),
    supplier_id BIGINT,
    supplier_name VARCHAR(100),
    source_type VARCHAR(30),
    external_sku_id VARCHAR(100),
    settlement_mode VARCHAR(30),
    settlement_tax_rate_snapshot DECIMAL(5,2),
    purchase_price_snapshot DECIMAL(10,2),
    settlement_tax_source VARCHAR(30),
    procurement_status VARCHAR(30),
    quantity INT DEFAULT 1,
    price DECIMAL(10,2),
    total_amount DECIMAL(10,2),
    receiver_name VARCHAR(50),
    receiver_phone VARCHAR(20),
    receiver_address VARCHAR(300),
    greeting_card TEXT COMMENT 'AI情感贺卡',
    status VARCHAR(30) DEFAULT 'PENDING_PAY',
    paid_at DATETIME,
    shipped_at DATETIME,
    express_company_code VARCHAR(50) COMMENT '快递公司编码',
    express_company_name VARCHAR(100) COMMENT '快递公司名称',
    tracking_no VARCHAR(100) COMMENT '物流单号',
    logistics_provider VARCHAR(30) COMMENT '物流服务商',
    logistics_status VARCHAR(30) COMMENT '物流状态编码',
    logistics_status_text VARCHAR(100) COMMENT '物流状态描述',
    logistics_last_trace VARCHAR(500) COMMENT '最新物流轨迹',
    logistics_trace_json LONGTEXT COMMENT '物流轨迹JSON',
    logistics_updated_at DATETIME COMMENT '物流更新时间',
    completed_at DATETIME,
    refund_status VARCHAR(20) DEFAULT 'NONE',
    refund_reason TEXT,
    refund_images LONGTEXT COMMENT '退款凭证图片JSON',
    refund_credit_score INT DEFAULT NULL COMMENT '退款发起时的信用分',
    refund_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 优惠券表
CREATE TABLE IF NOT EXISTS hl_coupon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100),
    type VARCHAR(20) COMMENT 'FIXED-固定金额, PERCENT-折扣',
    value DECIMAL(10,2),
    min_amount DECIMAL(10,2) DEFAULT 0,
    category_id BIGINT,
    start_time DATETIME,
    end_time DATETIME,
    total_count INT,
    remain_count INT,
    status INT DEFAULT 1,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户优惠券表
CREATE TABLE IF NOT EXISTS hl_user_coupon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    order_id BIGINT,
    status VARCHAR(20) DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/EXPIRED',
    received_at DATETIME,
    used_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 代付请求表
CREATE TABLE IF NOT EXISTS hl_payment_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT,
    parent_id BIGINT,
    child_id BIGINT,
    product_name VARCHAR(200),
    product_image VARCHAR(500),
    amount DECIMAL(10,2),
    message TEXT,
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/PAID/REJECTED/EXPIRED',
    paid_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
CREATE TABLE IF NOT EXISTS hl_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id BIGINT,
    receiver_id BIGINT,
    family_bind_id BIGINT,
    content TEXT,
    type VARCHAR(20) COMMENT 'TEXT/VOICE/IMAGE',
    voice_url VARCHAR(500),
    image_url VARCHAR(500),
    is_read INT DEFAULT 0,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 健康档案表
CREATE TABLE IF NOT EXISTS hl_health_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    profile_name VARCHAR(50),
    relation VARCHAR(30),
    is_primary INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    age INT,
    height DOUBLE,
    weight DOUBLE,
    chronic_diseases JSON,
    allergies JSON,
    hobbies JSON,
    clothing_size VARCHAR(10),
    shoe_size INT,
    preferences TEXT,
    updated_at DATETIME,
    KEY idx_health_profile_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 健康提醒表
CREATE TABLE IF NOT EXISTS hl_health_reminder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    child_id BIGINT,
    title VARCHAR(100),
    content TEXT,
    reminder_time TIME,
    repeat_type VARCHAR(20) COMMENT 'DAILY/WEEKLY/ONCE',
    week_days VARCHAR(20),
    enabled INT DEFAULT 1,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商品评价表
CREATE TABLE IF NOT EXISTS hl_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT,
    user_id BIGINT,
    order_id BIGINT,
    content TEXT,
    voice_url VARCHAR(500),
    rating INT,
    images JSON,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 智能货架表
CREATE TABLE IF NOT EXISTS hl_smart_shelf (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    child_id BIGINT,
    parent_id BIGINT,
    product_id BIGINT,
    voice_message TEXT,
    reaction VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/LIKE/DISLIKE',
    remark TEXT,
    created_at DATETIME,
    viewed_at DATETIME,
    reacted_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入管理员账号 (密码: admin123 的MD5)
INSERT INTO hl_user (phone, password, nickname, role, status, created_at, updated_at, deleted)
VALUES ('admin', '0192023a7bbd73250516f069df18b500', '系统管理员', 'ADMIN', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE password = '0192023a7bbd73250516f069df18b500';
