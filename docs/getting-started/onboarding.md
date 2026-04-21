# Onboarding

El onboarding de MiCuota busca que un profesional llegue a su primer cobro real lo antes posible.

## Registro de tenant

Endpoint:

```http
POST /api/auth/register-tenant
```

Payload orientativo:

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

El registro crea:

- Tenant.
- Usuario administrador.
- Perfil profesional.
- Token de sesion.

## Login

```http
POST /api/auth/login
```

```json
{
  "tenantSlug": "academia-norte",
  "email": "admin@example.com",
  "password": "secret"
}
```

Respuesta esperada:

```json
{
  "token": "eyJ...",
  "role": "TENANT_ADMIN",
  "backofficeUrl": "/backoffice.html"
}
```

## Checklist de activacion

1. Crear 1 alumno real.
2. Crear 1 curso o grupo.
3. Asociar alumno al curso.
4. Crear 1 cobro.
5. Compartir link.
6. Confirmar primer pago.

!!! tip "Promesa de producto"
    El objetivo operacional es que un profesional quede cobrando mejor que por WhatsApp en menos de 15 minutos.
