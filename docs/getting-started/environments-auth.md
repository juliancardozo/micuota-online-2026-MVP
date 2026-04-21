# Ambientes y autenticacion

## Ambientes

| Ambiente | Backend | Frontend |
| --- | --- | --- |
| Local backend | `http://localhost:8080` | Manual o Netlify local |
| Docker local | `http://localhost:8080` | `http://localhost:5500` |
| Produccion | Dominio configurado | Dominio configurado |

## Token de autenticacion

MiCuota no usa Spring Security. La autenticacion se maneja con tokens HMAC propios.

Los endpoints privados esperan:

```http
X-Auth-Token: <token>
```

Ejemplo:

```bash
curl http://localhost:8080/api/backoffice/payments \
  -H "X-Auth-Token: $MICUOTA_TOKEN"
```

## Roles

| Rol | Alcance |
| --- | --- |
| `TENANT_ADMIN` | Administra usuarios, cursos, pagos y metricas del tenant. |
| `TEACHER` | Gestiona alumnos, cursos y cobros del profesional. |
| `STUDENT` | Consulta su dashboard y pagos asignados. |

## CORS

La configuracion vive en:

```text
app.cors.allowed-origin-patterns
```

Valor local recomendado:

```properties
app.cors.allowed-origin-patterns=http://localhost:*,http://127.0.0.1:*,https://*.netlify.app
```

## Variables clave

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/micuota
export DB_USERNAME=micuota
export DB_PASSWORD=micuota
export MERCADOPAGO_ACCESS_TOKEN=APP_USR...
export MERCADOPAGO_WEBHOOK_SECRET=...
```
