package com.micuota.mvp.service;

import java.util.List;

/**
 * keepTenantSlugs: cuentas que se conservan (la de plataforma se conserva siempre).
 * deleteAllPayments: si es true tambien se borran los cobros de las cuentas conservadas.
 */
public record AdminResetRequest(
    List<String> keepTenantSlugs,
    boolean deleteAllPayments
) {
}
