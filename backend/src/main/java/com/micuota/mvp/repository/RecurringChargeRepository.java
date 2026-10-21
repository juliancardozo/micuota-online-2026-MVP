package com.micuota.mvp.repository;

import com.micuota.mvp.domain.RecurringCharge;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringChargeRepository extends JpaRepository<RecurringCharge, Long> {
    List<RecurringCharge> findTop200ByActiveTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(OffsetDateTime now);
    List<RecurringCharge> findByTeacherProfileIdInAndActiveTrueOrderByCreatedAtDesc(List<Long> teacherProfileIds);
}
