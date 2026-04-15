package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.Course;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.CourseRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PaymentNotificationService paymentNotificationService;
    private final Map<PaymentProviderType, PaymentProviderGateway> gateways;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public PaymentService(
        TeacherProfileRepository teacherProfileRepository,
        PaymentOperationRepository paymentOperationRepository,
        UserRepository userRepository,
        CourseRepository courseRepository,
        PaymentNotificationService paymentNotificationService,
        List<PaymentProviderGateway> providers
    ) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentOperationRepository = paymentOperationRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.paymentNotificationService = paymentNotificationService;
        this.gateways = new EnumMap<>(PaymentProviderType.class);
        providers.forEach(p -> this.gateways.put(p.provider(), p));
    }

    @Transactional
    public PaymentOperation createOneTime(CreatePaymentRequest request) {
        return createOperation(PaymentFlowType.ONE_TIME, request);
    }

    @Transactional
    public PaymentOperation createSubscription(CreatePaymentRequest request) {
        return createOperation(PaymentFlowType.SUBSCRIPTION, request);
    }

    @Transactional(readOnly = true)
    public List<PaymentOperation> lastOperationsByTeacher(Long teacherId) {
        return paymentOperationRepository.findTop20ByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Transactional(readOnly = true)
    public PaymentPublicView getPublicView(Long operationId) {
        PaymentOperation operation = paymentOperationRepository.findById(operationId)
            .orElseThrow(() -> new IllegalArgumentException("Operacion no encontrada"));

        TeacherProfile teacherProfile = teacherProfileRepository.findById(operation.getTeacherId())
            .orElseThrow(() -> new IllegalArgumentException("Perfil profesional no encontrado"));

        return new PaymentPublicView(
            operation.getId(),
            teacherProfile.getDisplayName(),
            operation.getDescription(),
            operation.getAmount(),
            operation.getCurrency(),
            operation.getFlowType(),
            operation.getProvider(),
            operation.getStatus(),
            operation.getCheckoutUrl()
        );
    }

    @Transactional(readOnly = true)
    public List<PaymentOperation> lastOperationsByUser(Long userId) {
        Long teacherProfileId = resolveTeacherProfileIdByUserId(userId);
        return paymentOperationRepository.findTop20ByTeacherIdOrderByCreatedAtDesc(teacherProfileId);
    }

    @Transactional(readOnly = true)
    public List<PaymentOperation> lastOperationsByUserAndCourse(Long userId, Long courseId) {
        Long teacherProfileId = resolveTeacherProfileIdByUserId(userId);
        return paymentOperationRepository.findTop50ByTeacherIdAndCourseIdOrderByCreatedAtDesc(teacherProfileId, courseId);
    }

    @Transactional
    public PaymentOperation createOneTimeForUser(Long userId, CreateBackofficePaymentRequest request) {
        Long teacherProfileId = resolveTeacherProfileIdByUserId(userId);
        return createOneTime(new CreatePaymentRequest(
            teacherProfileId,
            request.provider(),
            request.description(),
            request.amount(),
            request.currency(),
            request.payerEmail(),
            request.studentUserId(),
            request.courseId()
        ));
    }

    @Transactional
    public PaymentOperation createSubscriptionForUser(Long userId, CreateBackofficePaymentRequest request) {
        Long teacherProfileId = resolveTeacherProfileIdByUserId(userId);
        return createSubscription(new CreatePaymentRequest(
            teacherProfileId,
            request.provider(),
            request.description(),
            request.amount(),
            request.currency(),
            request.payerEmail(),
            request.studentUserId(),
            request.courseId()
        ));
    }

    @Transactional
    public PaymentOperation updateStatus(Long operationId, OperationStatus status) {
        PaymentOperation operation = paymentOperationRepository.findById(operationId)
            .orElseThrow(() -> new IllegalArgumentException("Operacion no encontrada: " + operationId));
        operation.setStatus(status);
        operation.setUpdatedAt(OffsetDateTime.now());
        return paymentOperationRepository.save(operation);
    }

    @Transactional
    public PaymentOperation updateStatusByProviderReference(String providerReference, OperationStatus status) {
        PaymentOperation operation = paymentOperationRepository.findByProviderReference(providerReference)
            .orElseThrow(() -> new IllegalArgumentException("Operacion no encontrada para providerReference: " + providerReference));
        operation.setStatus(status);
        operation.setUpdatedAt(OffsetDateTime.now());
        return paymentOperationRepository.save(operation);
    }

    private PaymentOperation createOperation(PaymentFlowType flowType, CreatePaymentRequest request) {
        TeacherProfile teacher = teacherProfileRepository.findById(request.teacherId())
            .orElseThrow(() -> new IllegalArgumentException("Teacher no encontrado: " + request.teacherId()));

        User teacherUser = teacher.getUser();
        Long tenantId = teacherUser.getTenant().getId();

        if (request.studentUserId() != null) {
            User student = userRepository.findById(request.studentUserId())
                .orElseThrow(() -> new IllegalArgumentException("Alumno/paciente no encontrado"));
            if (!student.getTenant().getId().equals(tenantId)) {
                throw new IllegalArgumentException("Alumno/paciente fuera del tenant");
            }
            if (student.getRole() != UserRole.STUDENT) {
                throw new IllegalArgumentException("El usuario seleccionado no es alumno/paciente");
            }
        }

        if (request.courseId() != null) {
            Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado"));
            if (!course.getTenant().getId().equals(tenantId)) {
                throw new IllegalArgumentException("Curso fuera del tenant");
            }
        }

        PaymentProviderGateway gateway = gateways.get(request.provider());
        if (gateway == null) {
            throw new IllegalArgumentException("Proveedor no soportado: " + request.provider());
        }

        TeacherProviderCredentials credentials = new TeacherProviderCredentials(
            teacher.getMpAccessToken(),
            teacher.getPrometeoApiKey(),
            teacher.getWooCommerceApiKey()
        );

        ProviderCheckoutResult result = gateway.createCheckout(flowType, request, credentials, appBaseUrl);

        PaymentOperation operation = new PaymentOperation();
        operation.setTeacherId(request.teacherId());
        operation.setProvider(request.provider());
        operation.setFlowType(flowType);
        operation.setStudentUserId(request.studentUserId());
        operation.setCourseId(request.courseId());
        operation.setAmount(request.amount());
        operation.setCurrency(request.currency());
        operation.setDescription(request.description());
        operation.setCheckoutUrl(result.checkoutUrl());
        operation.setProviderReference(result.providerReference());
        operation.setRawResponse(result.rawResponse());
        operation.setStatus(OperationStatus.CREATED);
        operation.setCreatedAt(OffsetDateTime.now());

        PaymentOperation saved = paymentOperationRepository.save(operation);
        String normalizedPayerEmail = request.payerEmail() == null ? null : request.payerEmail().trim().toLowerCase(Locale.ROOT);
        paymentNotificationService.sendPaymentCreatedEmail(normalizedPayerEmail, teacher.getDisplayName(), saved);
        return saved;
    }

    private Long resolveTeacherProfileIdByUserId(Long userId) {
        return teacherProfileRepository.findByUserId(userId)
            .map(TeacherProfile::getId)
            .orElseThrow(() -> new IllegalArgumentException("El usuario no tiene perfil profesional configurado"));
    }
}
