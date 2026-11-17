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

Ejemplos reales transcritos del fixture (`Diseño/logica-statecalc.md` §5.1):
```json
{ "name": "personIdType", "dataType": "INTEGER", "type": "CALCULATED", "value": "return field.standardizedIdType" }
```

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

El motor compila cada `where` dentro de `function aggregated_where_<name>(field){ ... }`, donde `<name>` es el nombre exacto de la sección. Filtra registro por registro, convirtiendo el resultado a booleano. **Si la lista filtrada queda vacía, el resultado es `null`, no `[]`** — distinción que cualquier artefacto generado debe tener presente si documenta el comportamiento esperado.

### 2.3 Funciones de agregación válidas para `type=AGGREGATES`

Solo estos valores de `function` están implementados y activos (Strategy en Scala, no el enum Java muerto — ver `references/convenciones-oxygen-statecalc.md` §1): `SUM`, `MIN`, `MAX`, `COUNT`, `AVERAGE`. Cualquier otro valor lanza `StateCalculationConfigException` listando los valores permitidos. `AVERAGE` devuelve `null` si el conteo es cero (división fallida capturada internamente).

## 3. Niveles de confianza del mapeo de campos (criterio heredado de Pinning y Synthesis)

Igual que en `edf-pinning` (dimensión 9, `contrato-identitymatcher.md` §2) y en `edf-synthesis` (mapeo campo→handler, `decisiones-pendientes.md` §2), cada campo que este skill podría generar se clasifica en dos niveles:

- **Nivel A — cadena de mapeo completa y verificada:** existe un campo curado SISBEN real de origen, confirmado por una fuente verificable (código real, HU formal, o decisión explícita del usuario en la conversación), Y un nombre de campo Foundation/Calculation confirmado. Solo estos campos se generan.
- **Nivel B — solo nombre XPM de destino propuesto:** el documento de negocio (`Levantamiento.md` §8.3) propone un nombre de campo final para el XPM, pero no confirma el campo curado real de origen ni el nombre que debería tener en Foundation. **Estos campos NUNCA se generan** — se reportan como pendientes.

Para la ingesta de referencia de este proyecto (SISBEN), de 25 campos de negocio esperados, **solo 2 alcanzan nivel A**: `personIdType` (origen `standardizedIdType`) y `personIdNumber` (origen `standardizedIdNumber`), ver `Diseño/yaml-skill-statecalc.yaml` (`mapeo_campos`). Los 23 restantes son nivel B — ver `references/decisiones-pendientes.md` §1.
