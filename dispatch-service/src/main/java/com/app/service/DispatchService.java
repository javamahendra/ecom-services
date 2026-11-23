package com.app.service;

import com.app.entity.AddressDocument;
import com.app.entity.DispatchRecord;
import com.app.entity.PendingInventoryStatus;
import com.app.event.InventoryEvent;
import com.app.event.OrderEvent;
import com.app.event.NotificationEvent;
import com.app.repository.AddressRepository;
import com.app.repository.DispatchRepository;
import com.app.repository.PendingInventoryStatusRepository;
import com.app.request.AddressRequest;
import com.app.request.DispatchStatusRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class DispatchService {
    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);
    private static final List<String> DISPATCH_STATUSES =
            List.of("READY_TO_DISPATCH", "PACKED", "SHIPPED", "IN_TRANSIT", "DELIVERED", "CANCELLED");
    private final AddressRepository addresses;
    private final DispatchRepository dispatches;
    private final PendingInventoryStatusRepository pendingInventory;
    private final MongoTemplate mongo;
    private final KafkaTemplate<Object, Object> notificationKafka;
    public DispatchService(AddressRepository addresses, DispatchRepository dispatches,
                           PendingInventoryStatusRepository pendingInventory, MongoTemplate mongo,
                           KafkaTemplate<Object, Object> notificationKafka) {
        this.addresses = addresses; this.dispatches = dispatches;
        this.pendingInventory = pendingInventory; this.mongo = mongo;
        this.notificationKafka = notificationKafka;
    }

    public List<AddressDocument> addresses(String userId) {
        return addresses.findByUserId(userId).stream()
                .sorted(java.util.Comparator.comparing(AddressDocument::isDefaultAddress).reversed()
                        .thenComparing(AddressDocument::getCreatedAt))
                .toList();
    }
    public AddressDocument saveAddress(String userId, String id, AddressRequest request, boolean admin) {
        AddressDocument address = id == null ? new AddressDocument() : addresses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        if (id != null && !admin && !userId.equals(address.getUserId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot modify another user's address");
        String ownerId = id != null && admin ? address.getUserId() : userId;
        List<AddressDocument> existingAddresses = addresses.findByUserId(ownerId);
        boolean keepCurrentDefault = id != null && address.isDefaultAddress() && !request.defaultAddress();
        boolean hasDefaultAddress = existingAddresses.stream().anyMatch(AddressDocument::isDefaultAddress);
        boolean makeDefault = request.defaultAddress() || keepCurrentDefault || !hasDefaultAddress;
        if (makeDefault) {
            existingAddresses.stream()
                    .filter(existing -> !existing.getId().equals(id) && existing.isDefaultAddress())
                    .forEach(existing -> {
                        existing.setDefaultAddress(false);
                        addresses.save(existing);
                    });
        }
        address.populate(ownerId, request, makeDefault);
        AddressDocument saved = addresses.save(address);
        if (id != null) {
            publish(saved.getUserId(), null, saved.getPhone(), null, "ADDRESS_UPDATED",
                    Map.of("name", saved.getRecipientName(), "status", "updated", "message", "Your saved address was updated."));
        }
        log.info("Address saved: addressId={}, userId={}", saved.getId(), saved.getUserId());
        return saved;
    }
    public void deleteAddress(String userId, String id, boolean admin) {
        AddressDocument address = addresses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        if (!admin && !userId.equals(address.getUserId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete another user's address");
        List<AddressDocument> remaining = addresses.findByUserId(address.getUserId()).stream()
                .filter(existing -> !existing.getId().equals(id))
                .toList();
        addresses.delete(address);
        if (address.isDefaultAddress() && !remaining.isEmpty()) {
            AddressDocument nextDefault = remaining.get(0);
            nextDefault.setDefaultAddress(true);
            addresses.save(nextDefault);
        }
        publish(address.getUserId(), null, address.getPhone(), null, "ADDRESS_UPDATED",
                Map.of("name", address.getRecipientName(), "status", "removed",
                        "message", "A saved address was removed from your account."));
    }

    public void orderEvent(OrderEvent event) {
        if ("ORDER_CREATED".equals(event.eventType())) {
            DispatchRecord record = dispatches.findById(event.orderId())
                    .orElseGet(() -> dispatches.save(new DispatchRecord(event)));
            pendingInventory.findById(event.orderId()).ifPresent(pending -> {
                applyInventoryStatus(record, pending.getStatus(), pending.getMessage());
                dispatches.save(record);
                pendingInventory.delete(pending);
            });
            log.info("Dispatch record created from order event: orderId={}", event.orderId());
        } else if ("ORDER_CANCELLED".equals(event.eventType())) {
            dispatches.findById(event.orderId()).ifPresent(record -> {
                if (!"SHIPPED".equals(record.getStatus()) && !"IN_TRANSIT".equals(record.getStatus())
                        && !"DELIVERED".equals(record.getStatus())) {
                    record.updateStatus("CANCELLED", null, "Order cancelled");
                    dispatches.save(record);
                }
            });
        }
    }
    public void inventoryEvent(InventoryEvent event) {
        DispatchRecord record = dispatches.findById(event.orderId()).orElse(null);
        if (record == null) {
            pendingInventory.save(new PendingInventoryStatus(
                    event.orderId(), event.status(), event.message(), event.occurredAt()));
            return;
        }
        applyInventoryStatus(record, event.status(), event.message());
        dispatches.save(record);
        log.info("Dispatch status updated from inventory: orderId={}, status={}",
                event.orderId(), record.getStatus());
    }
    private void applyInventoryStatus(DispatchRecord record, String status, String message) {
        String next = switch (status) {
            case "RESERVED" -> "READY_TO_DISPATCH";
            case "REJECTED" -> "INVENTORY_REJECTED";
            case "CANCELLED" -> "CANCELLED";
            default -> record.getStatus();
        };
        if (!next.equals(record.getStatus())) record.updateStatus(next, null, message);
    }
    public DispatchRecord updateStatus(String orderId, DispatchStatusRequest request) {
        DispatchRecord record = dispatches.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatch not found"));
        if (!DISPATCH_STATUSES.contains(request.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported dispatch status");
        if (request.status().equals(record.getStatus())) return record;
        boolean validTransition = switch (record.getStatus()) {
            case "READY_TO_DISPATCH" -> List.of("PACKED", "CANCELLED").contains(request.status());
            case "PACKED" -> List.of("SHIPPED", "CANCELLED").contains(request.status());
            case "SHIPPED" -> "IN_TRANSIT".equals(request.status());
            case "IN_TRANSIT" -> "DELIVERED".equals(request.status());
            case "AWAITING_INVENTORY", "INVENTORY_REJECTED" -> "CANCELLED".equals(request.status());
            default -> false;
        };
        if (!validTransition)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid dispatch transition from " + record.getStatus() + " to " + request.status());
        record.updateStatus(request.status(), request.trackingNumber(), request.note());
        DispatchRecord saved = dispatches.save(record);
        publish(saved.getUserId(), saved.getEmail(), saved.getShippingAddress().phone(), saved.getOrderId(),
                "CANCELLED".equals(saved.getStatus()) ? "ORDER_CANCELLED" : "ORDER_STATUS_UPDATED",
                Map.of("name", saved.getShippingAddress().recipientName(), "status", saved.getStatus(),
                        "trackingNumber", saved.getTrackingNumber() == null ? "" : saved.getTrackingNumber(),
                        "total", saved.getTotal().toPlainString(), "items", saved.getItems().stream()
                                .map(i -> i.productName() + " x" + i.quantity())
                                .collect(java.util.stream.Collectors.joining(", ")),
                        "message", request.note() == null ? "" : request.note()));
        log.info("Dispatch status changed: orderId={}, status={}", orderId, request.status());
        return saved;
    }
    private void publish(String userId, String email, String phone, String orderId,
                         String type, Map<String, String> data) {
        NotificationEvent event = new NotificationEvent(
                java.util.UUID.randomUUID().toString(), type, userId, email, phone,
                orderId, data, java.time.Instant.now());
        notificationKafka.send("commerce.notification-events", orderId == null ? userId : orderId, event)
                .whenComplete((result, error) -> {
                    if (error != null) log.error("Failed to publish notification event: userId={}, type={}",
                            userId, type, error);
                });
    }
    public DispatchRecord get(String orderId, String userId, boolean admin) {
        DispatchRecord record = dispatches.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatch not found"));
        if (!admin && !userId.equals(record.getUserId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's dispatch");
        return record;
    }
    public Map<String, Object> search(String userId, boolean admin, String status, String search,
                                      int page, int size, String sortBy, String direction) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 100");
        List<Criteria> filters = new ArrayList<>();
        if (!admin) filters.add(Criteria.where("userId").is(userId));
        if (status != null) filters.add(Criteria.where("status").is(status));
        if (search != null && !search.isBlank()) {
            String term = java.util.regex.Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(Criteria.where("_id").regex(term, "i"),
                    Criteria.where("trackingNumber").regex(term, "i"),
                    Criteria.where("shippingAddress.city").regex(term, "i")));
        }
        Query query = new Query(filters.isEmpty() ? new Criteria() :
                new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        long total = mongo.count(query, DispatchRecord.class);
        String field = Map.of("status", "status", "createdAt", "createdAt", "updatedAt", "updatedAt")
                .getOrDefault(sortBy, "createdAt");
        query.with(Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, field))
                .skip((long) page * size).limit(size);
        List<DispatchRecord> content = mongo.find(query, DispatchRecord.class);
        int pages = (int) Math.ceil((double) total / size);
        return Map.of("content", content, "page", page, "size", size, "totalElements", total,
                "totalPages", pages, "first", page == 0, "last", page + 1 >= Math.max(1, pages));
    }
}
