# Hallazgos del framework durante la corrida

Defectos del framework (skills, herramientas, plantillas) que encontraron los auditores durante la corrida. Regla
acordada: se registran, se corrigen con su prueba de regresión (commit aparte en esta rama) y la corrida sigue.

| ID | Etapa | Hallazgo | Evidencia | Corrección | Commit |
|---|---|---|---|---|---|
| HF-01 (AUD-E0-10) | analisis | P1. Una pregunta bloqueante frena toda la ingesta aunque solo afecte a una etapa posterior | `edf-analisis/references/deteccion-ambiguedades.md:65`, `edf-orquestador/references/04-contratos-por-skill.md:30` frente a `03-gobierno-riesgo-aprobacion.md:50` (condición 4 «en la etapa N») y `03-pipeline.md:200` | El bloqueo se mide por `etapa_afectada`; el gate de la etapa 0 considera solo `analisis` y `validation` | 7da4ca4 |
| HF-02 (AUD-E0-11) | analisis | P1. `edf-validation/SKILL.md:74` manda partir del ejemplo SISBEN, que difiere de la spec 1.0.2 (mismo riesgo en `edf-analisis/SKILL.md:75,84`) | Diff YAML del auditor: prefijos `RT_`/`EP_`, `SISBEN_ERR_001` longitud 50, otro catálogo; contradice `00` §6 | El ejemplo es solo de forma; los valores salen siempre de la HU y la spec | 7da4ca4 |
| HF-03 (AUD-E0-13) | analisis | P2. La plantilla de mapeo pone varios hechos por celda, contra `05-convenciones.md:113` | `mapeo-campos.md.tpl:12` | Excepción explícita para la tabla de mapeo y marcador `[A:*]`/`[PM]` por dimensión | 7da4ca4 |
| HF-04 (AUD-E0-14) | analisis | P2. `07-contratos-frontera.md:87` no tiene descarte silencioso, nulo + advertencia ni duplicado no aceptado | `07:87-91` frente a `07:105` y spec §5, §6, §10.4 | Agregar los tres efectos y alinear la plantilla | 7da4ca4 |
| HF-05 (AUD-E0-15) | analisis | P2. Falta la casilla «Insumos obligatorios presentes y leídos» en la plantilla de contexto | `contexto-ingesta.md.tpl:63-74` frente a `04-reglas-analisis.md:84` | Agregar la casilla | 7da4ca4 |

Hallazgos de la spec (no del framework; se dejan para la próxima versión de la spec, no se corrigen en la corrida):

| ID | Hallazgo | Evidencia |
|---|---|---|
| HS-01 (AUD-E0-08) | La spec cita `render-templates.jar --solo-verificar`; el generador vigente es Python | `spec-sisben.md:11` |
| HS-02 (AUD-E0-09) | `spec_version: "sisben-iv-1.0.0"` en el Anexo A frente a la spec 1.0.2 | `spec-sisben.md:390`, `:6-7` |

Hallazgos de la herramienta de la corrida (`herramientas/orquestador_e2e.py`):

| ID | Hallazgo | Corrección |
|---|---|---|
| HC-01 | `riesgo` cambiaba el JSON sin evento en la bitácora | Ahora registra un evento `riesgo` (BIT-0011 en adelante) |
| HC-02 | `vista` dejaba los contadores desfasados (detectado por el analista) | El evento de regeneración se registra antes de escribir la vista |

Observación abierta de la corrección HF-04: el corrector asignó «vaciado con preservación» a Curation por inferencia; lo verificará el auditor de la etapa de Curation.
