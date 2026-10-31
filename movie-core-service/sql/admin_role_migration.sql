-- ============================================================
-- 管理员角色字段迁移
-- 执行时间：2026-10-24
-- ============================================================

-- 1. 给 sys_user 表添加 role 字段
ALTER TABLE sys_user
ADD COLUMN role TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0普通用户 1管理员' AFTER status;

-- 2. 如果需要设置管理员账号，执行以下语句（把手机号改成你的）
-- UPDATE sys_user SET role = 1 WHERE phone = '13800138000';
