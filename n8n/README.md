# n8n SaaS Automation Pack (MiCuota)

Este paquete implementa una arquitectura modular de automatizacion en n8n para convertir MiCuota en una plataforma SaaS orientada a ventas, suscripciones y retencion.

## Estructura

- `workflows/00_orchestrator_master.json`
- `workflows/01_lead_capture.json`
- `workflows/02_lead_qualification_enrichment.json`
- `workflows/03_nurture_followup.json`
- `workflows/04_opportunity_offer_creation.json`
- `workflows/05_payment_reconciliation.json`
- `workflows/06_service_activation.json`
- `workflows/07_onboarding_first_success.json`
- `workflows/08_subscription_renewals.json`
- `workflows/09_retention_expansion.json`
- `workflows/10_ops_observability_error_control.json`

## Levantar n8n con Docker

Desde la raiz del proyecto:

```bash
docker compose up -d n8n
```

Acceso local:

- URL: `http://localhost:5678`
- Usuario: `admin`
- Password: `admin123`

Puedes cambiar credenciales y webhook URL usando variables del `.env.prod` (ver `.env.prod.example`).

## SMTP fake para pruebas (Mailpit)

El `docker-compose.yml` ya deja n8n configurado por defecto con SMTP fake:

- `N8N_EMAIL_MODE=smtp`
- `N8N_SMTP_HOST=mailpit`
- `N8N_SMTP_PORT=1025`
- `N8N_SMTP_SSL=false`

Con esto, cualquier envio de mail desde nodos de n8n se enruta a Mailpit.

Para ver los correos capturados:

- UI Mailpit: `http://localhost:8025`

Si cambiaste configuracion, reinicia n8n:

```bash
docker compose up -d n8n
```

## Como importar

1. Abrir n8n.
2. Ir a `Workflows > Import from File`.
3. Importar primero `00_orchestrator_master.json`.
4. Importar el resto de workflows del `01` al `10`.
5. Configurar credenciales en nodos `HTTP Request`, `Email`, `Slack` y `WhatsApp` (si aplica).
6. Activar workflows en orden de dependencia.

## Variables recomendadas

Configurar como variables de entorno en n8n:

- `MICUOTA_API_BASE_URL` (ej: `http://localhost:8080`)
- `MICUOTA_INTERNAL_TOKEN` (token de servicio interno o JWT tecnico)
- `CRM_BASE_URL` (para este repo: `http://localhost:8080/api/crm`)
- `CRM_API_KEY` (debe matchear `APP_CRM_API_KEY` del backend)
- `SLACK_WEBHOOK_URL`
- `N8N_PUBLIC_WEBHOOK_URL`

## Mapeo con APIs actuales de MiCuota

- Registro/login: `/api/auth/register-tenant`, `/api/auth/login`
- Backoffice users/courses/enrollments: `/api/backoffice/*`
- Cobros one-time/subscription: `/api/backoffice/payments/*`
- Callback de estados: `/api/callbacks/*`
- Vista publica de pago: `/api/public/payments/{operationId}`
- CRM MVP para leads: `/api/crm/leads`, `/api/crm/leads/search`, `/api/crm/leads/assign`

## Notas de diseno

- Cada workflow es modular y se puede ejecutar de forma independiente.
- El orquestador usa `Execute Workflow` para componer el embudo completo.
- El workflow `10` centraliza errores con `Error Trigger`, alertas y retry policy base.
