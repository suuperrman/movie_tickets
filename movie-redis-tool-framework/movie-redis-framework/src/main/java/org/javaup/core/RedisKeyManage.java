package org.javaup.core;


import lombok.Getter;

/**
 * @program: movie-ticket
 * @description: redis key管理
 * @author: xiong
 **/
@Getter
public enum RedisKeyManage {
    /**
     * key信息，关于key中为什么使用{}大括号，在星球中有详细的讲解：<a href="https://articles.zsxq.com/id_7k4qtb2mofst.html">...</a>
     * */
    USER_INFO_KEY("user:info:%s","用户id","value为UserInfo类型","k"),
   
    CACHE_SHOP_KEY("cache:shop:%s","商铺id","value为Shop类型","k"),
 
    CACHE_SHOP_KEY_NULL("cache:shop_null:%s","商铺id","value为这是空值","k"),
    
    SECKILL_STOCK_TAG_KEY("seckill:stock:{%s}","秒杀券id（同槽位HashTag）","value为库存","k"),
    
    SECKILL_USER_TAG_KEY("seckill:user:{%s}","秒杀券id（同槽位HashTag）","value为已下单用户集合","k"),
    
    SECKILL_VOUCHER_TAG_KEY("seckill:voucher:{%s}","秒杀券id（同槽位HashTag）","value为SeckillVoucher类型","k"),
    
    SECKILL_VOUCHER_NULL_TAG_KEY("seckill:voucher_null:{%s}","秒杀券id（同槽位HashTag）","value为这是空值","k"),
    
    SECKILL_TRACE_LOG_TAG_KEY("seckill:trace:log:{%s}","秒杀券id（同槽位HashTag）","value为操作记录日志","k"),
    
    SECKILL_LIMIT_IP_TAG_KEY("seckill:limit:ip:{%s}:%s","秒杀券id（同槽位HashTag）","value为按IP的限流计数","k"),
    
    SECKILL_LIMIT_USER_TAG_KEY("seckill:limit:user:{%s}:%s","秒杀券id（同槽位HashTag）","value为按用户的限流计数","k"),
    
    SECKILL_LIMIT_IP_SW_TAG_KEY("seckill:limit:ip:sw:{%s}:%s","秒杀券id（同槽位HashTag）","value为按IP的滑动窗口计数","k"),
    
    SECKILL_LIMIT_USER_SW_TAG_KEY("seckill:limit:user:sw:{%s}:%s","秒杀券id（同槽位HashTag）","value为按用户的滑动窗口计数","k"),
    
    SECKILL_LIMIT_IP_TB_TAG_KEY("seckill:limit:ip:tb:{%s}:%s","秒杀券id（同槽位HashTag）","value为按IP的令牌桶HASH（tokens/last_ms）","k"),

    SECKILL_LIMIT_USER_TB_TAG_KEY("seckill:limit:user:tb:{%s}:%s","秒杀券id（同槽位HashTag）","value为按用户的令牌桶HASH（tokens/last_ms）","k"),
    
    SECKILL_BLOCK_IP_TAG_KEY("seckill:block:ip:{%s}:%s","秒杀券id（同槽位HashTag）","value为按IP的临时封禁标记","k"),
    
    SECKILL_BLOCK_USER_TAG_KEY("seckill:block:user:{%s}:%s","秒杀券id（同槽位HashTag）","value为按用户的临时封禁标记","k"),
    
    SECKILL_VIOLATION_IP_TAG_KEY("seckill:violation:ip:{%s}:%s","秒杀券id（同槽位HashTag）","value为按IP的违规计数","k"),
    
    SECKILL_VIOLATION_USER_TAG_KEY("seckill:violation:user:{%s}:%s","秒杀券id（同槽位HashTag）","value为按用户的违规计数","k"),
   
    DB_SECKILL_ORDER_KEY("db:seckill:order:%s","秒杀券的订单id","value为订单","k"),
    
    SECKILL_ROLLBACK_ALERT_DEDUP_KEY("seckill:rollback:alert:dedup:%s","秒杀券的id","value为1","k"),
    
    SECKILL_AUTO_ISSUE_NOTIFY_DEDUP_KEY("seckill:autoissue:notify:dedup:{%s}:%s","秒杀券id（同槽位HashTag）与用户id","value为1","k"),
    
    SECKILL_REMINDER_NOTIFY_DEDUP_KEY("seckill:reminder:notify:dedup:{%s}:%s","秒杀券id（同槽位HashTag）与用户id","value为1","k"),
    
    SECKILL_ACCESS_TOKEN_TAG_KEY("seckill:access:token:{%s}:%s","秒杀券id（同槽位HashTag）与用户id","访问令牌","k"),
    
    SECKILL_SUBSCRIBE_USER_TAG_KEY("seckill:subscribe:user:{%s}","秒杀券id（同槽位HashTag）","value为订阅用户集合","k"),
    
    SECKILL_SUBSCRIBE_ZSET_TAG_KEY("seckill:subscribe:zset:{%s}","秒杀券id（同槽位HashTag）","value为订阅队列，member为用户id，score为加入时间戳","k"),
    
    SECKILL_SUBSCRIBE_STATUS_TAG_KEY("seckill:subscribe:status:{%s}","秒杀券id（同槽位HashTag）","value为用户订阅状态HASH，field为用户id，value为状态码","k"),
    
