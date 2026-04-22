# Webhooks

MiCuota recibe notificaciones de proveedores para actualizar estados de pago y registrar auditoria.

## Mercado Pago

Endpoint:

```http
POST /api/callbacks/mercadopago
```

Variables:

```bash
export MERCADOPAGO_WEBHOOK_SECRET=...
```

El `Access Token` de Mercado Pago no es global para crear cobros. Lo conecta cada profesor desde su perfil para que los fondos, preferencias y suscripciones queden asociados a la cuenta vendedora correcta.

## Firma

Si `MERCADOPAGO_WEBHOOK_SECRET` esta configurado, MiCuota valida:

- Header `x-signature`.
- Header `x-request-id`.
- `data.id` del payload.
- Manifest `id:{data.id};request-id:{x-request-id};ts:{ts};`.
- HMAC SHA256 contra el secreto configurado.

## Resolucion de estado

Cuando el webhook trae `type=payment`, MiCuota consulta:

```http
GET /v1/payments/{paymentId}
```

Con esa respuesta resuelve:

- `status`
- `external_reference`
- `preference_id`

La `notification_url` generada por MiCuota incluye `externalReference`. Con esa referencia se ubica primero la `PaymentOperation`, se obtiene el token Mercado Pago del profesor propietario y recien despues se consulta la API del proveedor.

## Mapeo de estados

| Mercado Pago | MiCuota |
| --- | --- |
| `approved`, `accredited`, `authorized` | `SUCCESS` |
| `pending`, `in_process`, `in_mediation` | `PENDING` |
| `rejected`, `cancelled`, `refunded`, `charged_back` | `FAILURE` |

## Callback browser

Los `back_urls` apuntan a:

```text
/api/callbacks/success/by-reference
/api/callbacks/pending/by-reference
/api/callbacks/failed/by-reference
```

Aceptan:

- `providerReference`
- `preference_id`
- `externalReference`
- `external_reference`

## Simulacion local sin firma

Sin `MERCADOPAGO_WEBHOOK_SECRET`, puedes probar:

```bash
curl -X POST http://localhost:8080/api/callbacks/mercadopago \
  -H "Content-Type: application/json" \
  -d '{
    "type": "payment",
    "status": "approved",
    "preference_id": "123456789-abcd",
    "external_reference": "MC-demo"
  }'
```
