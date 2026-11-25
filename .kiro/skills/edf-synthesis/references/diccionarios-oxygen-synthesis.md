# Los tres diccionarios de Synthesis — esquema exacto

Fuente de verdad verificada por lectura directa de código: `Diseño/logica-synthesis.md` §5. Synthesis se gobierna por **tres diccionarios independientes**, no uno. Confundirlos es el error de generación más grave posible en esta etapa — tienen esquemas y propósitos distintos, y generar contenido del diccionario equivocado produce un artefacto que el motor ni siquiera intentará leer donde se espera.

Este skill solo genera contenido para el **primero** (GDD). Los otros dos se describen aquí para que sepas identificarlos y para que nunca los confundas ni generes contenido en ellos sin evidencia explícita de que la ingesta los necesita.

## 1. GDD — Global Domain Dictionary (`domaindictionary.json`) — EL QUE ESTE SKILL GENERA

Raíz: `{"domain": "<nombre>", "version": 2, "fields": [...]}` (HF-71). Claves de cada elemento según `DictionaryFieldV2` (`DomainDictionaryV2.java:46-56`):

| Campo de cada elemento de `fields` | Tipo | Obligatorio | Efecto |
|---|---|---|---|
| `name` | string | sí | nombre del campo del dominio en el GDD: literal una clave publicada por Curation |
| `handler` | `"EUC"` \| `"LAP"` \| ausente | no, default LFP | ausente = LFP implícito (comportamiento por defecto del motor, sin clase propia) |
| `periodicity` | boolean | no, default `true` | si el campo participa en la lógica periódica |
| `complexTypeDefinition` | objeto (`SchemaElement`) | no | definición de un campo compuesto |
| `dataType` | enum `Datatype` (oxygen-global) | no, default `STRING` | no se escribe sin confirmación (sus valores no están en el repo) |
| `synthesizer` | string (FQCN) | no | clase `SynthesisProcessor` custom — este skill NUNCA la asigna, ver `decisiones-pendientes.md` |
| `description` | string | no | texto descriptivo; inerte para el motor |
| `section` | string | no, default `"default"` | sección del campo |
| `enabled` | boolean | no, default `true` | si el campo está habilitado |

**Versión 1, solo histórica:** `"version": 1` (deprecated, con warning del motor) es la del fixture local `domaindictionary-local.json`. `struct` y `singleValued` aparecen solo en ese fixture: no las lee ni el lector v1 ni el v2, así que no se generan.

Reglas verificadas que debes respetar al generar:

- **`handler` y `synthesizer` son ortogonales.** Un campo puede tener `handler` y `synthesizer` a la vez (el fixture real confirma esto con `accountInformationLAP` y `accountInformationEUC`, ambos con el mismo `synthesizer`). Esto no te habilita a asignar `synthesizer` por tu cuenta — solo significa que si el usuario ya confirmó uno, no entra en conflicto con el `handler`.
- **Tres campos de sistema se inyectan automáticamente** por el motor (`inferredAsDeleted`, `shouldDelete`, `fileTag`, todos `LAP`) — **nunca los declares tú** en el array `fields[]` que generas; ya existen sin que el usuario los pida.
- **Tres campos EUC más los agrega el motor** al calcular los EUC: `subscriberRelationStartDate`, `subscriberRelationEndDate` y `firstEventDate` (`DomainDictionaryInternal.scala`, `getAllEUCFields`). Tampoco se declaran (AUD-S3).
- **Claves de un campo en la versión 2** (`DomainDictionaryV2.java`): `name`, `handler` (LFP por defecto), `periodicity` (true por defecto), `complexTypeDefinition`, `dataType` (enum `Datatype` de oxygen-global, `STRING` por defecto; sus valores no están en el repo, así que no se escribe sin confirmación), `synthesizer`, `description`, `section` y `enabled`. `fieldType` es del lector de la versión 1 y la versión 2 lo ignora.
- El motor soporta versión 1 (deprecated, con warning) y versión 2 del formato. Usa siempre `"version": 2` salvo que el usuario pida explícitamente compatibilidad con sistemas que todavía leen versión 1.
- Si el GDD no declara ningún `"version"`, el motor real lanza `OxygenException` — nunca generes un GDD sin ese campo.

## 2. Diccionario de reglas de consistencia (`consistency-checks.json`) — ESTE SKILL NUNCA LO GENERA

Raíz: `{"referenceCache": [...], "checks": [...]}`. Define reglas `CONS-*` con `checkName`, `triggerCondition`, `check` (DSL propio con parser combinators), `shouldFilterEventOnError`, etc.

**Por qué este skill no lo toca:** para que exista contenido real en este archivo, la ingesta necesita reglas de consistencia específicas más allá de las que el framework ya aplica de forma estándar. Si no tienes evidencia explícita de que la ingesta las requiere (una HU, una decisión de negocio citada, o el usuario diciéndolo literalmente), no generes nada aquí — ver `decisiones-pendientes.md`.

## 3. Diccionario de reglas custom de filtrado/visibilidad (`custom-rules.json`) — ESTE SKILL NUNCA LO GENERA

Array plano de reglas de ocultar/marcar campos o eventos completos, con un DSL **distinto** al de consistencia (`this.campo`, `mostRecent(...)`, `xor(...)`). El valor por defecto de la plataforma es un archivo vacío (`custom-rules-empty.json`) — es decir, "sin reglas custom" es el estado normal, no una ausencia que haya que llenar.

**Por qué este skill no lo toca:** mismo criterio que el diccionario de consistencia — sin evidencia explícita de caso de negocio, el archivo vacío es el estado correcto, no un hueco a rellenar.

## Resumen operable

| Diccionario | ¿Este skill genera contenido? | Condición para hacerlo en el futuro |
|---|---|---|
| GDD (`domaindictionary.json`) | **Sí** — es su propósito | — |
| `consistency-checks.json` | No | Evidencia explícita de reglas CONS-* específicas requeridas |
| `custom-rules.json` | No | Evidencia explícita de necesidad de ocultar/marcar campos o eventos |
