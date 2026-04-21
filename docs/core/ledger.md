# Ledger y timeline

El ledger minimo de MiCuota vive en `payment_events`.

## Objetivo

No basta con saber el ultimo estado de un pago. MiCuota necesita saber la historia:

- cuando se creo
- que proveedor se uso
- que payload devolvio
- cuando se envio el link
- que webhook llego
- que estado cambio
- que conciliacion se aplico

## Entidad PaymentEvent

Campos principales:

| Campo | Descripcion |
| --- | --- |
| `operationId` | PaymentOperation asociada. |
| `teacherId` | Profesional propietario. |
| `studentUserId` | Alumno/paciente asociado. |
| `courseId` | Curso/grupo asociado. |
| `eventType` | Tipo de evento. |
| `statusFrom` | Estado anterior si aplica. |
| `statusTo` | Estado nuevo si aplica. |
| `amount` | Monto snapshot. |
| `currency` | Moneda snapshot. |
| `providerReference` | Referencia del proveedor. |
| `rawPayload` | Payload completo o metadata del evento. |
| `createdAt` | Fecha del evento. |

## Eventos actuales

| Evento | Cuando se registra |
| --- | --- |
| `PAYMENT_CREATED` | Al persistir la operacion. |
| `PAYMENT_LINK_SENT` | Cuando se envia email de link. |
| `WEBHOOK_RECEIVED` | Cuando entra notificacion de proveedor. |
| `PAYMENT_STATUS_CHANGED` | Cuando cambia el estado. |

## Consultar timeline

```http
GET /api/backoffice/payments/{operationId}/events
```

Requiere:

```http
X-Auth-Token: <token>
```

Respuesta:

```json
[
  {
    "id": 1,
    "operationId": 123,
    "eventType": "PAYMENT_CREATED",
    "statusFrom": null,
    "statusTo": "CREATED",
    "amount": 1200.00,
    "currency": "UYU",
    "providerReference": "123456789-abcd",
    "createdAt": "2026-04-21T10:00:00-03:00"
  }
]
```

## Por que importa

Este ledger habilita:

- soporte con evidencia
- conciliacion
- reportes de mora
- recupero automatizado
- metricas de conversion
- futuras liquidaciones y split de pagos
