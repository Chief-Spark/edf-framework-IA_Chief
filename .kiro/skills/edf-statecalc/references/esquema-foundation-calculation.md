# Esquema exacto de Foundation y Calculation — DTOs reales

Fuente de verdad verificada por lectura directa de código: `oxygen-statecalculation`, DTOs Jackson en `config/foundation/` y `config/calculation/`. Citado también en `Diseño/logica-statecalc.md` §5.1-§5.3. Este skill genera contenido en los dos archivos que describe esta referencia — nunca en ningún otro.

## 1. Foundation (`statecalc-foundation-local.json`)

Raíz: `{"domain": "<nombre>", "fields": [...]}`.

Cada elemento de `fields[]` (DTO `FoundationField`):

| Campo | Tipo | Obligatorio | Default | Efecto |
|---|---|---|---|---|
| `name` | string | sí | — | nombre del campo en el registro homogéneo (Foundation record) |
| `description` | string | no | — | documentación, sin efecto funcional |
| `aliases` | lista de strings | no | — | nombres alternativos; si `aliasEnabled=true` en el `.conf`, el valor también se escribe bajo cada alias |
| `dataType` | enum | no | `STRING` | `INTEGER`, `LONG`, `DOUBLE`, `BOOLEAN` confirmados en el fixture real |
| `type` | enum (`FieldType`) | no | `DERIVED` | `DERIVED` \| `CALCULATED` \| `CUSTOM` \| `CUSTOM_WITH_HISTORY` \| `UNKNOWN` (deprecated, lanza excepción si se usa) |
| `value` | string (script JS) | solo si `type=CALCULATED` | — | cuerpo del script Nashorn, ver §3 |
| `defaultValue` | string | no | — | usado si `DERIVED` no encuentra valor en el evento curado |
| `nullable` | boolean | no | `true` | — |
| `handler` | objeto `{class, lookups}` | solo si `CUSTOM`/`CUSTOM_WITH_HISTORY` | — | FQCN cargado por reflexión con constructor **sin argumentos** — si falta, el motor lanza `StateCalculationConfigException` en construcción, antes de procesar cualquier evento |

**Este skill solo genera campos `type=DERIVED` (implícito, sin la clave `type`) o `type=CALCULATED` con un `value` confirmado por fuente.** Nunca genera `CUSTOM`, `CUSTOM_WITH_HISTORY` ni `UNKNOWN` — los dos primeros requieren una clase Scala que este skill no produce (es config-driven puro, ver `references/decisiones-pendientes.md`), y el tercero está deprecado y siempre falla.

Ejemplos reales transcritos del fixture (`Motor-Oxigen/oxygen-statecalculation/src/main/resources/local/statecalc-foundation-local.json:369-379`): el DERIVED `standardizedIdType` va declarado y el CALCULATED lo lee del registro Foundation.
```json
{ "name": "standardizedIdType", "dataType": "INTEGER" },
{ "name": "personIdType", "dataType": "INTEGER", "type": "CALCULATED", "value": "return field.standardizedIdType" }
```

**Un DERIVED se lee por `name`; un CALCULATED lee el registro Foundation (HF-74, HF-75).** El DERIVED busca en `curatedRecordJson` la clave igual a su `name` (`FoundationFunctions.scala:24-35`; un `name` con punto `a.b` lee el anidado y se guarda como `b`, `:63-71`): el `name` de un DERIVED **es** la clave curada publicada, y un `value` en un DERIVED no se usa. En un CALCULATED, `field` es el registro Foundation que se está armando (`FoundationStateCalculator.scala:52-63`), no el evento curado: `field.X` solo vale si `X` es un DERIVED declarado en `fields[]` (todos los DERIVED se calculan antes que cualquier CALCULATED), un no DERIVED declarado **antes** o un campo inyectado (`pin`, `dataSource`, `subscriber`, `subscriberRelation`, `asOfPeriod`, `parentEventIds`). Sin el DERIVED, `field.standardizedIdType` es `undefined`, el campo no se escribe (`:86-90`) y el XPM lo muestra nulo en silencio. Verificación: `tools/verificar-artefactos-etapa.py statecalc --foundation … --source-spec …` y `tools/verificar-claves.py`.

