package com.micuota.mvp.integration.prometeo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.integration.prometeo.config.PrometeoProperties;
import com.micuota.mvp.integration.prometeo.exception.PrometeoIntegrationException;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriBuilder;
import reactor.netty.http.client.HttpClient;

/**
 * Cliente de la Banking API de Prometeo. Devuelve el JSON tal cual (con su "status") para que el servicio
 * decida; solo lanza excepcion ante fallas tecnicas. Nunca registra los parametros: llevan credenciales bancarias.
 */
@Component
public class PrometeoBankingClient {

    private static final Logger log = LoggerFactory.getLogger(PrometeoBankingClient.class);

    private final PrometeoProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public PrometeoBankingClient(PrometeoProperties properties, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
            .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.webClient = webClientBuilder.clone()
            .baseUrl(properties.getBankingBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    public boolean isConfigured() {
        return properties.isBankPaymentsEnabled() && StringUtils.hasText(properties.getApiKey());
    }

    public JsonNode providers() {
        return get("/provider/", null, Map.of());
    }

    public JsonNode providerDetail(String providerCode) {
        return get("/provider/" + providerCode + "/", null, Map.of());
    }

    public JsonNode login(Map<String, String> fields) {
        return post("/login/", null, fields);
    }

    /** Segundo paso del login cuando el banco pide un dato extra (OTP, pregunta de seguridad). */
    public JsonNode answerLogin(String sessionKey, Map<String, String> fields) {
        return post("/login/", sessionKey, fields);
    }

    public JsonNode clients(String sessionKey) {
        return get("/client/", sessionKey, Map.of());
    }

    public JsonNode selectClient(String sessionKey, String clientId) {
        return get("/client/" + clientId + "/", sessionKey, Map.of());
    }

    public JsonNode accounts(String sessionKey) {
        return get("/account/", sessionKey, Map.of());
    }

    public JsonNode transferDestinations(String sessionKey) {
        return get("/transfer/destinations", sessionKey, Map.of());
    }

    public JsonNode preprocessTransfer(String sessionKey, Map<String, String> fields) {
        return post("/transfer/preprocess", sessionKey, fields);
    }

    public JsonNode confirmTransfer(String sessionKey, Map<String, String> fields) {
        return post("/transfer/confirm", sessionKey, fields);
    }

    public void logout(String sessionKey) {
        try {
            get("/logout/", sessionKey, Map.of());
        } catch (RuntimeException exception) {
            log.debug("Logout de Prometeo fallido: {}", exception.getMessage());
        }
    }

    private JsonNode get(String path, String sessionKey, Map<String, String> query) {
        requireConfigured();
        try {
            String body = webClient.get()
                .uri(builder -> withQuery(builder.path(path), sessionKey, query).build())
                .header("X-API-Key", properties.getApiKey())
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                .block(Duration.ofMillis(properties.getReadTimeoutMs() + 1000L));
            return parse(body);
        } catch (WebClientRequestException exception) {
            throw unreachable(exception);
        }
    }

    private JsonNode post(String path, String sessionKey, Map<String, String> fields) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        fields.forEach((name, value) -> {
            if (value != null) {
                form.add(name, value);
            }
        });
        try {
            String body = webClient.post()
                .uri(builder -> withQuery(builder.path(path), sessionKey, Map.of()).build())
                .header("X-API-Key", properties.getApiKey())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                .block(Duration.ofMillis(properties.getReadTimeoutMs() + 1000L));
            return parse(body);
        } catch (WebClientRequestException exception) {
            throw unreachable(exception);
        }
    }

    private static UriBuilder withQuery(UriBuilder builder, String sessionKey, Map<String, String> query) {
        if (sessionKey != null) {
            builder.queryParam("key", sessionKey);
        }
        query.forEach(builder::queryParam);
        return builder;
    }

    // Prometeo responde 500 con texto plano ante errores propios: se informa como "error" para que el servicio lo maneje.
    private JsonNode parse(String body) {
        try {
            JsonNode node = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            if (node.isObject()) {
                return node;
            }
        } catch (IOException ignored) {
            // cae al envoltorio de error de abajo
        }
        return objectMapper.createObjectNode().put("status", "error").put("message", "Respuesta inesperada del banco");
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new PrometeoIntegrationException("El pago desde el banco no esta habilitado", HttpStatus.SERVICE_UNAVAILABLE, null, null);
        }
    }

    private PrometeoIntegrationException unreachable(WebClientRequestException exception) {
        log.warn("No se pudo conectar con Prometeo: {}", exception.getMessage());
        return new PrometeoIntegrationException("No pudimos conectar con el banco. Probá de nuevo en un rato.", HttpStatus.BAD_GATEWAY, null, null, exception);
    }
}
