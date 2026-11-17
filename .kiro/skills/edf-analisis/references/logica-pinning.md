---
inclusion: manual
---

# Lógica de negocio operable — etapa Pinning

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-10-01 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; formato según `05-convenciones.md`; metodología según `04-reglas-analisis.md` (tipo T3, Skill-Pinning, sin skill de análisis especificado — `[PM]`) |
| Naturaleza | Análisis técnico derivado de código real (`oxygen-pinning`), destinado a servir de insumo YAML/Markdown intermedio para un Skill generador de artefactos de configuración (`.csv`, `.json`, `.config`/`.conf`) de esta etapa. No cierra preguntas abiertas ni fija supuestos |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

Describir, con precisión operable y cita verificable, toda la lógica de negocio de la etapa Pinning (etapa 4 del pipeline, `02-lexico-edf.md` §2) tal como existe en el código real de `oxygen-pinning`: claves de configuración y su efecto, formato y esquema de cada archivo de entrada/salida, estructuras de datos, algoritmo de matching y clustering, reglas de reuso de PIN entre generaciones, y validaciones de fail-fast.

**Cubre:** todo lo que un Skill necesita conocer para proponer archivos de configuración (`.conf`/HOCON, `.json`, `.csv`) correctos para ejecutar esta etapa — claves, formatos de archivo, estructura de `identifying-attributes.json`, contratos de los puntos de extensión (`IdentityMatcher`, `ClusterSplitter`, `RelationshipMigrationProcessor`), y los casos de borde que ya se conocen por lectura directa del código.

**No cubre:** la clasificación de complejidad técnica Spark (eso vive en `problema-pinning.md` §8), la comparación arquitectónica con Acceptance/Curation (`DevPatterns/Pinning-Patterns.md`), el contrato de frontera formal con Synthesis (`07-contratos-frontera.md`), ni la generación de código/configuración en sí (tarea de un Skill posterior, fuera de este documento).

Este documento se basa en lectura estática del repositorio `oxygen-pinning`. No se ejecutó código. No se abrió, leyó ni citó `settings-artifactory.xml` (prohibido por `01-restricciones-principios.md` §4). Ningún dato de este documento proviene de personas reales; los archivos de ejemplo citados son fixtures sintéticos del propio repositorio InnerSource.

## 2. Advertencia estructural: qué vive en este repo y qué no `[C]`

Las estructuras de datos centrales que el motor de pinning consume y produce — `TaggedIdentity`, `Identity`, `TaggedCuratedEvent`, `TaggedCuratedEventWithMap`, `PinnedEventWithMap`, `StampedIdentity` — **no están definidas en `oxygen-pinning`**. Se importan desde `com.experian.edf.oxygen.domain.*`, paquete del core InnerSource de Oxygen, que es Z2 (solo lectura, repo no presente en este workspace).

Evidencia: `domain/Pinner.scala` importa `com.experian.edf.oxygen.domain.TaggedIdentity`; `engines/SparkPinner.scala` (bloque de imports inicial) importa el resto desde el mismo paquete; búsqueda exhaustiva de `TaggedIdentity.scala` en todo el workspace sin resultados.

Consecuencia práctica para el Skill: este documento puede describir con certeza los **puntos de extensión** (`IdentityMatcher`, `ClusterSplitter`, `RelationshipMigrationProcessor`) y los **artefactos de configuración y de archivo** que sí viven en `oxygen-pinning`. No puede certificar el esquema interno completo de `Identity`/`TaggedIdentity` — solo lo que se infiere por **uso** (campos accedidos: `identity.identityId`, `identity.attributes: Map[String,Any]`, `taggedIdentity.subscriber`, `taggedIdentity.subscriberRelation`). `[A:sin-clasificar]` — se recomienda escalar antes de que el Skill genere configuración que asuma campos no confirmados de esas clases.

## 3. El problema que resuelve (resumen operable)

Pinning agrupa eventos curados que pertenecen a la misma entidad real, asignándoles un **pin** (identificador de entidad) compartido. Sin esta etapa, Synthesis no tendría una clave estable para reconciliar eventos de la misma persona a través del tiempo (`problema-pinning.md` §3.1, `[C]`). El mecanismo opera en tres fases:

1. **Identity Extraction**: de cada evento curado se extraen solo los atributos declarados en `identifying-attributes.json`, formando una identidad candidata.
2. **Identity Pinning**: las identidades del mismo entity real se agrupan vía blocking (Near Neighbor) + comparación por pares (`doesMatch`) + clustering global (Union Find local + GraphFrames `connectedComponents` global).
3. **Heterogeneous Collection (HC)**: job separado, posterior, que hace el join final pin↔identidad × identidad↔evento, produciendo pin↔eventos.

