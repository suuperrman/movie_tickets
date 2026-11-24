-- ============================================================
-- 秒杀异步下单相关表结构
-- 执行前请确认已连接 movie_ticket 库
-- ============================================================

CREATE TABLE IF NOT EXISTS `seckill_voucher` (
    `id`             BIGINT       NOT NULL COMMENT '主键',
    `voucher_id`     BIGINT       NOT NULL COMMENT '优惠券ID',
    `init_stock`     INT          NOT NULL DEFAULT 0 COMMENT '初始库存',
    `stock`          INT          NOT NULL DEFAULT 0 COMMENT '剩余库存',
    `allowed_levels` VARCHAR(128)          DEFAULT NULL COMMENT '允许的会员等级，逗号分隔',
    `min_level`      INT                   DEFAULT NULL COMMENT '最低会员等级',
    `shop_id`        BIGINT                DEFAULT NULL COMMENT '所属店铺ID',
    `begin_time`     DATETIME              DEFAULT NULL COMMENT '开抢时间',
    `end_time`       DATETIME              DEFAULT NULL COMMENT '结束时间',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架',
    `create_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_voucher_id` (`voucher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '秒杀优惠券';

CREATE TABLE IF NOT EXISTS `seckill_voucher_order` (
    `id`          BIGINT      NOT NULL COMMENT '订单ID（雪花ID，应用层生成）',
    `voucher_id`  BIGINT      NOT NULL COMMENT '优惠券ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `shop_id`     BIGINT               DEFAULT NULL COMMENT '店铺ID',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1已创建 2已支付 3已取消',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_voucher_user` (`voucher_id`, `user_id`) COMMENT '同一用户同一券只能下一单，消费幂等依赖此唯一索引',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_voucher_id` (`voucher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '秒杀优惠券订单';

-- 示例数据：开抢时间为当前时间之前，便于本地联调
INSERT INTO `seckill_voucher` (`id`, `voucher_id`, `init_stock`, `stock`, `begin_time`, `end_time`, `status`, `create_time`)
SELECT 1, 1001, 100, 100, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 7 DAY), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `seckill_voucher` WHERE `voucher_id` = 1001);
