package com.micuota.mvp.service;

import com.micuota.mvp.domain.SessionActivity;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.SessionActivityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionActivityService {

    private final SessionActivityRepository sessionActivityRepository;
    private final SaasMetricsService saasMetricsService;

    public SessionActivityService(SessionActivityRepository sessionActivityRepository, SaasMetricsService saasMetricsService) {
        this.sessionActivityRepository = sessionActivityRepository;
        this.saasMetricsService = saasMetricsService;
    }

    @Transactional
    public void recordSessionStarted(String token, Long tenantId, Long userId, UserRole role, OffsetDateTime startedAt) {
        String tokenHash = hashToken(token);
        SessionActivity activity = sessionActivityRepository.findByTokenHash(tokenHash).orElseGet(SessionActivity::new);
        activity.setTokenHash(tokenHash);
        activity.setTenantId(tenantId);
        activity.setUserId(userId);
        activity.setRole(role);
        activity.setStartedAt(startedAt);
        activity.setLastSeenAt(startedAt);
        activity.setEndedAt(null);
        activity.setDurationSeconds(0L);
        sessionActivityRepository.save(activity);
        saasMetricsService.recordSessionStarted(role.name());
    }

    @Transactional
    public void touchSession(String token, OffsetDateTime seenAt) {
        sessionActivityRepository.findByTokenHash(hashToken(token))
            .ifPresent(activity -> {
                activity.setLastSeenAt(seenAt);
                activity.setDurationSeconds(Math.max(0L, secondsBetween(activity.getStartedAt(), seenAt)));
                sessionActivityRepository.save(activity);
            });
    }

    @Transactional
    public void endSession(String token, OffsetDateTime endedAt) {
        sessionActivityRepository.findByTokenHash(hashToken(token))
            .ifPresent(activity -> {
                activity.setLastSeenAt(endedAt);
                activity.setEndedAt(endedAt);
                activity.setDurationSeconds(Math.max(0L, secondsBetween(activity.getStartedAt(), endedAt)));
                sessionActivityRepository.save(activity);
                saasMetricsService.recordSessionEnded(activity.getRole().name(), activity.getDurationSeconds());
            });
    }

    private long secondsBetween(OffsetDateTime startedAt, OffsetDateTime endedAt) {
        return endedAt.toEpochSecond() - startedAt.toEpochSecond();
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular hash de sesion", ex);
        }
    }
}
