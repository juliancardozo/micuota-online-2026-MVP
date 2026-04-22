# MiCuota.online - MVP SaaS (Frontend + Backend)

Repositorio con un MVP real orientado a SaaS:

- Frontend estatico deployable en Netlify
- Backend Spring Boot con API REST
- Persistencia por perfiles: H2 (dev) y PostgreSQL (prod)
- Callbacks `success/pending/failure`
- Capa abstracta de proveedores: MercadoPago, Prometeo, WooCommerce, Stripe, Square y Plexo

## Arquitectura

### Frontend

- `landing.html`: landing comercial con autoregistro/login por tenant
- `backoffice.html`: app backoffice para gestionar alumnos/pacientes, cursos y cobros
- `profesor.html`: workspace del profesor para grupos, personas, pendientes y cobros
- `alumno.html`: workspace del alumno para cursos, pagos y suscripciones
- `pago.html`: experiencia de pago para cliente final (alumno/paciente)
- `netlify.toml`: configuracion para Netlify

### Backend

Proyecto Maven en `backend/` con:

- `PaymentProviderGateway` (estrategia abstracta por proveedor)
- Implementaciones:
	- `MercadoPagoGateway`
	- `PrometeoGateway`
	- `WooCommerceGateway`
	- `StripeGateway`
	- `SquareGateway`
	- `PlexoGateway`
- `PaymentService` para orquestar flujos `ONE_TIME` y `SUBSCRIPTION`
- `PaymentController` para crear operaciones
- `CallbackController` para actualizar estados
- Entidades principales:
	- `User` (base)
	- `TeacherProfile` (composicion 1:1 con credenciales provider)
	- `PaymentOperation`

### Integracion real de links (Mercado Pago, Stripe, Square, Plexo)

Para Stripe, Square y Plexo el backend soporta dos modos:

1. Modo sandbox interno (default): genera links locales `/sandbox/...` para pruebas.
2. Modo externo real: crea links en APIs de Stripe/Square/Plexo segun variables de entorno.

Mercado Pago usa la API real cuando el profesor conecta su propio Access Token desde el perfil. No hay fallback global de entorno para crear cobros, porque cada link o suscripcion debe quedar asociado al vendedor real.

Variables relevantes:

- `APP_PAYMENTS_EXTERNAL_PROVIDERS_ENABLED=true`
- `MERCADOPAGO_WEBHOOK_SECRET=...`
- `APP_PAYMENTS_STRIPE_SECRET_KEY=...`
- `APP_PAYMENTS_SQUARE_ACCESS_TOKEN=...`
- `APP_PAYMENTS_SQUARE_LOCATION_ID=...`
- `APP_PAYMENTS_SQUARE_USE_SANDBOX=true|false`
- `APP_PAYMENTS_PLEXO_API_URL=...`
- `APP_PAYMENTS_PLEXO_API_KEY=...`

Mercado Pago usa `POST /checkout/preferences` para pagos puntuales y `POST /preapproval` para suscripciones. El backend guarda el `id` devuelto por Mercado Pago como `providerReference`, publica el webhook en `/api/callbacks/mercadopago?externalReference=...` y mantiene la respuesta completa de la API y las notificaciones en `PaymentOperation.rawResponse`.

Para validar webhooks, configura `MERCADOPAGO_WEBHOOK_SECRET` con el secreto de la app de Mercado Pago. Si el webhook trae solo `data.id`, el backend usa el `externalReference` de la URL para encontrar la operacion, obtiene el token del profesor y consulta `/v1/payments/{id}` para resolver `status`, `external_reference` y `preference_id`.

### Ledger minimo de pagos

Cada cobro genera eventos auditables en `payment_events`: creacion de pago, envio de link por email, webhook recibido y cambios de estado. El backoffice puede consultar el timeline con:

- `GET /api/backoffice/payments/{operationId}/events`

Este ledger es la base para conciliacion, reportes de mora, recupero, auditoria y futuras capas como split/payouts.

Nota: Plexo se deja con endpoint configurable (`.../links`) porque la URL final depende de la cuenta/entorno provisto por Plexo.

Referencias oficiales de producto (links de pago):

