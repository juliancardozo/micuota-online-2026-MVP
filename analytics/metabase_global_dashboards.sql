-- MiCuota MVP | Metabase SQL Pack
-- Dashboards globales para entidades principales y metricas de negocio.
-- Base objetivo: PostgreSQL (database: micuota)

-- =========================================================
-- A) KPI CARDS (global)
-- =========================================================

-- A1) Total tenants
SELECT COUNT(*) AS total_tenants
FROM tenants;

-- A2) Total usuarios
SELECT COUNT(*) AS total_users
FROM users;

-- A3) Total cursos
SELECT COUNT(*) AS total_courses
FROM courses;

-- A4) Total inscripciones
SELECT COUNT(*) AS total_enrollments
FROM course_enrollments;

-- A5) Total operaciones de pago
SELECT COUNT(*) AS total_payment_operations
FROM payment_operations;

-- A6) Monto total operado (todas las operaciones)
SELECT COALESCE(SUM(amount), 0) AS total_amount_all_statuses
FROM payment_operations;

-- A7) Monto efectivamente cobrado (solo SUCCESS)
SELECT COALESCE(SUM(amount), 0) AS total_amount_success
FROM payment_operations
WHERE status = 'SUCCESS';

-- A8) Ticket promedio cobrado (solo SUCCESS)
SELECT COALESCE(AVG(amount), 0) AS avg_ticket_success
FROM payment_operations
WHERE status = 'SUCCESS';


-- =========================================================
-- B) COMPOSICION DE ENTIDADES
-- =========================================================

-- B1) Usuarios por rol
SELECT role, COUNT(*) AS total_users
FROM users
GROUP BY role
ORDER BY total_users DESC;

-- B2) Cursos por tenant
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(c.id) AS total_courses
FROM tenants t
LEFT JOIN courses c ON c.tenant_id = t.id
GROUP BY t.id, t.slug, t.name
ORDER BY total_courses DESC, t.id;

-- B3) Inscripciones por tenant
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(e.id) AS total_enrollments
FROM tenants t
LEFT JOIN course_enrollments e ON e.tenant_id = t.id
GROUP BY t.id, t.slug, t.name
ORDER BY total_enrollments DESC, t.id;

-- B4) Profesores por tenant
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(u.id) FILTER (WHERE u.role = 'TEACHER') AS total_teachers
FROM tenants t
LEFT JOIN users u ON u.tenant_id = t.id
GROUP BY t.id, t.slug, t.name
ORDER BY total_teachers DESC, t.id;

-- B5) Alumnos por tenant
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(u.id) FILTER (WHERE u.role = 'STUDENT') AS total_students
FROM tenants t
LEFT JOIN users u ON u.tenant_id = t.id
GROUP BY t.id, t.slug, t.name
ORDER BY total_students DESC, t.id;


-- =========================================================
-- C) EMBUDO Y CALIDAD DE COBRO
-- =========================================================

-- C1) Operaciones por estado
SELECT status, COUNT(*) AS total_operations
FROM payment_operations
GROUP BY status
ORDER BY total_operations DESC;

-- C2) Operaciones por tipo de flujo
SELECT flow_type, COUNT(*) AS total_operations
FROM payment_operations
GROUP BY flow_type
ORDER BY total_operations DESC;

-- C3) Operaciones por proveedor
SELECT provider, COUNT(*) AS total_operations
FROM payment_operations
GROUP BY provider
ORDER BY total_operations DESC;

-- C4) Tasa global de exito
SELECT
  COUNT(*) AS total_operations,
  COUNT(*) FILTER (WHERE status = 'SUCCESS') AS total_success,
  ROUND(
    100.0 * COUNT(*) FILTER (WHERE status = 'SUCCESS') / NULLIF(COUNT(*), 0),
    2
  ) AS success_rate_pct
FROM payment_operations;

-- C5) Monto pendiente o no concretado
SELECT
  COALESCE(SUM(amount) FILTER (WHERE status IN ('CREATED', 'PENDING')), 0) AS amount_open,
  COALESCE(SUM(amount) FILTER (WHERE status = 'FAILURE'), 0) AS amount_failed
FROM payment_operations;


-- =========================================================
-- D) SERIES TEMPORALES (mensual)
-- =========================================================

