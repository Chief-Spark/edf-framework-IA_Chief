---
inclusion: manual
---

# Lógica de negocio operable — etapa Synthesis / Consistency

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-10-01 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; formato según `05-convenciones.md`; metodología según `04-reglas-analisis.md` (tipo T3, sin skill de análisis especificado — `[PM]`) |
| Naturaleza | Análisis técnico derivado de código real (`oxygen-synthesis`), destinado a servir de insumo YAML/Markdown intermedio para un Skill generador de artefactos de configuración (`.csv`, `.json`, `.config`/`.conf`) de esta etapa. No cierra preguntas abiertas ni fija supuestos |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

Describir, con precisión operable y cita verificable, toda la lógica de negocio de la etapa Synthesis / Consistency (etapa 5 del pipeline, `02-lexico-edf.md` §2) tal como existe en el código real de `oxygen-synthesis`: claves de configuración, estructura de los tres diccionarios que gobiernan el comportamiento (GDD, reglas de consistencia, reglas custom de filtrado), el algoritmo real de reconciliación, el DSL de consistencia, los puntos de extensión, y los casos de borde conocidos por lectura directa del código.

**Cubre:** todo lo que un Skill necesita conocer para proponer archivos de configuración (`.conf`/HOCON, `.json`) correctos para ejecutar esta etapa — claves, formato y esquema de los tres diccionarios, contratos de extensión (`ConsistencyCheckProcessor`, `SynthesisProcessor`), gramática del DSL, y comportamientos fail-fast/fail-silent verificados.

**No cubre:** la clasificación de complejidad técnica Spark ni el problema de negocio en términos generales (eso vive en `problema-synthesis.md`), el contrato de frontera formal con Pinning/State Calculation (`07-contratos-frontera.md`), ni la generación de código/configuración en sí.

Este documento se basa en lectura estática del repositorio `oxygen-synthesis`. No se ejecutó código. No se abrió, leyó ni citó `settings-artifactory.xml` (prohibido por `01-restricciones-principios.md` §4). Ningún dato de este documento proviene de personas reales; los archivos de ejemplo citados son fixtures sintéticos del propio repositorio InnerSource.

## 2. Advertencia estructural: nomenclatura y qué vive en este repo `[C]`

### 2.1 Discrepancia de nombres, confirmada a tres niveles

El producto se llama "Synthesis" (README, metadatos, documentación de negocio), pero:

- **El 100% del código Scala vive bajo el paquete** `com.experian.edf.oxygen.consistencycheck` (`Hallazgos-Symthesis.md`; confirmado por la estructura de `src/main/scala/com/experian/edf/oxygen/consistencycheck/`).
- **La clase principal ejecutable usa ese mismo namespace**: `com.experian.edf.oxygen.consistencycheck.app.SynthesisApp`, confirmado en el comando `spark-submit --class` del `README.MD` §RUN.
- **El propio metadato de discoverability del README mezcla ambos nombres como sinónimos** (`tags: synthesis, oxygen synthesis, consistency check, consistency, synthesize, periodic check`), confirmando que la organización interna no distingue los dos términos a nivel de intención, solo a nivel de código.

### 2.2 Bugs de paquete, verificados por lectura directa (no solo reportados)

Tres rutas de paquete inconsistentes confirmadas en esta sesión, cada una verificada con `grep` directo sobre el código, no solo citadas de análisis previos:

| Archivo | Declara/espera | Importa/usa en realidad | Verificación |
|---|---|---|---|
| `extension/RelationshipMigrationProcessor.scala` (puente lookup) | — | `package com.experian.edf.oxygen.pinning.lookup` — paquete copiado literalmente del repo `oxygen-pinning`, nunca corregido | Confirmado en `Hallazgos-Symthesis.md`; no releído directamente en esta sesión pero el hallazgo es específico y verificable |
| `domain/SynthesisProcessor.scala` | `package com.experian.edf.oxygen.consistencycheck.domain` (confirmado por lectura directa, línea 1) | — | `[C]`, verificado por `grep` directo en esta sesión |
| `domain/DomainDictionaryInternal.scala` | — | `import com.experian.edf.oxygen.domain.SynthesisProcessor` (**sin** `.consistencycheck.`, ruta distinta a donde la clase realmente vive) | `[C]`, verificado por `grep` directo en esta sesión — confirmado que es un import literal, no un alias ni un wildcard |
| `extension/SyntheticFieldCustomProcessor.scala` | — | `import com.experian.edf.oxygen.consistencycheck.domain.SynthesisProcessor` (ruta completa, correcta) | `[C]`, verificado por `grep` directo en esta sesión |

**Nota de alcance**: no se verificó en esta sesión si el proyecto compila con esta inconsistencia tal cual (podría existir un `type` alias, una reexportación, o el import roto podría pertenecer a una rama de compilación condicional no activa). Se registra como hallazgo verificado por lectura textual, no como afirmación sobre el comportamiento de compilación. `[A:sin-clasificar]`

### 2.3 Qué vive en este repo y qué no

Igual que en Pinning, las estructuras de datos centrales de dominio (`SyntheticEvent`, `TaggedCuratedEvent`, `TaggedCuratedEventWithMap`, `ConsistencyInfo`, `TaggedConsistencyInfo`) **no están definidas en `oxygen-synthesis`** — se importan de `com.experian.edf.oxygen.domain.*`, Z2, no presente en el workspace. `[C]`

