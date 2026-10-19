package com.micuota.mvp.repository;

import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.OperationStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {
    List<PaymentOperation> findTop20ByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<PaymentOperation> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<PaymentOperation> findByTeacherIdInOrderByCreatedAtDesc(List<Long> teacherIds);
    List<PaymentOperation> findTop50ByTeacherIdAndCourseIdOrderByCreatedAtDesc(Long teacherId, Long courseId);
    List<PaymentOperation> findTop50ByTeacherIdInAndCourseIdOrderByCreatedAtDesc(List<Long> teacherIds, Long courseId);
    List<PaymentOperation> findTop50ByStudentUserIdOrderByCreatedAtDesc(Long studentUserId);
    List<PaymentOperation> findTop300ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(List<OperationStatus> statuses, OffsetDateTime nextRetryAt);
    List<PaymentOperation> findTop300ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(List<OperationStatus> statuses, OffsetDateTime createdAt);
    List<PaymentOperation> findTop300ByStatusNotAndDueAtBeforeOrderByDueAtAsc(OperationStatus status, OffsetDateTime dueAt);
    Optional<PaymentOperation> findByProviderReference(String providerReference);
    Optional<PaymentOperation> findByExternalReference(String externalReference);
    Optional<PaymentOperation> findFirstByRawResponseContainingOrderByCreatedAtDesc(String rawResponseFragment);

    // Bloqueo de fila (SELECT ... FOR UPDATE) para serializar webhooks concurrentes sobre la misma operacion.
    // Debe ser la primera lectura de la fila dentro de la transaccion para que el estado leido sea el ya commiteado.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOperation p where p.externalReference = :externalReference")
    Optional<PaymentOperation> findByExternalReferenceForUpdate(@Param("externalReference") String externalReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOperation p where p.providerReference = :providerReference")
    Optional<PaymentOperation> findByProviderReferenceForUpdate(@Param("providerReference") String providerReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOperation p where p.id = :id")
    Optional<PaymentOperation> findByIdForUpdate(@Param("id") Long id);

    // Solo ids (sin cargar la entidad) para poder bloquearla despues con findByIdForUpdate.
    @Query("select p.id from PaymentOperation p where p.rawResponse like concat('%', :fragment, '%') order by p.createdAt desc")
    List<Long> findIdsByRawResponseContaining(@Param("fragment") String fragment, Pageable pageable);
}
