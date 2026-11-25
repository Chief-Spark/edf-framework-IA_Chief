---
inclusion: manual
---

# Lógica de negocio operable — etapa State Calculation

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-10-01 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; formato según `05-convenciones.md`; metodología según `04-reglas-analisis.md` (tipo T3, sin skill de análisis especificado — `[PM]`) |
| Naturaleza | Análisis técnico derivado de código real (`oxygen-statecalculation`), destinado a servir de insumo YAML/Markdown intermedio para un Skill generador de artefactos de configuración (`.csv`, `.json`, `.config`/`.conf`) de esta etapa. No cierra preguntas abiertas ni fija supuestos |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

Describir, con precisión operable y cita verificable, toda la lógica de negocio de la etapa State Calculation (etapa 6 del pipeline, `02-lexico-edf.md` §2) tal como existe en el código real de `oxygen-statecalculation`: claves de configuración, estructura exacta de los JSON de Foundation y Calculation, el algoritmo de las dos fases encadenadas, el motor Nashorn, los puntos de extensión, el mecanismo de histórico, y los comportamientos fail-fast verificados.

**Cubre:** todo lo que un Skill necesita conocer para proponer archivos de configuración (`.conf`/HOCON, `.json`) correctos para ejecutar esta etapa — claves, esquema exacto de los JSON de Foundation/Calculation, contratos de extensión (`FoundationStateEnrichmentProcessor`, `StateCalcProcessor`, los tres patrones de reflexión), y los casos de borde conocidos por lectura directa del código, incluida una discrepancia real sin resolver en el formato de agrupación.

**No cubre:** el problema de negocio en términos generales ni la clasificación de complejidad Spark (eso vive en `problema-stateCalculation.md`, ya aceptado), el contrato de frontera formal con Synthesis/XPM (`07-contratos-frontera.md`), ni la generación de código/configuración en sí.

Este documento se basa en lectura estática del repositorio `oxygen-statecalculation`. No se ejecutó código — en particular, **la discrepancia de §5.4 no pudo verificarse empíricamente** porque ejecutar la deserialización Jackson está fuera de lo permitido (`01-restricciones-principios.md` §2.2, X7). No se abrió, leyó ni citó `settings-artifactory.xml` (prohibido por `01-restricciones-principios.md` §4). Ningún dato de este documento proviene de personas reales; los archivos de ejemplo citados son fixtures sintéticos del propio repositorio InnerSource.

## 2. Advertencia estructural: nombres y huecos ya conocidos `[C]`

Heredado y confirmado de `problema-stateCalculation.md` §2 (no se repite el detalle, se referencia): el punto de entrada real es `StateCalcApp`, no `StateCalcColombiaApp` como documenta el steering; el enricher por defecto es `DefaultFoundationStateEnrichmentProcessor` (no-op); y **no existe en este repo ningún motor de calidad de datos de 4 dimensiones** (completitud/validez/unicidad/consistencia). Esta ausencia se **reconfirmó en esta sesión** con una segunda búsqueda exhaustiva (`completeness|validity|uniqueness|consistency|qualityThreshold|dataQuality`, sin coincidencias relevantes más allá del nombre de la etapa anterior y de constantes de un dominio brasileño no relacionado). Se mantiene `[A:sin-clasificar]`, no se promueve a `[C]` de ausencia definitiva sin decisión humana.

## 3. El problema que resuelve (resumen operable)

State Calculation toma los eventos sintetizados (uno por fuente/cuenta/periodo) y produce el **Behavioral Profile** consolidado por sección temática que alimenta el XPM, en dos fases encadenadas (`problema-stateCalculation.md` §3.2, `[C]`):

1. **Foundation**: enriquecimiento fila por fila — un registro homogéneo por evento sintético, con campos resueltos según su tipo.
2. **Calculation**: agregación tipo SQL sobre la lista de registros Foundation de un mismo pin — N registros → 1 estado por sección.

## 4. Claves de configuración

### 4.1 Claves propias de StateCalc (`statecalc-application-local.conf`, bajo `oxygen.stage.stateCalculation` salvo donde se indique)

