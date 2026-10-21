package com.micuota.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * En modo prueba solo se crean cobros con cuentas de prueba de Mercado Pago (usuarios TESTUSER), asi ningun
 * cobro mueve plata real. Se le pregunta a Mercado Pago quien es el dueño del token y se recuerda la respuesta.
 */
@Component
public class MercadoPagoAccountGuard {

    static final String NOT_TEST_ACCOUNT = "MiCuota está en modo prueba: solo se cobra con cuentas de prueba de Mercado Pago. "
        + "Conectá tu usuario de prueba (TESTUSER…) o cobrá por transferencia o efectivo.";

    private final MercadoPagoService mercadoPagoService;
    private final PlatformSettingsService platformSettingsService;
    private final Map<String, Boolean> testAccountByToken = new ConcurrentHashMap<>();

    public MercadoPagoAccountGuard(MercadoPagoService mercadoPagoService, PlatformSettingsService platformSettingsService) {
        this.mercadoPagoService = mercadoPagoService;
        this.platformSettingsService = platformSettingsService;
    }

    public void requireAllowed(String accessToken) {
        if (platformSettingsService.isLive()) {
            return;
        }
        if (!isTestAccount(accessToken)) {
            throw new TestModeViolationException(NOT_TEST_ACCOUNT);
        }
    }

    boolean isTestAccount(String accessToken) {
        if (accessToken.startsWith("TEST-")) {
            return true;
        }
        Boolean known = testAccountByToken.get(accessToken);
        if (known != null) {
            return known;
        }
        JsonNode me;
        try {
            me = mercadoPagoService.getCurrentUser(accessToken).body();
        } catch (RuntimeException exception) {
            throw new TestModeViolationException("MiCuota está en modo prueba y no pudimos verificar con Mercado Pago que tu cuenta sea de prueba. Probá de nuevo en un rato.");
        }
        boolean test = me.path("nickname").asText("").toUpperCase().startsWith("TESTUSER");
        for (JsonNode tag : me.path("tags")) {
            test = test || "test_user".equals(tag.asText());
        }
        testAccountByToken.put(accessToken, test);
        return test;
    }
}
