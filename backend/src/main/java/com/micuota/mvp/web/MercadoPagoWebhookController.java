package com.micuota.mvp.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.service.MercadoPagoService;
import com.micuota.mvp.service.PaymentService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/callbacks/mercadopago")
public class MercadoPagoWebhookController {

    private final PaymentService paymentService;
    private final MercadoPagoService mercadoPagoService;
    private final ObjectMapper objectMapper;

    @Value("${app.payments.mercadopago.access-token:}")
    private String mercadoPagoAccessToken;

    @Value("${app.payments.mercadopago.webhook-secret:}")
    private String webhookSecret;

    public MercadoPagoWebhookController(
        PaymentService paymentService,
        MercadoPagoService mercadoPagoService,
        ObjectMapper objectMapper
    ) {
        this.paymentService = paymentService;
        this.mercadoPagoService = mercadoPagoService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public PaymentOperation receive(
        @RequestHeader HttpHeaders headers,
        @RequestParam Map<String, String> queryParams,
        @RequestBody(required = false) String rawBody
    ) {
        JsonNode payload = parsePayload(rawBody);
        String dataId = extractDataId(payload, queryParams);
        validateSignatureIfConfigured(headers, dataId);

        MercadoPagoLookup lookup = enrichFromMercadoPago(payload, queryParams, dataId);
        OperationStatus status = toOperationStatus(firstNonBlank(lookup.status(), text(payload, "status"), queryParams.get("status")));
        String rawEvent = buildRawEvent(headers, queryParams, payload, lookup);

        String externalReference = firstNonBlank(lookup.externalReference(), text(payload, "external_reference"), queryParams.get("external_reference"));
        if (externalReference != null && !externalReference.isBlank()) {
            return paymentService.updateStatusByExternalReference(externalReference, status, rawEvent);
        }

        String providerReference = firstNonBlank(lookup.providerReference(), dataIdForProviderReference(lookup.type(), dataId));
        if (providerReference != null && !providerReference.isBlank()) {
            return paymentService.updateStatusByProviderReference(providerReference, status, rawEvent);
        }

        throw new IllegalArgumentException("Webhook Mercado Pago sin referencia de operacion");
    }

    private MercadoPagoLookup enrichFromMercadoPago(JsonNode payload, Map<String, String> queryParams, String dataId) {
        String type = firstNonBlank(text(payload, "type"), text(payload, "topic"), queryParams.get("type"), queryParams.get("topic"));
        String status = firstNonBlank(text(payload, "status"), queryParams.get("status"));
        String externalReference = firstNonBlank(text(payload, "external_reference"), queryParams.get("external_reference"));
        String providerReference = firstNonBlank(text(payload, "preference_id"), queryParams.get("preference_id"));
        JsonNode providerLookup = null;
        String providerLookupError = null;

        if (isPaymentNotification(type) && dataId != null && !dataId.isBlank()) {
            if (mercadoPagoAccessToken == null || mercadoPagoAccessToken.isBlank()) {
                providerLookupError = "MERCADOPAGO_ACCESS_TOKEN no configurado para consultar payment";
            } else {
                try {
                    MercadoPagoService.MercadoPagoApiResponse response = mercadoPagoService.getPayment(mercadoPagoAccessToken, dataId);
                    providerLookup = response.body();
                    status = firstNonBlank(providerLookup.path("status").asText(""), status);
                    externalReference = firstNonBlank(providerLookup.path("external_reference").asText(""), externalReference);
                    providerReference = firstNonBlank(providerLookup.path("preference_id").asText(""), providerReference);
                } catch (RuntimeException exception) {
                    providerLookupError = exception.getMessage();
                }
            }
        } else if (isPreapprovalNotification(type) && dataId != null && !dataId.isBlank()) {
            providerReference = firstNonBlank(dataId, providerReference);
            if (mercadoPagoAccessToken == null || mercadoPagoAccessToken.isBlank()) {
                providerLookupError = "MERCADOPAGO_ACCESS_TOKEN no configurado para consultar preapproval";
            } else {
                try {
                    MercadoPagoService.MercadoPagoApiResponse response = mercadoPagoService.getPreapproval(mercadoPagoAccessToken, dataId);
                    providerLookup = response.body();
                    status = firstNonBlank(providerLookup.path("status").asText(""), status);
                    externalReference = firstNonBlank(providerLookup.path("external_reference").asText(""), externalReference);
                    providerReference = firstNonBlank(providerLookup.path("id").asText(""), providerReference);
                } catch (RuntimeException exception) {
                    providerLookupError = exception.getMessage();
                }
            }
        }

        return new MercadoPagoLookup(type, status, externalReference, providerReference, providerLookup, providerLookupError);
    }

    private void validateSignatureIfConfigured(HttpHeaders headers, String dataId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return;
        }

        String xSignature = headers.getFirst("x-signature");
        String xRequestId = headers.getFirst("x-request-id");
        if (xSignature == null || xSignature.isBlank() || xRequestId == null || xRequestId.isBlank() || dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("Webhook Mercado Pago sin headers o data.id para validar firma");
        }

        Map<String, String> signatureParts = parseSignature(xSignature);
        String ts = signatureParts.get("ts");
        String v1 = signatureParts.get("v1");
        if (ts == null || ts.isBlank() || v1 == null || v1.isBlank()) {
            throw new IllegalArgumentException("Webhook Mercado Pago con x-signature invalida");
        }

        String manifest = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        String expected = hmacSha256(manifest, webhookSecret);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), v1.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Firma de webhook Mercado Pago invalida");
        }
    }

    private Map<String, String> parseSignature(String xSignature) {
        Map<String, String> parts = new LinkedHashMap<>();
        for (String part : xSignature.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length == 2) {
                parts.put(keyValue[0], keyValue[1]);
            }
        }
        return parts;
    }

    private String hmacSha256(String manifest, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo validar firma Mercado Pago", exception);
        }
    }

    private JsonNode parsePayload(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Webhook Mercado Pago con JSON invalido");
        }
    }

    private String extractDataId(JsonNode payload, Map<String, String> queryParams) {
        String dataId = firstNonBlank(
            payload.path("data").path("id").asText(""),
            text(payload, "data.id"),
            text(payload, "id"),
            queryParams.get("data.id"),
            queryParams.get("id")
        );
        if (dataId != null && !dataId.isBlank()) {
            return dataId;
        }

        String resource = firstNonBlank(text(payload, "resource"), queryParams.get("resource"));
        if (resource == null || resource.isBlank()) {
            return null;
        }
        int lastSlash = resource.lastIndexOf('/');
        return lastSlash >= 0 ? resource.substring(lastSlash + 1) : resource;
    }

    private OperationStatus toOperationStatus(String mercadoPagoStatus) {
        if (mercadoPagoStatus == null || mercadoPagoStatus.isBlank()) {
            throw new IllegalArgumentException("Webhook Mercado Pago sin status");
        }

        return switch (mercadoPagoStatus.trim().toLowerCase()) {
            case "approved", "accredited", "authorized" -> OperationStatus.SUCCESS;
            case "pending", "in_process", "in_mediation" -> OperationStatus.PENDING;
            case "rejected", "cancelled", "canceled", "refunded", "charged_back", "paused" -> OperationStatus.FAILURE;
            default -> OperationStatus.PENDING;
        };
    }

    private String dataIdForProviderReference(String type, String dataId) {
        if (isPreapprovalNotification(type)) {
            return dataId;
        }
        return null;
    }

    private boolean isPaymentNotification(String type) {
        return type == null || type.isBlank() || "payment".equalsIgnoreCase(type);
    }

    private boolean isPreapprovalNotification(String type) {
        return "preapproval".equalsIgnoreCase(type) || "subscription_preapproval".equalsIgnoreCase(type);
    }

    private String buildRawEvent(
        HttpHeaders headers,
        Map<String, String> queryParams,
        JsonNode payload,
        MercadoPagoLookup lookup
    ) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("source", "mercadopago_webhook");
        event.put("x_request_id", headers.getFirst("x-request-id"));
        event.put("x_signature_present", headers.getFirst("x-signature") != null);
        event.set("query", objectMapper.valueToTree(queryParams));
        event.set("payload", payload);
        event.put("type", lookup.type());
        event.put("status", lookup.status());
        event.put("external_reference", lookup.externalReference());
        event.put("provider_reference", lookup.providerReference());
        if (lookup.providerLookup() != null) {
            event.set("provider_lookup", lookup.providerLookup());
        }
        if (lookup.providerLookupError() != null) {
            event.put("provider_lookup_error", lookup.providerLookupError());
        }
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar webhook Mercado Pago", exception);
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record MercadoPagoLookup(
        String type,
        String status,
        String externalReference,
        String providerReference,
        JsonNode providerLookup,
        String providerLookupError
    ) {
    }
}
