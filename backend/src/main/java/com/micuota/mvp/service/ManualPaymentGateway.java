package com.micuota.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cobro sin procesador: el cliente paga por transferencia, giro o efectivo con los datos que muestra
 * la pagina de pago, y el profesional lo marca como pagado. El link es la pagina publica del cobro,
 * que PaymentService completa al guardar la operacion.
 */
@Component
public class ManualPaymentGateway implements PaymentProviderGateway {

    private final ObjectMapper objectMapper;

    public ManualPaymentGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.MANUAL;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        if (flowType == PaymentFlowType.SUBSCRIPTION) {
            throw new IllegalArgumentException("Los cobros mensuales automaticos necesitan Mercado Pago conectado");
        }
        String externalReference = "MC-" + UUID.randomUUID();
        ObjectNode trace = objectMapper.createObjectNode();
        trace.put("provider", "manual");
        trace.put("external_reference", externalReference);
        try {
            return new ProviderCheckoutResult("MANUAL-" + UUID.randomUUID(), externalReference, "", objectMapper.writeValueAsString(trace));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo registrar el cobro manual", exception);
        }
    }
}
