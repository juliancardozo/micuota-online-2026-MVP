package com.micuota.mvp.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.service.MercadoPagoPaymentDetails;
import com.micuota.mvp.service.MercadoPagoService;
import com.micuota.mvp.service.PaymentService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

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
    public ResponseEntity<Map<String, Object>> receive(
        @RequestHeader HttpHeaders headers,
        @RequestParam Map<String, String> queryParams,
        @RequestBody(required = false) String rawBody
    ) {
        JsonNode payload = parsePayload(rawBody);
        String dataId = extractDataId(payload, queryParams);
        validateSignature(headers, queryParams, dataId);
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("Webhook Mercado Pago sin data.id");
        }

        String type = firstNonBlank(text(payload, "type"), text(payload, "topic"), queryParams.get("type"), queryParams.get("topic"));
        boolean preapproval = isPreapprovalNotification(type);
        if (!preapproval && !isPaymentNotification(type)) {
            return ignored("Tipo de notificacion no soportado: " + type);
        }

        // Los hints solo sirven para elegir el token del profesor; el estado y las referencias salen de la API de Mercado Pago.
        String externalReferenceHint = firstNonBlank(queryParams.get("externalReference"), queryParams.get("external_reference"));
        String accessToken = paymentService
            .mercadoPagoAccessTokenForOperationReference(externalReferenceHint, preapproval ? dataId : null)
            .orElse(null);
        if (accessToken == null) {
            log.warn("Webhook Mercado Pago ignorado: sin operacion/token para dataId={} externalReference={}", dataId, externalReferenceHint);
            return ignored("Operacion sin Access Token Mercado Pago asociado");
        }

        JsonNode providerLookup = preapproval
            ? mercadoPagoService.getPreapproval(accessToken, dataId).body()
            : mercadoPagoService.getPayment(accessToken, dataId).body();
        MercadoPagoLookup lookup = new MercadoPagoLookup(
            type,
            firstNonBlank(providerLookup.path("status").asText("")),
            firstNonBlank(providerLookup.path("external_reference").asText("")),
            firstNonBlank(providerLookup.path(preapproval ? "id" : "preference_id").asText("")),
            providerLookup
        );
        OperationStatus status = toOperationStatus(lookup.status());
        String rawEvent = buildRawEvent(headers, queryParams, payload, lookup);
        // Id, comision y neto reales del pago segun Mercado Pago (no aplica a preapprovals).
        MercadoPagoPaymentDetails paymentDetails = preapproval ? null : MercadoPagoPaymentDetails.fromPayment(providerLookup);

        PaymentOperation operation;
        if (lookup.externalReference() != null) {
            operation = paymentService.updateStatusByExternalReference(lookup.externalReference(), status, rawEvent, paymentDetails);
        } else if (lookup.providerReference() != null) {
            operation = paymentService.updateStatusByProviderReference(lookup.providerReference(), status, rawEvent, paymentDetails);
        } else {
            return ignored("Mercado Pago no devolvio referencia de operacion");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operationId", operation.getId());
        body.put("status", operation.getStatus());
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> ignored(String reason) {
        return ResponseEntity.ok(Map.of("ignored", true, "reason", reason));
    }

    /**
     * Valida x-signature si hay secreto configurado. El estado nunca sale del payload sino de la API de Mercado Pago,
     * asi que sin secreto (p. ej. tokens de apps propias de cada profesor) el webhook se procesa igual de forma segura.
     */
    private void validateSignature(HttpHeaders headers, Map<String, String> queryParams, String dataId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return;
        }

        String xSignature = headers.getFirst("x-signature");
        if (xSignature == null || xSignature.isBlank()) {
            throw new IllegalArgumentException("Webhook Mercado Pago sin x-signature");
        }

        Map<String, String> signatureParts = parseSignature(xSignature);
        String ts = signatureParts.get("ts");
        String v1 = signatureParts.get("v1");
        if (ts == null || ts.isBlank() || v1 == null || v1.isBlank()) {
            throw new IllegalArgumentException("Webhook Mercado Pago con x-signature invalida");
        }

        String expected = hmacSha256(
            signatureManifest(firstNonBlank(queryParams.get("data.id"), dataId), headers.getFirst("x-request-id"), ts),
            webhookSecret
        );
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), v1.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Firma de webhook Mercado Pago invalida");
        }
    }

    // Template oficial: id:[data.id];request-id:[x-request-id];ts:[ts]; — data.id en minusculas y se omite cada valor ausente.
    static String signatureManifest(String dataId, String requestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId.trim().toLowerCase(Locale.ROOT)).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            manifest.append("request-id:").append(requestId.trim()).append(';');
        }
        manifest.append("ts:").append(ts.trim()).append(';');
        return manifest.toString();
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
        event.set("provider_lookup", lookup.providerLookup());
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
        JsonNode providerLookup
    ) {
    }
}
