package com.micuota.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MercadoPagoGateway implements PaymentProviderGateway {

    private final MercadoPagoService mercadoPagoService;
    private final ObjectMapper objectMapper;

    public MercadoPagoGateway(MercadoPagoService mercadoPagoService, ObjectMapper objectMapper) {
        this.mercadoPagoService = mercadoPagoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.MERCADOPAGO;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String token = firstNonBlank(credentials.mercadoPagoAccessToken());
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("El profesor debe conectar su Access Token de Mercado Pago para usar este motor");
        }

        String externalReference = "MC-" + UUID.randomUUID();

        if (flowType == PaymentFlowType.SUBSCRIPTION) {
            return createPreapproval(token, request, callbackBaseUrl, externalReference);
        }

        return createPreference(token, request, callbackBaseUrl, externalReference);
    }

    private ProviderCheckoutResult createPreference(
        String token,
        CreatePaymentRequest request,
        String callbackBaseUrl,
        String externalReference
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode items = payload.putArray("items");
        ObjectNode item = items.addObject();
        item.put("title", request.description());
        item.put("quantity", 1);
        item.put("currency_id", normalizeCurrency(request.currency()));
        item.put("unit_price", request.amount());

        if (request.payerEmail() != null && !request.payerEmail().isBlank()) {
            payload.putObject("payer").put("email", request.payerEmail().trim().toLowerCase(Locale.ROOT));
        }

        ObjectNode backUrls = payload.putObject("back_urls");
        backUrls.put("success", callbackBaseUrl + "/api/callbacks/success/by-reference?externalReference=" + externalReference);
        backUrls.put("pending", callbackBaseUrl + "/api/callbacks/pending/by-reference?externalReference=" + externalReference);
        backUrls.put("failure", callbackBaseUrl + "/api/callbacks/failed/by-reference?externalReference=" + externalReference);
        payload.put("auto_return", "approved");
        payload.put("external_reference", externalReference);
        payload.put("notification_url", callbackBaseUrl + "/api/callbacks/mercadopago?externalReference=" + externalReference);

        MercadoPagoService.MercadoPagoApiResponse response = mercadoPagoService.createPreference(token, payload);
        String preferenceId = response.body().path("id").asText("");
        if (preferenceId.isBlank()) {
            throw new IllegalStateException("Mercado Pago no devolvio id de preferencia");
        }

        String checkoutUrl = firstNonBlank(
            response.body().path("init_point").asText(""),
            response.body().path("sandbox_init_point").asText("")
        );
        if (checkoutUrl.isBlank()) {
            throw new IllegalStateException("Mercado Pago no devolvio init_point para la preferencia");
        }

        return new ProviderCheckoutResult(preferenceId, externalReference, checkoutUrl, rawTrace("checkout_preference", externalReference, payload, response.body()));
    }

    private ProviderCheckoutResult createPreapproval(
        String token,
        CreatePaymentRequest request,
        String callbackBaseUrl,
        String externalReference
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("reason", request.description());
        payload.put("external_reference", externalReference);
        payload.put("back_url", callbackBaseUrl + "/api/callbacks/success/by-reference?externalReference=" + externalReference);
        payload.put("notification_url", callbackBaseUrl + "/api/callbacks/mercadopago?externalReference=" + externalReference);

        if (request.payerEmail() != null && !request.payerEmail().isBlank()) {
            payload.put("payer_email", request.payerEmail().trim().toLowerCase(Locale.ROOT));
        }

        ObjectNode autoRecurring = payload.putObject("auto_recurring");
        autoRecurring.put("frequency", 1);
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("transaction_amount", request.amount());
        autoRecurring.put("currency_id", normalizeCurrency(request.currency()));

        MercadoPagoService.MercadoPagoApiResponse response = mercadoPagoService.createPreapproval(token, payload);
        String preapprovalId = response.body().path("id").asText("");
        if (preapprovalId.isBlank()) {
            throw new IllegalStateException("Mercado Pago no devolvio id de preapproval");
        }

        String checkoutUrl = firstNonBlank(
            response.body().path("init_point").asText(""),
            response.body().path("sandbox_init_point").asText("")
        );
        if (checkoutUrl.isBlank()) {
            throw new IllegalStateException("Mercado Pago no devolvio init_point para la suscripcion");
        }

        return new ProviderCheckoutResult(preapprovalId, externalReference, checkoutUrl, rawTrace("preapproval", externalReference, payload, response.body()));
    }

    private String rawTrace(String operation, String externalReference, ObjectNode request, com.fasterxml.jackson.databind.JsonNode response) {
        ObjectNode trace = objectMapper.createObjectNode();
        trace.put("provider", "mercadopago");
        trace.put("operation", operation);
        trace.put("external_reference", externalReference);
        trace.set("request", request);
        trace.set("response", response);
        try {
            return objectMapper.writeValueAsString(trace);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar respuesta de Mercado Pago", exception);
        }
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? "UYU" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
