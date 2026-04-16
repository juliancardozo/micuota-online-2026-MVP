package com.micuota.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlexoGateway implements PaymentProviderGateway {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.payments.external-providers-enabled:false}")
    private boolean externalProvidersEnabled;

    @Value("${app.payments.plexo.api-url:}")
    private String plexoApiUrl;

    @Value("${app.payments.plexo.api-key:}")
    private String plexoApiKey;

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.PLEXO;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String ref = "PX-" + UUID.randomUUID().toString().substring(0, 8);

        if (!externalProvidersEnabled
            || plexoApiUrl == null || plexoApiUrl.isBlank()
            || plexoApiKey == null || plexoApiKey.isBlank()) {
            return sandboxResult(flowType, callbackBaseUrl, ref);
        }

        try {
            String successUrl = callbackBaseUrl + "/api/callbacks/success/by-reference?providerReference=" + ref;
            String pendingUrl = callbackBaseUrl + "/api/callbacks/pending/by-reference?providerReference=" + ref;
            String failureUrl = callbackBaseUrl + "/api/callbacks/failed/by-reference?providerReference=" + ref;

            String body = """
                {
                  "reference": "%s",
                  "description": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "payerEmail": "%s",
                  "callbacks": {
                    "success": "%s",
                    "pending": "%s",
                    "failure": "%s"
                  }
                }
                """.formatted(
                    ref,
                    escapeJson(request.description()),
                    request.amount(),
                    request.currency().toUpperCase(),
                    request.payerEmail() == null ? "" : escapeJson(request.payerEmail()),
                    successUrl,
                    pendingUrl,
                    failureUrl
                );

            String normalizedApiUrl = plexoApiUrl.endsWith("/")
                ? plexoApiUrl.substring(0, plexoApiUrl.length() - 1)
                : plexoApiUrl;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(normalizedApiUrl + "/links"))
                .header("Authorization", "Bearer " + plexoApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Plexo devolvio status " + response.statusCode() + ": " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String checkoutUrl = firstNonBlank(
                json.path("url").asText(""),
                json.path("linkUrl").asText(""),
                json.path("checkoutUrl").asText(""),
                json.path("payment_link").asText("")
            );
            if (checkoutUrl.isBlank()) {
                throw new IllegalStateException("Plexo no devolvio URL de link de pago");
            }

            String providerRef = firstNonBlank(
                json.path("id").asText(""),
                json.path("reference").asText(""),
                ref
            );

            return new ProviderCheckoutResult(providerRef, checkoutUrl, response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo crear Payment Link en Plexo", ex);
        }
    }

    private ProviderCheckoutResult sandboxResult(PaymentFlowType flowType, String callbackBaseUrl, String ref) {
        String checkoutUrl = callbackBaseUrl + "/sandbox/plexo/checkout/" + ref;
        String raw = "{\"provider\":\"plexo\",\"flow\":\""
            + flowType
            + "\",\"mode\":\"payment_link\"}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