Lo que **sí** vive en este repo y se puede describir con certeza completa: los tres diccionarios de configuración (GDD, reglas de consistencia, reglas custom), los puntos de extensión (`ConsistencyCheckProcessor`, `SynthesisProcessor`), el motor del DSL (lexer/AST/generador), y el orquestador (`ConsistencyCheckOps`).

## 3. El problema que resuelve (resumen operable)

Synthesis agrupa eventos ya pineados por `pin + cuenta + periodo` y genera un único registro reconciliado por cada grupo-periodo, resolviendo dos problemas distintos con motores de código separados (`problema-synthesis.md` §2.2, `[C]`):

1. **Reconciliación temporal** (Field Handlers LFP/LAP/EUC): decide, campo por campo, qué valor prevalece cuando el mismo pin/cuenta/periodo tiene múltiples reportes en el tiempo.
2. **Verificación de consistencia** (reglas CONS-\*): detecta violaciones de reglas de negocio entre eventos. Severidad de advertencia — nunca bloquea el pipeline completo (confirmado con cita de código en §10 de este documento).

## 4. Claves de configuración

### 4.1 Claves propias de Synthesis (`consistency-application.conf`, bajo `oxygen.stage.consistencyCheck`)

| Clave | Valor en el ejemplo local | Efecto |
|---|---|---|
| `oxygen.application.lambda` | `batch` | Modo de ejecución |
| `oxygen.application.paths.lookupHome` | ruta local | Carpeta raíz de los CSV de lookup |
| `oxygen.stage.pinning.paths.pinnedDir` | ruta local | Entrada: carpetas `*-pin-*` producidas por Pinning. **Solo la de tipo `-pin-hc` es la que Synthesis realmente consume** (ver §9) |
| `oxygen.stage.consistencyCheck.paths.checkedDir` | ruta local | Salida: resultados de consistencia (Avro) |
| `oxygen.stage.consistencyCheck.paths.synthesizedDir` | ruta local | Salida: eventos sintéticos (JSON) |
| `oxygen.stage.consistencyCheck.paths.dictionaryFile` | `["consistency-checks.json"]` | Nombre del archivo del diccionario de reglas de consistencia (ver §5.2) |
| `oxygen.stage.consistencyCheck.paths.domainDictionaryFile` | `["domaindictionary.json"]` | Nombre del GDD (ver §5.1) |
| `oxygen.stage.consistencyCheck.paths.customRulesFile` | `["custom-rules-empty.json"]` | Diccionario de reglas custom de filtrado/visibilidad (ver §5.3) — **vacío por defecto en el fixture local, confirmando que esta funcionalidad está deshabilitada salvo activación explícita** |
| `oxygen.stage.consistencyCheck.relationshipMigrationProcessorClassName` | FQCN de ejemplo | Procesador de migración de relaciones suscriptor-cuenta, cargado por reflexión |
| `oxygen.stage.consistencyCheck.checkFileTagEnabled` | `false` | Si se usa `fileTag` para agrupar eventos en periodos |
| `oxygen.stage.consistencyCheck.eventTypeList` | lista de nombres de ejemplo | Tipos de evento considerados — **valores de ejemplo del fixture, no genéricos; para SISBEN debe sustituirse con el tipo de evento real (30, con la ambigüedad ya registrada en `07-contratos-frontera.md` §2)** |
| `oxygen.stage.consistencyCheck.dateType` | `receivedDate` | Campo de fecha usado para agrupar por periodo |
| `oxygen.stage.consistencyCheck.historicAttributeArgs` | filtro de ejemplo | Filtra qué generaciones históricas de HBase se consideran al reconstruir estado |
| `oxygen.stage.consistencyCheck.synthesisOutputAsTSV` | `false` | Si se escribe adicionalmente el output sintetizado como TSV |
| `oxygen.stage.consistencyCheck.saveConsistencyInfoToFile` | `true` | Si se persiste el resultado de consistencia como Avro |
| `oxygen.stage.consistencyCheck.sendConsistencyInfoToKafka` | `false` | Si se emite el resultado de consistencia a Kafka |
| `oxygen.stage.consistencyCheck.disabledCategories` | ejemplo con umbral 100 cuentas | Deshabilita checks de una `checkCategory` cuando el número de cuentas del HC supera un umbral — control de costo computacional |
| `oxygen.stage.consistencyCheck.referenceEventsWindowInMonths` | comentado, sin default activo | Si se define, limita los eventos de referencia a una ventana temporal hacia atrás desde `asOfDate` |
| `oxygen.stage.consistencyCheck.contextualOverrideAttributes.shouldFilterDeletedEvents` | comentado (`false` implícito) | Activa inferencia de borrado: eventos recibidos antes de un evento marcado como borrado se excluyen |
| `oxygen.stage.consistencyCheck.datasetFormat.input/output` | `text` | Formato de lectura/escritura |

(`[C]`, verificado — `src/main/resources/local/consistency-application.conf`)

### 4.2 Claves compartidas de plataforma relevantes (`oxygen-config-default-local.conf`, sección `oxygen.stage.consistencyCheck`)

