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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PaymentNotificationService paymentNotificationService;
    private final SaasMetricsService saasMetricsService;
    private final Map<PaymentProviderType, PaymentProviderGateway> gateways;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${app.payments.default-provider:PROMETEO}")
    private String defaultProviderName;

    @Value("${app.payments.fallback-provider:MERCADOPAGO}")
    private String fallbackProviderName;

    public PaymentService(
        TeacherProfileRepository teacherProfileRepository,
        PaymentOperationRepository paymentOperationRepository,
        UserRepository userRepository,
        CourseRepository courseRepository,
        PaymentNotificationService paymentNotificationService,
        SaasMetricsService saasMetricsService,
        List<PaymentProviderGateway> providers
    ) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentOperationRepository = paymentOperationRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.paymentNotificationService = paymentNotificationService;
        this.saasMetricsService = saasMetricsService;
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
            operation.getCheckoutUrl(),
            teacherProfile.getTransferAlias(),
            teacherProfile.getTransferBankName()
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
        PaymentOperation saved = paymentOperationRepository.save(operation);
        saasMetricsService.recordPaymentStatusChanged(status);
        return saved;
    }

    @Transactional
    public PaymentOperation updateStatusByProviderReference(String providerReference, OperationStatus status) {
        PaymentOperation operation = paymentOperationRepository.findByProviderReference(providerReference)
            .orElseThrow(() -> new IllegalArgumentException("Operacion no encontrada para providerReference: " + providerReference));
        operation.setStatus(status);
        operation.setUpdatedAt(OffsetDateTime.now());
        PaymentOperation saved = paymentOperationRepository.save(operation);
        saasMetricsService.recordPaymentStatusChanged(status);
        return saved;
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

        TeacherProviderCredentials credentials = new TeacherProviderCredentials(
            teacher.getMpAccessToken(),
            teacher.getPrometeoApiKey(),
            teacher.getWooCommerceApiKey(),
            teacher.getTransferAlias(),
            teacher.getTransferBankName()
        );
        ProviderSelection selection = resolveCheckout(flowType, request, credentials, tenantId, teacher.getId());

        PaymentOperation operation = new PaymentOperation();
        operation.setTeacherId(request.teacherId());
        operation.setProvider(selection.provider());
        operation.setFlowType(flowType);
        operation.setStudentUserId(request.studentUserId());
        operation.setCourseId(request.courseId());
        operation.setAmount(request.amount());
        operation.setCurrency(request.currency());
        operation.setDescription(request.description());
        operation.setCheckoutUrl(selection.result().checkoutUrl());
        operation.setProviderReference(selection.result().providerReference());
        operation.setRawResponse(selection.rawResponse());
        operation.setStatus(OperationStatus.CREATED);
        operation.setCreatedAt(OffsetDateTime.now());

        PaymentOperation saved = paymentOperationRepository.save(operation);
        String normalizedPayerEmail = request.payerEmail() == null ? null : request.payerEmail().trim().toLowerCase(Locale.ROOT);
        paymentNotificationService.sendPaymentCreatedEmail(normalizedPayerEmail, teacher.getDisplayName(), saved);
        saasMetricsService.recordPaymentCreated(selection.provider(), flowType, request.amount());
        return saved;
    }

    private ProviderSelection resolveCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        Long tenantId,
        Long teacherProfileId
    ) {
        PaymentProviderType requestedProvider = request.provider();
        List<PaymentProviderType> providerChain = buildProviderChain(requestedProvider);
        Map<PaymentProviderType, String> failures = new LinkedHashMap<>();

        for (PaymentProviderType candidate : providerChain) {
            PaymentProviderGateway gateway = gateways.get(candidate);
            if (gateway == null) {
                failures.put(candidate, "Proveedor no soportado");
                continue;
            }

            try {
                ProviderCheckoutResult result = gateway.createCheckout(
                    flowType,
                    new CreatePaymentRequest(
                        request.teacherId(),
                        candidate,
                        request.description(),
                        request.amount(),
                        request.currency(),
                        request.payerEmail(),
                        request.studentUserId(),
                        request.courseId()
                    ),
                    credentials,
                    appBaseUrl
                );
                if (requestedProvider == null) {
                    log.info("Payment provider auto-selected tenantId={} teacherProfileId={} provider={}", tenantId, teacherProfileId, candidate);
                } else if (candidate != requestedProvider) {
                    log.warn(
                        "Payment provider fallback used tenantId={} teacherProfileId={} requested={} fallback={} failures={}",
                        tenantId,
                        teacherProfileId,
                        requestedProvider,
                        candidate,
                        failures
                    );
                }
                return new ProviderSelection(candidate, result, wrapRawResponse(requestedProvider, candidate, failures, result.rawResponse()));
            } catch (RuntimeException exception) {
                failures.put(candidate, exception.getMessage());
            }
        }

        throw new IllegalStateException("No se pudo generar el link de cobro. " + formatFailures(failures));
    }

    private List<PaymentProviderType> buildProviderChain(PaymentProviderType requestedProvider) {
        PaymentProviderType defaultProvider = parseProvider(defaultProviderName, PaymentProviderType.PROMETEO);
        PaymentProviderType fallbackProvider = parseProvider(fallbackProviderName, PaymentProviderType.MERCADOPAGO);
        List<PaymentProviderType> chain = new ArrayList<>();

        if (requestedProvider != null) {
            chain.add(requestedProvider);
            if (requestedProvider == defaultProvider && fallbackProvider != requestedProvider) {
                chain.add(fallbackProvider);
            }
            return chain;
        }

        chain.add(defaultProvider);
        if (fallbackProvider != defaultProvider) {
            chain.add(fallbackProvider);
        }
        return chain;
    }

    private PaymentProviderType parseProvider(String value, PaymentProviderType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return PaymentProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String wrapRawResponse(
        PaymentProviderType requestedProvider,
        PaymentProviderType resolvedProvider,
        Map<PaymentProviderType, String> failures,
        String providerRawResponse
    ) {
        StringBuilder raw = new StringBuilder();
        raw.append("{\"requestedProvider\":\"")
            .append(requestedProvider == null ? "AUTO" : requestedProvider.name())
            .append("\",\"resolvedProvider\":\"")
            .append(resolvedProvider.name())
            .append("\",\"fallbackUsed\":")
            .append(requestedProvider != null && requestedProvider != resolvedProvider)
            .append(",\"attemptFailures\":{");

        int index = 0;
        for (Map.Entry<PaymentProviderType, String> entry : failures.entrySet()) {
            if (index++ > 0) {
                raw.append(',');
            }
            raw.append('"')
                .append(entry.getKey().name())
                .append("\":\"")
                .append(escapeJson(entry.getValue()))
                .append('"');
        }

        raw.append("},\"providerResponse\":")
            .append(providerRawResponse == null || providerRawResponse.isBlank() ? "null" : providerRawResponse)
            .append('}');
        return raw.toString();
    }

    private String formatFailures(Map<PaymentProviderType, String> failures) {
        if (failures.isEmpty()) {
            return "No hay proveedores disponibles.";
        }
        return failures.entrySet().stream()
            .map(entry -> entry.getKey().name() + ": " + entry.getValue())
            .reduce((left, right) -> left + " | " + right)
            .orElse("No hay proveedores disponibles.");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private Long resolveTeacherProfileIdByUserId(Long userId) {
        return teacherProfileRepository.findByUserId(userId)
            .map(TeacherProfile::getId)
            .orElseThrow(() -> new IllegalArgumentException("El usuario no tiene perfil profesional configurado"));
    }

    private record ProviderSelection(
        PaymentProviderType provider,
        ProviderCheckoutResult result,
        String rawResponse
    ) {
    }
}
