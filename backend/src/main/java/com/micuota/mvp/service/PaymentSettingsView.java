package com.micuota.mvp.service;

/**
 * mercadoPagoConnection: OAUTH (cobra con comision de MiCuota), MANUAL (Access Token pegado, sin comision) o NONE.
 * platformFeeBps: comision total de MiCuota por cobro, en puntos basicos.
 */
public record PaymentSettingsView(
    String transferAlias,
    String transferBankName,
    boolean mercadoPagoConfigured,
    String mercadoPagoPublicKey,
    String mercadoPagoConnection,
    boolean mercadoPagoOAuthAvailable,
    int platformFeeBps
) {
}
