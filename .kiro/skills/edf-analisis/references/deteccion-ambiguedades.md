---
inclusion: manual
---

# Detección de ambigüedades y formulación de preguntas abiertas

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-11-05 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; formato según `05-convenciones.md`; metodología según `04-reglas-analisis.md` §3 y §5 |
| Naturaleza | Procedimiento operativo de `edf-analisis`, `[P]`. Las reglas que cita de los steerings conservan el estado que tienen allí. No cierra preguntas abiertas, no elige entre fuentes contradictorias y no rellena huecos |
| Fuentes | `.kiro/steering/04-reglas-analisis.md` §3, §4.4, §5, §5.1, §7; `.kiro/steering/05-convenciones.md` §3, §4.3, §6, §7; `.kiro/steering/07-contratos-frontera.md` §3, §5.1; `.kiro/skills/edf-orquestador/references/04-contratos-por-skill.md` (etapa 0); adaptación de la skill externa `deteccion-ambiguedades` 1.0.0 (paquete `analisis-edf`, que no forma parte de este framework) |
| Aprobador | PENDIENTE-MANUAL (`[A:OQ-06]`) |

## 1. Objetivo y alcance

Dar a `edf-analisis` un procedimiento común para decidir **cuándo algo es una ambigüedad**, **cómo se escribe la pregunta abierta** y **si bloquea el cierre de la etapa 0**.

**Cubre:** los seis patrones de ambigüedad, el criterio bloqueante / no bloqueante, el formato de la pregunta `PA-nnn`, el marcado `[A:PA-nnn]` y el procedimiento de detección.

**No cubre:**
- La metodología de análisis completa (`04-reglas-analisis.md` §3).
- El registro de la pregunta en `estado-ingesta.json`, su estado y su cierre. Eso lo hace `edf-orquestador` con decisión humana registrada.
- Los casos reales del proyecto. Viven en `04-reglas-analisis.md` §5.1 y no se copian aquí (`05` §7).

## 2. Principio

**Detectar y preguntar. Nunca asumir.** Ante un vacío, el análisis no elige la lectura más probable: registra la pregunta, marca como `[A:PA-nnn]` toda afirmación que dependa de ella y, si la ambigüedad afecta un entregable, bloquea el cierre (`04` §5). Lo que esta referencia nunca hace (`04` §4.4):

- Cerrar una pregunta abierta.
- Elegir entre dos fuentes que se contradicen. Se registran ambas, con su cita textual.
- Rellenar un hueco con el valor más probable.

Una sesión de transferencia sirve para detectar que falta una regla, no para fijarla (`04` §7). Una marca de pendiente en una sesión abre una pregunta; no la responde.

## 3. Los seis patrones

| # | Patrón | Cómo se detecta | Dónde ver un caso real |
|---|---|---|---|
| 1 | **Dato no confirmado** | La fuente dice algo que alguien dejó "por validar", o lo dice una sola persona y nada lo respalda | `04` §5.1: `fec_digitacion`; regla retirada en vivo; semántica de LFP / LAP / EUC |
| 2 | **Contradicción entre fuentes** | HU, spec y layout no dicen lo mismo sobre el mismo atributo | `04` §5.1: delimitador y número de campos |
| 3 | **Regla en vuelo** | Una regla o un catálogo va a cambiar y no está claro cuándo | `04` §5.1: catálogos que cambian "a mitad de camino" |
| 4 | **Colisión de códigos** | Dos fuentes comparten un identificador que debería discriminar | `04` §5.1: tipo de evento 30 |
| 5 | **Jerarquía indeterminada** | No se sabe la precedencia o la clasificación de la fuente. **Nunca es bloqueante:** la fuente oficial es un dato informativo (`edf-orquestador/prompts/00`) | `04` §5.1: "¿SISBEN es fuente oficial?" |
| 6 | **Dimensión ausente** | A un campo le falta alguna de las **nueve dimensiones** de `07` §3, o al contrato del archivo alguna de sus seis dimensiones (`04` §4.2) | Sin caso registrado en `04` §5.1 |

Los patrones 1, 4 y 5 ya ocurrieron en el proyecto. La lección registrada en `04` §5.1 es que detectar temprano es la función del análisis, no una cortesía del proceso.

## 4. Bloqueante o no bloqueante

