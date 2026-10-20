package com.micuota.mvp.web;

import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.service.AuthSessionService;
import com.micuota.mvp.service.MercadoPagoOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/mercadopago/oauth")
@Tag(name = "Mercado Pago OAuth", description = "Conexion de la cuenta de Mercado Pago del profesor a la aplicacion marketplace de MiCuota")
public class MercadoPagoOAuthController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoOAuthController.class);

    private final AuthSessionService authSessionService;
    private final TeacherProfileRepository teacherProfileRepository;
    private final MercadoPagoOAuthService oauthService;

    @Value("${app.frontend-base-url:${app.base-url}}")
    private String frontendBaseUrl;

    public MercadoPagoOAuthController(
        AuthSessionService authSessionService,
        TeacherProfileRepository teacherProfileRepository,
        MercadoPagoOAuthService oauthService
    ) {
        this.authSessionService = authSessionService;
        this.teacherProfileRepository = teacherProfileRepository;
        this.oauthService = oauthService;
    }

    @GetMapping("/authorize")
    @Operation(summary = "URL de autorizacion", description = "Devuelve la URL de Mercado Pago donde el profesor autoriza a MiCuota a cobrar en su nombre.")
    public Map<String, String> authorize(@RequestHeader("X-Auth-Token") String token) {
        return Map.of("url", oauthService.authorizationUrlFor(requireTeacherProfile(token).getId()));
    }

    @GetMapping("/callback")
    @Operation(summary = "Retorno de Mercado Pago", description = "Mercado Pago redirige aca con el codigo; se canjea por el token y se vuelve al panel del profesor.")
    public RedirectView callback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String error
    ) {
        String result = "conectado";
        if (error != null && !error.isBlank()) {
            result = "cancelado";
        } else {
            try {
                oauthService.completeAuthorization(code, state);
            } catch (RuntimeException exception) {
                log.warn("Fallo el OAuth de Mercado Pago: {}", exception.getMessage());
                result = "error";
            }
        }
        String target = UriComponentsBuilder.fromUriString(frontendBaseUrl)
            .path("/profesor.html")
            .queryParam("mp", result)
            .fragment("mercadopago")
            .build()
            .toUriString();
        RedirectView redirectView = new RedirectView(target);
        redirectView.setExposeModelAttributes(false);
        return redirectView;
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Desconectar Mercado Pago", description = "Borra los tokens de Mercado Pago del profesor autenticado.")
    public Map<String, Boolean> disconnect(@RequestHeader("X-Auth-Token") String token) {
        oauthService.disconnect(requireTeacherProfile(token).getId());
        return Map.of("disconnected", true);
    }

    private TeacherProfile requireTeacherProfile(String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        if (session.role() != UserRole.TEACHER) {
            throw new IllegalArgumentException("Operacion permitida solo para TEACHER");
        }
        return teacherProfileRepository.findByUserId(session.userId())
            .orElseThrow(() -> new IllegalArgumentException("El usuario no tiene perfil profesional configurado"));
    }
}