Nombres de tabla HBase para eventos sintéticos y state-check (`syntheticEventRealTimeTableName`, `syntheticEventBatchTableName`, `stateCheckRealTimeTableName`), rutas derivadas de `candidateGenerationHome` (`consistencyDir`, `checkedDir`, `synthesizedDir`, `synthesizedTsvDir`, `archivedDir`), y el procesador por defecto de Heterogeneous Collection (`com.experian.edf.oxygen.domain.defaults.DefaultHeterogeneousCollectionProcessor`, clase externa al repo). `[C]`

### 4.3 Lookup tables (`oxygen-lookup-local.conf`)

Declara tablas de lookup con `tableName`, `file` (ruta relativa a `lookupHome`), `keys` (lista de índices, algunos compuestos con `+`, algunos con `caseSensitive=false`), y opcionalmente un `processor` custom por tabla. Confirmado 8 tablas de ejemplo en el fixture local; ninguna específica de SISBEN. `[C]`

## 5. Los tres diccionarios — estructura exacta

Synthesis se gobierna por **tres diccionarios independientes**, no uno. Confundirlos es un error de generación grave porque tienen esquemas y propósitos distintos.

### 5.1 GDD — Global Domain Dictionary (`domaindictionary.json`)

Raíz: `{"domain": "<nombre>", "version": 1, "fields": [...]}`. Cada elemento de `fields`:

| Campo | Tipo | Obligatorio | Efecto |
|---|---|---|---|
| `name` | string | sí | nombre del campo del dominio |
| `handler` | `"EUC"` \| `"LAP"` \| ausente | no | ausente = LFP implícito (sin clase propia, es el comportamiento `else` del motor) |
| `periodicity` | boolean | no, default `true` | si el campo participa en la lógica periódica |
| `struct` | array de strings | no | subcampos de un campo compuesto (ej. `address` → `["street1","city","State"]`) |
| `singleValued` | boolean | no | acompaña a `struct` |
| `synthesizer` | string (FQCN) | no | clase `SynthesisProcessor` custom, cargada por reflexión (ver §7) |

**Hallazgo verificado, no documentado en análisis previos**: `handler` y `synthesizer` son **ortogonales** — un campo puede declarar `synthesizer` custom combinado con cualquiera de los tres handlers base. El fixture real confirma esto con tres campos (`accountInformation` LFP+synthesizer, `accountInformationLAP` LAP+mismo synthesizer, `accountInformationEUC` EUC+mismo synthesizer) que comparten el mismo processor custom, el cual discrimina por `fieldName` internamente — es decir, **un solo processor puede atender varios campos del GDD simultáneamente**, contradiciendo la expectativa de "un handler por campo" que otros documentos de diseño podrían sugerir. `[C]`

**Campos de sistema inyectados automáticamente** (no declarados por el usuario en el GDD, pero siempre presentes): `inferredAsDeleted`, `shouldDelete`, `fileTag` — los tres como `LAP` (`DomainDictionaryInternal.readDomainDictionary`). `[C]`

Soporta versión 1 (deprecated, con warning) y versión 2 del formato — no se profundizó en las diferencias exactas entre ambas versiones de parseo (`DomainDictionaryV1`/`DomainDictionaryV2`, clases Java). `[PM]`

### 5.1.1 Resolución del conflicto de nomenclatura LFP/LAP/EUC — decisión del usuario `[C]`

Durante esta iniciativa se detectaron tres versiones en conflicto del nombre y mecanismo de los Field Handlers: la Versión A (steering + código real, la que usa este documento), la Versión B (`Docs-General/EDF_RULES_BY_STAGE.md`) y la Versión C (`Docs-General/SISBEN_V4_Synthesis_StateCalculation_Levantamiento.md`), ninguna de las dos últimas coincidiendo con el código ni entre sí.

**Decisión del usuario, 2026-10-01**: se adopta la **Versión A** (la de este documento, §5.1) como la correcta para toda la iniciativa. Los documentos `EDF_RULES_BY_STAGE.md`, `SISBEN_V4_..._Levantamiento.md` y `Transformaciones/04_discovery_y_calidad/criterios_calidad_por_etapa.md` fueron corregidos con tachado y nota de corrección explícita (no se borró el contenido original). Registro completo de esta decisión en `Memory.md`, entrada "Resolución de conflicto documental: nomenclatura y mecanismo de los Field Handlers LFP/LAP/EUC".

**Importante — lo que esta decisión NO resuelve**: corregir el nombre no implica que la asignación campo→handler propuesta en `SISBEN_V4_..._Levantamiento.md` §7.3 bajo los nombres antiguos siga siendo válida bajo los nombres/mecanismos correctos. En particular, la conclusión "LFP no aplica a SISBEN" se apoyaba en una definición de LFP que ya no es la vigente — queda marcada `[A:sin-clasificar]`, pendiente de revisión funcional, no de inferencia de este documento. Pendientes #9 y #10 agregados a este documento en consecuencia.

### 5.2 Diccionario de reglas de consistencia (`consistency-checks.json`)

Raíz: `{"referenceCache": [...], "checks": [...]}`.

`referenceCache[]`: `{name, predicates: [campo1, campo2, ...]}` — define grupos de cacheo de eventos de referencia por igualdad de esos campos.

`checks[]`, esquema completo (`domain/ConsistencyCheckDictionary.scala`):

