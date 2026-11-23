package com.app.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public record ScheduleNotificationRequest(
        @NotBlank @Size(max = 60) String eventType,
        @NotBlank @Size(max = 255) String userId,
        @Email @Size(max = 254) String email,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$") String phone,
        @Size(max = 100) String orderId,
        @NotNull LocalDate deliveryDate,
        @NotNull Map<@Size(max = 60) String, @Size(max = 4000) String> data) {}
