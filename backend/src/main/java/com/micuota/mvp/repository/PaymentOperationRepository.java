package com.micuota.mvp.repository;

import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.OperationStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
    Optional<PaymentOperation> findFirstByRawResponseContainingOrderByCreatedAtDesc(String rawResponseFragment);
}
