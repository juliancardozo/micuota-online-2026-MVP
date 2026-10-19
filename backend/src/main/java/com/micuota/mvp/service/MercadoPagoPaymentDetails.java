package com.micuota.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Datos reales de un pago de Mercado Pago tomados de {@code GET /v1/payments/{id}}.
 *
 * @param paymentId          {@code id} del pago en Mercado Pago.
 * @param feeAmount          suma de {@code fee_details[].amount} que paga el vendedor (se excluyen los de {@code fee_payer = payer});
 *                           null si la respuesta no trae {@code fee_details}.
 * @param netReceivedAmount  {@code transaction_details.net_received_amount}; null si no viene.
 */
public record MercadoPagoPaymentDetails(String paymentId, BigDecimal feeAmount, BigDecimal netReceivedAmount) {

    public static MercadoPagoPaymentDetails fromPayment(JsonNode payment) {
        if (payment == null || payment.isMissingNode() || payment.isNull()) {
            return null;
        }
        JsonNode idNode = payment.path("id");
        String paymentId = idNode.isMissingNode() || idNode.isNull() ? null : idNode.asText();
        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }

        BigDecimal fee = null;
        JsonNode feeDetails = payment.path("fee_details");
        if (feeDetails.isArray()) {
            fee = BigDecimal.ZERO;
            for (JsonNode feeDetail : feeDetails) {
                if ("payer".equalsIgnoreCase(feeDetail.path("fee_payer").asText(""))) {
                    continue;
                }
                BigDecimal amount = decimal(feeDetail.path("amount"));
                if (amount != null) {
                    fee = fee.add(amount);
                }
            }
            fee = fee.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal net = decimal(payment.path("transaction_details").path("net_received_amount"));
        return new MercadoPagoPaymentDetails(paymentId, fee, net == null ? null : net.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
