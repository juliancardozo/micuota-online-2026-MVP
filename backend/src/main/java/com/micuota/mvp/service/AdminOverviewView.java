package com.micuota.mvp.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Resumen del panel de administracion. No incluye el tenant de plataforma ni al administrador. */
public record AdminOverviewView(
    OffsetDateTime generatedAt,
    long accounts,
    long professionals,
    long professionalsConnectedViaOAuth,
    long professionalsWithManualToken,
    long students,
    long paymentsCreated,
    long paymentsPaid,
    long paymentsPaidByHand,
    long paymentsPending,
    long leads,
    List<CurrencyTotal> totals,
    List<MonthPoint> months,
    List<RecentAccount> recentAccounts
) {

    /** Montos pagados y comision cobrada, separados por moneda (no se suman UYU con USD). */
    public record CurrencyTotal(String currency, long paidPayments, BigDecimal paidAmount, BigDecimal commissionCollected) {
    }

    public record MonthPoint(String period, long newAccounts, long paidPayments) {
    }

    public record RecentAccount(
        Long tenantId,
        String tenantName,
        String tenantSlug,
        OffsetDateTime createdAt,
        String contactName,
        String contactEmail,
        String mercadoPagoConnection
    ) {
    }
}
