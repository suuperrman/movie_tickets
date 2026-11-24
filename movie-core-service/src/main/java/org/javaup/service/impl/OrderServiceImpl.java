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
import org.javaup.service.SeatStateService;
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
    private final SeatStateService seatStateService;

    /** 座位锁定超时（毫秒）：15 分钟 */
    private static final long SEAT_LOCK_EXPIRE_MS = 15 * 60 * 1000L;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 下单失败时释放幂等 key，避免用户 10 秒内无法重试 */
    private void releaseIdempotentKey(String idempotentKey) {
        try {
            stringRedisTemplate.delete(idempotentKey);
        } catch (Exception e) {
            log.warn("[幂等key释放失败] key={}: {}", idempotentKey, e.getMessage());
        }
    }

    // ==================== 用户端 1. 创建订单（锁座） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order create(Long userId, OrderCreateDTO dto) {
        // ===== 幂等防重：同一用户10秒内不能对同一场次相同座位重复下单 =====
        String seatPosHash = dto.getSeats().stream()
                .map(p -> p.getRowNo() + ":" + p.getColNo())
                .sorted()
                .collect(Collectors.joining(","));
        String idempotentKey = RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_CREATE_IDEMPOTENT_KEY,
                userId, dto.getScheduleId(), cn.hutool.crypto.SecureUtil.md5(seatPosHash)).getRelKey();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 10, java.util.concurrent.TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            throw new MovieFrameException("请勿重复提交订单");
        }

        Long scheduleId = dto.getScheduleId();
        // ★ 先分配 orderId：座位状态 HASH 里要以订单 id 作为锁归属，便于支付/取消/退款校验
        Long orderId = IdUtil.getSnowflakeNextId();
        long expireMs = System.currentTimeMillis() + SEAT_LOCK_EXPIRE_MS;
        Date expireDate = new Date(expireMs);
        Date now = new Date();
        List<Long> seatIds = new ArrayList<>();
        boolean reserved = false;
        Order order;

        try {
            // ===== 布隆过滤器防穿透：场次ID不存在直接返回 =====
            if (!bloomFilterService.containsScheduleId(scheduleId)) {
                throw new MovieFrameException("场次不存在");
            }

            Schedule schedule = scheduleMapper.selectById(scheduleId);
            if (schedule == null) throw new MovieFrameException("场次不存在");
            if (schedule.getStatus() != 1) throw new MovieFrameException("该场次已停售");
            // ★ 过期场次也不允许下单（防手改 URL 绕过）
            if (schedule.getEndTime() != null && schedule.getEndTime().before(now)) {
                throw new MovieFrameException("场次已结束，无法购票");
            }
            if (dto.getSeats().size() > schedule.getAvailableSeat()) {
                throw new MovieFrameException("可选座位不足");
            }

            // ===== 行列 → schedule_seat：前端不再传座位 ID，按场次内唯一的行列定位 =====
            Set<String> wanted = new LinkedHashSet<>();
            for (OrderCreateDTO.SeatPosition pos : dto.getSeats()) {
                if (!wanted.add(pos.getRowNo() + ":" + pos.getColNo())) {
                    throw new MovieFrameException("座位重复，请刷新后重选");
                }
            }
            Set<Integer> rowNos = dto.getSeats().stream()
                    .map(OrderCreateDTO.SeatPosition::getRowNo).collect(Collectors.toSet());
            Set<Integer> colNos = dto.getSeats().stream()
                    .map(OrderCreateDTO.SeatPosition::getColNo).collect(Collectors.toSet());
            Map<String, ScheduleSeat> seatByPos = scheduleSeatMapper.selectList(
                            new LambdaQueryWrapper<ScheduleSeat>()
                                    .eq(ScheduleSeat::getScheduleId, scheduleId)
                                    .in(ScheduleSeat::getRowNo, rowNos)
                                    .in(ScheduleSeat::getColNo, colNos))
                    .stream()
                    .collect(Collectors.toMap(s -> s.getRowNo() + ":" + s.getColNo(), s -> s, (a, b) -> a));

            List<ScheduleSeat> seats = new ArrayList<>(wanted.size());
            for (OrderCreateDTO.SeatPosition pos : dto.getSeats()) {
                ScheduleSeat seat = seatByPos.get(pos.getRowNo() + ":" + pos.getColNo());
                if (seat == null) {
                    throw new MovieFrameException("部分座位不存在");
                }
                seats.add(seat);
            }
            // schedule_seat.id 才是状态 HASH 的 field，后续锁座/明细仍以 ID 为准
            seatIds = seats.stream().map(ScheduleSeat::getId).collect(Collectors.toList());

            // 批量从物理座位表拿 seatCode（非持久化字段，用于日志和 order_item 明细）
            Map<Long, String> seatCodeMap = seatMapper.selectBatchIds(
                            seats.stream().map(ScheduleSeat::getSeatId).distinct().collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(Seat::getId, Seat::getSeatCode));
            seats.forEach(s -> s.setSeatCode(seatCodeMap.getOrDefault(s.getSeatId(), "")));

            BigDecimal totalAmount = seats.stream().map(ScheduleSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

            order = new Order();
            order.setId(orderId);
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setScheduleId(scheduleId);
            order.setMovieId(schedule.getMovieId());
            order.setCinemaId(schedule.getCinemaId());
            order.setSeatCount(seats.size());
            order.setTotalAmount(totalAmount);
            order.setDiscount(BigDecimal.ZERO);
            order.setPayAmount(totalAmount);
            order.setStatus(0); // 待支付
            order.setExpireAt(expireDate);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            // ① Redis HASH 原子锁座（并发抢座的第一道闸，All-or-Nothing）
            long reserveResult = seatStateService.reserve(scheduleId, seatIds, orderId, expireMs);
            if (reserveResult > 0) {
                throw new MovieFrameException("座位已被占用，请刷新后重选");
            }
            if (reserveResult != 0) {
                throw new MovieFrameException("座位状态异常，请稍后重试");
            }
            reserved = true;

            // ② DB 二次校验 + 单条批量 UPDATE（允许接管 lock_expire_at 已到期的旧锁，与 Lua 判定一致）
            int rows = scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                    .in(ScheduleSeat::getId, seatIds)
                    .and(w -> w.eq(ScheduleSeat::getStatus, 0)
                            .or(x -> x.eq(ScheduleSeat::getStatus, 1).le(ScheduleSeat::getLockExpireAt, now)))
                    .set(ScheduleSeat::getStatus, 1)
                    .set(ScheduleSeat::getLockUserId, userId)
                    .set(ScheduleSeat::getLockExpireAt, expireDate)
                    .set(ScheduleSeat::getOrderId, orderId));
            if (rows != seatIds.size()) {
                throw new MovieFrameException("座位抢购失败，请重试");
            }

            // ③ 订单 + 明细（明细单条 SQL 批量插入，避免 N 次 INSERT 往返）
            save(order);
            List<OrderItem> items = new ArrayList<>(seats.size());
            for (ScheduleSeat seat : seats) {
                OrderItem item = new OrderItem();
                item.setId(IdUtil.getSnowflakeNextId());
                item.setOrderId(orderId);
                item.setScheduleSeatId(seat.getId());
                item.setSeatCode(seat.getSeatCode());
                item.setRowNo(seat.getRowNo());
                item.setColNo(seat.getColNo());
                item.setPrice(seat.getPrice());
                item.setCreatedAt(now);
                items.add(item);
            }
            orderItemMapper.insertBatch(items);

            // ④ 扣减可售座位
            scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                    .eq(Schedule::getId, scheduleId)
                    .setSql("available_seat = available_seat - " + seats.size()));
        } catch (RuntimeException e) {
            // DB 事务会回滚，但 Redis 状态迁移是事务外的，需要显式回滚已锁定的座位
            if (reserved) {
                try {
                    seatStateService.release(scheduleId, seatIds, orderId, 1);
                } catch (Exception ex) {
                    log.warn("[下单失败回滚座位状态异常] scheduleId={} orderId={}: {}", scheduleId, orderId, ex.getMessage());
                }
            }
            releaseIdempotentKey(idempotentKey);
            throw e;
        }

        // ⑤ 事务提交后再投递延时任务（事务回滚则不会投递）
        seatStateService.scheduleTimeoutAfterCommit(scheduleId, seatIds, orderId, expireMs);

        log.info("创建订单 orderNo={} userId={} scheduleId={} seatCount={}",
                order.getOrderNo(), userId, scheduleId, seatIds.size());
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

        Long scheduleId = order.getScheduleId();
        List<Long> seatIds = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId))
                .stream().map(OrderItem::getScheduleSeatId).collect(Collectors.toList());

        // ① Redis HASH 原子释放（1→0，校验锁归属；已被支付/自动解锁处理过则不动）
        seatStateService.release(scheduleId, seatIds, orderId, 1);

        // ② DB 乐观释放：只释放仍处于锁定中的座位
        int rows = scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                .in(ScheduleSeat::getId, seatIds)
                .eq(ScheduleSeat::getStatus, 1)
                .eq(ScheduleSeat::getOrderId, orderId)
                .set(ScheduleSeat::getStatus, 0)
                .set(ScheduleSeat::getLockUserId, 0L)
                .set(ScheduleSeat::getLockExpireAt, new Date(0))
                .set(ScheduleSeat::getOrderId, 0L));

        // 按实际释放数加回 availableSeat，避免与自动解锁并发时重复加库存
        if (rows > 0) {
            scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                    .eq(Schedule::getId, scheduleId)
                    .setSql("available_seat = available_seat + " + rows));
        }

        // ③ 订单状态按 0→2 条件更新（并发下若已支付/已取消则不覆盖）
        Date now = new Date();
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 2)
                .set(Order::getCancelTime, now)
                .set(Order::getUpdatedAt, now));

        // ④ 事务提交后移除全局延时队列中的该订单条目
        seatStateService.cancelTimeoutAfterCommit(scheduleId, seatIds, orderId);

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
        Date now = new Date();
        if (order.getExpireAt() != null && order.getExpireAt().before(now)) {
            throw new MovieFrameException("订单已超时，请重新下单");
        }

        Long scheduleId = order.getScheduleId();
        List<Long> seatIds = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId))
                .stream().map(OrderItem::getScheduleSeatId).collect(Collectors.toList());

        // ① Redis HASH 原子确认（1→2，校验锁归属；锁定已超时/被释放则失败）
        long confirmResult = seatStateService.confirmSold(scheduleId, seatIds, orderId);
        if (confirmResult != 0) {
            throw new MovieFrameException("座位锁定已失效，请重新下单");
        }

        // ② 订单 0→1 条件更新（防与取消/自动解锁并发覆盖）
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 1)
                .set(Order::getPayTime, now)
                .set(Order::getUpdatedAt, now));
        if (orderRows == 0) {
            Order latest = getById(orderId);
            if (latest != null && latest.getStatus() == 1) {
                log.info("[支付并发幂等] 订单已被其他请求置为已支付 orderNo={}", order.getOrderNo());
                return;
            }
            seatStateService.release(scheduleId, seatIds, orderId, 2);
            throw new MovieFrameException("订单状态已变化，请刷新后重试");
        }

        // ③ DB 锁定→已售（1→2）
        int rows = scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                .in(ScheduleSeat::getId, seatIds)
                .eq(ScheduleSeat::getStatus, 1)
                .eq(ScheduleSeat::getOrderId, orderId)
                .set(ScheduleSeat::getStatus, 2));
        if (rows != seatIds.size()) {
            seatStateService.release(scheduleId, seatIds, orderId, 2);
            throw new MovieFrameException("座位状态异常，请刷新后重试");
        }

        // ④ 事务提交后移除延时队列条目（支付完成无需自动解锁）
        seatStateService.cancelTimeoutAfterCommit(scheduleId, seatIds, orderId);

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
        Long scheduleId = order.getScheduleId();
        List<Long> seatIds = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId))
                .stream().map(OrderItem::getScheduleSeatId).collect(Collectors.toList());

        // ① Redis HASH 原子释放（2→0，校验锁归属）
        seatStateService.release(scheduleId, seatIds, orderId, 2);

        // ② DB 已售→可售（只释放仍为已售的座位）
        int rows = scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                .in(ScheduleSeat::getId, seatIds)
                .eq(ScheduleSeat::getStatus, 2)
                .eq(ScheduleSeat::getOrderId, orderId)
                .set(ScheduleSeat::getStatus, 0)
                .set(ScheduleSeat::getLockUserId, 0L)
                .set(ScheduleSeat::getLockExpireAt, new Date(0))
                .set(ScheduleSeat::getOrderId, 0L));

        // 按实际释放数加回 availableSeat
        if (rows > 0) {
            scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                    .eq(Schedule::getId, scheduleId)
                    .setSql("available_seat = available_seat + " + rows));
        }

        Date now = new Date();
        order.setStatus(4);
        order.setCancelTime(now);
        order.setRefundAuditAt(now);
        if (cn.hutool.core.util.StrUtil.isNotBlank(reason)) {
            order.setRefundReason(reason);
        }
        order.setUpdatedAt(now);
        updateById(order);

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
