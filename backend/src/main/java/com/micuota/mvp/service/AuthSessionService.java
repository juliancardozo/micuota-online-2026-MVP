package com.micuota.mvp.service;

import com.micuota.mvp.domain.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    @Value("${app.auth.session-secret:micuota-local-dev-secret-change-in-prod}")
    private String sessionSecret;

    @Value("${app.auth.session-ttl-seconds:86400}")
    private long ttlSeconds;

    public String createSession(Long tenantId, Long userId, UserRole role) {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        long expiresAtEpoch = createdAt.plusSeconds(ttlSeconds).toEpochSecond();
        String payload = tenantId + ":" + userId + ":" + role.name() + ":" + createdAt.toEpochSecond() + ":" + expiresAtEpoch;
        String payloadB64 = base64UrlEncode(payload);
        String signature = sign(payloadB64);
        return payloadB64 + "." + signature;
    }

    public Optional<SessionContext> findSession(String token) {
        try {
            return Optional.of(parseAndValidate(token));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public SessionContext requireSession(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de sesion requerido");
        }
        return findSession(token).orElseThrow(() -> new IllegalArgumentException("Sesion invalida o expirada"));
    }

    private SessionContext parseAndValidate(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Formato de token invalido");
        }

        String payloadB64 = parts[0];
        String receivedSignature = parts[1];
        String expectedSignature = sign(payloadB64);

        if (!MessageDigest.isEqual(receivedSignature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Firma de token invalida");
        }

        String payload = base64UrlDecode(payloadB64);
        String[] values = payload.split(":");
        if (values.length != 5) {
            throw new IllegalArgumentException("Payload de token invalido");
        }

        Long tenantId = Long.parseLong(values[0]);
        Long userId = Long.parseLong(values[1]);
        UserRole role = UserRole.valueOf(values[2]);
        long createdAtEpoch = Long.parseLong(values[3]);
        long expiresAtEpoch = Long.parseLong(values[4]);

        if (OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() > expiresAtEpoch) {
            throw new IllegalArgumentException("Token expirado");
        }

        return new SessionContext(
            tenantId,
            userId,
            role,
            OffsetDateTime.ofInstant(Instant.ofEpochSecond(createdAtEpoch), ZoneOffset.UTC)
        );
    }

    private String sign(String payloadB64) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sessionSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el token", ex);
        }
    }

    private String base64UrlEncode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlDecode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record SessionContext(Long tenantId, Long userId, UserRole role, OffsetDateTime createdAt) {
    }
}
