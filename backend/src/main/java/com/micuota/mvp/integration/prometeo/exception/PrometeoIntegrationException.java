package com.micuota.mvp.integration.prometeo.exception;

import org.springframework.http.HttpStatus;

public class PrometeoIntegrationException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final Integer providerCode;
    private final Integer upstreamHttpStatus;

    public PrometeoIntegrationException(
        String message,
        HttpStatus httpStatus,
        Integer upstreamHttpStatus,
        Integer providerCode,
        Throwable cause
    ) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.providerCode = providerCode;
        this.upstreamHttpStatus = upstreamHttpStatus;
    }

    public PrometeoIntegrationException(
        String message,
        HttpStatus httpStatus,
        Integer upstreamHttpStatus,
        Integer providerCode
    ) {
        this(message, httpStatus, upstreamHttpStatus, providerCode, null);
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Integer getProviderCode() {
        return providerCode;
    }

    public Integer getUpstreamHttpStatus() {
        return upstreamHttpStatus;
    }
}
