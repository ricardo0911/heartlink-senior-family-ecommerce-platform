-- Admin seed data
-- Password is md5 of 'admin123'
-- Run this manually or add to schema initialization
INSERT INTO hl_user (phone, password, nickname, role, status, created_at, updated_at, deleted)
VALUES ('admin', '0192023a7bbd73250516f069df18b500', '系统管理员', 'ADMIN', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE password = '0192023a7bbd73250516f069df18b500';
