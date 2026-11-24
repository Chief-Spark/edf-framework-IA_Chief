---
name: edf-synthesis
description: >-
  Genera, para una ingesta nueva, la configuración declarativa de la etapa Synthesis/Consistency del
  pipeline Oxygen (EDF/IFR): el GDD (`domaindictionary.json`) con el mapeo campo→handler
  (LFP/LAP/EUC) confirmado, y el bloque HOCON de `consistency-application.conf` (clave real
  `oxygen.stage.consistencyCheck`). Usar siempre que se pida crear o escribir configuración,
  diccionario de dominio o mapeo de handlers para Synthesis o Consistency Check, se mencione
  "config-driven" con esta etapa, o se pida aplicar un YAML intermedio tipo
  `yaml-skill-synthesis.yaml` a una ingesta nueva, aunque no se nombre "GDD" o "domaindictionary".
  No usar para Pinning ni State Calculation (otros skills) ni para el análisis de requerimiento de
  Synthesis. NUNCA genera código Scala: para SISBEN se confirmó que no requiere `SynthesisProcessor`
  custom (`Diseño/templates-scala/synthesis/README.md`); un caso futuro que lo requiera es una
  decisión nueva, no una extensión silenciosa.
---

# Skill ConfigGen — Synthesis

## Paso 0 · Habilitación por el expediente (obligatorio, framework EDF)

Esta skill es parte del framework `edf-framework-IA` y opera **debajo del Orquestador** (`.kiro/steering/00-framework-edf.md` §3). Antes de generar cualquier artefacto:

1. **Identifica la ingesta** (`<ingesta-id>`) y abre `expedientes/<ingesta-id>/estado-ingesta.json`. Si no existe → no generes; pide al Desarrollador que inicie el expediente con `edf-orquestador`.
2. **Verifica la habilitación:** en `etapas[]`, la etapa `pinning` debe estar en `aprobada`, `completada_manualmente` u `omitida` (esta última solo con aprobación de omisión del PO, `edf-orquestador/references/03` §4b), con `transicion_habilitada: true`. Tu etapa en el expediente es `synthesis`.
3. **Verifica el insumo:** `expedientes/<ingesta-id>/insumos/yaml-skill-synthesis.yaml` (formato de referencia: `.kiro/skills/edf-analisis/assets/examples/sisben/yaml-skill-synthesis.yaml`) debe existir y figurar como `aprobado` en los artefactos de tu etapa. Si falta → no generes; deriva a `edf-analisis` ("prepara el insumo de Synthesis para la ingesta <id>").
4. **Si algo falla:** responde con lo que falta, sin generar nada y sin ofrecer cómo saltarse el control. Deriva a `edf-orquestador`.

Excepción única: trabajo sobre fixtures sintéticos de evaluación (`evals/` o `assets/examples/`) fuera de una ingesta real. Decláralo al inicio de tu respuesta.

Al terminar: escribe la propuesta en `expedientes/<ingesta-id>/propuestas/synthesis/` y entrega al Desarrollador la lista de artefactos (ruta, tipo, supuestos abiertos, resultado de las verificaciones) **para que el Orquestador la registre**. Esta skill **no escribe** `estado-ingesta.json` ni `bitacora.md`.


Este skill convierte un mapeo de campos ya confirmado (normalmente ya resuelto en un YAML intermedio como `yaml-skill-synthesis.yaml`) en dos artefactos de configuración declarativa listos para revisión humana: el GDD (`domaindictionary.json`) y el bloque HOCON de `oxygen.stage.consistencyCheck`.

El principio que gobierna todo lo que sigue es el mismo que en `edf-pinning`, con un matiz propio de esta etapa: **genera mapeo confirmado, nunca handlers inventados.** El bloqueo típico de Synthesis no es "qué implementación usar" (no hay código que elegir), es "qué handler le corresponde a cada campo" — y ese tipo de bloqueo es fácil de rellenar sin darse cuenta con un criterio que suena razonable pero no es una cita de fuente. Un handler mal asignado (EUC donde debía ser LAP, o LFP implícito donde debía ser EUC) cambia silenciosamente qué valor histórico sobrevive la reconciliación — es un error que no falla en build, falla en los datos.

## Antes de generar nada: reúne el insumo

Necesitas, como mínimo, por cada campo del dominio de la ingesta:

