# Callbacks y webhooks

## Callbacks browser

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

MiCuota consulta Mercado Pago para enriquecer la notificacion y luego actualiza la operacion.