```
checkName: String                              (obligatorio)
className: String = null                       (FQCN de ConsistencyCheckProcessor custom)
isNonPeriodic: Boolean = false
shouldSynthesizeSubjectEvent: Boolean = false
synthesisType: String = "withinDataSource"
isEnabled: Boolean = true
triggerCondition: String                        (DSL de disparo — ver §6)
withReferenceEvents: String                     (DSL de referencia — ver §6)
check: String = null                            (DSL de check, expresión única — ver §6)
shouldFilterEventOnError: String = null
shouldFilterFieldOnError: String = null
filterFieldsOnError: List[String] = []
consistencyInfo: ConsistencyInfo = null         ({code, subCode, level})
referenceCache: String = null                   (nombre de un referenceCache[] declarado arriba)
checkCategory: String = null                    (usado por disabledCategories del .conf)
groupedChecks: List[CheckWithInfo] = []         (sub-checks encadenados, cada uno con su propio check/className/shouldFilterEventOnError/consistencyInfo)
```

**Hallazgo verificado**: el campo `level` dentro de `consistencyInfo` acepta tanto un literal fijo como una expresión DSL evaluada dinámicamente vía lookup externo (`ConsistencyInfoParser`), confirmado en el fixture real con la expresión `"first.external.consistencyfiltertable1.filter(errorcode == errorCode5).select(errorvalue)"`. **Regla para el Skill: no asumir que `level` es siempre un literal simple.** `[C]`

### 5.3 Diccionario de reglas custom de filtrado/visibilidad (`custom-rules.json`)

**Tercer diccionario, distinto y paralelo a los dos anteriores** — hallazgo no documentado en análisis previos de esta iniciativa. Esquema: array plano, cada elemento con `name`, `description`, `check` (DSL **distinto** al de consistencia: sintaxis `this.campo`, `$campo`, `mostRecent($campo.asBoolean)`, `xor(...)`, comparación de periodos `asPeriod`), `action` (`"hide"` observado en el fixture), `fields` (lista opcional de campos afectados), `precedence` (int, orden de aplicación). Procesado por `standalone/custom/CustomRuleChecker.scala` + `CustomRuleCheckParser.scala` — no se profundizó en su mecanismo de evaluación interno. `[C]` para la existencia y esquema; `[PM]` para el detalle de evaluación.

El fixture local usa `custom-rules-empty.json` (0 bytes) como default activo, confirmando que esta funcionalidad está deshabilitada salvo activación explícita de un archivo real. `[C]`

## 6. El algoritmo de reconciliación — dos motores, uno activo

### 6.1 Advertencia verificada: `EventSynthesizer` (la clase) no tiene invocación activa confirmada `[C]`

Existen **dos motores de síntesis** en el código: la clase `EventSynthesizer` (con un bucle `@tailrec` documentado extensamente en comentarios de cabecera) y el objeto `IncrementalEventSynthesizer` (en el mismo archivo, `domain/EventSynthesizer.scala`).

**Verificado en esta sesión con `grep` directo sobre todo el repo**: no existe ninguna invocación `new EventSynthesizer(...)` en ningún archivo `.scala` del repositorio. El objeto `IncrementalEventSynthesizer.synthesize(...)` sí se invoca activamente, tanto en `standalone/consistencycheck/ConsistencyCheckOps.scala` (el orquestador batch) como en `engines/HighFrequencyEventSynthesizer.scala` (ruta de alta frecuencia/tiempo real).

**Regla para el Skill: `IncrementalEventSynthesizer`, no `EventSynthesizer`, es el motor de reconciliación real.** La clase `EventSynthesizer` se documenta aquí porque sus comentarios de cabecera son la mejor fuente de intención de diseño disponible, pero no debe tratarse como código activo. `[C]`

### 6.2 Resolución de campo por `IncrementalEventSynthesizer.synthesize`

Firma: `synthesize(tce: TaggedCuratedEventWithMap, p: SyntheticEvent, fields: Array[DictionaryFieldInternal], euc: EUC, lap: LAP): SyntheticEvent`. Por cada campo del GDD:

```
EUC   → euc.get(...)                                                    (valor de la caché EUC, no del evento actual)
LAP   → lap.get(...) SOLO SI p.asOfDate <= tce.asOfDate                 (el evento de referencia debe ser anterior o igual)
LFP con synthesizer custom → f.synthesizer.get.performFieldSynthesis(...)
LFP sin synthesizer        → tce.curatedRecordJson.get(f.name)          (el evento actual gana — "último evento pisa al anterior")
```

Añade el campo de sistema `firstEventDate` (desde `euc.getFirstEventDate`) al resultado. `[C]`, verificado por lectura directa.

### 6.3 El verdadero ciclo de retroalimentación: `ConsistencyCheckOps.performChecks`

El ciclo check↔synthesize que `Hallazgos-Symthesis.md` describe como entrelazado **no vive dentro de `EventSynthesizer`/`IncrementalEventSynthesizer`** (que son funciones puras de resolución de un solo evento), sino en el orquestador `ConsistencyCheckOps.performChecks`:

1. Se agrupan los eventos por periodo (`HCUtil.groupEventsByPeriod`), **después** de aplicar los checks no periódicos.
2. Por cada periodo, se ordenan los eventos del periodo por `(asOfDate, receivedDate)`.
3. Por cada evento del periodo, en ese orden: se evalúan los checks periódicos contra `syntheticEvents` (acumulado entre periodos) + `partiallySynthesizedEvents` (del mismo periodo, en construcción).
4. Si el evento no se filtra completo: se actualizan `euc`/`lap` con ese evento (`euc.update(subjectEvent)` / `lap.update(subjectEvent)`), y se genera un evento sintético parcial con `IncrementalEventSynthesizer.synthesize`, indexado por suscriptor+relación. **Ese evento sintético parcial se convierte inmediatamente en candidato de referencia para el siguiente evento del mismo periodo** — este es el ciclo de retroalimentación real.
5. Al cerrar el periodo, los eventos sintéticos parciales se añaden a la lista acumulada entre periodos.

`[C]`, verificado por lectura directa de `standalone/consistencycheck/ConsistencyCheckOps.scala` (método `performChecks`, ~1090 líneas leídas).

### 6.4 Precondición no verificada por el código: orden de entrada

El comentario de cabecera de `EventSynthesizer` documenta que la lista de eventos debe llegar **ya ordenada por fecha de recepción** — la clase no reordena internamente. Esta precondición se traslada al llamador real (`ConsistencyCheckOps`, paso 2 de §6.3, que sí ordena explícitamente por `(asOfDate, receivedDate)` antes de procesar). `[C]` para el ordenamiento confirmado en el motor activo; la cita original de la precondición proviene de la clase no activa (`EventSynthesizer`), por lo que se registra con esa salvedad.

## 7. Punto de extensión `SynthesisProcessor`

Contrato (`domain/SynthesisProcessor.scala`, completo):

```scala
abstract class SynthesisProcessor extends Serializable {
  // Abstracto — a implementar:
  def performFieldSynthesis(fieldName: String, cached: Option[JsonElement], current: Option[JsonElement]): JsonElement

  // Concreto, puente de conversión:
  def performFieldSynthesis(fieldName: String, cached: Option[Any], current: Option[Any]): Any
}
```

**Hallazgo verificado, relevante para generación de código**: el puente entre el overload de `Any` y el de `JsonElement` **no es un cast directo** — convierte vía `Map[String,Any]` → JSON (serialización completa con `JsonUtils`) → `JsonElement`, y de vuelta. El método base asume que el valor de entrada es un `Map[String,Any]` (`.asInstanceOf[Map[String,Any]]`). **El valor que llega al override de `JsonElement` ya pasó por una vuelta completa de serialización, no es el objeto original en memoria.** `[C]`

Carga por reflexión (`DictionaryFieldDeser.deserialize`, al parsear el campo `synthesizer` del GDD): `Class.forName(nombre).newInstance.asInstanceOf[SynthesisProcessor]` — **constructor sin argumentos**, a diferencia de `ConsistencyCheckProcessor` (§8), que recibe un contexto. Si la clase no existe, se captura `ClassNotFoundException` y se asigna `None` **sin loguear el error** — fail-silent confirmado. `[C]`

Ejemplo de referencia (`extension/SyntheticFieldCustomProcessor.scala`): un solo processor discrimina por `fieldName` con ramas `if/else` para atender tres campos distintos del GDD simultáneamente (ver §5.1).

## 8. Punto de extensión `ConsistencyCheckProcessor`

Contrato (`standalone/consistencycheck/ConsistencyCheckProcessor.scala`, completo, 44 líneas):

```scala
abstract class ConsistencyCheckProcessor(ctx: ConsistencyCheckProcessorContext) extends OxygenProcessor {
  override def lookupService: OxygenLookup = ctx.lookup

  // Con implementación default (delegan a la variante singular @deprecated):
  def performChecks(event: TaggedCuratedEventWithMap, syntheticEvents: List[SyntheticEvent]): (List[ConsistencyInfo], Boolean)
  def performNonPeriodicChecks(event: TaggedCuratedEventWithMap, withEvents: List[TaggedCuratedEventWithMap]): (List[ConsistencyInfo], Boolean)

  // @deprecated, sin implementación real:
  @deprecated def performCheck(event: TaggedCuratedEventWithMap, syntheticEvents: List[SyntheticEvent]): ConsistencyInfo
  @deprecated def performNonPeriodicCheck(event: TaggedCuratedEventWithMap, withEvents: List[TaggedCuratedEventWithMap]): ConsistencyInfo
}
```

**Regla de contrato**: `performChecks`/`performNonPeriodicChecks` sí tienen implementación default (delegan a la variante singular, marcada `@deprecated`). El patrón recomendado es sobrescribir el par plural directamente, como hace el ejemplo de referencia. `[C]`

Instanciación por reflexión con **constructor de un argumento** (`MigrationProcessorContext`-análogo), a diferencia de `SynthesisProcessor` (sin argumentos) — misma asimetría ya observada entre `IdentityMatcher`/`ClusterSplitter` (sin args) y `RelationshipMigrationProcessor` (con contexto) en Pinning (`logica-pinning.md` §7.6). `[C]`

Ejemplo de referencia (`extension/CreditLimitChangeCheck.scala`, completo): compara `creditLimit` del evento actual contra el del periodo anterior; si el incremento supera 1.5×, genera `ConsistencyInfo`. Usa `Util.getLookup(...)` para consultar una tabla externa dentro de la lógica del check. Resultado construido con una subclase propia de `ConsistencyInfo` (`CreditLimitChangeInfo(code, subCode, level, detail, period)`). **Contiene el mismo antipatrón `logger.error` para debug** ya señalado como transversal al framework. `[C]`

