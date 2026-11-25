package com.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.KafkaListenerContainerFactory;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.listener.auto-startup=false",
        "eureka.client.enabled=false"
})
class KafkaListenerConfigurationTests {
    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private KafkaListenerContainerFactory<?> kafkaListenerContainerFactory;

    @Test
    void registersKafkaListenerFactoryAndListeners() {
        assertThat(kafkaListenerContainerFactory).isNotNull();
        assertThat(listenerRegistry.getListenerContainerIds()).hasSize(2);
        assertThat(listenerRegistry.getListenerContainers())
                .allSatisfy(container -> assertThat(container.isRunning()).isFalse());
    }
}
