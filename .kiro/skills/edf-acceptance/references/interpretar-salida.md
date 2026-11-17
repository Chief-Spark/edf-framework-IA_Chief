# Interpretar la salida de Acceptance

## Dónde mirar

| Resultado | Ubicación (defaults) | Mensaje de log del motor |
|---|---|---|
| Rechazo automático (E4) | `acceptance/rejected/files/<archivo>.<id>` | `File rejected due to --- <marcador>` |
| Rechazo por calidad (E8) | `acceptance/rejected/files/<archivo>.<id>` | `File rejection expression … evaluated to: true. Reason Codes: SISBFA00001: true` |
| Aceptado (E9) + split (E10) | `acceptance/accepted/<archivo>` y `acceptance/rejected/records/<archivo>.<id>` | `File acceptance expression: … Evaluated to: true` y `Record level acceptance of … acceptedRecordCnt = N rejectedRecordCnt = M` |
| Pendiente | sigue en `validation/validated/` | `… Evaluated to: false` en reject y accept |
| Todo rechazado por registro | archivo marcado rechazado | `Rejecting the file … since all records were rejected by RecordLevelAcceptance` |
| Borrados inferidos (E12) | `acceptance/accepted/inferred-<archivo>-asDeleted.<id>` | `----Count of delete inferred events: N` |

Métricas Kafka (`metricTopic`): `acceptedRecords`, `rejectedRecords`, `totalRecords` (si
`emitStockMetrics = true`). Actualizaciones de proceso (`puTopic`): estado del dataset.

## Señales de alarma (fallos silenciosos)

- `Setting default evaluation to true` → una expresión de **registro** falló y se aceptó todo.
- `Evaluating reject configuration using older syntax. Reason Codes will not be logged.` → `reject`
  cayó a la sintaxis antigua: no habrá `SISBFA00001`.
- `Setting evaluation result as false for Undefined conditions: X` → `reject` cita una condición que no
  está en `fileConditions` (o su nombre difiere).
- `Unable to lookup reason code … skipped in severity evaluation` → falta la fila en el lookup de
  severidades (solo con `hasErrorlevelIn`).
- `Invalid lookupMapping config` → `lookupMapping` incompleto: se ignora.
- Un archivo que no sale nunca de `validated/` → pendiente; revise que Validation emita `RQS`.

## Lectura para SISBEN

| Observación | Significado |
|---|---|
| `Reason Codes: SISBFA00001: true` | FQS < 0.90; el archivo no pasa a Curation. Notificar a negocio. |
| Rechazo con `errorInHeader` / `errorInFileName` | Diagnóstico de archivo de Validation (header de 22 columnas, nombre). |
| Rechazo con `errorInEmptyFileBody` | Archivo con header y sin registros. |
| `rejectedRecordCnt` > 0 en un archivo aceptado | Registros con sev. 1 o `OxygenDuplicate`; esperado mientras el FQS sea ≥ 0.90 (los duplicados no bajan el FQS). |
| `acceptedRecordCnt` = 0 con archivo aceptado | Contradicción: revisar `recordAcceptance` y los niveles emitidos. |

Contraste siempre con el resultado de `scripts/simular_decision.py` sobre la misma spec: si difiere,
la diferencia está en la configuración integrada (nombre, escapes) o en el contrato con Validation.

## Hand-off a Curation

Curation lee `acceptance/accepted/*` (`ACCEPTED_DIRECTORY`). Solo los registros aceptados pasan;
`errorInEmptyFileBody` se omite en Curation ("Curation Skipped"). Ver el contrato completo en
`SKILL.md`, sección *Contratos de hand-off*.
