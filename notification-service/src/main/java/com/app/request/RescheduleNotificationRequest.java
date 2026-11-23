package com.app.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RescheduleNotificationRequest(@NotNull LocalDate deliveryDate) {}
