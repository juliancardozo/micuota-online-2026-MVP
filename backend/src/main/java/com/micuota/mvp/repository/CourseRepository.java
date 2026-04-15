package com.micuota.mvp.repository;

import com.micuota.mvp.domain.Course;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<Course> findByTenantIdAndTeacherIdOrderByCreatedAtDesc(Long tenantId, Long teacherUserId);
}
