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
