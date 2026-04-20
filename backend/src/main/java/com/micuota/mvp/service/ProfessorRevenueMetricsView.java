package com.micuota.mvp.service;

import java.math.BigDecimal;

public record ProfessorRevenueMetricsView(
    String planCode,
    BigDecimal grossRevenueSuccess,
    BigDecimal platformProcessingFees,
    BigDecimal platformAdvancedFeatureFees,
    BigDecimal teacherNetRevenue,
    double takeRatePercent,
    double advancedFeatureFeePercent,
    boolean recoveryAutomationEnabled,
    boolean advancedAnalyticsEnabled,
    boolean integrationsEnabled
) {
}
