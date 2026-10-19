# Callbacks y webhooks

## Callbacks browser

Son el retorno del navegador desde el checkout. **Solo redirigen a `pago.html`; no modifican el estado del pago.**

```http
GET /api/callbacks/success
GET /api/callbacks/pending
GET /api/callbacks/failure
GET /api/callbacks/failed
```

Aceptan:

```text
operationId
```

## Callbacks por referencia

```http
GET /api/callbacks/success/by-reference
GET /api/callbacks/pending/by-reference
GET /api/callbacks/failure/by-reference
GET /api/callbacks/failed/by-reference
```

Aceptan:

```text
providerReference
preference_id
externalReference
external_reference
```

## Mercado Pago webhook

```http
POST /api/callbacks/mercadopago
```

Headers:

```http
x-signature: ts=<timestamp>,v1=<hmac>
x-request-id: <request-id>
```

Payload tipico:

```json
{
  "type": "payment",
  "data": {
    "id": "987654321"
  }
}
```

Si hay `MERCADOPAGO_WEBHOOK_SECRET` se valida la firma con el template `id:[data.id];request-id:[x-request-id];ts:[ts];` (HMAC-SHA256 hex, `data.id` en minusculas, se omite cada valor ausente). MiCuota toma solo `data.id`, consulta `/v1/payments/{id}` o `/preapproval/{id}` con el token del profesor y actualiza la operacion con el `status` y `external_reference` que devuelve Mercado Pago. El payload nunca define el estado. Si la consulta falla responde error para que Mercado Pago reintente; las notificaciones de tipos no soportados se responden `200` con `ignored: true`.
