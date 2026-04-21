package com.micuota.mvp.repository;

import com.micuota.mvp.domain.PaymentEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    List<PaymentEvent> findTop50ByOperationIdOrderByCreatedAtDesc(Long operationId);
    List<PaymentEvent> findTop100ByTeacherIdOrderByCreatedAtDesc(Long teacherId);
}
