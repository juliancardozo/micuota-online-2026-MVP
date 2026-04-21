# Get Started

Esta guia te lleva desde cero hasta un cobro funcional con MiCuota.

## Conceptos base

| Concepto | Descripcion |
| --- | --- |
| Tenant | Organizacion o cuenta principal del profesional/centro. |
| Usuario | Persona autenticada dentro de un tenant. Puede ser admin, profesor o alumno. |
| TeacherProfile | Perfil profesional que concentra datos operativos y credenciales de pago. |
| Course | Grupo, curso, plan o servicio recurrente. |
| PaymentOperation | Intento/operacion de cobro creada contra un proveedor. |
| PaymentEvent | Evento auditable del ciclo de vida de un cobro. |

## Flujo minimo

1. Registrar tenant.
2. Iniciar sesion y obtener `X-Auth-Token`.
3. Crear alumnos o pacientes.
4. Crear curso o grupo.
5. Crear un cobro unico o suscripcion.
6. Compartir link o QR.
7. Recibir callback/webhook del proveedor.
8. Revisar estado y timeline.

## URL base

Local:

```text
http://localhost:8080
```

Frontend local:

```text
http://localhost:5500
```

## Ejemplo mental

Un profesor crea un cobro mensual para un alumno. MiCuota genera la operacion, obtiene un checkout de Mercado Pago, guarda la referencia del proveedor y registra eventos de auditoria. Cuando Mercado Pago notifica el resultado, MiCuota actualiza el estado y conserva el payload original.

## Siguiente paso

Configura [ambientes y autenticacion](environments-auth.md).
