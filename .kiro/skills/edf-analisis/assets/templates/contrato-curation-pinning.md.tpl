# Contrato Curation → Pinning — {{Ingesta}}

| Campo | Valor |
|---|---|
| Ingesta | `{{ingesta-id}}` |
| Versión / Fecha | 1.0.0 / {{AAAA-MM-DD}} |
| Estado | PROPUESTO |
| Contrato general | `.kiro/steering/11-contrato-curation-pinning.md` (formato del evento, regla de normalización, pendientes) |
| Fuentes de esta tabla | `insumos/source-spec-curation.yaml` (`campos_curados[]`), `analisis/mapeo-campos.md`, `references/logica-pinning.md` §5, §7, §12.1 |

## 1. Claves que Pinning consume

Una fila por clave de `curatedRecordJson` que entra a `identifying-attributes.json`. La columna "Clave publicada" se copia **literal** de `campos_curados[].destino`.

| Clave publicada en `curatedRecordJson` | Tipo publicado | ¿Puede ser `null`? | Normalización aplicada en Curation | PII | Slot del motor (`atributo_motor`) | Estado | Fuente |
|---|---|---|---|---|---|---|---|

## 2. Verificaciones antes de aprobar

- [ ] Cada clave existe en `campos_curados[].destino` del source-spec de Curation (mismo texto, mismas mayúsculas).
- [ ] Cada clave de identidad sale normalizada: mayúsculas, sin tildes, sin caracteres especiales, ceros tratados (steering `11` §3).
- [ ] Está declarado cómo se pasa de la clave publicada al slot del motor (steering `11` §6 #5). Si no está confirmado → fila `[A:mapeo-claves-curation-pinning]` y el atributo no entra al array activo.
- [ ] Ruta S3 de entrada a Pinning registrada o marcada `[PM]` (steering `11` §6 #1).
- [ ] Ningún valor real: solo nombres de campo y reglas.
