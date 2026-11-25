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

## BIT-0034 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-003: ¿Se amplía el filePattern de spec §2/Anexo A (p. ej. .*SISBEN_EXPERIAN_.*\.(txt/TXT)) para que validateFilename rechace con SISBEN_ERR_024 el archivo sin marca de tiempo, o se ajusta el escenario de HU §5.1 a «no se procesa y queda registrado 'No validation configuration found'»? |
| **Quién** | Aprobador simulado SIM-PO-01/SIM-DEV-01 (SIMULADO, INT-012) |
| **Cuándo** | 2026-11-24T17:55:52-05:00 |
| **Por qué** | AUD-V-04; HU §5.1, R-01; spec §2 RA-01; SparkValidator.scala:176-190. Dirigida al PO y negocio para la próxima versión de HU/spec. |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0035 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-004: ¿Se excluyen del agrupamiento de duplicados los registros con error sev. 1 (incluida la fila de 23 columnas con ERR_026), o no pueden ser los que se conservan? Hoy (spec §6, HU R-19) gana el de mayor fec_actualizacion_cns aunque tenga error. |
| **Quién** | Aprobador simulado SIM-PO-01/SIM-DEV-01 (SIMULADO, INT-012) |
| **Cuándo** | 2026-11-24T17:55:53-05:00 |
| **Por qué** | AUD-V-07; spec §6; HU R-19. Dirigida al PO para la próxima versión de la spec. |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0036 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-005: ¿De dónde obtiene el reporting estándar el contador REGISTROS MENORES DE EDAD (HU §5.1, spec §5 RF-02) si el filtro de Validation no deja contador (SparkValidator.scala:1128-1130)? |
| **Quién** | Aprobador simulado SIM-PO-01/SIM-DEV-01 (SIMULADO, INT-012) |
| **Cuándo** | 2026-11-24T17:55:53-05:00 |
| **Por qué** | AUD-V-18; spec §5, §14; HU R-27. Dirigida al PO y al dueño del reporting estándar. |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0037 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-012 (medio, validation): Un archivo sin marca de tiempo no coincide con el filePattern: no deja marcador ni reporte de rechazo ERR_024 (HU §5.1 cumplido en parte) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:55:53-05:00 |
| **Por qué** | Aprobador simulado SIM-PO-01/SIM-DEV-01 (SIMULADO, INT-012), punto 1 |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0038 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-013 (medio, validation): En duplicados gana el de mayor fec_actualizacion_cns aunque tenga error sev. 1; el registro válido queda sin aceptar (dato incompleto) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:55:53-05:00 |
| **Por qué** | Aprobador simulado SIM-PO-01/SIM-DEV-01 (SIMULADO, INT-012), punto 3 |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0039 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-014 (medio, validation): El contador REGISTROS MENORES DE EDAD del Log_Sisben no tiene origen: el filtro de Validation no deja rastro |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:55:53-05:00 |
| **Por qué** | Aprobador simulado SIM-PO-01/SIM-DEV-01 (SIMULADO, INT-012), punto 4 |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0040 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T17:56:04-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0041 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | ART-010 vuelve a propuesto: versión 2 del insumo con claves aditivas del framework 1.9.2 (RA-02..04, solo_ceros RV-SIS-05, supuestos_resueltos, casos nuevos del fixture) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:15:59-05:00 |
| **Por qué** | Autorización del aprobador simulado (INT-012, punto 2); la transición analisis→validation (APR-004) no se reabre; la versión 1 queda trazada en APR-003 |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-010 |
| **Trazabilidad** | prompt 08 |
| **Corrige** | BIT-0023 |

---

## BIT-0042 · `bloqueo`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `analisis` → estado `aprobada` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:16:14-05:00 |
| **Por qué** | ART-010 (insumo de validation) volvió a propuesto (BIT-0041): la condición c3 deja de cumplirse; la transición analisis→validation queda suspendida hasta re-aprobar el insumo y la transición (regla 7b; hallazgo HF-12). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0043 · `bloqueo`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `validation` → estado `no_iniciada` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:16:57-05:00 |
| **Por qué** | Suspensión por revisión del insumo ART-010 (references/03 §3c paso 2, framework 1.9.3): validation vuelve a no_iniciada; ART-011..015 (prevalidados con el insumo v1) no se aprueban y se regenerarán. |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0044 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:17:11-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0045 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:19:34-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0046 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Hash de ART-010 v2 = sha256:dd707688…92aa65 (reemplaza el de la v1 7a8d7a6e… aprobado en APR-003); difiere del hash auditado 46dfbdef… solo en comentarios (AUD-V-22/23) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:24:43-05:00 |
| **Por qué** | Condición 1 del aprobador (INT-016); AUD-V-24 |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-010 |
| **Trazabilidad** | prompt 08 · references/03 §3c |
| **Corrige** | BIT-0023 |

---

