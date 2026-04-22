package com.micuota.mvp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.service.MercadoPagoService;
import com.micuota.mvp.service.PaymentService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MercadoPagoWebhookControllerTest {

    private static final String SECRET = "webhook-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PaymentService paymentService;

    @Mock
    private MercadoPagoService mercadoPagoService;

    private MercadoPagoWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new MercadoPagoWebhookController(paymentService, mercadoPagoService, objectMapper);
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
    }

    @Test
    void receiveValidatesSignatureFetchesPaymentAndUpdatesByExternalReference() throws Exception {
        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"987654321\"}}";
        JsonNode payment = objectMapper.readTree("""
            {"id":987654321,"status":"approved","external_reference":"MC-abc","preference_id":"123456789-abcd"}
            """);
        PaymentOperation operation = new PaymentOperation();
        when(paymentService.mercadoPagoAccessTokenForOperationReference("MC-abc", null))
            .thenReturn(java.util.Optional.of("teacher-mp-token"));
        when(mercadoPagoService.getPayment("teacher-mp-token", "987654321"))
            .thenReturn(new MercadoPagoService.MercadoPagoApiResponse(payment.toString(), payment));
        when(paymentService.updateStatusByExternalReference(eq("MC-abc"), eq(OperationStatus.SUCCESS), contains("mercadopago_webhook")))
            .thenReturn(operation);

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "request-123");
        headers.add("x-signature", "ts=1713389300,v1=" + signature("id:987654321;request-id:request-123;ts:1713389300;"));

        PaymentOperation result = controller.receive(headers, Map.of("externalReference", "MC-abc"), body);

        assertThat(result).isSameAs(operation);
        verify(paymentService).updateStatusByExternalReference(eq("MC-abc"), eq(OperationStatus.SUCCESS), contains("\"provider_reference\":\"123456789-abcd\""));
    }

    @Test
    void receiveFetchesPreapprovalAndUpdatesSubscriptionByExternalReference() throws Exception {
        String body = "{\"type\":\"preapproval\",\"data\":{\"id\":\"preapproval-123\"}}";
        JsonNode preapproval = objectMapper.readTree("""
            {"id":"preapproval-123","status":"authorized","external_reference":"MC-subscription"}
            """);
        PaymentOperation operation = new PaymentOperation();
        when(paymentService.mercadoPagoAccessTokenForOperationReference("MC-subscription", "preapproval-123"))
            .thenReturn(java.util.Optional.of("teacher-mp-token"));
        when(mercadoPagoService.getPreapproval("teacher-mp-token", "preapproval-123"))
            .thenReturn(new MercadoPagoService.MercadoPagoApiResponse(preapproval.toString(), preapproval));
        when(paymentService.updateStatusByExternalReference(eq("MC-subscription"), eq(OperationStatus.SUCCESS), contains("mercadopago_webhook")))
            .thenReturn(operation);

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "request-456");
        headers.add("x-signature", "ts=1713389301,v1=" + signature("id:preapproval-123;request-id:request-456;ts:1713389301;"));

        PaymentOperation result = controller.receive(headers, Map.of("externalReference", "MC-subscription"), body);

        assertThat(result).isSameAs(operation);
        verify(paymentService).updateStatusByExternalReference(eq("MC-subscription"), eq(OperationStatus.SUCCESS), contains("\"provider_reference\":\"preapproval-123\""));
    }

    private String signature(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
