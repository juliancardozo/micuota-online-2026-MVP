package com.micuota.mvp.service;

import com.micuota.mvp.domain.UserRole;

public record AuthResponse(
    String token,
    Long tenantId,
    String tenantSlug,
    Long userId,
    UserRole role,
    String backofficeUrl
) {
}
