package com.micuota.mvp.repository;

import com.micuota.mvp.domain.CourseEnrollment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {
    boolean existsByCourseIdAndStudentId(Long courseId, Long studentUserId);
    List<CourseEnrollment> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<CourseEnrollment> findByStudentIdOrderByCreatedAtDesc(Long studentUserId);
}
