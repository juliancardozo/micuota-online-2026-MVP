package com.micuota.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripeGateway implements PaymentProviderGateway {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.payments.external-providers-enabled:false}")
    private boolean externalProvidersEnabled;

    @Value("${app.payments.stripe.secret-key:}")
    private String stripeSecretKey;

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.STRIPE;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String ref = "ST-" + UUID.randomUUID().toString().substring(0, 8);

        if (!externalProvidersEnabled || stripeSecretKey == null || stripeSecretKey.isBlank()) {
            return sandboxResult(flowType, callbackBaseUrl, ref);
        }

        try {
            long unitAmount = request.amount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            String successUrl = callbackBaseUrl + "/api/callbacks/success/by-reference?providerReference=" + ref;

            String body = "line_items[0][price_data][currency]=" + encode(request.currency().toLowerCase())
                + "&line_items[0][price_data][unit_amount]=" + unitAmount
                + "&line_items[0][price_data][product_data][name]=" + encode(request.description())
                + "&line_items[0][quantity]=1"
                + "&metadata[provider_reference]=" + encode(ref)
                + "&after_completion[type]=redirect"
                + "&after_completion[redirect][url]=" + encode(successUrl);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.stripe.com/v1/payment_links"))
                .header("Authorization", "Bearer " + stripeSecretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Stripe devolvio status " + response.statusCode() + ": " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String checkoutUrl = json.path("url").asText("");
            if (checkoutUrl.isBlank()) {
                throw new IllegalStateException("Stripe no devolvio URL de payment link");
            }

            String providerRef = json.path("id").asText(ref);
            return new ProviderCheckoutResult(providerRef, checkoutUrl, response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo crear Payment Link en Stripe", ex);
        }
    }

    private ProviderCheckoutResult sandboxResult(PaymentFlowType flowType, String callbackBaseUrl, String ref) {
        String checkoutUrl = callbackBaseUrl + "/sandbox/stripe/checkout/" + ref;
        String raw = "{\"provider\":\"stripe\",\"flow\":\""
            + flowType
            + "\",\"mode\":\"payment_link\"}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
