package com.app.controller;

import com.app.service.NotificationService;
import com.app.entity.NotificationRecord;
import com.app.request.ScheduleNotificationRequest;
import com.app.request.RescheduleNotificationRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping
    public Map<String, Object> search(Authentication auth,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(required = false) String deliveryStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_admin"));
        return notifications.search(auth.getName(), admin, orderId, type, search,
                deliveryDate, deliveryStatus, page, size, sortBy, direction);
    }

    @PostMapping("/schedule")
    public ResponseEntity<NotificationRecord> schedule(@Valid @RequestBody ScheduleNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notifications.schedule(request));
    }

    @PatchMapping("/{eventId}/schedule")
    public NotificationRecord reschedule(@PathVariable String eventId,
            @Valid @RequestBody RescheduleNotificationRequest request) {
        return notifications.reschedule(eventId, request.deliveryDate());
    }

    @GetMapping("/delivery/days")
    public Object deliveryDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return notifications.deliveryDays(from, to);
    }

    @PostMapping("/delivery/run")
    public Map<String, Object> deliverDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Map.of("date", date, "completed", notifications.deliverOnDate(date));
    }
    @PostMapping("/{eventId}/retry")
    public NotificationRecord retry(@PathVariable String eventId) {
        return notifications.retry(eventId);
    }
}
