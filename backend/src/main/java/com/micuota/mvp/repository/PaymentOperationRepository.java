package com.micuota.mvp.repository;

import com.micuota.mvp.domain.PaymentOperation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {
    List<PaymentOperation> findTop20ByTeacherIdOrderByCreatedAtDesc(Long teacherId);
}
