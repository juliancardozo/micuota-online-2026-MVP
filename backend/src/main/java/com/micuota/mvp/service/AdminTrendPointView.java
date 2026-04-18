package com.micuota.mvp.service;

public record AdminTrendPointView(
    String period,
    long revenueSuccess,
    long sessionsStarted,
    long leadsCreated
) {
}
