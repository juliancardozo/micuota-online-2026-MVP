# Deploy

## Backend

Variables minimas para produccion:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://<host>:5432/<database>
export DB_USERNAME=<user>
export DB_PASSWORD=<password>
export MAIL_HOST=<smtp-host>
export MAIL_PORT=587
export MAIL_USERNAME=<smtp-user>
export MAIL_PASSWORD=<smtp-password>
export MERCADOPAGO_ACCESS_TOKEN=<token>
export MERCADOPAGO_WEBHOOK_SECRET=<secret>
```

## Frontend

El frontend es estatico y puede publicarse en Netlify.

Archivos principales:

- `landing.html`
- `backoffice.html`
- `profesor.html`
- `alumno.html`
- `pago.html`

## Base de datos

En perfil `prod`:

- Flyway activo.
- JPA en modo `validate`.
- PostgreSQL recomendado.

## Migraciones

```text
backend/src/main/resources/db/migration
```

## Checklist predeploy

- `mvn test`
- migraciones revisadas
- variables de Mercado Pago configuradas
- webhook publico registrado en Mercado Pago
- CORS con dominio final
- Mailpit reemplazado por SMTP real
