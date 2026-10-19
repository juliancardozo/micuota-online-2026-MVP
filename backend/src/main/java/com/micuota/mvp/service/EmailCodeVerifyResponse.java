package com.micuota.mvp.service;

import com.micuota.mvp.domain.UserRole;
import java.util.List;

/**
 * status:
 * SIGNED_IN: session tiene la sesion (cuenta existente o recien creada; created indica cual).
 * NEEDS_NAME: el codigo es correcto pero el email no tiene cuenta; reenviar con fullName y el mismo codigo.
 * CHOOSE_ACCOUNT: el email tiene varias cuentas; reenviar con tenantSlug y el mismo codigo.
 */
public record EmailCodeVerifyResponse(
    String status,
    boolean created,
    AuthResponse session,
    List<AccountOption> accounts
) {
    public record AccountOption(String tenantSlug, String tenantName, UserRole role) {
    }

    static EmailCodeVerifyResponse signedIn(AuthResponse session, boolean created) {
        return new EmailCodeVerifyResponse("SIGNED_IN", created, session, List.of());
    }

    static EmailCodeVerifyResponse needsName() {
        return new EmailCodeVerifyResponse("NEEDS_NAME", false, null, List.of());
    }

    static EmailCodeVerifyResponse chooseAccount(List<AccountOption> accounts) {
        return new EmailCodeVerifyResponse("CHOOSE_ACCOUNT", false, null, accounts);
    }
}
