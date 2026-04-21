# Conciliacion

La conciliacion compara lo que MiCuota esperaba con lo que informo el proveedor.

## Estado actual

`PaymentOperation` tiene:

- `reconciliationStatus`
- `lastReconciledAt`
- `rawResponse`
- `providerReference`
- `status`

## Estados sugeridos

| Estado | Significado |
| --- | --- |
| `PENDING` | Aun no conciliado o sin cierre definitivo. |
| `MATCHED` | Coincide con lo esperado. |
| `MISMATCH` | Hay diferencia o inconsistencia. |

## Entradas de conciliacion

- Webhook del proveedor.
- Consulta directa al PSP.
- Job programado.
- Operacion manual de soporte.

## Eventos recomendados

- `RECONCILIATION_MATCHED`
- `RECONCILIATION_MISMATCHED`

## Criterios minimos

Un pago se considera conciliado si coincide:

- provider
- providerReference
- monto
- moneda
- estado final

## Siguiente capa tecnica

Separar `Receivable` de `PaymentOperation`:

- `Receivable`: deuda/cuota que se espera cobrar.
- `PaymentOperation`: intento concreto contra un proveedor.
- `PaymentEvent`: historia auditable.

Eso permite varios intentos para una misma deuda.
