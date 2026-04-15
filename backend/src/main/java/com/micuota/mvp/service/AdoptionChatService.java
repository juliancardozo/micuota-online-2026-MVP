package com.micuota.mvp.service;

import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.UserRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdoptionChatService {

    private final AuthSessionService authSessionService;
    private final UserRepository userRepository;

    public AdoptionChatService(AuthSessionService authSessionService, UserRepository userRepository) {
        this.authSessionService = authSessionService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdoptionChatResponse advise(String token, AdoptionChatRequest request) {
        String normalizedMessage = request.message().toLowerCase(Locale.ROOT);

        AuthSessionService.SessionContext session = authSessionService.findSession(token).orElse(null);
        User sessionUser = null;
        if (session != null) {
            sessionUser = userRepository.findById(session.userId()).orElse(null);
        }

        String role = detectRole(request.roleHint(), request.page(), normalizedMessage, sessionUser);
        String flow = detectFlow(normalizedMessage);

        String situation;
        String recommendation;
        String why;
        String now;
        String text;

        if ("suscripcion".equals(flow)) {
            situation = "Tienes un servicio recurrente y quieres reducir seguimiento manual de pagos.";
            recommendation = "Te conviene suscripcion para pagos periodicos, con opcion de pago unico en etapa de transicion.";
            why = "La suscripcion ordena mensualidades, mejora previsibilidad y evita perseguir comprobantes uno por uno.";
            now = "Define monto y fecha de cobro. Inicia con un grupo piloto de 3 a 5 personas esta semana.";
            text = "Hola, para ordenar los cobros vamos a usar MiCuota.online con una modalidad mensual simple. Si prefieres, en esta primera etapa tambien podemos usar pago unico mientras te adaptas.";
        } else if ("qr".equals(flow)) {
            situation = "Necesitas cobrar de forma presencial, rapida y sin pasos complejos.";
            recommendation = "Te conviene QR para el momento del encuentro.";
            why = "Reduce friccion en caja y facilita que el pago quede registrado al instante.";
            now = "Genera un cobro unico, muestra el QR y valida confirmacion en el momento.";
            text = "Te comparto un QR para que puedas pagar ahora mismo desde tu celular. Es rapido y queda todo ordenado.";
        } else if ("link".equals(flow)) {
            situation = "Cobras en remoto y necesitas una opcion clara para WhatsApp o email.";
            recommendation = "Te conviene link de pago para envio directo.";
            why = "Es facil de entender, no requiere presencia y reduce idas y vueltas administrativas.";
            now = "Crea un cobro, copia el link y envialo con concepto, monto y fecha sugerida.";
            text = "Te comparto este link de pago para que puedas abonarlo cuando te quede comodo. Si tienes dudas, te acompano paso a paso.";
        } else {
            situation = "Estas queriendo ordenar cobros sin volver todo acartonado ni perder cercania con la gente.";
            recommendation = "Arranca liviano: primero pago unico por link, y cuando tome ritmo pasas a suscripcion.";
            why = "Asi el cambio se siente natural, bajas la resistencia y todos se adaptan sin presion.";
            now = "Esta semana prueba con un solo caso real, mandalo con un mensaje simple y mira como responde la persona.";
            text = "Hola! Para hacerlo mas facil, vamos a arrancar con un link de pago simple en cada cobro. Si vemos que funciona comodo para todos, despues pasamos a una modalidad mensual.";
        }

        if ("alumno".equals(role) || "paciente".equals(role)) {
            situation = "Quieres pagar facil y entender exactamente que estas pagando.";
            recommendation = "Para pagos puntuales usa link. Para servicios estables, suscripcion con explicacion previa.";
            why = "Ambos flujos te muestran concepto y estado para evitar confusiones.";
            now = "Pide enlace con detalle de concepto, monto y frecuencia antes de confirmar.";
            text = "Para ti sera simple: recibes un enlace, revisas el detalle y pagas en pocos pasos. Si es mensual, se aclara antes de activar cualquier recurrencia.";
        }

        String answer = String.join("\n\n",
            "1. Que esta pasando\n" + situation,
            "2. Que te conviene mas\n" + recommendation,
            "3. Por que\n" + why,
            "4. Que puedes hacer ahora\n" + now,
            "5. Texto sugerido\n" + text
        );

        return new AdoptionChatResponse(answer, role, flow, "backend-contextual");
    }

    private String detectRole(String roleHint, String page, String message, User sessionUser) {
        if (sessionUser != null) {
            if (sessionUser.getRole() == UserRole.STUDENT) return "alumno";
            if (sessionUser.getRole() == UserRole.TEACHER) return "profesor";
            if (sessionUser.getRole() == UserRole.TENANT_ADMIN || sessionUser.getRole() == UserRole.ADMIN) return "administrador";
        }

        String hint = roleHint == null ? "" : roleHint.toLowerCase(Locale.ROOT);
        if (hint.contains("prof")) return "profesor";
        if (hint.contains("admin")) return "administrador";
        if (hint.contains("alumn")) return "alumno";
        if (hint.contains("pacien")) return "paciente";

        String route = page == null ? "" : page.toLowerCase(Locale.ROOT);
        if (route.contains("profesor")) return "profesor";
        if (route.contains("backoffice")) return "administrador";
        if (route.contains("alumno") || route.contains("pago")) return "alumno";

        if (message.contains("pacient")) return "paciente";
        if (message.contains("alumn")) return "alumno";
        if (message.contains("admin")) return "administrador";
        if (message.contains("profesor") || message.contains("docente") || message.contains("terapeuta")) return "profesor";

        return "usuario";
    }

    private String detectFlow(String message) {
        if (message.contains("suscrip") || message.contains("mensual") || message.contains("cuota")) {
            return "suscripcion";
        }
        if (message.contains("qr") || message.contains("presencial") || message.contains("cara a cara")) {
            return "qr";
        }
        if (message.contains("link") || message.contains("whatsapp") || message.contains("remoto")) {
            return "link";
        }
        return "hibrido";
    }
}
