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

## Pagos por fuera de Mercado Pago

Cada profesional elige en la app (**Cómo te pagan**) una o varias formas: Mercado Pago, transferencia o depósito
(banco, cuenta, titular, alias) y texto libre para efectivo, giro u otras. La página de pago (`pago.html`) muestra
todas las que tenga cargadas.

- Sin Mercado Pago conectado, el cobro se crea con `provider = MANUAL` (`ManualPaymentGateway`): no hay checkout
  externo, el link es la página pública del cobro y no lleva comisión. Los cobros mensuales automáticos necesitan
  Mercado Pago.
- El profesional concilia con `POST /api/backoffice/payments/{id}/mark-paid` (`{method: TRANSFER|CASH|GIRO|OTHER, note}`),
  que deja el cobro `SUCCESS` con `paid_method`, `paid_at` y `paid_note`, y registra el evento en el historial.
  `POST .../mark-unpaid` lo deshace. Los pagos por Mercado Pago se marcan solos (`paid_method = MERCADOPAGO`).
- Si un cobro de Mercado Pago se marca pagado a mano, la comisión no cuenta como cobrada.
- Los recordatorios por email siguen funcionando para los cobros manuales y llevan a la página de pago.

## Cobros mensuales sin Mercado Pago

"Todos los meses" funciona con cualquier medio. Con Mercado Pago es su suscripción (preapproval). Sin Mercado Pago,
`POST /api/backoffice/payments/subscriptions` con `provider = MANUAL` crea el cobro de este mes y una plantilla en
`recurring_charges` (día del mes, máximo 28). `RecurringChargeService` corre cada hora
(`app.payments.recurring.cron`), genera el cobro del mes cuando corresponde (con Mercado Pago si el profesional ya lo
conectó) y el cliente lo recibe por email. `GET /api/backoffice/recurring-charges` los lista y
`POST .../recurring-charges/{id}/stop` deja de generarlos.

## Pago desde el banco del cliente (Prometeo)

MiCuota actúa de proxy de la Banking API de Prometeo (`PrometeoBankingClient`, `BankTransferPaymentService`):
el cliente elige su banco en la página de pago, entra con sus credenciales (que se reenvían a Prometeo sin
guardarse ni registrarse), elige su cuenta y confirma una transferencia a la cuenta del profesional por el monto
del cobro. Al confirmarse, el cobro queda pagado solo (`paid_method = TRANSFER`, sin comisión).

Endpoints públicos bajo `/api/public/payments/{id}/bank`: `GET` (disponibilidad y bancos), `GET /providers/{code}/fields`,
`POST /login`, `/challenge`, `/client`, `/transfer`, `/confirm`, `/cancel`. La sesión con el banco vive en memoria
como máximo 5 minutos (lo que dura la clave de sesión en Prometeo) y se cierra al terminar; hay hasta 5 intentos de login por cobro cada 15 minutos.

Requisitos: el profesional cargó banco, número de cuenta y titular; el cobro es en UYU o USD y no es una suscripción
de Mercado Pago.

| Variable | Uso |
|---|---|
| `APP_PROMETEO_API_KEY` | API key de Prometeo |
| `APP_PROMETEO_BANK_PAYMENTS_ENABLED` | `true` para mostrar "Pagar desde tu banco" (por defecto apagado) |
| `APP_PROMETEO_SANDBOX` | `true` en sandbox: aparece el banco de prueba y el destino es `APP_PROMETEO_SANDBOX_DESTINATION_INSTITUTION` |
| `APP_PROMETEO_SANDBOX_SIMULATE_CONFIRM` | Solo pruebas. El sandbox de Prometeo devuelve 500 en `/transfer/confirm`; con `true` (y solo en sandbox) ese error se toma como pago simulado y la nota del cobro dice `SIMULADO-SANDBOX` |
| `APP_PROMETEO_BANKING_BASE_URL` | `https://banking.sandbox.prometeoapi.com` o el host de producción que indique Prometeo |

En producción el banco destino se busca por nombre en la lista de destinos del banco del cliente.

## Modo prueba e interruptores por etapa

La plataforma arranca en **modo prueba** (`platform_settings.payments.mode = TEST`) y el administrador la cambia
desde `admin.html#mantenimiento` sin redesplegar (`GET/PUT /api/admin/platform-settings`; pasar a real exige
`confirm = "REAL"`). En modo prueba ningún cobro mueve plata real:

- **Mercado Pago**: `MercadoPagoAccountGuard` consulta `/users/me` del token y solo deja crear cobros con cuentas de
  prueba (`TESTUSER…`, tag `test_user`, o tokens `TEST-`). Con una cuenta real el cobro se rechaza con un mensaje claro.
- **Prometeo**: el pago desde el banco solo funciona si `APP_PROMETEO_SANDBOX=true`.
- La página de pago y la app muestran un aviso de "Modo prueba" (`GET /api/public/platform`).

Interruptores (además de las variables de entorno, que siguen siendo el límite):

| Interruptor | Por defecto | Efecto |
|---|---|---|
| Pagar desde el banco (Prometeo) | apagado | Muestra u oculta "Pagar desde tu banco" |
| Conectar Mercado Pago con un clic (OAuth) | prendido | Apagado, el profesional pega su Access Token (de prueba) |