(`README.md` §Introduction, §Identity Extraction, §Identity Pinning, §Heterogeneous Collection — `[C]`)

## 4. Claves de configuración

### 4.1 Claves propias de Pinning (HOCON, confirmadas en `pinning-application.conf` y `oxygen-config-default.conf`)

| Clave | Default de plataforma | Efecto en el comportamiento | Estado |
|---|---|---|---|
| `oxygen.stage.pinning.paths.identifyingAttributesFile` | — | Lista de rutas a archivos JSON con los atributos identificadores. **Solo se usa el primer elemento de la lista**; el resto se ignora silenciosamente (ver §9) | `[C]` |
| `oxygen.stage.pinning.identityMatcher` | `""` (vacío) | FQCN de la implementación de `IdentityMatcher` instanciada por reflexión. Si queda vacío, `Class.forName("")` falla en tiempo de ejecución | `[C]` |
| `oxygen.stage.pinning.clusterSplitter` | — | FQCN opcional de `ClusterSplitter`. Si es `null`/`""`, no se instancia y no se aplica partición de clusters | `[C]` |
| `oxygen.stage.pinning.reusePin` | — | `true` = pinning estático (reusa pines de la generación de referencia); `false` = pinning dinámico (todos los pines se regeneran, sin merge/split) | `[C]` |
| `oxygen.stage.pinning.saveNearNeighborState` | `false` | Si se persiste el artefacto `-nearNeighbor` | `[C]` |
| `oxygen.stage.pinning.saveStampedIdentityState` | `false` | Si se persiste el artefacto `-stampedIdentity` (identity-pin-map) | `[C]` |
| `oxygen.stage.pinning.groupSSR` | `true` | Activa una unión adicional de clusters por `subscriber:subscriberRelation` (SSR), fusionada con el resultado del clustering principal. **El `.conf` de ejemplo lo pone en `false`** — el override explícito gana sobre el default de plataforma | `[C]` |
| `oxygen.stage.pinning.nearNeighborBlockSize` | `700` | Umbral de tamaño de bloque near-neighbor. Bloques con tamaño ≥ este valor se **descartan silenciosamente** del matching pairwise (ver §9) | `[C]` |
| `oxygen.stage.pinning.pinningVleRange` | `2000` | Rango `[0, N)` reservado para pines VLE/celebrity. También es el `minValueAllowed` que valida que un pin reusado sea válido, y el desplazamiento aplicado a los pines nuevos en la rama sin reuso | `[C]` |
| `oxygen.stage.pinning.staticPinningFormat` | `${oxygen.application.storage}` | Formato de escritura del identity-pin-map estático y del pin-index | `[C]` |
| `oxygen.stage.pinning.identitySufficiency` | `""` | Declarada en `oxygen-config-default.conf` pero **sin consumidor visible** en el código Scala leído de este repo | `[A:sin-clasificar]` |
| `oxygen.stage.consistencyCheck.relationshipMigrationProcessorClassName` | `""` | FQCN opcional de `RelationshipMigrationProcessor`. **Nota de nomenclatura:** la clave HOCON usa el prefijo `consistencyCheck`; el acceso desde Scala es `oxygenConf.oxygen.stage.consistency.get...` — el mapeo `consistencyCheck` (config) ↔ `consistency` (API) lo resuelve una capa externa al repo, no visible aquí | `[C]` para la existencia de la clave; `[A:sin-clasificar]` para el mecanismo exacto de mapeo |

### 4.2 Flags leídas desde constantes de plataforma (`oxygenConf.oxygen.*`), sin texto HOCON directo visible en este repo

Estas resuelven a claves HOCON equivalentes definidas en una capa de configuración del core de Oxygen (fuera de este repo). Se listan por su nombre de constante Scala y su efecto confirmado en `engines/SparkPinner.scala`:

| Constante | Efecto |
|---|---|
| `ENABLE_PROFILING` → `enableProfiling` | Activa logging adicional de conteos y diagnóstico durante el pinning |
| `ENABLE_DOESMATCH_PROFILING` → `enableDoesMatchProfiling` | Activa medición de duración (nanosegundos) de cada llamada a `doesMatch`, vía `doesMatchWithProfiling` |
| `SAVE_HETEROGENEOUS_COLLECTION` | Si `true`, el stamping final se delega al job HC; si `false`, `SparkPinner` hace el stamping directo (`stampingByHC = !SAVE_HETEROGENEOUS_COLLECTION`) |
| `ENABLE_LARGE_BLOCK_HANDLING` → `enableLargeBlockHandling` | Se lee y se imprime en log; **no se encontró una rama de código activa en el flujo principal (`performPinning`) que cambie comportamiento según este flag** | `[A:sin-clasificar]` — verificado por lectura directa, no solo por reporte de terceros |
| `GROUP_SSR` → `groupSSR` | Ver §4.1 |
| `PINNING_NEAR_NEIGHBOR_BLOCKSIZE` → `nearNeighborBlockSize` | Ver §4.1 |
| `CONNECTED_COMPONENTS_TIMEOUT` → `connectedComponentsTimeout` | Timeout en milisegundos para `graph.connectedComponents.run()` vía `Await.result` |
| `PINNED_DIRECTORY` / `PINNED_REFERENCE_DIR` / `PINNING_ARCHIVED_DIRECTORY` | Rutas de salida, referencia (generación anterior) y archivado |
| `KEEP_TOGETHER_DIRECTORY` / `KEEP_APART_DIRECTORY` | Rutas de los archivos de usuario KT/KA |
| `VLE_FILE` | Ruta del archivo de metadatos VLE/celebrity |

