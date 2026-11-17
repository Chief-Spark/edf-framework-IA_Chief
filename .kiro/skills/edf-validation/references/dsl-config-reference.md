# Referencia de configuración HOCON y DSL (Validation)

Fuente de verdad: el parser de configuración del framework (`ValidationConfigs`, `ValidationStage`,
`LookupElements`) y el motor `SparkValidator`. El cargador lee con `try` y devuelve `None` si la clave
falta o tiene otro tipo: **una clave mal escrita se ignora en silencio** y rige el default.

## Bloque por ingesta: `oxygen.stage.validation.configs[]`

| Clave HOCON | Default efectivo | Emitido por el skill |
|---|---|---|
| `filePattern` | obligatoria; se compara contra la **ruta completa** | `.*...` |
| `processor` | obligatoria; `Class.forName` al parsear (la clase debe existir en el classpath) | FQCN generado |
| `fileFormat` | `"text"` | `text` |
| `headerRows` (no `numberOfHeaderRows`) | `1` | `1` |
| `footerRows` (no `numberOfFooterRows`) | **`1`** | `0` explícito |
| `searchAllHeaderFooter` | `false` | `false` |
| `computeMetrics` | `true` | `true` (requerido para deduplicar) |
| `dupKeyCheckEnabled` | `false` | `true` |
| `deDupVersion` | `""` → AS-IS (marca todos) | `EXTN` |
| `duplicateThreshold` | `Integer.MAX_VALUE` | omitido |
| `encoding` | ausente → `textFile` (UTF-8, particionado) | omitido |
| `historicAttributeArgs` (con s) | `""` | omitido |
| `fullScanSchema` | `true` (el motor 2404 no lo lee) | omitido |
| `sumMetrics` | `""` | omitido |
| `validate = [{ filePattern?, fileName?, header?, footer?, contexual|contextual { enabled, qualification, version } }]` | sin expresión el archivo pasa como válido | `fileName`, `header` = `errorlevelNotIn("1")`, `contexual.enabled = false` |
| `metrics = [{ filePattern?, file = [{ metricId, metricExpr }] }]` | vacío | `AverageRQS`, `TotalRegistros` |
| XML: `rootTag, headerTag, footerTag, dataTag, xsdFile, xmlVersion, enableXsdValidation, customXmlTagHandler` | `None`/`false` | no aplica |

Efectos combinados que el skill fuerza:

- Con `footerRows = 1` (default) el motor tomaría el último registro como "footer" para
  `validateFooter`. Por eso se emite `footerRows = 0`.
- La deduplicación corre solo si `computeMetrics = true` **y** `dupKeyCheckEnabled = true`. Con
  `dupKeyCheckEnabled = true` y `keysForDuplicate` vacío, el motor hace `assert(false)`.
- En `EXTN` el motor llama a `listDuplicateEvents` solo si el grupo tiene más de un elemento y es menor
  que el umbral, y **no agrega la marca**: la agrega el processor.

## Claves de etapa y de ambiente (no se escriben; se verifican)

| Clave | Requisito |
|---|---|
| `oxygen.application.lambda` | `batch` (otro valor → `UnsupportedOperationException`) |
| `oxygen.application.historicAttributes` | `false` (default del parser `false`; algunos confs de ejemplo lo ponen en `true`) |
| `oxygen.features.enable.defaultOutputStructure` | `true` → `SparkValidator`; `false`/ausente → `SparkValidatorCustomMode` (borra `appHome` al terminar). F-04 |
| `oxygen.features.enable.emitStockMetrics` | debe existir (se lee con `.get`) |
| `oxygen.stage.validation.paths { validationDir, validatedDir, ... }` | no existe `rejectedDir` en Validation |

## Lookups: `oxygen.lookups[]`

`{ tableName (obligatoria), file, fileFormat, delimiter, processor (default LookupProcessor),
keys = [ { index = "col" | "colA+colB" } ] }`. `caseSensitive` no lo lee el parser. El delimitador por
defecto del lector no está verificado (F-12): el skill lo emite siempre.

## DSL de expresiones de registro/archivo

Operadores: `errorcodeIn("A,B")`, `errorcodeNotIn(...)`, `errorcodeInList("A","B")`,
`errorcodeNotInList(...)`, `errorlevelIn(...)`, `errorlevelNotIn(...)`, `errorlevelInList(...)`,
`errorlevelNotInList(...)`, `count(errors)`, aritmética y `and`/`or`/`not`.

**Semántica de subcadena:** los valores se unen con coma y se busca con `contains`.
`errorcodeIn("ERR_01")` es verdadero si existe `ERR_010`; `errorlevelIn("1")` es verdadero con un nivel
`-1` o `10`. Reglas que el skill verifica:

1. Todos los códigos de la fuente con la misma longitud y ninguno subcadena de otro ni de `OxygenDuplicate`.
2. Ningún código de 11 caracteres (`decodeErrorObject` lo reescribe en la clase base).
3. Niveles de un carácter (`1`, `3`).
4. Los nombres de quality score se sustituyen por su valor en el texto de la expresión: ningún nombre de
   QS puede aparecer dentro de un operador, literal o código.

## DSL de métricas de archivo

`count(*)`, `sum("qs.X")`, `count("campo")`, `average("qs.X")`, `stddev`, `min`, `max`,
`duplicates("campo")`, `countByField(...)`, `sumByFields(...)`, `countByFields(...)`,
`(if(<bool>) warning "texto")`. Los campos `qs.<nombre>` vienen del mapa de quality scores.