## 9. El DSL de consistencia — gramática verificada

### 9.1 Tres sub-lenguajes independientes, no uno

| Sub-lenguaje | Evalúa | Recibe | Archivo |
|---|---|---|---|
| **Check** | la expresión del campo `check` | evento sujeto + eventos de referencia | `standalone/parser/ast/CheckLexer.scala`, `CheckAST.scala` |
| **Trigger** | el campo `triggerCondition` | **solo el evento sujeto**, nunca eventos de referencia | `TriggerAST`/`TriggerLexer`/`TriggerParser` |
| **Reference** | el campo `withReferenceEvents` | predicado de filtro sobre pares (sujeto, candidato) | `ReferenceAST`/`ReferenceLexer`/`ReferenceParser` |

**Regla operable confirmada**: si el trigger evalúa falso, el check completo se omite **sin costo** de evaluar `withReferenceEvents` ni `check` — orden de evaluación: trigger primero, siempre. `[C]`, verificado en `ConsistencyCheckOps.performCheck`.

### 9.2 Gramática del sub-lenguaje Check

- Comparación: `==`, `!=`, `>`, `>=`, `<`, `<=`.
- Lógicos: `&&`, `||`.
- Pertenencia: `in(v1, v2, ...)`, `notIn(v1, v2, ...)`.
- Prefijos de alcance: `this.$campo` (evento sujeto), `that.$campo` (evento de referencia), `this.global.$campo` / `that.global.$campo` (variantes "globales" — diferencia semántica exacta con la variante no-global no verificada, `[PM]`).
- Funciones: `mostRecent(that)`, `first(that)`, `last(that)`, `anyOne(that)`, `referenceEventCount`, `willBeMigrated == true/false`, `prevPeriod`, `$SHOULD_ACCEPT_STATE(...)`.
- Lookup externo: `external.<tableName>.filter(campo==valor).select(campo)`, con variantes de nodo AST distintas si el valor de comparación viene directo o de una función como `mostRecent`.

Cada nodo AST implementa **doble despacho obligatorio**: `eval(...)` (caso periódico, recibe `List[SyntheticEvent]` como referencia) y `evalForNonPeriodic(...)` (caso no periódico, recibe `List[TaggedCuratedEventWithMap]`) — la diferencia de tipo es la razón estructural de la duplicación de jerarquías de evaluación en todo el motor, no una conveniencia de nombres. `[C]`

### 9.3 Periódico vs. no periódico

| | Checks no periódicos (`isNonPeriodic=true`) | Checks periódicos (default) |
|---|---|---|
| Cuándo se ejecutan | Una sola vez por evento, **antes** de agrupar por periodo | Dentro del bucle por periodo |
| Contra qué comparan | `List[TaggedCuratedEventWithMap]` (eventos crudos, cualquier periodo, filtrados por `referenceCache` si aplica) | `List[SyntheticEvent]` (ya sintetizados de periodos anteriores + parciales del periodo actual) |

`[C]`, verificado por lectura directa.

### 9.4 Corte temprano en `groupedChecks`

Tanto a nivel de "qué check de la lista se ejecuta" (`performAllChecks`) como de "qué sub-check dentro de un `groupedChecks` se ejecuta" (`evaluateCheckForPeriodic`/`evaluateCheckForNonPeriodic`), el motor usa `scala.util.control.Breaks.{break, breakable}`: en el momento en que un check/sub-check devuelve `shouldFilterEventOnError = true`, se corta la evaluación de los restantes de esa lista mediante excepción de control real, no una bandera comprobada en cada iteración. El resultado acumulado hasta el punto de corte se conserva. `[C]`, verificado por lectura directa.

## 10. Severidad — confirmado con cita de código que nunca bloquea el pipeline

```scala
if (shouldFilterEventOnError) (List.empty, shouldFilterEventOnError)                      // excluye el EVENTO completo
else if (shouldFilterFieldOnError && filterFieldOnError.nonEmpty) (filterFieldOnError, false)  // excluye solo CAMPOS
else (List.empty, false)   // solo se registra el ConsistencyInfo, nada se filtra
```

(`ConsistencyCheckOps.shouldFilter`/`shouldFilterForListOfChecks`)

- `shouldFilterEventOnError`/`shouldFilterFieldOnError` son booleanos **fijos por regla**, resueltos al cargar el diccionario, no evaluados dinámicamente campo a campo en tiempo de ejecución.
- Si `shouldFilterEventOnError` dispara: el evento se excluye de la síntesis de ese periodo, y si `stateCheckEnabled`, se notifica a `StateCheck.updateAfterFilteringEvent`.
- Si solo se filtran campos: el evento sigue participando en la síntesis sin esos campos (`TaggedCuratedEventWithMap.excludeField`).
- **En ningún punto del motor hay una ruta que aborte el job de Spark o lance una excepción no capturada por una violación de regla de negocio.** La unidad mínima de penalización es el campo; la máxima, el evento individual; nunca el batch/job completo.

`[C]`, confirma textualmente lo que `problema-synthesis.md` §2.2 describe como "severidad de advertencia", con el matiz operativo exacto.

## 11. Archivos de entrada y salida

