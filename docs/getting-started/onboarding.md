# Onboarding

El onboarding de MiCuota busca que un profesional llegue a su primer cobro real lo antes posible.

## Alta de tenant

Endpoint:

```http
POST /api/admin/tenants
X-Auth-Token: <platform-admin-token>
```

Payload orientativo:

```json
{
  "tenantName": "Academia Norte",
  "tenantSlug": "academia-norte",
  "adminFullName": "Paula Gomez",
  "adminEmail": "admin@example.com",
  "adminPassword": "secret123"
}
```

El administrador de plataforma crea:

- Tenant.
- Usuario `TENANT_ADMIN` inicial.
- Perfil profesional base para operar el tenant.
- URL de setup para entregar al administrador del tenant.

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
