package com.micuota.mvp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Frena la fuerza bruta contra el login del administrador: 5 intentos fallidos por IP cada 15 minutos. */
@Component
public class AdminLoginThrottle {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public void checkAllowed(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= MAX_FAILURES) {
                throw new IllegalArgumentException("Demasiados intentos. Proba de nuevo en 15 minutos.");
            }
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(Instant.now());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> attempts) {
        Instant limit = Instant.now().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(limit)) {
            attempts.pollFirst();
        }
    }
}
