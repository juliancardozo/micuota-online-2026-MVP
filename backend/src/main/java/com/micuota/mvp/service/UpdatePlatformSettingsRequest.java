package com.micuota.mvp.service;

/** Campos en null no se tocan. Para pasar a modo real hay que mandar confirm = "REAL". */
public record UpdatePlatformSettingsRequest(
    Boolean live,
    Boolean bankPayments,
    Boolean mercadoPagoOAuth,
    String confirm
) {
}
