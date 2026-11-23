package com.app.service;

import com.app.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderDispatchListener {
    private static final Logger log = LoggerFactory.getLogger(OrderDispatchListener.class);
    private final DispatchService dispatch;
    public OrderDispatchListener(DispatchService dispatch) { this.dispatch = dispatch; }
    @KafkaListener(topics = "commerce.order-events", groupId = "dispatch-service")
    public void onOrderEvent(OrderEvent event) {
        try { dispatch.orderEvent(event); }
        catch (RuntimeException exception) {
            log.error("Failed to process dispatch order event: orderId={}", event.orderId(), exception);
            throw exception;
        }
    }
}
