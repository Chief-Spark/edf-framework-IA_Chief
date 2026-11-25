# Mapeo de campos — {{Ingesta}}

| Campo | Valor |
|---|---|
| Ingesta | `{{ingesta-id}}` |
| Versión / Fecha | 1.0.0 / {{AAAA-MM-DD}} |
| Cobertura | {{n}} de {{N}} campos del contrato del archivo — **debe ser N de N para cerrar la etapa 0** |

Una fila por campo de la fuente, con las **nueve dimensiones** de `07-contratos-frontera.md` §3. Ninguna celda vacía: si no se sabe, `[A:<ID>]` o `[PM]` con su pregunta abierta. **No se mapea a XPM aquí** (`04` §4.4).

Formato de contrato (excepción explícita de `05-convenciones.md` §8):

- Una celda puede juntar los hechos de **una misma dimensión**: «Tipo origen → destino» (`texto → Long`) y «Obligatorio (y qué pasa si falta)» (`sí; si falta: rechazo del registro`).
- **Marcador por dimensión.** Todo hecho de una celda que no esté confirmado lleva su marcador dentro de la celda, junto al hecho: `texto → Long [A:PA-003]`, `sí; si falta: [PM]`. Así se marca una sola dimensión sin marcar el campo entero.
- La columna `Estado` resume la fila: `[C]` solo si las nueve dimensiones son `[C]`; si no, la lista de marcadores abiertos de la fila (`[A:PA-003]`, `[PM]`).
- Efecto de la violación: uno de los efectos de `07-contratos-frontera.md` §5.1, con su nombre exacto (rechazo del registro, inconsistencia registrada, vaciado del campo con preservación, nulo con advertencia, descarte silencioso, marca de duplicado no aceptado), o `—` si el campo no tiene regla.

| # | Nombre en origen | Nombre curado propuesto | Público / PII | Tipo origen → destino | Regla de transformación (incl. EPOCH) | Catálogo | Obligatorio (y qué pasa si falta) | Efecto de la violación (`07` §5.1) | ¿Llave de identidad? | Field Handler (LFP/LAP/EUC) | Estado | Fuente |
|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Notas por campo

<!-- Solo para campos con reglas complejas o contradicciones. Referenciar por # de fila. -->

## Uso aguas abajo

- El **nombre curado** de esta tabla es el que `source-spec-curation.yaml` usará como `campos_curados[].destino` y el que `contrato-curation-pinning.md` y los `yaml-skill-*.yaml` deben citar **exactamente** (steering `11` §6 #5).
- Los valores PII de esta tabla son nombres de campo y reglas, nunca valores reales.
