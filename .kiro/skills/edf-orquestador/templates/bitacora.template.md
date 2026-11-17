<!--
  BITÁCORA AUDITABLE — APPEND-ONLY (R4.1).
  Nunca se edita ni se borra una entrada previa. Una corrección es una entrada NUEVA
  que referencia a la anterior en el campo "Corrige".
  Prohibido registrar PII (R4.3 / GR-6): nombres de campo y reglas sí; valores de campos PII no.
  Retención: 24 meses desde el último evento como default del framework (OQ-T-3);
  prevalece la política de Experian si existe.
-->

# Bitácora de la ingesta — {{ingesta.nombre}} (`{{ingesta.id}}`)

Expediente: `estado-ingesta.json` · Vista: `estado-ingesta.md` · Skill: `edf-orquestador` v{{version}}

## Formato de evento

Cada evento es un bloque con los 5 campos obligatorios de R4.2 (**qué · quién · cuándo · por qué · resultado**) más trazabilidad.

Tipos de evento válidos: `inicio_expediente` · `interaccion` · `propuesta` · `aprobacion` · `rechazo` · `aprobacion_condicional` · `bloqueo` · `guardrail_invocado` · `regla_de_corte` · `sincronizacion_evidencia` · `pregunta_abierta` · `cierre_pregunta` · `riesgo` · `regeneracion_vista` · `correccion`

---

## BIT-0001 · `inicio_expediente`

| Campo | Valor |
|-------|-------|
| **Qué** | Creación del expediente de la ingesta {{ingesta.nombre}}. Sembradas 8 entradas de etapa (etapa 0 + 6 etapas, Curation en Flow y Stock), {{n}} preguntas abiertas y {{m}} riesgos. |
| **Quién** | Desarrollador · `{{identificador-no-pii}}` |
| **Cuándo** | {{ISO-8601 con zona}} |
| **Por qué** | Apertura de la ingesta piloto. Requisito R2.1–R2.3 (expediente persistente como fuente de verdad). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.1, R2.3 · `references/01-flujo-etapas.md` |
| **Corrige** | — |

---

## BIT-0002 · `bloqueo`

| Campo | Valor |
|-------|-------|
| **Qué** | Intento de habilitar `validation` desde `analisis`. Checklist de bloqueo evaluado: **BLOQUEADA**. |
| **Quién** | Desarrollador · `{{identificador-no-pii}}` |
| **Cuándo** | {{ISO-8601}} |
| **Por qué** | Fallaron las condiciones 1, 2 y 4: la etapa `analisis` está `no_iniciada`, no hay aprobación registrada y existen 3 preguntas abiertas bloqueantes (PA-001, PA-002, PA-003). Regla: `references/03-gobierno-riesgo-aprobacion.md` §3. |
| **Resultado** | `bloqueado` |
| **Etapa** | `analisis` → `validation` |
| **Riesgo** | alto |
| **Artefactos** | ART-001..ART-004 (`ausente`) |
| **Trazabilidad** | R3.1, R3.2, R4.4 |
| **Corrige** | — |

---

## BIT-0003 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Aprobación de la transición `analisis` → `validation`. Artefactos ART-001..ART-004 aprobados. Registrada como APR-001. |
| **Quién** | Desarrollador · `{{identificador-no-pii}}` |
| **Cuándo** | {{ISO-8601}} |
| **Por qué** | Contexto de la ingesta cerrado: 22/22 campos mapeados con clasificación PII, tipo, regla EPOCH y catálogo; PA-001 y PA-003 resueltas y documentadas. Riesgo bajo (N1, clasificación fija de la etapa 0 en steering `06` §2.2) → aprobación ligera. |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` → `validation` |
| **Riesgo** | bajo · fricción `ligera` |
| **Artefactos** | ART-001, ART-002, ART-003, ART-004 (`aprobado`) |
| **Trazabilidad** | R3.1, R3.5, APR-001 |
| **Corrige** | — |

---

## BIT-0004 · `guardrail_invocado`

| Campo | Valor |
|-------|-------|
| **Qué** | Se solicitó disparar el DAG `daily-validation`. No ejecutado. |
| **Quién** | Desarrollador · `{{identificador-no-pii}}` |
| **Cuándo** | {{ISO-8601}} |
| **Por qué** | GR-2 / R5.1: el Orquestador no ejecuta el pipeline ni dispara DAGs de Airflow. Se ofreció la alternativa dentro del perímetro (precondiciones + registro de evidencia posterior). |
| **Resultado** | `descartado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | GR-2, R5.1 · `references/02-guardrails-perimetro.md` §3 |
| **Corrige** | — |

---

<!-- Los eventos siguientes se APENDIZAN aquí, nunca arriba, y el contador nunca se reusa. -->
