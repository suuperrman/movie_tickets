package org.javaup.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.core.RedisKeyManage;
import org.javaup.entity.SeckillVoucher;
import org.javaup.enums.BaseCode;
import org.javaup.exception.MovieFrameException;
import org.javaup.lua.SeckillVoucherDomain;
import org.javaup.lua.SeckillVoucherOperate;
import org.javaup.lua.SeckillVoucherRollBackOperate;
import org.javaup.mapper.SeckillVoucherMapper;
import org.javaup.mapper.SeckillVoucherOrderMapper;
import org.javaup.model.SeckillVoucherFullModel;
import org.javaup.mq.SeckillOrderMessage;
import org.javaup.mq.SeckillOrderProducer;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.ISeckillVoucherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.javaup.utils.SeckillConstants.*;

/**
 * 秒杀优惠券服务实现
 *
 * 下单链路：校验活动 → Redis Lua 原子预扣库存并去重 → 投递 Kafka → 立即返回订单号，
 * 由 {@link org.javaup.mq.SeckillOrderConsumer} 异步落库，实现削峰填谷。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillVoucherServiceImpl implements ISeckillVoucherService {

    private static final long VOUCHER_CACHE_TTL_MINUTES = 10L;

    private final SeckillVoucherMapper seckillVoucherMapper;
    private final SeckillVoucherOrderMapper seckillVoucherOrderMapper;
    private final SeckillVoucherOperate seckillVoucherOperate;
    private final SeckillVoucherRollBackOperate seckillVoucherRollBackOperate;
    private final SeckillOrderProducer seckillOrderProducer;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${seckill.kafka.order-topic:seckill_voucher_order_topic}")
    private String orderTopic;

    @Override
    public SeckillVoucherFullModel queryByVoucherId(Long voucherId) {
        if (voucherId == null) {
            return null;
        }
        String cacheKey = RedisKeyBuild
                .createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId)
                .getRelKey();
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, SeckillVoucherFullModel.class);
        }

        SeckillVoucher entity = seckillVoucherMapper.selectOne(
                new LambdaQueryWrapper<SeckillVoucher>().eq(SeckillVoucher::getVoucherId, voucherId));
        if (entity == null) {
            return null;
        }

        SeckillVoucherFullModel model = toModel(entity);
        stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(model),
                VOUCHER_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        initStockCache(model);
        return model;
    }

    @Override
    public Long seckill(Long voucherId, Long userId) {
        // 1. 活动校验
        SeckillVoucherFullModel voucher = queryByVoucherId(voucherId);
        if (voucher == null) {
            throw new MovieFrameException(BaseCode.SECKILL_VOUCHER_NOT_EXIST);
        }
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime() != null && now.isBefore(voucher.getBeginTime())) {
            throw new MovieFrameException(BaseCode.SECKILL_VOUCHER_NOT_BEGIN);
        }
        if (voucher.getEndTime() != null && now.isAfter(voucher.getEndTime())) {
            throw new MovieFrameException(BaseCode.SECKILL_VOUCHER_IS_OVER);
        }
        if (voucher.getStatus() != null && voucher.getStatus() != 1) {
            throw new MovieFrameException(BaseCode.VOUCHER_UNAVAILABLE);
        }

        // 2. Redis Lua 原子预扣库存 + 用户去重（All or Nothing）
        String stockKey = RedisKeyBuild
                .createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId).getRelKey();
        String userKey = RedisKeyBuild
                .createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId).getRelKey();
        String[] args = new String[]{String.valueOf(userId), "1"};
        SeckillVoucherDomain domain = seckillVoucherOperate.execute(List.of(stockKey, userKey), args);
        checkDeductResult(domain);

        // 3. 生成订单号并写入处理中状态
        Long orderId = IdUtil.getSnowflakeNextId();
        String statusKey = ORDER_STATUS_KEY_PREFIX + orderId;
        stringRedisTemplate.opsForValue().set(statusKey, ORDER_STATUS_PENDING,
                ORDER_STATUS_TTL_MINUTES, TimeUnit.MINUTES);

        // 4. 投递 Kafka 异步落库；发送失败则回滚 Redis 库存
        SeckillOrderMessage message = new SeckillOrderMessage(
                orderId, voucherId, userId, voucher.getShopId());
        try {
            seckillOrderProducer.sendPayload(orderTopic, String.valueOf(orderId), message, null)
                    .get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("[SECKILL] 投递消息失败，回滚库存 voucherId={} userId={} orderId={}",
                    voucherId, userId, orderId, e);
            seckillVoucherRollBackOperate.execute(List.of(stockKey, userKey), args);
            stringRedisTemplate.delete(statusKey);
            throw new MovieFrameException("系统繁忙，请稍后重试");
        }

        log.info("[SECKILL] 预扣库存成功并已投递消息 voucherId={} userId={} orderId={} afterQty={}",
                voucherId, userId, orderId, domain.getAfterQty());
        return orderId;
    }

    @Override
    public String getOrderStatus(Long orderId) {
        if (orderId == null) {
            return ORDER_STATUS_NOT_FOUND;
        }
        String status = stringRedisTemplate.opsForValue().get(ORDER_STATUS_KEY_PREFIX + orderId);
        if (status != null) {
            return status;
        }
        return seckillVoucherOrderMapper.selectById(orderId) != null
                ? ORDER_STATUS_SUCCESS : ORDER_STATUS_NOT_FOUND;
    }

    /**
     * 校验 Lua 预扣库存结果
     */
    private void checkDeductResult(SeckillVoucherDomain domain) {
        if (domain == null || domain.getCode() == null) {
            throw new MovieFrameException(BaseCode.SECKILL_VOUCHER_STOCK_NOT_EXIST);
        }
        switch (domain.getCode()) {
            case 0 -> {
                // 成功
            }
            case 1 -> throw new MovieFrameException(BaseCode.SECKILL_VOUCHER_STOCK_INSUFFICIENT);
            case 2 -> throw new MovieFrameException(BaseCode.SECKILL_VOUCHER_CLAIM);
            case 3 -> throw new MovieFrameException(BaseCode.SECKILL_VOUCHER_STOCK_NOT_EXIST);
            default -> throw new MovieFrameException("秒杀失败，请稍后重试");
        }
    }

    /**
     * 库存缓存初始化：以数据库剩余库存为准，仅在 key 不存在时写入
     */
    private void initStockCache(SeckillVoucherFullModel voucher) {
        String stockKey = RedisKeyBuild
                .createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucher.getVoucherId()).getRelKey();
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
            int stock = voucher.getStock() == null ? 0 : voucher.getStock();
            stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(stock));
        }
    }

    private SeckillVoucherFullModel toModel(SeckillVoucher entity) {
        return new SeckillVoucherFullModel()
                .setId(entity.getId())
                .setVoucherId(entity.getVoucherId())
                .setInitStock(entity.getInitStock())
                .setStock(entity.getStock())
                .setAllowedLevels(entity.getAllowedLevels())
                .setMinLevel(entity.getMinLevel())
                .setShopId(entity.getShopId())
                .setBeginTime(entity.getBeginTime())
                .setEndTime(entity.getEndTime())
                .setStatus(entity.getStatus())
                .setCreateTime(entity.getCreateTime());
    }
}
