# Metabase | Interactive charts y drill-through (MiCuota)

Esta guia te deja los dashboards de MiCuota explorables: zoom, ver registros, breakouts, X-ray y destinos personalizados.

## Objetivo

Hacer que los usuarios puedan:

- Hacer zoom en periodos/categorias.
- Abrir los registros que componen una barra o punto.
- Cambiar la dimension de analisis (breakout).
- Abrir exploraciones automaticas (X-ray).
- Navegar a dashboards/preguntas/URLs al hacer clic (custom destination).

## Requisito clave para drill-through completo

Metabase ofrece mejor drill-through cuando las preguntas se crean con **Query Builder** o con **Modelos** bien tipados.

- Si usas SQL nativo directo, el drill-through puede ser mas limitado.
- Para SQL: convertir la consulta en **Model** y definir metadata de columnas.

## Parte A) Crear charts explorables con Query Builder

Aplica en los dashboards:

- Tenants
- Profesores y Alumnos
- Pagos

Pasos:

1. New question > Query Builder.
2. Seleccionar tabla base (ej: `payment_operations`, `users`, `tenants`, `courses`).
3. Agregar agregaciones (count, sum, etc.) y filtros.
4. Usar una dimension temporal (`created_at`) en el eje X para habilitar zoom por granularidad.
5. Guardar pregunta y agregarla al dashboard.

Resultado:

- Click en barra/punto abre Action Menu con `Zoom in`, `View these records`, `Break out`, `X-ray`.

## Parte B) Si la base es SQL: convertir a Model

Para preguntas existentes en:

- `analytics/metabase_3_dashboards_tenants_profesores_alumnos_pagos.sql`

Haz esto:

1. Crear pregunta SQL en Metabase (sin sobre-resumir cuando necesites exploracion).
2. Guardar pregunta.
3. En el menu de la pregunta: **Turn into model**.
4. En Model metadata, configurar tipos de columna:
   - IDs: Entity key o FK.
   - Fechas: Date/DateTime.
   - Montos: Currency o Number.
   - Estado/rol/provider: Category.
5. Crear nuevas preguntas con Query Builder **desde ese modelo**.

Resultado:

- Drill-through mas rico sin perder tu logica SQL.

## Parte C) Drill-through esperado por dashboard

## 1) Dashboard Tenants

Charts recomendados:

- Tenants por mes (line/bar con `created_at`).
- Tenants por estado (bar).

Interacciones:

- Zoom por fecha (mes -> semana -> dia).
- View records para abrir lista de tenants del punto seleccionado.
- Breakout por plan/estado si esta disponible.

## 2) Dashboard Profesores y Alumnos

Charts recomendados:

- Usuarios por rol por mes (stacked bar).
- Alumnos por curso (bar).

Interacciones:

- Click en segmento `TEACHER` o `STUDENT` para ver registros.
- Breakout por tenant o curso.
- Zoom temporal en altas de usuarios.

## 3) Dashboard Pagos

Charts recomendados:

- Monto cobrado por mes (`sum(amount)`) por estado/provider.
- Operaciones por estado (`CREATED`, `PENDING`, `SUCCESS`, `FAILURE`).

Interacciones:

- Click en barra para ver operaciones subyacentes.
- Breakout por `provider`, `currency` o `tenant`.
- Zoom en picos de cobro para investigar outliers.

## Parte D) Custom destinations (SQL o Query Builder)

Si necesitas controlar adonde navegar al hacer clic:

1. Editar tarjeta en dashboard.
2. Click behavior > **Go to custom destination**.
3. Elegir destino:
   - Otra pregunta.
   - Otro dashboard.
   - URL externa (parametrizable).
4. Mapear parametros desde la celda clickeada.

Ejemplos utiles en MiCuota:

- Desde un chart de pagos por estado -> dashboard de detalle filtrado por `status`.
- Desde tabla de operaciones -> URL de pago publica:
  - `/pago.html?operationId={{id}}`

Nota: custom destination reemplaza el click default de drill-through en esa tarjeta.

## Parte E) X-rays y compare

Para habilitar exploracion automatica:

- Dejar activa la opcion de X-ray.
- En un chart, click > X-ray.
- Si aporta valor, guardar el X-ray como dashboard y limpiarlo (quitar tarjetas irrelevantes).

Recomendacion:

- Usar X-ray para descubrimiento, no como dashboard final sin curado.

## Checklist rapido de calidad

- Pregunta base creada en Query Builder o sobre Model.
- Columnas bien tipadas en metadata.
- Fechas con granularidad adecuada.
- IDs presentes para `View records`.
- Dashboard con filtros conectados (`created_at`, `tenant_slug`, `provider`, `status`).
- Click behavior revisado (default drill-through o custom destination segun caso).

## Relacion con assets existentes

- SQL pack 3 dashboards:
  - `analytics/metabase_3_dashboards_tenants_profesores_alumnos_pagos.sql`
- Setup base 3 dashboards:
  - `analytics/metabase_3_dashboards_setup.md`
- Setup dashboards globales:
  - `analytics/metabase_dashboard_setup.md`
