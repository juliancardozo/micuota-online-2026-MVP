package com.micuota.mvp.service;

import com.micuota.mvp.domain.UserRole;

/**
 * teacherProfileId y mercadoPagoConnection (OAUTH, MANUAL o NONE) solo se informan para TEACHER;
 * teacherProfileId es el que aparece como teacherId en los cobros.
 */
public record BackofficeUserView(
    Long id,
    String fullName,
    String email,
    UserRole role,
    Long teacherProfileId,
    String mercadoPagoConnection
) {
}
