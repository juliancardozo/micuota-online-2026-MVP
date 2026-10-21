package com.micuota.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.integration.prometeo.config.PrometeoProperties;
import com.micuota.mvp.integration.prometeo.service.PrometeoBankingClient;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Pago desde el banco del cliente usando la Banking API de Prometeo, con MiCuota como proxy:
 * el cliente entra a su banco, elige su cuenta y transfiere a la cuenta del profesional; al confirmarse
 * el cobro queda pagado solo.
 *
 * Las credenciales bancarias solo pasan hacia Prometeo: no se guardan en la base ni se registran en logs.
 * Mientras dura el pago (como maximo 5 minutos, lo que dura la sesion en Prometeo) vive en memoria y se cierra al terminar.
 */
@Service
public class BankTransferPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BankTransferPaymentService.class);
    // Prometeo vence la clave de sesion a los 5 minutos: no tiene sentido mantenerla mas.
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);
    private static final Duration PROVIDERS_TTL = Duration.ofHours(1);
    private static final int MAX_LOGINS_PER_OPERATION = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Set<String> CURRENCIES = Set.of("UYU", "USD");

    private final PrometeoBankingClient client;
    private final PrometeoProperties properties;
    private final PaymentOperationRepository paymentOperationRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentService paymentService;

    private final Map<String, BankSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Instant>> loginAttempts = new ConcurrentHashMap<>();
    private volatile List<BankProvider> providersCache = List.of();
    private volatile Instant providersCachedAt = Instant.EPOCH;
    private final Map<String, List<BankAuthField>> fieldsCache = new ConcurrentHashMap<>();

    public BankTransferPaymentService(
        PrometeoBankingClient client,
        PrometeoProperties properties,
        PaymentOperationRepository paymentOperationRepository,
        TeacherProfileRepository teacherProfileRepository,
        PaymentService paymentService
    ) {
        this.client = client;
        this.properties = properties;
        this.paymentOperationRepository = paymentOperationRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentService = paymentService;
    }

    public record BankAuthField(String name, String label, String type, boolean optional) {
    }

    public record BankProvider(String code, String name) {
    }

    public record BankAccount(String id, String name, String maskedNumber, String currency) {
    }

    public record BankClient(String id, String name) {
    }

    public record AuthorizationDevice(String type, String number, List<String> data) {
    }

    public record BankOptions(boolean available, boolean sandbox, List<BankProvider> providers, String reason) {
    }

    /** Paso siguiente del pago: CHALLENGE, CLIENTS, ACCOUNTS, AUTHORIZE o DONE. */
    public record BankStep(
        String session,
        String step,
        String message,
        String challenge,
        List<BankClient> clients,
        List<BankAccount> accounts,
        List<AuthorizationDevice> authorizationDevices,
        String destinationName
    ) {
        static BankStep of(String session, String step) {
            return new BankStep(session, step, null, null, List.of(), List.of(), List.of(), null);
        }
    }

    public record BankStepRequest(
        String session,
        String provider,
        Map<String, String> fields,
        String answer,
        String clientId,
        String accountId,
        String authorizationType,
        String authorizationData,
        String authorizationDeviceNumber
    ) {
    }

    private static final class BankSession {
        final Long operationId;
        final String provider;
        final Map<String, String> loginFields;
        final Instant createdAt = Instant.now();
        volatile String prometeoKey;
        volatile String challengeField;
        volatile List<JsonNode> accounts = List.of();
        volatile String requestId;

        BankSession(Long operationId, String provider, Map<String, String> loginFields) {
            this.operationId = operationId;
            this.provider = provider;
            this.loginFields = loginFields;
        }
    }

    public BankOptions options(Long operationId) {
        String reason = unavailableReason(operationId);
        return new BankOptions(reason == null, properties.isSandbox(), reason == null ? providers() : List.of(), reason);
    }

    public List<BankAuthField> providerFields(String providerCode) {
        requireKnownProvider(providerCode);
        return fieldsCache.computeIfAbsent(providerCode, code -> {
            JsonNode detail = client.providerDetail(code);
            List<BankAuthField> fields = new ArrayList<>();
            for (JsonNode field : detail.path("provider").path("auth_fields")) {
                if (field.path("interactive").asBoolean(false)) {
                    continue;
                }
                fields.add(new BankAuthField(
                    field.path("name").asText(),
                    field.path("label_es").asText(field.path("name").asText()),
                    "password".equals(field.path("type").asText()) ? "password" : "text",
                    field.path("optional").asBoolean(false)
                ));
            }
            return List.copyOf(fields);
        });
    }

    public BankStep login(Long operationId, BankStepRequest request) {
        String reason = unavailableReason(operationId);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        requireKnownProvider(request.provider());
        checkLoginAllowed(operationId);

        Map<String, String> fields = new LinkedHashMap<>();
        for (BankAuthField field : providerFields(request.provider())) {
            String value = request.fields() == null ? null : request.fields().get(field.name());
            if ((value == null || value.isBlank()) && !field.optional()) {
                throw new IllegalArgumentException("Completá " + field.label().toLowerCase(Locale.ROOT));
            }
            if (value != null && !value.isBlank()) {
                fields.put(field.name(), value.trim());
            }
        }
        fields.put("provider", request.provider());

        BankSession session = new BankSession(operationId, request.provider(), fields);
        String token = UUID.randomUUID().toString();
        sessions.put(token, session);
        return handleLogin(token, session, client.login(fields));
    }

    public BankStep answerChallenge(Long operationId, BankStepRequest request) {
        BankSession session = requireSession(operationId, request.session());
        if (session.challengeField == null || request.answer() == null || request.answer().isBlank()) {
            throw new IllegalArgumentException("Escribí el código que te pidió tu banco");
        }
        Map<String, String> fields = new LinkedHashMap<>(session.loginFields);
        fields.put(session.challengeField, request.answer().trim());
        return handleLogin(request.session(), session, client.answerLogin(session.prometeoKey, fields));
    }

    public BankStep selectClient(Long operationId, BankStepRequest request) {
        BankSession session = requireSession(operationId, request.session());
        JsonNode result = client.selectClient(session.prometeoKey, request.clientId());
        if (!"success".equals(result.path("status").asText())) {
            return fail(request.session(), session, "Tu banco no dejó elegir esa empresa. Probá con otra.");
        }
        return accountsStep(request.session(), session);
    }

    public BankStep transfer(Long operationId, BankStepRequest request) {
        BankSession session = requireSession(operationId, request.session());
        PaymentOperation operation = requireOpenOperation(operationId);
        TeacherProfile payee = requirePayee(operation);
        JsonNode account = session.accounts.stream()
            .filter(candidate -> candidate.path("id").asText().equals(request.accountId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Elegí una de tus cuentas"));

        String institution = destinationInstitution(session, payee);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("origin_account", account.path("number").asText());
        fields.put("destination_institution", institution);
        fields.put("destination_account", payee.getTransferAccountNumber().replaceAll("\\s", ""));
        fields.put("currency", operation.getCurrency().toUpperCase(Locale.ROOT));
        fields.put("amount", operation.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        fields.put("concept", "MiCuota " + operation.getId());
        fields.put("destination_owner_name", payee.getTransferAccountHolder());
        fields.put("branch", "0");

        JsonNode result = client.preprocessTransfer(session.prometeoKey, fields);
        JsonNode body = result.path("result");
        if (!"success".equals(result.path("status").asText()) || !body.path("approved").asBoolean(false)) {
            String message = body.path("message").asText(result.path("message").asText(""));
            return fail(request.session(), session, message.isBlank()
                ? "Tu banco no aceptó la transferencia. Revisá que tengas saldo o probá con otra cuenta."
                : "Tu banco no aceptó la transferencia: " + message);
        }
        session.requestId = body.path("request_id").asText();
        List<AuthorizationDevice> devices = new ArrayList<>();
        for (JsonNode device : body.path("authorization_devices")) {
            List<String> data = new ArrayList<>();
            device.path("data").forEach(item -> data.add(item.asText()));
            if (data.isEmpty() && device.path("data").isTextual()) {
                data.add(device.path("data").asText());
            }
            devices.add(new AuthorizationDevice(device.path("type").asText(), device.path("number").asText(null), data));
        }
        return new BankStep(request.session(), "AUTHORIZE", null, null, List.of(), List.of(), devices, body.path("message").asText(payee.getTransferAccountHolder()));
    }

    public BankStep confirm(Long operationId, BankStepRequest request) {
        BankSession session = requireSession(operationId, request.session());
        if (session.requestId == null) {
            throw new IllegalArgumentException("Primero elegí desde qué cuenta pagás");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("request_id", session.requestId);
        fields.put("authorization_type", blankToEmpty(request.authorizationType()));
        fields.put("authorization_data", blankToEmpty(request.authorizationData()));
        fields.put("authorization_device_number", blankToEmpty(request.authorizationDeviceNumber()));
        JsonNode result = client.confirmTransfer(session.prometeoKey, fields);
        if (!"success".equals(result.path("status").asText()) && properties.canSimulateConfirm()
            && result.path("message").asText("").startsWith("Respuesta inesperada")) {
            log.warn("Sandbox: confirmacion de Prometeo simulada operationId={} (el sandbox devolvio error)", operationId);
            paymentService.markPaidByBankTransfer(operationId, "SIMULADO-SANDBOX-" + session.requestId);
            close(request.session(), session);
            return new BankStep(request.session(), "DONE", "Pago simulado: el sandbox de Prometeo no confirma transferencias.", null,
                List.of(), List.of(), List.of(), null);
        }
        if (!"success".equals(result.path("status").asText())) {
            String message = result.path("message").asText("");
            log.warn("Prometeo no confirmo la transferencia operationId={} status={}", operationId, result.path("status").asText());
            return fail(request.session(), session, message.isBlank() || message.startsWith("Respuesta inesperada")
                ? "Tu banco no pudo confirmar la transferencia. No se debitó nada; probá de nuevo o pagá con los datos de transferencia."
                : "Tu banco no confirmó la transferencia: " + message);
        }
        String reference = firstText(result, "operation_id", "transfer_id", "id", "request_id");
        paymentService.markPaidByBankTransfer(operationId, reference == null ? session.requestId : reference);
        close(request.session(), session);
        log.info("Pago desde el banco confirmado operationId={} provider={}", operationId, session.provider);
        return BankStep.of(request.session(), "DONE");
    }

    public void cancel(Long operationId, String token) {
        BankSession session = sessions.get(token);
        if (session != null && session.operationId.equals(operationId)) {
            close(token, session);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireSessions() {
        Instant limit = Instant.now().minus(SESSION_TTL);
        sessions.forEach((token, session) -> {
            if (session.createdAt.isBefore(limit)) {
                close(token, session);
            }
        });
    }

    private BankStep handleLogin(String token, BankSession session, JsonNode result) {
        String status = result.path("status").asText();
        if (result.hasNonNull("key")) {
            session.prometeoKey = result.path("key").asText();
        }
        switch (status) {
            case "logged_in":
                session.challengeField = null;
                return accountsStep(token, session);
            case "interaction_required":
                session.challengeField = result.path("field").asText("otp");
                return new BankStep(token, "CHALLENGE", null, result.path("context").asText("Tu banco pide un código de verificación."),
                    List.of(), List.of(), List.of(), null);
            case "select_client":
                List<BankClient> clients = new ArrayList<>();
                JsonNode list = client.clients(session.prometeoKey).path("clients");
                list.fields().forEachRemaining(entry -> clients.add(new BankClient(entry.getKey(), entry.getValue().asText())));
                if (list.isArray()) {
                    list.forEach(item -> clients.add(new BankClient(item.path("id").asText(), item.path("name").asText())));
                }
                return new BankStep(token, "CLIENTS", null, null, clients, List.of(), List.of(), null);
            case "wrong_credentials":
                return fail(token, session, "Tu banco dice que el usuario o la contraseña no son correctos.");
            default:
                return fail(token, session, "No pudimos entrar a tu banco. Probá de nuevo en un rato.");
        }
    }

    private BankStep accountsStep(String token, BankSession session) {
        PaymentOperation operation = requireOpenOperation(session.operationId);
        String currency = operation.getCurrency().toUpperCase(Locale.ROOT);
        List<JsonNode> matching = new ArrayList<>();
        for (JsonNode account : client.accounts(session.prometeoKey).path("accounts")) {
            if (currency.equalsIgnoreCase(account.path("currency").asText())) {
                matching.add(account);
            }
        }
        if (matching.isEmpty()) {
            return fail(token, session, "No encontramos cuentas en " + currency + " en tu banco para hacer este pago.");
        }
        session.accounts = List.copyOf(matching);
        List<BankAccount> accounts = matching.stream()
            .map(account -> new BankAccount(
                account.path("id").asText(),
                account.path("name").asText("Cuenta"),
                mask(account.path("number").asText()),
                account.path("currency").asText()
            ))
            .toList();
        return new BankStep(token, "ACCOUNTS", null, null, List.of(), accounts, List.of(), null);
    }

    // En produccion el banco destino se busca por nombre en la lista de destinos que ofrece el banco del cliente.
    private String destinationInstitution(BankSession session, TeacherProfile payee) {
        if (properties.isSandbox()) {
            return properties.getSandboxDestinationInstitution();
        }
        String wanted = normalizeBank(payee.getTransferBankName());
        for (JsonNode destination : client.transferDestinations(session.prometeoKey).path("destinations")) {
            String name = normalizeBank(destination.path("name").asText());
            if (!wanted.isBlank() && (name.contains(wanted) || wanted.contains(name))) {
                return destination.path("id").asText();
            }
        }
        throw new IllegalArgumentException("Desde tu banco no se puede transferir a " + payee.getTransferBankName()
            + " por acá. Usá los datos de transferencia de la página.");
    }

    private String unavailableReason(Long operationId) {
        if (!client.isConfigured()) {
            return "El pago desde el banco no está habilitado";
        }
        PaymentOperation operation = paymentOperationRepository.findById(operationId).orElse(null);
        if (operation == null) {
            return "Operacion no encontrada";
        }
        if (operation.getStatus() == OperationStatus.SUCCESS) {
            return "Este cobro ya está pagado";
        }
        if (operation.getFlowType() == PaymentFlowType.SUBSCRIPTION) {
            return "Las suscripciones de Mercado Pago no se pagan por transferencia";
        }
        if (!CURRENCIES.contains(operation.getCurrency().toUpperCase(Locale.ROOT))) {
            return "El pago desde el banco es solo en pesos o dólares";
        }
        TeacherProfile payee = teacherProfileRepository.findById(operation.getTeacherId()).orElse(null);
        if (payee == null || isBlank(payee.getTransferAccountNumber()) || isBlank(payee.getTransferAccountHolder()) || isBlank(payee.getTransferBankName())) {
            return "Quien te cobra no cargó sus datos bancarios completos";
        }
        return null;
    }

    private List<BankProvider> providers() {
        if (Instant.now().isAfter(providersCachedAt.plus(PROVIDERS_TTL)) || providersCache.isEmpty()) {
            List<BankProvider> providers = new ArrayList<>();
            for (JsonNode provider : client.providers().path("providers")) {
                String country = provider.path("country").asText();
                String code = provider.path("code").asText();
                boolean sandboxTestBank = properties.isSandbox() && "test".equals(code);
                if (properties.getCountry().equalsIgnoreCase(country) || sandboxTestBank) {
                    providers.add(new BankProvider(code, sandboxTestBank ? "Banco de prueba (sandbox)" : provider.path("name").asText(code)));
                }
            }
            providersCache = List.copyOf(providers);
            providersCachedAt = Instant.now();
        }
        return providersCache;
    }

    private void requireKnownProvider(String providerCode) {
        if (providerCode == null || providers().stream().noneMatch(provider -> provider.code().equals(providerCode))) {
            throw new IllegalArgumentException("Elegí tu banco de la lista");
        }
    }

    private void checkLoginAllowed(Long operationId) {
        Deque<Instant> attempts = loginAttempts.computeIfAbsent(operationId, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            Instant limit = Instant.now().minus(LOGIN_WINDOW);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(limit)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= MAX_LOGINS_PER_OPERATION) {
                throw new IllegalArgumentException("Demasiados intentos. Probá de nuevo en 15 minutos o pagá con los datos de transferencia.");
            }
            attempts.addLast(Instant.now());
        }
    }

    private BankSession requireSession(Long operationId, String token) {
        BankSession session = token == null ? null : sessions.get(token);
        if (session == null || !session.operationId.equals(operationId)
            || session.createdAt.isBefore(Instant.now().minus(SESSION_TTL))) {
            throw new IllegalArgumentException("Se venció el tiempo con tu banco (5 minutos). Empezá de nuevo.");
        }
        return session;
    }

    private PaymentOperation requireOpenOperation(Long operationId) {
        PaymentOperation operation = paymentOperationRepository.findById(operationId)
            .orElseThrow(() -> new IllegalArgumentException("Operacion no encontrada"));
        if (operation.getStatus() == OperationStatus.SUCCESS) {
            throw new IllegalArgumentException("Este cobro ya está pagado");
        }
        return operation;
    }

    private TeacherProfile requirePayee(PaymentOperation operation) {
        return teacherProfileRepository.findById(operation.getTeacherId())
            .orElseThrow(() -> new IllegalArgumentException("Perfil profesional no encontrado"));
    }

    private BankStep fail(String token, BankSession session, String message) {
        close(token, session);
        throw new IllegalArgumentException(message);
    }

    private void close(String token, BankSession session) {
        sessions.remove(token);
        session.loginFields.clear();
        if (session.prometeoKey != null) {
            client.logout(session.prometeoKey);
        }
    }

    static String normalizeBank(String value) {
        if (value == null) {
            return "";
        }
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        plain = plain.replace("banco de la republica oriental del uruguay", "brou").replace("republica", "brou");
        return plain.replaceAll("\\b(banco|bank|s\\.?a\\.?|uruguay)\\b", "").replaceAll("[^a-z0-9]", "");
    }

    private static String mask(String number) {
        String digits = number == null ? "" : number.trim();
        return digits.length() <= 4 ? digits : "•••• " + digits.substring(digits.length() - 4);
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode found = node.findValue(name);
            if (found != null && !found.isNull() && !found.asText().isBlank()) {
                return found.asText();
            }
        }
        return null;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