    SECKILL_SHOP_TOP_BUYERS_DAILY_TAG_KEY("seckill:shop:topbuyers:daily:{%s}:%s","商铺id（同槽位HashTag）与日期(yyyyMMdd)","ZSET，member为用户id，score为购买次数","k"),
    
    SECKILL_SHOP_TOP_BUYERS_UNION_TAG_KEY("seckill:shop:topbuyers:union:{%s}:%s","商铺id（同槽位HashTag）与聚合范围","临时ZSET，member为用户id，score为购买次数合并","k"),
    
    SECKILL_USER_LEVEL_MEMBERS_TAG_KEY("seckill:user:level:{%s}:members","用户等级（同槽位HashTag）","SET，member为用户id","k"),
    
    SECKILL_USER_LEVEL_MEMBERS_UNION_TAG_KEY("seckill:user:level:union:{%s}","用户等级范围标签","临时SET，member为用户id","k"),

    // ========== 电影购票系统 ==========

    /** 场次座位图缓存（Bitmap+seatIds 整体 JSON），TTL 30s，订单操作时主动失效 */
    SCHEDULE_SEAT_MAP_KEY("movie:schedule:seatmap:%s","场次id","SeatMapVO JSON","k"),

    /** 单座位锁定标记 Hash（field=seatId, value=userId），用于快速判断某座是否锁定 */
    SCHEDULE_SEAT_LOCK_KEY("movie:schedule:seatlock:%s","场次id","HASH field=seatId value=userId","k"),

    /** 锁定超时延时队列（ZSET，member=seatId:orderId，score=到期时间戳） */
    SCHEDULE_SEAT_LOCK_ZSET("movie:schedule:lock:zset:%s","场次id","ZSET member=seatId:orderId score=过期ms","k"),

    /** 用户端电影详情缓存（按 movieId），TTL 30min，电影写操作主动失效 */
    MOVIE_DETAIL_USER_KEY("movie:detail:user:%s","电影id","MovieDetailVO JSON","k"),

    /** 管理端电影详情缓存（按 movieId），TTL 10min，电影写操作主动失效 */
    MOVIE_DETAIL_ADMIN_KEY("movie:detail:admin:%s","电影id","MovieDetailVO JSON","k"),

    /** 正在热映分页缓存（按 pageNo:pageSize），TTL 5min，电影/场次写操作主动失效 */
    MOVIE_NOW_SHOWING_KEY("movie:list:now:%s:%s","页码:页大小","Page<MovieListItemVO> JSON","k"),

    /** 即将上映分页缓存（按 pageNo:pageSize），TTL 5min，电影/场次写操作主动失效 */
    MOVIE_COMING_SOON_KEY("movie:list:coming:%s:%s","页码:页大小","Page<MovieListItemVO> JSON","k"),

    /** 电影搜索分页缓存（按 keyword:categoryId:pageNo:pageSize），TTL 3min，电影/分类写操作主动失效 */
    MOVIE_SEARCH_KEY("movie:search:%s:%s:%s:%s","关键词:分类id:页码:页大小","Page<MovieListItemVO> JSON","k"),

    /** 电影→影院列表缓存（按 movieId:city:date），TTL 1min，场次写操作主动失效 */
    MOVIE_CINEMAS_KEY("movie:cinemas:%s:%s:%s","电影id:城市:日期","List<CinemaScheduleVO> JSON","k"),

    /** 电影+影院→场次列表缓存（按 movieId:cinemaId:date），TTL 30s，场次写操作主动失效 */
    MOVIE_SESSIONS_KEY("movie:sessions:%s:%s:%s","电影id:影院id:日期","List<ScheduleVO> JSON","k"),

    /** 电影分类列表缓存（单 key），TTL 1h，分类写操作主动失效 */
    MOVIE_CATEGORY_LIST_KEY("movie:category:list","-","List<MovieCategory> JSON","k"),

    /** 通用接口限流计数（固定窗口），key=rate:limit:{scene}:{identifier}，value=次数 */
    RATE_LIMIT_KEY("rate:limit:%s:%s","场景:标识","限流计数","k"),

    /** 订单创建幂等防重key，key=order:create:{userId}:{scheduleId}:{seatIdsHash}，value=1，TTL 10s */
    ORDER_CREATE_IDEMPOTENT_KEY("order:create:%s:%s:%s","用户id:场次id:座位id哈希","幂等标记","k"),

    /** 支付回调幂等key，key=order:pay:{orderId}，value=1，TTL 60s */
    ORDER_PAY_IDEMPOTENT_KEY("order:pay:%s","订单id","幂等标记","k"),

    ;

    /**
     * key值
     * */
    private final String key;

    /**
     * key的说明
     * */
    private final String keyIntroduce;

    /**
     * value的说明
     * */
    private final String valueIntroduce;

    /**
     * 作者
     * */
    private final String author;

    RedisKeyManage(String key, String keyIntroduce, String valueIntroduce, String author){
        this.key = key;
        this.keyIntroduce = keyIntroduce;
        this.valueIntroduce = valueIntroduce;
        this.author = author;
    }

    public static RedisKeyManage getRc(String keyCode) {
        for (RedisKeyManage re : RedisKeyManage.values()) {
            if (re.key.equals(keyCode)) {
                return re;
            }
        }
        return null;
    }
    
}
