package com.micuota.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class MercadoPagoService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public MercadoPagoService(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        @Value("${app.payments.mercadopago.base-url:https://api.mercadopago.com}") String mercadoPagoBaseUrl
    ) {
        this.webClient = webClientBuilder.clone().baseUrl(mercadoPagoBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    public MercadoPagoApiResponse createPreference(String accessToken, JsonNode payload) {
        return post(accessToken, "/checkout/preferences", payload, "preferencia de Mercado Pago");
    }

    public MercadoPagoApiResponse createPreapproval(String accessToken, JsonNode payload) {
        return post(accessToken, "/preapproval", payload, "preapproval de Mercado Pago");
    }

    public MercadoPagoApiResponse getPayment(String accessToken, String paymentId) {
        return get(accessToken, "/v1/payments/{id}", paymentId, "pago");
    }

    public MercadoPagoApiResponse getPreapproval(String accessToken, String preapprovalId) {
        return get(accessToken, "/preapproval/{id}", preapprovalId, "preapproval");
    }

    private MercadoPagoApiResponse get(String accessToken, String path, String id, String operationName) {
        try {
            String raw = webClient.get()
                .uri(path, id)
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            return parse(raw);
        } catch (WebClientResponseException exception) {
            throw new IllegalStateException(
                "Mercado Pago devolvio status " + exception.getStatusCode().value() + " al consultar " + operationName + ": " + exception.getResponseBodyAsString(),
                exception
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException("No se pudo consultar " + operationName + " en Mercado Pago", exception);
        }
    }

    private MercadoPagoApiResponse post(String accessToken, String path, JsonNode payload, String operationName) {
        try {
            String raw = webClient.post()
                .uri(path)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            return parse(raw);
        } catch (WebClientResponseException exception) {
            throw new IllegalStateException(
                "Mercado Pago devolvio status " + exception.getStatusCode().value() + " creando " + operationName + ": " + exception.getResponseBodyAsString(),
                exception
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException("No se pudo crear " + operationName, exception);
        }
    }

    private MercadoPagoApiResponse parse(String raw) {
        try {
            String safeRaw = raw == null || raw.isBlank() ? "{}" : raw;
            return new MercadoPagoApiResponse(safeRaw, objectMapper.readTree(safeRaw));
        } catch (IOException exception) {
            throw new IllegalStateException("Mercado Pago devolvio JSON invalido", exception);
        }
    }

    public record MercadoPagoApiResponse(String rawBody, JsonNode body) {
    }
}
