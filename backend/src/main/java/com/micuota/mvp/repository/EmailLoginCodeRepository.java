package com.micuota.mvp.repository;

import com.micuota.mvp.domain.EmailLoginCode;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailLoginCodeRepository extends JpaRepository<EmailLoginCode, Long> {
    Optional<EmailLoginCode> findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(String email);
    Optional<EmailLoginCode> findFirstByEmailOrderByCreatedAtDesc(String email);
    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);
    long countByRequestIpAndCreatedAtAfter(String requestIp, OffsetDateTime since);

    /** Marca el codigo como usado solo si nadie lo uso antes; devuelve 0 si otra request gano la carrera. */
    @Modifying
    @Transactional
    @Query("update EmailLoginCode c set c.consumedAt = :now where c.id = :id and c.consumedAt is null")
    int consume(@Param("id") Long id, @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("update EmailLoginCode c set c.attempts = c.attempts + 1 where c.id = :id")
    int incrementAttempts(@Param("id") Long id);
}
