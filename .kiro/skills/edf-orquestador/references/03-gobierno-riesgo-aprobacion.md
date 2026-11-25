# Gobierno: clasificación de riesgo, aprobación, bloqueo y bitácora

Subordinado a `.kiro/steering/01-restricciones-principios.md`, `06-riesgo-y-aprobaciones.md` (taxonomía y matriz de aprobadores) y `09-estado-y-trazabilidad.md`. Este documento dice **cómo los aplica el Orquestador** sobre el expediente.
Trazabilidad: `requirements-orquestador.md` R3, R4 · `arquitectura_framework.md` §6.1–6.4, §7 · `especificacion_requerimientos_skills.md` G4.

## 1. Clasificación de riesgo (R3.3)

**Primero** se evalúa el umbral de parada de `01` §6. Si aplica, el Orquestador se detiene y escala: la clasificación no lo habilita a continuar.

Después se clasifica con la taxonomía **N1–N4** de `06` §2 (criterio más alto que aplique, nunca el promedio, y las clasificaciones fijas de `06` §2.2). En el expediente se registra con este valor:

| `06` | Valor en el expediente | Ejemplos típicos |
|:---:|:---:|---|
| N1 | `bajo` | Análisis de la etapa 0 sin generación; agregar un campo al mapeo con sus dimensiones completas |
| N2 | `medio` | Artefacto de las etapas 1–3; catálogo propio nuevo |
| N3 | `alto` | Etapas 4–5; toca la llave de identidad o `reusePIN`; modifica el umbral de calidad; clase compartida `Colombia*` |
| N4 | `critico` | **Cualquier generación de State Calculation**; toque a un `*-common-application.conf`; Z2/Z3; dato real o PII |

Elevadores automáticos a `alto` (N3), sin importar la etapa, salvo que ya sea `critico`:
- `reusePIN` cambia de valor.
- Se modifica el umbral de calidad de Acceptance.
- La evidencia del pipeline es `null` pero se pide avanzar.

La fuente oficial y su jerarquía son datos informativos: declararlos o cambiarlos **no** eleva el riesgo ni bloquea.

La etapa 0 sin generación es siempre N1 `bajo` (clasificación fija de `06` §2.2); sus preguntas bloqueantes no elevan el riesgo, **bloquean** (condición 4 de §3).

## 2. Fricción proporcional al riesgo (R3.5, `06` §3)

| Riesgo | `nivel_friccion` | Qué presenta el Orquestador | Quién aprueba (`06` §4) |
|:------:|---------------------------|-----------------------------|---|
| `bajo` | `ligera` | Resumen de 1 línea + artefacto + "¿apruebo?" | Desarrollador |
| `medio` | `diff_contexto` | Qué cambió, por qué, regla aplicable, alternativas consideradas, qué se rompe si está mal, prueba asociada | Desarrollador |
| `alto` | `detallada` | Todo lo anterior + impacto en otras ingestas + plan de reversión + preguntas abiertas. **Justificación escrita obligatoria** | Desarrollador, con la justificación. Visado del dueño del patrón solo si el Desarrollador lo pide |
| `critico` | `detallada` | **No se propone como acción.** Se documenta como hallazgo y se escala. Solo se registra una aprobación si la da el `PO` o el `Aprobador designado`, con justificación | PO / Aprobador designado `[A:OQ-06]` |

En `alto` y `critico` el campo `justificacion` es **obligatorio**: sin él, la aprobación no se registra y la transición sigue bloqueada. El esquema y `validar-expediente.py` lo verifican.

## 3. Regla de bloqueo (R3.1, R3.2, R3.4)

Antes de habilitar la etapa `N+1`, el Orquestador verifica **todas** estas condiciones:

```
[ ] 1. La etapa N está en estado `aprobada` o `completada_manualmente`, o `omitida` con
       aprobación de omisión (§4b).
[ ] 2. Existe una aprobación registrada en `aprobaciones[]` con `decision: aprobada`,
       que referencia la transición N → N+1 y apunta a un evento de bitácora.
[ ] 3. Todas las dependencias declaradas de N+1 están satisfechas (ver `01-flujo-etapas.md` §2),
       incluido su insumo aprobado de `edf-analisis` (ver `04-contratos-por-skill.md`).
[ ] 4. No quedan preguntas abiertas con `bloqueante: true` cuya `etapa_afectada` sea N o N+1
       (una pregunta sin `etapa_afectada` cuenta para todas las etapas).
[ ] 5. Si la transición exige evidencia de pipeline, `evidencia_pipeline.observada != null`,
       o la etapa N fue aprobada con evidencia **diferida** (§3b).
[ ] 6. Si el riesgo es `alto` o `critico`, la aprobación trae `justificacion` no vacía
       (y en `critico`, el actor es `PO` o `Aprobador designado`).
```

