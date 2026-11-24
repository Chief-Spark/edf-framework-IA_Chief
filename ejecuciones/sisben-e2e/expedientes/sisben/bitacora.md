<!--
  BITÁCORA AUDITABLE — APPEND-ONLY (R4.1).
  Nunca se edita ni se borra una entrada previa. Una corrección es una entrada NUEVA
  que referencia a la anterior en el campo "Corrige".
  Prohibido registrar PII (R4.3 / GR-6): nombres de campo y reglas sí; valores de campos PII no.
  Retención: 24 meses desde el último evento como default del framework (OQ-T-3);
  prevalece la política de Experian si existe.
-->

# Bitácora de la ingesta — SISBEN IV (`sisben`)

Expediente: `estado-ingesta.json` · Vista: `estado-ingesta.md` · Skill: `edf-orquestador` v1.9.0

## Formato de evento

Cada evento es un bloque con los 5 campos obligatorios de R4.2 (**qué · quién · cuándo · por qué · resultado**) más trazabilidad.

Tipos de evento válidos: `inicio_expediente` · `interaccion` · `propuesta` · `aprobacion` · `rechazo` · `aprobacion_condicional` · `bloqueo` · `guardrail_invocado` · `regla_de_corte` · `sincronizacion_evidencia` · `pregunta_abierta` · `cierre_pregunta` · `riesgo` · `regeneracion_vista` · `correccion`

---

## BIT-0001 · `inicio_expediente`

| Campo | Valor |
|-------|-------|
| **Qué** | Creación del expediente de SISBEN IV desde la semilla: 8 etapas, 0 preguntas abiertas, 2 riesgos. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T16:59:45-05:00 |
| **Por qué** | Apertura de la ingesta. Compuerta de la etapa 0 superada (HU y spec presentes). Requisitos R2.1 a R2.3. |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | prompt 00 · R2.1, R2.3 |
| **Corrige** | — |

---

