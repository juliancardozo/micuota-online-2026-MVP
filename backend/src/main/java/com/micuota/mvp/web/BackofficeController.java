package com.micuota.mvp.web;

import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.service.AuthSessionService;
import com.micuota.mvp.service.BackofficeService;
import com.micuota.mvp.service.BackofficeUserView;
import com.micuota.mvp.service.CourseView;
import com.micuota.mvp.service.CreateBackofficeUserRequest;
import com.micuota.mvp.service.CreateBackofficePaymentRequest;
import com.micuota.mvp.service.CreateCourseRequest;
import com.micuota.mvp.service.CreateEnrollmentRequest;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.service.EnrollmentView;
import com.micuota.mvp.service.PaymentService;
import com.micuota.mvp.service.ProfessorDashboardView;
import com.micuota.mvp.service.StudentDashboardView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backoffice")
public class BackofficeController {

    private final AuthSessionService authSessionService;
    private final BackofficeService backofficeService;
    private final PaymentService paymentService;

    public BackofficeController(
        AuthSessionService authSessionService,
        BackofficeService backofficeService,
        PaymentService paymentService
    ) {
        this.authSessionService = authSessionService;
        this.backofficeService = backofficeService;
        this.paymentService = paymentService;
    }

    @PostMapping("/users")
    public BackofficeUserView createUser(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateBackofficeUserRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdmin(session);
        return backofficeService.createUser(session.tenantId(), request);
    }

    @GetMapping("/users")
    public List<BackofficeUserView> listUsers(
        @RequestHeader("X-Auth-Token") String token,
        @RequestParam(required = false) UserRole role
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.listUsers(session.tenantId(), role);
    }

    @PostMapping("/courses")
    public CourseView createCourse(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateCourseRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.createCourse(session.tenantId(), request);
    }

    @GetMapping("/courses")
    public List<CourseView> listCourses(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.listCourses(session.tenantId());
    }

    @PostMapping("/payments/one-time")
    public PaymentOperation createOneTimeFromBackoffice(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateBackofficePaymentRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return paymentService.createOneTimeForUser(session.userId(), request);
    }

    @PostMapping("/payments/subscriptions")
    public PaymentOperation createSubscriptionFromBackoffice(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateBackofficePaymentRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return paymentService.createSubscriptionForUser(session.userId(), request);
    }

    @GetMapping("/payments")
    public List<PaymentOperation> listBackofficePayments(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return paymentService.lastOperationsByUser(session.userId());
    }

    @GetMapping("/payments/by-course")
    public List<PaymentOperation> listBackofficePaymentsByCourse(
        @RequestHeader("X-Auth-Token") String token,
        @RequestParam Long courseId
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return paymentService.lastOperationsByUserAndCourse(session.userId(), courseId);
    }

    @PostMapping("/enrollments")
    public EnrollmentView createEnrollment(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateEnrollmentRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.enrollStudent(session.tenantId(), request);
    }

    @GetMapping("/enrollments")
    public List<EnrollmentView> listEnrollments(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.listEnrollments(session.tenantId());
    }

    @GetMapping("/dashboard/professor")
    public ProfessorDashboardView professorDashboard(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.getProfessorDashboard(session.tenantId(), session.userId());
    }

    @GetMapping("/dashboard/student")
    public StudentDashboardView studentDashboard(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        if (session.role() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Operacion permitida solo para STUDENT");
        }
        return backofficeService.getStudentDashboard(session.tenantId(), session.userId());
    }

    private void requireTenantAdmin(AuthSessionService.SessionContext session) {
        if (session.role() != UserRole.TENANT_ADMIN) {
            throw new IllegalArgumentException("Operacion permitida solo para TENANT_ADMIN");
        }
    }

    private void requireTenantAdminOrTeacher(AuthSessionService.SessionContext session) {
        if (session.role() != UserRole.TENANT_ADMIN && session.role() != UserRole.TEACHER) {
            throw new IllegalArgumentException("Operacion permitida para TENANT_ADMIN o TEACHER");
        }
    }
}
