package com.micuota.mvp.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * fullName solo hace falta la primera vez (alta). tenantSlug solo si el email tiene varias cuentas.
 */
public record EmailCodeVerifyRequest(
    @NotBlank @Email String email,
    @NotBlank String code,
    String fullName,
    String phone,
    String activity,
    String tenantSlug
) {
}