1. El nombre del campo tal como debe aparecer en el GDD (`name`).
2. El handler, si tiene uno confirmado: `EUC`, `LAP`, o explícitamente "LFP" (que en el JSON se traduce en omitir la clave `handler`, nunca en escribir el string literal `"LFP"` — ver `references/diccionarios-oxygen-synthesis.md` §1).
3. La cita de la fuente que confirma ese handler para ese campo específico: código real, HU formal, o confirmación explícita y consciente del usuario en la conversación actual. Una categoría genérica ("los campos de identidad suelen ser estables") o una afirmación en bloque ("el resto usa LAP") no cuenta como confirmación campo por campo.
4. Si el campo requiere `synthesizer` custom (FQCN de un `SynthesisProcessor`) — este skill nunca lo asigna por iniciativa propia, solo si el usuario lo confirma explícitamente.

Si el usuario ya tiene un YAML intermedio de Synthesis para su ingesta (siguiendo la estructura de `.kiro/skills/edf-analisis/assets/examples/sisben/yaml-skill-synthesis.yaml`), toma el insumo de ahí — sección `mapeo_campos_handler`. Recuerda que en ese YAML de referencia casi todo quedó marcado `[P]`, no `[C]`: eso es el estado esperado para una etapa donde la mayoría de los mapeos campo→handler todavía no tienen cita de fuente verificable, no un defecto del YAML. No lo tomes como "ya confirmado" solo porque está en ese archivo.

## El flujo de generación

### 1. Clasifica cada campo por estado de confirmación

Separa el insumo en: campos con handler confirmado por cita de fuente verificable (van al GDD con la clave `handler` explícita), campos confirmados como LFP explícito (van al GDD sin la clave `handler`, nunca con el string literal), y todo lo demás (`[A:...]`, `[P]`, o sin marcador). El tercer grupo no entra al array `fields[]` salvo que el usuario insista explícitamente en incluirlo — y en ese caso, repórtalo en tu respuesta como no confirmado por fuente, nunca lo presentes con el mismo nivel de certeza que un campo con cita directa. Ver `references/decisiones-pendientes.md` §2 para el precedente exacto de este criterio.

### 2. Verifica que no estés generando contenido en los diccionarios equivocados

Antes de tocar cualquier archivo, confirma con el usuario (si no es evidente del insumo) que lo que te está pidiendo es GDD. Si en algún momento el pedido se refiere a reglas de consistencia específicas (`CONS-*`) o a reglas de ocultar/marcar campos, detente: esos son `consistency-checks.json` y `custom-rules.json`, dos diccionarios distintos que este skill nunca genera salvo evidencia explícita de que la ingesta los necesita (ver `references/diccionarios-oxygen-synthesis.md` §2-3). El estado por defecto para ambos es "no generar nada", no un hueco a rellenar.

### 3. Genera el GDD desde `assets/domaindictionary.json.template`

Copia la plantilla y sustituye cada `{{PLACEHOLDER}}`:
- `{{DOMINIO_NOMBRE}}`: nombre del dominio de la ingesta.
- Un objeto por campo confirmado dentro de `fields[]`, con `"handler"` presente solo si es `EUC` o `LAP`; omitido por completo si es LFP.
- `"synthesizer"` solo si el usuario confirmó explícitamente una clase custom, citando de dónde viene esa decisión.
- Fuerza siempre `"version": 2` salvo que el usuario pida compatibilidad explícita con versión 1 (deprecated, con warning del motor).
- **Nunca** declares `inferredAsDeleted`, `shouldDelete` ni `fileTag` — el motor los inyecta automáticamente; declararlos es un error de generación, no una mejora de completitud. Tampoco `subscriberRelationStartDate`, `subscriberRelationEndDate` ni `firstEventDate`: el motor los agrega como EUC (`DomainDictionaryInternal.getAllEUCFields`, AUD-S3).
- Cada `name` debe ser **literal** una clave publicada por Curation (`campos_curados[].destino`): un nombre distinto no da error, el campo simplemente pierde su handler. `python tools/verificar-claves.py <ingesta>` lo cruza (AUD-S2).
- `dataType` (enum `Datatype` de oxygen-global, fuera del repo) se omite salvo confirmación: el motor usa `STRING` por defecto y un valor inválido hace fallar la lectura de todo el diccionario.
- Verificación en Python, además del `.ps1`: `python tools/verificar-artefactos-etapa.py synthesis --gdd <domaindictionary.json> --hocon <bloque.conf>`.
- Elimina todos los comentarios `//` de la plantilla antes de entregar el archivo final — el JSON real no los admite.

### 4. Genera el bloque HOCON desde `assets/consistency-application-block.conf.template`

