package com.micuota.mvp.service;

import java.math.BigDecimal;

public record ProfessorCoursePaymentSummary(
    Long courseId,
    String courseName,
    Long paymentsCount,
    BigDecimal totalAmount
) {
}
