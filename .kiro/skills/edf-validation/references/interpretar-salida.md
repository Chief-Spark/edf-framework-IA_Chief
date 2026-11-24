# Interpretar la salida y entregar a Acceptance

## Qué produce Validation

Un único destino: `paths.validatedDir` (texto en QA/STG, Avro en PRO), más eventos de métricas en
Kafka. No hay `rejected/`. Cada línea es un `TaggedValidatedEvent`:

| Campo | Qué mirar |
|---|---|
| `lineage` | `dataSource = DNP`, `subscriber = 999722`, `fileName`, `receivedDate`, `fileAsOfDate`, `recordHash` |
| `validatedRecordJson` | Mapa ordenado `NOMBRE_CAMPO → valor` (22 claves) |
| `errorCodes[]` | `ObjectError`: `errorCodeValue` = código, `errorLevel` = `1`/`3`, `messageKey` = causa |
| `qualityScores[]` | `{name: "RQS", score: 1.0 | 0.0}` |
| `eventType` | 30; o el del marcador (-1002…-1005) |
| `asOfDate`, `subscriberRelation` | `FECHA_ACTUALIZACION_CNS` en epoch ms; `NUM_DOCUMENTO` |
| `keys` | Clave de duplicado unida |

## Árbol de diagnóstico

1. **El job falla** → archivo totalmente vacío (`File Empty Exception`), clase `processor` no
   encontrada (`.processorerror`), `lambda ≠ batch`, `emitStockMetrics` ausente, o una excepción por
   registro distinta de `MalformedEventException` (no debería ocurrir con el código generado).
2. **Solo hay un evento marcador** con sufijo en el nombre de salida:
   - `errorInFileName` (-1002): el nombre no cumple el regex o la marca de tiempo es imposible. Código
     `SISBEN_ERR_024`.
   - `errorInHeader` (-1003): header con ≠ 22 columnas o nombres no reconocidos. `SISBEN_ERR_025`.
   - `errorInFooter` (-1004): no aplica a SISBEN (`footerRows = 0`). Con `filas_footer > 0` el processor
     valida el footer (`validateFooter`, código `codigos_especiales.footer`) y lo decide `validate[].footer` (AUD-V3).
   - `errorInEmptyFileBody` (-1005): header presente pero **ningún registro tras filtros**; ocurre
     también si todas las filas son de menores o vacías.
   Acceptance rechaza automáticamente estos marcadores.
3. **Hay registros**: revisar conteo por código y promedio de RQS (métrica `AverageRQS`).
   - `SISBEN_ERR_026` alto → delimitador incorrecto o `;` dentro de un valor.
   - `SISBEN_ERR_003/005` altos → catálogo DANE desactualizado o mal cargado en `lookupHome`.
   - `SISBEN_ERR_018/022` altos → formato de fecha nuevo en la fuente.
   - `SISBEN_ERR_023` → salvoconductos pendientes de homologación (esperado, F-08).
   - `OxygenDuplicate` → duplicados por tipo + número; se conserva el más reciente.

Comparar con `prevalidar_archivo.py --detalle` sobre el mismo archivo: debe coincidir el conteo por
código. Una diferencia indica catálogos distintos o un comportamiento del motor no simulado.

**Límite del oráculo: no modela E8 (footer).** `prevalidar_archivo.py` reproduce E5 → E6 → E7 → E9 →
E12 → E13 (orden del motor en `alcance-y-glosario.md`); no lee `filas_footer` ni `footer_regex` y nunca
produce `errorInFooter` (-1004). Si la spec declara `filas_footer > 0`, el script se detiene con
`SpecError` (código 2) en lugar de validar el footer como un registro. Para SISBEN no hay diferencia:
`filas_footer = 0`. (AC-24; plan de corrección #6, P-2.)

## Privacidad al revisar

No pegar `validatedRecordJson` en tickets, chats ni logs. Reportar números de línea, códigos y conteos.

## Contrato de entrega a Acceptance

Lo que Validation garantiza y Acceptance consume:

| Elemento | Valor SISBEN |
|---|---|
| Ubicación | `validatedDir` del ambiente; `lineage.fileName` = nombre original del archivo |
| `filePattern` de Acceptance | debe coincidir con la ruta **sin extensión** y con `lineage.fileName` |
| Niveles | `1` (error) y `3` (inconsistencia); sin `0` |
| Códigos | `SISBEN_ERR_0xx`, `SISBEN_INC_0xx` (14 caracteres) + `OxygenDuplicate` |
| Quality score | `RQS` ∈ {0.0, 1.0} por registro |
| Regla de registro sugerida | `errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate")` |
| Regla de archivo sugerida | `average("qs.RQS") >= 0.90` acepta; condición `RQS_BAJO_UMBRAL_SISBEN` = `average("qs.RQS") < 0.90`, `reasonCode = SISBFA00001` |
| Rechazo automático | marcadores `errorInFileName`/`errorInHeader`; `customReasonForAutomaticRejectionEnabled = true` |
| Duplicados | Acceptance no deduplica: rechaza por `OxygenDuplicate` |

La configuración de Acceptance la genera su propio skill; este skill solo entrega el contrato y el
resumen de pre-validación (`vista_previa_acceptance`).
