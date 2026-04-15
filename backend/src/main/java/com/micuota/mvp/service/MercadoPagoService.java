package com.micuota.mvp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class MercadoPagoService {

        private static final String MP_API_BASE = "https://api.mercadopago.com";
        private final WebClient webClient;

        public MercadoPagoService(WebClient.Builder webClientBuilder) {
                this.webClient = webClientBuilder.baseUrl(MP_API_BASE).build();
    }

        public Map<String, Object> createOneTimePayment(
                String accessToken,
                String title,
                BigDecimal amount,
                String currency,
                String baseUrl
        ) {
                Map<String, Object> payload = Map.of(
                        "items", List.of(Map.of(
                                "title", title,
                                "quantity", 1,
                                "currency_id", currency,
                                "unit_price", amount
                        )),
                        "back_urls", Map.of(
                                "success", baseUrl + "/api/callbacks/success",
                                "pending", baseUrl + "/api/callbacks/pending",
                                "failure", baseUrl + "/api/callbacks/failure"
                        ),
                        "auto_return", "approved"
                );

                return webClient.post()
                        .uri("/checkout/preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .onErrorResume(error -> Mono.error(new IllegalStateException("Error creando pago unico en MercadoPago", error)))
                        .block();
    }

        public Map<String, Object> createSubscription(
                String accessToken,
                String reason,
                BigDecimal amount,
                String currency,
                String baseUrl,
                String payerEmail
        ) {
                Map<String, Object> payload = Map.of(
                        "reason", reason,
                        "auto_recurring", Map.of(
                                "frequency", 1,
                                "frequency_type", "months",
                                "transaction_amount", amount,
                                "currency_id", currency
                        ),
                        "back_url", baseUrl + "/api/callbacks/success",
                        "payer_email", payerEmail,
                        "status", "authorized"
                );

                return webClient.post()
                        .uri("/preapproval")
                        .header("Authorization", "Bearer " + accessToken)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .onErrorResume(error -> Mono.error(new IllegalStateException("Error creando suscripcion en MercadoPago", error)))
                        .block();
    }
}
