---
name: edf-statecalc
description: >-
  Genera, para una ingesta nueva, la configuración declarativa de la etapa State Calculation del
  pipeline Oxygen (EDF/IFR): el bloque `fields[]` de `statecalc-foundation-local.json`
  (enriquecimiento fila por fila) y una `CalculationSection` de `statecalc-calculation-local.json`
  (agregación tipo SQL: MOST_RECENT_VALUE, AGGREGATES, DISTINCT o ALL). Usar siempre que se pida
  crear o escribir configuración de Foundation, Calculation, Behavioral Profile o campos del XPM
  para State Calculation, se mencione "config-driven" con esta etapa, o se pida aplicar un YAML
  intermedio tipo `yaml-skill-statecalc.yaml`, aunque no se nombren las clases. No usar para Pinning
  ni Synthesis (otros skills) ni para el análisis de requerimiento. Tiene un modo Colombia para la
  configuración compartida del bureau de Colombia. NUNCA genera código Scala (enricher ni
  `CustomOnSectionProcessor`): si la ingesta lo necesita es una decisión de alcance del PO. Tampoco
  genera `grouping.groupBy` anidado, solo `groupBy` plano.
---

# Skill ConfigGen — State Calculation

## Paso 0 · Habilitación por el expediente (obligatorio, framework EDF)

Esta skill es parte del framework `edf-framework-IA` y opera **debajo del Orquestador** (`.kiro/steering/00-framework-edf.md` §3). Antes de generar cualquier artefacto:

1. **Identifica la ingesta** (`<ingesta-id>`) y abre `expedientes/<ingesta-id>/estado-ingesta.json`. Si no existe → no generes; pide al Desarrollador que inicie el expediente con `edf-orquestador`.
2. **Verifica la habilitación:** en `etapas[]`, la etapa `synthesis` debe estar en `aprobada`, `completada_manualmente` u `omitida` (esta última solo con aprobación de omisión del PO, `edf-orquestador/references/03` §4b), con `transicion_habilitada: true`. Tu etapa en el expediente es `state_calculation` (riesgo fijo N4 `critico`: la aprobación la da el PO o el Aprobador designado).
3. **Verifica el insumo:** `expedientes/<ingesta-id>/insumos/yaml-skill-statecalc.yaml` (formato de referencia: `.kiro/skills/edf-analisis/assets/examples/sisben/yaml-skill-statecalc.yaml`) debe existir y figurar como `aprobado` en los artefactos de tu etapa. Si falta → no generes; deriva a `edf-analisis` ("prepara el insumo de State Calculation para la ingesta <id>").
4. **Si algo falla:** responde con lo que falta, sin generar nada y sin ofrecer cómo saltarse el control. Deriva a `edf-orquestador`.

Excepción única: trabajo sobre fixtures sintéticos de evaluación (`evals/` o `assets/examples/`) fuera de una ingesta real. Decláralo al inicio de tu respuesta.

Al terminar: escribe la propuesta en `expedientes/<ingesta-id>/propuestas/statecalc/` y entrega al Desarrollador la lista de artefactos (ruta, tipo, supuestos abiertos, resultado de las verificaciones) **para que el Orquestador la registre**. Esta skill **no escribe** `estado-ingesta.json` ni `bitacora.md`.

## Modo Colombia · configuración compartida, no un archivo por ingesta (HF-77)

Para ingestas del bureau de Colombia (`bureau.colombia_innersource`) la configuración de State Calculation es **compartida**: la corrida lee todo el sintetizado (`StateCalcIOUtils.scala:174-177`), el registro Foundation de un PIN mezcla los eventos de todas las fuentes (`FoundationStateCalculator.scala:37-51`) y los foundation y calculation se fusionan (`ContextUtils.scala:15-30`; el `domain` del archivo de la ingesta se ignora, `:17`). Detalle y evidencia: `references/modo-colombia.md`. En modo Colombia tu entrega es:

1. **las entradas `fields[]` de la ingesta** (ART-071), que se integran en el foundation compartido **sin repetir un `name` (ni alias) existente**: un nombre duplicado se procesa dos veces y el último gana para **todas** las fuentes (`ContextUtils.scala:15-19`; `FoundationStateCalculator.scala:52-59`). Si el campo ya existe con la misma lectura se reutiliza; con otro `dataType` o significado es un bloqueo N4 que se escala. Prefiere campos solo DERIVED (no dependen de Nashorn);
2. **las secciones de la ingesta** (ART-072), que se integran en el calculation compartido. **Toda sección filtra por fuente** con un `where` (`field.dataSource`/`field.subscriber`, inyectados en `FoundationStateCalculator.scala:47-48`, o una bandera de fuente publicada): sin él, MOST_RECENT_VALUE toma el registro más reciente de cualquier fuente (`MostRecentValue.scala:20-24`). El `where` es Nashorn: depende de `[A:OQ-TR-2]` (steering `07` §5.7) y, mientras siga abierta, la sección queda pendiente; nunca se entrega sin filtro. El `name` de una sección con `where` es parte de la función JavaScript `aggregated_where_<name>` (`QueryFunctions.scala:75-82`): **sin `#`** ni caracteres inválidos para un identificador (con `#sisben` la etapa falla al arrancar), y sin repetir una sección existente;
3. **la lista de verificación del fork** (en tu respuesta de texto, precondición de ART-073): nombres existentes del foundation y del calculation compartidos; **modo delta** (`deltaMode`, `deltaModeAccountLevel`, `doesComputeXpm`, `SparkStateCalculator.scala:187-188`): en modo delta solo se lee el sintetizado delta (`:207-213`) y un PIN sin cambios **conserva su XPM anterior** (`getAndSaveUnchangedV2`, `:246-271`), aunque haya salido del corte vigente; `aliasEnabled` si usas `aliases[]`; el enricher del fork; el JDK del runtime y la decisión de Experian sobre Nashorn (OQ-TR-2).

Verificación: `python3 tools/verificar-artefactos-etapa.py statecalc --modo colombia --calculation <secciones.json> --foundation <campos.json> --source-spec <source-spec-curation.yaml> --campos-existentes <foundation-compartido.json> --secciones-existentes <calculation-compartido.json> [--campo-fuente <bandera>]`. El modo lo fija el Orquestador al abrir el expediente (`edf-orquestador/prompts/00-iniciar-ingesta.md`, paso 2; `references/04-contratos-por-skill.md`, etapa 6) y lo declara el insumo en `modo_colombia.aplica`.


Este skill convierte un mapeo de campos ya confirmado (normalmente ya resuelto en un YAML intermedio como `yaml-skill-statecalc.yaml`) en dos artefactos de configuración declarativa listos para revisión humana: el bloque `fields[]` de Foundation y una sección `CalculationSection` de Calculation.

El principio que gobierna todo lo que sigue es el mismo que en `edf-pinning` y `edf-synthesis`, llevado al extremo más estricto de las tres etapas: **genera solo cadena de mapeo completa, nunca nombres de campo inventados a partir de un nombre de negocio en español.** En el ejemplo histórico del piloto SISBEN (no es fuente: la ingesta SISBEN vigente se rige por su spec), de 25 campos de negocio esperados, **solo 2 alcanzaban ese nivel de confirmación** (`personIdType`, `personIdNumber`). Este skill no cierra esa brecha por iniciativa propia — un nombre de campo mal inferido en Foundation se propaga silenciosamente a Calculation y de ahí al XPM, sin que el motor lo rechace (Foundation con `DERIVED` simplemente no encuentra el valor y usa `defaultValue` o `null`, sin fallar).

## Antes de generar nada: reúne el insumo

Necesitas, como mínimo, por cada campo del Behavioral Profile de la ingesta:

1. El campo curado real de origen (el nombre que trae el evento sintetizado de la etapa Synthesis/StateCalc).
2. El nombre que debe tener el campo en el registro Foundation (`name` de `FoundationField`).
3. Si el campo es `DERIVED` (lectura directa) o `CALCULATED` (requiere un script Nashorn) — y si es `CALCULATED`, el script exacto confirmado por fuente.
4. Si el campo participa en alguna sección de Calculation: como `select`, como parte de `groupBy`, o como `field` de un `aggregates[]`.
5. La cita de la fuente que confirma cada uno de los puntos anteriores: código real, HU formal, o confirmación explícita y consciente del usuario en esta conversación. Un nombre de destino XPM propuesto por un documento de negocio (ej. "el campo debería llamarse `primerNombre`") **no es, por sí solo, una cadena de mapeo confirmada** — ver `references/decisiones-pendientes.md` §1 para el precedente exacto de este criterio.

Si el usuario ya tiene un YAML intermedio de State Calculation para su ingesta (siguiendo la estructura de `.kiro/skills/edf-analisis/assets/examples/sisben/yaml-skill-statecalc.yaml`), toma el insumo de ahí — sección `mapeo_campos`, filtrando por `nivel_confianza: "A"`. Recuerda que en ese YAML de referencia (ejemplo histórico, solo forma) la gran mayoría de los campos quedó en nivel B o `[A:sin-clasificar]`: eso es el estado esperado para esta etapa, no un defecto del YAML.