### 3b. Evidencia diferida

El Desarrollador puede cerrar la etapa N y avanzar a N+1 sin haber ejecutado aún la etapa N en el pipeline. Se hace en este orden, que es el mismo de la etapa 0 (aprobar la etapa, luego el insumo de N+1, luego la transición):

1. **Aprobar la etapa N con evidencia diferida** (prompt `03`, `tipo: contexto`, `riesgo: alto`). La aprueba el **Desarrollador** con justificación escrita que diga "evidencia diferida" y por qué. Requisito: todos los artefactos obligatorios de N aprobados. Efecto: la etapa N pasa de `pendiente_evidencia` a `aprobada` con `evidencia_pipeline.observada: null`, y se abre el riesgo `RG-nnn` "evidencia diferida de <etapa>" (`alto`, `abierto`).
2. **Aprobar el insumo de N+1** (prompt `03`, `tipo: artefacto`). Ya es posible porque su dependencia, N, está `aprobada` (prompt `08`, paso 1).
3. **Aprobar la transición N → N+1** (prompts `02` y `03`, `tipo: transicion`, `riesgo: alto`, justificación del Desarrollador). La condición 5 se da por cumplida porque la etapa N tiene la evidencia diferida registrada en el paso 1.

El riesgo "evidencia diferida" se cierra cuando el Desarrollador reporta la evidencia (prompt `05`). La bitácora registra los pasos 1 y 3 como `aprobacion`, con la palabra "diferida" en **Por qué**. No aplica a `state_calculation` (N4): su aprobación sigue siendo del `PO` o del `Aprobador designado`.

### 3c. Revisión de un insumo ya aprobado

Si el insumo de una etapa que ya empezó (N+1 `en_progreso`) tiene que cambiar —por ejemplo, porque la auditoría de su generación encontró que el insumo no expresa una regla de la spec—, se hace en este orden (si el cambio es **no sustantivo** —no cambia ningún valor desplegable—, el aprobador de la etapa puede usar §3d-bis, que no suspende la transición):

1. **El insumo vuelve a `propuesto`** (prompt `08`) con versión nueva y evento `correccion` que cite la aprobación anterior. El hash aprobado queda en esa aprobación; no se borra.
2. **Se suspende la transición N → N+1**: `transicion_habilitada = false` con evento `bloqueo` (la condición 3 deja de cumplirse). La etapa N+1 vuelve a `no_iniciada` (evento `bloqueo`; regla 7b: una etapa empezada exige la transición de su dependencia habilitada) y **no se aprueba ningún artefacto de N+1** mientras dure la suspensión. Sus artefactos generados con el insumo anterior quedan como están (no `aprobado`) y se regeneran con el nuevo.
3. **Re-auditoría y re-aprobación del insumo** (prompt `03`, `tipo: artefacto`, mismo riesgo que la primera vez) con el hash nuevo.
4. **Nueva aprobación de la transición** (prompts `02` y `03`, `tipo: transicion`) con el checklist reevaluado; `ref_aprobacion` de N pasa a esa aprobación y N+1 vuelve a `en_progreso`. La aprobación de la etapa N no se repite: no cambió.

El validador lo exige con la regla 7b (ninguna transición habilitada con un insumo de N+1 que no esté `aprobado`) y con la regla 7g (una re-aprobación del insumo con la transición habilitada desde antes, sin una aprobación de la transición posterior, solo pasa como §3d-bis).

### 3d. Revisión de artefactos de una etapa ya aprobada

Si los artefactos de una etapa ya `aprobada` tienen que regenerarse —por ejemplo, porque se corrigió un defecto de la skill que los genera—, la etapa **no** se desaprueba y la transición no se suspende, siempre que el insumo no cambie:

1. Se regeneran los artefactos en `propuestas/<etapa>/` con la versión corregida de la skill. En el expediente los artefactos **siguen `aprobado` con el hash anterior** hasta el paso 3 (regla 7e: una etapa aprobada exige sus artefactos aprobados); la diferencia entre el hash registrado y el archivo es, justamente, lo que se está revisando.
2. **Re-auditoría por diferencia**: el auditor revisa solo lo que cambió frente a lo aprobado y confirma que el comportamiento aprobado se conserva (mismas verificaciones de la etapa).
3. **Nueva aprobación `tipo: artefacto`** (prompt `03`, mismo riesgo que la primera) con los hashes nuevos, y evento `correccion` que cite la aprobación anterior y el motivo (versión del framework y hallazgo).
4. Si el cambio altera el alcance, el riesgo o la decisión de la etapa (no solo su implementación), se re-aprueba también la etapa (`tipo: contexto`); si cambia el insumo, aplica §3c, o §3d-bis si el cambio del insumo es no sustantivo.