Completa solo las claves para las que tengas una fuente o una decisión explícita del usuario:
- `eventTypeList`: agrega únicamente el `eventType` real de esta ingesta. Nunca copies los valores de ejemplo del fixture local del repo (`sisben`, `jurad`, `judicial`) salvo que la ingesta sea, literalmente, una de esas tres.
- La clave raíz es `oxygen.stage.consistencyCheck`, nunca `oxygen.stage.synthesis` — lee `references/convenciones-oxygen-synthesis.md` §1 antes de escribir cualquier ruta o comentario; el nombre de producto y el nombre de la clave real son distintos a propósito.
- `paths.domainDictionaryFile` debe coincidir con el nombre de archivo del GDD que generaste en el paso 3.
- `paths.dictionaryFile` queda como `TODO` explícito salvo que el usuario confirme que la ingesta usa reglas de consistencia propias — nunca completes con un path inventado.
- `paths.customRulesFile` **nunca es `null`**: el motor hace `.head` sobre la lista y la etapa falla (AUD-S1). Sin reglas custom confirmadas, escribe `["custom-rules-empty.json"]` y dilo en la respuesta; reglas custom propias, solo con evidencia.
- `synthesisOutputAsTSV`, `checkFileTagEnabled`, `relationshipMigrationProcessorClassName`: completa con el default documentado en la plantilla solo si el usuario no da una instrucción distinta; dilo explícitamente en tu respuesta cuando uses un default en vez de una confirmación.
- Si el usuario pide una clave como `periodType` que no corresponde a ninguna clave verificada en el código real de `oxygen-synthesis`, no la generes en este bloque — repórtala como pendiente de ubicación (podría vivir en otra capa, ver `references/decisiones-pendientes.md` §5).

### 5. Entrega los dos artefactos juntos, con un resumen de huecos

Cierra siempre con la lista de campos excluidos del GDD por falta de confirmación, los campos incluidos pese a no tener cita (si el usuario insistió), y las claves HOCON que quedaron como `TODO`. Usa el formato de `references/decisiones-pendientes.md` §Formato de la nota:

> **Pendiente de confirmar:** `<campo o clave>` — `<qué falta exactamente>`. Fuente consultada: `<cita o "ninguna">`.

No entierres esto solo en comentarios de los archivos — repítelo en tu respuesta de texto.

## Qué este skill nunca hace

- No genera código Scala en ninguna forma — ni un `SynthesisProcessor` custom, ni una clase de ejemplo "por si acaso". Para la ingesta de referencia de este proyecto (SISBEN) ya se confirmó explícitamente que la etapa no lo requiere (`Diseño/templates-scala/synthesis/README.md`); si una ingesta nueva sí lo necesitara, es una decisión de alcance nueva que el usuario debe plantear aparte, no una extensión que este skill asuma por similitud.
- No asigna el handler `LFP` como string literal — LFP es siempre la ausencia de la clave `handler`.
- No genera contenido en `consistency-checks.json` ni en `custom-rules.json` sin evidencia explícita de que la ingesta los necesita.
- No hace el análisis de requerimiento de Synthesis: ese es un tipo de análisis (T2/Skill-Curation en su acepción de etapa), no esta skill de generación. Se prepara con `edf-analisis`.
- No resuelve una revalidación pendiente de negocio con el valor que ya existía bajo una definición de términos (LFP/LAP/EUC) que fue corregida — si una conclusión anterior se basó en la nomenclatura antigua, se trata como no revalidada, no como vigente.
- No completa `dictionaryFile`, `customRulesFile`, `periodType` ni cualquier clave sin ubicación o confirmación verificada con un valor "razonable" para que el bloque HOCON se vea completo.
- No transcribe los artefactos generados a un repositorio protegido (fuera de Z1) — son siempre una propuesta para revisión humana.

Si en algún punto te encuentras tentado a "simplemente elegir un handler que suena razonable" para que el GDD se vea completo, detente: lee `references/decisiones-pendientes.md` primero. El caso en el que dudas casi siempre ya está catalogado ahí, con el precedente exacto de cómo se trató la primera vez que ocurrió en este proyecto.

## Referencias

- `references/diccionarios-oxygen-synthesis.md` — esquema exacto de los tres diccionarios de Synthesis y por qué este skill solo genera el GDD.
- `references/convenciones-oxygen-synthesis.md` — la discrepancia de nombre producto/código (`synthesis` vs. `consistencyCheck`), terminología real del dominio, y qué no replicar del repo real.
- `references/decisiones-pendientes.md` — el catálogo de puntos de decisión de negocio que este skill nunca resuelve solo, con el precedente exacto de `yaml-skill-synthesis.yaml` y el formato de nota a usar.
