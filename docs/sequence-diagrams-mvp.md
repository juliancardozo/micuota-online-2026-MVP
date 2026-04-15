# Sequence Diagrams - MVP

Documento técnico consolidado para revisar los flujos principales de extremo a extremo.

## 1. Profesor se loguea

Objetivo: autenticar al profesor y redirigirlo automáticamente a su dashboard según rol.

Actor principal: Profesor.

Endpoint: POST /api/auth/login

```mermaid
sequenceDiagram
    autonumber
    actor P as Profesor
    participant L as landing.html
    participant AC as AuthController
    participant TAS as TenantAuthService
    participant TR as TenantRepository
    participant UR as UserRepository
    participant ASS as AuthSessionService

    P->>L: Completa slug + email + contraseña
    L->>AC: POST /api/auth/login
    AC->>TAS: login(request)
    TAS->>TR: findBySlug(tenantSlug)
    TR-->>TAS: tenant
    TAS->>UR: findByTenantIdAndEmail(...)
    UR-->>TAS: user (TEACHER)
    TAS->>TAS: valida password
    TAS->>ASS: createSession(tenantId, userId, role)
    ASS-->>TAS: token
    TAS-->>AC: AuthResponse(token, role, backofficeUrl)
    AC-->>L: 200 OK
    L->>L: guarda token en localStorage
    L->>P: redirige a /profesor.html?token=...
```

## 2. Configuración básica del profesor

Objetivo: dejar lista la base operativa (usuario profesor, perfil profesional y curso asignado).

Actor principal: Tenant Admin.

Endpoint:
- POST /api/backoffice/users
- POST /api/backoffice/courses

```mermaid
sequenceDiagram
    autonumber
    actor A as Tenant Admin
    participant B as backoffice.html
    participant BC as BackofficeController
    participant AS as AuthSessionService
    participant BS as BackofficeService
    participant UR as UserRepository
    participant TPR as TeacherProfileRepository
    participant CR as CourseRepository

    A->>B: Crea usuario TEACHER
    B->>BC: POST /api/backoffice/users
    BC->>AS: requireSession(token)
    BC->>BS: createUser(tenantId, request)
    BS->>UR: save(User role=TEACHER)
    BS->>TPR: save(TeacherProfile displayName)
    BS-->>BC: BackofficeUserView
    BC-->>B: 200 OK

    A->>B: Crea curso asignando profesor
    B->>BC: POST /api/backoffice/courses
    BC->>BS: createCourse(tenantId, request)
    BS->>CR: save(Course)
    BS-->>BC: CourseView
    BC-->>B: 200 OK
```

## 3. Crear pago único

Objetivo: generar una operación ONE_TIME con checkout del proveedor y persistencia en estado CREATED.

Actor principal: Profesor.

Endpoint: POST /api/backoffice/payments/one-time

```mermaid
sequenceDiagram
    autonumber
    actor P as Profesor
    participant B as backoffice.html
    participant BC as BackofficeController
    participant AS as AuthSessionService
    participant PS as PaymentService
    participant TPR as TeacherProfileRepository
    participant GW as PaymentProviderGateway
    participant POR as PaymentOperationRepository

    P->>B: Envía formulario de pago único
    B->>BC: POST /api/backoffice/payments/one-time
    BC->>AS: requireSession(token)
    BC->>PS: createOneTimeForUser(userId, request)
    PS->>TPR: findByUserId(userId)
    TPR-->>PS: teacherProfileId
    PS->>PS: createOperation(ONE_TIME,...)
    PS->>GW: createCheckout(...)
    GW-->>PS: checkoutUrl + reference
    PS->>POR: save(PaymentOperation status=CREATED)
    POR-->>PS: operation(id,...)
    PS-->>BC: operation
    BC-->>B: 200 OK
```

## 4. Crear suscripción

Objetivo: generar una operación SUBSCRIPTION con checkout del proveedor y persistencia en estado CREATED.

Actor principal: Profesor.

Endpoint: POST /api/backoffice/payments/subscriptions

```mermaid
sequenceDiagram
    autonumber
    actor P as Profesor
    participant B as backoffice.html
    participant BC as BackofficeController
    participant PS as PaymentService
    participant GW as PaymentProviderGateway
    participant POR as PaymentOperationRepository

    P->>B: Envía formulario de suscripción
    B->>BC: POST /api/backoffice/payments/subscriptions
    BC->>PS: createSubscriptionForUser(...)
    PS->>PS: createOperation(SUBSCRIPTION,...)
    PS->>GW: createCheckout(SUBSCRIPTION,...)
    GW-->>PS: checkoutUrl + reference
    PS->>POR: save(PaymentOperation status=CREATED)
    POR-->>PS: operation
    PS-->>BC: operation
    BC-->>B: 200 OK
```

## 5. Profesor ve enlace y QR

Objetivo: mostrar link de app, link proveedor y QR para compartir el cobro.

Actor principal: Profesor.

Endpoint: GET /api/backoffice/payments/by-course?courseId=...

