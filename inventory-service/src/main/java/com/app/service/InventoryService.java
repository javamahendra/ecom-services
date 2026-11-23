package com.app.service;

import com.app.entity.InventoryItem;
import com.app.entity.ProcessedOrder;
import com.app.event.InventoryEvent;
import com.app.event.OrderEvent;
import com.app.repository.InventoryRepository;
import com.app.repository.ProcessedOrderRepository;
import com.app.request.InventoryRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final InventoryRepository inventory;
    private final ProcessedOrderRepository processed;
    private final MongoTemplate mongo;
    private final KafkaTemplate<Object, Object> events;

    public InventoryService(InventoryRepository inventory, ProcessedOrderRepository processed,
                            MongoTemplate mongo, KafkaTemplate<Object, Object> events) {
        this.inventory = inventory; this.processed = processed; this.mongo = mongo; this.events = events;
    }

    public InventoryItem upsert(InventoryRequest request) {
        String id = request.productId() + ":" + request.variantId();
        InventoryItem item = inventory.findById(id).orElseGet(() -> new InventoryItem(
                request.productId(), request.variantId(), request.productName(), request.color(), request.quantity()));
        item.update(request.productName(), request.color(), request.quantity());
        log.info("Inventory level updated: productId={}, variantId={}, color={}, quantity={}",
                request.productId(), request.variantId(), request.color(), request.quantity());
        return inventory.save(item);
    }

    public Map<String, Object> search(String productId, String color, Integer minQuantity,
                                      int page, int size, String sortBy, String direction) {
        checkPage(page, size);
        List<Criteria> filters = new ArrayList<>();
        if (productId != null) filters.add(Criteria.where("productId").is(productId));
        if (color != null) filters.add(Criteria.where("color").regex(
                java.util.regex.Pattern.quote(color), "i"));
        if (minQuantity != null) filters.add(Criteria.where("quantity").gte(minQuantity));
        Query query = new Query(filters.isEmpty() ? new Criteria() :
                new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        long total = mongo.count(query, InventoryItem.class);
        String field = Map.of("color", "color", "quantity", "quantity",
                "updatedAt", "updatedAt", "productName", "productName")
                .getOrDefault(sortBy, "updatedAt");
        query.with(Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, field))
                .skip((long) page * size).limit(size);
        List<InventoryItem> content = mongo.find(query, InventoryItem.class);
        int pages = (int) Math.ceil((double) total / size);
        return Map.of("content", content, "page", page, "size", size, "totalElements", total,
                "totalPages", pages, "first", page == 0, "last", page + 1 >= Math.max(1, pages));
    }

    public void handle(OrderEvent event) {
        if ("ORDER_CANCELLED".equals(event.eventType())) {
            release(event);
            return;
        }
        if (!"ORDER_CREATED".equals(event.eventType())) {
            log.warn("Ignoring unsupported order event type: eventId={}, type={}", event.eventId(), event.eventType());
            return;
        }
        if (processed.existsById(event.orderId())) return;
        reserve(event);
    }

    private void reserve(OrderEvent event) {
        List<ProcessedOrder.Reservation> changed = new ArrayList<>();
        try {
            for (OrderEvent.OrderItem orderItem : event.items()) {
                String key = orderItem.productId() + ":" + orderItem.variantId();
                var result = mongo.updateFirst(
                        Query.query(Criteria.where("_id").is(key)
                                .and("quantity").gte(orderItem.quantity())),
                        new Update().inc("quantity", -orderItem.quantity())
                                .set("updatedAt", Instant.now()),
                        InventoryItem.class);
                if (result.getModifiedCount() == 0) {
                    InventoryItem current = inventory.findById(key).orElse(null);
                    throw new IllegalStateException("Insufficient stock for " + orderItem.productName()
                            + " (" + orderItem.color() + ")"
                            + (current == null ? "; inventory item not found" : ""));
                }
                changed.add(new ProcessedOrder.Reservation(key, orderItem.quantity()));
            }
            processed.save(new ProcessedOrder(event.orderId(), "RESERVED", changed));
            publish(event, "RESERVED", "Inventory reserved");
            log.info("Inventory reserved for orderId={}", event.orderId());
        } catch (IllegalStateException exception) {
            for (ProcessedOrder.Reservation reservation : changed) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(reservation.inventoryId())),
                        new Update().inc("quantity", reservation.quantity()).set("updatedAt", Instant.now()),
                        InventoryItem.class);
            }
            processed.save(new ProcessedOrder(event.orderId(), "REJECTED", List.of()));
            publish(event, "REJECTED", exception.getMessage());
            log.warn("Inventory reservation rejected: orderId={}, reason={}", event.orderId(), exception.getMessage());
        }
    }

    private void release(OrderEvent event) {
        ProcessedOrder reservation = processed.findById(event.orderId()).orElse(null);
        if (reservation == null) {
            processed.save(new ProcessedOrder(event.orderId(), "CANCELLED", List.of()));
            publish(event, "CANCELLED", "Order cancelled; no stock reservation found");
            return;
        }
        if ("RELEASED".equals(reservation.getStatus()) || "CANCELLED".equals(reservation.getStatus())) return;
        if (!"RESERVED".equals(reservation.getStatus())) {
            reservation.setState("CANCELLED");
            processed.save(reservation);
            publish(event, "CANCELLED", "Order cancelled");
            return;
        }
        for (ProcessedOrder.Reservation itemReservation : reservation.getReservations()) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(itemReservation.inventoryId())),
                    new Update().inc("quantity", itemReservation.quantity()).set("updatedAt", Instant.now()),
                    InventoryItem.class);
        }
        reservation.setState("RELEASED");
        processed.save(reservation);
        publish(event, "CANCELLED", "Stock restored");
        log.info("Inventory released for cancelled orderId={}", event.orderId());
    }

    private void publish(OrderEvent orderEvent, String status, String message) {
        events.send("commerce.inventory-events", orderEvent.orderId(),
                new InventoryEvent(java.util.UUID.randomUUID().toString(), orderEvent.orderId(),
                        orderEvent.userId(), orderEvent.email(),
                        orderEvent.shippingAddress() == null ? null : orderEvent.shippingAddress().phone(),
                        orderEvent.shippingAddress() == null ? "" : orderEvent.shippingAddress().recipientName(),
                        status, message, Instant.now()))
                .whenComplete((result, error) -> {
                    if (error != null) log.error("Failed to publish inventory event for orderId={}",
                            orderEvent.orderId(), error);
                });
    }

    private static void checkPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 100");
    }
}
