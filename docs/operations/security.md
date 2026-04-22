# Seguridad

## Autenticacion

MiCuota usa tokens HMAC propios en:

```http
X-Auth-Token: <token>
```

No hay Spring Security configurado actualmente.

## Webhooks

Mercado Pago se valida con:

- `x-signature`
- `x-request-id`
- `data.id`
- secreto `MERCADOPAGO_WEBHOOK_SECRET`

## CORS

Configuracion:

```properties
app.cors.allowed-origin-patterns=...
```

## Datos sensibles

No commitear:

- tokens Mercado Pago
- public keys Mercado Pago reales si identifican una integracion productiva
- secrets webhook
- SMTP credentials
- DB passwords reales

Los tokens de Mercado Pago se guardan a nivel `TeacherProfile`. Solo usuarios `TEACHER` pueden conectarlos desde el backoffice; un `TENANT_ADMIN` puede operar y supervisar, pero no debe centralizar credenciales de vendedores.

## Mejoras recomendadas

- Centralizar auth con filtro HTTP.
- Agregar rate limiting en endpoints publicos.
- Idempotencia explicita para webhooks.
- Rotacion de tokens.
- Auditoria de accesos administrativos.
- Separar secrets por tenant/proveedor.
