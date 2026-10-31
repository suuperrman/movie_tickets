-- ============================================================
-- 电影评价 / 评分系统 迁移脚本
-- 库：movie_ticket
-- 说明：执行一次即可，重复执行安全（IF NOT EXISTS）
-- ============================================================

CREATE TABLE IF NOT EXISTS `movie_review` (
    `id`         BIGINT       NOT NULL COMMENT '主键，雪花ID，应用层生成',
    `movie_id`   BIGINT       NOT NULL COMMENT '电影ID，关联 movie.id',
    `user_id`    BIGINT       NOT NULL COMMENT '评价用户ID，关联 sys_user.id',
    `order_id`   BIGINT       DEFAULT NULL COMMENT '关联订单ID，关联 order.id',
    `rating`     TINYINT      NOT NULL COMMENT '评分 1-5 星',
    `content`    VARCHAR(500) NOT NULL DEFAULT '' COMMENT '评价内容',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '审核状态：0待审核 1已通过 2已拒绝',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同一用户对同一部电影只能评价一次
    UNIQUE KEY `uk_movie_user` (`movie_id`, `user_id`),
    -- 电影详情页按 movie_id + status 查评价列表 / 聚合评分
    KEY `idx_movie_status` (`movie_id`, `status`),
    -- 管理端按状态 + 时间倒序分页
    KEY `idx_status_created` (`status`, `created_at`),
    -- 我的评价按 user_id + 时间倒序
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='电影评价表';
