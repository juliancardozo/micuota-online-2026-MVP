# API de autenticacion

## Registrar tenant

```http
POST /api/auth/register-tenant
```

Uso: restringido. Para el MVP, el alta de tenants no es self-service.

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
