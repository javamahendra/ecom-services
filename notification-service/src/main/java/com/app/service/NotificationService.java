package com.app.service;

import com.app.entity.NotificationRecord;
import com.app.event.NotificationEvent;
import com.app.event.UserEvent;
import com.app.repository.NotificationRepository;
import com.app.request.ScheduleNotificationRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import jakarta.mail.internet.MimeMessage;
import org.springframework.web.client.RestClient;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.bson.Document;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final MongoTemplate mongo;
    private final NotificationRepository notifications;
    private final NotificationTemplateCatalog templates;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String smsUrl;
    private final String smsToken;
    private final ZoneId deliveryZone;
    private final int deliveryBatchSize;
    private final RestClient restClient = RestClient.create();

    public NotificationService(MongoTemplate mongo, NotificationRepository notifications,
                               NotificationTemplateCatalog templates,
                               @Value("${notification.email.from:no-reply@example.com}") String mailFrom,
                               @Value("${notification.email.host:}") String mailHost,
                               @Value("${notification.email.port:587}") int mailPort,
                               @Value("${notification.email.username:}") String mailUsername,
                               @Value("${notification.email.password:}") String mailPassword,
                               @Value("${notification.email.starttls:true}") boolean mailStartTls,
                               @Value("${notification.sms.provider-url:}") String smsUrl,
                               @Value("${notification.sms.auth-token:}") String smsToken,
                               @Value("${notification.delivery.zone:Asia/Kolkata}") String deliveryZone,
                               @Value("${notification.delivery.batch-size:500}") int deliveryBatchSize) {
        this.mongo = mongo;
        this.notifications = notifications;
        this.templates = templates;
        this.mailFrom = mailFrom;
        this.mailSender = createMailSender(mailHost, mailPort, mailUsername, mailPassword, mailStartTls);
        this.smsUrl = smsUrl;
        this.smsToken = smsToken;
        this.deliveryZone = ZoneId.of(deliveryZone);
        this.deliveryBatchSize = Math.max(1, Math.min(deliveryBatchSize, 5000));
    }

    public void process(NotificationEvent event) {
        process(event.eventId(), event.eventType(), event.userId(), event.email(), event.phone(),
                event.orderId(), event.data());
    }

    public void processUser(UserEvent event) {
        process(event.eventId(), event.eventType(), event.userId(), event.email(), event.phone(),
                null, event.data());
    }

    public void processOrder(com.app.event.OrderEvent event) {
        String type = switch (event.eventType()) {
            case "ORDER_CREATED" -> "ORDER_CREATED";
            case "ORDER_CANCELLED" -> "ORDER_CANCELLED";
            default -> "ORDER_STATUS_UPDATED";
        };
        Map<String, String> data = new LinkedHashMap<>();
        data.put("name", event.shippingAddress() == null ? "" : event.shippingAddress().recipientName());
        data.put("items", event.items().stream().map(i -> i.productName() + " (" + i.color() + ") x" + i.quantity())
                .collect(java.util.stream.Collectors.joining(", ")));
        data.put("total", event.total() == null ? "" : event.total().toPlainString());
        data.put("status", "ORDER_CANCELLED".equals(event.eventType()) ? "Cancellation requested" : "Processing");
        data.put("trackingNumber", "");
        data.put("message", "We will update you when the order changes.");
        process(event.eventId(), type, event.userId(), event.email(),
                event.shippingAddress() == null ? null : event.shippingAddress().phone(),
                event.orderId(), data);
    }

    public void processInventory(com.app.event.InventoryEvent event) {
        String type = "RESERVED".equals(event.status()) ? "INVENTORY_RESERVED"
                : "REJECTED".equals(event.status()) ? "INVENTORY_REJECTED"
                : "CANCELLED".equals(event.status()) ? "ORDER_CANCELLED" : null;
        if (type == null) return;
        Map<String, String> data = Map.of("name", event.name() == null ? "" : event.name(),
                "orderId", event.orderId(),
                "status", event.status(), "total", "", "trackingNumber", "",
                "message", event.message() == null ? "" : event.message());
        process(event.eventId(), type, event.userId(), event.email(), event.phone(), event.orderId(), data);
    }

    private void process(String eventId, String eventType, String userId, String email, String phone,
                         String orderId, Map<String, String> eventData) {
        NotificationRecord prior = userId == null ? null : notifications
                .findFirstByUserIdAndRecipientIsNotNullOrderByCreatedAtDesc(userId).orElse(null);
        if ((email == null || email.isBlank()) && prior != null) email = prior.getRecipient();
        if ((phone == null || phone.isBlank()) && prior != null) phone = prior.getPhone();
        if (eventData == null) eventData = Map.of();
        Map<String, String> values = new LinkedHashMap<>(eventData);
        values.putIfAbsent("name", "");
        values.putIfAbsent("username", "");
        values.putIfAbsent("orderId", orderId == null ? "" : orderId);
        values.putIfAbsent("status", eventType);
        values.putIfAbsent("total", "");
        values.putIfAbsent("items", "");
        values.putIfAbsent("trackingNumber", "");
        values.putIfAbsent("message", "");

        NotificationRecord record = notifications.findById(eventId).orElse(null);
        if (record == null) {
            record = notifications.save(new NotificationRecord(eventId, orderId, userId, email, phone,
                    eventType, values.getOrDefault("message", eventType), values,
                    LocalDate.now(deliveryZone)));
        }
        log.info("Notification queued: eventId={}, type={}, deliveryDate={}",
                eventId, eventType, record.getDeliveryDate());
    }

    public NotificationRecord schedule(ScheduleNotificationRequest request) {
        if (!templates.supports(request.eventType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported notification event type");
        }
        LocalDate today = LocalDate.now(deliveryZone);
        if (request.deliveryDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryDate cannot be in the past");
        }
        Map<String, String> values = new LinkedHashMap<>(request.data());
        values.putIfAbsent("orderId", request.orderId() == null ? "" : request.orderId());
        values.putIfAbsent("status", request.eventType());
        values.putIfAbsent("name", "");
        values.putIfAbsent("username", "");
        values.putIfAbsent("total", "");
        values.putIfAbsent("items", "");
        values.putIfAbsent("trackingNumber", "");
        values.putIfAbsent("message", "");
        String id = java.util.UUID.randomUUID().toString();
        NotificationRecord record = new NotificationRecord(id, request.orderId(), request.userId(),
                request.email(), request.phone(), request.eventType(),
                values.get("message"), values, request.deliveryDate());
        NotificationRecord saved = notifications.save(record);
        log.info("Notification scheduled: eventId={}, type={}, deliveryDate={}",
                id, request.eventType(), request.deliveryDate());
        return saved;
    }

    public int deliverDue(LocalDate date) {
        int delivered = 0;
        while (true) {
            List<NotificationRecord> batch = notifications
                    .findByDeliveryDateLessThanEqualAndDeliveryStatusInOrderByDeliveryDateAscCreatedAtAsc(
                            date, List.of("PENDING"),
                            org.springframework.data.domain.PageRequest.of(0, deliveryBatchSize));
            if (batch.isEmpty()) return delivered;
            for (NotificationRecord record : batch) {
                if (!deliver(record)) continue;
                delivered++;
            }
            if (batch.size() < deliveryBatchSize) return delivered;
        }
    }

    public int deliverOnDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(deliveryZone))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deliver future notifications now");
        }
        return deliverDue(date);
    }

    public NotificationRecord reschedule(String eventId, LocalDate date) {
        if (date.isBefore(LocalDate.now(deliveryZone))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryDate cannot be in the past");
        }
        NotificationRecord record = notifications.findById(eventId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!List.of("PENDING", "FAILED").contains(record.getDeliveryStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending or failed notifications can be rescheduled");
        }
        record.scheduleFor(date);
        NotificationRecord saved = notifications.save(record);
        log.info("Notification rescheduled: eventId={}, deliveryDate={}", eventId, date);
        return saved;
    }

    public List<Document> deliveryDays(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("deliveryDate").gte(from).lte(to)),
                Aggregation.group("deliveryDate", "deliveryStatus").count().as("count"),
                Aggregation.project("count")
                        .and("_id.deliveryDate").as("deliveryDate")
                        .and("_id.deliveryStatus").as("deliveryStatus")
                        .andExclude("_id"),
                Aggregation.sort(Sort.Direction.ASC, "deliveryDate", "deliveryStatus"));
        return mongo.aggregate(aggregation, "notifications", Document.class).getMappedResults();
    }

    private boolean deliver(NotificationRecord record) {
        Map<String, String> values = new LinkedHashMap<>(
                record.getData() == null ? Map.of() : record.getData());
        values.putIfAbsent("name", "");
        values.putIfAbsent("username", "");
        values.putIfAbsent("orderId", record.getOrderId() == null ? "" : record.getOrderId());
        values.putIfAbsent("status", record.getType());
        values.putIfAbsent("total", "");
        values.putIfAbsent("items", "");
        values.putIfAbsent("trackingNumber", "");
        values.putIfAbsent("message", record.getMessage() == null ? "" : record.getMessage());
        record.startDelivery();
        notifications.save(record);
        try {
            deliverEmail(record, record.getType(), record.getRecipient(), values);
            deliverSms(record, record.getType(), record.getPhone(), values);
            record.finishDelivery("FAILED".equals(record.getEmailStatus())
                    || "FAILED".equals(record.getSmsStatus()));
            notifications.save(record);
            if ("COMPLETED".equals(record.getDeliveryStatus())) {
                log.info("Notification delivery completed: eventId={}, scheduledDate={}",
                        record.getEventId(), record.getDeliveryDate());
                return true;
            }
            log.warn("Notification delivery failed and will be retried: eventId={}", record.getEventId());
            return false;
        } catch (RuntimeException exception) {
            record.finishDelivery(true);
            notifications.save(record);
            log.error("Daily notification delivery failed: eventId={}", record.getEventId(), exception);
            return false;
        }
    }

    private void deliverEmail(NotificationRecord record, String type, String email, Map<String, String> values) {
        if ("SENT".equals(record.getEmailStatus()) || "SKIPPED".equals(record.getEmailStatus())) return;
        if (email == null || email.isBlank() || mailSender == null) {
            record.updateEmail("SKIPPED", email == null || email.isBlank() ? "No email address" : "SMTP is not configured");
            notifications.save(record);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper mail = new MimeMessageHelper(message, true, "UTF-8");
            mail.setFrom(mailFrom);
            mail.setTo(email);
            mail.setSubject(templates.render(type, "email", "subject", values));
            mail.setText(templates.render(type, "email", "body", values), true);
            mail.addInline("company-logo",
                    new ClassPathResource("templates/brand/company-logo.svg"),
                    "image/svg+xml");
            mailSender.send(message);
            record.updateEmail("SENT", null);
            log.info("Notification email sent: eventId={}, type={}", record.getEventId(), type);
        } catch (Exception exception) {
            record.updateEmail("FAILED", exception.getClass().getSimpleName());
            notifications.save(record);
            log.error("Notification email delivery failed: eventId={}, type={}", record.getEventId(), type, exception);
        }
    }

    private void deliverSms(NotificationRecord record, String type, String phone, Map<String, String> values) {
        if ("SENT".equals(record.getSmsStatus()) || "SKIPPED".equals(record.getSmsStatus())) return;
        if (phone == null || phone.isBlank() || smsUrl.isBlank()) {
            record.updateSms("SKIPPED", phone == null || phone.isBlank() ? "No phone number" : "SMS provider is not configured");
            notifications.save(record);
            return;
        }
        try {
            String text = templates.render(type, "sms", null, values);
            var request = restClient.post().uri(smsUrl).contentType(MediaType.APPLICATION_JSON);
            if (!smsToken.isBlank()) request.header("Authorization", "Bearer " + smsToken);
            request.body(Map.of("to", phone, "message", text)).retrieve().toBodilessEntity();
            record.updateSms("SENT", null);
            log.info("Notification text sent: eventId={}, type={}", record.getEventId(), type);
        } catch (RuntimeException exception) {
            record.updateSms("FAILED", exception.getClass().getSimpleName());
            notifications.save(record);
            log.error("Notification text delivery failed: eventId={}, type={}", record.getEventId(), type, exception);
        }
    }

    private static JavaMailSender createMailSender(String host, int port, String username,
                                                    String password, boolean startTls) {
        if (host == null || host.isBlank()) return null;
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.getJavaMailProperties().put("mail.smtp.auth", !username.isBlank());
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", startTls);
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "5000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "5000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "5000");
        return sender;
    }

    public NotificationRecord retry(String eventId) {
        NotificationRecord record = notifications.findById(eventId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        record.retrySkippedChannels();
        notifications.save(record);
        deliver(record);
        return notifications.findById(eventId).orElse(record);
    }

    public Map<String, Object> search(String userId, boolean admin, String orderId, String type,
                                      String search, LocalDate deliveryDate, String deliveryStatus,
                                      int page, int size, String sortBy, String direction) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 100");
        List<Criteria> filters = new ArrayList<>();
        if (!admin) filters.add(Criteria.where("userId").is(userId));
        if (orderId != null) filters.add(Criteria.where("orderId").is(orderId));
        if (type != null) filters.add(Criteria.where("type").is(type));
        if (deliveryDate != null) filters.add(Criteria.where("deliveryDate").is(deliveryDate));
        if (deliveryStatus != null) filters.add(Criteria.where("deliveryStatus").is(deliveryStatus));
        if (search != null && !search.isBlank()) {
            String term = java.util.regex.Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(Criteria.where("message").regex(term, "i"),
                    Criteria.where("recipient").regex(term, "i"), Criteria.where("orderId").regex(term, "i")));
        }
        Query query = new Query(filters.isEmpty() ? new Criteria() :
                new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        long total = mongo.count(query, NotificationRecord.class);
        String field = "type".equals(sortBy) ? "type" : "createdAt";
        query.with(Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, field))
                .skip((long) page * size).limit(size);
        List<NotificationRecord> items = mongo.find(query, NotificationRecord.class);
        int pages = (int) Math.ceil((double) total / size);
        return Map.of("content", items, "page", page, "size", size, "totalElements", total,
                "totalPages", pages, "first", page == 0, "last", page + 1 >= Math.max(1, pages));
    }
}
