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