### 4.3 Claves de orquestación general (no exclusivas de Pinning, pero condicionan su ejecución)

| Clave | Efecto | Estado |
|---|---|---|
| `oxygen.application.lambda` | Decide la implementación concreta en `Pinner.create`: `"batch"` → `SparkPinner`; cualquier otro valor (incluido `"stream"`, cuya rama está comentada en el código) → `UnsupportedOperationException("only oxygen.application.lambda = stream / batch are supported!")`. En la práctica, **solo `"batch"` funciona** | `[C]` |
| `oxygen.application.generation.candidate` / `...reference` | Nombres de generación actual y de referencia; resuelven las rutas de pinning cuando se usa la estructura de salida por defecto | `[C]` |

## 5. `identifying-attributes.json` — contrato de identidad

Contenido real del archivo de ejemplo del repo (`src/main/resources/identifying-attributes.json`):

```json
["firstName","middleName","firstLastName","secondLastName","nationalId","cellPhone"]
```

Reglas confirmadas por lectura de `Pinner.scala::readIdentifyingAttributes`:

- Es un **array JSON plano de strings**, no un objeto con metadatos adicionales.
- Se carga vía **classloader** (`getClass.getClassLoader.getResource(filename)`), no vía ruta de filesystem arbitraria. El archivo debe estar en el classpath como recurso empaquetado. Esto condiciona cómo el Skill debe proponer la ruta de este archivo: no es una ruta de datos cualquiera, es un recurso de aplicación.
- El array resultante se **broadcastea** (`identifyingAttributesBC`) y es la única fuente de verdad de qué campos forman la identidad en toda la ejecución.
- `SparkPinner` sobrescribe el método del padre, pero delega sin cambios (`super.readIdentifyingAttributes(filename)`) — no hay lógica adicional de validación de este archivo en la subclase.
- `identifyingAttributesFile` en el `.conf` es una **lista** de rutas, pero el código solo consume el primer elemento; el resto de la lista se ignora sin aviso. **Regla para el Skill: generar siempre un único elemento en esta lista, o documentar explícitamente que los adicionales no tienen efecto.**

(`[C]`, verificado por lectura directa de `identifying-attributes.json` y del método `readIdentifyingAttributes`)

## 6. Archivos de entrada y salida

| Artefacto | Patrón de nombre | Formato | Generado/consumido por |
|---|---|---|---|
| Eventos curados (entrada) | carpeta `curatedDir`, JSON-lines | `text`/JSON, un evento por línea | Entrada desde Curation (etapa anterior) |
| `identifying-attributes.json` | recurso de classpath | JSON (array de strings) | Config de usuario, ver §5 |
| `keep-together` (KT) | archivo de usuario en `KEEP_TOGETHER_DIRECTORY` | JSON: `{"identities":[{...atributos identificadores...,"subscriber":"...","subscriberRelation":"..."}]}` | Fuerza que un grupo de identidades comparta pin |
| `keep-apart` (KA) | archivo de usuario en `KEEP_APART_DIRECTORY` | JSON: `{"identities":[{...atributos identificadores...}]}`, sin `subscriber`/`subscriberRelation` | Fuerza que un grupo de identidades nunca comparta pin |
| `idMigrationConf` | archivo apuntado por configuración | JSON → `IdMigrationConf(identityMigrationFilter: Map[String,Any], identityMigrationFields: Array[Map[String,String]])` | Reglas de migración de relación entre eventos |
| `pin-index` (archivo de definición) | archivo de usuario, JSON-lines | cada línea → `PinIndex(name: String, keys: List[String])` | Define índices auxiliares pin↔atributo |
| `{timestamp}-pin-EID` | salida | DataFrame; ejemplo real JSON-lines `{"eventId":"-705","pin":2005}` | `saveStampedEventId` |
| `{pinFilename}-identity-pin-map` | salida (y entrada de la generación siguiente) | JSON o avro/parquet según `staticPinningFormat` | `saveIdentityPinMap`; releído como estado por la generación siguiente |
| `{pinFilename}-nearNeighbor` | salida, condicional a `saveNearNeighborState` | uno de `TSV, AVRO, PARQUET, TEXT, JSON` | `SparkPinner.saveNearNeighbors` |
| `{pinFilename}-stampedIdentity` | salida, condicional a `saveStampedIdentityState` | idem; TSV usa `"${identityId}\t${json}"` | `SparkPinner.saveStampedIdentity` |
| `{pinFilename}-merged-pin` | salida | texto plano (`saveAsTextFile`) | `saveMergedPin` |
| `{pinFilename}-split-pin` | **se calcula pero no se escribe** — la llamada a `saveSplitPin` está comentada en el código activo | texto plano (si se activara) | n/a — ver §10 |
| `{pinFilename}-unpinned-celebrity-events` | salida | Dataset vía `pinningIOUtils.saveCelebrityEvent` | bloque de procesamiento VLE en `run` |
| `{timestamp}-pin-hc` | salida | JSON por defecto; avro/parquet si se configura | `saveHeterogeneousCollection` (job `SparkHeterogeneousCollector`) |
| `{pinFilename}-context-pin-map` | salida | texto plano (`saveAsTextFile`) | `saveContextPinMap` |
| `pin-index` (resultado) y `pin-index-tmp/{indexName}` | salida | formato = `staticPinningFormat` | `processPinIndex`, ver §8 |

