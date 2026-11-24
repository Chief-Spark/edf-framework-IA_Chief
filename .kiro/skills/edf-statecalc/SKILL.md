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
  ni Synthesis (otros skills) ni para el análisis de requerimiento. NUNCA genera código Scala: para
  SISBEN el passthrough `MOST_RECENT_VALUE` evita el `CustomOnSectionProcessor`; un caso futuro que
  lo requiera es una decisión de alcance nueva. Tampoco genera `grouping.groupBy` anidado, solo
  `groupBy` plano.
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


Este skill convierte un mapeo de campos ya confirmado (normalmente ya resuelto en un YAML intermedio como `yaml-skill-statecalc.yaml`) en dos artefactos de configuración declarativa listos para revisión humana: el bloque `fields[]` de Foundation y una sección `CalculationSection` de Calculation.

El principio que gobierna todo lo que sigue es el mismo que en `edf-pinning` y `edf-synthesis`, llevado al extremo más estricto de las tres etapas: **genera solo cadena de mapeo completa, nunca nombres de campo inventados a partir de un nombre de negocio en español.** Para la ingesta de referencia de este proyecto, de 25 campos de negocio esperados, **solo 2 alcanzan ese nivel de confirmación** (`personIdType`, `personIdNumber`). Este skill no cierra esa brecha por iniciativa propia — un nombre de campo mal inferido en Foundation se propaga silenciosamente a Calculation y de ahí al XPM, sin que el motor lo rechace (Foundation con `DERIVED` simplemente no encuentra el valor y usa `defaultValue` o `null`, sin fallar).

## Antes de generar nada: reúne el insumo

Necesitas, como mínimo, por cada campo del Behavioral Profile de la ingesta:

1. El campo curado real de origen (el nombre que trae el evento sintetizado de la etapa Synthesis/StateCalc).
2. El nombre que debe tener el campo en el registro Foundation (`name` de `FoundationField`).
3. Si el campo es `DERIVED` (lectura directa) o `CALCULATED` (requiere un script Nashorn) — y si es `CALCULATED`, el script exacto confirmado por fuente.
4. Si el campo participa en alguna sección de Calculation: como `select`, como parte de `groupBy`, o como `field` de un `aggregates[]`.
5. La cita de la fuente que confirma cada uno de los puntos anteriores: código real, HU formal, o confirmación explícita y consciente del usuario en esta conversación. Un nombre de destino XPM propuesto por un documento de negocio (ej. "el campo debería llamarse `primerNombre`") **no es, por sí solo, una cadena de mapeo confirmada** — ver `references/decisiones-pendientes.md` §1 para el precedente exacto de este criterio.

Si el usuario ya tiene un YAML intermedio de State Calculation para su ingesta (siguiendo la estructura de `.kiro/skills/edf-analisis/assets/examples/sisben/yaml-skill-statecalc.yaml`), toma el insumo de ahí — sección `mapeo_campos`, filtrando por `nivel_confianza: "A"`. Recuerda que en ese YAML de referencia la gran mayoría de los campos quedó en nivel B o `[A:sin-clasificar]`: eso es el estado esperado para esta etapa, no un defecto del YAML.

## El flujo de generación

### 1. Clasifica cada campo por nivel de confianza

Separa el insumo en nivel A (cadena de mapeo completa: origen confirmado + nombre Foundation confirmado — van a `fields[]` activo) y todo lo demás (nivel B, solo nombre de destino propuesto, o `[A:...]`/`[P]`/sin marcador — nunca entran a `fields[]`). Si el usuario insiste explícitamente en incluir un campo de nivel B, inclúyelo solo bajo esa insistencia y márcalo en tu respuesta de texto como no confirmado por fuente — nunca lo presentes con la misma certeza que un campo de nivel A. Ver `references/decisiones-pendientes.md` §1.

### 2. Genera `fields[]` de Foundation desde `assets/foundation-fields.json.template`

Copia la plantilla y sustituye cada `{{PLACEHOLDER}}`:
- `{{DOMINIO_NOMBRE}}`: nombre del dominio de la ingesta.
- Un objeto por campo de nivel A: `type` omitido (DERIVED implícito) si el campo se lee directo del evento, o `"type": "CALCULATED"` con un `"value"` (script Nashorn) solo si el usuario confirmó ese script explícitamente — nunca inventes la lógica de un campo calculado.
- `dataType` solo si es distinto de `STRING` (el default); usa `INTEGER`, `LONG`, `DOUBLE` o `BOOLEAN` según confirme la fuente.
- **Nunca** declares `dataSource`, `subscriber`, `subscriberRelation`, `asOfPeriod`, `pin` ni `parentEventIds` — el motor los inyecta automáticamente antes de resolver los campos configurados (`references/esquema-foundation-calculation.md` §1).
- **Nunca** generes `type: CUSTOM`, `CUSTOM_WITH_HISTORY` ni `UNKNOWN` — los dos primeros requieren una clase Scala que este skill no produce (es config-driven puro); el tercero está deprecado y siempre falla en runtime.
- Elimina todos los comentarios `//` de la plantilla antes de entregar el archivo final — el JSON real no los admite.

### 3. Genera la sección de Calculation desde `assets/calculation-section.json.template`

