package com.micuota.mvp.service;

/**
 * live: false = modo prueba (nada mueve plata real).
 * *Configured: si las variables de entorno lo permiten; el interruptor solo tiene efecto si es true.
 */
public record PlatformSettingsView(
    boolean live,
    boolean bankPayments,
    boolean bankPaymentsConfigured,
    boolean prometeoSandbox,
    boolean mercadoPagoOAuth,
    boolean mercadoPagoOAuthConfigured
) {
}