Confirmado con archivo de ejemplo real del repo (`example/pinned-output/.../part-00000-....json`):

```json
{"eventId":"-705","pin":2005}
{"eventId":"-800","pin":2007}
{"eventId":"-801","pin":2007}
```

(`[C]`, verificado por lectura directa de código y de los archivos de ejemplo del repo)

## 7. Motor de matching de identidad — algoritmo exacto

### 7.1 Las cuatro sub-fases de `performPinning` (`engines/SparkPinner.scala::performPinning`)

1. **Near-neighbor blocking**: cada identidad se agrupa bajo cada clave que devuelve `identityMatcher.nearNeighborKeys(...)` (`flatMap` + `reduceByKey(_ ++ _)`). Una identidad puede caer en varios bloques simultáneamente.
2. **Pairwise match dentro de cada bloque**: doble `while` O(n²) (`getExactMatches` / `getExactMatchesWithProfile`), llamando a `identityMatcher.doesMatchWithProfiling` para cada par. Bloques con tamaño ≥ `nearNeighborBlockSize` se descartan por completo (ver §9).
3. **Union Find local**: dentro de cada bloque, los pares que hacen match se funden con una estructura `UnionFind` (compresión de camino + unión por tamaño de árbol).
4. **Connected Components global**: los componentes locales de todos los bloques se modelan como aristas de un grafo (`GraphFrame`), y `connectedComponents.run()` los fusiona en clusters globales de identidad, con checkpoint y timeout explícitos.

Después del clustering: `clusterSplitter` opcional (parte clusters según reglas de dominio), Keep Apart (separa por fuerza), Keep Together e Identity Migration (unen por fuerza), y opcionalmente un agrupado adicional por SSR si `groupSSR = true`.

### 7.2 Contrato de `IdentityMatcher` (`util/IdentityMatcher.scala`, completo)

Único punto de extensión **obligatorio**. Un solo método abstracto; el resto tiene default razonable que la implementación puede o no sobrescribir:

| Método | Firma | Default | Obligatorio |
|---|---|---|---|
| `nearNeighborKeys` | `(TaggedIdentity) => List[String]` | `List(subscriber + ":" + subscriberRelation)` | no |
| `defaultNearNeighborKeys` | igual firma | mismo default; usado específicamente para el agrupado SSR | no |
| `celebrityIdentityThreshold` | `Int` | `700` | no (puede sobrescribirse) |
| `matchingKeysForCelebrity` | `(TaggedIdentity) => List[String]` | — | **sí, abstracto** |
| `doesMatch` | `(TaggedIdentity, TaggedIdentity) => (Boolean, Double)` | `true, 0.0` si mismo `subscriber`+`subscriberRelation`; si no, `false, 1.0` | no, pero toda implementación real **debe llamar a `super.doesMatch` primero** (regla de contrato explícita en comentario del código de referencia) |
| `doesMatchWithProfiling` | envuelve `doesMatch` con `System.nanoTime()` | — | no |
| `doesMatchKA` | `(TaggedIdentity, TaggedIdentity) => Double` | suma 1.0 por cada atributo identificador exactamente igual | no |

