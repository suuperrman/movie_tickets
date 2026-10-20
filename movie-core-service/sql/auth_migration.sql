-- ====================================================================
-- 登录模块数据库迁移脚本
-- 为 sys_user 表添加 email 和 password_changed_at 字段
-- 前置条件：sys_user 表已存在
-- ====================================================================

-- 1. 添加 email 字段（用于忘记密码功能）
ALTER TABLE sys_user 
    ADD COLUMN email VARCHAR(128) DEFAULT NULL COMMENT '邮箱（用于忘记密码/重置密码）' 
    AFTER avatar;

-- 2. 添加 password_changed_at 字段（用于修改密码后使所有已签发令牌失效）
ALTER TABLE sys_user 
    ADD COLUMN password_changed_at DATETIME DEFAULT NULL COMMENT '密码最后修改时间（用于令牌失效机制）' 
    AFTER status;

-- 3. 为 phone 字段添加唯一索引（确保手机号不重复）
-- 如果 phone 没有唯一约束，建议添加
-- ALTER TABLE sys_user ADD UNIQUE INDEX uk_sys_user_phone (phone);

-- ====================================================================
-- 新增辅助表（如需要）
-- ====================================================================

-- 用户信息表（如果 tb_user_info 不存在，需要先创建）
CREATE TABLE IF NOT EXISTS tb_user_info (
    user_id BIGINT PRIMARY KEY COMMENT '用户ID',
    level INT DEFAULT 1 COMMENT '会员等级',
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会员等级表';

-- ====================================================================
-- 初始化示例数据（可选）
-- ====================================================================
-- 插入一个测试账号（密码：Test1234，BCrypt cost=12 加密后）
-- INSERT INTO sys_user (id, phone, password, nickname, status, password_changed_at, created_at, updated_at)
-- VALUES (1, '13800138000', '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '测试用户', 1, NOW(), NOW(), NOW());
