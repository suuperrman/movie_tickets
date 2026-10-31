-- ============================================================
-- 用户退款申请流程 迁移脚本
-- 库：movie_ticket
-- 说明：MySQL 的 ALTER TABLE 不支持 IF NOT EXISTS，仅执行一次
-- ============================================================

ALTER TABLE `order`
    ADD COLUMN `refund_reason`   VARCHAR(200) DEFAULT NULL COMMENT '退款原因（用户申请或管理员填写）',
    ADD COLUMN `refund_apply_at` DATETIME     DEFAULT NULL COMMENT '退款申请时间',
    ADD COLUMN `refund_audit_at` DATETIME     DEFAULT NULL COMMENT '退款审核时间';
