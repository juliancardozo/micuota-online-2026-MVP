package com.micuota.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SquareGateway implements PaymentProviderGateway {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.payments.external-providers-enabled:false}")
    private boolean externalProvidersEnabled;

    @Value("${app.payments.square.access-token:}")
    private String squareAccessToken;

    @Value("${app.payments.square.location-id:}")
    private String squareLocationId;

    @Value("${app.payments.square.use-sandbox:true}")
    private boolean squareUseSandbox;

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.SQUARE;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String ref = "SQ-" + UUID.randomUUID().toString().substring(0, 8);

        if (!externalProvidersEnabled
            || squareAccessToken == null || squareAccessToken.isBlank()
            || squareLocationId == null || squareLocationId.isBlank()) {
            return sandboxResult(flowType, callbackBaseUrl, ref);
        }

        try {
            long amount = request.amount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            String successUrl = callbackBaseUrl + "/api/callbacks/success/by-reference?providerReference=" + ref;

            String body = """
                {
                  "idempotency_key": "%s",
                  "quick_pay": {
                    "name": "%s",
                    "price_money": {
                      "amount": %d,
                      "currency": "%s"
                    }
                  },
                  "checkout_options": {
                    "redirect_url": "%s"
                  },
                  "pre_populated_data": {
                    "buyer_email": "%s"
                  }
                }
                """.formatted(
                    UUID.randomUUID(),
                    escapeJson(request.description()),
                    amount,
                    request.currency().toUpperCase(),
                    successUrl,
                    request.payerEmail() == null ? "" : escapeJson(request.payerEmail())
                );

            String baseUrl = squareUseSandbox
                ? "https://connect.squareupsandbox.com"
                : "https://connect.squareup.com";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/online-checkout/payment-links"))
                .header("Authorization", "Bearer " + squareAccessToken)
                .header("Square-Version", "2025-01-23")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Square devolvio status " + response.statusCode() + ": " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode paymentLink = json.path("payment_link");
            String checkoutUrl = paymentLink.path("url").asText("");
            if (checkoutUrl.isBlank()) {
                throw new IllegalStateException("Square no devolvio URL de payment link");
            }

            String providerRef = paymentLink.path("id").asText(ref);
            return new ProviderCheckoutResult(providerRef, checkoutUrl, response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo crear Payment Link en Square", ex);
        }
    }

    private ProviderCheckoutResult sandboxResult(PaymentFlowType flowType, String callbackBaseUrl, String ref) {
        String checkoutUrl = callbackBaseUrl + "/sandbox/square/checkout/" + ref;
        String raw = "{\"provider\":\"square\",\"flow\":\""
            + flowType
            + "\",\"mode\":\"payment_link\"}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
