package com.micuota.mvp.config;

import com.micuota.mvp.domain.Course;
import com.micuota.mvp.domain.CourseEnrollment;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.CourseEnrollmentRepository;
import com.micuota.mvp.repository.CourseRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.time.OffsetDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SeedDataConfig {

    @Bean
    @ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
    CommandLineRunner seedUsers(
        UserRepository userRepository,
        TeacherProfileRepository teacherProfileRepository,
        TenantRepository tenantRepository,
        CourseRepository courseRepository,
        CourseEnrollmentRepository courseEnrollmentRepository
    ) {
        return args -> {
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            Tenant tenant = tenantRepository.findBySlug("demo-academia")
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setName("Demo Academia");
                    t.setSlug("demo-academia");
                    t.setCreatedAt(OffsetDateTime.now());
                    t.setPlanCode("BASE");
                    t.setTakeRateBps(350);
                    t.setAdvancedDunningFeeBps(120);
                    t.setRecoveryAutomationEnabled(false);
                    t.setAdvancedAnalyticsEnabled(false);
                    t.setIntegrationsEnabled(false);
                    return tenantRepository.save(t);
                });

            User adminUser = userRepository.findByTenantIdAndEmail(tenant.getId(), "admin@micuota.online")
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail("admin@micuota.online");
                    user.setPasswordHash("demo");
                    user.setFullName("Admin Demo");
                    user.setRole(UserRole.TENANT_ADMIN);
                    user.setTenant(tenant);
                    return userRepository.save(user);
                });

            User platformAdmin = userRepository.findByTenantIdAndEmail(tenant.getId(), "platform-admin@micuota.online")
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail("platform-admin@micuota.online");
                    user.setFullName("Platform Admin");
                    user.setRole(UserRole.ADMIN);
                    user.setTenant(tenant);
                    return userRepository.save(user);
                });
            platformAdmin.setPasswordHash(passwordEncoder.encode("ADMIN1234"));
            platformAdmin.setRole(UserRole.ADMIN);
            userRepository.save(platformAdmin);

            teacherProfileRepository.findByUserId(adminUser.getId())
                .orElseGet(() -> {
                    TeacherProfile profile = new TeacherProfile();
                    profile.setUser(adminUser);
                    profile.setDisplayName("Admin Demo");
                    profile.setMpAccessToken("TEST-MP-TOKEN-ADMIN");
                    profile.setPrometeoApiKey("TEST-PROMETEO-KEY-ADMIN");
                    profile.setWooCommerceApiKey("TEST-WC-KEY-ADMIN");
                    profile.setTransferAlias("micuota.admin.ar");
                    profile.setTransferBankName("Banco Demo AR");
                    return teacherProfileRepository.save(profile);
                });

            User teacherUser = userRepository.findByTenantIdAndEmail(tenant.getId(), "teacher@micuota.online")
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail("teacher@micuota.online");
                    user.setPasswordHash("demo");
                    user.setFullName("Profe Demo");
                    user.setRole(UserRole.TEACHER);
                    user.setTenant(tenant);
                    return userRepository.save(user);
                });

            teacherProfileRepository.findByUserId(teacherUser.getId())
                .orElseGet(() -> {
                    TeacherProfile profile = new TeacherProfile();
                    profile.setUser(teacherUser);
                    profile.setDisplayName("Profe Demo");
                    profile.setMpAccessToken("TEST-MP-TOKEN");
                    profile.setPrometeoApiKey("TEST-PROMETEO-KEY");
                    profile.setWooCommerceApiKey("TEST-WC-KEY");
                    profile.setTransferAlias("profe.demo.ar");
                    profile.setTransferBankName("Banco Aula AR");
                    return teacherProfileRepository.save(profile);
                });

            User studentUser = userRepository.findByTenantIdAndEmail(tenant.getId(), "student@micuota.online")
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail("student@micuota.online");
                    user.setPasswordHash("demo");
                    user.setFullName("Alumno Demo");
                    user.setRole(UserRole.STUDENT);
                    user.setTenant(tenant);
                    return userRepository.save(user);
                });

            Course demoCourse = courseRepository.findByTenantIdAndTeacherIdOrderByCreatedAtDesc(tenant.getId(), teacherUser.getId())
                .stream()
                .filter(course -> "Curso Demo MiCuota".equals(course.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Course course = new Course();
                    course.setTenant(tenant);
                    course.setTeacher(teacherUser);
                    course.setName("Curso Demo MiCuota");
                    course.setDescription("Curso seed para probar paneles de profesor y alumno");
                    course.setCreatedAt(OffsetDateTime.now());
                    return courseRepository.save(course);
                });

            if (!courseEnrollmentRepository.existsByCourseIdAndStudentId(demoCourse.getId(), studentUser.getId())) {
                CourseEnrollment enrollment = new CourseEnrollment();
                enrollment.setTenant(tenant);
                enrollment.setCourse(demoCourse);
                enrollment.setStudent(studentUser);
                enrollment.setCreatedAt(OffsetDateTime.now());
                courseEnrollmentRepository.save(enrollment);
            }
        };
    }
}