Mientras dure la revisión, la etapa siguiente puede seguir, porque consume el contrato aprobado, no la implementación; si la re-auditoría encuentra que el contrato cambió, se aplica §3c.

### 3d-bis. Revisión no sustantiva de un insumo ya aprobado

Si el insumo de una etapa que ya empezó o ya está aprobada (con sucesoras aprobadas o no) tiene que cambiar, pero el cambio **no es sustantivo**, no se aplica §3c: la transición no se suspende y ninguna etapa se re-aprueba. Evita desaprobar en cascada etapas que no cambian (regla 7b) por una clave de prueba, un comentario o una cita (corrida E2E SISBEN, HF-80).

**No sustantivo** = se cumplen **todas**:

- **(a)** Ningún valor que llegue a un artefacto **desplegable** cambia: al comparar los desplegables regenerados con los aprobados, quitando comentarios, son idénticos.
- **(b)** Solo cambian pruebas, fixtures de prueba, comentarios, citas o metadatos del insumo.
- **(c)** No cambia ninguna decisión, alcance, riesgo ni marcador de estado de un hecho (`[C]`, `[P]`, `[PM]`, `[A:]`).
- **(d)** El insumo no gana ni pierde dependencias de preguntas abiertas ni de riesgos.

Si falla una, o si el insumo es de riesgo `critico` (incluido el de `state_calculation`), aplica §3c sin excepción.

Procedimiento:

1. El insumo **sigue `aprobado` con el hash anterior** hasta el paso 3, como los artefactos en §3d (regla 7e); el validador lo admite: la regla 7b no se dispara y las transiciones siguen habilitadas. La nueva versión se registra con evento `propuesta` sin cambiar `estado_artefacto`.
2. Se regeneran los artefactos de la etapa que consumen el insumo y se hace la **re-auditoría por diferencia**, que confirma (a)-(d) con evidencia: el diff del insumo, el diff de los desplegables sin comentarios (vacío) y la lista de marcadores, preguntas y riesgos (sin cambios).
3. **Nueva aprobación `tipo: artefacto` del insumo** y **de cada artefacto regenerado** (prompt `03`), con el **mismo riesgo que la primera vez**, y evento `correccion` que cite la aprobación anterior y el motivo. La aprobación del insumo lleva en `condiciones` el marcador literal **«revisión no sustantiva (§3d-bis)»** y en `justificacion` la evidencia de (a), (b), (c) y (d), citadas así.
4. No se suspende ninguna transición ni se re-aprueba ninguna etapa (`tipo: contexto` o `transicion`).

**Decide el aprobador de la etapa** (`Desarrollador`, `PO` o `Aprobador designado`, según `06` §4), con justificación escrita aunque el riesgo sea `bajo` o `medio`. Nadie puede convertir un cambio sustantivo en no sustantivo por decisión: si la re-auditoría no confirma (a)-(d), aplica §3c.

El validador lo controla con la **regla 7g**: una re-aprobación `tipo: artefacto` de un artefacto de `insumos/` hecha mientras la transición hacia su etapa sigue habilitada desde antes (no hay una aprobación `tipo: transicion` hacia esa etapa posterior a la re-aprobación, que es la huella de §3c) es hallazgo salvo que declare el marcador en `condiciones`, cite (a)-(d) en `justificacion`, no baje el riesgo de la primera aprobación y la decida el aprobador de la etapa. Una aprobación de un insumo crítico con el marcador también es hallazgo.

### 3e. Verificación diferida de hechos fuera del repositorio

Cuando una pregunta abierta solo se puede cerrar con código o configuración que no están en el workspace (por ejemplo, una clase compartida de otro repositorio o el valor vigente de una clave del fork), el Desarrollador (N3) o el PO (N4) puede convertir la parte no verificable en **verificación diferida**, siempre que:

1. **Ningún artefacto generado dependa del hecho no verificado** (ni un valor, ni una clase, ni un umbral). Si alguno lo necesita, la pregunta sigue bloqueante y la etapa no avanza.
2. La parte verificable se cierra con su evidencia; la otra se marca `[PM]` en el insumo y en los artefactos (nunca `[C]`).
3. Se abre un riesgo `alto` «verificación diferida de <tema>» con sus criterios de cierre. Ese riesgo **bloquea la integración al fork y la certificación** de la etapa y de las que consumen su salida, que deben citarlo en sus aprobaciones; no bloquea la propuesta ni la auditoría.
4. Si al verificar el hecho contradice lo supuesto, se abre una pregunta bloqueante para la certificación y se revisa con §3c o §3d.