Determina qué etapa queda detenida. **El bloqueo se mide por `etapa_afectada`:** una pregunta bloqueante detiene solo la etapa a la que afecta (su insumo, su generación, su aprobación y la transición hacia ella), no las demás (`.kiro/skills/edf-orquestador/references/03-gobierno-riesgo-aprobacion.md` §3, condición 4). El gate de la etapa 0 considera solo las preguntas con `bloqueante: true` y `etapa_afectada` ∈ {`analisis`, `validation`}: `validation` cuenta porque su insumo se aprueba junto con la etapa 0 (`.kiro/skills/edf-orquestador/references/04-contratos-por-skill.md`, etapa 0). Una pregunta bloqueante de una etapa posterior (por ejemplo, `state_calculation`) queda abierta y detiene esa etapa, no la etapa 0. Una pregunta sin `etapa_afectada` se trata como bloqueante de todas las etapas (en la duda, bloqueante). El skill propone el valor de `bloqueante` y de `etapa_afectada` al abrir la pregunta. No reclasifica ni cierra una pregunta ya registrada.

**Bloqueante para su `etapa_afectada`** si la respuesta puede cambiar:

- El efecto de la violación de una regla, sobre todo si pasa a ser o deja de ser **rechazo del registro** (`07` §5.1).
- La llave de identidad de un campo (`07` §3, dimensión 8) o el valor de `reusePIN`.
- El contrato del archivo, en cualquiera de sus seis dimensiones (`04` §4.2).
- La clasificación público / PII de un campo (`07` §3, dimensión 2; `08-seguridad-y-pii.md`).
- El tipo de dato de un campo (`07` §3, dimensión 3).
- El umbral de calidad `qualityThreshold` o la fórmula del FQS (`02-lexico-edf.md`).
- El número de campos a mapear (`04` §4.1).

**No bloqueante** solo si la respuesta no cambia ningún valor de los artefactos ni del insumo de la etapa afectada, por ejemplo:

- Afecta un campo opcional cuya violación no rechaza el registro.
- Es una mejora de documentación.
- Es una regla futura con fecha conocida que no aplica a esta corrida.

**En la duda, bloqueante.** Bloquear cuesta una pregunta al Desarrollador. No bloquear puede dejar pasar un supuesto invisible dentro de un contexto aprobado.

Una pregunta no bloqueante sigue abierta: la afirmación que dependa de ella queda `[A:PA-nnn]` y no se convierte en instrucción de generación (`05` §3).

**Qué etapa afecta.** `etapa_afectada` es la primera etapa cuyos artefactos o cuyo insumo cambian con la respuesta (identificador de `00-framework-edf.md` §2). Si la respuesta cambia un artefacto de la etapa 0 o `source-spec-validation.yaml`, la etapa afectada es `analisis` o `validation` y la pregunta frena la etapa 0. Si solo cambia el insumo de una etapa posterior, la etapa afectada es esa etapa: la pregunta no frena la etapa 0 y frena la aprobación del insumo y la transición hacia esa etapa.

## 5. Formato de la pregunta abierta

Cada pregunta se presenta como ficha. Lo que tiene destino en el expediente usa el mismo nombre que `preguntas_abiertas[]`; el resto solo vive en la ficha, porque el esquema del expediente no admite campos adicionales.

```markdown
### PA-nnn · <la pregunta, en una frase interrogativa>

| Dato de la ficha | Contenido | Destino |
|---|---|---|
| Patrón | <1 a 6 de §3> | Solo la ficha |
| Bloqueante | sí / no (§4) | `bloqueante` (booleano) y columna `Bloqueante` de `contexto-ingesta.md` §6 |
| Campo o atributo afectado | <nombre en origen y curado> | Fila del campo en `mapeo-campos.md` |
| Etapa afectada | <identificador de etapa, `00-framework-edf.md` §2> | `etapa_afectada` |
| Qué dice cada fuente | HU: «…» · Spec: «…» · Layout: «…» | `origen`; si es patrón 2, también `contexto-ingesta.md` §4 |
| Por qué no la resuelvo | <la contradicción concreta o la regla que lo prohíbe, `04` §4.4> | Solo la ficha |
| Alternativas | <cada lectura posible y su consecuencia, sin recomendar una> (`04` §5, paso 5) | Solo la ficha |
| Dirigida a | Desarrollador, que escala al dueño del dominio (`06` §5) | Solo la ficha |
| Qué desbloquea | <qué artefacto o campo puede cerrarse al responder> | Solo la ficha |
```

El `id` continúa la numeración de `preguntas_abiertas[]` del expediente: no se reutiliza un ID existente. El patrón válido es `PA-` más al menos tres dígitos. El estado inicial y los demás campos del expediente los asigna `edf-orquestador`.

Reglas de forma:

- **Se cita textualmente** lo que dice cada fuente, con el formato de `05` §6: `archivo §sección` para documentos, `[<código> | hh:mm:ss]` para sesiones. Una pregunta que parafrasea obliga a releer la fuente.
- **Se formula cerrada** cuando se pueda («¿es numérico o carácter?»), no abierta («¿cómo es el campo?»). Una pregunta cerrada se responde en un mensaje.
- Las citas llevan nombres de campo y reglas, nunca valores reales ni PII (`08-seguridad-y-pii.md`).
- Una sesión se cita como contexto (nivel 4 de `04` §7), nunca como la respuesta.