Contrato de retorno de `doesMatch`: `(Boolean, Double)` donde el `Double` es, según el comentario del código, una **distancia** (0.0 = más cercano, 1.0 = más lejano). La implementación de referencia (`ISIdentityMatcher`) en realidad acumula **similaridad creciente** (0.0 = sin evidencia, hasta 1.0 = evidencia máxima) e invierte la escala documentada cuando declara match (`0.0` en el caso de match exacto por `super.doesMatch`, pero score de similitud ascendente en el resto). **Regla operable para el Skill: no asumir que el segundo elemento de la tupla es homogéneo entre implementaciones; cada `IdentityMatcher` concreto define su propia semántica de distancia/similitud.** `[C]`, verificado por lectura directa.

### 7.3 Implementación de referencia `ISIdentityMatcher` — scoring exacto

```
similarityThreshold = 0.6
+0.5  si nationalId1 == nationalId2
+0.3  si firstName1 == firstName2  Y  firstLastName1 == firstLastName2
+0.3  si cellPhone1 == cellPhone2
match si similarity >= 0.6
```

**Defecto real confirmado por lectura directa del código** (`extension/ISIdentityMatcher.scala`, líneas de asignación de `thisFirstLastName`/`thatFirstLastName`): ambas variables leen `ISIdentityAttributesKey.firstName` en vez de `ISIdentityAttributesKey.firstLastName`. El mismo defecto existe, idéntico, en `RIIdentityMatcher.scala`. Efecto: la condición de +0.3 por apellido en realidad compara `firstName` contra sí mismo dos veces, nunca el apellido real. **No se corrige en este documento** (la corrección es una decisión de diseño del Skill o del equipo, no de este análisis) — se registra como hallazgo verificado, no como suposición. `[C]`, confirmado por `grep` directo sobre ambos archivos del repo.

`nearNeighborKeys` de `ISIdentityMatcher`: `[nationalId, firstName[0:5]+firstLastName[0:5], cellPhone]`, filtrando `null` y duplicados.

### 7.4 Implementación de referencia `RIIdentityMatcher` — scoring exacto

Lógica booleana de dos niveles, sin umbral configurable:

```
match total  (0.0) si nationalId1 == nationalId2
match parcial (0.5) si firstName1 == firstName2 Y firstLastName1 == firstLastName2   (mismo defecto de §7.3)
no-match (1.0) en cualquier otro caso
```

`nearNeighborKeys`: `nationalId` + 4 claves combinando prefijo/sufijo de 5 caracteres de `nationalId` con `firstLastName`/`secondLastName`.

### 7.5 Contrato de `ClusterSplitter` (`util/ClusterSplitter.scala`, completo)

```scala
abstract class ClusterSplitter extends Serializable {
  def splitCluster(cluster: List[TaggedIdentity], identityMatcher: IdentityMatcher): List[List[TaggedIdentity]]
}
```

Un único método abstracto. Opcional: solo se instancia por reflexión si `oxygen.stage.pinning.clusterSplitter` no está vacío. **El repo no trae ninguna implementación concreta de referencia** — a diferencia de `IdentityMatcher`, que sí trae dos (`ISIdentityMatcher`, `RIIdentityMatcher`). `[C]`

### 7.6 Contrato de `RelationshipMigrationProcessor` (`lookup/RelationshipMigrationProcessor.scala`)

Mismo idioma de deprecación dual que `CurationProcessor` (etapa Curation): API vieja basada en `JsonObject`, marcada `@deprecated` con cuerpo `???`; API nueva basada en `java.util.Map[String,Any]` que delega convirtiendo a `JsonObject`. Dos miembros abstractos: `fieldsToTransformInSource: Array[String]` y `fieldsToTransformInDestination: Array[String]`. Se instancia por reflexión con un **constructor de un argumento** (`MigrationProcessorContext`) — a diferencia de `IdentityMatcher`/`ClusterSplitter`, que usan constructor sin argumentos. `[C]`

## 8. Generaciones y reuso de PIN entre ejecuciones

Mecanismo exclusivo de Pinning entre las etapas analizadas hasta ahora (`problema-pinning.md` §5, `[C]`).

- `reusePin = true` → pinning estático: se lee el `identity-pin-map` de la generación de referencia (`PINNED_REFERENCE_DIR`), localizado exigiendo **unicidad estricta** (si hay 0 o más de 1 archivo que matchee `.*-identity-pin-map$`, se trata como "no hay generación previa" y se generan pines nuevos, degradando con gracia vía `logger.warn`, no con excepción).
- `reusePin = false` → pinning dinámico: todos los pines se regeneran, sin merge ni split; se usan pines secuenciales desplazados por `pinningVleRange` (`zipWithUniqueId` + offset).

### 8.1 Centinela de "sin pin previo"

El valor `-1L` marca una identidad que no tiene pin de la generación anterior. Se fusiona el estado nuevo (`-1L` por defecto) con el estado leído (`PinnedTaggedIdentity.pin` real) mediante `aggregateByKey`, y luego:

| Pines previos encontrados para el cluster | Acción | Resultado |
|---|---|---|
| 0 (`pins.length == 1 && pins.last == -1L`) | Pin nuevo vía `PinGenerator.createPin(partitionIndex, pinningVleRange, vlePins)` | Identidad nueva |
| 1 | Se reutiliza ese pin | Identidad ya conocida, sin conflicto |
| &gt;1 | Sobrevive uno (`filteredPins.last`), el resto se registra como `MergedPin(pins.filter(_ != -1L))` | Fusión (pin merge) |

`[C]`, verificado por lectura directa del bloque `mapPartitionsWithIndex` en `SparkPinner.scala`.

### 8.2 Validación de pin reusado fuera de rango

```scala
if (x.pin < pinningVleRange)
  throw new Exception(s"Invalid Pin. Pin Must be greater than PinningVleRange [0 - '${pinningVleRange}']. Set [reusePin] = false to recreate the pinId with a valid range")
```

Fail-fast explícito: un pin leído del histórico que caiga por debajo del rango VLE se considera inválido y aborta el job, con el mensaje indicando la mitigación (`reusePin = false`). `[C]`

### 8.3 Generación de PIN (`util/PinGenerator.scala`, completo)

```
createPinId(index) = (timestamp << 22) | (index << 12) | index | random(0..4095)
createPin(index, minValueAllowed=2000, vlePins=[]):
    pin = createPinId(index)
    si pin > minValueAllowed y pin no está en vlePins: usar pin
    si no: reintentar UNA sola vez con createPinId(index) (sin volver a validar el resultado del reintento)
```

41 bits de timestamp, 10 bits de índice de partición, 12 bits aleatorios. El rango `[0, pinningVleRange)` queda reservado para pines VLE/celebrity. `mapPartitionsWithIndex` es obligatorio en el llamador porque `createPin` necesita el `partitionIndex` real. `[C]`

### 8.4 Archivado de la generación anterior

```scala
def archivePinDir(timestamp: String): String = {
  fileUtils.moveFile(pinnedDir, archivedDir)   // archivedDir = PINNING_ARCHIVED_DIRECTORY + "/archived-on-" + timestamp
  fileUtils.touch(pinnedDir, true)
  archiveDir
}
```

Si no hay eventos curados en la entrada, el job sale **antes** de archivar (comentario explícito en el código: *"Skip pinning and avoid unnecessary archival"*). `[C]`

## 9. Validaciones de entrada y comportamientos fail-fast / fail-silent

Distinción importante para el Skill: no todos los guards del código son fail-fast. Algunos son fail-silent y el Skill debe conocer la diferencia para no asumir que "si no hay excepción, no hay pérdida de datos".

| Comportamiento | Tipo | Detalle |
|---|---|---|
| Pin reusado fuera de rango (§8.2) | **Fail-fast** | `throw new Exception(...)` |
| Formato de salida inválido para Stamped Identity | **Fail-fast** | `throw new Exception("Invalid Stamped Identities output format. Must be one of '...'")` |
| Formato de salida inválido para Near Neighbors | **Fail-fast** | `throw new Exception("Invalid Near Neighbors output format. Must be one of '...'")` |
| `HC_CELEBRITY_PIN_THRESHOLD` inválido (vacío o ≤ 0) | **Fail-fast** | `Failure(new UnsupportedOperationException(...))` en `SparkHeterogeneousCollector` |
| Conflicto de unicidad en pin-index (ver §11) | **Fail-fast** | `throw new Exception("Conflicts in pin-index!...")`, con el detalle del conflicto escrito a archivo antes de abortar |
| `oxygen.application.lambda` no soportado | **Fail-fast** | `UnsupportedOperationException`; solo `"batch"` funciona en la práctica |
| Timeout de Connected Components | **Fail-silent (parcial)** | Se maneja como `Option`; el código fuente devuelve siempre `Some(...)` o lanza `TimeoutException` directamente — nunca llega realmente a `None` tal como el código espera comprobar con `== None`. El job no produce una tupla de resultados utilizable si falla, pero el mecanismo de detección de fallo (`ccFailed`) está roto: nunca se activa por la vía esperada |
| Bloques near-neighbor con tamaño ≥ `nearNeighborBlockSize` | **Fail-silent** | Se descartan con `println("*** Ignored block: " + ...)`, sin excepción, sin entrada en el logger formal. Identidades de ese bloque **no se comparan entre sí en absoluto** — quedan sin oportunidad de match dentro de ese vecindario |
| `identity-pin-map` de la generación de referencia no legible | **Degrada con gracia** | `try/catch`, `logger.warn`, continúa asumiendo que no hay pines previos |

`[C]`, verificado por lectura directa de cada bloque de código citado.

## 10. Artefactos calculados pero no persistidos

