package com.micuota.mvp.integration.prometeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.prometeo")
public class PrometeoProperties {

    private boolean enabled = true;
    private boolean sandbox = true;
    private String baseUrl = "https://account-validation.sandbox.prometeoapi.com";
    private String apiKey = "";
    // Banking API: el cliente entra a su banco a traves de MiCuota (proxy) y transfiere a la cuenta del profesional.
    private String bankingBaseUrl = "https://banking.sandbox.prometeoapi.com";
    private boolean bankPaymentsEnabled = false;
    private String country = "UY";
    // En sandbox los bancos destino se llaman "Bank A/B/C": se usa este codigo de institucion.
    private String sandboxDestinationInstitution = "012";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getBankingBaseUrl() {
        return bankingBaseUrl;
    }

    public void setBankingBaseUrl(String bankingBaseUrl) {
        this.bankingBaseUrl = bankingBaseUrl;
    }

    public boolean isBankPaymentsEnabled() {
        return bankPaymentsEnabled;
    }

    public void setBankPaymentsEnabled(boolean bankPaymentsEnabled) {
        this.bankPaymentsEnabled = bankPaymentsEnabled;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getSandboxDestinationInstitution() {
        return sandboxDestinationInstitution;
    }

    public void setSandboxDestinationInstitution(String sandboxDestinationInstitution) {
        this.sandboxDestinationInstitution = sandboxDestinationInstitution;
    }
}
