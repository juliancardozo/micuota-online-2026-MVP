package com.micuota.mvp.service;

import java.math.BigDecimal;

/**
 * mercadoPagoMarketplaceFee: comision de MiCuota a descontar via marketplace_fee.
 * Solo se informa cuando el token es OAuth de la aplicacion marketplace; si no, es null.
 */
public record TeacherProviderCredentials(
    String mercadoPagoAccessToken,
    String mercadoPagoPublicKey,
    String prometeoApiKey,
    String wooCommerceApiKey,
    String transferAlias,
    String transferBankName,
    BigDecimal mercadoPagoMarketplaceFee
) {
}