| Clave | Valor en el ejemplo local | Efecto |
|---|---|---|
| `paths.statecalcDir` | ruta local | Directorio de salida |
| `deltaModeAccountLevel` | `false` | Modo delta a nivel de cuenta |
| `doesComputeXpm` | `true` | Si calcula el XPM final |
| `doesArchive` | `true` | Si archiva la generación anterior |
| `foundationWithAlias` / `aliasEnabled` | `true` | Habilita resolución de alias de campos en Foundation — un campo con `aliases` escribe también bajo cada alias en el registro homogéneo |
| `eventTrackingEnabled` | `true` | — |
| `datasetFormat.input/output` | `text` | Formato de lectura/escritura |
| `paths.foundationConfigurations` | `["local/statecalc-foundation-local.json"]` | **Lista** de rutas al JSON de Foundation — pueden combinarse varios archivos |
| `paths.calculationConfigurations` | `["local/statecalc-calculation-local.json"]` | **Lista** de rutas al JSON de Calculation |
| `appenders` | comentado por defecto | Lista de bloques `{name, processor}` — punto de registro de `StateCalcProcessor` (ver §7.2), consumido como `oxygen.stage.stateCalc.appenders` |
| `oxygen.features.statecalc.outputFoundation` | `true` | Si se persiste el resultado de Foundation (impacta performance, advertencia explícita en el comentario del `.conf`) |
| `oxygen.features.statecalc.outputCalculation` | `true` | Si se persiste el resultado de Calculation |
| `oxygen.features.statecalc.additionalOutputFoundation` | `false` | — |
| `oxygen.features.statecalc.attachHistorcData` | `false` | **Typo real confirmado en el código** (`attachHistorcData`, no `attachHistoricData`) — controla `STATECALC_ATTACH_HISTORIC_DATA`, activa `attachHistoricalData` (ver §6) |
| `oxygen.features.statecalc.performCalculationEnrichment` | `true` | — |
| `oxygen.features.statecalc.performFoundationEnrichment` | `true` | — |
| `oxygen.features.statecalc.outputAsTSV` | `false` | — |
| `oxygen.features.statecalc.pinGroupFilter` | `true` | — |

Bloque `historicalData` (comentado en el fixture, ejemplo de uso): `identifyingAttributes`, `historicalNumPeriods`, `historicalObjectName`, `historicalDataPaths` — alimenta `attachHistoricalData` (§6). `[C]`

### 4.2 Claves de plataforma relevantes (`oxygen-config-default.conf`)

Patrón de nombre de tabla HBase (usado por `XpmEventDAO`, no hardcodeado): `oxygen.application.configs.hbase.namespace`, resuelto a `namespaceWithEnvironment` = `<namespace>:<environment>`, y a `namespaceWithEnviromnmentAndGeneration` = `<namespaceWithEnvironment>_<generation.candidate>_<entity>`. El nombre de tabla XPM (`xpmEventStore`) se inyecta como parámetro al DAO, no vive en el DAO mismo (ver §10). `[C]`

Entrada que StateCalc lee: `oxygen.stage.consistencyCheck.paths.synthesizedDir` (clave de la etapa anterior, Synthesis — StateCalc la reutiliza para ubicar su entrada). `[C]`

## 5. Estructura exacta de los JSON de configuración

### 5.1 Foundation (`statecalc-foundation-local.json`)

Raíz: `{"domain": "<nombre>", "fields": [...]}`. Cada elemento de `fields` (DTO `FoundationField`, ver §5.3):

| Campo | Tipo | Obligatorio | Default | Efecto |
|---|---|---|---|---|
| `name` | string | sí | — | nombre del campo en el registro homogéneo |
| `description` | string | no | — | — |
| `aliases` | lista de strings | no | — | nombres alternativos; si `aliasEnabled=true`, el valor también se escribe bajo cada alias |
| `dataType` | enum | no | `STRING` | tipo del valor resuelto (`INTEGER`, `LONG`, `DOUBLE`, `BOOLEAN` confirmados en el fixture) |
| `type` | enum (`FieldType`) | no | `DERIVED` | `DERIVED` \| `CALCULATED` \| `CUSTOM` \| `CUSTOM_WITH_HISTORY` \| `UNKNOWN` (deprecated) |
| `value` | string (JS) | solo si `type=CALCULATED` | — | cuerpo del script Nashorn (ver §5.5) |
| `defaultValue` | string | no | — | usado si `DERIVED` no encuentra valor en el evento |
| `nullable` | boolean | no | `true` | — |
| `handler` | objeto `{class, lookups}` | solo si `CUSTOM`/`CUSTOM_WITH_HISTORY` | — | FQCN cargado por reflexión (ver §7.3) |

Ejemplos reales transcritos del fixture:
```json
{ "name": "subscriber", "description": "Subscriber code", "aliases": ["subscriberCode", "counterpartyCode"] }
{ "name": "numberOfChecksReturned", "description": "Number of checks returned", "dataType": "INTEGER" }
{ "name": "isNovedat", "description": "is Novedat", "defaultValue": "false", "dataType": "BOOLEAN" }
{ "name": "standardizedIdType", "description": "The Idtype Schema", "dataType": "INTEGER" }
{ "name": "personIdType", "dataType": "INTEGER", "type": "CALCULATED", "value": "return field.standardizedIdType" }
```