## El flujo de generación

### 1. Clasifica cada campo por nivel de confianza

Separa el insumo en nivel A (cadena de mapeo completa: origen confirmado + nombre Foundation confirmado — van a `fields[]` activo) y todo lo demás (nivel B, solo nombre de destino propuesto, o `[A:...]`/`[P]`/sin marcador — nunca entran a `fields[]`). Si el usuario insiste explícitamente en incluir un campo de nivel B, inclúyelo solo bajo esa insistencia y márcalo en tu respuesta de texto como no confirmado por fuente — nunca lo presentes con la misma certeza que un campo de nivel A. Ver `references/decisiones-pendientes.md` §1.

### 2. Genera `fields[]` de Foundation desde `assets/foundation-fields.json.template`

Copia la plantilla y sustituye cada `{{PLACEHOLDER}}`:
- `{{DOMINIO_NOMBRE}}`: nombre del dominio de la ingesta.
- Un objeto por campo de nivel A: `type` omitido (DERIVED implícito) si el campo se lee directo del evento, o `"type": "CALCULATED"` con un `"value"` (script Nashorn) solo si el usuario confirmó ese script explícitamente — nunca inventes la lógica de un campo calculado.
- **El `name` de un DERIVED es la clave curada que lee** (`FoundationFunctions.scala:24-35`), sin `value` (no se usa). Un DERIVED con un nombre que Curation no publica vale `null` siempre; para otro nombre en el registro se usa `aliases[]` (solo con `aliasEnabled = true`) o un CALCULATED.
- **En un CALCULATED, `field` es el registro Foundation, no el evento curado** (`FoundationStateCalculator.scala:52-63`): cada `field.X` debe ser un DERIVED declarado en el mismo `fields[]`, un no DERIVED declarado antes o un campo inyectado (`pin`, `dataSource`…). Si el script lee una clave curada (p. ej. `return field.standardizedIdType`), declara también el DERIVED de esa clave (como el fixture del motor, `statecalc-foundation-local.json:369-379`): no es inferir un campo, es la condición para que el CALCULATED no salga `null` en silencio (HF-75).
- `dataType` solo si es distinto de `STRING` (el default); usa `INTEGER`, `LONG`, `DOUBLE` o `BOOLEAN` según confirme la fuente.
- **Nunca** declares `dataSource`, `subscriber`, `subscriberRelation`, `asOfPeriod`, `pin` ni `parentEventIds` — el motor los inyecta automáticamente antes de resolver los campos configurados (`references/esquema-foundation-calculation.md` §1).
- **Nunca** generes `type: CUSTOM`, `CUSTOM_WITH_HISTORY` ni `UNKNOWN` — los dos primeros requieren una clase Scala que este skill no produce (es config-driven puro); el tercero está deprecado y siempre falla en runtime.
- Elimina todos los comentarios `//` de la plantilla antes de entregar el archivo final — el JSON real no los admite.

### 3. Genera la sección de Calculation desde `assets/calculation-section.json.template`

Completa solo con los campos ya generados en el paso 2 (nunca un campo que no exista en el Foundation correspondiente):
- `type`: `MOST_RECENT_VALUE` para passthrough directo de atributos, `AGGREGATES` solo si el usuario confirma que necesita agregación real (`SUM`/`AVERAGE`/`MIN`/`MAX`/`COUNT`), `DISTINCT`/`ALL` si corresponde. **Nunca `CUSTOM_ON_SECTION`** — requiere una clase Scala que este skill no produce.
- `groupBy`: **siempre la clave plana `groupBy`**, nunca `grouping.groupBy` anidado — esta es una regla dura ya fijada (`references/esquema-foundation-calculation.md` §2.1), no una decisión que debas replantear ni preguntarle al usuario. El valor es el "grain" de agrupación (ej. `[personIdType, personIdNumber]` en el ejemplo histórico), confirmado por fuente o por el usuario.
- Si generas una sección `AGGREGATES`: `type` en mayúsculas exactas (el validador del motor compara literal), `select` solo con `groupBy` y todo campo de `select` dentro de `groupBy` (OXY-SC-2/3; AUD-SC2). Verifícalo con `python tools/verificar-artefactos-etapa.py statecalc --calculation <calc.json> --foundation <foundation.json> --source-spec <insumos/source-spec-curation.yaml>` (con `--source-spec` cruza el `name` de cada DERIVED con las claves publicadas; un `select` sobre un alias pasa con aviso de `aliasEnabled`, HF-73).
- Si generas una sección `AGGREGATES`, los valores de `"function"` van siempre en **mayúsculas exactas** (`SUM`, `AVERAGE`, `MIN`, `MAX`, `COUNT`) — a diferencia de `type`, este campo no es case-insensitive en el motor real (`references/convenciones-oxygen-statecalc.md` §2).
- No agregues `where` sin que el usuario confirme el predicado exacto, ni `contains` apuntando a una sección que no esté ya declarada antes en el mismo array. Una sección con `where` lleva un `name` válido como identificador JavaScript (sin `#`, `QueryFunctions.scala:75-82`). En modo Colombia toda sección necesita su filtro por fuente (sección «Modo Colombia»).
- El nombre de la sección (`name`) es una propuesta de este skill si el usuario no da uno explícito — repórtalo como tal en tu respuesta, no es un valor confirmado por fuente.

