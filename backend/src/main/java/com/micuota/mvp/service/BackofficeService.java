package com.micuota.mvp.service;

import com.micuota.mvp.domain.Course;
import com.micuota.mvp.domain.CourseEnrollment;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.CourseEnrollmentRepository;
import com.micuota.mvp.repository.CourseRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BackofficeService {

    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final CourseRepository courseRepository;
    private final TenantRepository tenantRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final PaymentKpiService paymentKpiService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public BackofficeService(
        UserRepository userRepository,
        TeacherProfileRepository teacherProfileRepository,
        CourseRepository courseRepository,
        TenantRepository tenantRepository,
        CourseEnrollmentRepository courseEnrollmentRepository,
        PaymentOperationRepository paymentOperationRepository,
        PaymentKpiService paymentKpiService
    ) {
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.courseRepository = courseRepository;
        this.tenantRepository = tenantRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.paymentOperationRepository = paymentOperationRepository;
        this.paymentKpiService = paymentKpiService;
    }

    @Transactional
    public BackofficeUserView createUser(Long tenantId, CreateBackofficeUserRequest request) {
        if (request.role() != UserRole.TEACHER && request.role() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Solo se pueden crear perfiles TEACHER o STUDENT");
        }

        if (userRepository.findByTenantIdAndEmail(tenantId, request.email().toLowerCase(Locale.ROOT)).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario con ese email en el tenant");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));

        User user = new User();
        user.setTenant(tenant);
        user.setFullName(request.fullName());
        user.setEmail(request.email().toLowerCase(Locale.ROOT));
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        if (request.role() == UserRole.TEACHER) {
            TeacherProfile profile = new TeacherProfile();
            profile.setUser(user);
            profile.setDisplayName(request.fullName());
            teacherProfileRepository.save(profile);
        }

        return new BackofficeUserView(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public List<BackofficeUserView> listUsers(Long tenantId, UserRole role) {
        List<User> users = role == null
            ? userRepository.findByTenantIdOrderByFullNameAsc(tenantId)
            : userRepository.findByTenantIdAndRoleOrderByFullNameAsc(tenantId, role);

        return users.stream()
            .map(u -> new BackofficeUserView(u.getId(), u.getFullName(), u.getEmail(), u.getRole()))
            .toList();
    }

    @Transactional
    public CourseView createCourse(Long tenantId, CreateCourseRequest request) {
        User teacher = userRepository.findById(request.teacherUserId())
            .orElseThrow(() -> new IllegalArgumentException("Profesor no encontrado"));

        if (!teacher.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("El profesor no pertenece al tenant");
        }

        if (teacher.getRole() != UserRole.TEACHER && teacher.getRole() != UserRole.TENANT_ADMIN) {
            throw new IllegalArgumentException("El usuario seleccionado no es profesor");
        }

        Course course = new Course();
        course.setTenant(teacher.getTenant());
        course.setTeacher(teacher);
        course.setName(request.name());
        course.setDescription(request.description());
        course.setCreatedAt(OffsetDateTime.now());
        course = courseRepository.save(course);

        return new CourseView(course.getId(), course.getName(), course.getDescription(), teacher.getId(), teacher.getFullName());
    }

    @Transactional(readOnly = true)
    public List<CourseView> listCourses(Long tenantId) {
        return courseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .map(course -> new CourseView(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getTeacher().getId(),
                course.getTeacher().getFullName()
            ))
            .toList();
    }

    @Transactional
    public EnrollmentView enrollStudent(Long tenantId, CreateEnrollmentRequest request) {
        Course course = courseRepository.findById(request.courseId())
            .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado"));
        if (!course.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("El curso no pertenece al tenant");
        }

        User student = userRepository.findById(request.studentUserId())
            .orElseThrow(() -> new IllegalArgumentException("Alumno/paciente no encontrado"));
        if (!student.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("El alumno/paciente no pertenece al tenant");
        }
        if (student.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Solo usuarios STUDENT pueden inscribirse");
        }

        if (courseEnrollmentRepository.existsByCourseIdAndStudentId(course.getId(), student.getId())) {
            throw new IllegalArgumentException("El alumno/paciente ya esta asignado al curso");
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setTenant(course.getTenant());
        enrollment.setCourse(course);
        enrollment.setStudent(student);
        enrollment.setCreatedAt(OffsetDateTime.now());
        enrollment = courseEnrollmentRepository.save(enrollment);

        return new EnrollmentView(
            enrollment.getId(),
            course.getId(),
            course.getName(),
            student.getId(),
            student.getFullName(),
            course.getTeacher().getFullName()
        );
    }

    @Transactional(readOnly = true)
    public List<EnrollmentView> listEnrollments(Long tenantId) {
        return courseEnrollmentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .map(enrollment -> new EnrollmentView(
                enrollment.getId(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getName(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getFullName(),
                enrollment.getCourse().getTeacher().getFullName()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public ProfessorDashboardView getProfessorDashboard(Long tenantId, Long userId) {
        User professorUser = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario profesional no encontrado"));
        if (!professorUser.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("Profesional fuera de tenant");
        }

        TeacherProfile profile = teacherProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Perfil profesional no encontrado"));

        List<Course> courses = courseRepository.findByTenantIdAndTeacherIdOrderByCreatedAtDesc(tenantId, userId);
        Map<Long, String> courseNames = new HashMap<>();
        courses.forEach(course -> courseNames.put(course.getId(), course.getName()));

        List<com.micuota.mvp.domain.PaymentOperation> payments = paymentOperationRepository.findByTeacherIdOrderByCreatedAtDesc(profile.getId());

        Map<Long, BigDecimal> totals = new HashMap<>();
        Map<Long, Long> counts = new HashMap<>();
        for (com.micuota.mvp.domain.PaymentOperation payment : payments) {
            if (payment.getCourseId() == null) {
                continue;
            }
            totals.put(payment.getCourseId(), totals.getOrDefault(payment.getCourseId(), BigDecimal.ZERO).add(payment.getAmount()));
            counts.put(payment.getCourseId(), counts.getOrDefault(payment.getCourseId(), 0L) + 1);
        }

        List<ProfessorCoursePaymentSummary> courseSummaries = totals.keySet().stream()
            .map(courseId -> new ProfessorCoursePaymentSummary(
                courseId,
                courseNames.getOrDefault(courseId, "Curso sin nombre"),
                counts.getOrDefault(courseId, 0L),
                totals.getOrDefault(courseId, BigDecimal.ZERO)
            ))
            .sorted(Comparator.comparing(ProfessorCoursePaymentSummary::totalAmount).reversed())
            .toList();

        List<StudentPaymentView> recent = payments.stream().limit(20)
            .map(payment -> new StudentPaymentView(
                payment.getId(),
                payment.getDescription(),
                courseNames.getOrDefault(payment.getCourseId(), "Sin curso"),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getFlowType(),
                payment.getStatus(),
                payment.getCheckoutUrl(),
                profile.getDisplayName()
            ))
            .toList();

        List<CourseView> courseViews = courses.stream()
            .map(course -> new CourseView(course.getId(), course.getName(), course.getDescription(), course.getTeacher().getId(), course.getTeacher().getFullName()))
            .toList();

        ProfessorRevenueMetricsView revenueMetrics = buildRevenueMetrics(professorUser.getTenant(), payments);
        PaymentKpiFrameworkView kpiFramework = paymentKpiService.buildTenantKpis(tenantId);

        return new ProfessorDashboardView(profile.getDisplayName(), courseViews, courseSummaries, recent, revenueMetrics, kpiFramework);
    }

    @Transactional(readOnly = true)
    public StudentDashboardView getStudentDashboard(Long tenantId, Long userId) {
        User studentUser = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Alumno/paciente no encontrado"));
        if (!studentUser.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("Alumno/paciente fuera de tenant");
        }

        List<CourseEnrollment> enrollments = courseEnrollmentRepository.findByStudentIdOrderByCreatedAtDesc(userId);
        List<CourseView> enrolledCourses = enrollments.stream()
            .map(enrollment -> new CourseView(
                enrollment.getCourse().getId(),
                enrollment.getCourse().getName(),
                enrollment.getCourse().getDescription(),
                enrollment.getCourse().getTeacher().getId(),
                enrollment.getCourse().getTeacher().getFullName()
            ))
            .toList();

        Map<Long, String> courseNames = new HashMap<>();
        enrollments.forEach(enrollment -> courseNames.put(enrollment.getCourse().getId(), enrollment.getCourse().getName()));

        List<com.micuota.mvp.domain.PaymentOperation> payments = paymentOperationRepository.findTop50ByStudentUserIdOrderByCreatedAtDesc(userId);
        List<StudentPaymentView> paymentViews = payments.stream()
            .map(payment -> {
                TeacherProfile profile = teacherProfileRepository.findById(payment.getTeacherId()).orElse(null);
                String professional = profile != null ? profile.getDisplayName() : "Profesional";
                String checkoutInApp = "/pago.html?operationId=" + payment.getId();
                return new StudentPaymentView(
                    payment.getId(),
                    payment.getDescription(),
                    courseNames.getOrDefault(payment.getCourseId(), "Sin curso"),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getFlowType(),
                    payment.getStatus(),
                    checkoutInApp,
                    professional
                );
            })
            .toList();

        return new StudentDashboardView(studentUser.getFullName(), enrolledCourses, paymentViews);
    }

    @Transactional(readOnly = true)
    public PaymentSettingsView getPaymentSettings(Long tenantId, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (!user.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("Usuario fuera de tenant");
        }

        TeacherProfile profile = teacherProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("El usuario no tiene perfil profesional configurado"));

        return new PaymentSettingsView(profile.getTransferAlias(), profile.getTransferBankName());
    }

    @Transactional
    public PaymentSettingsView updatePaymentSettings(Long tenantId, Long userId, UpdatePaymentSettingsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (!user.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("Usuario fuera de tenant");
        }

        TeacherProfile profile = teacherProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("El usuario no tiene perfil profesional configurado"));

        profile.setTransferAlias(blankToNull(request.transferAlias()));
        profile.setTransferBankName(blankToNull(request.transferBankName()));
        TeacherProfile saved = teacherProfileRepository.save(profile);

        return new PaymentSettingsView(saved.getTransferAlias(), saved.getTransferBankName());
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ProfessorRevenueMetricsView buildRevenueMetrics(Tenant tenant, List<com.micuota.mvp.domain.PaymentOperation> payments) {
        List<com.micuota.mvp.domain.PaymentOperation> success = payments.stream()
            .filter(payment -> payment.getStatus() == OperationStatus.SUCCESS)
            .toList();

        BigDecimal gross = success.stream()
            .map(payment -> payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal processingFees = success.stream()
            .map(payment -> payment.getProcessingFeeAmount() == null ? BigDecimal.ZERO : payment.getProcessingFeeAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal advancedFees = success.stream()
            .map(payment -> payment.getAdvancedFeatureFeeAmount() == null ? BigDecimal.ZERO : payment.getAdvancedFeatureFeeAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal net = success.stream()
            .map(payment -> payment.getNetAmountForTeacher() == null
                ? (payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount().subtract(
                    (payment.getProcessingFeeAmount() == null ? BigDecimal.ZERO : payment.getProcessingFeeAmount())
                        .add(payment.getAdvancedFeatureFeeAmount() == null ? BigDecimal.ZERO : payment.getAdvancedFeatureFeeAmount())
                ))
                : payment.getNetAmountForTeacher())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        double takeRate = gross.compareTo(BigDecimal.ZERO) == 0
            ? 0D
            : processingFees.multiply(BigDecimal.valueOf(100)).divide(gross, 4, RoundingMode.HALF_UP).doubleValue();

        double advancedRate = gross.compareTo(BigDecimal.ZERO) == 0
            ? 0D
            : advancedFees.multiply(BigDecimal.valueOf(100)).divide(gross, 4, RoundingMode.HALF_UP).doubleValue();

        return new ProfessorRevenueMetricsView(
            tenant.getPlanCode() == null ? "BASE" : tenant.getPlanCode(),
            gross.setScale(2, RoundingMode.HALF_UP),
            processingFees.setScale(2, RoundingMode.HALF_UP),
            advancedFees.setScale(2, RoundingMode.HALF_UP),
            net,
            Math.round(takeRate * 10.0) / 10.0,
            Math.round(advancedRate * 10.0) / 10.0,
            Boolean.TRUE.equals(tenant.getRecoveryAutomationEnabled()),
            Boolean.TRUE.equals(tenant.getAdvancedAnalyticsEnabled()),
            Boolean.TRUE.equals(tenant.getIntegrationsEnabled())
        );
    }
}