El CALCULATED `personIdType` funciona porque el fixture declara el DERIVED `standardizedIdType` (`statecalc-foundation-local.json:369-379`): en un CALCULATED, `field` es el **registro Foundation** (`FoundationStateCalculator.scala:52-63`), no el evento curado, y un DERIVED se lee por su `name` (`FoundationFunctions.scala:24-35`). Un `field.X` sin un DERIVED (o un campo inyectado como `pin`) que lo ponga en el registro vale `null` en silencio (HF-74, HF-75). `[C]`

El ejemplo real más complejo de `CALCULATED` (`typeOfData`) encadena múltiples `if/else if` dentro del script JS para resolver un código numérico según banderas booleanas del evento (`isCredit`, `isExpress`, `isCheckingAndSaving`, ..., `isDian`) — confirma que el DSL de Nashorn soporta control de flujo completo, no solo expresiones de una línea. `[C]`

### 5.2 Calculation (`statecalc-calculation-local.json`)

Raíz: `{"domain": "<nombre>", "calculations": [...]}`. Cada sección (DTO `CalculationSection`, ver §5.3):

| Campo | Tipo | Obligatorio | Default | Efecto |
|---|---|---|---|---|
| `name` | string | sí | — | se convierte en la clave de salida de la sección |
| `description` | string | no | — | — |
| `type` | string | sí | — | `AGGREGATES` \| `MOST_RECENT_VALUE` \| `DISTINCT` \| `ALL` \| `CUSTOM_ON_SECTION` \| pluggable |
| `select` | lista de strings | no | — | campos a proyectar |
| `groupBy` | lista de strings | no | — | **campo plano del DTO oficial** — ver discrepancia en §5.4 |
| `where` | string (JS) | no | — | predicado Nashorn de filtro (ver §5.6) |
| `contains` | lista de `{name, section}` | no | — | anida otra sección ya calculada (debe declararse **antes** en el JSON) |
| `transient` (JSON) → `isTransient` (DTO) | boolean | no | `false` | si `true`, se calcula y queda disponible vía `contains`, pero **no aparece en la salida final** |
| `enabled` | boolean | no | `true` | si `false`, la sección se omite por completo |
| `numberOfPeriods` | integer | no | — | limita cuántos periodos recientes se consideran |
| `return` (JSON) → `returnAs` (DTO) | enum | no | `KEY_VALUE` | `KEY_VALUE` \| `VALUE` |
| `aggregates` | lista de `{function, field, name}` | solo si `type=AGGREGATES` | — | `name` es opcional: si es `null`, el getter devuelve `field` como fallback |
| `plugin` | objeto libre (`Map<String,Object>`) | solo para tipos pluggable | — | capturado vía `@JsonAnySetter`, convertido al tipo concreto que declare el `CalculationType` pluggable |

Ejemplo real completo de `AGGREGATES` (usa `groupBy` plano):
```json
{
  "name": "AggregationOnBalanceGroupBySubSR",
  "type": "AGGREGATES",
  "select": ["subscriber", "subscriberRelation"],
  "groupBy": ["subscriber", "subscriberRelation"],
  "aggregates": [
    {"function": "SUM", "field": "balance", "name": "sum"},
    {"function": "AVERAGE", "field": "balance", "name": "avg"},
    {"function": "MIN", "field": "balance", "name": "min"}
  ]
}
```

Ejemplo real con `where` + `transient` + `return: VALUE` (usa `grouping.groupBy` anidado — ver §5.4):
```json
{
  "name": "dataSources", "type": "DISTINCT", "transient": true,
  "select": ["dataSource"], "grouping": {"groupBy": ["counterpartyIdNumber"]},
  "return": "VALUE",
  "where": "return ( field.isCredit == true || field.isCheckingAndSaving == true ) && field.counterpartyIdNumber != null"
}
```

Ejemplo real con `contains` (anidamiento):
```json
{
  "name": "counterparties", "type": "DISTINCT",
  "select": ["counterpartyIdType", "counterpartyIdNumber", "businessName"],
  "grouping": {"groupBy": ["counterpartyIdNumber"]},
  "where": "return (field.isCredit == true || ...) && field.counterpartyIdNumber != null",
  "contains": [{"name": "businessLines", "section": "businessLines"}, {"name": "dataSources", "section": "dataSources"}]
}
```

### 5.3 DTOs Jackson exactos

