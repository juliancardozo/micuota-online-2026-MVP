# Pagos publicos

Los endpoints publicos permiten que el alumno o paciente vea y complete un pago sin iniciar sesion.

## Ver pago

```http
GET /api/public/payments/{operationId}
```

Respuesta:

```json
{
  "operationId": 123,
  "professionalName": "Paula Gomez",
  "description": "Clase individual",
  "amount": 1200.00,
  "currency": "UYU",
  "flowType": "ONE_TIME",
  "provider": "MERCADOPAGO",
  "status": "CREATED",
  "checkoutUrl": "https://www.mercadopago.com/...",
  "transferAlias": "micuota.paula",
  "transferBankName": "Banco Demo"
}
```

## Experiencia web

El frontend usa:

```text
/pago.html?operationId={id}
```

## Recomendaciones

- Mostrar estado claro.
- Mantener boton de pago visible en mobile.
- Si el estado es `SUCCESS`, evitar reabrir checkout.
- Si el estado es `FAILURE`, permitir retomar o solicitar nuevo link.
