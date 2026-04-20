# MiCuota Embedded Payments Playbook (LATAM)

Fecha: 2026-04-20  
Rama: `feat/embedded-payments-strategy-micuota`

## Objetivo
Diseñar y escalar MiCuota.online como una plataforma SaaS con embedded payments para profesores, psicologos y profesionales de servicios recurrentes en LATAM (foco Uruguay y Argentina).

## North Star
"Construir el Stripe de profesores en LATAM":
- UX de cobro extremadamente simple para usuarios no tecnicos.
- Pagos embebidos como ventaja competitiva y motor de retencion.
- Datos de pagos convertidos en decisiones de producto, automatizaciones y revenue.

## Principios de diseno obligatorios
1. Embedded payments como ventaja competitiva
- El flujo de cobro ocurre dentro de MiCuota, no como experiencia externa fragmentada.
- Menos pasos y menos context switching = mas conversion.

2. Payments como centro de ingresos
- Los pagos no son solo infraestructura: son una linea de negocio.
- Debe existir monetizacion en capas: take rate + planes + valor financiero.

3. Reduccion de friccion
- Reducir redirecciones, clicks y formularios largos.
- Checkout orientado a mobile-first (uso principal en LATAM).

4. Data como ventaja
- Cada intento de pago, abandono y exito es signal para mejorar cobrabilidad.
- Score por alumno/paciente y score por profesional.

5. Retencion via pagos
- Si un profesional depende de MiCuota para cobrar, conciliar y recuperar morosidad, el churn baja.

6. Modelo de pagos evolutivo
- Hoy: Partner-enabled (MercadoPago y similares).
- Mediano plazo: revenue sharing optimizado por mix de metodos.
- Largo plazo: evaluacion de modelo PayFac o esquema hibrido.

7. Infraestructura escalable
- Backend desacoplado por dominio de pagos.
- Automaciones por eventos.
- Soporte multi-tenant desde el diseño.

## Estado objetivo del producto (vision 12 meses)
- Alta embebida de profesional con onboarding KYC/KYB guiado.
- Suscripciones con dunning automatico y reintentos inteligentes.
- Cobro omnicanal: link, QR, boton embebido y checkout in-app.
- Dashboard financiero por tenant: MRR, cobranza neta, mora, recupero.
- Motor de recomendaciones: "que hacer hoy para cobrar mas".

## Arquitectura recomendada (simple y escalable)

### Capas
1. Frontend SaaS (web app)
- Modulos: onboarding, alumnos/pacientes, planes, checkout links/QR, reportes.
- UI con estado de pago en tiempo real (pendiente, aprobado, rechazado, vencido).

2. Payments Orchestrator (backend)
- Servicio central que abstrae proveedores (MercadoPago primero).
- Responsabilidades:
  - crear intents/cobros
  - registrar metodos
  - manejar webhooks idempotentes
  - conciliacion y ledger interno
  - dunning y reintentos

3. Billing & Revenue Engine
- Gestion de comisiones, take rate y reglas por plan.
- Separacion entre:
  - dinero del profesional
  - comision MiCuota
  - impuestos/percepciones

4. Data & Insights
- Event bus + almacenamiento analitico.
- Eventos minimos:
  - payment_created
  - payment_attempted
  - payment_succeeded
  - payment_failed
  - payment_refunded
  - subscription_renewal_due

5. Notification Automation
- WhatsApp/email/SMS por estado de pago.
- Flujos: recordatorio pre-vencimiento, post-fallo, recupero.

### Requisitos tecnicos no negociables
- Idempotencia en webhooks y cobros.
- Trazabilidad completa por transaction_id y tenant_id.
- Multi-tenant isolation (datos, permisos, reportes).
- Observabilidad de pagos (latencia, exito, fail reasons, retries).
- Auditoria para soporte y compliance.

