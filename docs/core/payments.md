# Pagos

Los pagos puntuales usan `PaymentFlowType.ONE_TIME`.

## Crear pago

Endpoint privado:

```http
POST /api/backoffice/payments/one-time
```

Campos principales:

| Campo | Descripcion |
| --- | --- |
| `provider` | `MERCADOPAGO`, `PROMETEO`, `STRIPE`, etc. |
| `description` | Concepto visible para el cobro. |
| `amount` | Monto a cobrar. |
| `currency` | Moneda. Ejemplo: `UYU`. |
| `payerEmail` | Email del alumno/paciente. |
| `studentUserId` | Alumno asociado, opcional. |
| `courseId` | Curso/grupo asociado, opcional. |

## Estados

| Estado | Significado |
| --- | --- |
| `CREATED` | Cobro creado, pendiente de pago. |
| `PENDING` | Proveedor lo dejo en revision/proceso. |
| `SUCCESS` | Pago aprobado o acreditado. |
| `FAILURE` | Pago rechazado, cancelado o fallido. |

## Proveedor

MiCuota selecciona proveedor segun:

1. `provider` pedido en el request.
2. `app.payments.default-provider`.
3. `app.payments.fallback-provider`.

## Respuesta

```json
{
  "id": 123,
  "provider": "MERCADOPAGO",
  "flowType": "ONE_TIME",
  "checkoutUrl": "https://www.mercadopago.com/...",
  "providerReference": "123456789-abcd",
  "status": "CREATED"
}
```

## Trazabilidad

Cada pago crea un `PaymentEvent` de tipo `PAYMENT_CREATED`. Si hay email, tambien se registra `PAYMENT_LINK_SENT`.
