package com.micuota.mvp.domain;

public enum PaymentProviderType {
    MERCADOPAGO,
    // Transferencia, giro o efectivo: no pasa por ningun procesador y lo concilia el profesional.
    MANUAL,
    PROMETEO,
    WOOCOMMERCE,
    STRIPE,
    SQUARE,
    PLEXO
}