Completa solo con los campos ya generados en el paso 2 (nunca un campo que no exista en el Foundation correspondiente):
- `type`: `MOST_RECENT_VALUE` para passthrough directo (igual criterio que el caso de referencia SISBEN), `AGGREGATES` solo si el usuario confirma que necesita agregación real (`SUM`/`AVERAGE`/`MIN`/`MAX`/`COUNT`), `DISTINCT`/`ALL` si corresponde. **Nunca `CUSTOM_ON_SECTION`** — requiere una clase Scala que este skill no produce.
- `groupBy`: **siempre la clave plana `groupBy`**, nunca `grouping.groupBy` anidado — esta es una regla dura ya fijada (`references/esquema-foundation-calculation.md` §2.1), no una decisión que debas replantear ni preguntarle al usuario. El valor es el "grain" de agrupación (ej. `[personIdType, personIdNumber]` en el caso SISBEN), confirmado por fuente o por el usuario.
- Si generas una sección `AGGREGATES`: `type` en mayúsculas exactas (el validador del motor compara literal), `select` solo con `groupBy` y todo campo de `select` dentro de `groupBy` (OXY-SC-2/3; AUD-SC2). Verifícalo con `python tools/verificar-artefactos-etapa.py statecalc --calculation <calc.json> --foundation <foundation.json>`.
- Si generas una sección `AGGREGATES`, los valores de `"function"` van siempre en **mayúsculas exactas** (`SUM`, `AVERAGE`, `MIN`, `MAX`, `COUNT`) — a diferencia de `type`, este campo no es case-insensitive en el motor real (`references/convenciones-oxygen-statecalc.md` §2).
- No agregues `where` sin que el usuario confirme el predicado exacto, ni `contains` apuntando a una sección que no esté ya declarada antes en el mismo array.
- El nombre de la sección (`name`) es una propuesta de este skill si el usuario no da uno explícito — repórtalo como tal en tu respuesta, no es un valor confirmado por fuente.

### 4. Entrega los dos artefactos juntos, con un resumen de huecos

Cierra siempre con la lista de campos excluidos de Foundation/Calculation por falta de confirmación, usando el formato de `references/decisiones-pendientes.md`:

> **Pendiente de confirmar:** `<campo o sección>` — `<qué falta exactamente>`. Fuente consultada: `<cita o "ninguna">`.

Para la ingesta de referencia de este proyecto, este resumen normalmente va a listar muchos más campos pendientes que confirmados — eso es correcto y esperado, no un indicio de que el skill está fallando. No entierres esto solo en comentarios de los archivos — repítelo en tu respuesta de texto.

## Qué este skill nunca hace

- No genera código Scala en ninguna forma — ni un `CustomOnSectionProcessor` del motor genérico, ni `ColombiaCustomOnSectionProcessorAlpha` (fuera de alcance, Z2 no verificable). Para SISBEN ya se confirmó que el passthrough resuelve el caso sin processor custom (`Diseño/templates-scala/statecalc/README.md`).
- No genera `grouping.groupBy` anidado bajo ninguna circunstancia, aunque el usuario lo pida o lo haya visto en el fixture de ejemplo del repo — siempre `groupBy` plano.
- No hace el análisis de requerimiento de State Calculation: ese es un tipo de análisis (T3/T4), no esta skill de generación. El insumo se prepara con `edf-analisis` (`yaml-skill-statecalc.yaml`).
- No infiere el nombre de un campo Foundation a partir de su nombre de destino XPM en español (ej. nunca asume que `primerNombre` se llamaría `firstName` o literalmente `primerNombre` en Foundation sin que una fuente lo confirme).
- No genera configuración de prioridad de fuentes del XPM compartido (`bestName`, `bestIdentifications`, `location`) ni resuelve el punto abierto de si SISBEN participa en `bestName` — esa es la frontera con Hydration/XPM, fuera del alcance de esta etapa (`references/decisiones-pendientes.md` §4).
- No genera ninguna clave de "quality threshold" o motor de calidad de datos — no existe tal mecanismo verificado en el código real de esta etapa.
- No transcribe los artefactos generados a un repositorio protegido (fuera de Z1) — son siempre una propuesta para revisión humana.

Si en algún punto te encuentras tentado a "completar" los 23 campos restantes de una ingesta como SISBEN para que el JSON se vea más completo, detente: lee `references/decisiones-pendientes.md` primero. El caso en el que dudas casi siempre ya está catalogado ahí, con el precedente exacto de cómo se trató la primera vez que ocurrió en este proyecto.

## Referencias

- `references/esquema-foundation-calculation.md` — esquema exacto de los DTOs `FoundationField` y `CalculationSection`, la regla dura de `groupBy` plano, y los niveles de confianza A/B.
- `references/convenciones-oxygen-statecalc.md` — nomenclatura real del repo, el `AggregateFunction` duplicado (uno muerto, uno activo), case-sensitivity de `function` vs. `type`, y qué no replicar del repo real.
- `references/decisiones-pendientes.md` — el catálogo de puntos de decisión de negocio que este skill nunca resuelve solo, con el precedente exacto de `yaml-skill-statecalc.yaml` (2 de 25 campos confirmados) y el formato de nota a usar.