**`config/foundation/`**: `FieldType` (enum: `DERIVED, CALCULATED, CUSTOM_WITH_HISTORY, CUSTOM, @Deprecated UNKNOWN`); `FoundationField` (`name`, `description`, `type=DERIVED`, `value`, `handler`, `defaultValue`, `nullable=true`, `dataType=STRING`, `aliases`); `FoundationStateConfiguration` (`domain`, `fields: List<FoundationField>`).

**`config/calculation/`**: `CalculationConfiguration` (`domain` obligatorio, `calculations: List<CalculationSection>`, `references: List<ReferenceSection>`); `CalculationSection` (campos de la tabla §5.2, con setter fluido `.groupBy(List<String>)` para post-procesamiento); `AggregatesConfiguration` (`function`, `field`, `name` con fallback a `field`); `ReferenceSection` (`name`, `description`, `type`, `select`, `reference`, `where`, `enabled=true`, `numberOfPeriods`); `Contains` (`name`, `section`, `linkedBy=AUTO`); `ContainsLinkedBy` (enum, **único valor existente**: `AUTO`); `CustomHandler` (`clazz` mapeado desde JSON `"class"`, `lookups`); `GroupingClause` (`groupBy: List<String>` — **DTO independiente**, ver §5.4); `PluggableConfiguration` (mapa abierto); `CalculationReturn` (enum: `KEY_VALUE, VALUE`); `AggregateFunction` (enum Java **confirmado muerto**, cero referencias de uso real — las funciones de agregación reales se resuelven en Scala, ver §5.7).

`[C]`, verificado por lectura directa de los `.java` del repo.

### 5.4 Discrepancia real sin resolver: `groupBy` plano vs. `grouping.groupBy` anidado

**Hallazgo verificado por conteo directo en esta sesión, no solo reportado**: en `statecalc-calculation-local.json`, al menos **11 secciones** usan la forma anidada `"grouping": {"groupBy": [...]}`, y solo **1 sección** (`AggregationOnBalanceGroupBySubSR`) usa la forma plana `"groupBy": [...]`.

**El DTO oficial (`CalculationSection.java`, línea 26) solo declara el campo plano `groupBy`.** No existe ningún campo `grouping` en esa clase. Verificado por lectura directa — `grep` sobre el archivo confirma un único campo `private List<String> groupBy;`, con getter `groupBy()` y setter fluido `groupBy(List<String>)`, sin rastro de `grouping` ni de `GroupingClause` como campo embebido.

**Consecuencia no verificada empíricamente (no se puede ejecutar código, `01-restricciones-principios.md` §2.2)**: si Jackson usa su comportamiento por defecto (ignorar propiedades JSON desconocidas sin lanzar error), las ~11 secciones que usan `grouping.groupBy` **nunca activarían agrupación real** — `section.groupBy()` devolvería `null` o vacío para ellas, y `computeAggregationSection` tomaría la rama `withoutGrouping` en vez de `withGrouping`, cambiando el comportamiento de agregación de forma silenciosa y sin excepción.

**Regla dura para el Skill: usar siempre `groupBy` plano al generar nuevas secciones, nunca `grouping.groupBy`**, hasta que se confirme empíricamente (por el equipo, con una ejecución real controlada) cuál de las dos formas realmente funciona en este motor. No se elige una interpretación como correcta en este documento — se documenta la contradicción observable entre el fixture de ejemplo y el DTO Java. `[A:sin-clasificar]`

### 5.5 Mecanismo Nashorn para campos `CALCULATED` de Foundation

Confirmado por lectura directa de `FoundationFunctions.scala`:

1. `constructCalculatedFieldJsFunction` genera y compila, una sola vez por campo:
   ```js
   function foundation_calculated_<nombreCampo>(field){
     <contenido literal de "value">
   }
   ```
2. `computeCalculatedField` invoca `invokableEngine.invokeFunction("foundation_calculated_" + field.name, foundationRecord)`, pasando el registro homogéneo actual (Java `HashMap`) como único argumento, visible dentro del script como `field.xxx`.
3. Cualquier excepción se envuelve en `StateCalculationScriptExecutionException(mensaje + " - In Field : " + field.name + " of foundation config")`.

**Convención de nombre de función: `foundation_calculated_` + nombre exacto del campo.** `[C]`

### 5.6 Mecanismo Nashorn para predicados `where` de Calculation

Confirmado por lectura directa de `QueryFunctions.scala`:

1. Convención de nombre: **`aggregated_where_` + nombre exacto de la sección** (`QueryFunctions.scala:75-82`). El `name` de una sección con `where` debe servir como parte de un identificador JavaScript: con `#` (p. ej. `#sisben`), espacios u otros caracteres inválidos la compilación falla al construir el calculador (`CalculationStateCalculator.scala:50-52`) y la etapa no arranca (HF-77).
2. Se compila/evalúa una sola vez por sección que declare `where`.
3. `where(...)` filtra la lista de registros invocando la función **registro por registro**, convirtiendo el resultado JS a `Boolean` vía `.toString.toBoolean`.
4. **Si la lista filtrada queda vacía, `where` devuelve `null`** (`QueryFunctions.scala:61`): sin `groupBy` la sección vale `null`; con `groupBy` los grupos vacíos no salen y la sección es una lista vacía `[]` (`CalculationStateCalculator.scala:123-143`, `:162`). Detalle operable importante: una sección sin matches propaga `null`, lo cual el Skill y cualquier consumidor posterior debe distinguir de "sección calculada con cero resultados" de forma explícita.
5. Errores se envuelven en `StateCalculationScriptExecutionException` con el nombre de la sección.

`[C]`

**Modo Colombia (HF-77).** Con la configuración compartida de Colombia el registro Foundation de un PIN mezcla todas las fuentes y los foundation y calculation de la corrida se fusionan (`ContextUtils.scala:15-30`). El insumo declara `modo_colombia` (plantilla `yaml-skill-statecalc.yaml.tpl`): el filtro por fuente confirmado de cada sección (sin él MOST_RECENT_VALUE toma el registro más reciente de cualquier fuente, `MostRecentValue.scala:20-24`), los nombres existentes contra los que se comprobó el choque y el modo delta del conf de Colombia (`deltaMode`, `deltaModeAccountLevel`, `doesComputeXpm`: en modo delta un PIN sin cambios conserva su XPM anterior, `SparkStateCalculator.scala:187-188, 207-213, 246-271`). Detalle: `edf-statecalc/references/modo-colombia.md`.

### 5.7 Funciones de agregación disponibles (`AGGREGATES`)

Firma base: `abstract class AggregateFunction[T](name, options) { def execute(records: FoundationRecordList, fieldName: String): T }`.

| Función (`"function"` en JSON) | Implementación | Comportamiento |
|---|---|---|
| `SUM` | `Sum` | suma de valores convertidos a `BigDecimal` |
| `MIN` / `MAX` | `Min` / `Max` | mismo patrón, extremos |
| `COUNT` | `Count` | cuenta valores no nulos |
| `AVERAGE` | `Average` | `sum.divide(count, 4, RoundingMode.HALF_UP)`, usa internamente `Sum`+`Count`; **devuelve `null` si la división falla** (p.ej. `count=0`), capturado con `Try(...).getOrElse(null)` |

Los nombres de función del JSON se resuelven contra estas implementaciones Scala, **no** contra el enum Java `AggregateFunction` (confirmado muerto, §5.3). `[C]`

## 6. Algoritmo exacto de Foundation (`FoundationStateCalculator.scala`)

Al construirse: carga los campos del JSON, compila la función JS de cada campo `CALCULATED` (una vez), e instancia por reflexión los procesadores `CUSTOM`/`CUSTOM_WITH_HISTORY` (uno por nombre de campo, cacheados).

`computeFoundationState(syntheticEvents)`:

1. Filtra eventos con `isVisible == false` (los excluye).
2. **Ordena los eventos por `asOfPeriod().epochDateTime()` ascendente** — el orden temporal determina qué registros ve un campo `CUSTOM_WITH_HISTORY` como "pasado".
3. Por cada evento, construye un registro homogéneo (`HashMap[String, Any]`) con campos base fijos: `pin`, `dataSource`, `subscriber`, `subscriberRelation`, `asOfPeriod`, `parentEventIds`.
4. **Dos pasadas de resolución de campos, no una sola**:
   - **Pasada 1 — todos los campos `DERIVED`**: lee directo del `curatedRecordJson` del evento, con soporte de nombre anidado (`domain.elementName`); si no hay valor, usa `defaultValue` convertido al `dataType` declarado.
   - **Pasada 2 — todos los campos no-`DERIVED`, en el orden del JSON**: `CALCULATED` invoca el script Nashorn (§5.5); `CUSTOM_WITH_HISTORY` recibe el registro actual **y** la lista acumulada de registros Foundation ya calculados (eventos anteriores en orden temporal); `CUSTOM` recibe solo el registro actual; `UNKNOWN` lanza `StateCalculationConfigException`.
