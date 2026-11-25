package com.app.service;

import com.app.entity.InventoryItem;
import com.app.entity.ProcessedOrder;
import com.app.event.InventoryEvent;
import com.app.event.OrderEvent;
import com.app.repository.InventoryRepository;
import com.app.repository.ProcessedOrderRepository;
import com.app.request.InventoryRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final InventoryStockPublisher stockPublisher;

    public InventoryService(InventoryRepository inventory, ProcessedOrderRepository processed,
                            MongoTemplate mongo, KafkaTemplate<Object, Object> events,
                            InventoryStockPublisher stockPublisher) {
        this.inventory = inventory; this.processed = processed; this.mongo = mongo; this.events = events;
        this.stockPublisher = stockPublisher;
    }

    public InventoryItem upsert(InventoryRequest request) {
        String id = request.productId() + ":" + request.variantId();
        InventoryItem item = inventory.findById(id).orElseGet(() -> new InventoryItem(
                request.productId(), request.variantId(), request.productName(), request.color(), request.quantity()));
        item.update(request.productName(), request.color(), request.quantity());
        log.info("Inventory level updated: productId={}, variantId={}, color={}, quantity={}",
                request.productId(), request.variantId(), request.color(), request.quantity());
        InventoryItem saved = inventory.save(item);
        stockPublisher.publish(saved);
        return saved;
    }

    public void syncFromProduct(com.app.event.StockSyncEvent event) {
        if (!List.of("STOCK_SET", "STOCK_SEED", "STOCK_METADATA", "STOCK_REMOVED", "STOCK_RECONCILE")
                .contains(event.eventType())) {
            throw new IllegalArgumentException("Unsupported product stock event type: " + event.eventType());
        }
        String id = event.key();
        InventoryItem existing = inventory.findById(id).orElse(null);
        if (List.of("STOCK_SEED", "STOCK_METADATA").contains(event.eventType()) && existing != null) {
            existing.update(event.productCode(), event.productName(), event.color(), existing.getQuantity());
            inventory.save(existing);
            return;
        }
        int quantity = "STOCK_REMOVED".equals(event.eventType()) ? 0 : event.quantity();
        InventoryItem item = existing == null
                ? new InventoryItem(event.productId(), event.productCode(), event.variantId(),
                        event.productName(), event.color(), quantity)
                : existing;
        if (existing != null) item.update(event.productCode(), event.productName(), event.color(), quantity);
        inventory.save(item);
        log.info("Product stock synchronized to inventory: productId={}, variantId={}, eventType={}, quantity={}",
                event.productId(), event.variantId(), event.eventType(), quantity);
    }

    public void syncToProduct(String productId, String variantId) {
        InventoryItem item = inventory.findById(productId + ":" + variantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory variant not found."));
        stockPublisher.publishAndWait(item);
    }

    public Map<String, Object> search(String productId, String color, String search, Integer minQuantity,
                                      Integer maxQuantity, String quantityOperator, Integer quantityValue,
                                      LocalDate fromDate, LocalDate toDate,
                                      int page, int size, String sortBy, String direction) {
        checkPage(page, size);
        if ((minQuantity != null && minQuantity < 0) || (maxQuantity != null && maxQuantity < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity filters must be non-negative.");
        }
        if (minQuantity != null && maxQuantity != null && minQuantity > maxQuantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minQuantity must be less than or equal to maxQuantity.");
        }
        if ((quantityOperator == null) != (quantityValue == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "quantityOperator and quantityValue must be provided together.");
        }
        if (quantityValue != null && quantityValue < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantityValue must be non-negative.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be on or before toDate.");
        }
        List<Criteria> filters = new ArrayList<>();
        if (productId != null) filters.add(Criteria.where("productId").is(productId));
        if (color != null) filters.add(Criteria.where("color").regex(
                java.util.regex.Pattern.quote(color), "i"));
        if (search != null && !search.isBlank()) {
            String term = java.util.regex.Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(Criteria.where("productName").regex(term, "i"),
                    Criteria.where("productCode").regex(term, "i"),
                    Criteria.where("productId").regex(term, "i"), Criteria.where("variantId").regex(term, "i")));
        }
        if (minQuantity != null) filters.add(Criteria.where("quantity").gte(minQuantity));
        if (maxQuantity != null) filters.add(Criteria.where("quantity").lte(maxQuantity));
        if (quantityOperator != null) {
            Criteria quantity = Criteria.where("quantity");
            switch (quantityOperator.toLowerCase(java.util.Locale.ROOT)) {
                case "eq" -> quantity.is(quantityValue);
                case "gte" -> quantity.gte(quantityValue);
                case "lte" -> quantity.lte(quantityValue);
                case "gt" -> quantity.gt(quantityValue);
                case "lt" -> quantity.lt(quantityValue);
                case "ne" -> quantity.ne(quantityValue);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "quantityOperator must be one of eq, gte, lte, gt, lt, ne.");
            }
            filters.add(quantity);
        }
        if (fromDate != null || toDate != null) {
            Criteria updatedAt = Criteria.where("updatedAt");
            if (fromDate != null) updatedAt.gte(fromDate.atStartOfDay().toInstant(ZoneOffset.UTC));
            if (toDate != null) updatedAt.lt(toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
            filters.add(updatedAt);
        }
        Query query = new Query(filters.isEmpty() ? new Criteria() :
                new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        long total = mongo.count(query, InventoryItem.class);
        String field = Map.of("color", "color", "quantity", "quantity", "productCode", "productCode",
                "variantId", "variantId", "updatedAt", "updatedAt", "productName", "productName")
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
        List<InventoryEvent.ItemResult> itemResults = new ArrayList<>();
        int reserved = 0;
        for (int itemIndex = 0; itemIndex < event.items().size(); itemIndex++) {
            OrderEvent.OrderItem orderItem = event.items().get(itemIndex);
            String key = orderItem.productId() + ":" + orderItem.variantId();
            var result = mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(key)
                            .and("quantity").gte(orderItem.quantity())),
                    new Update().inc("quantity", -orderItem.quantity())
                            .set("updatedAt", Instant.now()),
                    InventoryItem.class);
            if (result.getModifiedCount() == 0) {
                InventoryItem current = inventory.findById(key).orElse(null);
                String message = "Insufficient stock for " + orderItem.productName()
                        + " (" + orderItem.color() + ")"
                        + (current == null ? "; inventory item not found" : "");
                itemResults.add(new InventoryEvent.ItemResult(itemIndex, "REJECTED", message));
                log.warn("Order item could not be reserved: orderId={}, itemIndex={}, reason={}",
                        event.orderId(), itemIndex, message);
                continue;
            }
            changed.add(new ProcessedOrder.Reservation(key, orderItem.quantity()));
            inventory.findById(key).ifPresent(stockPublisher::publish);
            itemResults.add(new InventoryEvent.ItemResult(itemIndex, "RESERVED", "Inventory reserved"));
            reserved++;
        }
        String status = reserved == event.items().size() ? "RESERVED"
                : reserved == 0 ? "REJECTED" : "PARTIALLY_RESERVED";
        String message = reserved == event.items().size() ? "Inventory reserved for all items"
                : reserved == 0 ? "No items could be reserved"
                : reserved + " item(s) reserved; " + (event.items().size() - reserved) + " item(s) unavailable";
        processed.save(new ProcessedOrder(event.orderId(), status, changed));
        publish(event, status, message, itemResults);
        log.info("Inventory reservation completed: orderId={}, status={}, reservedItems={}",
                event.orderId(), status, reserved);
    }

    private void release(OrderEvent event) {
        ProcessedOrder reservation = processed.findById(event.orderId()).orElse(null);
        if (reservation == null) {
            processed.save(new ProcessedOrder(event.orderId(), "CANCELLED", List.of()));
            publish(event, "CANCELLED", "Order cancelled; no stock reservation found");
            return;
        }
        if ("RELEASED".equals(reservation.getStatus()) || "CANCELLED".equals(reservation.getStatus())) return;
        if (!"RESERVED".equals(reservation.getStatus())
                && !"PARTIALLY_RESERVED".equals(reservation.getStatus())) {
            reservation.setState("CANCELLED");
            processed.save(reservation);
            publish(event, "CANCELLED", "Order cancelled");
            return;
        }
        for (ProcessedOrder.Reservation itemReservation : reservation.getReservations()) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(itemReservation.inventoryId())),
                    new Update().inc("quantity", itemReservation.quantity()).set("updatedAt", Instant.now()),
                    InventoryItem.class);
            inventory.findById(itemReservation.inventoryId()).ifPresent(stockPublisher::publish);
        }
        reservation.setState("RELEASED");
        processed.save(reservation);
        publish(event, "CANCELLED", "Stock restored", List.of());
        log.info("Inventory released for cancelled orderId={}", event.orderId());
    }

    private void publish(OrderEvent orderEvent, String status, String message) {
        publish(orderEvent, status, message, List.of());
    }

    private void publish(OrderEvent orderEvent, String status, String message,
                         List<InventoryEvent.ItemResult> itemResults) {
        events.send("commerce.inventory-events", orderEvent.orderId(),
                new InventoryEvent(java.util.UUID.randomUUID().toString(), orderEvent.orderId(),
                        orderEvent.userId(), orderEvent.email(),
                        orderEvent.shippingAddress() == null ? null : orderEvent.shippingAddress().phone(),
                        orderEvent.shippingAddress() == null ? "" : orderEvent.shippingAddress().recipientName(),
                        status, message, Instant.now(), itemResults))
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
