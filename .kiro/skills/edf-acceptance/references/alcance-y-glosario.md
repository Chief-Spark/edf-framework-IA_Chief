# Alcance y glosario — Acceptance (EDF/Oxygen)

## Qué responde la etapa

¿Qué datos tienen calidad suficiente para seguir, y cuáles no? Acceptance **decide** (archivo y
registro) y, opcionalmente, **infiere eliminaciones**. No diagnostica (eso es Validation) ni
transforma (eso es Curation).

| | Validation (antes) | **Acceptance** | Curation (después) |
|---|---|---|---|
| Produce | `TaggedValidatedEvent` con `errorCodes`, `qualityScores`, marcadores | decisión de archivo + split de registros | `CuratedEvent` |
| Código propio | processor Scala | **ninguno** (100 % HOCON) | processor Scala |
| Lee | archivo crudo | `validation/validated/` | `acceptance/accepted/` |

## Qué está dentro del skill

- Generar el bloque HOCON de la ingesta (`oxygen.stage.acceptance.configs[]`), las condiciones con
  nombre (`oxygen.stage.acceptance.fileConditions[]`) y, si aplica, el lookup de severidades y el
  `contextualOverride`.
- Generar casos de prueba de expresiones (Scala, esqueleto) y un fixture sintético de eventos.
- Verificar la propuesta, presentarla y **esperar aprobación** antes de entregarla.
- Explicar cómo ejecutar el motor y cómo leer su salida.

## Qué está fuera

- Modificar el motor (`SparkAcceptor`) o el `*-common-application.conf` base (regla aditiva).
- Escribir clases Scala de producción: `IAcceptanceProcessor` existe pero **nadie lo invoca**.
- Definir códigos de error o quality scores: vienen de Validation (IN-05).
- Reportes `Log_Sisben/RPT_*`, notificación al DNP, anti-duplicado por nombre de archivo.
- Decidir la semántica de borrado (D2): requiere decisión humana.

## Glosario

| Término | Significado |
|---|---|
| `TaggedValidatedEvent` | Evento de salida de Validation: `lineage`, `eventId`, `validatedRecordJson`, `qualityScores[]`, `errorCodes[]`, `eventType`, `asOfDate`, `subscriberRelation`, `headerErrors`, `footerErrors`, `keys`, `fileTag`… |
| `ObjectError` | `{errorCodeName, errorCodeValue, errorLevel, messageKey, errorType}`. El DSL compara `errorCodeValue` y `errorLevel`. |
| `QualityScore` | `{name, score: Double}`. SISBEN: `RQS` (0..1). |
| Marcador de archivo | Nombre con `errorInFileName` (-1002), `errorInHeader` (-1003), `errorInFooter` (-1004), `errorInEmptyFileBody` (-1005). |
| FQS | Calidad del archivo = `average("qs.RQS")` sobre los eventos que llegan (los menores ya fueron filtrados). |
| Condición con nombre | Entrada de `fileConditions[]`: `{name, expression, reasonCode, isLogged}`. Global a todas las ingestas. |
| `reasonCode` | Código que el motor registra cuando una condición con nombre se cumple (sintaxis nueva). SISBEN: `SISBFA00001`. |
| Sintaxis antigua | Expresión aritmética/booleana directa (`average(...) >= 0.90`). No registra `reasonCode`. |
| Sintaxis nueva | `anyOf(...)` / `allOf(...)` [+ `hasErrorlevelIn/NotIn(n)`] sobre condiciones con nombre. |
| Pendiente | Archivo cuyo `reject` y `accept` valen `false`: queda en `validated/` y se reevalúa en la siguiente corrida. |
| E1…E13 | Etapas internas del motor (ver `dsl-config-reference.md`). |
| Regla aditiva | Solo se agregan elementos en el archivo `*-inner.conf`; nunca se edita el base. |
| ASSUMPTION | Etiqueta obligatoria para todo valor sin evidencia (ver `decisiones-y-abiertos.md`). |
| HITL | Humano en el ciclo: el skill no entrega sin aprobación explícita. |

## Estados de un valor

`cerrado` (decidido) · `propuesta_de_frontera` (decidido aquí, falta confirmación de otro flujo) ·
`inferido` (evidencia parcial) · `abierto` (sin valor; bloquea su hueco).
