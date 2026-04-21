# Notificaciones

MiCuota usa notificaciones para cerrar el ciclo de cobro: enviar links, recordar pagos y alertar descalces.

## Email de cobro

Cuando se crea un pago con `payerEmail`, MiCuota envia un email con:

- concepto
- monto
- link de pago
- QR

El evento queda registrado como:

```text
PAYMENT_LINK_SENT
```

## Recordatorios

El servicio de recupero puede enviar recordatorios por email para pagos:

- creados y no completados
- pendientes
- fallidos
- vencidos

## Alertas operativas

Si hay descalces de conciliacion, MiCuota puede enviar alerta a:

```properties
app.mail.ops-alert
```

## Canales futuros

- WhatsApp
- SMS
- Web push
- Integracion n8n
- Templates por vertical

## Principio

Cada comunicacion relevante debe producir un evento auditable para medir conversion y recupero.