| Artefacto | Patrón / ubicación | Formato | Notas |
|---|---|---|---|
| **Pinned-input (entrada)** | carpetas `<timestamp>-pin-<tipo>` bajo `pinnedDir` | JSON-lines, `part-NNNNN-<uuid>-c000.json` + `_SUCCESS` | De los tipos disponibles (`-pin-EID`, `-pin-hc`, `-pin-identity-pin-map`, `-pin-nearNeighbor`, `-pin-stampedIdentity`), **Synthesis consume `-pin-hc`**, confirmado por `oxygen.stage.pinning.paths.pinnedDir` apuntando a esa estructura |
| `-pin-hc` (estructura real) | — | `{"pin": <long>, "events": [...]}` por línea, cada evento con `asOfDate`, `eventData.{...}`, `eventId`, `eventType`, `lineage.{...}`, `minFrequency`, `payload.data`, `subscriber`, `subscriberRelation`, `subscriberRelationStartDate/EndDate` | Confirmado con fixture real `example/pinned-input/.../part-00000-...c000.json` |
| **Synthesized (salida)** | `synthesized/<timestamp>/part-...json` + `_SUCCESS` | JSON-lines, `{"pin": <long>, "synthesizedEvents": [...]}` | Cada evento con `asOfPeriod.{epochDateTime,id,unit.name}`, `curatedRecordJson` (campos reconciliados + `firstEventDate`), `dataSource` fijo `"Oxygen"` (re-etiquetado, no la fuente original), `frequency`, `migrationHistory`, `parentEventIds` (trazabilidad a eventId originales), `subscriber`, `subscriberRelation` |
| **Checked / consistency-info (salida)** | `checked/<timestamp>-consistency-info/part-...avro` + `_SUCCESS` | **Avro**, no JSON | Coherente con `saveConsistencyInfoToFile=true`; contenido binario no decodificado en este análisis |
| **Lookup** | CSV planos bajo `lookupHome` | CSV | Esquema de columnas no verificado en esta sesión (no solicitado); referenciados por nombre desde `oxygen-lookup-local.conf` |

`[C]`, verificado por lectura directa de código y de los fixtures reales del repo.

### 11.1 `example/plutus/` — hueco caracterizado, no cerrado

No es una ingesta piloto documentada ni un ambiente de prueba con documentación propia. Replica la estructura de `pinned-input/` con timestamp distinto (2024 vs. 2023 del fixture principal) y datos particionados en 2 archivos en vez de 1. Agrega una carpeta `<timestamp>-doesMatchProfileData/` que no corresponde a ningún tipo de artefacto documentado en `README.MD` ni en `consistency-application.conf`. Ninguna referencia a "plutus" en código fuente, `.conf` ni `.md` del repo. **Conclusión: con alta probabilidad es un segundo dataset de prueba manual dejado en el repo, sin trazabilidad a documentación oficial.** `[PM]` — no se puede cerrar sin preguntar al equipo.

## 12. Validaciones de entrada y comportamientos fail-fast / fail-silent

| Comportamiento | Tipo | Detalle |
|---|---|---|
| GDD sin `version` reconocida | **Fail-fast** | `throw OxygenException("Domain Dictionary version not defined...")`, con mensaje y URL de documentación |
| `handler` del GDD con valor fuera de EUC/LAP/LFP | **Fail-fast (indirecto)** | `assert(...)` en el constructor de `DictionaryFieldInternal` — un typo real en el JSON (`"Lap"` en vez de `"LAP"`) rompe con `AssertionError`, no con un mensaje de negocio claro |
| String vacío/nulo a hashear (`HashFunctions.getSHA1Hash`) | **Fail-fast** | `throw OxygenException("String to hash cannot be null or empty")` |
| **Excepción durante evaluación de un check individual** (`ConsistencyCheckOps.performCheck`/`performNonPeriodicCheck`) | **Fail-silent, verificado por grep directo** | `catch { case e: Exception => logger.trace(...) }` (líneas confirmadas en el código real) — el check se omite como si no hubiera encontrado inconsistencia, **sin reportarse como fallo**; el detalle solo queda en nivel `trace`, típicamente deshabilitado en producción. Un error de división por cero o un campo ausente en una expresión DSL mal escrita puede pasar desapercibido indefinidamente |
| Fallo de parseo del DSL (`CheckLexer`/parsers hermanos, `NoSuccess`) | **Fail-silent** | `logger.error` con línea/columna, pero el AST resultante queda `None` — ese check nunca se evalúa, sin excepción que detenga nada |
| `SynthesisProcessor` custom no encontrado por reflexión | **Fail-silent** | `ClassNotFoundException` capturada, se asigna `None` **sin loguear el error** — más silencioso aún que el fallo de parseo del DSL |

`[C]`, verificado por lectura directa; el caso de `ConsistencyCheckOps.performCheck` fue confirmado con `grep` directo en esta sesión (líneas 360 y 567 de `ConsistencyCheckOps.scala`).

