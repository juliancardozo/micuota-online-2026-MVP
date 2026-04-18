package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.CourseEnrollmentRepository;
import com.micuota.mvp.repository.CourseRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaunchpadService {

    private static final long FREE_STUDENT_LIMIT = 50;

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentOperationRepository paymentOperationRepository;

    public LaunchpadService(
        UserRepository userRepository,
        CourseRepository courseRepository,
        CourseEnrollmentRepository courseEnrollmentRepository,
        TeacherProfileRepository teacherProfileRepository,
        PaymentOperationRepository paymentOperationRepository
    ) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentOperationRepository = paymentOperationRepository;
    }

    @Transactional(readOnly = true)
    public LaunchpadView buildTenantLaunchpad(Long tenantId) {
        long teachers = userRepository.countByTenantIdAndRole(tenantId, UserRole.TEACHER);
        long students = userRepository.countByTenantIdAndRole(tenantId, UserRole.STUDENT);
        long courses = courseRepository.countByTenantId(tenantId);
        long enrollments = courseEnrollmentRepository.countByTenantId(tenantId);

        List<Long> teacherProfileIds = teacherProfileRepository.findByUserTenantId(tenantId).stream()
            .map(TeacherProfile::getId)
            .toList();
        List<PaymentOperation> payments = teacherProfileIds.isEmpty()
            ? List.of()
            : paymentOperationRepository.findByTeacherIdInOrderByCreatedAtDesc(teacherProfileIds);

        long paymentsCount = payments.size();
        long successfulPayments = payments.stream().filter(payment -> payment.getStatus() == OperationStatus.SUCCESS).count();
        long pendingPayments = payments.stream().filter(payment -> payment.getStatus() == OperationStatus.PENDING).count();
        long subscriptions = payments.stream().filter(payment -> payment.getFlowType() == PaymentFlowType.SUBSCRIPTION).count();
        long remainingStudentSlots = Math.max(0, FREE_STUDENT_LIMIT - students);
        boolean hasTransferAlias = teacherProfileIds.isEmpty()
            ? false
            : teacherProfileRepository.findByUserTenantId(tenantId).stream()
                .anyMatch(profile -> profile.getTransferAlias() != null && !profile.getTransferAlias().isBlank());

        List<LaunchpadChecklistItemView> checklist = List.of(
            new LaunchpadChecklistItemView(
                "first-teacher",
                "Crear al menos 1 profesor",
                teachers > 0,
                teachers > 0 ? "Ya tienes estructura operativa para vender." : "El siguiente paso es sumar el primer profesor o profesional.",
                15,
                "Ir a usuarios",
                "#usuarios"
            ),
            new LaunchpadChecklistItemView(
                "first-student",
                "Cargar tus primeros clientes",
                students > 0,
                students > 0 ? "Base cargada para empezar a cobrar." : "Carga 3 clientes reales o piloto para validar el onboarding.",
                15,
                "Cargar clientes",
                "#usuarios"
            ),
            new LaunchpadChecklistItemView(
                "first-course",
                "Crear 1 oferta o curso",
                courses > 0,
                courses > 0 ? "Tu propuesta ya se puede explicar dentro del producto." : "Crea una oferta clara para que el primer cobro tenga contexto.",
                10,
                "Ir a cursos",
                "#cursos"
            ),
            new LaunchpadChecklistItemView(
                "first-enrollment",
                "Asignar 1 cliente a una oferta",
                enrollments > 0,
                enrollments > 0 ? "Ya existe una relacion real entre oferta y cliente." : "La asignacion te acerca al primer uso con sentido.",
                10,
                "Asignar cliente",
                "#cursos"
            ),
            new LaunchpadChecklistItemView(
                "first-payment",
                "Crear el primer link de pago",
                paymentsCount > 0,
                paymentsCount > 0 ? "Ya puedes compartir un flujo real por WhatsApp o email." : "Crea un cobro puntual de prueba y compartelo hoy.",
                15,
                "Crear cobro",
                "#pagos"
            ),
            new LaunchpadChecklistItemView(
                "first-success",
                "Conseguir el primer pago exitoso",
                successfulPayments > 0,
                successfulPayments > 0 ? "Llegaste al primer momento de valor real." : "Haz seguimiento a 3 invitaciones y busca una confirmacion real.",
                20,
                "Hacer seguimiento",
                "#pagos"
            ),
            new LaunchpadChecklistItemView(
                "first-subscription",
                "Probar 1 suscripcion recurrente",
                subscriptions > 0,
                subscriptions > 0 ? "Ya validaste el caso de mayor retencion." : "Activa una suscripcion con un cliente estable para aprender friccion.",
                10,
                "Crear suscripcion",
                "#suscripciones"
            ),
            new LaunchpadChecklistItemView(
                "transfer-alias",
                "Configurar alias para transferencias AR",
                hasTransferAlias,
                hasTransferAlias ? "Ya ofreces una salida local muy familiar para Argentina." : "Carga un alias para contemplar transferencias, muy usado en Argentina.",
                5,
                "Configurar alias",
                "#cuenta"
            )
        );

        long completedSteps = checklist.stream().filter(LaunchpadChecklistItemView::done).count();
        int totalSteps = checklist.size();
        int activationScore = (int) Math.round((completedSteps * 100.0) / checklist.size());
        int experiencePoints = checklist.stream()
            .filter(LaunchpadChecklistItemView::done)
            .mapToInt(LaunchpadChecklistItemView::points)
            .sum();
        String stage = resolveStage(successfulPayments, subscriptions, paymentsCount, enrollments, students);

        return new LaunchpadView(
            "FREE",
            stage,
            activationScore,
            (int) completedSteps,
            totalSteps,
            experiencePoints,
            resolveHeadline(stage, activationScore),
            resolveNextBestAction(checklist),
            resolveNextReward(checklist),
            "El aha moment llega cuando un usuario crea un cobro y consigue su primer pago exitoso sin ayuda manual.",
            "Freemium sugerido para lanzamiento: hasta 50 clientes cargados para aprender uso, activacion y referidos antes de cobrar.",
            new LaunchpadUsageView(
                teachers,
                students,
                courses,
                enrollments,
                paymentsCount,
                successfulPayments,
                subscriptions,
                pendingPayments,
                remainingStudentSlots
            ),
            checklist,
            buildExperiments(stage)
        );
    }

    private String resolveStage(long successfulPayments, long subscriptions, long paymentsCount, long enrollments, long students) {
        if (successfulPayments > 0 && subscriptions > 0 && students >= 3) {
            return "scaling";
        }
        if (successfulPayments > 0) {
            return "activated";
        }
        if (paymentsCount > 0 || enrollments > 0 || students > 0) {
            return "onboarding";
        }
        return "setup";
    }

    private String resolveHeadline(String stage, int activationScore) {
        return switch (stage) {
            case "scaling" -> "Ya tienes senales reales para iterar pricing, referidos y upgrade.";
            case "activated" -> "Ya encontraste valor inicial; ahora toca repetirlo en mas clientes.";
            case "onboarding" -> "Vas bien: convierte configuracion en primer pago confirmado.";
            default -> "Empieza simple: configura tu cuenta y busca el primer cobro real.";
        } + " Score de activacion: " + activationScore + "/100.";
    }

    private String resolveNextBestAction(List<LaunchpadChecklistItemView> checklist) {
        return checklist.stream()
            .filter(item -> !item.done())
            .findFirst()
            .map(item -> item.title())
            .orElse("Itera mensaje, canal y seguimiento para repetir la activacion en los proximos 5 usuarios.");
    }

    private String resolveNextReward(List<LaunchpadChecklistItemView> checklist) {
        return checklist.stream()
            .filter(item -> !item.done())
            .findFirst()
            .map(item -> "Siguiente recompensa: +" + item.points() + " XP al completar '" + item.title() + "'.")
            .orElse("Ya completaste el launchpad base. Ahora toca repetir la activacion con consistencia.");
    }

    private List<LaunchpadExperimentView> buildExperiments(String stage) {
        String followUp = "Mide cuantas personas completan el cobro en menos de 24 horas.";
        if ("setup".equals(stage)) {
            return List.of(
                new LaunchpadExperimentView(
                    "Piloto con 3 clientes",
                    "Si empiezas con pocos casos reales, aprendes objeciones rapido sin soporte pesado.",
                    "3 clientes cargados y 1 cobro enviado hoy.",
                    "Carga 3 clientes reales, crea 1 oferta y comparte un link manual por WhatsApp."
                ),
                new LaunchpadExperimentView(
                    "Mensaje corto de cobro",
                    "Un copy simple deberia reducir dudas en el primer uso.",
                    "Al menos 1 respuesta positiva o pago.",
                    "Envia un texto corto con concepto, monto y link. " + followUp
                ),
                new LaunchpadExperimentView(
                    "Prueba de onboarding self-service",
                    "Un registro claro deberia permitir que un usuario entre sin tu ayuda.",
                    "1 alta nueva desde landing.",
                    "Usa el autoregistro gratis y valida si el usuario entiende que hacer en menos de 5 minutos."
                )
            );
        }

        return List.of(
            new LaunchpadExperimentView(
                "Repite el caso ganador",
                "Lo que ya te dio un pago probablemente se pueda repetir en tu siguiente segmento cercano.",
                "2 pagos nuevos en la misma semana.",
                "Duplica el mismo flujo con 5 usuarios similares al primero que activo."
            ),
            new LaunchpadExperimentView(
                "Mover 1 cliente a suscripcion",
                "La recurrencia mejora retencion y aprendizaje sobre permanencia.",
                "1 suscripcion creada y compartida.",
                "Elige un cliente estable y proponle cobro mensual con el mismo concepto."
            ),
            new LaunchpadExperimentView(
                "Pedir referido luego del exito",
                "Un cliente satisfecho puede abrirte la siguiente ola organica.",
                "1 referido o introduccion.",
                "Cuando un pago salga bien, pide una recomendacion directa con mensaje corto y personal."
            )
        );
    }
}