## 6. Procedimiento

1. **Verificar insumos.** La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. (`04` §3, paso 1).
2. Recorrer `mapeo-campos.md` buscando celdas vacías, `?` y valores inferidos, campo por campo y dimensión por dimensión → patrón 6.
3. Contrastar HU, spec y layout atributo por atributo. Buscar activamente la contradicción, no solo la confirmación (`04` §3, paso 4) → patrón 2.
4. Buscar marcas de pendiente en las fuentes y en las transcripciones («me lo llevo», «lo valido», «por confirmar», «queda pendiente») → patrón 1.
5. Verificar códigos e identificadores contra otras fuentes que consten en lo leído o en `expedientes/` → patrón 4.
6. Si las fuentes declaran la clasificación de fuente oficial, registrarla; si no, `indeterminado` sin pregunta (patrón 5, no bloqueante).
7. Revisar si alguna regla o catálogo se anuncia como futuro o modificable → patrón 3.
8. Clasificar cada pregunta como bloqueante o no según §4.
9. Registrar:
   - La pregunta en `contexto-ingesta.md` §6 y, si hay contradicción, en §4.
   - `[A:PA-nnn]` en la columna `Estado` de la fila del campo afectado en `mapeo-campos.md`.
   - La pregunta en la entrega al Desarrollador, para que el Orquestador la registre.
10. Si queda una pregunta bloqueante abierta con `etapa_afectada` ∈ {`analisis`, `validation`}, la etapa 0 **no** está lista para aprobar y se dice así al entregar (`04` §4.3). Las bloqueantes de etapas posteriores se entregan igual, con su etapa afectada, y no frenan la etapa 0. Una pregunta que solo vive en el chat se pierde en la sesión siguiente.

## 7. Qué no es una ambigüedad

Para no inflar la lista y volverla inútil:

- Un dato que la fuente **sí** da y que parece raro. Se registra como regla o como riesgo en `contexto-ingesta.md` §3 o §7, con su marcador y su cita.
- Una regla que los steerings ya fijan: la regla EPOCH (`07` §3, dimensión 4), los efectos de violación (`07` §5.1) o el valor de `qualityThreshold`.
- Una decisión de implementación de una etapa posterior. No es del análisis.
- Un supuesto ya declarado con dueño y consecuencia (`04` §6). Si falta información de la fuente para confirmarlo, esa falta sí se registra como pregunta.
- Un punto que el steering o una `reference` dejan abierto (`[A:*]`, `[PM]`, `OQ-*`) y que la HU o la spec de la ingesta responden. La respuesta de la ingesta manda para esa ingesta (`00-framework-edf.md` §6): se cita como `[C]`, no como supuesto.
- Una diferencia entre la spec de la ingesta y un ejemplo del framework (`assets/examples/`, ejemplos de los steerings). Los ejemplos ilustran; la spec manda.

## 8. Uso desde `SKILL.md`

- Modo 1, paso 3: al abrir una `PA-nnn`, decidir `bloqueante` con §4 y escribir la ficha con §5.
- Modo 2, paso 2: un valor no confirmado queda `[A:PA-nnn]` y no pasa a ninguna `instruccion_generacion*` (§4, último párrafo).

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Aprobador designado | `[A:OQ-06]` |
| 2 | El patrón, el destinatario y lo que desbloquea cada pregunta no tienen campo en el esquema del expediente. Agregarlos sería un cambio de esquema fuera del alcance de esta referencia | `[PM]` |
| 3 | Firma humana de la referencia | `[P]` |

## Pie de trazabilidad

- Procedimiento adaptado de la skill externa `deteccion-ambiguedades` 1.0.0. Se conservan los seis patrones, el criterio bloqueante con su regla de duda, el formato de la pregunta y la sección de lo que no es ambigüedad. Se alinean a este framework las dimensiones por campo, los marcadores, el campo `bloqueante` y las citas.
- Metodología y protocolo de incertidumbre: `04-reglas-analisis.md` §3, §4.4, §5, §5.1, §7. Marcadores y citas: `05-convenciones.md` §3, §6. Dimensiones por campo y efecto de la violación: `07-contratos-frontera.md` §3, §5.1. Gate de la etapa 0: `.kiro/skills/edf-orquestador/references/04-contratos-por-skill.md`. Esquema de la pregunta: `.kiro/skills/edf-orquestador/templates/estado-ingesta.schema.json` (`preguntaAbierta`).
- Última verificación de referencias: 2026-11-05.
