# Primer cobro de prueba

Esta guia crea un pago unico desde el backoffice.

## 1. Tener token

Inicia sesion y guarda el token:

```bash
export MICUOTA_TOKEN="<token>"
```

## 2. Crear alumno

```bash
curl -X POST http://localhost:8080/api/backoffice/users \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $MICUOTA_TOKEN" \
  -d '{
    "email": "alumno@example.com",
    "fullName": "Alumno Demo",
    "password": "secret",
    "role": "STUDENT"
  }'
```

## 3. Crear cobro unico

```bash
curl -X POST http://localhost:8080/api/backoffice/payments/one-time \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $MICUOTA_TOKEN" \
  -d '{
    "provider": "MERCADOPAGO",
    "description": "Clase individual",
    "amount": 1200.00,
    "currency": "UYU",
    "payerEmail": "alumno@example.com"
  }'
```

Respuesta simplificada:

```json
{
  "id": 123,
  "provider": "MERCADOPAGO",
  "flowType": "ONE_TIME",
  "amount": 1200.00,
  "currency": "UYU",
  "checkoutUrl": "https://www.mercadopago.com/...",
  "providerReference": "123456789-abcd",
  "status": "CREATED"
}
```

## 4. Abrir experiencia del alumno

```text
http://localhost:5500/pago.html?operationId=123
```

## 5. Ver timeline

```bash
curl http://localhost:8080/api/backoffice/payments/123/events \
  -H "X-Auth-Token: $MICUOTA_TOKEN"
```

Eventos esperados:

- `PAYMENT_CREATED`
- `PAYMENT_LINK_SENT` si habia email de pagador
- `WEBHOOK_RECEIVED` cuando llegue notificacion
- `PAYMENT_STATUS_CHANGED` si cambia el estado
