# API de autenticacion

## Registrar tenant

```http
POST /api/auth/register-tenant
```

Uso: alta inicial de organizacion/profesional.

Payload:

```json
{
  "tenantName": "Academia Norte",
  "tenantSlug": "academia-norte",
  "fullName": "Paula Gomez",
  "email": "admin@example.com",
  "password": "secret",
  "mpAccessToken": "APP_USR..."
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