5. Cada valor se escribe en el registro con `put(nombre, valor)`; si tiene `aliases` y `aliasEnabled` está activo, también se escribe bajo cada alias.
6. Al final de todos los eventos del pin, se invoca el punto de extensión `foundationStateEnrichmentProcessor.erichFoundationState(...)` (ver §7.1).

`[C]`, verificado por lectura directa.

## 7. Algoritmo exacto de Calculation (`CalculationStateCalculator.scala`)

Al construirse: registra los `CalculationType` built-in + pluggables inyectados; para secciones con `plugin`, convierte la configuración genérica al tipo concreto vía Jackson; registra la función JS de cada sección con `where` (§5.6); pre-calcula un cache a dos niveles de agrupación (`sectionToGroupIdentifierMap`); instancia por reflexión los procesadores `CUSTOM_ON_SECTION`; **valida la configuración y lanza `StateCalculationConfigException` si hay errores** (fail-fast en construcción, antes de procesar ningún evento).

`computeAggregatedState(foundationState)`:

1. Construye un cache de agrupaciones (`groupByMasterCache`) una sola vez por combinación distinta de `groupBy`.
2. Itera **cada sección en el orden declarado en el JSON**:
   - **Con `groupBy`** (`withGrouping`): toma los registros ya agrupados, aplica `where` + filtro de `numberOfPeriods` **por grupo**, descarta grupos vacíos tras el filtro, resuelve el `CalculationType` por `section.type.toUpperCase` (si no existe, `UnsupportedOperationException` con mensaje que lista los tipos soportados), llama `aggregationType.computeByGroup(...)`.
   - **Sin `groupBy`** (`withoutGrouping`): aplica `where` + `numberOfPeriods` sobre toda la lista; si el tipo es `CUSTOM_ON_SECTION`, delega al processor custom; si no, resuelve el `CalculationType` normal.
3. El resultado de cada sección se acumula en un mapa (clave = nombre de sección) **y** en un cache que las secciones posteriores pueden consultar vía `contains` — por eso las secciones referenciadas por `contains` deben declararse **antes** en el JSON (el procesamiento es secuencial, el cache solo tiene lo ya calculado).
4. Al final, las secciones `transient=true` se filtran de la salida final (pero siguen disponibles para `contains` durante el procesamiento).
5. Si está habilitado, agrega `parentEventIds` = unión de todos los `parentEventIds` de los registros Foundation de ese pin.

`[C]`, verificado por lectura directa.

## 8. `attachHistoricalData` (`StateCalcAddOns.scala`)

Activado solo si el flag `attachHistorcData` (typo real, §4.1) está en `true`.

1. Convierte el XPM calculado a `DataFrame` vía JSON intermedio.
2. Lee configuración: `historicalObjectName`, `historicalDataPaths` (lista de rutas JSON), `historicalNumPeriods`, `identifyingAttributes` (nombres de campo, potencialmente anidados `domain.campo`).
3. Si hay rutas históricas: carga el JSON histórico; si `historicalNumPeriods > 0`, filtra por periodo (`Period.monthlyPeriodFromEpochDateTime(now).minus(historicalNumPeriods)`).
4. **UDF `checkValue`**: compara un array contra un valor escalar (`array.contains(value)`).
5. **Condición de join dinámica**: por cada nombre en `identifyingAttributes` (toma el último segmento tras el `.`), construye `checkValue(left(att), right(att))`, y combina todas con `reduce(_ && _)` — un AND de N condiciones generadas dinámicamente según cuántos atributos se configuren.
6. `left_outer join`, selecciona `pin` + columnas identificadas, **re-anida por pin** (`groupBy("pin").agg(collect_set(struct(...)))`), persistido en `MEMORY_AND_DISK_SER`.
7. Segundo join de vuelta al XPM original por `pin`, `left_outer`.
8. Convierte de vuelta a XPM.

`[C]`, verificado por lectura directa.

## 9. Puntos de extensión — contratos exactos

### 9.1 `FoundationStateEnrichmentProcessor`

```scala
abstract class FoundationStateEnrichmentProcessor extends Serializable {
  def erichFoundationState(foundationState: FoundationRecordList): (FoundationRecordList, FoundationRecordList)
}
```

**El nombre del método tiene el typo `erich`, no `enrich` — confirmado, no es error de transcripción de este documento.** Implementación default: `DefaultFoundationStateEnrichmentProcessor` → `(foundationState, List.empty)`, no-op real. Se inyecta vía constructor de `StateCalculationContext` en `StateCalcApp.main` — no se carga por reflexión desde configuración, es código fijado al compilar. `[C]`

### 9.2 `StateCalcProcessor`

