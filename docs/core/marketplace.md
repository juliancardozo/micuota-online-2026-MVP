# Marketplace y comisiones

MiCuota cobra su comision con el modelo marketplace de Mercado Pago: cada profesional conecta su cuenta
a la aplicacion de MiCuota por OAuth y cada cobro lleva un `marketplace_fee`. Mercado Pago acredita el pago
en la cuenta del profesional y transfiere la comision a la cuenta duena de la aplicacion.

## Configuracion

| Variable | Uso |
|---|---|
| `MERCADOPAGO_CLIENT_ID` | App ID de la aplicacion de MiCuota en Mercado Pago |
| `MERCADOPAGO_CLIENT_SECRET` | Client secret de esa aplicacion |
| `MERCADOPAGO_OAUTH_REDIRECT_URI` | Opcional. Por defecto `${APP_BASE_URL}/api/mercadopago/oauth/callback`; tiene que coincidir exacto con la Redirect URL cargada en la aplicacion |
| `APP_FRONTEND_BASE_URL` | Adonde vuelve el profesional despues de autorizar (`/profesor.html?mp=...#mercadopago`) |

Sin `MERCADOPAGO_CLIENT_ID` y `MERCADOPAGO_CLIENT_SECRET` el OAuth queda apagado y los profesionales siguen
pegando su Access Token a mano (sin comision). Con el OAuth activo ya no se aceptan tokens pegados.

## Flujo

1. `GET /api/mercadopago/oauth/authorize` (rol `TEACHER`) devuelve la URL de autorizacion con un `state` firmado que vence en 15 minutos.
2. Mercado Pago redirige a `GET /api/mercadopago/oauth/callback?code&state`; el backend canjea el codigo en `POST /oauth/token` y guarda access token, refresh token, `user_id` y vencimiento.
3. Un job diario (`app.payments.mercadopago.oauth.refresh-cron`) renueva los tokens que vencen en menos de 30 dias (duran 180).
4. `POST /api/mercadopago/oauth/disconnect` borra la conexion.

## Calculo de la comision

- `takeRateBps` del tenant (por defecto 350 = 3,5 %) sobre el monto del cobro.
- Mas `advancedDunningFeeBps` (por defecto 120 = 1,2 %) si el tenant tiene la recuperacion automatica activa.
- Se envia como `marketplace_fee` solo en pagos unicos (`/checkout/preferences`) de profesionales conectados por OAuth.
  Las suscripciones (`/preapproval`) no admiten `marketplace_fee`.
- Lo pedido queda en `payment_operations.marketplace_fee_amount`.

El administrador ve y edita las tasas por tenant en `admin.html#cuentas`
(`GET /api/admin/tenants`, `PUT /api/admin/tenants/{tenantId}/billing`). Los cambios aplican a los cobros nuevos.

## Administrador de la plataforma

Hay un unico usuario `ADMIN`, en su propio tenant de plataforma (`micuota`, excluido de metricas y de la limpieza), creado al arrancar por `PlatformAdminSeeder` con `APP_PLATFORM_ADMIN_EMAIL`
(por defecto `admin@micuota.online`) y `APP_PLATFORM_ADMIN_PASSWORD`. Cualquier otro `ADMIN` pierde el rol.
Entra solo por `POST /api/auth/admin-login` (formulario de `admin.html`), con un limite de 5 intentos fallidos
por IP cada 15 minutos; no puede usar el login por tenant ni el codigo por email.
