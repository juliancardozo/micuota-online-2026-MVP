package com.micuota.mvp.service;

import java.util.List;

public record AdminResetResult(
    int deletedTenants,
    int deletedUsers,
    int deletedPayments,
    List<String> keptTenantSlugs
) {
}
