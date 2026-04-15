# MiCuota.online - MVP SaaS (Frontend + Backend)

Repositorio con un MVP real orientado a SaaS:

- Frontend estatico deployable en Netlify
- Backend Spring Boot con API REST
- Persistencia por perfiles: H2 (dev) y PostgreSQL (prod)
- Callbacks `success/pending/failure`
- Capa abstracta de proveedores: MercadoPago, Prometeo y WooCommerce

## Arquitectura

### Frontend

- `index.html`: dashboard simple de profesor
- `landing.html`: landing comercial con autoregistro/login por tenant
- `backoffice.html`: app backoffice para gestionar alumnos/pacientes, cursos y cobros
- `pago.html`: experiencia de pago para cliente final (alumno/paciente)
- `styles.css`: UI responsive
- `app.js`: consumo de API backend
- `netlify.toml`: configuracion para Netlify

### Backend

Proyecto Maven en `backend/` con:

- `PaymentProviderGateway` (estrategia abstracta por proveedor)
- Implementaciones:
	- `MercadoPagoGateway`
	- `PrometeoGateway`
	- `WooCommerceGateway`
- `PaymentService` para orquestar flujos `ONE_TIME` y `SUBSCRIPTION`
- `PaymentController` para crear operaciones
- `CallbackController` para actualizar estados
- Entidades principales:
	- `User` (base)
	- `TeacherProfile` (composicion 1:1 con credenciales provider)
	- `PaymentOperation`

## Ejecutar local

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

## Swagger y OpenAPI

Con el backend levantado, la documentacion interactiva queda disponible en:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Autorizacion en Swagger

El esquema privado usa header `X-Auth-Token`.

Flujo recomendado:

1. Ejecutar `POST /api/auth/login` (o `POST /api/auth/register-tenant`).
2. Copiar el campo `token` de la respuesta.
3. En Swagger UI, boton `Authorize`, pegar el token en `AuthToken`.
4. Ejecutar endpoints privados (`/api/backoffice/*` y `/api/auth/me`).

Notas:

- Endpoints publicos como `/api/public/payments/*` y callbacks no requieren token.
- Existen endpoints legacy en `/api/payments/*` sin `X-Auth-Token`, documentados para compatibilidad MVP.

## API multi-tenant y backoffice

- `POST /api/auth/register-tenant`: crea tenant + usuario admin y devuelve token + URL de backoffice.
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
