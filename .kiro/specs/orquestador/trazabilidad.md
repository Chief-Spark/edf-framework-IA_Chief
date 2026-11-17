# Trazabilidad — requisitos → implementación

Fuente de requisitos (**no se duplica aquí**):
`Ingesta_EDF_Aut/Contexto Principal/ingesta_edf_chief/Inception-Analisis-Orquestador/requirements-orquestador.md`
`EXP-023_Paquete_Tecnico_Desarrollo/02_arquitectura/especificacion_requerimientos_skills.md` (§7 G3)

## R1 — Conocimiento y coordinación del flujo de etapas (etapa 0 + 6)

| AC | Implementación |
|----|----------------|
| R1.1 Conocer las etapas | `references/01` §1 (tabla de 8 entradas: 6 etapas + habilitador, con split de Curation; Reception fuera por H-3/H-4) · `etapaId` del esquema |
| R1.2 Respetar el orden con dependencias | `references/01` §2 · `etapas[].depende_de` · `prompts/02` tabla de dependencias |
| R1.3 Pipeline configurado = Validation → State Calculation | `references/01` §1 columna "en pipeline configurado" y §3 · `en_pipeline_configurado` (D-1) |
| R1.4 Verificar la etapa previa antes de habilitar | `references/03` §3 condiciones 1 y 3 · `checklist_bloqueo` |

## R2 — Estado / expediente persistente

| AC | Implementación |
|----|----------------|
| R2.1 JSON con esquema + `estado-ingesta.md` | `templates/estado-ingesta.schema.json` · `templates/estado-ingesta.template.md` |
| R2.2 Artefactos, aprobaciones y riesgos por etapa | `etapas[].artefactos[]`, `ref_aprobacion`, `riesgos[]`, `preguntas_abiertas[]` |
| R2.3 Sobrevive entre sesiones como fuente de verdad | `SKILL.md` §3 · `prompts/01` (primer prompt de toda sesión) |
| R2.4 Reflejar el avance real por movimiento en S3 | `references/01` §5–§6 · `evidenciaPipeline` del esquema · `prompts/05` |
| R2.5 Coherencia JSON ↔ `.md` | `SKILL.md` invariante 7 · `generado_por.vista_md_regenerada_en` · `prompts/01` paso 2 |

## R3 — Gobierno: aprobación y bloqueo

| AC | Implementación |
|----|----------------|
| R3.1 Exigir aprobación al finalizar una etapa | `references/03` §3 cond. 2 · `prompts/02` paso 7 · `prompts/03` |
| R3.2 **Bloquear sin aprobación registrada** | **3 capas:** esquema `if/then` de `transicion_habilitada` (D-3) + `references/03` §3 + `prompts/02` |
| R3.3 Clasificar el riesgo | `references/03` §1 (incluye elevadores automáticos a alto) |
| R3.4 Flujo detenido mientras está pendiente | `SKILL.md` §5 · `prompts/02` paso 8 · `prompts/04` paso 4 |
| R3.5 Fricción proporcional al riesgo | `references/03` §2 · `aprobacion.justificacion` obligatoria en riesgo alto (D-4) |

## R4 — Bitácora auditable

| AC | Implementación |
|----|----------------|
| R4.1 `.md` append-only | `templates/bitacora.template.md` · `bitacora.append_only: const true` |
| R4.2 Qué / quién / cuándo / por qué / resultado | Formato de evento de `references/03` §5 y de la plantilla |
| R4.3 Sin PII | `references/02` §4 · `actor = {rol, identificador}` (D-6) · `conteos` solo agregados (D-7) |

## R5 — Integración de solo lectura con el pipeline

| AC | Implementación |
|----|----------------|
| R5.1 No ejecuta pipeline/Spark/DAGs | GR-2 · `prompts/05` §Guardrail · caso C-5 de certificación |
| R5.2 No escribe en repos protegidos | GR-3 · `artefacto.ruta` ("nunca una ruta de escritura en repos protegidos") |
| R5.3 Referenciar rutas y contratos solo como lectura | `rutasPipeline` ("solo lectura") · `references/04` |
| R5.4 Reconocer los 4 DAGs ya implementados | `references/01` §4 · `etapas[].dag_airflow` |

## R6 — Guardarraíles

| AC | Implementación |
|----|----------------|
| R6.1 No AWS/IAM/infra/redes | GR-1 |
| R6.2 No despliegues | GR-4 |
| R6.3 Solo fixtures sintéticos | GR-5 · `certificacion.md` §Preparación |
| R6.4 Propone, no ejecuta sin autorización | GR-8 · `SKILL.md` §1–§2 · `prompts/02` paso 7 |
| R6.5 Sin PII en logs | GR-6 · `references/02` §4 |

## R7 — Certificación (regla de 3 componentes)

| AC | Implementación |
|----|----------------|
| R7.1 Definición de agente funcional | ① `SKILL.md` + 6 steering |
| R7.2 Interacción reproducible | ② `prompts/00..07` · verificación V-6 |
| R7.3 Piloto SISBEN: expediente trazable y bloqueo de avance sin aprobación | ③ `certificacion.md` C-1..C-7 (C-2, C-3, C-5, C-6, C-7 deben bloquear) |

## G3-ORQ (especificación de skills)

| RF | Implementación |
|----|----------------|
| G3-ORQ-RF-01 Conocer el flujo y decidir orden | `references/01` |
| G3-ORQ-RF-02 Estado de la ingesta e inventario | esquema completo · un expediente por ingesta en `expedientes/` |
| G3-ORQ-RF-03 Enrutar al harness antes de presentar | `artefacto.prevalidacion` + `contexto_precargado` (RT-03) · `references/04` §Validation |
| G3-ORQ-RF-04 Regla de corte | `references/03` §4 · `prompts/06` · D-5 del esquema · `backlog_evolutivo[]` |
| G3-ORQ-RF-05 No genera, no aprueba, no ejecuta | GR-7, GR-8, GR-2 · `SKILL.md` §1 |

## Preguntas abiertas: estado

| ID original | Estado | Dónde |
|-------------|--------|-------|
| OQ-1 / OQ-T-1 — esquema del estado JSON | ✅ **CERRADA** | `templates/estado-ingesta.schema.json` · `design.md` §3 |
| OQ-T-3 — retención de la bitácora | ⚠️ **CERRADA con supuesto** (24 meses default; prevalece la política de Experian) | `design.md` §5 · OQ-D-2 |
| OQ-T-4 — prueba funcional de la certificación | ✅ **CERRADA** | `certificacion.md` |
| OQ-D-1..OQ-D-4 — nuevas, abiertas | 🔵 abiertas | `design.md` §8 · `tasks.md` Fase B |