## BIT-0002 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T16:59:45-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0003 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | ingesta.periodo corregido de 'mensual' a '202601' (periodo AAAAMM de la muestra). |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T16:59:51-05:00 |
| **Por qué** | El validador del expediente (regla 1, esquema) exige periodo AAAAMM. |
| **Resultado** | `modificado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | validar-expediente.py regla 1 |
| **Corrige** | BIT-0001 |

---

## BIT-0004 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T16:59:51-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0005 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Propuestos ART-001..004 (análisis) y ART-010 (insumo de Validation), prevalidados. |
| **Quién** | Analista (agente) · `AGT-ANALISTA` |
| **Cuándo** | 2026-11-24T17:11:35-05:00 |
| **Por qué** | Entrega de edf-analisis Modo 1 y Modo 2 (INT-001). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | bajo |
| **Artefactos** | ART-001, ART-002, ART-003, ART-004, ART-010 |
| **Trazabilidad** | prompt 08 · INT-001 |
| **Corrige** | — |

---

## BIT-0006 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-001: ¿Con qué mecanismo se cumple R-26 / HU §5.6 (la sección #sisben solo para los PIN del archivo vigente)? Opciones: (a) archivo.metadata fileLoadType en Validation; (b) inferDelete en Acceptance; (c) lógica en el enricher de State Calculation; (d) el motor ya lo resuelve. |
| **Quién** | Analista (agente) · `AGT-ANALISTA` |
| **Cuándo** | 2026-11-24T17:11:35-05:00 |
| **Por qué** | Analista (INT-001): spec §13 y HU R-26 exigen el resultado pero no el mecanismo. |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0007 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:11:35-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0008 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:11:39-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0009 · `cierre_pregunta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-001 resuelta: [SIMULADO · aprobador SIM-DEV-01] Resuelta por las fuentes: R-26 se cumple en State Calculation (HU R-26, §5.6, §6; spec §13). Validation, Acceptance, Curation y Synthesis no cambian (spec Anexo A, §12; HU §6). Las alternativas (a) archivo.metadata/fileLoadType y (b) inferDelete quedan descartadas mientras no exista una versión nueva de la HU y la spec (HU §3). El mecanismo concreto dentro de la etapa 6 pasa a PA-002. |
| **Quién** | Desarrollador SIM-DEV-01 (SIMULADO) |
| **Cuándo** | 2026-11-24T17:20:06-05:00 |
| **Por qué** | Respuesta del aprobador simulado con cita a las fuentes. |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0010 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-002: En State Calculation (statecalc-foundation/enricher), ¿cómo se determina el corte vigente de SISBEN y cómo se excluye la sección #sisben de los PIN cuyo último evento SISBEN no pertenece a ese corte (HU R-26, §5.6)? Opciones compatibles: (c1) filtro en el enricher comparando fechaCorte curado (spec §10.1) con el corte vigente (máximo fechaCorte o corte del último archivo aceptado; toca [A:CQ-006]); (d) el motor ya lo cumple, solo con evidencia de código o pipeline. Fuera de alcance sin nueva versión de HU/spec: cambios al Anexo A, a Acceptance o a Synthesis. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:20:06-05:00 |
| **Por qué** | Cierre de PA-001 (aprobador SIM-DEV-01, INT-003); HU R-26, §5.6, §6; spec §13, §10.1, §10.3; auditoría AUD-E0-07. |
| **Resultado** | `aplicado` |
| **Etapa** | `state_calculation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0011 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-003 (critico, state_calculation): R-26 sin mecanismo definido: un PIN ausente del archivo vigente podría conservar su sección #sisben (impacto N4). |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:20:34-05:00 |
| **Por qué** | Aprobador SIM-DEV-01 (INT-003): el impacto (XPM visible al cliente) es N4 según 06 §6 #1; mitigación remite a PA-002. |
| **Resultado** | `aplicado` |
| **Etapa** | `state_calculation` |
| **Riesgo** | critico |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0012 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-007 (medio, curation_flow): Una Fec_digitacion futura con formato válido es inconsistencia sev. 3 pero se transforma: Curation la publica como epoch y llega a #sisben como fecha de encuesta futura (spec §10.4 regla 1, §13) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:20:54-05:00 |
| **Por qué** | Propuesto por el analista (INT-001), alineado por AUD-E0-05 (INT-002/003) |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0013 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-008 (medio, curation_flow): ide_hogar e ide_ficha_origen son identificadores públicos (R-28, spec §10.1) que podrían enlazarse con una persona (re-identificación indirecta) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:20:54-05:00 |
| **Por qué** | Propuesto por el analista (INT-001), alineado por AUD-E0-05 (INT-002/003) |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0014 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-009 (medio, validation): Adultos con tipo 7 (salvoconducto) se rechazan hasta que Experian cree el tipo (R-17; 07 §5.7 [A:OQ-TR-2b]) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:20:54-05:00 |
| **Por qué** | Propuesto por el analista (INT-001), alineado por AUD-E0-05 (INT-002/003) |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0015 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-010 (medio, synthesis): La reference curation-pinning-sisben.md §6/§8 dice que SISBEN va sin Synthesis y periodicidad Unique, contra HU R-25 y spec §12 |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:20:54-05:00 |
| **Por qué** | Propuesto por el analista (INT-001), alineado por AUD-E0-05 (INT-002/003) |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0016 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-011 (alto, state_calculation): La certificación de 4 dimensiones antes de producción (07 §5.7) no figura en la DoD de la HU §6 y no hay motor que la calcule |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:20:54-05:00 |
| **Por qué** | Propuesto por el analista (INT-001), alineado por AUD-E0-05 (INT-002/003) |
| **Resultado** | `aplicado` |
| **Etapa** | `state_calculation` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0017 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:22:16-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0018 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `analisis` → estado `en_progreso` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:30:03-05:00 |
| **Por qué** | La etapa 0 empezó con la propuesta del analista (BIT-0005) y quedó en no_iniciada; se corrige (HC-03). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | BIT-0005 |

---

## BIT-0019 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `analisis` → estado `pendiente_aprobacion` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:30:04-05:00 |
| **Por qué** | Re-auditoría APROBADO (INT-006) y rondas 2-3 aplicadas (INT-004, INT-007); se pide la aprobación al aprobador simulado. |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0020 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-001 (artefacto): Artefactos de la etapa 0 (contexto 1.1.0, mapeo 1.0.1, dependencias 1.1.0, plan 1.1.0) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T17:31:25-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Re-auditoría APROBADO de los cuatro (auditorias/analisis.md §6.4), AUD-E0-01..06 resueltos; contenido fiel a las fuentes (R-26 en State Calculation: HU R-26, §5.6, §6; spec §13; 22/22 campos de spec §3). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | bajo |
| **Artefactos** | ART-001, ART-002, ART-003, ART-004 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0021 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-002 (contexto): Aprobación de la etapa 0 analisis |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T17:31:25-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Artefactos obligatorios aprobados; checklist de cierre 04 §4.3 completo (13/13); ninguna pregunta bloqueante con etapa_afectada analisis o validation (PA-001 resuelta; PA-002 de state_calculation). La etapa 0 no corre en pipeline: sin evidencia ni evidencia diferida (01-flujo-etapas §1). Supuesto: el cierre formal del PO (06 §4) queda pendiente no bloqueante. |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | bajo |
| **Artefactos** | ART-001, ART-002, ART-003, ART-004 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0022 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `analisis` → estado `aprobada` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:31:25-05:00 |
| **Por qué** | Aplica APR-002 (SIMULADO, SIM-DEV-01, INT-008). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0023 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-003 (artefacto): Insumo de Validation insumos/source-spec-validation.yaml |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T17:31:49-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] YAML idéntico al Anexo A (diff limpio, auditorias/analisis.md §6.2); --solo-verificar OK (22 campos, 29 códigos); prevalidación de la muestra = spec §15 (20/1/1/0, RQS 0.95, 19 aceptados). Sin [A:*] ni [PM]. |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-010 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0024 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-004 (transicion): Transición analisis → validation |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T17:31:49-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Análisis cerrado y auditado; el insumo de Validation es fiel al Anexo A; ninguna pregunta bloqueante tiene etapa_afectada en {analisis, validation}. Checklist: c1 cumple (APR-002); c2 cumple (esta aprobación); c3 cumple (analisis aprobada e insumo ART-010 aprobado; mapeo 22/22); c4 cumple (PA-001 resuelta; PA-002 es de state_calculation); c5 no aplica (la etapa 0 no corre en pipeline); c6 no aplica (riesgo medio). Riesgo medio por la etapa afectada (06 §2.1): supuesto del aprobador simulado. |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | medio |
| **Artefactos** | ART-010 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0025 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `analisis` → estado `aprobada` · transicion_habilitada=True |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:31:49-05:00 |
| **Por qué** | Transición habilitada por APR-004 (checklist c1..c6 sin fallas). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0026 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `validation` → estado `en_progreso` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:31:49-05:00 |
| **Por qué** | Inicio de Validation tras APR-004; se encarga la generación a edf-validation. |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0027 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:31:49-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0028 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `analisis` → estado `aprobada` · transicion_habilitada=True |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:32:03-05:00 |
| **Por qué** | ref_aprobacion de una etapa con transición habilitada debe ser la aprobación tipo transicion (APR-004), no la de contexto (APR-002); regla 7a del validador. |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | BIT-0025 |

---

## BIT-0029 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:32:03-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0030 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Propuesta de Validation generada en propuestas/validation/ (18 archivos + manifest.json, 17 supuestos); registrados como prevalidados; ART-014 con la ruta real env/application-conf/innersource/propuesta/ |
| **Quién** | Generador edf-validation (agente) · registra ORQ-E2E |
| **Cuándo** | 2026-11-24T17:39:29-05:00 |
| **Por qué** | Generación desde el insumo aprobado ART-010 (APR-003), INT-009; el generador reporta 10 posibles defectos, el principal: RA-02..04 (fechas del nombre de archivo) sin implementar |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-011, ART-012, ART-013, ART-014, ART-015 |
| **Trazabilidad** | prompt 08 |
| **Corrige** | — |

---

## BIT-0031 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:40:10-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0032 · `rechazo`

| Campo | Valor |
|-------|-------|
| **Qué** | Dictamen del auditor independiente: propuesta de Validation RECHAZADA (ART-014 RECHAZADO por P0 AUD-V-01; resto AJUSTES) |
| **Quién** | Auditor independiente (agente) · registra ORQ-E2E |
| **Cuándo** | 2026-11-24T17:53:45-05:00 |
| **Por qué** | auditorias/validation.md: validate[]/metrics[] sin filePattern (SparkValidator.scala:1417-1446) y P1 AUD-V-02..06; se corrige el framework y se regenera (regla de la corrida: registrar, corregir y seguir) |
| **Resultado** | `bloqueado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-011, ART-012, ART-013, ART-014, ART-015 |
| **Trazabilidad** | prompt 08 · auditoría |
| **Corrige** | — |

---

## BIT-0033 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:53:45-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---