- Mercado Pago Checkout Pro preferences: https://www.mercadopago.com.uy/developers/en/docs/checkout-pro/checkout-customization/preferences
- Mercado Pago Webhooks: https://www.mercadopago.com.uy/developers/en/docs/checkout-pro/additional-content/notifications/webhooks
- Stripe Payment Links: https://stripe.com/payments/payment-links
	- Crea y comparte un enlace de pago sin necesidad de sitio web.
- Square Payment Links: https://squareup.com/payment-links
	- Configuracion rapida y flexible para generar links de cobro en segundos.
- Plexo Links (UY): https://www.plexo.com.uy/productos/plexo-links
	- Cobranzas para compartir por WhatsApp, email o redes sociales con un link.

## Ejecutar local

### Documentacion

La documentacion navegable vive en `docs/` y se genera con MkDocs Material.

Con Docker:

```bash
docker compose --profile docs up docs
```

Luego abrir:

```text
http://localhost:8000
```

Build estricto:

```bash
docker compose --profile docs run --rm docs mkdocs build --strict
```

### 1) Levantar backend

```bash
cd backend
mvn spring-boot:run
```

Backend disponible en `http://localhost:8080`.

Por defecto arranca perfil `dev` (H2 local, seed demo activo).

### 1.1) Preparar backend para MVP productivo (PostgreSQL)

#### Opcion recomendada local: Docker Compose

En la raiz del proyecto:

```bash
docker compose up -d
```

Esto levanta PostgreSQL en `localhost:5432` y Mailpit para correos en `http://localhost:8025`.

Tambien quedan disponibles clientes de administracion:

- Adminer (cliente SQL): `http://localhost:8081`
- Metabase (dashboards y metricas): `http://localhost:3001`
- Prometheus (scrape / queries): `http://localhost:9090`
- Grafana (observabilidad SaaS): `http://localhost:3002`

PostgreSQL:

- DB: `micuota`
- User: `micuota`
- Password: `micuota`

Mailpit (sin credenciales para desarrollo):

- SMTP: `localhost:1025`
- Inbox web: `http://localhost:8025`

Metabase (dashboard admin del sistema):

- URL: `http://localhost:3001`
- Primera vez: crear usuario admin de Metabase (setup inicial)
- Agregar fuente de datos PostgreSQL con:
	- Host: `postgres`
	- Port: `5432`
	- Database: `micuota`
	- Username: `micuota`
	- Password: `micuota`

Grafana:

- URL: `http://localhost:3002`
- User: `admin`
- Password: `admin123`
- Datasource Prometheus provisionado automaticamente
- Dashboard inicial provisionado: `MiCuota SaaS Overview`

Con eso puedes construir dashboards globales para todas las entidades (usuarios, cursos, pagos, enrollments, tenants).

Para apagarlo:

```bash
docker compose down
```

Para apagarlo y borrar datos:

```bash
docker compose down -v
```

#### Variables de entorno para perfil `prod`

Puedes usar el template:

```bash
cp .env.prod.example .env.prod
source .env.prod
```

Configurar variables de entorno:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://<host>:5432/<database>
export DB_USERNAME=<usuario>
export DB_PASSWORD=<password>
```

Luego levantar:

```bash
cd backend
mvn spring-boot:run
```

Con perfil `prod` se ejecuta Flyway (`db/migration/V1__initial_schema.sql`) y se valida esquema JPA.

### 1.2) Correos temporales para MVP (camino facil)

Recomendado para desarrollo: usar Mailpit local.

1. Levanta servicios con `docker compose up -d`.
2. Crea un pago desde backoffice (`payerEmail` ahora es requerido).
3. Abre `http://localhost:8025` para ver el email generado con template HTML, QR y link de pago.

Esto evita credenciales SMTP reales durante el MVP.

### 1.3) Dashboard de sistema (Metabase)

Con `docker compose up -d` tambien se levanta Metabase para administracion y metricas de negocio.

Sugerencia de tablero inicial (MVP):

1. Total de tenants
2. Usuarios por rol (`ADMIN`, `TEACHER`, `STUDENT`)
3. Cursos activos por tenant
4. Operaciones de pago por estado (`CREATED`, `PENDING`, `SUCCESS`, `FAILURE`)
5. Monto total cobrado por mes
6. Conversion de cobros (`SUCCESS / total`)