`split-pin` se calcula (`findPinSplits1`) pero la llamada a `saveSplitPin` está **comentada** en el código activo de `run`. El Skill no debe asumir que este archivo se genera en una ejecución real a partir del código tal como está hoy, aunque la lógica de cálculo exista. `[C]`, verificado por lectura directa.

## 11. `processPinIndex` — validación de unicidad de índices auxiliares

Mecanismo para construir índices auxiliares pin↔atributo, con las siguientes reglas:

1. Lee un archivo de definición JSON-lines, cada línea → `PinIndex(name, keys)`.
2. Para cada índice: extrae de `identity-pin-map` las columnas indicadas en `keys` (vía `taggedIdentity.identity.attributes.<campo>`), deduplica, y construye un `indexId` reutilizando el **mismo algoritmo de hash de identidad**, agregando el nombre del índice como atributo extra (para que dos índices con las mismas claves no colisionen entre sí).
3. Agrupa por `indexId`, colecciona el conjunto de pines asociados.
4. **Valida unicidad**: si un mismo `indexId` resuelve a más de un pin, escribe el detalle del conflicto a `{ruta}-conflict` y lanza `Exception("Conflicts in pin-index!...")`, abortando el proceso.

`[C]`, verificado por lectura directa de `SparkPinner.scala`.

## 12. Fase HC (Heterogeneous Collection)

Job Spark separado (`SparkHeterogeneousCollector`), ejecutado después de `SparkPinner` dentro del mismo `PinningApp.main`, leyendo de disco (no en memoria compartida). Hace el join final: pin↔eventId + identidad↔pin + eventos curados → por cada pin, lista completa de eventos (`groupBy("pin").agg(collect_list(...))`).

Maneja "celebrity pins": pines cuyo volumen de eventos supera `HC_CELEBRITY_PIN_THRESHOLD` se separan antes del join principal, con lógica adicional de VLE (Very Large Entity — expansión no confirmada por comentario explícito en código, `[PM]`) para no degradar el resto del procesamiento.

`[C]` para el mecanismo; `[PM]` para la expansión de la sigla VLE.

## 12.1 Atributos de identificación SISBEN que Pinning consume — confirmado por fuente externa + decisión del usuario `[C]`

Hallazgo externo a `oxygen-pinning` pero necesario para que el Skill resuelva `identifying-attributes.json` para el piloto SISBEN. Fuente: `Docs-General/contratos_frontera.md` §2 (documento de un discovery paralelo de Validation/Acceptance/Curation, no vinculante por sí solo para esta iniciativa — nivel 3 de evidencia según `04-reglas-analisis.md` §7, no nivel 1).

Esa fuente declara que Pinning (vía HU-006, no copiada en ese workspace) usa como atributos de identificación:

- **Prioridad 1**: `standardizedIdType`, `standardizedIdNumber` — coincide con `identitySufficiency` ya confirmado en `07-contratos-frontera.md` §5.5 (`standardizedIdNumber`, `standardizedIdType`, `standardizedIdName`).
- **Prioridad 2**: apellidos y nombres normalizados. La fuente dejaba abierto (su pregunta D11) si los nombres de estos campos curados son los del layout de 17 campos (`APELLID1`/`APELLID2`/`NOMBRES1`/`NOMBRES2`, obsoletos) o los del layout de 22 campos vigente (`PRI_APELLIDO`/`SEG_APELLIDO`/`PRI_NOMBRE`/`SEG_NOMBRE`).

**Decisión del usuario, 2026-10-01**: para efectos de esta iniciativa y de la generación del Skill-ConfigGen-Pinning, se usa `PRI_APELLIDO`, `SEG_APELLIDO`, `PRI_NOMBRE`, `SEG_NOMBRE` (nomenclatura del layout de 22 campos, consistente con el número de campos ya confirmado en `07-contratos-frontera.md` §2 y corroborado de forma independiente en `Docs-General/contratos_frontera.md` §4.1). Esta decisión resuelve D11 **solo dentro del alcance de este documento y de los artefactos que de él se derivan**; no cierra D11 en el workspace de origen de esa pregunta, que sigue fuera del alcance de esta iniciativa (`01-restricciones-principios.md` §0.4).

Consecuencia práctica para el mapeo de campos de identidad de Pinning (identifying-attributes / identitySufficiency, SISBEN):

| Rol en Pinning | Campo curado SISBEN (layout de 22, decisión del usuario) |
|---|---|
| Identificador — tipo | `standardizedIdType` |
| Identificador — número | `standardizedIdNumber` |
| Nombre — primer apellido | `PRI_APELLIDO` |
| Nombre — segundo apellido | `SEG_APELLIDO` |
| Nombre — primer nombre | `PRI_NOMBRE` |
| Nombre — segundo nombre | `SEG_NOMBRE` |

