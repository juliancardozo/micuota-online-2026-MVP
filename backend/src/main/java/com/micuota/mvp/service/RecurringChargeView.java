package com.micuota.mvp.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RecurringChargeView(
    Long id,
    Long studentUserId,
    String description,
    BigDecimal amount,
    String currency,
    int dayOfMonth,
    OffsetDateTime nextRunAt,
    boolean active
) {
}
