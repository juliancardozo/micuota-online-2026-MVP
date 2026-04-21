# Errores y testing

## Formato de error

Los errores de negocio devuelven:

```json
{
  "timestamp": "2026-04-21T10:00:00-03:00",
  "error": "Operacion no encontrada"
}
```

## Errores frecuentes

| Caso | Causa probable | Accion |
| --- | --- | --- |
| Token invalido | `X-Auth-Token` ausente o expirado | Rehacer login. |
| Proveedor sin credenciales | Falta token del PSP | Configurar token del profesor o variable de entorno. |
| Webhook sin firma valida | `MERCADOPAGO_WEBHOOK_SECRET` no coincide | Verificar secreto en Mercado Pago. |
| Operacion no encontrada | `providerReference` desconocido | Revisar `PaymentOperation.providerReference`. |

## Tests backend

```bash
cd backend
mvn test
```

La suite cubre:

- Construccion de preferences y preapproval de Mercado Pago.
- Validacion de webhook firmado.
- Registro y mapeo de eventos de pago.

## Smoke tests

```bash
./tests/run_all.sh
```

## Postman

Colecciones disponibles:

```text
postman/MiCuota_Onboarding.postman_collection.json
postman/MiCuota_Local.postman_environment.json
```