Nota de contraste con el motor genérico InnerSource (§5, §7 de este documento): el `identifying-attributes.json` de ejemplo del repo usa `firstName`, `middleName`, `firstLastName`, `secondLastName`, `nationalId`, `cellPhone` — nombres genéricos en inglés. El mapeo real para SISBEN sustituye esos nombres por los campos curados reales listados arriba; `cellPhone` **no aplica a SISBEN** (confirmado también en `Docs-General/Entendimiento_funcional.md` §5.2: "No aplica en SISBEN V4 — dato no disponible en el dataset DNP").

## 13. Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Esquema exacto de `TaggedIdentity`/`Identity` y demás case classes del core Oxygen, no visibles en este repo (Z2) | `[A:sin-clasificar]` — recomendado escalar antes de generar configuración que dependa de su estructura interna completa |
| 2 | Consumidor real de `oxygen.stage.pinning.identitySufficiency` | `[A:sin-clasificar]` |
| 3 | Rama de código que debería activar `enableLargeBlockHandling` — no se encontró en el flujo principal verificado (`performPinning`); existe un método hermano `getExactMatchesSingleBlockingKey` pero sin invocación activa confirmada desde `run` | `[A:sin-clasificar]` |
| 4 | Decisión sobre si el defecto de `firstLastName`/`firstName` en `ISIdentityMatcher`/`RIIdentityMatcher` (§7.3) se replica, se corrige o se reporta como hallazgo al InnerSource — **no es una decisión de este documento** | `[A:sin-clasificar]` |
| 5 | Mecanismo exacto de mapeo `consistencyCheck` (HOCON) ↔ `consistency` (API Scala) para `relationshipMigrationProcessorClassName` | `[A:sin-clasificar]` |
| 6 | Expansión confirmada de la sigla VLE | `[PM]` |
| 7 | Mapeo de este documento al esquema YAML intermedio que consumirá el Skill — tarea siguiente, no resuelta aquí | `[PM]` |
| 8 | Resolución de D11 (nomenclatura de apellidos/nombres curados que Pinning espera) en el workspace de origen (`Docs-General/contratos_frontera.md`) — la decisión del usuario registrada en §12.1 aplica solo al alcance de esta iniciativa, no cierra la pregunta en el discovery externo | `[PM]` — pendiente de comunicación explícita al responsable de ese discovery, si corresponde |

## Pie de trazabilidad

- Problema de negocio y fases: `README.md` §Introduction, §Identity Extraction, §Identity Pinning, §Heterogeneous Collection.
- Claves de configuración: `src/main/resources/pinning-application.conf`; `src/main/resources/oxygen-config-default.conf`; `src/main/resources/env.conf`; `src/main/resources/spark.conf`.
- `identifying-attributes.json`: `src/main/resources/identifying-attributes.json`; `domain/Pinner.scala::readIdentifyingAttributes` (heredado, override sin cambios en `engines/SparkPinner.scala`).
- Motor de matching: `util/IdentityMatcher.scala`; `extension/ISIdentityMatcher.scala`; `extension/RIIdentityMatcher.scala` (defecto `firstLastName`/`firstName` verificado por `grep` directo sobre ambos archivos).
- Clustering: `engines/SparkPinner.scala::performPinning`; `util/UnionFind.scala`; GraphFrames `connectedComponents`.
- Reuso de PIN y generaciones: `engines/SparkPinner.scala` (bloques `lastGenIdentityPinMap`, `mapPartitionsWithIndex`); `util/PinGenerator.scala`; `advanced.md` §Reusing pins.
- Archivos de entrada/salida: `engines/SparkPinner.scala` (métodos `save*`); `domain/Pinner.scala` (constructores de nombre de archivo); `example/pinned-output/`, `example/keep-apart/`, `example/keep-together/`, `example/curated-events-input/`.
- `processPinIndex`: `engines/SparkPinner.scala`, bloque de validación de unicidad.
- Fase HC: `engines/SparkHeterogeneousCollector.scala`; `domain/HeterogeneousCollection.scala`.
- Documentos previos de la iniciativa consultados para no duplicar contenido (`05-convenciones.md` §7): `Diseño/problema-pinning.md`; `DevPatterns/Pinning-Patterns.md`; `Hallazgos-Pinning.md`.
- Atributos de identificación SISBEN (§12.1): `Docs-General/contratos_frontera.md` §2, §4.2 (D11); `Docs-General/Entendimiento_funcional.md` §5.2; `07-contratos-frontera.md` §5.5 (`identitySufficiency`); decisión del usuario registrada en esta sesión, 2026-10-01.
- No se leyó ni citó `settings-artifactory.xml` (`01-restricciones-principios.md` §4).
- Última verificación de referencias: 2026-10-01.
