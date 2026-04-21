# Suscripciones

Las suscripciones usan `PaymentFlowType.SUBSCRIPTION`.

## Crear suscripcion

```http
POST /api/backoffice/payments/subscriptions
```

Payload:

```json
{
  "provider": "MERCADOPAGO",
  "description": "Mensualidad piano",
  "amount": 1500.00,
  "currency": "UYU",
  "payerEmail": "alumno@example.com",
  "studentUserId": 42,
  "courseId": 7
}
```

## Mercado Pago

Para suscripciones, MiCuota llama:

```http
POST /preapproval
```

Con:

- `reason`
- `payer_email`
- `auto_recurring`
- `external_reference`
- `notification_url`
- `back_url`

El `id` de preapproval queda guardado como `providerReference`.

## Diferencia con pago unico

| Aspecto | Pago unico | Suscripcion |
| --- | --- | --- |
| Endpoint proveedor | `/checkout/preferences` | `/preapproval` |
| Referencia guardada | Preference ID | Preapproval ID |
| Caso de uso | Clase puntual, matricula, saldo | Cuota mensual, plan recurrente |

## Roadmap recomendado

- Renovaciones automáticas con eventos dedicados.
- Pausas y reactivaciones.
- Cambio de plan.
- Dunning por fallo de renovación.
- Historial por alumno/paciente.
