package com.micuota.mvp.integration.prometeo.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.integration.prometeo.config.PrometeoProperties;
import com.micuota.mvp.integration.prometeo.dto.PrometeoAccountValidationRequest;
import com.micuota.mvp.integration.prometeo.dto.PrometeoAccountValidationResponse;
import com.micuota.mvp.integration.prometeo.exception.PrometeoIntegrationException;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.MultipartBodyBuilder;
import reactor.netty.http.client.HttpClient;

@Component
public class PrometeoAccountValidationClient {

    private static final Logger log = LoggerFactory.getLogger(PrometeoAccountValidationClient.class);

    private final PrometeoProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PrometeoAccountValidationClient(
        PrometeoProperties properties,
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
            .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.webClient = webClientBuilder.clone()
            .baseUrl(properties.getBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    public PrometeoAccountValidationResponse validateAccount(PrometeoAccountValidationRequest request) {
        if (!properties.isEnabled()) {
            throw new PrometeoIntegrationException(
                "La integracion con Prometeo no esta habilitada",
                HttpStatus.SERVICE_UNAVAILABLE,
                null,
                null
            );
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new PrometeoIntegrationException(
                "Falta configurar APP_PROMETEO_API_KEY",
                HttpStatus.SERVICE_UNAVAILABLE,
                null,
                null
            );
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        addField(builder, "country_code", normalizeUpper(request.countryCode()));
        addField(builder, "account_number", normalizeCompact(request.accountNumber()));
        addField(builder, "account_type", normalizeUpper(request.accountType()));
        addField(builder, "bank_code", normalizeCompact(request.bankCode()));
        addField(builder, "branch_code", normalizeCompact(request.branchCode()));
        addField(builder, "document_number", normalizeCompact(request.documentNumber()));
        addField(builder, "document_type", normalizeUpper(request.documentType()));

        try {
            return webClient.post()
                .uri("/validate-account/")
                .header("X-API-Key", properties.getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                    .map(body -> mapResponse(response.statusCode(), body, request)))
                .block(Duration.ofMillis(properties.getReadTimeoutMs() + 1000L));
        } catch (WebClientResponseException exception) {
            throw buildTechnicalException("Prometeo devolvio un error tecnico", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (WebClientRequestException exception) {
            log.warn("Prometeo request failed: {}", exception.getMessage());
            throw new PrometeoIntegrationException(
                "No se pudo conectar con Prometeo sandbox",
                HttpStatus.BAD_GATEWAY,
                null,
                null,
                exception
            );
        }
    }

    private PrometeoAccountValidationResponse mapResponse(
        HttpStatusCode statusCode,
        String body,
        PrometeoAccountValidationRequest request
    ) {
        PrometeoApiEnvelope envelope = parseEnvelope(statusCode, body);
        Integer providerCode = envelope.errors() != null ? envelope.errors().code() : null;
        String message = envelope.errors() != null && StringUtils.hasText(envelope.errors().message())
            ? envelope.errors().message()
            : "Validacion realizada";

        if (statusCode.is5xxServerError() || statusCode.value() == 401 || statusCode.value() == 403 || statusCode.value() == 429) {
            throw new PrometeoIntegrationException(
                message,
                HttpStatus.BAD_GATEWAY,
                statusCode.value(),
                providerCode
            );
        }

        return new PrometeoAccountValidationResponse(
            "PROMETEO",
            properties.isSandbox(),
            statusCode.is2xxSuccessful() && providerCode == null,
            statusCode.value(),
            providerCode,
            message,
            normalizeUpper(request.countryCode()),
            maskAccountNumber(request.accountNumber()),
            envelope.data(),
            envelope.validationData()
        );
    }

    private PrometeoApiEnvelope parseEnvelope(HttpStatusCode statusCode, String body) {
        try {
            return objectMapper.readValue(body, PrometeoApiEnvelope.class);
        } catch (Exception exception) {
            throw buildTechnicalException("No se pudo interpretar la respuesta de Prometeo", statusCode, body, exception);
        }
    }

    private PrometeoIntegrationException buildTechnicalException(
        String message,
        HttpStatusCode statusCode,
        String body,
        Throwable cause
    ) {
        String bodySnippet = body == null ? "" : body.substring(0, Math.min(body.length(), 240));
        log.warn("Prometeo technical error status={} body={}", statusCode != null ? statusCode.value() : null, bodySnippet);
        return new PrometeoIntegrationException(
            message,
            HttpStatus.BAD_GATEWAY,
            statusCode != null ? statusCode.value() : null,
            null,
            cause
        );
    }

    private void addField(MultipartBodyBuilder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.part(name, value);
        }
    }

    private String normalizeUpper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String normalizeCompact(String value) {
        return value == null ? null : value.trim();
    }

    private String maskAccountNumber(String accountNumber) {
        String compact = normalizeCompact(accountNumber);
        if (!StringUtils.hasText(compact)) {
            return "";
        }
        if (compact.length() <= 4) {
            return "*".repeat(compact.length());
        }
        return "*".repeat(compact.length() - 4) + compact.substring(compact.length() - 4);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PrometeoApiEnvelope(
        JsonNode data,
        PrometeoApiError errors,
        @JsonProperty("validation_data") JsonNode validationData
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PrometeoApiError(
        Integer code,
        String message
    ) {
    }
}
