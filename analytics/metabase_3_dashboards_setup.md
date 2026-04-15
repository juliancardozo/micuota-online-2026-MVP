# Setup rapido | 3 dashboards (Tenants, Profesores/Alumnos, Pagos)

Consultas SQL: [metabase_3_dashboards_tenants_profesores_alumnos_pagos.sql](metabase_3_dashboards_tenants_profesores_alumnos_pagos.sql)

## 1) Crear coleccion

En Metabase, crear una coleccion nueva:

- MiCuota | 3 Dashboards Operativos

## 2) Crear preguntas (Native query)

Para cada consulta del archivo SQL:

1. New question
2. Native query
3. Pegar consulta
4. Guardar con el mismo nombre del bloque (ej: D1_T1, D2_PA4, D3_P8)

## 3) Armar dashboards

## Dashboard 1: Tenants

Agregar:

- D1_T1 (KPI)
- D1_T2 (KPI)
- D1_T3 (bar horizontal)
- D1_T4 (bar horizontal)
- D1_T5 (bar horizontal)
- D1_T6 (line)

## Dashboard 2: Profesores y Alumnos

Agregar:

- D2_PA1 (KPI)
- D2_PA2 (KPI)
- D2_PA3 (stacked bar o tabla)
- D2_PA4 (bar horizontal)
- D2_PA5 (bar horizontal)
- D2_PA6 (line por serie role)
- D2_PA7 (bar horizontal)

## Dashboard 3: Pagos

Agregar:

- D3_P1 (KPI table)
- D3_P2 (KPI table)
- D3_P3 (KPI)
- D3_P4 (pie/bar)
- D3_P5 (bar)
- D3_P6 (bar)
- D3_P7 (line)
- D3_P8 (line)
- D3_P9 (table)

## 4) Filtros globales sugeridos

- Filtro de fecha por `created_at` para Dashboard 3.
- Filtro por tenant (usando `tenant_slug`) en D1_T3, D1_T4, D1_T5, D2_PA3 y D3_P9.
