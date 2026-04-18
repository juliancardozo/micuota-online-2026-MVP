package com.micuota.mvp.web;

import com.micuota.mvp.integration.prometeo.exception.PrometeoIntegrationException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Hidden
public class ApiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBusiness(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of(
            "timestamp", OffsetDateTime.now().toString(),
            "error", exception.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "timestamp", OffsetDateTime.now().toString(),
            "error", "Payload invalido",
            "details", exception.getBindingResult().toString()
        ));
    }

    @ExceptionHandler(PrometeoIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handlePrometeo(PrometeoIntegrationException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("error", exception.getMessage());
        body.put("provider", "PROMETEO");
        body.put("upstreamStatus", exception.getUpstreamHttpStatus());
        body.put("providerCode", exception.getProviderCode());
        return ResponseEntity.status(exception.getHttpStatus()).body(body);
    }
}
