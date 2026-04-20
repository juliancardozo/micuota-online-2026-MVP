package com.micuota.mvp.service;

import java.util.Map;

public record PaymentKpiFrameworkView(
    ActivationKpis activation,
    ConversionKpis conversion,
    RetentionKpis retention,
    RevenueKpis revenue,
    RiskKpis risk
) {
    public record ActivationKpis(
        long totalProfessionals,
        long professionalsWithFirstPayment,
        double professionalsFirstPaymentUnder24hPercent,
        double avgHoursToFirstSuccessfulPayment
    ) {
    }

    public record ConversionKpis(
        double overallSuccessRatePercent,
        Map<String, Double> successRateByMethodPercent,
        double checkoutAbandonmentRatePercent
    ) {
    }

    public record RetentionKpis(
        double churnRateWithRecurringPercent,
        double churnRateWithoutRecurringPercent,
        double professionalsWithMonthlyRenewalPercent
    ) {
    }

    public record RevenueKpis(
        double monthlyTpv,
        double netRevenueTakeRatePercent,
        Map<String, Double> arpaByVertical
    ) {
    }

    public record RiskKpis(
        double disputeChargebackRatioPercent,
        Map<String, Double> failureRateByReasonPercent
    ) {
    }
}
