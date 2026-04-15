package com.micuota.mvp.repository;

import com.micuota.mvp.domain.PaymentOperation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {
    List<PaymentOperation> findTop20ByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<PaymentOperation> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<PaymentOperation> findTop50ByTeacherIdAndCourseIdOrderByCreatedAtDesc(Long teacherId, Long courseId);
    List<PaymentOperation> findTop50ByStudentUserIdOrderByCreatedAtDesc(Long studentUserId);
    Optional<PaymentOperation> findByProviderReference(String providerReference);
}