**Convención de nombre de función Nashorn para campos `CALCULATED`:** el motor compila el `value` dentro de `function foundation_calculated_<name>(field){ ... }`, donde `<name>` es el nombre exacto del campo. El script recibe el registro homogéneo actual como `field` (notación de punto sobre el `HashMap` del evento). Un error en el script se envuelve en `StateCalculationScriptExecutionException` citando el nombre del campo.

**Campos base inyectados automáticamente, nunca declarados en `fields[]`:** `dataSource`, `subscriber`, `subscriberRelation`, `asOfPeriod` (los "guard rail fields" del motor, en mayúsculas internamente) más `pin` y `parentEventIds`. Declarar cualquiera de estos en `fields[]` es redundante — el motor ya los agrega al registro homogéneo antes de resolver los campos configurados.

## 2. Calculation (`statecalc-calculation-local.json`)

Raíz: `{"domain": "<nombre>", "calculations": [...]}`.

Cada elemento de `calculations[]` (DTO `CalculationSection`):

| Campo JSON | Tipo | Obligatorio | Default | Efecto |
|---|---|---|---|---|
| `name` | string | sí | — | se convierte en la clave de salida de la sección |
| `description` | string | no | — | — |
| `type` | string | sí | — | `AGGREGATES` \| `MOST_RECENT_VALUE` \| `MOST_FREQUENT_VALUE` \| `DISTINCT` \| `ALL` \| `CUSTOM_ON_SECTION` \| pluggable. Se resuelve case-insensitive contra un mapa de `CalculationType` registrados; si no existe, `UnsupportedOperationException` listando los tipos soportados |
| `select` | lista de strings | no | — | campos a proyectar en el resultado |
| `groupBy` | lista de strings | no | — | **el único campo real del DTO Java** — ver regla dura de §2.1 |
| `where` | string (script JS) | no | — | predicado booleano Nashorn de filtro, ver §3 |
| `contains` | lista de `{name, section}` | no | — | anida otra sección ya calculada; la sección referenciada debe declararse **antes** en el array `calculations[]` (procesamiento secuencial, el cache de secciones previas es lo único disponible) |
| `transient` (clave JSON) | boolean | no | `false` | si `true`, se calcula y queda disponible para `contains`, pero no aparece en la salida final |
| `enabled` | boolean | no | `true` | si `false`, la sección se omite por completo |
| `numberOfPeriods` | integer | no | — | limita cuántos periodos recientes se consideran antes de agregar |
| `return` (clave JSON) | enum | no | `KEY_VALUE` | `KEY_VALUE` \| `VALUE` |
| `aggregates` | lista de `{function, field, name}` | solo si `type=AGGREGATES` | — | `name` es opcional, si es `null` el resultado usa `field` como nombre de salida |
| `handler` | objeto `{class}` | solo si `type=CUSTOM_ON_SECTION` | — | FQCN cargado por reflexión, constructor sin argumentos |

**Este skill solo genera secciones `type=MOST_RECENT_VALUE`, `AGGREGATES` o `DISTINCT`/`ALL` sin `handler`.** Nunca genera `CUSTOM_ON_SECTION` — requiere una clase Scala que este skill no produce.

**Reglas de `AGGREGATES` que valida el motor** (`CalculationConfiguationValidator.scala`, AUD-SC2):
- `type` va en MAYÚSCULAS exactas: el validador compara `"AGGREGATES"` literal; en minúsculas la sección se procesa sin validar.
- `aggregates[]` no vacío (OXY-SC-1).
- `select` solo si hay `groupBy` (OXY-SC-2), y todo campo de `select` debe estar en `groupBy` (OXY-SC-3).

Verificación en Python, además del `.ps1`: `python tools/verificar-artefactos-etapa.py statecalc --calculation <calc.json> --foundation <foundation.json> --source-spec <source-spec-curation.yaml>` (en modo Colombia, además `--modo colombia --campos-existentes … --secciones-existentes …`, `references/modo-colombia.md` §3).

### 2.1 Regla dura: `groupBy` siempre plano, nunca `grouping.groupBy`

**El DTO Java real (`CalculationSection.java`) solo declara el campo plano `groupBy`.** No existe ningún campo `grouping` en esa clase — verificado por lectura directa del código (`Diseño/logica-statecalc.md` §5.4).

