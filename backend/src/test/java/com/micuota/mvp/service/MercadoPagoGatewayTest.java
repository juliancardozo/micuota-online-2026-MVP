package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MercadoPagoGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MercadoPagoService mercadoPagoService;

    private MercadoPagoGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MercadoPagoGateway(mercadoPagoService, objectMapper);
        ReflectionTestUtils.setField(gateway, "configuredAccessToken", "");
    }

    @Test
    void createCheckoutBuildsPreferenceAndUsesMercadoPagoIdAsProviderReference() throws Exception {
        JsonNode response = objectMapper.readTree("""
            {"id":"123456789-abcd","init_point":"https://www.mercadopago.com/checkout/v1/redirect?pref_id=123"}
            """);
        when(mercadoPagoService.createPreference(eq("teacher-token"), any(JsonNode.class)))
            .thenReturn(new MercadoPagoService.MercadoPagoApiResponse(response.toString(), response));

        ProviderCheckoutResult result = gateway.createCheckout(
            PaymentFlowType.ONE_TIME,
            new CreatePaymentRequest(1L, PaymentProviderType.MERCADOPAGO, "Clase de guitarra", new BigDecimal("1200.00"), "uyu", "alumno@example.com", 2L, 3L),
            new TeacherProviderCredentials("teacher-token", null, null, null, null),
            "https://micuota.online"
        );

        assertThat(result.providerReference()).isEqualTo("123456789-abcd");
        assertThat(result.checkoutUrl()).contains("mercadopago.com");
        assertThat(result.rawResponse()).contains("\"operation\":\"checkout_preference\"");
        assertThat(result.rawResponse()).contains("\"external_reference\":\"MC-");

        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(mercadoPagoService).createPreference(eq("teacher-token"), payload.capture());
        assertThat(payload.getValue().path("items").get(0).path("currency_id").asText()).isEqualTo("UYU");
        assertThat(payload.getValue().path("payer").path("email").asText()).isEqualTo("alumno@example.com");
        assertThat(payload.getValue().path("notification_url").asText()).isEqualTo("https://micuota.online/api/callbacks/mercadopago");
        assertThat(payload.getValue().path("back_urls").path("success").asText()).contains("/api/callbacks/success/by-reference");
    }

    @Test
    void createCheckoutBuildsPreapprovalForSubscriptions() throws Exception {
        JsonNode response = objectMapper.readTree("""
            {"id":"preapproval-123","init_point":"https://www.mercadopago.com/subscriptions/checkout"}
            """);
        when(mercadoPagoService.createPreapproval(eq("env-token"), any(JsonNode.class)))
            .thenReturn(new MercadoPagoService.MercadoPagoApiResponse(response.toString(), response));
        ReflectionTestUtils.setField(gateway, "configuredAccessToken", "env-token");

        ProviderCheckoutResult result = gateway.createCheckout(
            PaymentFlowType.SUBSCRIPTION,
            new CreatePaymentRequest(1L, PaymentProviderType.MERCADOPAGO, "Mensualidad", new BigDecimal("900.00"), "UYU", "alumno@example.com", 2L, 3L),
            new TeacherProviderCredentials(null, null, null, null, null),
            "https://micuota.online"
        );

        assertThat(result.providerReference()).isEqualTo("preapproval-123");

        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(mercadoPagoService).createPreapproval(eq("env-token"), payload.capture());
        assertThat(payload.getValue().path("auto_recurring").path("frequency_type").asText()).isEqualTo("months");
        assertThat(payload.getValue().path("external_reference").asText()).startsWith("MC-");
    }
}
