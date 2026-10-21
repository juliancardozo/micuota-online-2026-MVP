package com.micuota.mvp.service;

import com.micuota.mvp.domain.PlatformSetting;
import com.micuota.mvp.repository.PlatformSettingRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interruptores de la plataforma que maneja el administrador sin redesplegar.
 * Por defecto todo arranca en modo prueba: nada mueve plata real hasta que se pase a "real" a proposito.
 * Las variables de entorno siguen siendo el limite: un interruptor no prende algo que no esta configurado.
 */
@Service
public class PlatformSettingsService {

    public static final String MODE = "payments.mode";
    public static final String BANK_PAYMENTS = "feature.bank-payments";
    public static final String MERCADOPAGO_OAUTH = "feature.mercadopago-oauth";
    public static final String MODE_TEST = "TEST";
    public static final String MODE_LIVE = "LIVE";

    private final PlatformSettingRepository repository;

    public PlatformSettingsService(PlatformSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isLive() {
        return MODE_LIVE.equals(value(MODE, MODE_TEST));
    }

    @Transactional(readOnly = true)
    public boolean bankPaymentsOn() {
        return Boolean.parseBoolean(value(BANK_PAYMENTS, "false"));
    }

    @Transactional(readOnly = true)
    public boolean mercadoPagoOAuthOn() {
        return Boolean.parseBoolean(value(MERCADOPAGO_OAUTH, "true"));
    }

    @Transactional
    public void update(Boolean live, Boolean bankPayments, Boolean mercadoPagoOAuth) {
        if (live != null) {
            save(MODE, live ? MODE_LIVE : MODE_TEST);
        }
        if (bankPayments != null) {
            save(BANK_PAYMENTS, bankPayments.toString());
        }
        if (mercadoPagoOAuth != null) {
            save(MERCADOPAGO_OAUTH, mercadoPagoOAuth.toString());
        }
    }

    private String value(String key, String fallback) {
        return repository.findById(key).map(PlatformSetting::getValue).orElse(fallback);
    }

    private void save(String key, String value) {
        PlatformSetting setting = repository.findById(key).orElseGet(() -> {
            PlatformSetting created = new PlatformSetting();
            created.setKey(key);
            return created;
        });
        setting.setValue(value);
        setting.setUpdatedAt(OffsetDateTime.now());
        repository.save(setting);
    }
}
