-- MiCuota MVP | Pack de 3 dashboards para Metabase
-- Objetivo: Tenants, Profesores/Alumnos y Pagos
-- Base: PostgreSQL (database: micuota)

-- =========================================================
-- DASHBOARD 1: TENANTS
-- =========================================================

-- D1_T1) Total tenants
SELECT COUNT(*) AS total_tenants
FROM tenants;

-- D1_T2) Tenants con actividad (al menos 1 usuario)
SELECT COUNT(DISTINCT t.id) AS active_tenants
FROM tenants t
JOIN users u ON u.tenant_id = t.id;

-- D1_T3) Tenants por cantidad de usuarios
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(u.id) AS total_users
FROM tenants t
LEFT JOIN users u ON u.tenant_id = t.id
GROUP BY t.id, t.slug, t.name
ORDER BY total_users DESC, t.id;

-- D1_T4) Tenants por cantidad de cursos
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(c.id) AS total_courses
FROM tenants t
LEFT JOIN courses c ON c.tenant_id = t.id
GROUP BY t.id, t.slug, t.name
ORDER BY total_courses DESC, t.id;

-- D1_T5) Revenue (SUCCESS) por tenant
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(p.id) FILTER (WHERE p.status = 'SUCCESS') AS success_operations,
  COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'SUCCESS'), 0) AS success_amount
FROM tenants t
LEFT JOIN users u ON u.tenant_id = t.id
LEFT JOIN payment_operations p ON p.teacher_id = u.id
GROUP BY t.id, t.slug, t.name
ORDER BY success_amount DESC, t.id;

-- D1_T6) Creacion de tenants por mes
SELECT
  DATE_TRUNC('month', t.created_at)::date AS month,
  COUNT(*) AS new_tenants
FROM tenants t
GROUP BY DATE_TRUNC('month', t.created_at)
ORDER BY month;


-- =========================================================
-- DASHBOARD 2: PROFESORES Y ALUMNOS
-- =========================================================

-- D2_PA1) Total profesores
SELECT COUNT(*) AS total_teachers
FROM users
WHERE role = 'TEACHER';

-- D2_PA2) Total alumnos
SELECT COUNT(*) AS total_students
FROM users
WHERE role = 'STUDENT';

-- D2_PA3) Profesores y alumnos por tenant
SELECT
  t.id AS tenant_id,
  t.slug AS tenant_slug,
  t.name AS tenant_name,
  COUNT(u.id) FILTER (WHERE u.role = 'TEACHER') AS total_teachers,
  COUNT(u.id) FILTER (WHERE u.role = 'STUDENT') AS total_students
FROM tenants t
LEFT JOIN users u ON u.tenant_id = t.id
GROUP BY t.id, t.slug, t.name
ORDER BY t.id;

-- D2_PA4) Top profesores por monto cobrado (SUCCESS)
SELECT
  p.teacher_id,
  COALESCE(teacher.full_name, 'N/A') AS teacher_name,
  COUNT(p.id) FILTER (WHERE p.status = 'SUCCESS') AS success_operations,
  COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'SUCCESS'), 0) AS success_amount
FROM payment_operations p
LEFT JOIN users teacher ON teacher.id = p.teacher_id
GROUP BY p.teacher_id, teacher.full_name
ORDER BY success_amount DESC
LIMIT 10;

-- D2_PA5) Top alumnos por monto pagado (SUCCESS)
SELECT
  p.student_user_id,
  COALESCE(student.full_name, 'N/A') AS student_name,
  COUNT(p.id) FILTER (WHERE p.status = 'SUCCESS') AS success_operations,
  COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'SUCCESS'), 0) AS success_amount
FROM payment_operations p
LEFT JOIN users student ON student.id = p.student_user_id
GROUP BY p.student_user_id, student.full_name
ORDER BY success_amount DESC
LIMIT 10;

-- D2_PA6) Altas mensuales por rol (TEACHER/STUDENT)
SELECT
  DATE_TRUNC('month', u.created_at)::date AS month,
  u.role,
  COUNT(*) AS total_users
FROM users u
WHERE u.role IN ('TEACHER', 'STUDENT')
GROUP BY DATE_TRUNC('month', u.created_at), u.role
ORDER BY month, u.role;

-- D2_PA7) Inscripciones por curso con profesor
SELECT
  c.id AS course_id,
  c.name AS course_name,
  COALESCE(teacher.full_name, 'N/A') AS teacher_name,
  COUNT(e.id) AS total_enrollments
FROM courses c
LEFT JOIN users teacher ON teacher.id = c.teacher_user_id
LEFT JOIN course_enrollments e ON e.course_id = c.id
GROUP BY c.id, c.name, teacher.full_name
ORDER BY total_enrollments DESC, c.id;


-- =========================================================
-- DASHBOARD 3: PAGOS
-- =========================================================

-- D3_P1) KPI de operaciones por estado
SELECT
  COUNT(*) AS total_operations,
  COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success_operations,
  COUNT(*) FILTER (WHERE status = 'PENDING') AS pending_operations,
  COUNT(*) FILTER (WHERE status = 'CREATED') AS created_operations,
  COUNT(*) FILTER (WHERE status = 'FAILURE') AS failure_operations
FROM payment_operations;

-- D3_P2) KPI de montos
SELECT
  COALESCE(SUM(amount), 0) AS total_amount,
  COALESCE(SUM(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS success_amount,
  COALESCE(AVG(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS avg_ticket_success
FROM payment_operations;

-- D3_P3) Tasa de exito global
SELECT
  ROUND(
    100.0 * COUNT(*) FILTER (WHERE status = 'SUCCESS') / NULLIF(COUNT(*), 0),
    2
  ) AS success_rate_pct
FROM payment_operations;

-- D3_P4) Operaciones por estado
SELECT
  status,
  COUNT(*) AS total_operations
FROM payment_operations
GROUP BY status
ORDER BY total_operations DESC;

-- D3_P5) Operaciones por proveedor
SELECT
  provider,
  COUNT(*) AS total_operations
FROM payment_operations
GROUP BY provider
ORDER BY total_operations DESC;

-- D3_P6) Operaciones por tipo de flujo
SELECT
  flow_type,
  COUNT(*) AS total_operations
FROM payment_operations
GROUP BY flow_type
ORDER BY total_operations DESC;

-- D3_P7) Monto cobrado por mes (SUCCESS)
SELECT
  DATE_TRUNC('month', created_at)::date AS month,
  COALESCE(SUM(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS success_amount
FROM payment_operations
GROUP BY DATE_TRUNC('month', created_at)
ORDER BY month;

-- D3_P8) Conversion mensual (SUCCESS / total)
SELECT
  DATE_TRUNC('month', created_at)::date AS month,
  COUNT(*) AS total_operations,
  COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success_operations,
  ROUND(
    100.0 * COUNT(*) FILTER (WHERE status = 'SUCCESS') / NULLIF(COUNT(*), 0),
    2
  ) AS success_rate_pct
FROM payment_operations
GROUP BY DATE_TRUNC('month', created_at)
ORDER BY month;

-- D3_P9) Ultimas 100 operaciones con contexto
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
