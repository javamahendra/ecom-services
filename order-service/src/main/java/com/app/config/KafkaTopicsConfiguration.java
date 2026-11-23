package com.app.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfiguration {
    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name("commerce.order-events").partitions(6).replicas(1).build();
    }
    @Bean
    NewTopic inventoryEventsTopic() {
        return TopicBuilder.name("commerce.inventory-events").partitions(6).replicas(1).build();
    }
    @Bean
    NewTopic userEventsTopic() {
        return TopicBuilder.name("commerce.user-events").partitions(6).replicas(1).build();
    }
    @Bean
    NewTopic notificationEventsTopic() {
        return TopicBuilder.name("commerce.notification-events").partitions(6).replicas(1).build();
    }
}