## BIT-0047 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-005 (artefacto): Re-aprobación del insumo de Validation v2 (references/03 §3c paso 3) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T18:24:43-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Re-auditoría APROBADO (auditorias/validation.md §6): cambios aditivos autorizados en INT-012 con cita (RA-02..04 spec §2; RV-SIS-05 solo_ceros spec §4, §3 fila 15, HU R-18; casos DUP_CEROS_* y nombres_invalidos HU §5.1); 9 supuestos_resueltos verificados; muestra = spec §15. Supuesto SUP-APR-01 [PM]: el «mes actual» de RA-02 es el mes en America/Bogota; dueño el PO para la próxima versión de la spec; si es falso, rechazo o aceptación equivocados solo en el borde del mes, reversible; sin huella en el código (HF-13). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-010 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0048 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-006 (transicion): Nueva aprobación de la transición analisis → validation (references/03 §3c paso 4) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T18:24:43-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Insumo v2 re-auditado y re-aprobado con hash dd707688…; ninguna pregunta bloqueante en analisis ni en validation (PA-003..005 no bloqueantes; PA-002 de state_calculation); se reabre la transición suspendida en BIT-0042. c1 cumple (APR-002, no se repite); c2 cumple; c3 cumple (ART-010 aprobado); c4 cumple; c5 no aplica (etapa 0 sin pipeline); c6 no aplica (medio). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | medio |
| **Artefactos** | ART-010 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0049 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `analisis` → estado `aprobada` · transicion_habilitada=True |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:24:43-05:00 |
| **Por qué** | Transición reabierta por APR-006 (references/03 §3c paso 4). |
| **Resultado** | `aplicado` |
| **Etapa** | `analisis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0050 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `validation` → estado `en_progreso` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:24:43-05:00 |
| **Por qué** | Validation vuelve a en_progreso tras APR-006; se regenera la propuesta con el insumo v2 y el framework 1.9.2. |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0051 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:24:43-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0052 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Propuesta de Validation regenerada con el insumo v2 (APR-005) y el framework 1.9.4; registrada como prevalidada |
| **Quién** | Generador edf-validation (agente) · registra ORQ-E2E |
| **Cuándo** | 2026-11-24T18:30:48-05:00 |
| **Por qué** | Regeneración tras el RECHAZADO (BIT-0032) y las correcciones HF-06..15; INT-017, INT-018 |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-011, ART-012, ART-013, ART-014, ART-015 |
| **Trazabilidad** | prompt 08 |
| **Corrige** | — |

---

## BIT-0053 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:30:48-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0054 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-007 (artefacto): Artefactos de Validation regenerados (insumo v2, framework 1.9.4) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T18:35:41-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Re-auditoría APROBADO de los cinco (auditorias/validation.md §7); cerrados AUD-V-01 (P0) y AUD-V-02/03/05/06 (P1). Pruebas asociadas: ART-011 SISBENValidationProcessorSpec + muestra = spec §15 + casos v7; ART-012 FieldValidatorsSpec (SoloCeros, crudo, reglas del nombre, offset); ART-013 fixture 27 líneas verificado caso a caso y suite 216; ART-014 verificar_hocon OK (filePattern y lookupHome); ART-015 18/18 sha256 y marcas 8 abiertas / 9 resueltas. ART-013 incluye docs/codigos-error-sisben.csv (fuera del hash de src/test; cubierto por el manifest de ART-015). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-011, ART-012, ART-013, ART-014, ART-015 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0055 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-016 (medio, curation_flow): Con un archivo CRLF, FECHA_ACTUALIZACION_CNS llega a Curation con \r final (Campos.crudo, AUD-V-29) y alimenta fechaModificacion de #sisben (spec §13); la spec no fija el fin de línea |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:35:42-05:00 |
| **Por qué** | Aprobador SIM-DEV-01, INT-021, condición 4 |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0056 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `validation` → estado `pendiente_evidencia` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:35:42-05:00 |
| **Por qué** | Artefactos de Validation aprobados; no hay pipeline en la corrida: se pide la aprobación con evidencia diferida (references/03 §3b paso 1). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0057 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-008 (contexto): Aprobación de la etapa validation con evidencia diferida (references/03 §3b paso 1) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T18:35:42-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Validation se aprueba con evidencia diferida: la corrida autónoma no tiene pipeline ni fork (sin acceso a S3/Airflow; F-01), así que evidencia_pipeline.observada queda null. Los artefactos ART-010 a ART-015 están aprobados por auditoría independiente (auditorias/validation.md §6 y §7): reproducibles, la muestra da spec §15 y el fixture, los nombres inválidos y RV-SIS-05 dan los códigos de la spec. Acceptance depende del contrato fijado por la spec §7 y el Anexo A, no de la ejecución. La evidencia se reportará con el prompt 05. Escenarios HU §5.1: cubiertos salvo «sin marca de tiempo» (cumplido en parte, desviación PA-003/RG-012) y el contador de menores del log (no certificable por la skill, PA-005/RG-014); duplicados con notas PA-004/RG-013 y HF-16. DoD HU §6: prevalidación = spec §15 cumplida; SISBENValidationProcessorSpec y Zephyr diferidos (RG-015). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | alto |
| **Artefactos** | ART-010, ART-011, ART-012, ART-013, ART-014, ART-015 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0058 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-015 (alto, validation): Evidencia diferida de validation: aprobada sin ejecución de pipeline (observada null) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:35:42-05:00 |
| **Por qué** | APR-008, references/03 §3b paso 1 |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0059 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `validation` → estado `aprobada` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:35:42-05:00 |
| **Por qué** | Aplica APR-008 (SIMULADO, SIM-DEV-01, INT-021): aprobada con evidencia diferida. |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0060 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:35:42-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0061 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-006: ¿Qué valores usa Acceptance donde la HU y la spec callan? (a) nombre de la condición y reasonCode del rechazo por FQS (propuesta de la skill: RQS_BAJO_UMBRAL_SISBEN / SISBFA00001); (b) archivo sin registros (propuesta: aceptar_cuerpo_vacio false, rechazo automático); (c) customReasonForAutomaticRejectionEnabled (propuesta: true). |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:35:57-05:00 |
| **Por qué** | Analista (INT-020); HU R-20..22, §5.2, §6; spec §7; edf-acceptance/references/inventario-plantillas.md:18. Dirigida al Desarrollador; cierra el PO. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0062 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:36:23-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0063 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Se quita el RG-015 duplicado en etapas[validation].riesgos (AUD-A-11; HC-04: --agregar-riesgo ya no duplica) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:47:06-05:00 |
| **Por qué** | Hallazgo AUD-A-11 del auditor de Acceptance |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | prompt 08 |
| **Corrige** | BIT-0059 |

---

## BIT-0064 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:47:55-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0065 · `cierre_pregunta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-006 resuelta: [SIMULADO · aprobador SIM-DEV-01] La cierra el Desarrollador (N2; 06 §4: no cambia alcance). (a) RQS_BAJO_UMBRAL_SISBEN / SISBFA00001 como convención [P]; antes de pasar al fork, comprobar que no existen en el fileConditions[] global de acceptance-common-application-inner.conf [PM] (RG-017). (b) Se rechaza (aceptar_cuerpo_vacio false): solo header o todas las filas filtradas (errorInEmptyFileBody, SparkValidator.scala:615-627); deducido de HU R-22, spec RQ-03 y §10.3. (c) customReasonForAutomaticRejectionEnabled = false (default del motor, SparkAcceptor.scala:166; true no cambia decisiones y deja un error en el log, AUD-A-05). |
| **Quién** | Desarrollador SIM-DEV-01 (SIMULADO, INT-024) |
| **Cuándo** | 2026-11-24T18:49:09-05:00 |
| **Por qué** | Respuesta del aprobador simulado con cita a las fuentes. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0066 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-017 (medio, acceptance): Las fileConditions son globales (SparkAcceptor.scala:46): si RQS_BAJO_UMBRAL_SISBEN o SISBFA00001 ya existen en el fork, la decisión cambiaría sin avisar (06 §6 #9) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:49:09-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-024), PA-006 (a) |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0067 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-018 (medio, acceptance): Un archivo sin RQS queda pendiente en validated/ sin otra señal que un error en el log (AUD-A-12, motor) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:49:09-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-024), AUD-A-12 |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0068 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T18:49:09-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0069 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-016 (medio, curation_flow): Con un archivo CRLF, FECHA_ACTUALIZACION_CNS llega a Curation con \r final (Campos.crudo, AUD-V-29) y alimenta fechaModificacion de #sisben (spec §13); la spec no fija el fin de línea |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:08:58-05:00 |
| **Por qué** | Re-auditoría §8 (INT-027); condición 4 de APR-007 cumplida |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0070 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Revisión §3d de Validation: hashes nuevos de ART-012, ART-013 y ART-015 (manifest 735a2624…) por el framework 1.9.5 (HF-16/17/18); ART-011 mismo hash, comportamiento cambiado vía Capa A; ART-014 sin cambio (APR-007). AUD-V-30: en SISBEN (sin encoding) el \r nunca llegaba a Curation. La desviación AUD-V-27 declarada en APR-008 queda cerrada. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:12:42-05:00 |
| **Por qué** | references/03 §3d; re-auditoría por diferencia auditorias/validation.md §8; condiciones 1 y 2 de INT-029 |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-011, ART-012, ART-013, ART-015 |
| **Trazabilidad** | prompt 08 · references/03 §3d |
| **Corrige** | BIT-0054 |

---

## BIT-0071 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-009 (artefacto): Nueva aprobación de artefactos de Validation regenerados con el framework 1.9.5 (references/03 §3d) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T19:12:42-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Re-auditoría por diferencia APROBADO (auditorias/validation.md §8): reproducible; muestra = spec §15 también en CRLF; fixture igual; HOCON OK; 227 pruebas. Cerrados AUD-V-27 (filas sin número no se agrupan, spec §6), AUD-V-28 (RA-02 aislada en Scala) y AUD-V-29 (\r final). AUD-V-31 (UUID) aceptado: basta clave única. La etapa no se re-aprueba: no cambian insumo, alcance, riesgo ni decisión (§3d paso 4). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | medio |
| **Artefactos** | ART-011, ART-012, ART-013, ART-015 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0072 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:12:47-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0073 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-010 (artefacto): Insumo de Acceptance v3 (references/03 §3b paso 2) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T19:13:32-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Re-auditoría APROBADO de la v2 (auditorias/acceptance.md §6); v3 = v2 + AUD-A-13 (marcador RT_ con receivedDate/asOfDate 0 y fileTag vacío, SISBENFileMetadata.scala:25-37) y AUD-A-15 (citas por símbolo). Verificación por diferencia del Orquestador: diff v2→v3 solo esas líneas y la versión; render --audit sin avisos; simulador 9/9 y 2/2; muestra (900001) aceptada con 19 (spec §15); 0.95/0.94 fuera de los .conf. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | medio |
| **Artefactos** | ART-020 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0074 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-011 (transicion): Transición validation → acceptance con evidencia diferida (references/03 §3b paso 3) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T19:13:32-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Se habilita Acceptance con la evidencia diferida de Validation (APR-008, RG-015): la corrida autónoma no tiene pipeline. Acceptance consume el contrato fijado por spec §7 RQ-02 a RQ-05 y el Anexo A handoff_acceptance (códigos, qs.RQS, OxygenDuplicate, marcadores), y ese contrato es el mismo de la Validation aprobada y revisada por §3d (auditorias/validation.md §8). El insumo de Acceptance está aprobado con su hash. Ninguna pregunta bloqueante afecta a validation ni a acceptance. RG-015 se cierra con la evidencia del fork y del pipeline (prompt 05). Checklist: c1 cumple (APR-008); c2 cumple; c3 cumple (ART-020 aprobado); c4 cumple (PA-006 resuelta; PA-003..005 no bloqueantes; PA-002 de state_calculation); c5 cumple por evidencia diferida; c6 cumple (justificación del Desarrollador). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | alto |
| **Artefactos** | ART-020 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0075 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `validation` → estado `aprobada` · transicion_habilitada=True |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:13:32-05:00 |
| **Por qué** | Transición validation→acceptance habilitada por APR-011 (diferida). |
| **Resultado** | `aplicado` |
| **Etapa** | `validation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0076 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `acceptance` → estado `en_progreso` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:13:32-05:00 |
| **Por qué** | Inicio de Acceptance tras APR-011; se encarga la generación a edf-acceptance. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0077 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:13:32-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0078 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Propuesta de Acceptance generada en propuestas/acceptance/ (12 archivos) desde ART-020 v3; registrada como prevalidada |
| **Quién** | Generador edf-acceptance (agente) · registra ORQ-E2E |
| **Cuándo** | 2026-11-24T19:17:53-05:00 |
| **Por qué** | INT-031; render --audit, simulador 9/9 + 2/2, hocon_check, suite 147 |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | medio |
| **Artefactos** | ART-021, ART-022, ART-023 |
| **Trazabilidad** | prompt 08 |
| **Corrige** | — |

---

## BIT-0079 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:17:53-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0080 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-019 (bajo, curation_flow): Las claves standardizedId* se omiten cuando son nulas (omitir_si_nulas, steering 11 §3) en lugar de salir nulas como dice la letra de spec §10.1 para standardizedIdName |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:22:43-05:00 |
| **Por qué** | Propuesto por el analista (INT-032) |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | bajo |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0081 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-020 (alto, pinning): standardizedIdType sale entero con el código Experian; pinning-muric.md:29 lo describe como texto (CC, NIT, CE): si otras fuentes de Colombia publican otro valor, la prioridad 1 no cruza fuentes y se duplican PIN |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:22:43-05:00 |
| **Por qué** | Propuesto por el analista (INT-032) |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0082 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-021 (medio, pinning): La Ñ se conserva en standardizedIdName (spec §10.2) y otras fuentes podrían transliterarla, lo que afecta el cruce por nombre |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:22:43-05:00 |
| **Por qué** | Propuesto por el analista (INT-032) |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0083 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:23:10-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0084 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-012 (artefacto): Artefactos de Acceptance (desde ART-020 v3) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T19:23:33-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Auditoría APROBADO (auditorias/acceptance.md §7). Valores de las fuentes: umbral 0.90 y FQS average(qs.RQS) (HU R-21/R-22; spec RQ-02/03), recordAcceptance literal de RQ-04, filePattern spec §2, PA-006 (SISBFA00001; customReason false). HF-28 resuelto en 1.9.6. Pruebas: ART-021 hocon_check + pyhocon, simulador 9/9 y 2/2, muestra 19, sin 0.95 en .conf; ART-022 lo mismo + 900002 → SISBFA00001; ART-023 es la prueba (reproducible, 60 eventos SINT-*, 3 escenarios HU §5.2). Supuesto S-ACC-06 (AUD-A-16): AcceptanceCondition con 4 argumentos asume result con valor por defecto en oxygen-core; si no, el Spec no compila. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | medio |
| **Artefactos** | ART-021, ART-022, ART-023 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0085 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `acceptance` → estado `pendiente_evidencia` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:23:34-05:00 |
| **Por qué** | Artefactos de Acceptance aprobados; sin pipeline: se pide la aprobación con evidencia diferida (§3b paso 1). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0086 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-013 (contexto): Aprobación de la etapa acceptance con evidencia diferida (references/03 §3b paso 1) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T19:23:34-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Acceptance se aprueba con evidencia diferida: la corrida autónoma no tiene pipeline ni fork (F-01), así que evidencia_pipeline.observada queda null. ART-020 a ART-023 están aprobados por auditoría independiente (auditorias/acceptance.md §6 y §7): se reproducen, simulan 9/9 y 2/2, y la muestra da 19 aceptados (spec §15). Curation consume el contrato fijado por spec RQ-03 y RQ-04 y HU R-22, no la ejecución. La evidencia se reportará con el prompt 05. Escenarios HU §5.2: aceptado y rechazado cubiertos (ejecución diferida); umbral parametrizable cubierto solo en prueba (desplegado 0.90); marcadores y archivo sin registros cubiertos; 900009 pendiente (RG-018). DoD §6: bloque HOCON cumplido (integración sujeta a RG-017); AcceptanceExpressionsSpec generado, compilación diferida, S-ACC-06 abierto hasta HF-33. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | alto |
| **Artefactos** | ART-020, ART-021, ART-022, ART-023 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0087 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-022 (alto, acceptance): Evidencia diferida de acceptance: aprobada sin ejecución de pipeline (observada null) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:23:34-05:00 |
| **Por qué** | APR-013, references/03 §3b paso 1 |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0088 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `acceptance` → estado `aprobada` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:23:34-05:00 |
| **Por qué** | Aplica APR-013 (SIMULADO, SIM-DEV-01, INT-034): aprobada con evidencia diferida. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0089 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:23:34-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0090 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-019 (bajo, curation_flow): Las claves standardizedId* se omiten cuando son nulas (omitir_si_nulas, steering 11 §3) en lugar de salir nulas como dice la letra de spec §10.1 para standardizedIdName |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:36:07-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-038), punto 1 |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | bajo |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0091 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-020 (alto, pinning): standardizedIdType lleva el código Experian (spec §10.1). Si otras fuentes de Colombia publican otro juego de códigos (p. ej. CC) o si ColombiaIdentityMatcher no compara por toString, la prioridad 1 de R-24 no cruza la misma persona entre fuentes |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:36:07-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-038), punto 2 |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0092 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-007: Para la próxima versión de la spec: (1) ¿§10.1 debe decir «ausente (clave omitida) si todos faltan» en vez de «nulo» para standardizedIdName (un null rompe EventPinner)? (2) ¿El tipo «Ninguna» → texto debe publicar el valor literal sin recortar bordes? (hoy se recorta: supuesto del aprobador simulado). |
| **Quién** | Desarrollador SIM-DEV-01 (SIMULADO, INT-038) |
| **Cuándo** | 2026-11-24T19:36:07-05:00 |
| **Por qué** | AUD-C-12 / RG-019; INT-038. Dirigida al PO. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0093 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T19:36:07-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0094 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Revisión §3d de Acceptance: ART-023 regenerado con el framework 1.9.7 (HF-33: AcceptanceCondition con 5 argumentos y prueba Scala del filePattern); solo cambia SISBENAcceptanceExpressionsSpec.scala; vuelve a prevalidado hasta la re-auditoría por diferencia |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:18:22-05:00 |
| **Por qué** | references/03 §3d; condición HF-33/S-ACC-06 de APR-012 |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | medio |
| **Artefactos** | ART-023 |
| **Trazabilidad** | prompt 08 · references/03 §3d |
| **Corrige** | BIT-0084 |

---

## BIT-0095 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:18:22-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0096 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | ART-023 vuelve a aprobado con el hash de APR-012 mientras dura la revisión §3d: una etapa aprobada exige sus artefactos aprobados (regla 7e); la versión regenerada se aprobará con su hash nuevo |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:19:01-05:00 |
| **Por qué** | Regla 7e del validador; HF-43 (references/03 §3d no lo decía) |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | ART-023 |
| **Trazabilidad** | references/03 §3d |
| **Corrige** | BIT-0094 |

---

## BIT-0097 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:22:09-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0098 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Revisión §3d: hash nuevo de ART-023 (src/test de Acceptance) por el framework 1.9.7 (HF-33: 5 argumentos y prueba del filePattern); S-ACC-06 deja de existir |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:30:00-05:00 |
| **Por qué** | Re-auditoría por diferencia auditorias/acceptance.md §8; INT-044 punto 1 |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | medio |
| **Artefactos** | ART-023 |
| **Trazabilidad** | references/03 §3d |
| **Corrige** | BIT-0084 |

---

## BIT-0099 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-014 (artefacto): Nueva aprobación de ART-023 regenerado con 1.9.7 (references/03 §3d) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:30:00-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Re-auditoría por diferencia APROBADO (acceptance.md §8): solo cambia el Spec; reproducible, 9/9 y 2/2, pyhocon OK, 157 pruebas. AUD-A-16 (5 argumentos con result=false) y AUD-A-17 (prueba del filePattern con las 7 claves) cerrados; S-ACC-06 deja de existir. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | medio |
| **Artefactos** | ART-023 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0100 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-015 (artefacto): Insumos de Curation: source-spec-curation.yaml v2.1 y fixture-curation v2 (references/03 §3b paso 2) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:30:00-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Re-auditoría APROBABLE (curation.md §6) con las decisiones de INT-038 aplicadas (omitir_si_nulas [P] con cita del motor, regex literal de spec §2, RG-019 aceptado, «Ninguna» = texto en PA-007). AUD-C-15 posterior correcto y sin cambio de valores (C-CUR-05 abierto, APR-009, F-12 con §9). Fixture idéntico al auditado: 8 escenarios de HU §5.3 (cur-0004 = " 0001000000101" también en Scala), cur-0016 (RG-019), tipos 4/5/8 → 4/8/9 (spec §9.4), 18 subscriberRelation distintos. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | medio |
| **Artefactos** | ART-030, ART-035 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0101 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-008: Para Pinning: (a) ¿cómo lee oxygen-pinning el evento curado (steering 11 §6 #2)? (b) En el fork: ¿existe ColombiaIdentityMatcher, cuál es su firma, compara por toString, y qué juego de códigos publican en standardizedIdType las otras fuentes de Colombia (RG-020)? (c) ¿Cómo tratan la Ñ las otras fuentes (RG-021)? |
| **Quién** | Desarrollador SIM-DEV-01 (SIMULADO, INT-044) |
| **Cuándo** | 2026-11-24T21:30:01-05:00 |
| **Por qué** | AUD-C-11/16; INT-044 punto 2c; steering 11 §6 #2 y #7. Dirigida al Desarrollador con Ingesta 3; cierre N3. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0102 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:30:34-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0103 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-016 (artefacto): Contrato Curation → Pinning 2.0.0 (insumo de curation_flow) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:30:57-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] El lado Curation del contrato es fiel a spec §10.1 y §11 y al steering 11 §3 y §4, y lo confirman el motor (EventPinner.scala:64-65, IdentityMatcher.scala:46-49) y la re-auditoría §6. Lo no verificado afecta solo a Pinning y queda en PA-008, bloqueante para pinning. Marcadores ajustados (INT-044): matcher [C] decisión · [PM] verificación en el fork; lector [A:PA-008]; leyenda [PM] como steering 05:48. 09 §5.1 #2 se mide por la etapa_afectada de PA-008 (HF-46, 1.9.7). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | alto |
| **Artefactos** | ART-034 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0104 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-017 (transicion): Transición acceptance → curation_flow con evidencia diferida (references/03 §3b paso 3) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:30:57-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Se habilita Curation Flow con la evidencia diferida de Acceptance (APR-013, RG-022): la corrida no tiene pipeline. Curation consume el contrato que fija spec RQ-03/RQ-04 (registros aceptados de un archivo aceptado completo), el mismo de la propuesta de Acceptance aprobada (APR-012 y la revisión §3d de ART-023, APR-014). Los insumos ART-030, ART-034 y ART-035 están aprobados con su hash. Ninguna pregunta bloqueante afecta a acceptance ni a curation_flow. Checklist: c1 cumple (APR-013); c2 cumple; c3 cumple (ART-030/034/035 aprobados); c4 cumple (PA-006 resuelta, PA-007 no bloqueante, PA-008 de pinning, PA-002 de state_calculation); c5 cumple por evidencia diferida; c6 cumple. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | alto |
| **Artefactos** | ART-030, ART-034, ART-035 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0105 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `acceptance` → estado `aprobada` · transicion_habilitada=True |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:30:58-05:00 |
| **Por qué** | Transición acceptance→curation_flow habilitada por APR-017 (diferida). |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0106 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `curation_flow` → estado `en_progreso` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:30:58-05:00 |
| **Por qué** | Inicio de Curation Flow tras APR-017; se encarga la generación a edf-curation. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0107 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:30:58-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0108 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-009: oxygen.stage.pinning.reusePin es una sola clave para toda la aplicación de Pinning de Colombia (SparkPinner.scala:223,409), que lee las curadas de todas las fuentes: escribir false regenera los PIN de todas las fuentes. ¿(a) no tocar la clave (SISBEN es ingesta nueva sin generación previa propia), (b) escribir false en el conf de Colombia (riesgo N4 sobre las demás fuentes) o (c) una aplicación de Pinning propia para SISBEN? |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:35:47-05:00 |
| **Por qué** | Analista (INT-047); spec §11; RG-002; logica-pinning.md §8. Dirigida al Desarrollador con Ingesta 3; cierra el PO. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0109 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Propuesta de Curation generada en propuestas/curation/ (12 artefactos + MANIFEST.json + expected/) desde ART-030/034/035; registrada como prevalidada |
| **Quién** | Generador edf-curation (agente) · registra ORQ-E2E |
| **Cuándo** | 2026-11-24T21:36:45-05:00 |
| **Por qué** | INT-046; 8 escenarios HU §5.3 cumplen también en el Spec Scala |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | medio |
| **Artefactos** | ART-031, ART-032, ART-033 |
| **Trazabilidad** | prompt 08 |
| **Corrige** | — |

---

## BIT-0110 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:36:45-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0111 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-018 (artefacto): Artefactos de Curation (processor, fragmentos, pruebas) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:43:23-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Auditoría APROBADO (curation.md §7): contrato del motor (firma de CurationProcessor, gancho subscriberRelation en las dos rutas de Curator.scala:78,89 y :156,179, lookup, DomainDictionaryV2); spec §10.1 (34 claves), §10.4, §12 sin handlers; 8 escenarios HU §5.3. Pruebas: ART-031 SISBENCurationProcessorSpec (18 = expected/) y TransformacionesSpec (original con espacio); ART-032 hocon_check + pyhocon y verificar-claves; ART-033 comparación Spec ↔ expected/. sha256 por archivo en ejecuciones/sisben-e2e/hashes/curation.sha256 (AUD-C-24). Supuesto del aprobador simulado (HF-52): con NBSP en fechas sev. 3 vale el Scala (nulo con FORMATO_INVALIDO). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | medio |
| **Artefactos** | ART-031, ART-032, ART-033 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0112 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `curation_flow` → estado `pendiente_evidencia` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:23-05:00 |
| **Por qué** | Artefactos de Curation aprobados; sin pipeline (§3b paso 1). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0113 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-019 (contexto): Aprobación de la etapa curation_flow con evidencia diferida (§3b paso 1) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:43:23-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Curation Flow se aprueba con evidencia diferida: la corrida autónoma no tiene pipeline ni fork (F-01). Los artefactos están aprobados por auditoría independiente (curation.md §6 y §7): son reproducibles, cumplen los 8 escenarios de HU §5.3 en expected/ y en Scala, y las 34 claves de spec §10.1. Pinning consume el contrato ART-034 aprobado. La evidencia se reporta con el prompt 05. Escenarios HU §5.3 cubiertos (cur-0006 con precondición inyectada RG-005; cur-0007 el alcanzable); desviaciones RG-019 aceptado, PA-007, RG-007; DoD §6 cumplida (ART-031, ART-032, ART-034; lado Pinning en PA-008/PA-009). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | alto |
| **Artefactos** | ART-030, ART-031, ART-032, ART-033, ART-034, ART-035 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0114 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-023 (alto, curation_flow): Evidencia diferida de curation_flow: aprobada sin ejecución de pipeline |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:23-05:00 |
| **Por qué** | APR-019 |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0115 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `curation_flow` → estado `aprobada` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:23-05:00 |
| **Por qué** | Aplica APR-019 (SIMULADO, SIM-DEV-01, INT-050): aprobada con evidencia diferida. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0116 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-020 (transicion): Transición acceptance → curation_stock con evidencia diferida (§3b paso 3; HF-56: queda solo en aprobaciones[]) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Se habilita Curation Stock con la evidencia diferida de Acceptance (APR-013, RG-022). Stock consume el mismo contrato de Acceptance (spec RQ-03, RQ-04) y usa el insumo ART-030 ya aprobado (APR-015). c1 cumple (APR-013); c2 cumple (esta aprobación; ref_aprobacion de acceptance sigue en APR-017 por HF-56); c3 cumple (ART-030 aprobado en curation_flow); c4 cumple (PA-007 no bloqueante; PA-008/009 de pinning; PA-002 de state_calculation); c5 por evidencia diferida; c6 cumple. |
| **Resultado** | `aplicado` |
| **Etapa** | `acceptance` |
| **Riesgo** | alto |
| **Artefactos** | ART-030 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0117 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `curation_stock` → estado `en_progreso` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | Inicio de Curation Stock tras la aprobación de su transición (HF-56: registrada en aprobaciones[]). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_stock` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0118 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-021 (artefacto): ART-040 aprobado por referencia: el mismo processor de Curation Flow (ART-031, APR-018) en runMode stock |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Stock no tiene generación propia (01-flujo-etapas.md:21): usa el mismo processor y runMode lo fija la orquestación. El auditor verificó que el gancho subscriberRelation respeta el orden en la ruta stock (Curator.scala:156,179). El DAG collection-stock existe y no se modifica; el filePattern de ART-032 limita el processor a archivos SISBEN (aditividad). Mismo hash que ART-031; prueba asociada SISBENCurationProcessorSpec más la lectura del motor de la ruta stock. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_stock` |
| **Riesgo** | medio |
| **Artefactos** | ART-040 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0119 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `curation_stock` → estado `pendiente_evidencia` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | ART-040 aprobado; sin pipeline (§3b paso 1). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_stock` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0120 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-022 (contexto): Aprobación de la etapa curation_stock con evidencia diferida (§3b paso 1) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Curation Stock se aprueba con evidencia diferida: no hay pipeline. ART-040 es el processor aprobado de Curation Flow, y en la ruta stock el motor respeta el mismo contrato (Curator.scala:156,179). La evidencia se reporta con el prompt 05. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_stock` |
| **Riesgo** | alto |
| **Artefactos** | ART-040 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0121 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-024 (alto, curation_stock): Evidencia diferida de curation_stock: aprobada sin ejecución de pipeline |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | APR-022 |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_stock` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0122 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `curation_stock` → estado `aprobada` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | Aplica APR-022 (SIMULADO): aprobada con evidencia diferida. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_stock` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0123 · `interaccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Antecedentes para preguntas abiertas: PA-007 suma AUD-C-22 (motivos defensivos fuera de la lista cerrada de spec §10.1); PA-002 suma que collection-stock vuelve a curar todos los cortes SISBEN acumulados (refuerza el filtro por corte vigente, HU R-26) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | INT-050, condiciones 1.4 y 3b.3 |
| **Resultado** | `aplicado` |
| **Etapa** | `state_calculation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 |
| **Corrige** | — |

---

## BIT-0124 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:43:42-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0125 · `cierre_pregunta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-009 resuelta: [SIMULADO · aprobador SIM-PO-01] Opción (a): el bloque de SISBEN no escribe reusePin. «reusePIN = false» (spec §1, §11; HU §3, R-24, §5.4) significa que SISBEN es ingesta nueva sin PIN previos propios, no un valor de la clave global oxygen.stage.pinning.reusePin (escribir false renumera todas las fuentes, SparkPinner.scala:979-985, y rompe HU §5.4 «persona existente»). (b) es N4: se documenta y se escala, no se ofrece. (c) rompe el cruce entre fuentes. Supuesto del PO simulado; aclaración en PA-010. Si en el fork el valor compartido es false, se escala al PO y a Ingesta 3 sin cambiar nada desde la ingesta. |
| **Quién** | PO SIM-PO-01 (SIMULADO, INT-052) |
| **Cuándo** | 2026-11-24T21:47:03-05:00 |
| **Por qué** | Respuesta del aprobador simulado con cita a las fuentes. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0126 · `cierre_pregunta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-008 resuelta: [SIMULADO · aprobador SIM-DEV-01] (a) resuelta con el motor (PinningIOUtils.scala:23-31, OxygenIOReaderUtils.scala:72-75). (b) y (c) pasan a verificación diferida en RG-025 (alto): bloquea la integración al fork y la certificación de Pinning, Synthesis y State Calculation, no la corrida. Condición: ningún artefacto lleva valores que dependan de (b)/(c) (FQCN, atributos, umbral, comparación); si alguno los necesita, PA-008 vuelve a bloquear. Hechos marcados [PM] (RG-025), nunca [C]. |
| **Quién** | Desarrollador SIM-DEV-01 (SIMULADO, INT-052) |
| **Cuándo** | 2026-11-24T21:47:03-05:00 |
| **Por qué** | Respuesta del aprobador simulado con cita a las fuentes. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0127 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-010: Para la próxima versión de HU y spec: (1) ¿qué significa «reusePIN = false» (ingesta sin PIN previos propios, no la clave global de la aplicación compartida)? (2) La ruta Pinned propia de spec §14 frente al pinnedDir único de la aplicación de Pinning de Colombia (AUD-P-07). |
| **Quién** | PO SIM-PO-01 (SIMULADO, INT-052) |
| **Cuándo** | 2026-11-24T21:47:03-05:00 |
| **Por qué** | PA-009 (INT-052); AUD-P-02, AUD-P-07. Dirigida al PO. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0128 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-025 (alto, pinning): Verificación diferida del matcher de Colombia: ColombiaIdentityMatcher no está en Motor-Oxigen; sin fork no se verifican su FQCN, la comparación del standardizedIdType entero y el juego de códigos de las otras fuentes (RG-020), si "" cuenta como nulo en la regla 0, identifying-attributes-new.json, la Ñ (RG-021), AUD-P-03 (¿exige nombre además de tipo+número?) ni el umbral vigente (defecto 5) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:47:04-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-052), PA-008 (b)(c) y AUD-P-03 |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0129 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-002 (alto, pinning): reusePin es una clave global de la aplicación de Pinning de Colombia (SparkPinner.scala:223,409): escribirla desde una ingesta renumera los PIN de todas las fuentes (:979-985); en SISBEN no se escribe (PA-009) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:47:04-05:00 |
| **Por qué** | PO SIM-PO-01 (SIMULADO, INT-052), condición 2 de PA-009 |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0130 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-019 (bajo, curation_flow): Las claves standardizedId* se omiten cuando son nulas (omitir_si_nulas, steering 11 §3) en lugar de salir nulas como dice la letra de spec §10.1 para standardizedIdName |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:47:04-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-052), AUD-P-05 |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | bajo |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0131 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Nota sobre ART-034 (sin revisión §3c): la evidencia de «null lanza» es ISIdentityMatcher.scala:46-54 (EventPinner.scala:64-65 no se usa en batch) y el lector sí está en oxygen-pinning (PinningIOUtils.scala:23-46); se corrige en la próxima versión del contrato. La decisión de omitir nulos (RG-019) no cambia |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:47:04-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-052), AUD-P-05 y defecto 2 |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | ART-034 |
| **Trazabilidad** | R3.4 |
| **Corrige** | BIT-0103 |

---

## BIT-0132 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T21:47:14-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0133 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa pinning: se agrega PA-010 a sus preguntas (PA-008 y PA-009 quedan como historial, resueltas) y RG-025 a sus riesgos (AUD-P-17) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:01:17-05:00 |
| **Por qué** | Hallazgo AUD-P-17 del auditor de Pinning |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 |
| **Corrige** | BIT-0127 |

---

## BIT-0134 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Artefactos de pinning según el modo Colombia (framework 1.9.9, HF-63): ART-051 y ART-052 pasan a no obligatorios y ausentes (no aplican: matcher y lista compartidos); ART-053 es el bloque de verificación de las claves compartidas, sin redefinirlas ni escribir reusePin |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:05:23-05:00 |
| **Por qué** | HF-63 (AUD-P-14): la semilla exigía piezas de modo propio; la spec §11 fija el matcher compartido de Colombia; PA-009 resuelta |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | ART-051, ART-052, ART-053 |
| **Trazabilidad** | prompt 00 paso 2 (1.9.9) |
| **Corrige** | — |

---

## BIT-0135 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Confirmada por el aprobador (INT-057) la corrección del catálogo de Pinning a modo Colombia (BIT-0134; HF-63, AUD-P-14): ART-051/052 no obligatorios (no aplica en modo Colombia: spec §11; SKILL edf-pinning «Modo Colombia»), bajo RG-025; ART-053 obligatorio, alto, solo comentarios VERIFICAR y referencias. Preguntas de la etapa: PA-010 (PA-008/009 resueltas, AUD-P-17) |
| **Quién** | Desarrollador SIM-DEV-01 (SIMULADO) · registra ORQ-E2E |
| **Cuándo** | 2026-11-24T22:06:47-05:00 |
| **Por qué** | INT-057 punto 2 y condición 3 del punto 1 |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | ART-051, ART-052, ART-053 |
| **Trazabilidad** | prompt 00 paso 2 (1.9.9) |
| **Corrige** | BIT-0134 |

---

## BIT-0136 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-023 (artefacto): Insumo de Pinning v3 (§3b paso 2) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T22:07:17-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Insumo fiel a spec §11, HU R-24 y ART-034. Las decisiones PA-008 y PA-009 están aplicadas. Ningún valor que llegue a un artefacto depende de RG-025, cuyos hechos quedan [PM]. No se escribe ninguna clave compartida, y por eso no hay acción N4. RG-025 bloquea la integración al fork y la certificación (§3e). v3 = v2 (re-auditoría APROBADO, pinning.md §6) + AUD-P-13; verificación por diferencia del Orquestador: solo esa línea y la versión; verificar-insumo-pinning y verificar-claves rc 0. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | ART-050 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0137 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-024 (transicion): Transición curation_flow → pinning con evidencia diferida (§3b paso 3) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T22:07:17-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Se habilita Pinning con la evidencia diferida de curation_flow (APR-019, RG-023). Pinning consume el contrato aprobado ART-034: tres claves standardizedId*, tipos y normalización de spec §10.1 y §11. El insumo ART-050 v3 está aprobado. La verificación del matcher de Colombia queda diferida en RG-025, que bloquea la integración al fork y la certificación de Pinning, Synthesis y State Calculation (§3e). RG-002: la clave reusePin es global y no se escribe. Checklist: c1 cumple (APR-019); c2 cumple; c3 cumple (curation_flow y curation_stock aprobadas, ART-050 aprobado); c4 cumple (PA-007 no bloqueante; PA-008/009 resueltas; PA-010 no bloqueante; PA-002 de state_calculation); c5 por evidencia diferida (RG-023); c6 cumple. 09 §5.1: sin [A:*] activos en el insumo, RG-025 bloquea el despliegue, ninguna acción N4. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | alto |
| **Artefactos** | ART-050 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0138 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `curation_flow` → estado `aprobada` · transicion_habilitada=True |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:07:17-05:00 |
| **Por qué** | Transición curation_flow→pinning habilitada por APR-024 (diferida). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_flow` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0139 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-025 (transicion): Transición curation_stock → pinning con evidencia diferida (§3b paso 3) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T22:07:17-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Se habilita Pinning con la evidencia diferida de curation_stock (APR-022, RG-024). Pinning consume el contrato aprobado ART-034: tres claves standardizedId*, tipos y normalización de spec §10.1 y §11. El insumo ART-050 v3 está aprobado. La verificación del matcher de Colombia queda diferida en RG-025, que bloquea la integración al fork y la certificación de Pinning, Synthesis y State Calculation (§3e). RG-002: la clave reusePin es global y no se escribe. Checklist: c1 cumple (APR-022); c2 cumple; c3 cumple (curation_flow y curation_stock aprobadas, ART-050 aprobado); c4 cumple (PA-007 no bloqueante; PA-008/009 resueltas; PA-010 no bloqueante; PA-002 de state_calculation); c5 por evidencia diferida (RG-024); c6 cumple. 09 §5.1: sin [A:*] activos en el insumo, RG-025 bloquea el despliegue, ninguna acción N4. |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_stock` |
| **Riesgo** | alto |
| **Artefactos** | ART-050 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0140 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `curation_stock` → estado `aprobada` · transicion_habilitada=True |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:07:17-05:00 |
| **Por qué** | Transición curation_stock→pinning habilitada por APR-025 (diferida). |
| **Resultado** | `aplicado` |
| **Etapa** | `curation_stock` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0141 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `pinning` → estado `en_progreso` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:07:17-05:00 |
| **Por qué** | Inicio de Pinning tras las dos transiciones (curation_flow y curation_stock); se encarga la generación a edf-pinning en modo Colombia. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0142 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:07:17-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0143 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | ART-053 generado en modo Colombia (propuestas/pinning/pinning-application-block.sisben.conf): solo comentarios VERIFICAR y referencias a las piezas compartidas; sin claves activas; registrado como prevalidado |
| **Quién** | Generador edf-pinning (agente) · registra ORQ-E2E |
| **Cuándo** | 2026-11-24T22:12:02-05:00 |
| **Por qué** | INT-059 |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | ART-053 |
| **Trazabilidad** | prompt 08 |
| **Corrige** | — |

---

## BIT-0144 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:12:02-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0145 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-011: Para la próxima versión de la spec: dentro de un mismo periodo, si el corte más reciente trae un campo nulo, ¿debe ganar el nulo? Hoy LFP no escribe nulos y el valor anterior del periodo se conserva (ConsistencyCheckOps.scala:118,176-181); entre periodos no se arrastra (mapa vacío por periodo, :125). |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:13:27-05:00 |
| **Por qué** | Analista (INT-056), C-CUR-05; spec §12, HU R-25, §5.6. Dirigida al PO. |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0146 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-026 (medio, synthesis): El domain dictionary y el conf de Synthesis son únicos para Colombia: una corrida lee un solo diccionario y un solo customRulesFile (SparkConsistencyChecker.scala:241-245); un nombre de SISBEN que ya exista con otro handler cambiaría otra fuente |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:13:27-05:00 |
| **Por qué** | Propuesto por el analista (INT-056) |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0147 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:13:27-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0148 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Texto de prevalidación de ART-060 reescrito: la heurística anti-PII del validador (regla 6) tomó como nombre de persona la frase que comparaba los campos con ART-032 (falso positivo; HC-05) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:13:57-05:00 |
| **Por qué** | Regla 6 del validador |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | ART-060 |
| **Trazabilidad** | R4 |
| **Corrige** | — |

---

## BIT-0149 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Se reescribió el campo Qué de BIT-0148 (sin cambiar su ID, fecha ni sentido) porque citaba literalmente la frase que dispara la heurística anti-PII de la regla 6; única edición de un evento ya escrito en la corrida, documentada aquí |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:14:15-05:00 |
| **Por qué** | Regla 6 del validador (HC-05); la bitácora es append-only salvo esta excepción documentada |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R4 |
| **Corrige** | BIT-0148 |

---

## BIT-0150 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:14:23-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0151 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Prevalidación de ART-050 apunta a INT-058 (v3) en lugar de INT-053 (v2) (AUD-P-22). La decisión de INT-052 de no revisar ART-034 por la cita a EventPinner se extiende a ART-030 (source-spec-curation.yaml:26), que se corregirá en su próxima versión (AUD-P-25) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:16:27-05:00 |
| **Por qué** | Auditoría de la propuesta de Pinning §7 |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | ART-050, ART-030 |
| **Trazabilidad** | prompt 08 |
| **Corrige** | BIT-0136 |

---

## BIT-0152 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-026 (artefacto): ART-053 v2: bloque HOCON de Pinning en modo Colombia |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T22:19:40-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Bloque sin claves activas, fiel a la sección «Modo Colombia» de la plantilla y al insumo ART-050 v3 (APR-023). Ninguna clave compartida se escribe (PA-009, HF-57) y ningún valor depende de RG-025. La lista VERIFICAR usa las rutas HOCON que lee el motor y remite a los criterios de cierre de RG-025. v2 = v1 (auditoría APROBADO, pinning.md §7) + AUD-P-18/19/20; verificación por diferencia del Orquestador: 0 líneas activas fuera de comentarios, 0 claves compartidas activas en cualquier grafía, 0 {{, pyhocon vacío. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | ART-053 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0153 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `pinning` → estado `pendiente_evidencia` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:19:40-05:00 |
| **Por qué** | ART-050 y ART-053 aprobados; ART-051/052 no obligatorios en modo Colombia; sin pipeline (§3b paso 1). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0154 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-027 (contexto): Aprobación de la etapa pinning con evidencia diferida (§3b paso 1) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T22:19:40-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Pinning se aprueba con evidencia diferida: la corrida autónoma no tiene pipeline ni fork. Los artefactos ART-050 v3 y ART-053 v2 están aprobados por auditoría independiente (pinning.md §6 y §7). No se escribe ninguna clave compartida (PA-009). La verificación del matcher de Colombia sigue diferida en RG-025, que bloquea la integración al fork y la certificación de Pinning, Synthesis y State Calculation (§3e). La evidencia se reporta con el prompt 05. RG-002 reescrito. Escenarios HU §5.4: Background según PA-009; persona existente [C] con cumplimiento [PM] (RG-025; AUD-P-03); persona nueva: PIN nuevo con cualquier reusePin, unión correcta [PM] (RG-025); CC sin coincidencia en BDIIREGI no certificable por la skill (criterio de RG-027). DoD §6: reusePIN = false cumplida según PA-009 (PA-010); atributos de identidad cumplidos en el contrato ART-034 (identifying-attributes-new.json [PM] RG-025). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | ART-050, ART-053 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0155 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-027 (alto, pinning): Evidencia diferida de pinning: aprobada sin ejecución de pipeline |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:19:40-05:00 |
| **Por qué** | APR-027 |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0156 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `pinning` → estado `aprobada` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:19:40-05:00 |
| **Por qué** | Aplica APR-027 (SIMULADO, SIM-DEV-01, INT-062): aprobada con evidencia diferida. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0157 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:19:40-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0158 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-028 (alto, synthesis): Verificación diferida de Synthesis de Colombia (VD-SYN-01..06): nombres existentes en el GDD y su handler; lecturas .get sin defecto (dateType, fieldHandlerHashFunction, orchestration, readWriteFormat, historicAttributeArgs si checkFileTagEnabled); conf compartido; periodicidad efectiva (minFrequency de la clase base); escenario §5.5 con nulo; reglas CONS, custom y relationshipMigrationProcessor de Colombia aplicadas a todos los eventos del HC (ConsistencyCheckOps.scala:150-173, :240-271) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:28:05-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-065), AUD-S-02 y punto 5 |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | alto |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0159 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-026 (medio, synthesis): El GDD, el diccionario de consistencia y las reglas custom son únicos para Colombia. Una entrada existente gobierna también a SISBEN, y modificarla afectaría a la otra fuente. Las reglas compartidas se aplican a los eventos de SISBEN (verificación en RG-028) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:28:05-05:00 |
| **Por qué** | Desarrollador SIM-DEV-01 (SIMULADO, INT-065), punto 4 |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0160 · `interaccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Antecedentes: PA-011 suma (2) el motor ordena por (asOfDate, receivedDate) con asOfDate = fec_actualizacion_cns, no por corte (AUD-S-08) y (3) la periodicidad del evento (minFrequency); PA-010 suma que una ruta Pinned propia de SISBEN no se leería (Synthesis toma la última *-pin-hc, AUD-S-05); PA-002 suma el caso de la misma persona con SR distinta (dos sintéticos para un PIN, AUD-S-07) y el fileTag/voidIndicator (AUD-S-04). ART-060 pasa a riesgo alto (AUD-S-01) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:28:05-05:00 |
| **Por qué** | INT-065 puntos 1, 3, 4 y 5 |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 |
| **Corrige** | — |

---

## BIT-0161 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Artefactos de synthesis según el modo Colombia (framework 1.9.10, HF-68): ART-061 entradas del GDD v2 para integrar en el GDD compartido sin duplicar ni modificar existentes; ART-062 lista de verificación solo de comentarios (alto). Riesgos de la etapa: RG-026, RG-028 |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:37:01-05:00 |
| **Por qué** | HF-68: la semilla exigía un bloque por ingesta; spec §12 y el conf único de Colombia |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | ART-061, ART-062 |
| **Trazabilidad** | prompt 00 paso 2 (1.9.10) |
| **Corrige** | — |

---

## BIT-0162 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-012: Forma de la sección del XPM: (a) ¿la clave es «sisben» o «#sisben»? (b) si un PIN tiene varios documentos SISBEN, ¿un solo objeto o una lista por documento? (c) ¿«no tiene sección» (HU §5.6) se cumple con la sección en null, que es lo que produce el motor? |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:38:51-05:00 |
| **Por qué** | Analista (INT-066); spec §13; HU R-26, §5.6. La decide el PO. |
| **Resultado** | `aplicado` |
| **Etapa** | `state_calculation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0163 · `pregunta_abierta`

| Campo | Valor |
|-------|-------|
| **Qué** | PA-013: Hechos del fork para State Calculation: valor de aliasEnabled en el conf de Colombia (SparkStateCalculator.scala:66-67); App y enricher de Colombia (¿flag tipo isSisben? ¿cómo reciben un valor por corrida?); domain y nombres que ya declaran los JSON compartidos y si usan Nashorn; si una sección null se ve como «sin sección» en el XPM y en Data Provisioning. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:38:51-05:00 |
| **Por qué** | Analista (INT-066); ART-071/072 dependen de estos hechos (references/03 §3e no aplica). Dirigida al Desarrollador con Ingesta 3. |
| **Resultado** | `aplicado` |
| **Etapa** | `state_calculation` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 · condición 4 |
| **Corrige** | — |

---

## BIT-0164 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-029 (medio, state_calculation): Con (c1), un PIN vigente se excluye por error si su evento cae en el mismo periodo de Synthesis que uno anterior con asOfDate mayor (LFP, ConsistencyCheckOps.scala:115-118) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:38:51-05:00 |
| **Por qué** | Propuesto por el analista (INT-066) (R-SC-1) |
| **Resultado** | `aplicado` |
| **Etapa** | `state_calculation` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0165 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-030 (medio, state_calculation): Por PA-011, fechaEncuesta puede no salir nula dentro de un mismo periodo, contra el escenario de HU §5.6 |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:38:51-05:00 |
| **Por qué** | Propuesto por el analista (INT-066) (R-SC-2) |
| **Resultado** | `aplicado` |
| **Etapa** | `state_calculation` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0166 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:38:51-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0167 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-026 (medio, synthesis): El GDD, el diccionario de consistencia y las reglas custom son únicos para Colombia. Una entrada existente gobierna también a SISBEN, y modificarla afectaría a la otra fuente. Las reglas compartidas se aplican a los eventos de SISBEN (verificación en RG-028) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:44:50-05:00 |
| **Por qué** | AUD-S-15 |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0168 · `correccion`

| Campo | Valor |
|-------|-------|
| **Qué** | Texto de PA-002, PA-010 y PA-011 ampliado con los antecedentes que hasta ahora solo estaban en la bitácora (BIT-0123, BIT-0160); mitigación de RG-026 con VD-SYN-01..06 (AUD-S-15) |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:44:50-05:00 |
| **Por qué** | Re-auditoría del insumo de Synthesis v2, AUD-S-15 |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.1 |
| **Corrige** | BIT-0160 |

---

## BIT-0169 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-028 (artefacto): Insumo de Synthesis v3 (§3b paso 2) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T22:47:04-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Insumo fiel a spec §10.1 y §12 y a HU R-25. Las 34 entradas van con LFP, sin handler, sin dataType y con la description autorizada, y ninguna clave se escribe. Nada de lo que llega a ART-061/062 depende de RG-025 ni de RG-028, que bloquean la integración al fork y la certificación (§3e). El plan de reversión y la prueba de no afectación toman como base el GDD de Colombia sin entradas de SISBEN. v3 = v2 (re-auditoría APROBADO, synthesis.md §6) + AUD-S-12/13/14/16; verificación por diferencia del Orquestador. |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | alto |
| **Artefactos** | ART-060 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0170 · `riesgo`

| Campo | Valor |
|-------|-------|
| **Qué** | RG-010 (medio, synthesis): La reference curation-pinning-sisben.md §6/§8 dice que SISBEN va sin Synthesis y periodicidad Unique, contra HU R-25 y spec §12 |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:47:04-05:00 |
| **Por qué** | ART-060 aprobado citando la spec (INT-065, punto 4); se cierra al aprobar el insumo de State Calculation |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | medio |
| **Artefactos** | — |
| **Trazabilidad** | R3.4 · riesgos |
| **Corrige** | — |

---

## BIT-0171 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | APR-029 (transicion): Transición pinning → synthesis con evidencia diferida (§3b paso 3) |
| **Quién** | Desarrollador (simulado) · `SIM-DEV-01` |
| **Cuándo** | 2026-11-24T22:47:04-05:00 |
| **Por qué** | [SIMULADO · aprobador SIM-DEV-01] Se habilita Synthesis con la evidencia diferida de Pinning (APR-027, RG-027): la corrida no tiene pipeline ni fork. Synthesis consume el contrato de Pinning (eventos curados con PIN en el HC de Colombia; spec §12, LFP por defecto), no la ejecución. El insumo ART-060 v3 está aprobado con su hash. La verificación del matcher (RG-025) y la de las reglas compartidas de Synthesis (RG-028) quedan diferidas y bloquean la integración al fork y la certificación de Synthesis y State Calculation (§3e). Cita también RG-026 (medio) y RG-010 (mitigado). Checklist: c1 cumple (APR-027); c2 cumple; c3 cumple (ART-060 aprobado); c4 cumple (PA-010 y PA-011 no bloqueantes; PA-002/012/013 de state_calculation); c5 por evidencia diferida (RG-027); c6 cumple. 09 §5.1: sin [A:*] activos, sin acción N4. |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | alto |
| **Artefactos** | ART-060 |
| **Trazabilidad** | prompt 03 · R3.2 |
| **Corrige** | — |

---

## BIT-0172 · `aprobacion`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `pinning` → estado `aprobada` · transicion_habilitada=True |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:47:04-05:00 |
| **Por qué** | Transición pinning→synthesis habilitada por APR-029 (diferida). |
| **Resultado** | `aplicado` |
| **Etapa** | `pinning` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0173 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Etapa `synthesis` → estado `en_progreso` · transicion_habilitada=False |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:47:05-05:00 |
| **Por qué** | Inicio de Synthesis tras APR-029; se encarga la generación a edf-synthesis en modo Colombia. |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R3.2 · prompt 02 |
| **Corrige** | — |

---

## BIT-0174 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:47:05-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---

## BIT-0175 · `propuesta`

| Campo | Valor |
|-------|-------|
| **Qué** | Propuesta de Synthesis en modo Colombia generada en propuestas/synthesis/ (domaindictionary.json v2 con 34 entradas; bloque de verificación solo de comentarios); registrada como prevalidada |
| **Quién** | Generador edf-synthesis (agente) · registra ORQ-E2E |
| **Cuándo** | 2026-11-24T22:50:40-05:00 |
| **Por qué** | INT-072 |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | alto |
| **Artefactos** | ART-061, ART-062 |
| **Trazabilidad** | prompt 08 |
| **Corrige** | — |

---

## BIT-0176 · `regeneracion_vista`

| Campo | Valor |
|-------|-------|
| **Qué** | Regeneración de estado-ingesta.md desde el JSON. |
| **Quién** | Orquestador (emulado) · `ORQ-E2E` |
| **Cuándo** | 2026-11-24T22:50:40-05:00 |
| **Por qué** | La vista se regenera tras cada cambio de estado (R2.2). |
| **Resultado** | `aplicado` |
| **Etapa** | `synthesis` |
| **Riesgo** | — |
| **Artefactos** | — |
| **Trazabilidad** | R2.2 |
| **Corrige** | — |

---
