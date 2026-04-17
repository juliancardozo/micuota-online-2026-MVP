# Pruebas Locales MiCuota

## Objetivo

Este paquete valida rapidamente la infraestructura y el flujo API principal.

## Scripts

- `tests/smoke_infra.sh`: verifica `docker compose ps`, readiness de PostgreSQL y respuesta HTTP de Mailpit, Adminer, n8n y Metabase.
- `tests/smoke_api_onboarding.py`: ejecuta un flujo end-to-end sobre el backend local.
- `tests/run_all.sh`: ejecuta ambas pruebas en orden.

## Ejecucion

Desde la raiz del repo:

```bash
./tests/run_all.sh
```

Opcional, para apuntar a otro backend:

```bash
./tests/run_all.sh --base-url http://localhost:8080
```

## Resultado esperado

- `smoke_infra.sh` termina con `[infra] Todo OK`.
- `smoke_api_onboarding.py` imprime `[api] Smoke OK` y un JSON con IDs y links creados.
