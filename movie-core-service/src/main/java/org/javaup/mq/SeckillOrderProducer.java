package org.javaup.mq;

import org.javaup.AbstractProducerHandler;
import org.javaup.message.MessageExtend;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 秒杀下单消息生产者
 */
@Component
public class SeckillOrderProducer extends AbstractProducerHandler<MessageExtend<SeckillOrderMessage>> {

    public SeckillOrderProducer(KafkaTemplate<String, MessageExtend<SeckillOrderMessage>> seckillKafkaTemplate) {
        super(seckillKafkaTemplate);
    }
}
