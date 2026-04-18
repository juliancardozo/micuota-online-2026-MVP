package com.micuota.mvp.service;

public record LaunchpadUsageView(
    long teachers,
    long students,
    long courses,
    long enrollments,
    long payments,
    long successfulPayments,
    long subscriptions,
    long pendingPayments,
    long remainingStudentSlots
) {
}
