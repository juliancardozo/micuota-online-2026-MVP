# Observabilidad

## Actuator

Prometheus:

```text
http://localhost:8080/actuator/prometheus
```

Health:

```text
http://localhost:8080/actuator/health
```

## Prometheus

Local:

```text
http://localhost:9090
```

## Grafana

Local:

```text
http://localhost:3002
```

Credenciales por defecto:

```text
admin / admin123
```

## Metricas de negocio recomendadas

- pagos creados
- pagos exitosos
- pagos fallidos
- tasa de exito
- pagos vencidos
- recupero de mora
- tiempo a primer pago exitoso
- webhooks recibidos
- descalces de conciliacion

## Eventos como fuente de analitica

`payment_events` permite construir analitica por timeline, no solo por estado final.
