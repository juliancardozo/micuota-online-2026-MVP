package com.micuota.mvp.service;

/**
 * Cada campo se actualiza solo si viene (null = no tocar; texto vacio = borrar), asi cada pantalla
 * manda solo lo suyo sin pisar el resto.
 */
public record UpdatePaymentSettingsRequest(
    String transferAlias,
    String transferBankName,
    String transferAccountNumber,
    String transferAccountHolder,
    String otherPaymentInstructions,
    String mercadoPagoAccessToken,
    String mercadoPagoPublicKey
) {
}
