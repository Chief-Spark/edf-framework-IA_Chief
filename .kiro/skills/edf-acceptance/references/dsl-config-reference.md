# Referencia de configuración y DSL de Acceptance

Claves verificadas en el parser Typesafe del framework (`AcceptanceStage`, `AcceptanceConfigs`) y en
el motor `SparkAcceptor`. **Una clave mal escrita se ignora en silencio** (el lector devuelve `None`).

## 1. Claves HOCON

```hocon
oxygen.stage.acceptance {
  fileConditions = [                       # GLOBAL a todas las ingestas
    { name = "...", expression = "...", reasonCode = "...", isLogged = true }
  ]                                        # name, expression obligatorios; reasonCode "" ; isLogged false
  configs = [
    {
      filePattern = "..."                  # obligatoria
      fileActions {                        # obligatorio (el motor hace .get)
        reject = "..."                     # obligatorio
        accept = "..."                     # obligatorio
        manualReject = "..."               # opcional, solo externalAction(...)
        manualAccept = "..."               # opcional, solo externalAction(...)
        manualReview = "..."               # opcional (spec fileActions.manualReview), sintaxis nueva; solo SparkDatasetAcceptor (AUD-A4)
      }                                    # si falta una clave queda None (no hay default "true")
      recordAcceptance = "..."             # opcional; sin ella se aceptan todos los registros
      customReasonForAutomaticRejectionEnabled = true   # default false
      lookupMapping { tableName = "...", mapFromColumn = "...", mapToColumn = "..." }  # incompleto => ignorado
      contextualOverride {
        inferDelete    { enabled = false, lookBackPeriod = 1, deletePredicates = [["campoA", "campoB"]] }
        explicitDelete { enabled = false, lookBackPeriod = 1, deletePredicates = [[...]] }
      }
      # processor: NO se emite (default AcceptanceProcessor, nadie lo invoca)
    }
  ]
  paths { acceptanceDir, acceptedDir, rejectedDir, rejectedFilesDir, rejectedRecordsDir, logsDir }  # de etapa; no tocar
  datasetFormat { input, output }          # de etapa; default oxygen.application.storage
}
```

Requisitos del ambiente (no los genera el skill): `oxygen.application.lambda = batch`;
`oxygen.features.enable.emitStockMetrics` **debe existir** (se lee con `.get`);
`oxygen.features.enable.defaultOutputStructure` (default `false`: al terminar se borra la estructura
por defecto de salida).

## 2. `orden_real_de_evaluacion`

`E4 → (si no hay rechazo automático) E5 → E6 → E7 → E8 → E9 → E10-E13`

| Etapa | Qué hace | Clave | Si falla / falta |
|---|---|---|---|
| E1-E3 | Lista `validated/`, arma la instrucción, busca la config cuyo `filePattern` **coincide entero** con la ruta **sin extensión** (dos extensiones si contiene `errorIn`) | `filePattern` | Sin coincidencia: el código registra "No acceptance configuration found", pero el `match` anidado puede lanzar `MatchError` y cortar la corrida completa (ACC-AP01). Tratarlo como fallo duro |
| **E4** | Rechazo automático si el nombre contiene `errorInHeader`, `errorInFooter`, `errorInFileName`, `errorInHistoricAttribute`, o `errorInEmptyFileBody` y `accept` **no** contiene `isEmptyBodyFile`. Con `customReasonForAutomaticRejectionEnabled` evalúa antes `reject` para registrar el `reasonCode`; si no rechaza, mueve igual a `rejected/files` | `fileActions.accept`, `customReason…` | — |
| E5 | `manualReject` (sintaxis nueva, sin leer datos) | `manualReject` | Ausente ⇒ se salta |
| E6 | Lee eventos y arma la tabla `qs`/`err` (mapas por evento) | — | — |
| E7 | `manualAccept` | `manualAccept` | Ausente ⇒ se salta |
| **E8** | `reject`: compila en sintaxis nueva; si no compila, **cae en silencio** a la antigua | `reject` | Error ⇒ `false` |
| **E9** | `accept`: igual; si `false` ⇒ archivo **pendiente** | `accept` | Error ⇒ `false` |
| **E10** | Por registro: busca la config por `lineage.fileName` (¡segunda clave!) y evalúa `recordAcceptance` | `recordAcceptance` | Error ⇒ **`true`** (acepta) |
| E11 | Conteos `acceptedRecords`, `rejectedRecords`, `totalRecords` (Kafka) | — | — |
| E12 | `contextualOverride`: genera `inferred-<archivo>-asDeleted.<id>` | `contextualOverride` | Excepción ⇒ vacío **silencioso** |
| E13 | Escribe aceptados/rechazados, mueve el validado a `processed` | — | Si 0 aceptados ⇒ archivo marcado rechazado |

