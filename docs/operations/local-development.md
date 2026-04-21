# Desarrollo local

## Backend

```bash
cd backend
mvn spring-boot:run
```

Backend:

```text
http://localhost:8080
```

## Stack Docker

```bash
docker compose up -d
```

Servicios:

| Servicio | URL |
| --- | --- |
| Frontend | `http://localhost:5500` |
| Backend | `http://localhost:8080` |
| Mailpit | `http://localhost:8025` |
| Adminer | `http://localhost:8081` |
| Metabase | `http://localhost:3001` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3002` |

## Documentacion local

```bash
docker compose --profile docs up docs
```

Docs:

```text
http://localhost:8000
```

## Tests

```bash
cd backend
mvn test
```

Smoke:

```bash
./tests/run_all.sh
```
