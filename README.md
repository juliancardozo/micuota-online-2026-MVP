# MiCuota.online - MVP SaaS (Frontend + Backend)

Repositorio con un MVP real orientado a SaaS:

- Frontend estatico deployable en Netlify
- Backend Spring Boot con API REST
- Persistencia de operaciones en H2
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

Teacher demo seed:

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