## 13. Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Semántica exacta de LFP/LAP/EUC no confirmada por el dueño de dominio | `[A:OQ-TR-3]` (heredado de `02-lexico-edf.md`, igual que en `problema-synthesis.md`) |
| 2 | Diferencia semántica exacta entre `this.$campo`/`that.$campo` y sus variantes `.global.` en el DSL de Check | `[PM]` |
| 3 | Mecanismo de evaluación interno del diccionario de reglas custom de filtrado (`custom-rules.json`, §5.3) — esquema confirmado, evaluación no profundizada | `[PM]` |
| 4 | Si el proyecto compila con el import roto de `SynthesisProcessor` en `DomainDictionaryInternal.scala` (§2.2) — podría existir alias o reexportación no detectada | `[A:sin-clasificar]` |
| 5 | Diferencias exactas entre GDD versión 1 (deprecated) y versión 2 | `[PM]` |
| 6 | Esquema de columnas de los CSV de lookup — no verificado, no solicitado en esta pasada | `[PM]` |
| 7 | Naturaleza real de `example/plutus/` y de la carpeta `doesMatchProfileData` dentro de él | `[PM]` |
| 8 | Confirmar si `EventSynthesizer` (la clase, no el objeto `IncrementalEventSynthesizer`) es código completamente muerto o si existe alguna invocación vía reflexión/configuración no detectable por `grep` de texto | `[A:sin-clasificar]` |
| 9 | Mapeo campo→handler (LFP/LAP/EUC) de los 25/22 campos de SISBEN, bajo la definición correcta (Versión A, §5.1.1) — ninguna fuente lo confirma campo por campo; `SISBEN_V4_..._Levantamiento.md` §7.3 solo propone 2 fechas como EUC y "el resto" como LAP en bloque | `[A:sin-clasificar]` |
| 10 | Revalidación funcional de si LFP realmente no aplica a SISBEN, bajo la definición correcta de LFP (Versión A) — la razón original de descarte ya no es válida | `[A:sin-clasificar]` |

## Pie de trazabilidad

- Problema de negocio y las dos funciones separadas (reconciliación/consistencia): `problema-synthesis.md` §2.2; `README.MD` §Introduction; `domain/EventSynthesizer.scala` (comentario de cabecera).
- Discrepancia de nombres y bugs de paquete: `Hallazgos-Symthesis.md`; verificación directa por `grep` en esta sesión sobre `domain/SynthesisProcessor.scala`, `domain/DomainDictionaryInternal.scala`, `extension/SyntheticFieldCustomProcessor.scala`.
- Claves de configuración: `src/main/resources/local/consistency-application.conf`, `oxygen-config-default-local.conf`, `oxygen-lookup-local.conf`, `spark-local.conf`, `env-local.conf`.
- Los tres diccionarios: `domaindictionary-local.json`; `consistency-checks-local.json`; `custom-rules1-local.json` / `custom-rules-empty-local.json`; `domain/ConsistencyCheckDictionary.scala`; `standalone/consistencycheck/TaggedConsistencyCheckDictionary.scala`; `domain/DomainDictionaryInternal.scala`.
- Algoritmo de reconciliación: `domain/EventSynthesizer.scala` (completo); verificación de uso activo por `grep` directo en esta sesión (`new EventSynthesizer` sin resultados en todo el repo); `standalone/consistencycheck/ConsistencyCheckOps.scala` (método `performChecks`, ~1090 líneas); `engines/HighFrequencyEventSynthesizer.scala`.
- Puntos de extensión: `domain/SynthesisProcessor.scala`; `extension/SyntheticFieldCustomProcessor.scala`; `standalone/consistencycheck/ConsistencyCheckProcessor.scala`; `extension/CreditLimitChangeCheck.scala`; `extension/CreditLimitChangeInfo.scala`.
- DSL de consistencia: `standalone/parser/ast/CheckLexer.scala`, `CheckAST.scala` (~601 de un archivo más largo); `ConsistencyCheckOps.scala` (`evaluateTriggerCondition`, `performAllChecks`, `evaluateCheckForPeriodic`/`evaluateCheckForNonPeriodic`).
- Severidad y fail-fast/fail-silent: `standalone/consistencycheck/ConsistencyCheckOps.scala` (`shouldFilter`, `performCheck`/`performNonPeriodicCheck` con `catch { case e: Exception => logger.trace(...) }`, verificado por `grep` directo en esta sesión); `domain/DomainDictionaryInternal.scala`; `util/HashFunctions.scala` (no releído directamente, citado del análisis delegado).
- Archivos de entrada/salida: `example/pinned-input/`, `example/consistency-output/`, `example/lookup/`, `example/plutus/`, `example/default-output-structure.txt`.
- Despliegue: `aws/conf/local/`, `README.MD` §RUN, §RUN ON AWS; `advanced.md` §Synthesis output path options.
- Léxico y pipeline: `.kiro/steering/02-lexico-edf.md` §2, §5, §7; `.kiro/steering/03-pipeline.md` §3.
- Documentos previos de la iniciativa consultados para no duplicar contenido (`05-convenciones.md` §7): `Diseño/problema-synthesis.md`; `Hallazgos-Symthesis.md`.
- Resolución del conflicto de nomenclatura LFP/LAP/EUC (§5.1.1): `Memory.md`; `Docs-General/EDF_RULES_BY_STAGE.md` (corregido); `Docs-General/SISBEN_V4_Synthesis_StateCalculation_Levantamiento.md` §7.3 (corregido); `Transformaciones/04_discovery_y_calidad/criterios_calidad_por_etapa.md` (corregido).
- No se leyó ni citó `settings-artifactory.xml` (`01-restricciones-principios.md` §4).
- Última verificación de referencias: 2026-10-01.
