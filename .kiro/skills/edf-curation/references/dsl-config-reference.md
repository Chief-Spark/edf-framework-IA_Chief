# Referencia de configuración (HOCON) para Curation

Curation no tiene DSL de expresiones propio (a diferencia de Validation y Acceptance): la extensión
es **código** (processor) + **configuración** (enchufe por `filePattern`). Estados: VERIFICADO en el
código del framework (`CurationStageParser`, `CurationStageConfiguration`, `CurationRunMode`,
`LookupElements`) o en el motor (`SparkCurator`, `Curator`, `CuratorUtils`); SUPUESTO si no.

## Cargador

El framework une los `.conf` recibidos por argumento con *fallback* al `oxygen-config-default.conf`
del JAR y resuelve. `getString/getInt/...` devuelven `None` si la clave falta o tiene otro tipo:
**una clave mal escrita se ignora en silencio** y rige el default. Las claves leídas con `.get`
lanzan si faltan. `CurationApp` recibe tres archivos: `env.conf`, `curation-application.conf`,
`oxygen-lookup.conf`.

## Bloque de la ingesta (lo único que genera el skill)

Los ejemplos de esta página usan los valores del SISBEN **histórico** del ejemplo de la skill (HF-40,
AUD-C-07): ilustran la forma, no la spec vigente. Los valores reales salen del source-spec
(`archivo.file_pattern`, `catalogos[]`).

```hocon
oxygen.stage.curation.configs = [
  { filePattern = ".*(RT_|EP_)?SISBEN_EXPERIAN_\\d{6}\\.\\d{14}[+-]\\d{4}(\\.txt)?"
    processor   = "com.experian.edf.oxygen.curation.source.sisben.processor.SISBENCurationProcessor" }
]
```

| Clave | Regla | Estado |
|---|---|---|
| `filePattern` | obligatoria; es la llave del mapa patrón → processor. En HOCON se escapa `\\d`, `\\.` | VERIFICADO |
| `processor` | obligatoria (`.get` + `Class.forName`); la clase debe estar en el classpath o el motor lanza `OxygenException` al arrancar (CU:21-25) | VERIFICADO |

Matching (motor): la ruta del dataset, `<dir>/<lineage.fileName>.<datasetId>`, se compara **sin su
última extensión** (`removeExtension`, un solo corte, `SparkCurator.scala:188`): el corte quita el `.<datasetId>` y la
clave queda `<dir>/<lineage.fileName>`, con o sin `.txt` (ADR-03 del 2026-10-21; Validation escribe
`fileNameOnly + "." + fileId`). El doble corte con `errorIn` solo existe en el filtro `excludes`
(`removeFileExtension`, `SparkCurator.scala:174`, `:345-351`), no en esta búsqueda. Cada evento busca su processor por
`lineage.fileName`. Sin processor, el evento se descarta sin log (CUR-AP02). Datasets con
`errorInEmptyFileBody` se marcan NOOP.

**Regla aditiva**: agregar el objeto a la lista existente del inner; no reemplazarla. No editar el
conf base. HOCON reemplaza listas al fusionar archivos: si el inner redefine `configs = [...]`, debe
contener **todos** los bloques del inner, incluido el nuevo.

## Claves de etapa (el skill las lee y avisa; no las genera)

| Clave `oxygen.stage.curation.*` | Default | Estado | Nota |
|---|---|---|---|
| `runMode` | `stock` (`stock`, `flow`, `eventsync`) | VERIFICADO | FLOW para el periodo, STOCK para regenerar (RF-CUR-12); la orquestación decide |
| `partitionSize` (propiedad `partionSize`) | `None` | VERIFICADO | typo en la propiedad (CUR-AP12) |
| `enableSSRWrite` | `false` | VERIFICADO | |
| `reasonabilityChecks[]` | vacío | VERIFICADO | |
| `paths { curationDir, curatedDir, archivedDir, curatedStockDir, curatedFlowDir, curatedSSRDir, ... lambdaSync {...} }` | del default | VERIFICADO | todas con `.get` |
| `includeQualityCodes`, `includeErrorCodes` | ? | SUPUESTO (deriva de versión) | si `true`, el curado arrastra `qualityScores`/`errorCodes` |
| `excludes` | ? | SUPUESTO | si un patrón de `excludes` cubre SISBEN, el dataset se marca éxito **sin curar** |
| `INCSYNC` | — | SUPUESTO | no aplica a SISBEN (E13) |

Requisitos del ambiente (leídos con `.get`; si faltan, Curation no arranca):
`oxygen.features.enable.emitStockMetrics` y `oxygen.stage.consistencyCheck.paths.domainDictionaryFile`
(en el código Scala se lee como `oxygen.stage.consistency.get.paths...`, pero la clave HOCON es
`consistencyCheck`: `Motor-Oxigen/oxygen-curation/src/main/resources/local/oxygen-config-default-local.conf`).
Otros: `oxygen.application.lambda = batch`; `oxygen.features.enable.defaultCurationProcessor`
(si está activo, **todos** los patrones usan `DefaultOxygenCurationProcessor` y el processor SISBEN
no corre: verificar que esté apagado).

## Lookups `oxygen.lookups[]`

Ejemplo histórico (delimitador y columna del ejemplo de la skill; los de la ingesta salen de `catalogos[]`):

```hocon
oxygen.lookups = [
  { tableName = "202_SISBEN_PROCESS_ID_TYPE_HO"
    file = ${oxygen.application.paths.lookupHome}"/sisben/202_SISBEN_PROCESS_ID_TYPE_HO.csv"
    delimiter = ","
    keys = [ { index = "tipo_sisben" } ] }
]
```

| Clave | Default | Nota |
|---|---|---|
| `tableName` | obligatoria | llave del mapa |
| `file`, `fileFormat` | `None` | |
| `delimiter` | `None` | default de `LookupFileReadInfo`: SUPUESTO (clase ausente) |
| `processor` | `com.experian.edf.oxygen.lookup.LookupProcessor` | no se emite |
| `keys = [ { index = "col" \| "colA+colB" } ]` | vacío | `caseSensitive` aparece en ejemplos pero el parser no lo lee |

API en el processor: `context.lookup.get(tabla, Map(columna -> valor))` como en `Util.getLookup` de
la extensión de referencia: SUPUESTO (CUR-PT02; `OxygenLookup` no está en las fuentes disponibles).

## Domain dictionary

Archivo `colombia-domain-dictionary-inner.json`; lo consume Synthesis. El skill genera entradas
`{name, description}` desde Capa B (AUD-C1). Formato verificado en la copia del motor (C-CUR-06):
versión 2, `DomainDictionaryV2`. `dataType` y `handler` los fija Synthesis; `dataType` queda por
verificar en el fork (enum fuera del repo). Se fusionan solo las entradas de `fields[]`. Entradas
aditivas; nunca eliminar ni renombrar.

## Constructor y contexto

`class X(context: CurationProcessorContext) extends CurationProcessor(context)`; el motor crea
`CurationProcessorContext(lookupService)` y busca `getConstructor(classOf[CurationProcessorContext])`
(VERIFICADO). Imports 2404: `com.experian.edf.oxygen.curation.engines.CurationProcessor`,
`com.experian.edf.oxygen.domain.TaggedValidatedEvent`,
`com.experian.edf.oxygen.domain.internal.{CuratedEvent, Payload}`,
`com.experian.edf.oxygen.domain.processors.CurationProcessorContext`,
`com.experian.edf.oxygen.lookup.OxygenLookup`.
