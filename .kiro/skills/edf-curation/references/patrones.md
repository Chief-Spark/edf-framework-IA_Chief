# Patrones de Curation (derivado de `curation_patrones.yaml`)

Uso: **se_escribe** (la fuente escribe código) · **se_configura** (HOCON) · **se_consume** (lo aplica
el motor) · **opcional** · **no_usar**. Evidencia: verificado en el código de oxygen-curation salvo
que se indique. Abreviaturas de archivos del motor: SC `SparkCurator.scala`, CUR `Curator.scala`,
CP `CurationProcessor.scala`, CU `CuratorUtils.scala`, CIO `CuratorIOUtils.scala`,
CS `CurationService.java`, CCPJ `CreditCurationProcessorJson.scala`.

## Motor: 13 etapas internas

E1 construcción · E2 modo · E3 dataset · E4 filtros previos · E5 lectura · E6 processor por evento ·
E7 transformación · E8 fallos por evento · E9 métricas · E10 escritura · E11 estado del dataset ·
E12 STOCK · E13 EVENTSYNC/INCSYNC. SISBEN usa E1-E12; **E13 no aplica**.

Frontera: entra `TaggedValidatedEvent` aceptado; sale `TaggedCuratedEventWithMap` en
`curatedDir/flow` o `curatedDir/stock` + entradas del domain dictionary.

## Patrones

| ID | Patrón | Uso | Regla para el skill |
|---|---|---|---|
| CUR-P01 | Template Method (`Curator` + `runInternal`) | se_consume | — |
| CUR-P02 | Factory por `oxygen.application.lambda` | se_consume | `lambda = batch` |
| CUR-P03 | Enum de modos FLOW/STOCK/EVENTSYNC/INCSYNC | se_configura | FLOW para el periodo, STOCK para regenerar; **clave de etapa**, el skill no la emite |
| CUR-P04 | Deprecación con puente `run(Boolean)` | no_usar | — |
| CUR-P05 | Strategy por nombre de clase + reflexión (`filePattern → processor`) | se_configura | Declarar la clase en HOCON; constructor exacto `(CurationProcessorContext)` |
| CUR-P06 | Caché de processors + broadcast | se_escribe | Processor `Serializable` y liviano |
| CUR-P07 | Registry `CurationService` | opcional | No se usa (reflexión por llamada y errores silenciados) |
| CUR-P08 | Information Type + Action | se_escribe | Modelo de Capa A: acciones como funciones puras |
| CUR-P09 | Hook con un miembro abstracto (`curateEvent`) | se_escribe | — |
| CUR-P10 | Null Object (`DefaultOxygenCurationProcessor`, `InferredDeletedCurationProcessor`) | se_consume | Los eventos `inferredAsDeleted` los cura el motor |
| CUR-P11 | `@tailrec` sobre datasets | se_consume | — |
| CUR-P12 | `foldLeft` con estado en `mapPartitions` | se_consume | El processor se busca por `lineage.fileName` |
| CUR-P13 | Try + Option en IO | se_consume | — |
| CUR-P14 | Escritura temporal + move + archivado | se_consume | STOCK archiva la versión anterior |
| CUR-P15 | `CuratedEvent` con `write` JSON | se_escribe | Claves de `write` = nombres publicados |

## Antipatrones que el skill neutraliza

