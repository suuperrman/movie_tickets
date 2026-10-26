package org.javaup.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.order.*;
import org.javaup.entity.*;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.*;
import org.javaup.core.RedisKeyManage;
import org.javaup.redis.RedisCache;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.BloomFilterService;
import org.javaup.service.OrderService;
import org.javaup.service.RateLimitService;
import org.javaup.utils.MovieCacheEvictHelper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单模块服务实现
 *
 * 核心业务：订单状态 × 座位状态 的联动
 * ┌──────────────┬─────────────────────────────────────────┐
 * │ 动作          │ schedule_seat.status 变化                │
 * ├──────────────┼─────────────────────────────────────────┤
 * │ 下单（锁座）   │ 0（可售） → 1（锁定）                     │
 * │ 支付成功      │ 1（锁定） → 2（已售）                     │
 * │ 待支付取消     │ 1（锁定） → 0（可售），availableSeat +N   │
 * │ 已支付退款     │ 2（已售） → 0（可售），availableSeat +N   │
 * └──────────────┴─────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final SysUserMapper sysUserMapper;
    private final SeatMapper seatMapper;
    private final RedisCache redisCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final BloomFilterService bloomFilterService;
    private final MovieCacheEvictHelper cacheEvictHelper;

    /** 座位锁定超时（毫秒）：15 分钟 */
    private static final long SEAT_LOCK_EXPIRE_MS = 15 * 60 * 1000L;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 清除场次座位图缓存 —— 事务提交后再删！
     *
     * 旧实现（直接调 redisCache.del）有经典「先删缓存后提交事务」Race Condition：
     *   UPDATE seat SET status=1  ← DB 写但还没 COMMIT
     *   del(cache)                ← 先删！
     *   ┌─ 并发 seatMap() → cache MISS → SELECT FROM DB（READ_COMMITTED）
     *   │   → 读到 OLD status=0（事务没提交！）→ 把脏状态写回缓存！
     *   │   → 新用户进来看到"可售" → 撞车！
     *   └─ COMMIT                 ← 但缓存已经脏了！永久错误！
     *
     * 修复：注册 TransactionSynchronization.afterCommit() 回调
     *   事务 COMMIT 后再 DEL cache → 并发 seatMap 进来要么 HIT（还没 DEL）
     *   要么 MISS（DB 已提交）→ 两种情况都正确
     *
     * 兼容：非事务上下文（单测/手动调用）直接清
     */
    private void evictSeatMapCache(Long scheduleId) {
        if (scheduleId == null) return;
        String cacheKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_MAP_KEY, scheduleId).getRelKey();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 在 Spring @Transactional 里 → 注册提交后回调
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doDelCache(cacheKey);
                }
            });
        } else {
            // 不在事务中 → 直接清
            doDelCache(cacheKey);
        }
    }

    /** 实际执行 Redis DEL（try-catch 吞掉异常，Redis 挂了不影响主流程） */
    private void doDelCache(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
            log.info("[缓存失效] seatmap key={}", cacheKey);
        } catch (Exception e) {
            log.warn("[缓存失效失败] seatmap key={}: {}", cacheKey, e.getMessage());
        }
    }

    /** 在 Redis ZSET 中投递锁定超时延时任务（score=到期毫秒时间戳） */
    private void scheduleLockTimeout(Long scheduleId, String seatId, Long orderId, long expireMs) {
        try {
            String member = seatId + ":" + orderId;
            double score = (double) expireMs;
            RedisKeyBuild zsetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_LOCK_ZSET, scheduleId);
            redisCache.addForSortedSet(zsetKey, member, score);
            log.debug("[锁座延时队列] scheduleId={} member={} expireAt={}", scheduleId, member, new Date(expireMs));
        } catch (Exception e) {
            log.warn("[锁座延时队列失败] scheduleId={}: {}", scheduleId, e.getMessage());
        }
    }

    // ==================== 用户端 1. 创建订单（锁座） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order create(Long userId, OrderCreateDTO dto) {
        // ===== 幂等防重：同一用户10秒内不能对同一场次相同座位重复下单 =====
        String seatIdsHash = cn.hutool.core.util.StrUtil.join(",", dto.getSeatIds().stream().sorted().toArray());
        String idempotentKey = RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_CREATE_IDEMPOTENT_KEY,
                userId, dto.getScheduleId(), cn.hutool.crypto.SecureUtil.md5(seatIdsHash)).getRelKey();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 10, java.util.concurrent.TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            throw new MovieFrameException("请勿重复提交订单");
        }

        // ===== 布隆过滤器防穿透：场次ID不存在直接返回 =====
        if (!bloomFilterService.containsScheduleId(dto.getScheduleId())) {
            throw new MovieFrameException("场次不存在");
        }

        // ===== 座位图Redis前置过滤：缓存中座位已占用则快速失败，不查DB =====
        try {
            RedisKeyBuild seatMapKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_MAP_KEY, dto.getScheduleId());
            org.javaup.dto.movie.SeatMapVO cachedSeatMap = redisCache.get(seatMapKey, org.javaup.dto.movie.SeatMapVO.class);
            if (cachedSeatMap != null && cachedSeatMap.getSeatIds() != null && cachedSeatMap.getSeatStatusBitmap() != null) {
                Map<Long, Integer> seatIndexMap = new HashMap<>();
                for (int i = 0; i < cachedSeatMap.getSeatIds().size(); i++) {
                    seatIndexMap.put(Long.valueOf(cachedSeatMap.getSeatIds().get(i)), i);
                }
                for (Long seatId : dto.getSeatIds()) {
                    Integer idx = seatIndexMap.get(seatId);
                    if (idx != null) {
                        int status = org.javaup.utils.SeatBitmapUtil.getSeatStatus(cachedSeatMap.getSeatStatusBitmap(), idx);
                        if (status != 0) {
                            throw new MovieFrameException("座位已被占用，请刷新后重选");
                        }
                    }
                }
            }
        } catch (MovieFrameException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[座位图前置过滤失败，继续走DB校验] scheduleId={}: {}", dto.getScheduleId(), e.getMessage());
        }

        Schedule schedule = scheduleMapper.selectById(dto.getScheduleId());
        if (schedule == null) throw new MovieFrameException("场次不存在");
        if (schedule.getStatus() != 1) throw new MovieFrameException("该场次已停售");
        // ★ 过期场次也不允许下单（防手改 URL 绕过）
        if (schedule.getEndTime() != null && schedule.getEndTime().before(new java.util.Date())) {
            throw new MovieFrameException("场次已结束，无法购票");
        }
        if (dto.getSeatIds().size() > schedule.getAvailableSeat()) {
            throw new MovieFrameException("可选座位不足");
        }

        // 查询所选座位，必须全部可售
        List<ScheduleSeat> seats = scheduleSeatMapper.selectBatchIds(dto.getSeatIds());
        if (seats.size() != dto.getSeatIds().size()) {
            throw new MovieFrameException("部分座位不存在");
        }

        // 批量从物理座位表拿 seatCode（非持久化字段，用于日志和 order_item 明细）
        List<Long> seatIds = seats.stream().map(ScheduleSeat::getSeatId).distinct().collect(Collectors.toList());
        Map<Long, String> seatCodeMap = seatMapper.selectBatchIds(seatIds).stream()
                .collect(Collectors.toMap(Seat::getId, Seat::getSeatCode));
        seats.forEach(s -> s.setSeatCode(seatCodeMap.getOrDefault(s.getSeatId(), "")));

        for (ScheduleSeat seat : seats) {
            if (!seat.getScheduleId().equals(dto.getScheduleId())) {
                throw new MovieFrameException("部分座位不属于该场次");
            }
            if (seat.getStatus() != 0) {
                throw new MovieFrameException("座位 " + seat.getSeatCode() + " 已被占用");
            }
        }

        // 一次性锁座（乐观锁校验 status=0）
        Date now = new Date();
        for (ScheduleSeat seat : seats) {
            int rows = scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                    .eq(ScheduleSeat::getId, seat.getId())
                    .eq(ScheduleSeat::getStatus, 0)
                    .set(ScheduleSeat::getStatus, 1)
                    .set(ScheduleSeat::getLockUserId, userId)
                    .set(ScheduleSeat::getLockExpireAt, Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant())));
            if (rows == 0) {
                throw new MovieFrameException("座位 " + seat.getSeatCode() + " 抢购失败，请重试");
            }
        }

        // 计算总额
        BigDecimal totalAmount = seats.stream().map(ScheduleSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 创建订单
        Order order = new Order();
        order.setId(IdUtil.getSnowflakeNextId());
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setScheduleId(dto.getScheduleId());
        order.setMovieId(schedule.getMovieId());
        order.setCinemaId(schedule.getCinemaId());
        order.setSeatCount(seats.size());
        order.setTotalAmount(totalAmount);
        order.setDiscount(BigDecimal.ZERO);
        order.setPayAmount(totalAmount);
        order.setStatus(0); // 待支付
        order.setExpireAt(Date.from(LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant()));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        save(order);

        // 批量插入 order_item
        for (ScheduleSeat seat : seats) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setScheduleSeatId(seat.getId());
            item.setSeatCode(seat.getSeatCode());
            item.setRowNo(seat.getRowNo());
            item.setColNo(seat.getColNo());
            item.setPrice(seat.getPrice());
            item.setCreatedAt(now);
            orderItemMapper.insert(item);
        }

        // 更新 schedule.availableSeat
        scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, dto.getScheduleId())
                .setSql("available_seat = available_seat - " + seats.size()));

        // ★ 清除座位图缓存（订单操作必失效）
        evictSeatMapCache(dto.getScheduleId());

        // ★ Redis ZSET 投递 15 分钟后自动释放的延时任务
        long expireMs = System.currentTimeMillis() + SEAT_LOCK_EXPIRE_MS;
        for (ScheduleSeat seat : seats) {
            scheduleLockTimeout(dto.getScheduleId(), String.valueOf(seat.getId()), order.getId(), expireMs);
        }

        log.info("创建订单 orderNo={} userId={} scheduleId={} seatCount={}", order.getOrderNo(), userId, dto.getScheduleId(), seats.size());
        return order;
    }

    // ==================== 用户端 2. 我的订单列表 ====================

    @Override
    public Page<UserOrderListVO> myOrders(Long userId, UserOrderPageQuery query) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        if (query.getStatus() != null) wrapper.eq(Order::getStatus, query.getStatus());
        wrapper.orderByDesc(Order::getCreatedAt);

        Page<Order> entityPage = page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        Page<UserOrderListVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(buildUserOrderList(entityPage.getRecords()));
        return voPage;
    }

    // ==================== 用户端 3. 订单详情 ====================

    @Override
    public OrderDetailVO userDetail(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null) throw new MovieFrameException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new MovieFrameException("无权访问");
        return buildDetail(order);
    }

    // ==================== 用户端 4. 取消订单（释放锁座） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void userCancel(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null) throw new MovieFrameException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new MovieFrameException("无权操作");
        if (order.getStatus() != 0) throw new MovieFrameException("只有待支付订单可以取消");

        // 释放锁座（1→0）
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                .in(ScheduleSeat::getId, items.stream().map(OrderItem::getScheduleSeatId).collect(Collectors.toList()))
                .eq(ScheduleSeat::getStatus, 1)
                .set(ScheduleSeat::getStatus, 0)
                // ★ 修复：lockUserId/lockExpireAt 改为 0/NOT NULL 兼容
                .set(ScheduleSeat::getLockUserId, 0L)
                .set(ScheduleSeat::getLockExpireAt, new Date(0)));

        // 加回 availableSeat
        scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, order.getScheduleId())
                .setSql("available_seat = available_seat + " + items.size()));

        // 更新订单状态
        order.setStatus(2);
        order.setCancelTime(new Date());
        order.setUpdatedAt(new Date());
        updateById(order);

        // ★ 清除缓存 + 移除延时队列里的该订单条目（避免被自动解锁消费）
        evictSeatMapCache(order.getScheduleId());
        try {
            RedisKeyBuild zsetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_LOCK_ZSET, order.getScheduleId());
            for (OrderItem item : items) {
                redisCache.delForSortedSet(zsetKey, String.valueOf(item.getScheduleSeatId()) + ":" + order.getId());
            }
        } catch (Exception ignore) {}

        log.info("用户取消订单 orderNo={}", order.getOrderNo());
    }

    // ==================== 用户端 5. 支付成功（锁定→已售） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void userPay(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null) throw new MovieFrameException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new MovieFrameException("无权操作");

        // ===== 支付幂等：订单已支付则直接返回成功，防止回调/前端重复触发 =====
        if (order.getStatus() == 1) {
            log.info("[支付幂等] 订单已支付，直接返回 orderNo={}", order.getOrderNo());
            return;
        }
        if (order.getStatus() != 0) {
            throw new MovieFrameException("订单状态不允许支付");
        }

        // 锁定→已售（1→2）
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                .in(ScheduleSeat::getId, items.stream().map(OrderItem::getScheduleSeatId).collect(Collectors.toList()))
                .eq(ScheduleSeat::getStatus, 1)
                .set(ScheduleSeat::getStatus, 2));

        order.setStatus(1);
        order.setPayTime(new Date());
        order.setUpdatedAt(new Date());
        updateById(order);

        // ★ 清除缓存 + 移除延时队列条目（支付完成无需自动解锁）
        evictSeatMapCache(order.getScheduleId());
        try {
            RedisKeyBuild zsetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_LOCK_ZSET, order.getScheduleId());
            for (OrderItem item : items) {
                redisCache.delForSortedSet(zsetKey, String.valueOf(item.getScheduleSeatId()) + ":" + order.getId());
            }
        } catch (Exception ignore) {}

        log.info("支付成功 orderNo={}", order.getOrderNo());
    }

    // ==================== 用户端 5.5 申请退款（已支付→退款中） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void userApplyRefund(Long userId, Long orderId, String reason) {
        Order order = getById(orderId);
        if (order == null) throw new MovieFrameException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new MovieFrameException("无权操作");

        // 幂等：重复申请直接提示，不产生副作用
        if (order.getStatus() == 3) throw new MovieFrameException("退款申请已提交，请耐心等待审核");
        if (order.getStatus() != 1) throw new MovieFrameException("仅已支付订单可以申请退款");

        Date now = new Date();
        order.setStatus(3);
        order.setRefundReason(cn.hutool.core.util.StrUtil.trimToEmpty(reason));
        order.setRefundApplyAt(now);
        order.setUpdatedAt(now);
        updateById(order);

        log.info("[用户申请退款] orderNo={} userId={} reason={}", order.getOrderNo(), userId, reason);
    }

    // ==================== 管理端 6. 分页查询 ====================

    @Override
    public Page<AdminOrderListVO> adminPage(AdminOrderPageQuery query) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (cn.hutool.core.util.StrUtil.isNotBlank(query.getOrderNo())) wrapper.eq(Order::getOrderNo, query.getOrderNo());
        if (query.getUserId() != null) wrapper.eq(Order::getUserId, query.getUserId());
        if (query.getMovieId() != null) wrapper.eq(Order::getMovieId, query.getMovieId());
        if (query.getCinemaId() != null) wrapper.eq(Order::getCinemaId, query.getCinemaId());
        if (query.getStatus() != null) wrapper.eq(Order::getStatus, query.getStatus());
        if (cn.hutool.core.util.StrUtil.isNotBlank(query.getDate())) {
            LocalDate d = LocalDate.parse(query.getDate());
            Date dayStart = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            wrapper.ge(Order::getCreatedAt, dayStart).lt(Order::getCreatedAt, dayEnd);
        }
        wrapper.orderByDesc(Order::getCreatedAt);

        Page<Order> entityPage = page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        Page<AdminOrderListVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(buildAdminOrderList(entityPage.getRecords()));
        return voPage;
    }

    // ==================== 管理端 7. 详情 ====================

    @Override
    public OrderDetailVO adminDetail(Long orderId) {
        Order order = getById(orderId);
        if (order == null) throw new MovieFrameException("订单不存在");
        return buildDetail(order);
    }

    // ==================== 管理端 8. 退款（已售→可售） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminRefund(Long orderId, String reason) {
        Order order = getById(orderId);
        if (order == null) throw new MovieFrameException("订单不存在");
        // 允许两种入口：已支付(1) 管理员主动退款；退款中(3) 审核同意用户申请
        if (order.getStatus() != 1 && order.getStatus() != 3) {
            throw new MovieFrameException("仅已支付或退款中的订单可以退款");
        }

        // 已售→可售（2→0）
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                .in(ScheduleSeat::getId, items.stream().map(OrderItem::getScheduleSeatId).collect(Collectors.toList()))
                .eq(ScheduleSeat::getStatus, 2)
                .set(ScheduleSeat::getStatus, 0));

        // 加回 availableSeat
        scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, order.getScheduleId())
                .setSql("available_seat = available_seat + " + items.size()));

        Date now = new Date();
        order.setStatus(4);
        order.setCancelTime(now);
        order.setRefundAuditAt(now);
        if (cn.hutool.core.util.StrUtil.isNotBlank(reason)) {
            order.setRefundReason(reason);
        }
        order.setUpdatedAt(now);
        updateById(order);

        // ★ 退款后座位从已售→可售，必须清缓存（旧代码漏掉了！）
        evictSeatMapCache(order.getScheduleId());

        log.info("管理端退款 orderNo={} reason={}", order.getOrderNo(), reason);
    }

    // ==================== 管理端 9. 退款审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminAuditRefund(Long orderId, Boolean approve, String reason) {
        Order order = getById(orderId);
        if (order == null) throw new MovieFrameException("订单不存在");
        if (order.getStatus() != 3) throw new MovieFrameException("仅退款中的订单可以审核");

        if (Boolean.TRUE.equals(approve)) {
            // 同意退款：复用退款逻辑（释放座位 + 状态→已退款）
            adminRefund(orderId, reason);
            return;
        }

        // 拒绝退款：退款中(3) → 已支付(1)，记录审核时间与拒绝原因
        Date now = new Date();
        order.setStatus(1);
        order.setRefundAuditAt(now);
        if (cn.hutool.core.util.StrUtil.isNotBlank(reason)) {
            order.setRefundReason(reason);
        }
        order.setUpdatedAt(now);
        updateById(order);

        log.info("[管理端] 拒绝退款 orderNo={} reason={}", order.getOrderNo(), reason);
    }

    // ==================== 内部辅助 ====================

    private String generateOrderNo() {
        // 时间戳 + 雪花 ID 后 8 位
        String ts = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return ts + IdUtil.getSnowflakeNextIdStr().substring(IdUtil.getSnowflakeNextIdStr().length() - 8);
    }

    /** 批量构建用户端列表项 */
    private List<UserOrderListVO> buildUserOrderList(List<Order> orders) {
        if (orders.isEmpty()) return List.of();
        return orders.stream().map(o -> buildUserOrderItem(o)).collect(Collectors.toList());
    }

    /** 批量构建管理端列表项 */
    private List<AdminOrderListVO> buildAdminOrderList(List<Order> orders) {
        if (orders.isEmpty()) return List.of();

        // 批量查 movie / cinema / user
        List<Long> movieIds = orders.stream().map(Order::getMovieId).distinct().collect(Collectors.toList());
        List<Long> cinemaIds = orders.stream().map(Order::getCinemaId).distinct().collect(Collectors.toList());
        List<Long> userIds = orders.stream().map(Order::getUserId).distinct().collect(Collectors.toList());

        Map<Long, String> movieNameMap = movieMapper.selectBatchIds(movieIds).stream().collect(Collectors.toMap(Movie::getId, Movie::getName));
        Map<Long, String> cinemaNameMap = cinemaMapper.selectBatchIds(cinemaIds).stream().collect(Collectors.toMap(Cinema::getId, Cinema::getName));
        Map<Long, String> userNameMap = sysUserMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));

        return orders.stream().map(o -> AdminOrderListVO.builder()
                .id(o.getId()).orderNo(o.getOrderNo()).userId(o.getUserId())
                .username(userNameMap.getOrDefault(o.getUserId(), ""))
                .status(o.getStatus())
                .movieName(movieNameMap.getOrDefault(o.getMovieId(), ""))
                .cinemaName(cinemaNameMap.getOrDefault(o.getCinemaId(), ""))
                .seatCount(o.getSeatCount()).payAmount(o.getPayAmount())
                .createdAt(format(o.getCreatedAt()))
                .payTime(format(o.getPayTime()))
                .cancelTime(format(o.getCancelTime()))
                .build()).collect(Collectors.toList());
    }

    private UserOrderListVO buildUserOrderItem(Order order) {
        Schedule schedule = scheduleMapper.selectById(order.getScheduleId());
        Movie movie = movieMapper.selectById(order.getMovieId());
        Cinema cinema = cinemaMapper.selectById(order.getCinemaId());
        Hall hall = schedule != null ? hallMapper.selectById(schedule.getHallId()) : null;

        return UserOrderListVO.builder()
                .id(order.getId()).orderNo(order.getOrderNo()).status(order.getStatus())
                .movieId(order.getMovieId())
                .movieName(movie != null ? movie.getName() : "")
                .moviePoster(movie != null ? movie.getPosterUrl() : "")
                .cinemaName(cinema != null ? cinema.getName() : "")
                .hallName(hall != null ? hall.getName() : "")
                .startTime(schedule != null ? format(schedule.getStartTime()) : "")
                .seatCount(order.getSeatCount()).payAmount(order.getPayAmount())
                .createdAt(format(order.getCreatedAt()))
                .payTime(format(order.getPayTime()))
                .build();
    }

    private OrderDetailVO buildDetail(Order order) {
        Schedule schedule = scheduleMapper.selectById(order.getScheduleId());
        Movie movie = movieMapper.selectById(order.getMovieId());
        Cinema cinema = cinemaMapper.selectById(order.getCinemaId());
        Hall hall = schedule != null ? hallMapper.selectById(schedule.getHallId()) : null;
        SysUser user = sysUserMapper.selectById(order.getUserId());

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));

        OrderDetailVO.OrderDetailVOBuilder builder = OrderDetailVO.builder()
                .id(order.getId()).orderNo(order.getOrderNo())
                .userId(order.getUserId()).username(user != null ? user.getNickname() : "")
                .status(order.getStatus())
                .movieId(order.getMovieId())
                .movieName(movie != null ? movie.getName() : "")
                .moviePoster(movie != null ? movie.getPosterUrl() : "")
                .cinemaId(order.getCinemaId())
                .cinemaName(cinema != null ? cinema.getName() : "")
                .cinemaAddress(cinema != null ? cinema.getAddress() : "")
                .hallId(schedule != null ? schedule.getHallId() : null)
                .hallName(hall != null ? hall.getName() : "")
                .hallType(hall != null ? hall.getType() : null)
                .startTime(schedule != null ? format(schedule.getStartTime()) : "")
                .endTime(schedule != null ? format(schedule.getEndTime()) : "")
                .price(schedule != null ? schedule.getPrice() : null)
                .seatCount(order.getSeatCount())
                .totalAmount(order.getTotalAmount()).discount(order.getDiscount()).payAmount(order.getPayAmount())
                .expireAt(format(order.getExpireAt()))
                .payTime(format(order.getPayTime()))
                .cancelTime(format(order.getCancelTime()))
                .refundReason(order.getRefundReason())
                .refundApplyAt(format(order.getRefundApplyAt()))
                .refundAuditAt(format(order.getRefundAuditAt()))
                .createdAt(format(order.getCreatedAt()))
                .items(items.stream().map(i -> OrderDetailVO.OrderItemVO.builder()
                        .seatCode(i.getSeatCode()).rowNo(i.getRowNo()).colNo(i.getColNo()).price(i.getPrice())
                        .build()).collect(Collectors.toList()));
        return builder.build();
    }

    private static String format(Date d) {
        if (d == null) return null;
        return FMT.format(LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()));
    }
}
