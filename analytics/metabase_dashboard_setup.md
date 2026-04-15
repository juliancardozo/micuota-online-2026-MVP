# Metabase dashboard setup rapido

Archivo de consultas: [metabase_global_dashboards.sql](metabase_global_dashboards.sql)

## 1) Crear coleccion

En Metabase, crea una coleccion llamada:

- Dashboard Global MiCuota

## 2) Crear preguntas SQL

Para cada bloque del SQL:

1. New question
2. Native query
3. Pegar una consulta
4. Guardar con nombre descriptivo

## 3) Armar dashboards sugeridos

## Dashboard 1: Resumen Ejecutivo

- Total tenants (A1)
- Total usuarios (A2)
- Total cursos (A3)
- Total inscripciones (A4)
- Total operaciones (A5)
- Monto cobrado (A7)
- Ticket promedio (A8)
- Tasa global de exito (C4)

## Dashboard 2: Cobros y Conversion

- Operaciones por estado (C1)
- Operaciones por flujo (C2)
- Operaciones por proveedor (C3)
- Monto pendiente/fallido (C5)
- Operaciones por mes (D2)
- Monto cobrado por mes (D4)
- Conversion mensual (D5)

## Dashboard 3: Entidades y Operacion

- Usuarios por rol (B1)
- Cursos por tenant (B2)
- Inscripciones por tenant (B3)
- Top cursos por inscriptos (E3)
- Top profesores por revenue (E1)
- Top tenants por revenue (E2)
- Ultimas operaciones (F1)

## 4) Tipos de visualizacion recomendados

- KPI cards: A1-A8, C4
- Bar chart horizontal: B1, B2, B3, E1, E2, E3
- Stacked bar: C1, C2, C3
- Line chart: D1, D2, D3, D4, D5
- Table: F1

## 5) Filtro global recomendado

Agregar filtro de fecha en dashboards usando created_at cuando aplique (D2, D3, D4, D5, F1).