No aplica a lo que decide la spec (eso se resuelve con la spec) ni a un supuesto con consecuencia irreversible que no quede bloqueado por el riesgo (`09` §5.1 #6).

Además aplican las condiciones de bloqueo de `09` §5.1 (artefacto que depende de `[A:<ID>]`, checklist de seguridad de `08` §8 incompleto, supuesto irreversible sin confirmar, hallazgo de seguridad sin escalar).

Si **alguna** falla: `transicion_habilitada = false`, la etapa N+1 permanece `no_iniciada`, y el Orquestador responde con el checklist marcando exactamente qué falta. **No hay excepción ni override.**

Mientras una etapa esté `pendiente_aprobacion`, el flujo permanece detenido en ella (R3.4): el Orquestador no inicia trabajo de etapas posteriores. Las skills de etapa hacen la misma verificación en su Paso 0 (`00-framework-edf.md` §3).

## 4. Regla de corte (G3-ORQ-RF-04)

Solo para etapas de **compromiso Éxito**: `curation_flow`, `curation_stock`, `pinning`, `synthesis`, `state_calculation` (H-11, `03-pipeline.md`).

- Si la etapa no se automatiza dentro del plazo, el Desarrollador puede marcarla `completada_manualmente`.
- Requiere: aprobación explícita, riesgo clasificado, `justificacion` obligatoria y una entrada en `backlog_evolutivo[]` con el pendiente. La decisión "fallback manual vs. automatización" es del **PO** (`06` §4).
- En `state_calculation` (N4) la aprobación la da el PO o el Aprobador designado.
- **No aplica** a etapas de compromiso Aceptación (`validation`, `acceptance`) ni al habilitador `analisis`.
- Efecto: desbloquea el avance **sin** perder trazabilidad; queda marcada como deuda en el expediente.

## 4b. Omisión de una etapa

Para cuando una etapa **no aplica** a una ingesta (caso abierto: SISBEN y Synthesis, `OQ-TR-4`).

- `omitida` **no cuenta como aprobada por defecto** (steering `05` §3: no se cierra por decreto).
- Solo satisface la condición 1 del checklist si existe una aprobación `tipo: omision`, `decision: aprobada`, de un actor `PO` o `Aprobador designado`, con `justificacion` que cite la decisión de diseño que la respalda, y la etapa tiene `nota_omision`.
- El Orquestador **no propone** omitir una etapa por iniciativa propia: solo registra la omisión cuando el PO la decide (prompt `09`).
- Si la decisión de diseño está en conflicto entre fuentes (como `OQ-TR-4`), la omisión no se registra hasta que la pregunta se cierre.
- Efecto: la etapa siguiente puede habilitarse; el checklist se vuelve a correr completo.

## 5. Bitácora auditable (R4, `09` §4)

- **Formato:** `.md`, **append-only**. Nunca se edita ni se borra una entrada previa; una corrección es una entrada nueva que referencia la anterior.
- **Un evento por bloque**, con los 5 campos obligatorios de R4.2 + trazabilidad:

| Campo | Contenido |
|-------|-----------|
| `id` | `BIT-NNNN` secuencial, nunca se reusa |
| `qué` | Evento: interacción, propuesta, aprobación, rechazo, bloqueo, guardrail invocado, regla de corte, sincronización de evidencia |
| `quién` | Rol + identificador corporativo no-PII |
| `cuándo` | Timestamp ISO-8601 con zona |
| `por qué` | Justificación / regla aplicada / requisito invocado |
| `resultado` | `aplicado` \| `modificado` \| `descartado` \| `bloqueado` |
| trazabilidad | Etapa, artefactos, riesgo, requisito (`R3.2`, `GR-2`, …) |

- **Sin PII** (R4.3) — ver `02-guardrails-perimetro.md` §4 y steering `08`.
- **Retención:** la bitácora vive con el expediente y se versiona con él (git). Valor operativo del framework: **mínimo 24 meses** desde el último evento, sin rotar ni truncar. Es un **supuesto** (`09` §4.2 la deja `[A:OQ-RO-3]`): si Experian fija una política, manda la de Experian.

## 6. Eventos que SIEMPRE se registran

1. Inicio de una ingesta (creación del expediente).
2. Propuesta de un artefacto, de un insumo de `edf-analisis` o de una transición (con riesgo clasificado).
3. Aprobación, rechazo o aprobación condicional.
4. Bloqueo de una transición (con el checklist que falló).
5. Invocación de un guardrail.
6. Aplicación de la regla de corte.
7. Sincronización de evidencia del pipeline (qué se reportó y quién lo reportó).
8. Apertura y cierre de una pregunta abierta o de un riesgo.
9. Regeneración de `estado-ingesta.md` desde el JSON.
10. Escalamiento de un riesgo `critico` (N4), con la ruta usada (`06` §5).
