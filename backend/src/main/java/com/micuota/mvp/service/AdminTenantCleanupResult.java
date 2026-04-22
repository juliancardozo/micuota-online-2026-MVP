package com.micuota.mvp.service;

public record AdminTenantCleanupResult(
    long deletedTenants,
    long keptTenants
) {
}
