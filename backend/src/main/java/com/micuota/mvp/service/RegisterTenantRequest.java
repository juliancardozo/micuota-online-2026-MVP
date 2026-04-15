package com.micuota.mvp.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterTenantRequest(
    @NotBlank String tenantName,
    String tenantSlug,
    @NotBlank String fullName,
    @NotBlank @Email String email,
    @NotBlank String password,
    String mpAccessToken
) {
}
