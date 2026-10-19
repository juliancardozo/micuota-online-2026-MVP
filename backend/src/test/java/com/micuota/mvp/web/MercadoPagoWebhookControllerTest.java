package com.micuota.mvp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.http.ResponseEntity;
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
    void receiveWithoutSecretStillTakesStatusFromMercadoPagoApi() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"321\"},\"status\":\"approved\",\"external_reference\":\"MC-forjado\"}";
        JsonNode payment = objectMapper.readTree("""
            {"id":321,"status":"rejected","external_reference":"MC-abc","preference_id":"pref-1"}
            """);
        PaymentOperation operation = new PaymentOperation();
        when(paymentService.mercadoPagoAccessTokenForOperationReference("MC-abc", null))
            .thenReturn(java.util.Optional.of("teacher-mp-token"));
        when(mercadoPagoService.getPayment("teacher-mp-token", "321"))
            .thenReturn(new MercadoPagoService.MercadoPagoApiResponse(payment.toString(), payment));
        when(paymentService.updateStatusByExternalReference(eq("MC-abc"), eq(OperationStatus.FAILURE), contains("mercadopago_webhook")))
            .thenReturn(operation);

        ResponseEntity<Map<String, Object>> result = controller.receive(new HttpHeaders(), Map.of("externalReference", "MC-abc"), body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(paymentService, never()).updateStatusByExternalReference(eq("MC-forjado"), any(), any());
    }

    @Test
    void signatureManifestLowercasesAlphanumericIdAndSkipsMissingValues() {
        assertThat(MercadoPagoWebhookController.signatureManifest("2C938084ABC", "req-1", "1700"))
            .isEqualTo("id:2c938084abc;request-id:req-1;ts:1700;");
        assertThat(MercadoPagoWebhookController.signatureManifest("123", null, "1700"))
            .isEqualTo("id:123;ts:1700;");
        assertThat(MercadoPagoWebhookController.signatureManifest(null, "req-1", "1700"))
            .isEqualTo("request-id:req-1;ts:1700;");
    }

    @Test
    void receiveAcceptsSignedPreapprovalWithUppercaseId() throws Exception {
        String body = "{\"type\":\"preapproval\",\"data\":{\"id\":\"2C938084ABC\"}}";
        JsonNode preapproval = objectMapper.readTree("""
            {"id":"2C938084ABC","status":"authorized","external_reference":"MC-sub"}
            """);
        when(paymentService.mercadoPagoAccessTokenForOperationReference("MC-sub", "2C938084ABC"))
            .thenReturn(java.util.Optional.of("teacher-mp-token"));
        when(mercadoPagoService.getPreapproval("teacher-mp-token", "2C938084ABC"))
            .thenReturn(new MercadoPagoService.MercadoPagoApiResponse(preapproval.toString(), preapproval));
        when(paymentService.updateStatusByExternalReference(eq("MC-sub"), eq(OperationStatus.SUCCESS), contains("mercadopago_webhook")))
            .thenReturn(new PaymentOperation());

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "request-abc");
        headers.add("x-signature", "ts=1713389304,v1=" + signature("id:2c938084abc;request-id:request-abc;ts:1713389304;"));

        assertThat(controller.receive(headers, Map.of("externalReference", "MC-sub"), body).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void receiveRejectsInvalidSignature() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "request-123");
        headers.add("x-signature", "ts=1713389300,v1=deadbeef");

        assertThatThrownBy(() -> controller.receive(headers, Map.of(), "{\"type\":\"payment\",\"data\":{\"id\":\"1\"}}"))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(paymentService, mercadoPagoService);
    }

    @Test
    void receiveIgnoresPayloadStatusAndPropagatesLookupFailure() throws Exception {
        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"555\"},\"status\":\"approved\",\"external_reference\":\"MC-abc\"}";
        when(paymentService.mercadoPagoAccessTokenForOperationReference("MC-abc", null))
            .thenReturn(java.util.Optional.of("teacher-mp-token"));
        when(mercadoPagoService.getPayment("teacher-mp-token", "555"))
            .thenThrow(new IllegalStateException("Mercado Pago devolvio status 500"));

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "request-789");
        headers.add("x-signature", "ts=1713389302,v1=" + signature("id:555;request-id:request-789;ts:1713389302;"));

        assertThatThrownBy(() -> controller.receive(headers, Map.of("externalReference", "MC-abc"), body))
            .isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).updateStatusByExternalReference(any(), any(), any());
    }

    @Test
    void receiveIgnoresUnsupportedNotificationType() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "request-999");
        headers.add("x-signature", "ts=1713389303,v1=" + signature("id:777;request-id:request-999;ts:1713389303;"));

        ResponseEntity<Map<String, Object>> result = controller.receive(
            headers,
            Map.of(),
            "{\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":\"777\"}}"
        );

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).containsEntry("ignored", true);
        verifyNoInteractions(mercadoPagoService);
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

        ResponseEntity<Map<String, Object>> result = controller.receive(headers, Map.of("externalReference", "MC-abc"), body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
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

        ResponseEntity<Map<String, Object>> result = controller.receive(headers, Map.of("externalReference", "MC-subscription"), body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
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
