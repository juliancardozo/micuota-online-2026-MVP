# API de autenticacion

## Entrar o crear cuenta con codigo por email (self-service)

Es el flujo que usa `cuenta.html`. No hay contrasena: un codigo de 6 digitos enviado por email prueba que la persona es duena del email, y la cuenta se crea recien al verificarlo.

```http
POST /api/auth/email-code
{ "email": "ana@gmail.com" }
-> { "sent": true }
```

- El codigo vence en 10 minutos y admite 5 intentos.
- Limites: 1 pedido cada 30 s por email, 5 por hora por email y 20 por hora por IP.
- Si falla el envio (SMTP/Resend) responde 400 y el codigo no queda guardado.

```http
POST /api/auth/email-code/verify
{ "email": "ana@gmail.com", "code": "123456", "fullName": "Ana Perez", "phone": null, "activity": "CLASES", "tenantSlug": null }
```

`status` de la respuesta:

- `SIGNED_IN`: `session` trae el mismo `AuthResponse` que `/login`; `created: true` si la cuenta se acaba de crear (tenant propio con usuario `TEACHER`).
- `NEEDS_NAME`: el email no tiene cuenta; reenviar con `fullName` y el mismo codigo.
- `CHOOSE_ACCOUNT`: el email tiene varias cuentas (`accounts`); reenviar con `tenantSlug` y el mismo codigo.

En local (perfil `dev`) el codigo tambien se escribe en el log (`app.auth.log-login-codes`).

## Registrar tenant

```http
POST /api/auth/register-tenant
```

Uso: restringido. El alta self-service es por codigo por email (ver arriba); este endpoint con contrasena sigue cerrado.

Respuesta esperada para usuarios publicos:

```json
{
  "error": "El alta de tenants esta restringida al administrador de plataforma"
}
```

## Crear tenant desde plataforma

```http
POST /api/admin/tenants
X-Auth-Token: <platform-admin-token>
```

Requiere rol `ADMIN`.

Payload:

```json
{
  "tenantName": "Academia Norte",
  "tenantSlug": "academia-norte",
  "adminFullName": "Paula Gomez",
  "adminEmail": "admin@example.com",
  "adminPassword": "secret123"
}
```

## Login

```http
POST /api/auth/login
```

Devuelve token de sesion.

## Usuario actual

```http
GET /api/auth/me
X-Auth-Token: <token>
```

## Heartbeat

```http
POST /api/auth/heartbeat
X-Auth-Token: <token>
```

Actualiza actividad de sesion.

## Logout

```http
POST /api/auth/logout
X-Auth-Token: <token>
```

Finaliza sesion.
