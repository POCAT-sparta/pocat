package com.rocketcrew.pocat.global.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ------------------------------ 일반 토픽용 ------------------------------
    // Producer
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // 전송 실패 시 재시도
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        // 리더 브로커 저장 확인 후 완료
        props.put(ProducerConfig.ACKS_CONFIG, "1");

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Consumer
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // TODO : 옵셋 기준 논의해보기

        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ------------------------------ 중요한 토픽용 (결제, 정산, 환불 등) ------------------------------
    // TODO : 설정값 도메인별 세부 조정 논의

    // 금전 도메인 공통 Producer 설정 (acks=all, 멱등성 보장)
    private Map<String, Object> financialProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    // 결제 Producer
    @Bean
    public ProducerFactory<String, String> paymentProducerFactory() {
        return new DefaultKafkaProducerFactory<>(financialProducerProps());
    }

    @Bean
    public KafkaTemplate<String, String> paymentKafkaTemplate() {
        return new KafkaTemplate<>(paymentProducerFactory());
    }

    // 환불 Producer
    @Bean
    public ProducerFactory<String, String> refundProducerFactory() {
        return new DefaultKafkaProducerFactory<>(financialProducerProps());
    }

    @Bean
    public KafkaTemplate<String, String> refundKafkaTemplate() {
        return new KafkaTemplate<>(refundProducerFactory());
    }

    // 정산 Producer
    @Bean
    public ProducerFactory<String, String> settlementProducerFactory() {
        return new DefaultKafkaProducerFactory<>(financialProducerProps());
    }

    @Bean
    public KafkaTemplate<String, String> settlementKafkaTemplate() {
        return new KafkaTemplate<>(settlementProducerFactory());
    }

    // 금전 도메인 공통 Consumer 설정 (수동 커밋)
    private Map<String, Object> financialConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> manualAckFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    // 결제 Consumer
    @Bean
    public ConsumerFactory<String, String> paymentConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(financialConsumerProps());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    paymentKafkaListenerContainerFactory() {
        return manualAckFactory(paymentConsumerFactory());
    }

    // 환불 Consumer
    @Bean
    public ConsumerFactory<String, String> refundConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(financialConsumerProps());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    refundKafkaListenerContainerFactory() {
        return manualAckFactory(refundConsumerFactory());
    }

    // 정산 Consumer
    @Bean
    public ConsumerFactory<String, String> settlementConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(financialConsumerProps());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    settlementKafkaListenerContainerFactory() {
        return manualAckFactory(settlementConsumerFactory());
    }
}