| ID | Problema (dónde) | Cómo lo trata el skill |
|---|---|---|
| CUR-AP01 | Evento que lanza excepción se descarta en silencio (SC:280-284) | `curateEvent` nunca lanza; `curar` con `NonFatal` → registro vacío + `registro:ERROR_INTERNO` y log con `eventId` y clase de la excepción, sin valores (AUD-C2) |
| CUR-AP02 | Sin processor para `lineage.fileName` → descarte sin log (SC:277) | `filePattern` probado con ejemplos y contraejemplos; fixture `evt-0008` lo demuestra |
| CUR-AP03 | Registro de acciones silencia errores + `.get()` (CS:46-73) | No se usa `CurationService` |
| CUR-AP04 | `newInstance` por llamada (CS:48, 60) | Capa A sin reflexión |
| CUR-AP05 | `domainDictionaryFile` de otra fase leída con `.get` (SC:63-64) | Checklist: la clave debe existir en el ambiente |
| CUR-AP06 | `println`/`logger.error` informativos | Plantillas sin `println`; logs sin datos |
| CUR-AP07 | `emitStockMetrics` leída con `.get` (SC:47) | Checklist: la clave debe existir |
| CUR-AP08 | `outputEventType`/`enrichEventWithPayload` deprecados pero usados; default `Payload.empty` | Se sobrescriben: 30 y `event.payload` |
| CUR-AP09 | `inferredAsDeleted` sobrescribible pero ignorado en batch | No se sobrescribe |
| CUR-AP10 | Lookup y `logger.error` por registro (CCPJ:37-38) | Homologación cargada una vez (`@transient lazy val`) |
| CUR-AP11 | Casteos sin guarda de `REC_NEW_FREQUENCY` (CUR:193-196) | El evento SISBEN no publica esa clave |
| CUR-AP12 | Typo `partionSize` (propiedad) / clave `partitionSize` | No se genera; solo se lee |
| CUR-AP13 | Parámetros sin uso (`domainDictionarType`, `dropDuplicates`) | Ignorados |
| CUR-AP14 | Kryo sin registro de clases | Fuera de alcance (motor) |

## Superficie de extensión

| Miembro | Tipo | SISBEN |
|---|---|---|
| `curateEvent(TaggedValidatedEvent): CuratedEvent` | abstracto | Sí |
| `outputEventType: Int` | deprecado pero usado | 30 (inferido) |
| `enrichEventWithPayload(event): Payload` | deprecado pero usado | `event.payload` |
| `subscriberRelation(event)` | opcional | No por defecto (se copia del aceptado); sí si el source-spec declara `extension_subscriber_relation` (D14, firma verificada en la copia del motor; falta compilar, F-01) |
| `curationService` | opcional | No |
| `subscriberRelationStartDate/EndDate` | deprecado | No |
| `inferredAsDeleted`, `readAcceptedEvent` (`???`), `enrichLogging...` | — | No |

Reglas del contrato: CUR-RC01 constructor exacto · CUR-RC02 Serializable, `@transient lazy val` ·
CUR-RC03 nunca lanzar por registro · CUR-RC04 lookups por `context.lookup`, una vez · CUR-RC05 claves
de `write` = domain dictionary, sin renombrar · CUR-RC06 sin PII en logs.

## Techo de abstracción

CUR-T01 uno entra, uno sale · CUR-T02 sin contexto de otros registros ni histórico · CUR-T03 sin
canal de errores propio (las advertencias van dentro del JSON curado, D13) · CUR-T04 guard-rails
fijos · CUR-T05 salida débilmente tipada (`Map[String, Any]`): por eso Capa B genera evento y
dictionary · CUR-T06 un processor por `filePattern` (REGULAR/RT/EP se derivan del nombre, inferido).

## Modelo de capas

| Capa | Artefacto | Abstracción |
|---|---|---|
| A | `FieldMapping.scala`, `Transformaciones.scala` (+ tests) | Alta, agnóstica de fuente; se generan una vez |
| B | `SISBENCurationSpec.scala` | Declarativa: fuente única de verdad de evento + dictionary |
| C | `SISBENCurationProcessor.scala` | Baja, delgada; parte manual: enriquecimiento abierto |

## No usar como plantilla

`CreditCurationProcessorJson` (datos de prueba, `logger.error` por registro; solo la forma),
`extension/Constants.scala` (Brasil), `standalone/`, procesadores de lookup de Synthesis, modo
streaming/HF, copias de modelos de fases anteriores, `CurationInferProcessor` (comentado),
`curation-application-local.conf` (rutas de un desarrollador), `Curation-Patterns.md` (cifras que el
código desmiente).