## Backlog de alto impacto (priorizado)
1. Checkout embebido de 1 paso para alumnos/pacientes.
2. Dunning inteligente (reintentos + mensaje personalizado por causa de fallo).
3. Reconciliacion automatica diaria y alertas de descalce.
4. Panel "salud de cobranzas" para profesional.
5. Motor de recupero de mora con campanas preconfiguradas.
6. Plantillas por rubro (profesor, psicologo, academia) con onboarding express.

## Monetizacion recomendada

### Capa 1: Core payments revenue
- % por transaccion procesada (take rate).
- Fee por funcionalidad de cobro avanzada (por ejemplo, dunning avanzado).

### Capa 2: SaaS + Payments
- Plan base con cobro simple.
- Plan Pro con:
  - automatizaciones de recupero
  - analytics avanzado
  - integraciones (contable/CRM)

### Capa 3: Servicios financieros (fase 2/3)
- Adelanto de cobros (segun riesgo y historial).
- Cuenta virtual para liquidaciones y cash management.
- Seguros o proteccion de ingresos (alianzas).

## Riesgos clave y anti-patrones
- Depender solo de links externos sin experiencia embebida real.
- No instrumentar datos de fallos y abandono (se pierde palanca de mejora).
- Mezclar logica de negocio y proveedor de pago en el mismo modulo.
- Ausencia de idempotencia (doble cobro / estados inconsistentes).
- Pricing confuso: fee oculto o variable no predecible para el profesional.
- Intentar escalar a PayFac sin volumen, compliance y capacidades operativas.

## KPI framework (producto + pagos)
- Activacion:
  - % profesionales que crean primer cobro en <24h
  - tiempo a primer cobro exitoso
- Conversion:
  - tasa de pago exitoso por metodo
  - abandono de checkout
- Retencion:
  - churn de profesionales con y sin cobro recurrente activo
  - % profesionales con al menos 1 renovacion mensual
- Revenue:
  - TPV mensual
  - net revenue take rate
  - ARPA por vertical
- Riesgo operativo:
  - ratio de disputas/contracargos
  - tasa de fallos por causa

## Cadencia de experimentacion (1-2 semanas)

### Sprint 1 (impacto rapido)
Hipotesis:
- Un checkout embebido con menos campos aumenta conversion de alumnos/pacientes.

Implementacion:
- Reducir formulario al minimo.
- Mostrar metodos recomendados por contexto (monto, pais, recurrencia).

Medicion:
- baseline vs variante en conversion y tiempo de pago.

Criterio de exito:
- +8% conversion o -20% abandono en checkout.

### Sprint 2
Hipotesis:
- Mensajes de recupero personalizados por motivo de fallo mejoran recaudacion.

Implementacion:
- Segmentacion por codigo de error de pago.
- Secuencia automatica de 3 recordatorios.

Medicion:
- recupero de pagos fallidos a 7 dias.

Criterio de exito:
- +15% recupero vs flujo actual.

## Plantilla de respuesta operativa (usar siempre)
Cuando se analice cualquier decision en MiCuota, responder en este orden:

1. Diagnostico
- Que esta bien y que esta mal respecto a embedded payments.

2. Oportunidades
- Oportunidades concretas de producto y negocio, priorizadas por impacto.

3. Arquitectura recomendada
- Propuesta tecnica simple, incremental y escalable.

4. Monetizacion
- Como capturar mas ingreso (take rate, planes, servicios financieros).

5. Riesgos
- Riesgos tecnicos, regulatorios y de ejecucion.

6. Proximo experimento (MVP mindset)
- Prueba de 1-2 semanas con hipotesis, implementacion y KPI.

## Decision gates para evolucionar a PayFac (futuro)
Solo evaluar seriamente cuando se cumplan umbrales minimos:
- Volumen y TPV que justifiquen economics.
- Equipo de riesgo/fraude y compliance operativo.
- Capacidad de soporte financiero y conciliacion robusta.
- Acuerdos regulatorios por pais (UY/AR inicialmente).

---
Este documento es una guia de ejecucion. Si una iniciativa no mejora conversion, retencion o revenue de pagos, se desprioriza.
