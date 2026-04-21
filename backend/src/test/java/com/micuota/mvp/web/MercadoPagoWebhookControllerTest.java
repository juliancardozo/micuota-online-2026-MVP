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
        ReflectionTestUtils.setField(controller, "mercadoPagoAccessToken", "mp-token");
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
    }

    @Test
    void receiveValidatesSignatureFetchesPaymentAndUpdatesByPreferenceId() throws Exception {
        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"987654321\"}}";
        JsonNode payment = objectMapper.readTree("""
            {"id":987654321,"status":"approved","external_reference":"MC-abc","preference_id":"123456789-abcd"}
            """);
        PaymentOperation operation = new PaymentOperation();
        when(mercadoPagoService.getPayment("mp-token", "987654321"))
            .thenReturn(new MercadoPagoService.MercadoPagoApiResponse(payment.toString(), payment));
        when(paymentService.updateStatusByProviderReference(eq("123456789-abcd"), eq(OperationStatus.SUCCESS), contains("mercadopago_webhook")))
            .thenReturn(operation);

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "request-123");
        headers.add("x-signature", "ts=1713389300,v1=" + signature("id:987654321;request-id:request-123;ts:1713389300;"));

        PaymentOperation result = controller.receive(headers, Map.of(), body);

        assertThat(result).isSameAs(operation);
        verify(paymentService).updateStatusByProviderReference(eq("123456789-abcd"), eq(OperationStatus.SUCCESS), contains("\"external_reference\":\"MC-abc\""));
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
