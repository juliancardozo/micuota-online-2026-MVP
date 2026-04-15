package com.micuota.mvp.service;

import com.micuota.mvp.domain.UserRole;

public record BackofficeUserView(
    Long id,
    String fullName,
    String email,
    UserRole role
) {
}
