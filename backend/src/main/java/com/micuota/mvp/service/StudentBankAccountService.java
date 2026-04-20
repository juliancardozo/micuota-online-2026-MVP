package com.micuota.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.BankAccountVerificationStatus;
import com.micuota.mvp.domain.StudentBankAccount;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.integration.prometeo.dto.PrometeoAccountValidationRequest;
import com.micuota.mvp.integration.prometeo.dto.PrometeoAccountValidationResponse;
import com.micuota.mvp.integration.prometeo.exception.PrometeoIntegrationException;
import com.micuota.mvp.integration.prometeo.service.PrometeoAccountValidationService;
import com.micuota.mvp.repository.StudentBankAccountRepository;
import com.micuota.mvp.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentBankAccountService {

    private final StudentBankAccountRepository studentBankAccountRepository;
    private final UserRepository userRepository;
    private final PrometeoAccountValidationService prometeoAccountValidationService;
    private final ObjectMapper objectMapper;

    public StudentBankAccountService(
        StudentBankAccountRepository studentBankAccountRepository,
        UserRepository userRepository,
        PrometeoAccountValidationService prometeoAccountValidationService,
        ObjectMapper objectMapper
    ) {
        this.studentBankAccountRepository = studentBankAccountRepository;
        this.userRepository = userRepository;
        this.prometeoAccountValidationService = prometeoAccountValidationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<StudentBankAccountView> listAccounts(Long tenantId, Long studentUserId) {
        List<StudentBankAccount> accounts = studentUserId == null
            ? studentBankAccountRepository.findByTenantIdOrderByPreferredDescUpdatedAtDesc(tenantId)
            : studentBankAccountRepository.findByTenantIdAndStudentIdOrderByPreferredDescUpdatedAtDesc(tenantId, studentUserId);
        return accounts.stream().map(this::toView).toList();
    }

    @Transactional
    public StudentBankAccountView validateAndSave(Long tenantId, Long actorUserId, SaveStudentBankAccountRequest request) {
        User actor = userRepository.findById(actorUserId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        User student = userRepository.findById(request.studentUserId())
            .orElseThrow(() -> new IllegalArgumentException("Alumno/paciente no encontrado"));

        if (!student.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("El alumno/paciente no pertenece al tenant");
        }
        if (student.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("El usuario seleccionado no es alumno/paciente");
        }

        PrometeoAccountValidationResponse validation;
        try {
            validation = prometeoAccountValidationService.validateAccount(
                tenantId,
                actorUserId,
                new PrometeoAccountValidationRequest(
                    request.countryCode(),
                    request.accountNumber(),
                    request.accountType(),
                    request.bankCode(),
                    request.branchCode(),
                    request.documentNumber(),
                    request.documentType()
                )
            );
        } catch (PrometeoIntegrationException exception) {
            // MVP-friendly: si Prometeo no puede validar (sandbox/banco no disponible),
            // igual guardamos la cuenta como "requiere revision" para destrabar el flujo.
            validation = new PrometeoAccountValidationResponse(
                "PROMETEO",
                true,
                false,
                exception.getUpstreamHttpStatus() == null ? 0 : exception.getUpstreamHttpStatus(),
                exception.getProviderCode(),
                exception.getMessage(),
                request.countryCode(),
                maskAccountNumber(request.accountNumber()),
                null,
                null
            );
        }

        boolean preferred = request.preferred() == null ? true : request.preferred();
        List<StudentBankAccount> existingAccounts = studentBankAccountRepository.findByTenantIdAndStudentIdOrderByPreferredDescUpdatedAtDesc(
            tenantId,
            student.getId()
        );
        if (existingAccounts.isEmpty()) {
            preferred = true;
        }
        if (preferred) {
            existingAccounts.forEach(account -> {
                if (account.isPreferred()) {
                    account.setPreferred(false);
                    account.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                }
            });
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        StudentBankAccount account = new StudentBankAccount();
        account.setTenant(student.getTenant());
        account.setStudent(student);
        account.setCreatedByUser(actor);
        account.setProvider(validation.provider());
        account.setCountryCode(request.countryCode().trim().toUpperCase());
        account.setAccountType(request.accountType().trim().toUpperCase());
        account.setBankCode(blankToNull(request.bankCode()));
        account.setBranchCode(blankToNull(request.branchCode()));
        account.setAccountHolderName(resolveAccountHolderName(request.accountHolderName(), student));
        account.setAccountNumberMasked(validation.accountNumberMasked());
        account.setAccountLast4(last4(request.accountNumber()));
        account.setVerificationStatus(resolveStatus(validation));
        account.setProviderCode(validation.providerCode());
        account.setProviderMessage(validation.message());
        account.setPreferred(preferred);
        account.setRawResponse(toRawJson(validation));
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setVerifiedAt(validation.success() ? now : null);

        return toView(studentBankAccountRepository.save(account));
    }

    private String maskAccountNumber(String accountNumber) {
        String compact = accountNumber == null ? "" : accountNumber.trim();
        if (compact.isEmpty()) {
            return "";
        }
        if (compact.length() <= 4) {
            return "*".repeat(compact.length());
        }
        return "*".repeat(compact.length() - 4) + compact.substring(compact.length() - 4);
    }

    private StudentBankAccountView toView(StudentBankAccount account) {
        return new StudentBankAccountView(
            account.getId(),
            account.getStudent().getId(),
            account.getStudent().getFullName(),
            account.getStudent().getEmail(),
            account.getProvider(),
            account.getCountryCode(),
            account.getAccountType(),
            account.getBankCode(),
            account.getBranchCode(),
            account.getAccountHolderName(),
            account.getAccountNumberMasked(),
            account.getAccountLast4(),
            account.getVerificationStatus(),
            account.getProviderCode(),
            account.getProviderMessage(),
            account.isPreferred(),
            account.getCreatedAt(),
            account.getUpdatedAt(),
            account.getVerifiedAt()
        );
    }

    private BankAccountVerificationStatus resolveStatus(PrometeoAccountValidationResponse validation) {
        if (validation.success()) {
            return BankAccountVerificationStatus.VERIFIED;
        }
        if (validation.providerCode() != null && validation.providerCode() >= 500) {
            return BankAccountVerificationStatus.REVIEW_REQUIRED;
        }
        return BankAccountVerificationStatus.REJECTED;
    }

    private String resolveAccountHolderName(String value, User student) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized : student.getFullName();
    }

    private String toRawJson(PrometeoAccountValidationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar la validacion bancaria", exception);
        }
    }

    private String last4(String accountNumber) {
        String compact = accountNumber == null ? "" : accountNumber.trim();
        if (compact.isEmpty()) {
            return "";
        }
        return compact.length() <= 4 ? compact : compact.substring(compact.length() - 4);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
