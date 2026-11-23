package com.app.service;

import com.app.entity.OrderDocument;
import com.app.event.OrderEvent;
import com.app.repository.OrderRepository;
import com.app.request.CreateOrderRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "createdAt", "updatedAt", "updatedAt", "total", "total", "status", "status");
    private final OrderRepository repository;
    private final MongoTemplate mongo;
    private final KafkaTemplate<Object, Object> kafka;

    public OrderService(OrderRepository repository, MongoTemplate mongo, KafkaTemplate<Object, Object> kafka) {
        this.repository = repository; this.mongo = mongo; this.kafka = kafka;
    }

    public OrderDocument create(String userId, CreateOrderRequest request) {
        List<OrderEvent.OrderItem> items = request.toEventItems();
        BigDecimal total = items.stream().map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        OrderDocument order = repository.save(new OrderDocument(
                UUID.randomUUID().toString(), userId, request.email(), items,
                request.shippingAddress().toEventAddress(), total));
        publish(order, "ORDER_CREATED");
        log.info("Order created and inventory request published: orderId={}, userId={}", order.getId(), userId);
        return order;
    }

    public OrderDocument cancel(String id, String userId, boolean admin) {
        OrderDocument order = find(id);
        if (!admin && !order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot cancel another user's order");
        }
        if ("CANCELLED".equals(order.getStatus())) return order;
        if ("CANCELLATION_PENDING".equals(order.getStatus())) {
            publish(order, "ORDER_CANCELLED");
            return order;
        }
        if (!List.of("PENDING_INVENTORY", "CONFIRMED", "REJECTED").contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order cannot be cancelled in status " + order.getStatus());
        }
        order.setStatus("CANCELLATION_PENDING");
        repository.save(order);
        publish(order, "ORDER_CANCELLED");
        log.info("Order cancellation requested: orderId={}, userId={}", id, userId);
        return order;
    }

    public OrderDocument get(String id, String userId, boolean admin) {
        OrderDocument order = find(id);
        if (!admin && !order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's order");
        }
        return order;
    }

    public Map<String, Object> search(String userId, boolean admin, String status, String search,
                                      int page, int size, String sortBy, String direction) {
        validatePage(page, size);
        List<Criteria> filters = new java.util.ArrayList<>();
        if (!admin) filters.add(Criteria.where("userId").is(userId));
        if (status != null && !status.isBlank()) filters.add(Criteria.where("status").is(status.toUpperCase()));
        if (search != null && !search.isBlank()) {
            String term = java.util.regex.Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(Criteria.where("_id").regex(term, "i"),
                    Criteria.where("email").regex(term, "i")));
        }
        Criteria criteria = filters.isEmpty() ? new Criteria() :
                new Criteria().andOperator(filters.toArray(Criteria[]::new));
        Query query = new Query(criteria);
        long total = mongo.count(query, OrderDocument.class);
        String field = SORTS.getOrDefault(sortBy, "createdAt");
        Sort.Direction order = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        query.with(Sort.by(order, field)).skip((long) page * size).limit(size);
        List<OrderDocument> content = mongo.find(query, OrderDocument.class);
        return Map.of("content", content, "page", page, "size", size, "totalElements", total,
                "totalPages", (int) Math.ceil((double) total / size), "first", page == 0,
                "last", page + 1 >= Math.max(1, (int) Math.ceil((double) total / size)));
    }

    public void updateStatus(String id, String status) {
        repository.findById(id).ifPresent(order -> {
            order.setStatus(status);
            repository.save(order);
            log.info("Order status updated from inventory event: orderId={}, status={}", id, status);
        });
    }

    private OrderDocument find(String id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void publish(OrderDocument order, String type) {
        OrderEvent event = new OrderEvent(UUID.randomUUID().toString(), type, order.getId(), order.getUserId(),
                order.getEmail(), order.getItems(), order.getShippingAddress(), order.getTotal(), Instant.now());
        kafka.send("commerce.order-events", order.getId(), event)
                .whenComplete((result, error) -> {
                    if (error != null) log.error("Failed to publish order event: orderId={}, type={}",
                            order.getId(), type, error);
                });
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 100");
    }
}