Puedes crear preguntas en Metabase con query builder o SQL nativo y agrupar por `created_at` para tendencia temporal.

Pack listo para usar (consultas SQL + guia de armado):

- [analytics/metabase_global_dashboards.sql](analytics/metabase_global_dashboards.sql)
- [analytics/metabase_dashboard_setup.md](analytics/metabase_dashboard_setup.md)

Pack especifico de 3 dashboards (Tenants, Profesores/Alumnos y Pagos):

- [analytics/metabase_3_dashboards_tenants_profesores_alumnos_pagos.sql](analytics/metabase_3_dashboards_tenants_profesores_alumnos_pagos.sql)
- [analytics/metabase_3_dashboards_setup.md](analytics/metabase_3_dashboards_setup.md)

Guia para charts interactivos y drill-through en Metabase:

- [analytics/metabase_interactive_charts_drillthrough.md](analytics/metabase_interactive_charts_drillthrough.md)

### 1.4) Observabilidad del SaaS (Grafana + Prometheus)

El backend expone metricas en:

- `http://localhost:8080/actuator/prometheus`

Metricas utiles incluidas:

- `micuota_saas_tenants_total`
- `micuota_saas_users_total`
- `micuota_saas_courses_total`
- `micuota_saas_leads_total`
- `micuota_saas_active_sessions`
- `micuota_saas_revenue_success`
- `micuota_payments_created_total`
- `micuota_payment_status_changes_total`
- `micuota_leads_captured_total`
- `micuota_sessions_started_total`
- `micuota_sessions_ended_total`

Ademas quedan disponibles metricas tecnicas de Spring Boot / JVM / HTTP gracias a Actuator + Micrometer.

### 2) Levantar frontend

En la raiz del repo:

```bash
npx serve .
```

Si tu version de Node no soporta `npx serve`, usa:

```bash
python3 -m http.server 3000
```

Abrir la URL de `serve` (por ejemplo `http://localhost:3000`).

### 2.1) Quickstart: Modular Embedding SDK (React)

Se agrego una mini app React de ejemplo en `embedding-sdk-demo/` para embeber un dashboard de Metabase via API key (solo evaluacion local).

Prerequisitos:

1. Metabase v52+ (en este entorno esta corriendo `v0.60.1`).
2. En Metabase, habilitar: Admin > Embedding > Modular > React.
3. Crear API key en: Admin > Settings > Authentication > API keys.
4. Node.js `>= 18` y npm `>= 8`.

Pasos:

```bash
cd embedding-sdk-demo
cp .env.example .env.local
```

Editar `.env.local` con tu API key:

```bash
VITE_METABASE_URL=http://localhost:3001
VITE_METABASE_API_KEY=<TU_API_KEY>
VITE_METABASE_DASHBOARD_ID_TENANTS=1
VITE_METABASE_DASHBOARD_ID_PROFESORES_ALUMNOS=2
VITE_METABASE_DASHBOARD_ID_PAGOS=3
```

Instalar dependencias y ejecutar:

```bash
npm install
npm run dev
```

Abrir la URL que muestra Vite (usualmente `http://localhost:5173`).

Vista por una sola URL base con selector por query param:

- `http://localhost:5173/?view=tenants`
- `http://localhost:5173/?view=profesores-alumnos`
- `http://localhost:5173/?view=pagos`

Notas:

- El ejemplo usa `@metabase/embedding-sdk-react@60-beta` para coincidir con Metabase `v0.60.x` (aun sin tag `60-stable` en npm).
- Esta configuracion es para localhost y pruebas. Para produccion, usar JWT SSO con plan Pro/Enterprise.

## API principal

- `POST /api/payments/one-time`
- `POST /api/payments/subscriptions`
- `GET /api/payments/teacher/{teacherId}`
- `GET /api/callbacks/success?operationId=...`
- `GET /api/callbacks/pending?operationId=...`
- `GET /api/callbacks/failure?operationId=...`

## Postman (onboarding guiado)

Se incluye una coleccion de Postman con flujo completo de onboarding:

- Registro de tenant y token admin.
- Alta de profesor y alumno.
- Creacion de curso e inscripcion.
- Creacion de pagos (one-time y suscripcion).
- Simulacion de callbacks por referencia.
- Login por rol y dashboards de profesor/alumno.

Archivos:

