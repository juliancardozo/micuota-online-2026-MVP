package com.micuota.mvp.repository;

import com.micuota.mvp.domain.SessionActivity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionActivityRepository extends JpaRepository<SessionActivity, Long> {
    Optional<SessionActivity> findByTokenHash(String tokenHash);
    List<SessionActivity> findByStartedAtGreaterThanEqualOrderByStartedAtAsc(OffsetDateTime startedAt);
    long countByLastSeenAtGreaterThanEqualAndEndedAtIsNull(OffsetDateTime cutoff);
}
