package com.micuota.mvp.integration.prometeo.web;

import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.integration.prometeo.dto.PrometeoAccountValidationRequest;
import com.micuota.mvp.integration.prometeo.dto.PrometeoAccountValidationResponse;
import com.micuota.mvp.integration.prometeo.service.PrometeoAccountValidationService;
import com.micuota.mvp.service.AuthSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banking/prometeo")
public class PrometeoBankingController {

    private final AuthSessionService authSessionService;
    private final PrometeoAccountValidationService validationService;

    public PrometeoBankingController(
        AuthSessionService authSessionService,
        PrometeoAccountValidationService validationService
    ) {
        this.authSessionService = authSessionService;
        this.validationService = validationService;
    }

    @PostMapping("/account-validations")
    public PrometeoAccountValidationResponse validateAccount(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody PrometeoAccountValidationRequest request
    ) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        if (session.role() != UserRole.TENANT_ADMIN && session.role() != UserRole.TEACHER) {
            throw new IllegalArgumentException("Operacion permitida para TENANT_ADMIN o TEACHER");
        }
        return validationService.validateAccount(session.tenantId(), session.userId(), request);
    }
}