-- D1) Usuarios nuevos por mes
SELECT
  DATE_TRUNC('month', t.created_at)::date AS month,
  COUNT(*) AS new_tenants
FROM tenants t
GROUP BY DATE_TRUNC('month', t.created_at)
ORDER BY month;

-- D2) Operaciones de pago por mes
SELECT
  DATE_TRUNC('month', p.created_at)::date AS month,
  COUNT(*) AS total_operations
FROM payment_operations p
GROUP BY DATE_TRUNC('month', p.created_at)
ORDER BY month;

-- D3) Monto total operado por mes (todos los estados)
SELECT
  DATE_TRUNC('month', p.created_at)::date AS month,
  COALESCE(SUM(p.amount), 0) AS total_amount
FROM payment_operations p
GROUP BY DATE_TRUNC('month', p.created_at)
ORDER BY month;

-- D4) Monto cobrado por mes (solo SUCCESS)
SELECT
  DATE_TRUNC('month', p.created_at)::date AS month,
  COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'SUCCESS'), 0) AS success_amount
FROM payment_operations p
GROUP BY DATE_TRUNC('month', p.created_at)
ORDER BY month;

-- D5) Conversion mensual de cobros (SUCCESS / total)
SELECT
  DATE_TRUNC('month', p.created_at)::date AS month,
  COUNT(*) AS total_operations,
  COUNT(*) FILTER (WHERE p.status = 'SUCCESS') AS success_operations,
  ROUND(
    100.0 * COUNT(*) FILTER (WHERE p.status = 'SUCCESS') / NULLIF(COUNT(*), 0),
    2
  ) AS success_rate_pct
FROM payment_operations p
GROUP BY DATE_TRUNC('month', p.created_at)
ORDER BY month;


-- =========================================================
-- E) RANKINGS (gestion)
-- =========================================================

-- E1) Top 10 profesores por monto cobrado (SUCCESS)
SELECT
  p.teacher_id,
  COALESCE(u.full_name, 'N/A') AS teacher_name,
  COUNT(*) FILTER (WHERE p.status = 'SUCCESS') AS success_operations,
  COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'SUCCESS'), 0) AS success_amount
FROM payment_operations p
LEFT JOIN users u ON u.id = p.teacher_id
GROUP BY p.teacher_id, u.full_name
ORDER BY success_amount DESC
LIMIT 10;

-- E2) Top 10 tenants por monto cobrado (SUCCESS)
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(*) FILTER (WHERE p.status = 'SUCCESS') AS success_operations,
  COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'SUCCESS'), 0) AS success_amount
FROM payment_operations p
LEFT JOIN users u ON u.id = p.teacher_id
LEFT JOIN tenants t ON t.id = u.tenant_id
GROUP BY t.id, t.slug, t.name
ORDER BY success_amount DESC NULLS LAST
LIMIT 10;

-- E3) Top 10 cursos por cantidad de inscriptos
SELECT
  c.id AS course_id,
  c.name AS course_name,
  t.slug AS tenant_slug,
  COUNT(e.id) AS total_enrollments
FROM courses c
LEFT JOIN tenants t ON t.id = c.tenant_id
LEFT JOIN course_enrollments e ON e.course_id = c.id
GROUP BY c.id, c.name, t.slug
ORDER BY total_enrollments DESC, c.id
LIMIT 10;


-- =========================================================
-- F) VISTA OPERATIVA DE COBROS
-- =========================================================

-- F1) Ultimas 100 operaciones con contexto de tenant/profesor/alumno/curso
SELECT
  p.id,
  p.created_at,
  p.status,
  p.provider,
  p.flow_type,
  p.amount,
  p.currency,
  p.description,
  p.provider_reference,
  t.slug AS tenant_slug,
  teacher.full_name AS teacher_name,
  student.full_name AS student_name,
  c.name AS course_name
FROM payment_operations p
LEFT JOIN users teacher ON teacher.id = p.teacher_id
LEFT JOIN users student ON student.id = p.student_user_id
LEFT JOIN tenants t ON t.id = teacher.tenant_id
LEFT JOIN courses c ON c.id = p.course_id
ORDER BY p.created_at DESC
LIMIT 100;
