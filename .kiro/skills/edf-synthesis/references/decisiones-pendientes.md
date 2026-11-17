# Catálogo de decisiones de negocio que este skill NUNCA resuelve solo

Lee esto completo antes de generar cualquier artefacto. Es, otra vez, el documento más importante del skill — igual que en `edf-pinning`, pero con un matiz adicional específico de Synthesis que debes entender bien: **aquí el bloqueo típico no es "qué implementación usar", es "qué handler le corresponde a cada campo"**, y ya sabemos por experiencia directa en este proyecto que ese tipo de bloqueo es fácil de rellenar sin darse cuenta con un criterio "razonable" que en realidad es una inferencia no confirmada.

## 1. La nomenclatura de LFP/LAP/EUC ya está resuelta — úsala, no la reabras

Esto NO es una decisión pendiente, es una decisión ya tomada que debes dar por hecha: **LFP = Latest For Period, LAP = Latest Across Period, EUC = Earliest Until Corrected**, con los mecanismos exactos verificados en `Diseño/logica-synthesis.md` §5.1 (no las definiciones de documentos de negocio que fueron corregidas — ver `Memory.md`, entrada "Resolución de conflicto documental"). Si encuentras en algún documento del proyecto una definición distinta de estas siglas, esa fuente ya fue corregida con tachado y nota — no la cites como vigente.

## 2. Mapeo campo → handler: el bloqueo principal, casi siempre sin resolver

Para que un campo entre al array `fields[]` del GDD con un `handler` asignado, necesitas una fuente que confirme **específicamente ese campo** con **ese handler**, no una categoría genérica ("los campos de identidad suelen ser estables") ni una afirmación en bloque ("el resto de los campos usa LAP").

El precedente de este proyecto (`Diseño/yaml-skill-synthesis.yaml`, sección `mapeo_campos_handler`) es la referencia exacta de cómo tratar esto:
- Dos campos de identificador (`standardizedIdNumber`, `standardizedIdType`) se propusieron como EUC por un razonamiento del propio agente ("un identificador debería ser estable") — y se marcaron `[P]` (propuesta, no confirmada), **nunca `[C]`**, precisamente porque el razonamiento no es una cita de fuente.
- Un campo de fecha de primera ocurrencia sí tenía cita textual directa de un documento de negocio ya corregido en nombre — pero aun así se marcó `[P]`, no `[C]`, porque el documento de negocio es nivel 3 de evidencia (no HU formal ni código), y el propio documento advertía que la asignación no estaba confirmada por el dueño de dominio.
- Un bloque de "los 25 campos restantes usan LAP" se dejó **sin desglosar** — no se expandió a una lista de 25 campos individuales inventados.

Replica exactamente este nivel de rigor. Si el usuario te da un campo con handler sin cita de una fuente verificable (código real, HU formal, o su propia confirmación explícita y consciente en la conversación), **inclúyelo en el GDD generado solo si el usuario insiste explícitamente que así lo quiere, y márcalo en tu respuesta como no confirmado por fuente** — no lo omitas silenciosamente, pero tampoco lo presentes como si tuviera el mismo nivel de certeza que un campo con cita directa.

## 3. LFP no tiene handler explícito en el GDD — y eso no es un hueco

Un campo sin `handler` en el JSON es LFP por el comportamiento por defecto del motor (`Diseño/logica-synthesis.md` §5.1). **No generes `"handler": "LFP"` como valor literal** — ese string no es parte del vocabulario real que el motor reconoce como clave explícita del JSON (`DomainDictionaryFieldHandler` solo tiene casos reales para `EUC`/`LAP`; LFP es la ausencia de handler, el caso `else`). Si un campo debe ser LFP, simplemente omite la clave `handler` para ese elemento del array.

**Cuidado con una revalidación pendiente real de este proyecto:** hubo una conclusión de negocio ("LFP no aplica a esta ingesta") que se basaba en una definición de LFP que luego se corrigió — la conclusión nunca se revalidó bajo la definición correcta (`Diseño/logica-synthesis.md` pendiente #10). No asumas que "LFP no aplica" sigue siendo cierto solo porque algún documento lo dijo bajo el nombre antiguo. Si el usuario no te confirma explícitamente que un campo debe ser LFP (omitiendo el handler) o no, trátalo como pendiente, no como resuelto por default.

## 4. Reglas de consistencia y reglas custom: el default es "no generar nada"

Si el usuario no te confirma explícitamente que su ingesta necesita reglas `CONS-*` propias o reglas custom de ocultar/marcar campos, el estado correcto es **no generar ningún contenido** en esos dos diccionarios (ver `references/diccionarios-oxygen-synthesis.md`). La consistencia "estándar" del framework (los checks que ya aplica automáticamente sin configuración adicional) es la base por defecto, no una limitación que haya que compensar generando algo.

## 5. Claves HOCON con conflicto o sin ubicación confirmada

Dos ejemplos reales de este proyecto que debes usar como plantilla de cómo actuar ante algo similar:

- `periodType` apareció en un documento de negocio como decisión confirmada, pero **no corresponde a ninguna clave verificada en el código real** de `oxygen-synthesis` — podría vivir en otra capa (orquestación) no visible en este repo. No generes esta clave en el bloque HOCON sin confirmar primero dónde vive realmente en la configuración del motor.
- `dictionaryFile` (ruta al diccionario de consistencia) depende de si la ingesta usa consistencia estándar o reglas propias — no asumas automáticamente ninguno de los dos caminos sin que el usuario lo confirme.

## Formato de la nota en el artefacto generado

Cuando un campo o clave queda sin resolver, dilo en tu respuesta de texto con esta forma, no solo en un comentario que después se pierde:

> **Pendiente de confirmar:** `<campo o clave>` — `<qué falta exactamente>`. Fuente consultada: `<cita o "ninguna">`.

Si el usuario te confirma la decisión en la misma conversación, inclúyela en el artefacto citando "confirmado por el usuario en esta sesión" en vez de inventar una fuente de código o de negocio que no existe.