```mermaid
sequenceDiagram
    autonumber
    actor P as Profesor
    participant B as backoffice.html
    participant BC as BackofficeController
    participant PS as PaymentService

    P->>B: Click en "Cargar link/QR"
    B->>BC: GET /api/backoffice/payments/by-course?courseId=...
    BC->>PS: lastOperationsByUserAndCourse(userId, courseId)
    PS-->>BC: operaciones ordenadas desc
    BC-->>B: lista
    B->>B: toma última operación
    B->>B: arma /pago.html?operationId=id
    B->>B: genera QR con api.qrserver.com(data=checkoutUrl)
    B->>P: muestra link app + link proveedor + QR
```

## 6. Alumno paga

Objetivo: consultar operación pública y completar el pago en checkout externo.

Actor principal: Alumno.

Endpoint: GET /api/public/payments/{operationId}

```mermaid
sequenceDiagram
    autonumber
    actor Al as Alumno
    participant Pg as pago.html
    participant PPC as PublicPaymentsController
    participant PS as PaymentService
    participant PR as Proveedor de pago

    Al->>Pg: Abre /pago.html?operationId=123
    Pg->>PPC: GET /api/public/payments/123
    PPC->>PS: getPublicView(123)
    PS-->>PPC: PaymentPublicView(checkoutUrl, monto, estado)
    PPC-->>Pg: 200 OK
    Pg->>Al: Muestra detalle y botón pagar
    Al->>PR: Abre checkoutUrl y paga
    PR-->>Al: Resultado de pago
```

## 7. Callbacks mínimos

Objetivo: actualizar el estado de la operación según resultado del proveedor.

Actor principal: Proveedor de pago.

Endpoint:
- GET /api/callbacks/success?operationId=...
- GET /api/callbacks/pending?operationId=...
- GET /api/callbacks/failure?operationId=...

```mermaid
sequenceDiagram
    autonumber
    participant PR as Proveedor de pago
    participant CC as CallbackController
    participant PS as PaymentService
    participant POR as PaymentOperationRepository

    PR->>CC: callback (success/pending/failure)
    CC->>PS: updateStatus(operationId, status)
    PS->>POR: findById + save(status)
    POR-->>PS: operation actualizada
    PS-->>CC: operation
    CC-->>PR: 200 OK
```

## 8. Evolución sin romper MVP

Objetivo: extender proveedores y observabilidad manteniendo contratos actuales.

Actor principal: Equipo de tecnología.

Endpoint: no aplica (evolución de arquitectura interna).

```mermaid
sequenceDiagram
    autonumber
    actor E as Equipo
    participant CFG as application.properties
    participant PS as PaymentService
    participant GW as PaymentProviderGateway
    participant OBS as Eventos/Observabilidad

    E->>GW: agrega nuevo adapter de proveedor
    E->>PS: registra provider en mapa gateways
    E->>CFG: configura credenciales por entorno
    E->>OBS: agrega eventos de dominio
    Note over PS,GW: Se mantiene createOperation + updateStatus
```

---

## V2 futura (recomendada)

### A. Flujo con MercadoPago real

Objetivo: reemplazar simulación mínima por integración completa (preferencias, metadata, webhooks firmados, conciliación).

Actor principal: Proveedor MercadoPago.

Endpoint: a definir en diseño V2.

```mermaid
sequenceDiagram
    autonumber
    actor P as Profesor
    participant B as Backoffice
    participant MP as MercadoPago API
    participant WH as Webhook Receiver
    participant PS as PaymentService

    P->>B: crea cobro
    B->>MP: crea preferencia real
    MP-->>B: init_point + external_reference
    MP->>WH: webhook payment.updated (firmado)
    WH->>PS: valida firma + actualiza estado
```

### B. Flujo cuenta maestra vs cuenta del profesor

Objetivo: soportar dos estrategias de cobro (master account o subcuenta/profesor) por tenant y/o curso.

Actor principal: Tenant Admin.

Endpoint: a definir en diseño V2.

```mermaid
sequenceDiagram
    autonumber
    actor A as Tenant Admin
    participant CFG as Configuración Tenant
    participant PS as PaymentService
    participant GW as Provider Gateway

    A->>CFG: define estrategia de cobranza
    CFG-->>PS: master-account o profesor-account
    PS->>GW: createCheckout con credenciales resueltas
    GW-->>PS: checkout según estrategia
```

### C. Webhooks reales en lugar de callbacks mínimos

Objetivo: pasar de callbacks GET manuales a webhooks idempotentes, firmados y auditables.

Actor principal: Proveedor de pago.

Endpoint: POST /api/webhooks/{provider} (propuesto V2)

```mermaid
sequenceDiagram
    autonumber
    participant PR as Proveedor
    participant WH as WebhookController
    participant SEC as ValidadorFirma
    participant IDE as IdempotencyStore
    participant PS as PaymentService

    PR->>WH: POST webhook firmado
    WH->>SEC: valida firma/timestamp
    WH->>IDE: verifica duplicado
    IDE-->>WH: no duplicado
    WH->>PS: updateStatus + auditoría
    WH-->>PR: 200 ACK
```
