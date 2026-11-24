package org.javaup.mq;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.javaup.message.MessageExtend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀下单消息的 Kafka 生产者配置
 *
 * 单独定义带泛型的 KafkaTemplate，以便复用 {@link org.javaup.AbstractProducerHandler}。
 */
@Configuration
public class SeckillKafkaConfig {

    @Bean
    public ProducerFactory<String, MessageExtend<SeckillOrderMessage>> seckillProducerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // 可靠性：所有副本确认 + 幂等生产者 + 重试
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, MessageExtend<SeckillOrderMessage>> seckillKafkaTemplate(
            ProducerFactory<String, MessageExtend<SeckillOrderMessage>> seckillProducerFactory) {
        return new KafkaTemplate<>(seckillProducerFactory);
    }
}
