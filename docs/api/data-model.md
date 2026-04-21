# Modelo de datos

## Entidades principales

```mermaid
erDiagram
  TENANT ||--o{ USER : owns
  USER ||--|| TEACHER_PROFILE : has
  TENANT ||--o{ COURSE : owns
  COURSE ||--o{ COURSE_ENROLLMENT : has
  USER ||--o{ COURSE_ENROLLMENT : student
  TEACHER_PROFILE ||--o{ PAYMENT_OPERATION : creates
  PAYMENT_OPERATION ||--o{ PAYMENT_EVENT : emits
```

## PaymentOperation

Representa una operacion de cobro contra un proveedor.

Campos clave:

- `teacherId`
- `studentUserId`
- `courseId`
- `provider`
- `flowType`
- `amount`
- `currency`
- `checkoutUrl`
- `providerReference`
- `status`
- `rawResponse`
- `reconciliationStatus`

## PaymentEvent

Representa un evento auditable.

Campos clave:

- `operationId`
- `eventType`
- `statusFrom`
- `statusTo`
- `rawPayload`
- `createdAt`

## TeacherProfile

Concentra datos del profesional:

- display name
- access token de Mercado Pago
- API keys de otros proveedores
- alias/banco para transferencia manual

## Course

Puede modelar curso, grupo, plan o servicio recurrente.