```scala
abstract class StateCalcProcessor(context: StateCalculationContext) extends OxygenProcessor {
  def enrich(payload: Map[String, Any]): Map[String, Any]
}
```

Carga por reflexión: `Class.forName(fqcn).getConstructor(classOf[StateCalculationContext]).newInstance(stateCalcContext)` — **exige constructor público de un solo argumento `StateCalculationContext`**. Se resuelve desde `oxygen.stage.stateCalc.appenders.<nombre>.processor` (bloque `appenders` del `.conf`, §4.1). `[C]`

### 9.3 Reflexión para `CUSTOM` / `CUSTOM_WITH_HISTORY` / `CUSTOM_ON_SECTION`

Mismo patrón en los tres: `Class.forName(handler.clazz).newInstance().asInstanceOf[...]` — **constructor sin argumentos**, a diferencia de `StateCalcProcessor` (§9.2, un argumento). Si `handler` es `null` para un campo/sección que lo requiere, cada uno de los tres sitios de instanciación lanza `StateCalculationConfigException` antes de llegar a la reflexión.

| Contrato | Firma del método abstracto |
|---|---|
| `CustomFieldProcessor` | `def createField(currentEvent: Map[String, Any]): Any` |
| `CalculatedWithHistoryFieldProcessor` | `def createField(currentEvent: JavaFoundationRecord, pastEvents: JavaFoundationRecordList): Any` |
| `CustomOnSectionProcessor` | `def compute(section: CalculationSection, sections: Map[String, Any], lookup: Map[String, Any] = Map.empty): Map[String, Any]` |

`[C]`, verificado por lectura directa.

## 10. Entrada, salida y persistencia

### 10.1 Entrada

Lee `oxygen.stage.consistencyCheck.paths.synthesizedDir` (clave de Synthesis, reutilizada). `StateCalcIOUtils.retrieveSyntheticEventsAsDS` lee `synthesizedDir/*/*` como eventos sintéticos (Jackson). Confirmado con fixture real `example/syntehtic-events-input/consistency/synthesized/part-...json`. `[C]`

### 10.2 Salida

`oxygen.stage.stateCalculation.paths.statecalcDir`. Carpeta particionada por timestamp numérico con marcador `_SUCCESS` (confirmado: `example/output-demo/calculated/<timestamp>/_SUCCESS`).

Ejemplo real de contrato JSON de salida (`example/statecalc/agg.json` / `output.json`, idénticos):
```json
{"AggregationOnBalanceGroupBySubSR":[{"avg":26530.0,"min":26530,"subscriber":"230001","subscriberRelation":"...","sum":26530}, ...],"parentEventIds":["31555bbf-..."],"pin":2002}
```
Confirma: `pin` siempre presente; `parentEventIds` como lista plana; cada sección de agregación como clave de nivel superior cuyo valor es una lista de grupos resueltos. `[C]`

### 10.3 Persistencia HBase — `XpmEventDAO.scala`

Constructor recibe `xpmEventStore: String` (nombre de tabla) como parámetro — **no hardcodeado en el DAO**, se resuelve en la capa de configuración de plataforma (§4.2).

`saveStateEvents`: construye un `KeyValueEntry` (`table=xpmEventStore`, `key=pin.toString`, `data=stateEvent`) y ejecuta un **bucle de reintento con concurrencia optimista**, verificado línea por línea en esta sesión:

```scala
val retryInterval = 1   // milisegundo
val nTry = 10000
var iTry = 0
while ({
  val isWritten = newEventWriteWithVersionTry(newKeyValueEntry, true)
  iTry += 1
  if (iTry > nTry) { logger.error("Error: HBase concurrent write timeout - saveStateEvent") }
  (!isWritten) && (iTry <= nTry)
}) { Thread.sleep(retryInterval) }
```

- `newEventWriteWithVersionTry` relee la versión actual (`newEventReadWithVersion`) y escribe condicionado a esa versión (concurrencia optimista real, no un lock).
- Hasta **10.000 reintentos** de 1ms cada uno (10 segundos en el peor caso).
- Agotados los reintentos, solo se loguea `error` — **no se lanza excepción, el flujo continúa** sin confirmar que la escritura ocurrió.
- **Si el bloque `try` completo lanza una excepción** (no solo agotar reintentos), el `catch` hace un último intento **sin ningún control de versión**: `oxygenDataAccess.setValue(newKeyValueEntry)` — en ese camino se abandona por completo la concurrencia optimista.

`[C]`, verificado por lectura directa línea por línea en esta sesión.

## 11. Validaciones fail-fast explícitas

