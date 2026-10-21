package com.micuota.mvp.service;

/**
 * mercadoPagoConnection: OAUTH (cobra con comision de MiCuota), MANUAL (Access Token pegado, sin comision) o NONE.
 * platformFeeBps: comision total de MiCuota por cobro con Mercado Pago, en puntos basicos.
 */
public record PaymentSettingsView(
    String transferAlias,
    String transferBankName,
    String transferAccountNumber,
    String transferAccountHolder,
    String otherPaymentInstructions,
    boolean mercadoPagoConfigured,
    String mercadoPagoPublicKey,
    String mercadoPagoConnection,
    boolean mercadoPagoOAuthAvailable,
    int platformFeeBps,
    // Si esta activo, con banco, cuenta y titular completos el cliente puede pagar desde su banco (Prometeo).
    boolean bankPaymentsAvailable
) {
}
