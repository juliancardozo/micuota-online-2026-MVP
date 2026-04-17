package com.micuota.mvp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CrmApiKeyService {

    private final String configuredApiKey;

    public CrmApiKeyService(@Value("${app.crm.api-key:micuota-crm-dev-key}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    public void requireAuthorized(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization Bearer requerida para CRM");
        }

        String providedKey = authorizationHeader.substring("Bearer ".length()).trim();
        if (providedKey.isEmpty() || !configuredApiKey.equals(providedKey)) {
            throw new IllegalArgumentException("CRM API key invalida");
        }
    }
}