Sin embargo, el fixture de ejemplo del propio repositorio usa la forma anidada `"grouping": {"groupBy": [...]}` en **11 de 12 secciones**, y solo 1 usa la forma plana que el DTO realmente soporta. Esta es una discrepancia real, documentada y **no resuelta empíricamente** (ejecutar la deserialización Jackson está fuera de lo permitido para este framework — no se puede verificar corriendo código). Si Jackson ignora silenciosamente propiedades desconocidas (su comportamiento por defecto), las 11 secciones del fixture que usan `grouping.groupBy` **nunca agruparían de verdad** — un defecto silencioso sin excepción.

**Este skill genera siempre `groupBy` plano**, nunca `grouping.groupBy`, sin excepción y sin preguntar — esta regla ya está fijada como dura en `Diseño/logica-statecalc.md` §5.4 y en `Diseño/yaml-skill-statecalc.yaml` (`precondiciones_bloqueantes`), no es una decisión que este skill deba volver a plantear.

Ejemplo real verificado que usa la forma correcta (plana):
```json
{
  "name": "AggregationOnBalanceGroupBySubSR",
  "type": "AGGREGATES",
  "select": ["subscriber", "subscriberRelation"],
  "groupBy": ["subscriber", "subscriberRelation"],
  "aggregates": [
    {"function": "SUM", "field": "balance", "name": "sum"}
  ]
}
```

### 2.2 Convención de nombre de función Nashorn para `where`

El motor compila cada `where` dentro de `function aggregated_where_<name>(field){ ... }`, donde `<name>` es el nombre exacto de la sección. Filtra registro por registro, convirtiendo el resultado a booleano. **Si la lista filtrada queda vacía, sin `groupBy` la sección vale `null`; con `groupBy` los grupos vacíos no salen y la sección es una lista vacía `[]`** (`QueryFunctions.scala:61`; `CalculationStateCalculator.scala:123-143`, `:162`) — distinción que cualquier artefacto generado debe tener presente si documenta el comportamiento esperado. Como `<name>` es parte de un identificador JavaScript, el `name` de una sección con `where` no lleva `#`, espacios ni otros caracteres inválidos: la función se compila al construir el calculador (`CalculationStateCalculator.scala:50-52`) y la etapa falla al arrancar (HF-77).

### 2.3 Funciones de agregación válidas para `type=AGGREGATES`

Solo estos valores de `function` están implementados y activos (Strategy en Scala, no el enum Java muerto — ver `references/convenciones-oxygen-statecalc.md` §1): `SUM`, `MIN`, `MAX`, `COUNT`, `AVERAGE`. Cualquier otro valor lanza `StateCalculationConfigException` listando los valores permitidos. `AVERAGE` devuelve `null` si el conteo es cero (división fallida capturada internamente).

## 3. Niveles de confianza del mapeo de campos (criterio heredado de Pinning y Synthesis)

Igual que en `edf-pinning` (dimensión 9, `contrato-identitymatcher.md` §2) y en `edf-synthesis` (mapeo campo→handler, `decisiones-pendientes.md` §2), cada campo que este skill podría generar se clasifica en dos niveles:

- **Nivel A — cadena de mapeo completa y verificada:** existe un campo curado SISBEN real de origen, confirmado por una fuente verificable (código real, HU formal, o decisión explícita del usuario en la conversación), Y un nombre de campo Foundation/Calculation confirmado. Solo estos campos se generan.
- **Nivel B — solo nombre XPM de destino propuesto:** el documento de negocio (`Levantamiento.md` §8.3) propone un nombre de campo final para el XPM, pero no confirma el campo curado real de origen ni el nombre que debería tener en Foundation. **Estos campos NUNCA se generan** — se reportan como pendientes.

En el ejemplo histórico del piloto SISBEN (no es fuente: la ingesta SISBEN vigente se rige por su spec §13), de 25 campos de negocio esperados, **solo 2 alcanzaban nivel A**: `personIdType` (origen `standardizedIdType`) y `personIdNumber` (origen `standardizedIdNumber`), CALCULATED que leen los DERIVED `standardizedIdType` y `standardizedIdNumber` (HF-75), ver `Diseño/yaml-skill-statecalc.yaml` (`mapeo_campos`). Los 23 restantes son nivel B — ver `references/decisiones-pendientes.md` §1.
