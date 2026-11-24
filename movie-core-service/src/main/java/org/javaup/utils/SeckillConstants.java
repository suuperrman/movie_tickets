package org.javaup.utils;

/**
 * 秒杀相关常量
 */
public final class SeckillConstants {

    private SeckillConstants() {
    }

    /** 秒杀订单处理状态缓存前缀，key = 前缀 + orderId */
    public static final String ORDER_STATUS_KEY_PREFIX = "seckill:order:status:";

    /** 已进入异步队列，等待落库 */
    public static final String ORDER_STATUS_PENDING = "PENDING";

    /** 订单已落库 */
    public static final String ORDER_STATUS_SUCCESS = "SUCCESS";

    /** 订单不存在 */
    public static final String ORDER_STATUS_NOT_FOUND = "NOT_FOUND";

    /** 状态缓存有效期（分钟） */
    public static final long ORDER_STATUS_TTL_MINUTES = 5L;
}
