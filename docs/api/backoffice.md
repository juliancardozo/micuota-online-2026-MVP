# API de backoffice

Todos los endpoints requieren:

```http
X-Auth-Token: <token>
```

## Usuarios

```http
GET /api/backoffice/users
POST /api/backoffice/users
```

Filtro:

```http
GET /api/backoffice/users?role=STUDENT
```

## Cursos

```http
GET /api/backoffice/courses
POST /api/backoffice/courses
```

## Inscripciones

```http
GET /api/backoffice/enrollments
POST /api/backoffice/enrollments
```

## Pagos

```http
GET /api/backoffice/payments
GET /api/backoffice/payments/by-course?courseId=7
POST /api/backoffice/payments/one-time
POST /api/backoffice/payments/subscriptions
GET /api/backoffice/payments/{operationId}/events
```

## Salud de cobranzas

```http
GET /api/backoffice/payments/health
```

## KPI framework

```http
GET /api/backoffice/payments/kpi-framework
```

Requiere `TENANT_ADMIN`.

## Configuracion de cobro manual

```http
GET /api/backoffice/payment-settings
POST /api/backoffice/payment-settings
```

## Dashboards

```http
GET /api/backoffice/dashboard/professor
GET /api/backoffice/dashboard/student
```