## 3. `expresiones_dsl`

### 3.1 Registro (`recordAcceptance`, RecordValidationParser)

Operadores: `errorcodeIn("a,b")`, `errorcodeNotIn("a")`, `errorcodeInList("a","b")`,
`errorcodeNotInList(...)`, `errorlevelIn(...)`, `errorlevelNotIn(...)`, `errorlevelInList(...)`,
`errorlevelNotInList(...)`, `count(errors)`, aritmética (`+ - * /`, `> >= < <= == !=`), `and`, `or`,
paréntesis, `true`/`false`, nombres de QS (se sustituyen por su valor).

- **ACC-R01 subcadena**: los valores del evento se unen con coma y se usa `contains`.
  `errorlevelNotIn("1")` también rechaza `"10"` o `"-1"`; `errorcodeNotIn("ERR_01")` excluye
  `"ERR_010"`. Regla: códigos de longitud fija que no sean subcadena de otro; niveles de un carácter.
- **ACC-R02 sustitución de QS**: antes de parsear, cada nombre de QS del evento se reemplaza
  (`replaceAll`) por su score en **todo** el texto. Un QS llamado `in`, `error`, `1`… corrompe la
  expresión y el registro se acepta por default. Regla: ningún nombre de QS dentro de `recordAcceptance`.
- **ACC-R03 dos claves**: `filePattern` debe coincidir con la ruta sin extensión **y** con
  `lineage.fileName`; si un registro no encuentra config, `.get` lanza en el ejecutor.
- Default si falla: **`true`**.

### 3.2 Archivo, sintaxis antigua (`ExternalActionParser` → `AggregatesParserDF` → `QualityScoreParser`)

Agregados: `average("qs.X")`, `sum(...)`, `min(...)`, `max(...)`, `count(*)`, `duplicates(...)`,
`countByField(...)`, `sumByFields(...)`; además `externalAction("k") == "v"`, `headerErrorIn(...)`,
`headerErrorNotIn`, `footerErrorIn/NotIn`, `errorIn/NotIn`, `isEmptyBodyFile`. `count("campo")` **no**
existe en el parser de archivo. No registra `reasonCode`. Default si falla: `false`.
`average` sobre un QS que ningún evento trae devuelve null ⇒ la evaluación falla ⇒ `false` (inferido).

### 3.3 Archivo, sintaxis nueva (Lexer/Parser/AST)

Gramática exacta del lexer: `(anyOf|allOf) "(" ident ("," ident)* ")" [ (hasErrorlevelIn|hasErrorlevelNotIn) "(" int ("," int)* ")" ]`
con `ident = [a-zA-Z_][a-zA-Z0-9_]*` y la frase completa (nada más alrededor: **no** admite `and`/`or`).

- Cada condición citada se evalúa con el parser de archivo; si falla vale `false`.
- Una condición no definida se **descarta** (log de error). `allOf` con lista vacía ⇒ `false`.
- `hasErrorlevelIn(n)`: verdadero si alguna condición cumplida tiene severidad `n` según `lookupMapping`;
  un `reasonCode` sin severidad en el lookup se omite del chequeo.
- Registra `reasonCode: resultado` de las condiciones con `isLogged = true`.

## 4. Ejemplo histórico: expresiones SISBEN (D7; no es fuente)

**Ejemplo histórico (HF-30).** Muestra solo la **forma** de un bloque completo. Los valores (`filePattern`, umbral,
condiciones, `reasonCode`, `customReasonForAutomaticRejectionEnabled`) salen de la HU y la spec de la ingesta, no de
aquí. Por ejemplo, `customReason… = true` es el valor de esa versión histórica: el motor usa `false` si la clave
falta (`SparkAcceptor.scala:166`) y la spec de cada ingesta decide.

```hocon
fileConditions = [ { name = "RQS_BAJO_UMBRAL_SISBEN", expression = "average(\"qs.RQS\") < 0.90", reasonCode = "SISBFA00001", isLogged = true } ]
reject = "anyOf(RQS_BAJO_UMBRAL_SISBEN)"
accept = "average(\"qs.RQS\") >= 0.90"
recordAcceptance = "errorlevelNotIn(\"1\") and errorcodeNotIn(\"OxygenDuplicate\")"
customReasonForAutomaticRejectionEnabled = true
```

Una sola escala (0..1). `reject` y `accept` son complementarios: no hay estado pendiente salvo que
falte `RQS` (contrato roto con Validation).