| Validación | Momento | Detalle |
|---|---|---|
| `StateCalculationConfigException` — `FieldType.UNKNOWN` en Foundation | Runtime, al resolver un campo | Campo con tipo deprecated/no reconocido |
| `StateCalculationConfigException` — `handler` ausente | Construcción | Para `CUSTOM`, `CUSTOM_WITH_HISTORY` (Foundation) o `CUSTOM_ON_SECTION` (Calculation) sin `handler` declarado |
| `StateCalculationConfigException` — validación de configuración | Construcción de `CalculationStateCalculator` | `CalculationConfiguationValidator.validate` vuelca errores por `logger.error` antes de lanzar — fail-fast real, antes de procesar cualquier evento |
| `StateCalculationScriptExecutionException` | Runtime | Falla de un script Nashorn, tanto en campos `CALCULATED` de Foundation (mensaje incluye nombre de campo) como en `where` de Calculation (mensaje incluye nombre de sección) |
| `UnsupportedOperationException` | Runtime, al resolver `section.type` | Tipo de sección sin `CalculationType` registrado; mensaje lista los tipos soportados y enlaza al repo de plugins |

`[C]`, verificado por lectura directa.

## 12. Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Discrepancia `groupBy` plano vs. `grouping.groupBy` anidado (§5.4) — requiere verificación empírica de deserialización Jackson, no se puede resolver por lectura de código ni por inferencia | `[A:sin-clasificar]` — bloqueante para generar secciones nuevas con agrupación hasta resolverse |
| 2 | Ausencia de motor de calidad de datos de 4 dimensiones, reconfirmada en esta sesión | `[A:sin-clasificar]` (heredado de `problema-stateCalculation.md` §2) |
| 3 | Semántica de `ContainsLinkedBy` más allá de `AUTO` — es el único valor existente en el enum; no se investigó si hay otros valores previstos pero no implementados | `[PM]` |
| 4 | Mecanismo exacto por el que `GroupingClause.java` (DTO independiente con su propio `groupBy`) se relaciona, si acaso, con `CalculationSection` — no se encontró el punto de conexión en el código leído | `[A:sin-clasificar]`, relacionado con #1 |

## Pie de trazabilidad

- Problema de negocio y clasificación de complejidad: `Diseño/problema-stateCalculation.md` (documento ya aceptado, no se duplica aquí).
- Claves de configuración: `src/main/resources/local/statecalc-application-local.conf`, `env-local.conf`, `oxygen-config-default.conf`.
- Estructura de JSON y DTOs: `src/main/resources/local/statecalc-foundation-local.json`, `statecalc-calculation-local.json`; `config/foundation/FoundationField.java`, `FoundationStateConfiguration.java`; `config/calculation/CalculationSection.java` (verificado por `grep` directo en esta sesión), `AggregatesConfiguration.java`, `ReferenceSection.java`, `Contains.java`, `CustomHandler.java`, `GroupingClause.java`, `PluggableConfiguration.java`, `AggregateFunction.java`.
- Algoritmo Foundation: `calculators/foundation/FoundationStateCalculator.scala`; `calculators/foundation/FoundationFunctions.scala`.
- Algoritmo Calculation: `calculators/calculated/CalculationStateCalculator.scala`; `calculators/calculated/QueryFunctions.scala`; `calculators/calculated/types/aggregates/functions/impl/{Sum,Min,Max,Count,Average}.scala`.
- `attachHistoricalData`: `engines/StateCalcAddOns.scala`.
- Puntos de extensión: `processors/foundation/FoundationStateEnrichmentProcessor.scala`, `processors/defaults/DefaultFoundationStateEnrichmentProcessor.scala`, `processors/StateCalcProcessor.scala`; `app/StateCalcApp.scala`.
- Entrada/salida y persistencia: `utils/StateCalcIOUtils.scala`; `utils/XpmEventDAO.scala` (bucle de reintento verificado línea por línea en esta sesión); `example/syntehtic-events-input/`, `example/output-demo/`, `example/statecalc/`.
- Validaciones: `exceptions/StateCalculationConfigException`, `exceptions/StateCalculationScriptExecutionException`.
- `README.md` §Introduction, §Design StateCalc Calculation and Aggregation Function, §RUN ON AWS; `migrate.md` (restricción JDK 11 por Nashorn).
- Documentos previos de la iniciativa consultados para no duplicar contenido (`05-convenciones.md` §7): `Diseño/problema-stateCalculation.md`; `Hallazgos-StateCalculation.md`.
- No se leyó ni citó `settings-artifactory.xml` (`01-restricciones-principios.md` §4).
- Última verificación de referencias: 2026-10-01.
