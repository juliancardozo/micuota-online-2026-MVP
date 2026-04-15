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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Backoffice", description = "Servicios privados del backoffice por tenant y rol")
@SecurityRequirement(name = "AuthToken")
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
    @Operation(summary = "Crear usuario", description = "Crea usuarios TEACHER o STUDENT dentro del tenant autenticado. Requiere TENANT_ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario creado"),
        @ApiResponse(responseCode = "400", description = "Token invalido o permisos insuficientes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Payload invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BackofficeUserView createUser(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateBackofficeUserRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdmin(session);
        return backofficeService.createUser(session.tenantId(), request);
    }

    @GetMapping("/users")
    @Operation(summary = "Listar usuarios", description = "Lista usuarios del tenant autenticado, opcionalmente filtrando por rol.")
    public List<BackofficeUserView> listUsers(
        @RequestHeader("X-Auth-Token") String token,
        @RequestParam(required = false) UserRole role
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.listUsers(session.tenantId(), role);
    }

    @PostMapping("/courses")
    @Operation(summary = "Crear curso", description = "Crea un curso asociado a un profesor del tenant autenticado.")
    public CourseView createCourse(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateCourseRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.createCourse(session.tenantId(), request);
    }

    @GetMapping("/courses")
    @Operation(summary = "Listar cursos", description = "Lista cursos del tenant autenticado.")
    public List<CourseView> listCourses(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.listCourses(session.tenantId());
    }

    @PostMapping("/payments/one-time")
    @Operation(summary = "Crear pago unico", description = "Crea una operacion ONE_TIME para el profesional autenticado.")
    public PaymentOperation createOneTimeFromBackoffice(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateBackofficePaymentRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return paymentService.createOneTimeForUser(session.userId(), request);
    }

    @PostMapping("/payments/subscriptions")
    @Operation(summary = "Crear suscripcion", description = "Crea una operacion SUBSCRIPTION para el profesional autenticado.")
    public PaymentOperation createSubscriptionFromBackoffice(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateBackofficePaymentRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return paymentService.createSubscriptionForUser(session.userId(), request);
    }

    @GetMapping("/payments")
    @Operation(summary = "Listar pagos del profesional", description = "Devuelve operaciones recientes del profesional autenticado.")
    public List<PaymentOperation> listBackofficePayments(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return paymentService.lastOperationsByUser(session.userId());
    }

    @GetMapping("/payments/by-course")
    @Operation(summary = "Listar pagos por curso", description = "Devuelve operaciones recientes filtradas por curso.")
    public List<PaymentOperation> listBackofficePaymentsByCourse(
        @RequestHeader("X-Auth-Token") String token,
        @RequestParam Long courseId
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return paymentService.lastOperationsByUserAndCourse(session.userId(), courseId);
    }

    @PostMapping("/enrollments")
    @Operation(summary = "Inscribir alumno", description = "Asocia un alumno/paciente a un curso dentro del tenant autenticado.")
    public EnrollmentView createEnrollment(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody CreateEnrollmentRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.enrollStudent(session.tenantId(), request);
    }

    @GetMapping("/enrollments")
    @Operation(summary = "Listar inscripciones", description = "Devuelve las inscripciones curso-alumno del tenant autenticado.")
    public List<EnrollmentView> listEnrollments(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.listEnrollments(session.tenantId());
    }

    @GetMapping("/dashboard/professor")
    @Operation(summary = "Dashboard profesor", description = "Resumen de cursos y pagos recientes para TENANT_ADMIN o TEACHER.")
    public ProfessorDashboardView professorDashboard(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        requireTenantAdminOrTeacher(session);
        return backofficeService.getProfessorDashboard(session.tenantId(), session.userId());
    }

    @GetMapping("/dashboard/student")
    @Operation(summary = "Dashboard alumno", description = "Resumen de cursos y pagos del alumno autenticado (solo STUDENT).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard generado"),
        @ApiResponse(responseCode = "400", description = "Operacion permitida solo para STUDENT o token invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
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