### 4. Entrega los dos artefactos juntos, con un resumen de huecos

Cierra siempre con la lista de campos excluidos de Foundation/Calculation por falta de confirmación, usando el formato de `references/decisiones-pendientes.md`:

> **Pendiente de confirmar:** `<campo o sección>` — `<qué falta exactamente>`. Fuente consultada: `<cita o "ninguna">`.

En el ejemplo histórico del piloto, este resumen normalmente va a listar muchos más campos pendientes que confirmados — eso es correcto y esperado, no un indicio de que el skill está fallando. No entierres esto solo en comentarios de los archivos — repítelo en tu respuesta de texto.

## Qué este skill nunca hace

- No genera código Scala en ninguna forma — ni un enricher (`FoundationStateEnrichmentProcessor`), ni un `CustomOnSectionProcessor` del motor genérico, ni `ColombiaCustomOnSectionProcessorAlpha` (fuera de alcance, Z2 no verificable). Que una ingesta lo necesite no lo resuelve esta skill: para SISBEN, la regla del corte vigente (HU R-26) **no** se cumple con el passthrough solo; si requiere lógica propia es `[A:CQ-006]`, una decisión de alcance del PO (`references/decisiones-pendientes.md` §5).
- No genera `grouping.groupBy` anidado bajo ninguna circunstancia, aunque el usuario lo pida o lo haya visto en el fixture de ejemplo del repo — siempre `groupBy` plano.
- No hace el análisis de requerimiento de State Calculation: ese es un tipo de análisis (T3/T4), no esta skill de generación. El insumo se prepara con `edf-analisis` (`yaml-skill-statecalc.yaml`).
- No infiere el nombre de un campo Foundation a partir de su nombre de destino XPM en español (ej. nunca asume que `primerNombre` se llamaría `firstName` o literalmente `primerNombre` en Foundation sin que una fuente lo confirme).
- No genera configuración de prioridad de fuentes del XPM compartido (`bestName`, `bestIdentifications`, `location`) ni resuelve el punto abierto de si SISBEN participa en `bestName` — esa es la frontera con Hydration/XPM, fuera del alcance de esta etapa (`references/decisiones-pendientes.md` §4).
- No genera ninguna clave de "quality threshold" o motor de calidad de datos — no existe tal mecanismo verificado en el código real de esta etapa.
- No transcribe los artefactos generados a un repositorio protegido (fuera de Z1) — son siempre una propuesta para revisión humana.

Si en algún punto te encuentras tentado a "completar" los campos restantes de una ingesta (como los 23 del ejemplo histórico) para que el JSON se vea más completo, detente: lee `references/decisiones-pendientes.md` primero. El caso en el que dudas casi siempre ya está catalogado ahí, con el precedente exacto de cómo se trató la primera vez que ocurrió en este proyecto.

## Referencias

- `references/esquema-foundation-calculation.md` — esquema exacto de los DTOs `FoundationField` y `CalculationSection`, la regla dura de `groupBy` plano, y los niveles de confianza A/B.
- `references/convenciones-oxygen-statecalc.md` — nomenclatura real del repo, el `AggregateFunction` duplicado (uno muerto, uno activo), case-sensitivity de `function` vs. `type`, y qué no replicar del repo real.
- `references/modo-colombia.md` — modo Colombia (configuración compartida): nombres sin choque, filtro por fuente, `name` de sección con `where`, modo delta y la lista de verificación del fork (HF-77).
- `references/decisiones-pendientes.md` — el catálogo de puntos de decisión de negocio que este skill nunca resuelve solo, con el precedente exacto de `yaml-skill-statecalc.yaml` (2 de 25 campos confirmados) y el formato de nota a usar.
