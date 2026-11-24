package org.javaup.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.javaup.consumer.AbstractConsumerHandler;
import org.javaup.entity.SeckillVoucherOrder;
import org.javaup.mapper.SeckillVoucherOrderMapper;
import org.javaup.message.MessageExtend;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.javaup.utils.SeckillConstants.*;

/**
 * 秒杀下单消息消费者：异步落库
 *
 * 幂等策略：
 *  1. 唯一索引 uk_voucher_user(voucher_id, user_id) 兜底，重复消息插入失败即忽略；
 *  2. Redis 预扣库存阶段已用 SET 去重，正常情况不会产生重复消息。
 */
@Slf4j
@Component
public class SeckillOrderConsumer extends AbstractConsumerHandler<SeckillOrderMessage> {

    private final SeckillVoucherOrderMapper seckillVoucherOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public SeckillOrderConsumer(SeckillVoucherOrderMapper seckillVoucherOrderMapper,
                                StringRedisTemplate stringRedisTemplate) {
        super(SeckillOrderMessage.class);
        this.seckillVoucherOrderMapper = seckillVoucherOrderMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @KafkaListener(
            topics = "${seckill.kafka.order-topic:seckill_voucher_order_topic}",
            groupId = "${seckill.kafka.order-group:seckill_voucher_order_group}")
    public void onMessage(ConsumerRecord<String, String> record) {
        Map<String, Object> headers = new HashMap<>();
        if (record.headers() != null) {
            record.headers().forEach(header -> headers.put(header.key(), header.value()));
        }
        consumeRaw(record.value(), record.key(), headers);
    }

    @Override
    protected void doConsume(MessageExtend<SeckillOrderMessage> message) {
        SeckillOrderMessage body = message.getMessageBody();
        if (body == null || body.getOrderId() == null) {
            log.warn("[SECKILL_CONSUMER] 消息体为空，跳过 uuid={}", message.getUuid());
            return;
        }

        // 幂等前置判断：重复投递/重试时不再重复插入
        Long exists = seckillVoucherOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillVoucherOrder>()
                        .eq(SeckillVoucherOrder::getVoucherId, body.getVoucherId())
                        .eq(SeckillVoucherOrder::getUserId, body.getUserId()));
        if (exists != null && exists > 0) {
            log.warn("[SECKILL_CONSUMER] 该用户已存在订单，跳过 orderId={} voucherId={} userId={}",
                    body.getOrderId(), body.getVoucherId(), body.getUserId());
            markSuccess(body.getOrderId());
            return;
        }

        try {
            SeckillVoucherOrder order = new SeckillVoucherOrder();
            order.setId(body.getOrderId());
            order.setVoucherId(body.getVoucherId());
            order.setUserId(body.getUserId());
            order.setShopId(body.getShopId());
            order.setStatus(1);
            order.setCreateTime(LocalDateTime.now());
            seckillVoucherOrderMapper.insert(order);
            log.info("[SECKILL_CONSUMER] 订单落库成功 orderId={} voucherId={} userId={}",
                    body.getOrderId(), body.getVoucherId(), body.getUserId());
        } catch (DuplicateKeyException e) {
            // 重复消息：同一用户同一券已下过单，直接视为成功
            log.warn("[SECKILL_CONSUMER] 重复消息已忽略 orderId={} voucherId={} userId={}",
                    body.getOrderId(), body.getVoucherId(), body.getUserId());
        }

        markSuccess(body.getOrderId());
    }

    /**
     * 标记订单处理成功
     */
    private void markSuccess(Long orderId) {
        stringRedisTemplate.opsForValue().set(
                ORDER_STATUS_KEY_PREFIX + orderId,
                ORDER_STATUS_SUCCESS,
                ORDER_STATUS_TTL_MINUTES,
                TimeUnit.MINUTES);
    }
}