- `postman/MiCuota_Onboarding.postman_collection.json`
- `postman/MiCuota_Local.postman_environment.json`

Uso rapido:

1. Importar ambos archivos en Postman.
2. Seleccionar environment `MiCuota Local`.
3. Ejecutar requests en orden por carpeta (`00` a `06`).

La coleccion guarda automaticamente variables de salida (token, IDs, referencias) para encadenar cada paso del onboarding sin edicion manual.

## Branding visual landing

Prompt pack de imagenes para mantener consistencia visual SaaS (azul profundo, blanco y acento amarillo):

- [docs/landing-image-pack-prompts.md](docs/landing-image-pack-prompts.md)

## Swagger y OpenAPI

Con el backend levantado, la documentacion interactiva queda disponible en:

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Autorizacion en Swagger

El esquema privado usa header `X-Auth-Token`.

Flujo recomendado:

1. Ejecutar `POST /api/auth/login`.
2. Copiar el campo `token` de la respuesta.
3. En Swagger UI, boton `Authorize`, pegar el token en `AuthToken`.
4. Ejecutar endpoints privados (`/api/backoffice/*` y `/api/auth/me`).

Notas:

- Endpoints publicos como `/api/public/payments/*` y callbacks no requieren token.
- Existen endpoints legacy en `/api/payments/*` sin `X-Auth-Token`, documentados para compatibilidad MVP.

## API multi-tenant y backoffice

- `POST /api/auth/register-tenant`: restringido; el alta de tenants la hace plataforma.
- `POST /api/admin/tenants`: crea tenant + usuario `TENANT_ADMIN` inicial. Requiere `ADMIN`.
- `POST /api/admin/maintenance/purge-non-demo-tenants`: elimina tenants no demo para limpieza MVP. Requiere `ADMIN`.
- `POST /api/auth/login`: login por `tenantSlug + email + password`.
- `POST /api/backoffice/users` (header `X-Auth-Token`): crea perfil `TEACHER` o `STUDENT`.
- `GET /api/backoffice/users?role=TEACHER|STUDENT` (header `X-Auth-Token`): lista perfiles del tenant.
- `POST /api/backoffice/courses` (header `X-Auth-Token`): crea curso asociado a un profesor del tenant.
- `GET /api/backoffice/courses` (header `X-Auth-Token`): lista cursos del tenant.
- `POST /api/backoffice/payments/one-time` (header `X-Auth-Token`): crea pago unico desde el backoffice del profesional.
- `POST /api/backoffice/payments/subscriptions` (header `X-Auth-Token`): crea suscripcion desde backoffice.
- `GET /api/backoffice/payments` (header `X-Auth-Token`): lista operaciones recientes del profesional.

## API publica para experiencia de cliente

- `GET /api/public/payments/{operationId}`: devuelve detalle de cobro para renderizar `pago.html`.

Flujo UX MVP recomendado:

1. Profesional crea alumno/paciente en backoffice.
2. Profesional crea pago unico o suscripcion.
3. Backoffice genera link cliente: `pago.html?operationId=...`.
4. Alumno/paciente abre la pagina de pago y completa el checkout.

Flujo recomendado:

1. Abrir `landing.html`.
2. Registrar tenant o iniciar sesion.
3. Redireccion automatica a `backoffice.html?token=...`.
4. Crear perfiles de profesor/alumno y luego cursos.

Teacher demo seed (solo perfil `dev`):

- email: `teacher@micuota.online`
- id esperado: `1`
- credenciales demo para los 3 proveedores ya cargadas en seed

## Notas sobre SaaS y proveedores

El backend usa una capa abstracta para desacoplar el dominio de MiCuota de cada PSP/API.
Esto permite:

1. Mantener el mismo flujo de negocio para pagos unicos y suscripciones.
2. Cambiar o sumar proveedores sin romper controladores ni frontend.
3. Soportar esquema por profesor (credencial propia) y evolucionar a cuenta maestra.

## Deploy sugerido

- Frontend: Netlify
- Backend: Render/Railway/Fly.io o VM propia con Java

Si frontend y backend van en dominios distintos, ajustar CORS en:

- `backend/src/main/resources/application.properties`

## Flujo de ramas

- `develop`: desarrollo
- `test`: validacion
- `produccion`: releases
