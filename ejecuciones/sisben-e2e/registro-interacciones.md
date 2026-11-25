# Registro de interacciones de la corrida autónoma

Cada mensaje enviado a un agente (analista, generador, auditor, aprobador simulado), con lo que se le pidió, lo que respondió y la decisión que se tomó. `INT-nnn` se enlaza con el evento `BIT-nnnn` del expediente.

## INT-001 · Analista (edf-analisis) · `analisis`

- **Cuándo:** 2026-11-24T17:11:35-05:00
- **Pedido:** Modo 1 (4 documentos de etapa 0) y Modo 2 (source-spec-validation.yaml) desde hu-sisben.md y spec-sisben.md 1.0.2.
- **Respuesta:** 4 documentos en analisis/ (22/22 campos), insumo = Anexo A; --solo-verificar OK (22 campos, 29 códigos); prevalidación de la muestra coincide con spec §15. Propone PA-001 (bloqueante, mecanismo de R-26) y riesgos RG-003..011; reporta 7 posibles defectos del framework.
- **Decisión:** Registrar artefactos como prevalidados, PA-001 y riesgos; pasar a auditoría.
- **Bitácora:** —

## INT-002 · Auditor independiente · `analisis`

- **Cuándo:** 2026-11-24T17:20:06-05:00
- **Pedido:** Auditar ART-001..004, ART-010, PA-001 y los 6 posibles defectos del framework contra HU/spec 1.0.2, SKILL.md y motor.
- **Respuesta:** Dictamen AJUSTES: ART-002 y ART-010 APROBADO; ART-001/003/004 AJUSTES (PA-001 mal acotada, IDs de riesgo desalineados, fila 1 §4). Confirma defectos del framework AUD-E0-10/11 (P1) y -13/14/15 (P2); AUD-E0-08/09 son de la spec. Dictamen completo en auditorias/analisis.md.
- **Decisión:** Consultar al aprobador simulado por PA-001 y las condiciones de aprobación; registrar los defectos del framework.
- **Bitácora:** —

## INT-003 · Aprobador simulado SIM-DEV-01 · `analisis`

- **Cuándo:** 2026-11-24T17:20:06-05:00
- **Pedido:** Decidir cierre de PA-001, si se abre PA-002 y qué ajustar en ART-001/003/004 para aprobar la etapa 0.
- **Respuesta:** PA-001 resuelta por las fuentes (State Calculation: HU R-26, §5.6, §6; spec §13). Abrir PA-002 bloqueante solo para state_calculation (mecanismo del corte vigente; opción c1 fechaCorte). Aprobación de la etapa 0 condicional a los ajustes de ART-001 (§6, §4, §7 IDs RG), ART-003 (C-09, C-10, P-01, E-05) y ART-004. Sugiere RG-003 nivel critico.
- **Decisión:** Resolver PA-001, abrir PA-002, subir RG-003 a critico, pedir ronda 2 al analista.
- **Bitácora:** —

## INT-004 · Analista (edf-analisis) · ronda 2 · `analisis`

- **Cuándo:** 2026-11-24T17:21:44-05:00
- **Pedido:** Aplicar ajustes AUD-E0-01..06 y condiciones del aprobador (INT-003) a ART-001/003/004 (y nota en ART-002 fila 19); alinear IDs RG-001..011 con el expediente; PA-001 resuelta y PA-002 solo state_calculation.
- **Respuesta:** Ajustes aplicados: ART-001 1.1.0 (§3 regla 25, §4 sin la reference, §6 PA-001 resuelta y ficha PA-002, §7 RG-001..011 con IDs del expediente, RG-003 crítico), ART-003 1.1.0 (C-09, C-10, P-01, P-02, E-05), ART-004 1.1.0 (filas 21-26, §2, §4). ART-002: nota fila 19 y, para coherencia, RG-005→RG-007 (l.82) y la nota de PA-001 (l.85). ART-010: solo la cabecera de comentarios; YAML sin cambios. verificar-insumos-etapa0 rc=0.
- **Decisión:** Se aceptan los dos cambios extra de ART-002 (evitan IDs inconsistentes). Re-auditoría por diff con el mismo auditor.
- **Bitácora:** —

## INT-005 · Corrector del framework · `analisis`

- **Cuándo:** 2026-11-24T17:21:44-05:00
- **Pedido:** Corregir AUD-E0-10/11/13/14/15 en .kiro/ con pruebas de regresión; CHANGELOG 1.9.1.
- **Respuesta:** 5 defectos corregidos (bloqueo por etapa_afectada también en la regla 5 del validador, que contaba por lista de la etapa; ejemplos SISBEN marcados «solo forma» en validation, acceptance, curation y analisis; excepción de la tabla de mapeo; 3 efectos nuevos en 07; casilla de insumos). Pruebas: tests 114 (antes 85), validation 160, acceptance 127, curation 106; auditar-skills OK.
- **Decisión:** Revisado el diff del validador; commit aparte 7da4ca4 (framework 1.9.1). La condición AUD-E0-18 queda atendida.
- **Bitácora:** —

## INT-006 · Auditor independiente · re-auditoría · `analisis`

- **Cuándo:** 2026-11-24T17:24:34-05:00
- **Pedido:** Re-auditar ART-001..004 y ART-010 tras la ronda 2 (AUD-E0-01..06, coherencia RG/PA con el expediente, YAML = Anexo A).
- **Respuesta:** Ronda 2 APROBADO para los cinco artefactos; AUD-E0-01..06 resueltos; RG/PA coherentes con el expediente; YAML de ART-010 igual al Anexo A (--solo-verificar OK, prevalidación = spec §15). Observaciones nuevas: AUD-E0-16 (P2, fila PA-001 no muestra los valores registrados), AUD-E0-17 (P2, versión de mapeo-campos sin subir), AUD-E0-18 (P1, condición: la transición analisis→validation debe evaluar el bloqueo por etapa_afectada mientras HF-01 no esté corregido).
- **Decisión:** AUD-E0-16/17 al analista (ronda 3 editorial); AUD-E0-18 se atiende con la corrección HF-01 del framework antes de la transición.
- **Bitácora:** —

## INT-007 · Analista (edf-analisis) · ronda 3 · `analisis`

- **Cuándo:** 2026-11-24T17:26:12-05:00
- **Pedido:** AUD-E0-16 (fila PA-001 con valores registrados) y AUD-E0-17 (mapeo-campos 1.0.1).
- **Respuesta:** Fila PA-001 (contexto-ingesta.md:112) con los valores registrados (etapa analisis, bloqueante al abrirse y sin efecto al resolverse BIT-0009, RESUELTA); mapeo-campos.md 1.0.1 (sin historial de cambios en el documento).
- **Decisión:** Cambios editoriales verificados por el Orquestador; AUD-E0-16/17 cerrados. Etapa 0 lista para aprobación cuando HF-01 esté corregido.
- **Bitácora:** —

## INT-008 · Aprobador simulado SIM-DEV-01 · `analisis`

- **Cuándo:** 2026-11-24T17:30:11-05:00
- **Pedido:** Aprobar ART-001..004, la etapa 0 (contexto), el insumo ART-010 y la transición analisis→validation con checklist c1..c6.
- **Respuesta:** Aprueba ART-001..004 (bajo), la etapa 0 (contexto, bajo, con supuesto: cierre formal del PO pendiente no bloqueante), ART-010 (medio, diff limpio contra el Anexo A) y la transición analisis→validation (medio por la etapa afectada, supuesto del aprobador), con checklist c1..c6 (c5 y c6 no aplican). Pide el orden etapa → insumo → transición.
- **Decisión:** Registradas APR-001..004 en ese orden (BIT-0020..0024); hashes sha256 de los cinco artefactos; transición habilitada; Validation en_progreso. Corrección BIT-0028: ref_aprobacion de analisis = APR-004 (regla 7a).
- **Bitácora:** BIT-0020..BIT-0028

## INT-009 · Generador (edf-validation) · `validation`

- **Cuándo:** 2026-11-24T17:32:49-05:00
- **Pedido:** Generar la propuesta de Validation desde el insumo aprobado ART-010 (render_templates.py --out propuestas/validation/), checklist, verificar_hocon, prevalidación de la muestra, revisión a mano de escenarios HU §5; reportar ART-011..015.
- **Respuesta:** 18 archivos + manifest.json en propuestas/validation/ (17 supuestos con biyección ASSUMPTION↔manifest); --solo-verificar OK; verificar_hocon OK (con pyhocon en el scratchpad); suite de la skill 165 passed; fixture: 24 casos con su código; muestra = spec §15. Casos HU revisados: tipo 7 y municipio de otro dpto OK; duplicado con ceros OK en código pero no probado; **ERR_024 (periodo futuro / recepción fuera de rango) NO se cumple**; 23 columnas OK. Reporta 10 posibles defectos (principal: RA-02..04 sin implementar en esquema, plantilla y prevalidador). ART-014 vive en env/application-conf/innersource/propuesta/.
- **Decisión:** Registrar ART-011..015 como prevalidados (BIT-0030) con la ruta real de ART-014; pasar a auditoría independiente con los 10 posibles defectos para confirmar o descartar.
- **Bitácora:** BIT-0030

## INT-010 · Auditor independiente (validation) · `validation`

- **Cuándo:** 2026-11-24T17:39:37-05:00
- **Pedido:** Auditar ART-011..015 de Validation contra HU/spec 1.0.2, insumo ART-010, SKILL.md, checklist de entrega y motor; confirmar o descartar los 10 posibles defectos del generador.
- **Respuesta:** RECHAZADO. Propuesta reproducible byte a byte y fiel al insumo; muestra = spec §15. ART-014 RECHAZADO por P0 AUD-V-01 (validate[]/metrics[] sin filePattern: el motor hace data.filePattern.get en SparkValidator:1417-1446 y fallaría en cada archivo). ART-011/012/013/015 AJUSTES. P1: AUD-V-02 (RA-02..04 sin implementar), -03 (RV-SIS-05 sin normalizar), -04 (spec: escenario «sin marca de tiempo» inalcanzable), -05 (ValidatedEvent recorta el valor original que Curation necesita), -06 (lookups sin lookupHome). 15 P2 (AUD-V-07..21). Defectos del generador: 1,2,4,5,6,8,10 reales; 3 más amplio; 7 y 9 en parte.
- **Decisión:** Verificado el P0 en el motor (SparkValidator.scala:1417-1446 y los confs del motor declaran filePattern). Corregir el framework (P0 + P1 + P2 de framework) con pruebas; consultar al aprobador AUD-V-04, -07, -18 y el cambio del insumo; luego analista, regeneración y re-auditoría.
- **Bitácora:** —

## INT-011 · Corrector del framework (validation) · `validation`

- **Cuándo:** 2026-11-24T17:53:45-05:00
- **Pedido:** Corregir AUD-V-01 (P0), -02, -03, -05, -06 (P1) y los P2 de framework con pruebas; framework 1.9.2.
- **Respuesta:** Framework 1.9.2: filePattern obligatorio + chequeo (V-01); claves RA-02..04 (V-02); regla solo_ceros (V-03); Campos.crudo en validatedRecordJson (V-05); lookupHome (V-06); supuestos_resueltos, fixture multigrupo y nombres_invalidos, skill edf-validation, docs, 07, offset, docs/codigos-error, TaggedValidatedEvent con 17 argumentos (V-08..21). Suites: validation 208, tests 121, acceptance 127, curation 106; auditar-skills OK. Insumo actual sigue OK.
- **Decisión:** Suites verificadas por el Orquestador; commit 872c07c. Pasar al analista el cambio del insumo con la sintaxis nueva.
- **Bitácora:** —

## INT-012 · Aprobador simulado SIM-DEV-01 · `validation`

- **Cuándo:** 2026-11-24T17:53:45-05:00
- **Pedido:** Decidir AUD-V-04 (escenario sin marca de tiempo inalcanzable), el cambio del insumo para RA-02..04 / RV-SIS-05 / fixture, AUD-V-07, AUD-V-18 y AUD-V-20.
- **Respuesta:** No aprueba artefactos. (1, como SIM-PO-01) AUD-V-04: no se cambia el filePattern ni el Anexo A; desviación documentada no bloqueante (PA-003, RG-012). (2) Autoriza claves aditivas en el insumo para RA-02..04 (mes actual en America/Bogota: supuesto), RV-SIS-05 sobre valor normalizado (1-15 ceros igual; 16 ceros y «00 00» → ERR_029) y casos nuevos del fixture con IDs nuevos; ART-010 vuelve a propuesto con re-auditoría y re-aprobación N2; la transición no se reabre. (3) AUD-V-07: spec tal cual, PA-004 y RG-013, prueba que documente. (4) AUD-V-18: PA-005 y RG-014, no certificable por la skill. (5) Cabecera del insumo se corrige con el punto 2 (sin tocar spec_version).
- **Decisión:** Registradas PA-003..005 (no bloqueantes, validation) y RG-012..014 (BIT-0034..0039). Cambio del insumo al analista cuando el framework 1.9.2 publique las claves.
- **Bitácora:** BIT-0034..BIT-0039

## INT-013 · Analista (edf-analisis) · insumo v2 · `validation`

- **Cuándo:** 2026-11-24T18:15:59-05:00
- **Pedido:** Versión 2 de source-spec-validation.yaml con las claves 1.9.2 autorizadas (INT-012) y verificaciones.
- **Respuesta:** Insumo v2: cabecera (v2, PROPUESTO, 7 líneas que se apartan del Anexo A con cita), supuestos_resueltos (9 IDs con su sección de la spec; F-13 queda abierto), RA-02..04 en true, RV-SIS-05 tipo solo_ceros, casos DUP_CEROS_ANTIGUO/RECIENTE y 6 nombres_invalidos. No aplicado: header con nombres fuente (el esquema no lo admite; el generador ya prueba HeaderFuente desde alias). --solo-verificar OK; muestra = spec §15; los nombres inválidos dan ERR_024 y el control sale VALIDADO.
- **Decisión:** ART-010 v2 prevalidado; re-auditoría del insumo antes de re-aprobarlo.
- **Bitácora:** —

## INT-014 · Auditor independiente (validation) · insumo v2 · `validation`

- **Cuándo:** 2026-11-24T18:19:27-05:00
- **Pedido:** Auditar el insumo ART-010 v2 contra la spec, el Anexo A y la autorización INT-012.
- **Respuesta:** Insumo v2 APROBADO: diff contra el Anexo A = solo lo autorizado en INT-012, con citas; 9 supuestos_resueltos verificados contra las plantillas; fixture sigue HU §5.1; --solo-verificar OK; muestra = spec §15; los 7 nombres v7 dan ERR_024; RV-SIS-05 con 16 ceros, «00 00» y « 0000» da ERR_029. P2: AUD-V-22 (cabecera omite el supuesto de America/Bogota; el esquema no admite supuestos en archivo.nombre), -23 (cabecera sobre preguntas abiertas), -24 (hash v1 en el expediente), -25/-26 informativos, -27 (para la propuesta: VAL-SP03 agrupa dos líneas sin número).
- **Decisión:** AUD-V-22/23 al analista (solo comentarios); AUD-V-24 se resuelve al re-aprobar (hash nuevo); AUD-V-22 (esquema) se registra como HF-13 P2; AUD-V-27 pasa a la re-auditoría de la propuesta.
- **Bitácora:** —

## INT-015 · Analista (edf-analisis) · insumo v2 comentarios · `validation`

- **Cuándo:** 2026-11-24T18:22:41-05:00
- **Pedido:** AUD-V-22/23: corregir solo comentarios de la cabecera del insumo v2.
- **Respuesta:** Cabecera corregida (l.24-25, 38-43): supuesto [PM] SUP-APR-01 (mes actual de RA-02 en America/Bogota) y nota del esquema; texto de PA-003..005. --solo-verificar OK; YAML sin comentarios idéntico; sha256 dd707688…
- **Decisión:** Verificado por el Orquestador (diff sin comentarios vacío). Pedir al aprobador la re-aprobación de ART-010 v2 y la nueva aprobación de la transición (references/03 §3c pasos 3 y 4).
- **Bitácora:** —

## INT-016 · Aprobador simulado SIM-DEV-01 · `validation`

- **Cuándo:** 2026-11-24T18:23:08-05:00
- **Pedido:** Re-aprobar ART-010 v2 y aprobar de nuevo la transición analisis→validation (references/03 §3c).
- **Respuesta:** Re-aprueba ART-010 v2 (medio; hash dd707688…; SUP-APR-01 en la justificación con formato 04 §6) y aprueba de nuevo la transición analisis→validation (medio; c1..c4 cumplen, c5 y c6 no aplican), en ese orden. No cubre ART-011..015.
- **Decisión:** Registradas BIT-0046 (corrección del hash), APR-005 (insumo v2), APR-006 (transición); ref_aprobacion de analisis = APR-006; validation en_progreso (BIT-0050). Regenerar la propuesta.
- **Bitácora:** BIT-0046..BIT-0050

## INT-017 · Generador (edf-validation) · regeneración · `validation`

- **Cuándo:** 2026-11-24T18:25:01-05:00
- **Pedido:** Regenerar la propuesta de Validation con el insumo v2 (APR-005) y el framework 1.9.2; repetir verificaciones.
- **Respuesta:** Regenerada (18 archivos + manifest; codigos-error en docs/). Verificaciones V1-V6 OK (HOCON OK con filePattern y lookupHome; muestra = spec §15; fixture 26 casos). Supuestos abiertos 8, resueltos 9. Casos HU: ERR_024 por fechas ahora sí; duplicado con ceros OK; header con nombres fuente OK; solo ceros y 23 columnas OK. V7: 1 prueba del framework falla (D1). Posibles defectos: D1 (prueba acoplada al insumo E2E), D2 (RA-02 no aislable), D3 (RESUELTO F-12 que pide confirmar), D4 (SUP-APR-01 sin marca = HF-13).
- **Decisión:** D1 y D3 corregidos en el framework 1.9.4 (HF-14, HF-15, commit 5ae3380); D2 a la re-auditoría y al PO; regenerar una vez más.
- **Bitácora:** —

## INT-018 · Generador (edf-validation) · regeneración 1.9.4 · `validation`

- **Cuándo:** 2026-11-24T18:29:45-05:00
- **Pedido:** Regenerar tras framework 1.9.4 y repetir V1-V7.
- **Respuesta:** Regenerada con 1.9.4: frente a la entrega anterior solo cambia la línea 7 de lookups-sisben.conf y su sha256 en el manifest. V1-V7 OK (suite 216 passed con pyhocon); muestra = spec §15; fixture igual. 8 supuestos abiertos.
- **Decisión:** ART-011..015 prevalidados; re-auditoría de la propuesta.
- **Bitácora:** —

## INT-019 · Auditor independiente (validation) · re-auditoría de la propuesta · `validation`

- **Cuándo:** 2026-11-24T18:30:48-05:00
- **Pedido:** Re-auditar ART-011..015 regenerados (insumo v2, framework 1.9.4).
- **Respuesta:** APROBADO ART-011..015. Reproducible (diff -r vacío, 18/18 hashes); muestra = spec §15; fixture OK; 7 nombres v7 → ERR_024; RV-SIS-05 → ERR_029; verificar_hocon OK; suite 216. Cerrados AUD-V-01/02/03/05/06/08/09/10/16/17/19. P2 abiertos: AUD-V-27 (dos filas sin número se agrupan aunque VAL-SP03 figura RESUELTO), AUD-V-28 (D2: ninguna prueba aísla RA-02; sí es aislable con offset +1400), AUD-V-29 (Campos.crudo deja \r con CRLF). Riesgos residuales: F-01/RG-006; confirmación contra oxygen-core pendiente; PA-003..005.
- **Decisión:** Registrar AUD-V-27/28/29 como HF-16..18 (P2, lote de correcciones al cierre); pedir al aprobador ART-011..015 y la etapa con evidencia diferida.
- **Bitácora:** —

## INT-020 · Analista (edf-analisis) · insumo de Acceptance · `acceptance`

- **Cuándo:** 2026-11-24T18:31:02-05:00
- **Pedido:** Preparar insumos/source-spec-acceptance.yaml (ART-020) desde HU §5/§6 y spec 1.0.2, con verificaciones de edf-acceptance.
- **Respuesta:** Borrador de source-spec-acceptance.yaml (sin anexo en la spec: valores de HU R-20..22, §5.2, §6 y spec §1, §2, §6, §7, §8, handoff del Anexo A). Umbral 0.90 sobre average(qs.RQS); regla de registro errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate"); filePattern de spec §2; sin intervención manual ni inferDelete. Fixture de 9 archivos; render --audit rc=0; simular_decision: 9/9, la muestra acepta 19 (= spec §15); hocon_check OK. Propone PA-006 (nombre/reasonCode del rechazo, archivo vacío, customReason) bloqueante para el insumo. Reporta 5 posibles defectos del framework (P2).
- **Decisión:** Registrar ART-020 prevalidado y PA-006; auditor independiente de Acceptance para el insumo y los 5 posibles defectos; PA-006 al aprobador.
- **Bitácora:** —

## INT-021 · Aprobador simulado SIM-DEV-01 · `validation`

- **Cuándo:** 2026-11-24T18:34:06-05:00
- **Pedido:** Aprobar ART-011..015 y la etapa validation con evidencia diferida (references/03 §3b paso 1).
- **Respuesta:** Aprueba ART-011..015 (medio; ART-015 bajo) con condiciones (F-01; HF-16 desviación de spec §6 a corregir antes de certificar; HF-17; HF-18 antes de ART-030 → RG-016; confirmar oxygen-core y lookupHome) y la etapa validation con evidencia diferida (alto; RG-015; tabla de escenarios HU §5.1: cubiertos, PA-003 en parte, PA-005 no certificable).
- **Decisión:** Registradas APR-007 (artefactos con sha256), RG-016, validation pendiente_evidencia → APR-008 (contexto alto, diferida) → aprobada; RG-015 abierto.
- **Bitácora:** BIT-0054..BIT-0059

## INT-022 · Auditor independiente (acceptance) · `acceptance`

- **Cuándo:** 2026-11-24T18:36:23-05:00
- **Pedido:** Auditar el insumo ART-020, el contrato con la Validation aprobada, PA-006 y los 5 posibles defectos del framework.
- **Respuesta:** Insumo ART-020 AJUSTES (sin P0). Valores de negocio fieles y citados; contrato de entrada = Validation aprobada; herramientas OK (render --audit, hocon_check, simulador 9/9 con script propio, muestra acepta 19, suite 127). P1 AUD-A-01: escenario «umbral parametrizable» (HU §5.2, DoD §6) sin cobertura. P2: AUD-A-02 (desempate del fixture 900005), -03 (marcadores del fixture distintos de los de Validation), -04 (cabecera), -05 (customReason true sin efecto y error en log), -06 (RT_/EP_), -07 (errorInEmptyFileBody es [C]), -08..10 (framework: simulador no compara, comentario reasonCode, especificacion-sisben.md), -11 (RG-015 duplicado), -12 (motor). Los 5 posibles defectos: reales (5 reclasificado). PA-006: bien planteada pero la cierra el Desarrollador (N2); (b) se deduce de R-22/RQ-03 y §10.3; (c) recomienda false.
- **Decisión:** AUD-A-11 corregido (BIT-0063, HC-04). Framework: defectos 1-5 y AUD-A-08..10 + HF-16..18 a un corrector (1.9.5). PA-006 y AUD-A-01 al aprobador. Luego ronda 2 del analista.
- **Bitácora:** —

## INT-023 · Corrector del framework (acceptance + lote validation) · `acceptance`

- **Cuándo:** 2026-11-24T18:47:50-05:00
- **Pedido:** Corregir HF-16..24 con pruebas; framework 1.9.5.
- **Respuesta:** HF-16..24 corregidos con pruebas (acceptance 146, validation 220, curation 106, tests 126; auditar-skills OK). Claves nuevas: expresiones_propuestas, severidades {habilitado,estado}, fixture.umbrales_alternativos. La propuesta de Validation regenerada con 1.9.5 difiere en FieldValidators.scala, FieldValidatorsSpec.scala, SISBENValidationProcessorSpec.scala y manifest.json; prevalidación igual.
- **Decisión:** Suites verificadas; framework 1.9.5 commit 6cdd122 (con HF-25, references/03 §3d). Validation: regenerar y re-auditar por diferencia (§3d); Acceptance: ronda 2 del analista.
- **Bitácora:** —

## INT-024 · Aprobador simulado SIM-DEV-01 · `acceptance`

- **Cuándo:** 2026-11-24T18:47:50-05:00
- **Pedido:** Decidir PA-006 (a, b, c) y cómo cubrir AUD-A-01.
- **Respuesta:** PA-006 la cierra SIM-DEV-01: (a) RQS_BAJO_UMBRAL_SISBEN/SISBFA00001 [P] con comprobación en el fork [PM]; (b) rechazo (solo header / todo filtrado); (c) false. Autoriza umbral alternativo de prueba (0.95: 19/20 aceptado, 47/50 rechazado; el desplegable sigue 0.90; la prueba toma el umbral de la configuración). Autoriza AUD-A-02 (ganador por fecha + empate explícito por eventId), -03 (marcadores como los emite Validation), -04, -06 (RT_ con debe_coincidir true), -07 ([C]). Pide RG para AUD-A-12.
- **Decisión:** PA-006 resuelta; RG-017 y RG-018 abiertos. Ronda 2 del analista cuando el framework 1.9.5 publique la clave de umbrales alternativos.
- **Bitácora:** —

## INT-025 · Generador (edf-validation) · revisión §3d · `validation`

- **Cuándo:** 2026-11-24T19:05:04-05:00
- **Pedido:** Regenerar la propuesta de Validation con el framework 1.9.5 (HF-16..18), mismo insumo v2.
- **Respuesta:** Regenerada con 1.9.5: cambian FieldValidators.scala (HF-18 \r final; HF-16 clave única «SIN_CLAVE»+UUID), FieldValidatorsSpec.scala (3 pruebas), SISBENValidationProcessorSpec.scala (RA-02 aislada con +1400; prueba HF-16) y manifest.json (3 hashes). El resto idéntico. V1-V7 OK (227 con pyhocon); muestra y fixture iguales; muestra en CRLF igual. Observación: el UUID hace que `keys` de registros sin tipo/número cambie en cada ejecución.
- **Decisión:** Re-auditoría por diferencia (§3d), incluida la observación del UUID.
- **Bitácora:** —

## INT-026 · Analista (edf-analisis) · insumo de Acceptance v2 · `acceptance`

- **Cuándo:** 2026-11-24T19:05:04-05:00
- **Pedido:** ART-020 v2: PA-006 resuelta, umbrales_alternativos, AUD-A-02/03/04/06/07, claves 1.9.5.
- **Respuesta:** ART-020 v2 aplica INT-024: cabecera (APR-008/RG-015, PA-006 resuelta [P], errorInEmptyFileBody [C]); clave RT_ con debe_coincidir; customReason false y cuerpo vacío rechazado; severidades sin relleno y expresiones_propuestas; umbrales_alternativos 0.95 (FQS_095 aceptado, FQS_094 rechazado SISBFA00001); 900005 con 3 grupos (fecha, RG-013, empate por eventId); marcadores como los emite Validation. render --audit rc 0; simulador 9/9 + 2/2; muestra acepta 19; 0.95 fuera de los .conf. No aplicado: validatedRecordJson null (el esquema solo admite texto; posible defecto P2).
- **Decisión:** ART-020 v2 prevalidado; re-auditoría del insumo; validatedRecordJson null → HF-26 (P2).
- **Bitácora:** —

## INT-027 · Auditor independiente (validation) · re-auditoría §3d · `validation`

- **Cuándo:** 2026-11-24T19:06:32-05:00
- **Pedido:** Re-auditar por diferencia la propuesta regenerada con 1.9.5 (4 archivos) y la observación del UUID.
- **Respuesta:** Validation sigue APROBADO (ART-011 comportamiento vía Capa A, ART-012, ART-013, ART-015). AUD-V-27/28/29 cerrados; reproducible; muestra = spec §15 (también en CRLF); fixture igual; verificar_hocon OK; suite 227. UUID no es problema (AUD-V-31 informativo: basta clave única; documentar exclusión de eventId/keys al comparar corridas). AUD-V-30 rectifica AUD-V-29: sin `encoding` el motor lee con textFile y ya quita el CR; HF-18 protege la otra ruta.
- **Decisión:** Pedir al aprobador la nueva aprobación de ART-011..015 (§3d) junto con ART-020 y la transición; RG-016 pasa a mitigado (HF-18 corregido y AUD-V-30); AUD-V-31 al lote de documentación (HF-27).
- **Bitácora:** —

## INT-028 · Auditor independiente (acceptance) · insumo v2 · `acceptance`

- **Cuándo:** 2026-11-24T19:08:12-05:00
- **Pedido:** Re-auditar ART-020 v2 contra INT-024 y el informe de la ronda 1.
- **Respuesta:** ART-020 v2 APROBADO: ajustes de INT-024 aplicados y citados; render --audit sin avisos; hocon_check OK; 0.95 fuera de los .conf; simulador 9/9 + 2/2 (y detecta NO COINCIDE con una copia alterada); muestra acepta 19; suite 146; el Spec toma el umbral de la configuración. P2: AUD-A-13 (marcador RT_ con receivedDate/asOfDate/fileTag en 0/vacío), AUD-A-14 (framework: comentario engañoso sobre customReason en acceptance-block.conf.tpl:29), AUD-A-15 (dos citas desfasadas a FieldValidators.scala).
- **Decisión:** AUD-A-14 → HF-28 (lote de cierre). Consulta consolidada al aprobador: ART-011..015 (§3d), ART-020 (con AUD-A-13/15 como P2) y transición validation→acceptance (riesgo alto, evidencia diferida).
- **Bitácora:** —

## INT-029 · Aprobador simulado SIM-DEV-01 · `acceptance`

- **Cuándo:** 2026-11-24T19:11:14-05:00
- **Pedido:** Re-aprobar ART-011..015 (§3d), aprobar ART-020 v2 y la transición validation→acceptance (§3b pasos 2 y 3).
- **Respuesta:** (1) Re-aprueba ART-011/012/013/015 con hashes nuevos (ART-014 sigue con APR-007); la etapa no se re-aprueba. (2) No aprueba ART-020 v2: pide v3 con AUD-A-13 (900006 receivedDate/asOfDate 0, fileTag vacío) y AUD-A-15 (citas por símbolo), verificada por diferencia; HF-26 aceptada como desviación; HF-28 condición para ART-021; RG-017 antes del fork. (3) Aprueba la transición validation→acceptance (alto, diferida) en el orden: artefactos de Validation → ART-020 v3 → transición.
- **Decisión:** Registrada la corrección §3d y la nueva aprobación de Validation; v3 al analista; luego ART-020 v3 y la transición.
- **Bitácora:** —

## INT-030 · Analista (edf-analisis) · insumo de Acceptance v3 · `acceptance`

- **Cuándo:** 2026-11-24T19:12:43-05:00
- **Pedido:** AUD-A-13 y AUD-A-15 en ART-020 (nada más).
- **Respuesta:** v3: 900006 con receivedDate/asOfDate/fileAsOfDate 0 y fileTag vacío; citas por símbolo. Diff solo esas líneas; render --audit sin avisos; simulador 9/9 y 2/2; muestra 19; sin 0.95/0.94 en .conf. sha256 d8ac9e7d…
- **Decisión:** Verificación por diferencia hecha por el Orquestador (mismos resultados). Registradas APR-010 (ART-020 v3), APR-011 (transición validation→acceptance, alto, diferida); acceptance en_progreso (BIT-0073..0076). HF-28 corregido en 1.9.6 antes de generar Acceptance.
- **Bitácora:** BIT-0073..BIT-0076

## INT-031 · Generador (edf-acceptance) · `acceptance`

- **Cuándo:** 2026-11-24T19:14:14-05:00
- **Pedido:** Generar ART-021..023 desde el insumo aprobado ART-020 v3 con el framework 1.9.6.
- **Respuesta:** 12 archivos en propuestas/acceptance/: ART-021 acceptance-block.conf (filePattern spec §2, reject anyOf(RQS_BAJO_UMBRAL_SISBEN), accept >= 0.90, recordAcceptance RQ-04, customReason false), ART-022 file-conditions.conf (1 condición SISBFA00001), ART-023 SISBENAcceptanceExpressionsSpec + 9 directorios de fixture. ACC-TPL-03/04 omitidas. Verificaciones OK; 900001 acepta 19. Escenarios HU §5.2 cubiertos (umbral parametrizable con 0.95 solo en prueba). Posibles defectos: S-ACC-03/05 y checklist «mismos supuestos» atados al ejemplo; restos de SISBEN histórico en inventario-plantillas, dsl-config-reference y decisiones-y-abiertos; checklist pide documentos SINT*; cabecera del insumo dice PROPUESTO.
- **Decisión:** ART-021..023 prevalidados (BIT-0078); auditor independiente de Acceptance para la propuesta y los posibles defectos.
- **Bitácora:** BIT-0078

## INT-032 · Analista (edf-analisis) · insumos de Curation · `curation_flow`

- **Cuándo:** 2026-11-24T19:14:45-05:00
- **Pedido:** Preparar insumos/source-spec-curation.yaml (ART-030) e insumos/contrato-curation-pinning.md (ART-034).
- **Respuesta:** ART-030 source-spec-curation.yaml (34 claves de spec §10.1 + advertenciasCuracion; filePattern spec §2; America/Bogota; Ñ; niveles sev. 3 vacíos; homologación 202; subscriberRelation tipo_número; omitir_si_nulas [PM]) y ART-034 contrato-curation-pinning.md (3 claves, subscriberRelation, ColombiaIdentityMatcher, reusePIN false). Fixture nuevo en insumos/fixture-curation/ (15 eventos, 8 escenarios HU §5.3; RG-005 y RG-007 cubiertos). --solo-verificar OK; generación completa OK; hocon_check OK; verificar-claves coherente. Escenario HU §5.3 numeroDocumentoOriginal " 0001000000101" NO se cumple (sale sin espacio). Riesgos sugeridos RG-019..021. 6 posibles defectos (P1: transformación original recorta).
- **Decisión:** ART-030, ART-034 y ART-035 (fixture) prevalidados; RG-019..021 registrados (BIT-0080..0082); auditor independiente de Curation.
- **Bitácora:** BIT-0080..BIT-0082

## INT-033 · Auditor independiente (acceptance) · propuesta · `acceptance`

- **Cuándo:** 2026-11-24T19:17:53-05:00
- **Pedido:** Auditar ART-021..023 y los 4 posibles defectos del generador.
- **Respuesta:** APROBADO ART-021..023 (sin P0/P1). Reproducible (diff -r vacío); render --audit sin avisos; hocon_check OK con pyhocon real; sin 0.95/0.94 en .conf; simulador 9/9 + 2/2; muestra 19; suite 147; el Spec toma el umbral de la configuración. Los 4 posibles defectos son reales (P2 framework). Nuevos P2: AUD-A-16 (AcceptanceCondition con 4 argumentos frente a 5 del motor), AUD-A-17 (el Spec no prueba el filePattern), AUD-A-18 (hashes se registran al aprobar; PA-006 resuelta sigue listada en la etapa).
- **Decisión:** Registrar HF-29..33 (lote de cierre). Pedir al aprobador ART-021..023 y la etapa acceptance con evidencia diferida.
- **Bitácora:** —

## INT-034 · Aprobador simulado SIM-DEV-01 · `acceptance`

- **Cuándo:** 2026-11-24T19:21:56-05:00
- **Pedido:** Aprobar ART-021..023 y la etapa acceptance con evidencia diferida.
- **Respuesta:** Aprueba ART-021..023 (medio) con S-ACC-06 declarado (AUD-A-16) y condiciones (HF-33 por §3d antes del fork; checklist B del filePattern; RG-017 antes de integrar; HF-26 desviación). Aprueba la etapa acceptance con evidencia diferida (alto) y riesgo separado (sugerido RG-019, asignado RG-022 porque RG-019 ya existía), con tabla de escenarios HU §5.2 y DoD §6.
- **Decisión:** Registradas la aprobación de artefactos con sha256, acceptance pendiente_evidencia → aprobación de contexto (alto, diferida) → aprobada; RG-022 abierto.
- **Bitácora:** —

## INT-035 · Auditor independiente (curation) · `curation_flow`

- **Cuándo:** 2026-11-24T19:23:09-05:00
- **Pedido:** Auditar ART-030, ART-034, ART-035 y los 6 posibles defectos del framework.
- **Respuesta:** AJUSTES (sin P0). ART-030 fiel (34 claves de spec §10.1; epoch, Ñ, homologación, subscriberRelation); P2 de marcadores (AUD-C-12). ART-035 cubre los 8 escenarios + RG-005/007; cur-0004 falla por el P1; AUD-C-02 (cur-0001 y cur-0004 con la misma clave de duplicado), AUD-C-03 (faltan RG-019 y tipos 4, 5, 8). ART-034 coherente con steering 11 y el motor; ajustes documentales (AUD-C-11). P1 AUD-C-01 (framework): `original` recorta en Transformations, CurationProcessor, AcceptedEventReader y la réplica; la prueba generada fija el valor errado. Defectos: 1-5 reales, 6 descartado. Otros P2 framework: AUD-C-04, -05 (override de subscriberRelation puede lanzar NPE y cura dos veces), -07, -13 (vaciado con preservación no implementado; resuelve la observación de HF-04). Riesgos: RG-019 real (null rompe EventPinner:64-65; falta aceptación explícita del Desarrollador), RG-020 real (riesgo en los códigos, no en el tipo JSON), RG-021 real.
- **Decisión:** Corrector del framework Curation (HF-34..41) y lote de cierre (HF-26, 27, 29..33) en paralelo; aprobador para RG-019/020 y los ajustes del insumo; luego ronda 2 del analista.
- **Bitácora:** —

## INT-036 · Corrector del framework (curation) · `curation_flow`

- **Cuándo:** 2026-11-24T19:33:59-05:00
- **Pedido:** Corregir HF-34..41 con pruebas.
- **Respuesta:** HF-34..41 corregidos (cortado una vez por el límite de uso y reanudado): original sin recorte (Transformaciones.original, AcceptedRecord.crudo), override sin NPE y sin doble curación, supuestos_resueltos, verificar-claves por §1, código pendiente null en la homologación, nombre de skill, fixture fileAsOfDate/fileTag, referencias históricas, vaciado con preservación no soportado. Con el insumo de la corrida cur-0004 da " 0001000000101". Suites curation 126, tests 141; auditar-skills OK.
- **Decisión:** Commits 955bed3 (código) y 21b6781 (framework 1.9.7). Ronda 2 de los insumos de Curation con supuestos_resueltos.
- **Bitácora:** —

## INT-037 · Corrector del framework (lote de cierre) · `acceptance`

- **Cuándo:** 2026-11-24T19:33:59-05:00
- **Pedido:** Corregir HF-26, 27, 29..33 con pruebas.
- **Respuesta:** HF-26, 27, 29..33 corregidos con pruebas (acceptance 157, validation 220, tests 137; auditar-skills OK). Regenerar cambia solo SISBENAcceptanceExpressionsSpec.scala (HF-33); Validation no cambia. No corregido: plantillas de etapa 0 con «Estado | PROPUESTO», interpretar-salida.md:71 con customReason true, fila histórica AUD-A3.
- **Decisión:** Suites verificadas; commit a205649 (1.9.7 parte 1). Acceptance: revisión §3d de ART-023. Los tres restos no corregidos → HF-42 (lote final).
- **Bitácora:** —

## INT-038 · Aprobador simulado SIM-DEV-01 · `curation_flow`

- **Cuándo:** 2026-11-24T19:33:59-05:00
- **Pedido:** RG-019 (desviación de spec §10.1), RG-020 (descripción) y ajustes del insumo de Curation AUD-C-02/03/11/12.
- **Respuesta:** (1) Acepta RG-019 (bajo, [P]; omitir = nulo para el matching; null rompe EventPinner) y pide PA-007 no bloqueante para la spec (más el recorte de «Ninguna» → texto como supuesto). (2) RG-020 redactado sobre el juego de códigos; sigue alto; condiciona el insumo de Pinning. (3) Autoriza AUD-C-02 (NUM_DOCUMENTO único en base_registro), -03 (caso RG-019 y tipos 4, 5, 8), -11 (contrato), -12 ([P] y regex literal de spec §2 si el generador la admite). Los P2 del framework que cambian la salida van en el mismo ciclo que AUD-C-01.
- **Decisión:** RG-019 aceptado, RG-020 redactado, PA-007 abierta (no bloqueante). Ronda 2 del analista cuando el framework 1.9.7 esté publicado.
- **Bitácora:** —

## INT-039 · Generador (edf-acceptance) · revisión §3d · `acceptance`

- **Cuándo:** 2026-11-24T21:16:50-05:00
- **Pedido:** Regenerar Acceptance con a205649 (HF-26, HF-33); mismo insumo v3.
- **Respuesta:** Solo cambia SISBENAcceptanceExpressionsSpec.scala (sha256 1f7838db…): 5 argumentos y prueba del filePattern sobre las 7 claves_de_prueba. Verificaciones OK. Nuevo: rutas /home/app/... de las claves de prueba en el Spec (¿criterio C del checklist?), convención implícita del prefijo ruta_, dependencia de commons-io sin certificar.
- **Decisión:** ART-023 a prevalidado (§3d); re-auditoría por diferencia con las tres observaciones.
- **Bitácora:** —

## INT-040 · Auditor independiente (acceptance) · re-auditoría §3d · `acceptance`

- **Cuándo:** 2026-11-24T21:18:22-05:00
- **Pedido:** Re-auditar por diferencia ART-023 regenerado con 1.9.7.
- **Respuesta:** ART-023 sigue APROBADO por diferencia (reproducible; solo cambia el Spec +36/−2; simulador 9/9 + 2/2; pyhocon OK; suite 157). AUD-A-16/17 cerrados. Observaciones: rutas /home/app/... descartada (sintéticas, filePattern con .*); prefijo ruta_ implícito real P2 de framework; commons-io real P2 informativo (transitiva del motor). Nuevo AUD-A-19 (insumo P2): clave otra_fuente sin prefijo ruta_ no prueba el caso negativo.
- **Decisión:** HF-44 (convención ruta_ sin declarar) al lote final; AUD-A-19 para la próxima versión del insumo (no bloquea); re-aprobación de ART-023 junto con la consulta de Curation.
- **Bitácora:** —

## INT-041 · Analista (edf-analisis) · insumos de Curation v2 · `curation_flow`

- **Cuándo:** 2026-11-24T21:18:22-05:00
- **Pedido:** ART-030/034/035 v2: AUD-C-02/03/11/12 y RG-019 según INT-038, supuestos_resueltos 1.9.7, homologación del 7 pendiente, cabecera sin estado (HF-32).
- **Respuesta:** v2 de los tres insumos con las 7 decisiones de INT-038: cabecera sin estado; 10 supuestos_resueltos (CUR-PT01/02 quedan abiertos); regex literal de spec §2; omitir_si_nulas [P]; "7": null pendiente; fixture con base_registro 1000000100, cur-0004 con el literal de la HU, cur-0016 (RG-019) y cur-0017..0019 (tipos 4, 5, 8), 19 eventos sin claves de duplicado repetidas; contrato v2.0.0 (encabezado del steering 11 §4, «se omite, nunca null», matcher [C] no presente en Motor-Oxigen, RG-019..021). Verificaciones OK: 18 curados, cur-0015 descartado, cur-0004 = " 0001000000101"; verificar-claves rc 0 con las comillas de §2.
- **Decisión:** ART-030, ART-034 y ART-035 v2 prevalidados; re-auditoría con el auditor de Curation.
- **Bitácora:** —

## INT-042 · Auditor independiente (curation) · insumos v2 · `curation_flow`

- **Cuándo:** 2026-11-24T21:22:09-05:00
- **Pedido:** Re-auditar ART-030, ART-034 y ART-035 v2 con el framework 1.9.7.
- **Respuesta:** Los tres insumos v2 aprobables (sin P0/P1). Cerrados en la salida generada AUD-C-01, -04..-10, -13 (cur-0004 = " 0001000000101" también en Scala; tipoDocumentoSisben sin recortar; fixture REGULAR y fileAsOfDate; override sin NPE; fila del 7; edf-curation; verificar-claves §1; 07). Verificaciones OK (pyhocon real; suite 128). P2: AUD-C-14 (framework: resumen.json clasifica la omisión de cur-0016 como por severidad), AUD-C-15 (tres citas de supuestos_resueltos), AUD-C-16 (marcador del matcher).
- **Decisión:** AUD-C-15/16 al analista antes de aprobar; AUD-C-14 → HF-45 (lote final).
- **Bitácora:** —

## INT-043 · Analista (edf-analisis) · insumos de Curation v2.1 · `curation_flow`

- **Cuándo:** 2026-11-24T21:26:58-05:00
- **Pedido:** AUD-C-15 (citas de supuestos_resueltos) y AUD-C-16 (marcador del matcher).
- **Respuesta:** C-CUR-05 vuelve a abierto (la spec no responde la confirmación con Synthesis; RG-019); C-CUR-07 cita APR-009; F-12 cita §9 y §9.4; ART-034 marca la clase del matcher como [A:matcher-colombia]. Verificaciones OK; 9 supuestos_resueltos; quedan abiertos CUR-PT01, CUR-PT02 y C-CUR-05. sha256: spec 506e8b9a…, contrato 074743e6…; fixture sin cambios.
- **Decisión:** Diff verificado por el Orquestador (solo esas líneas). Consulta consolidada al aprobador.
- **Bitácora:** —

## INT-044 · Aprobador simulado SIM-DEV-01 · `curation_flow`

- **Cuándo:** 2026-11-24T21:27:29-05:00
- **Pedido:** Re-aprobar ART-023 (§3d), aprobar ART-030/034/035 y la transición acceptance→curation_flow.
- **Respuesta:** (1) Aprueba ART-023 con hash nuevo (S-ACC-06 deja de existir; AUD-A-19 al backlog). (2a) Aprueba ART-030 (C-CUR-05 pendiente del insumo de Synthesis; HF-45). (2b) Aprueba ART-035. (2c) ART-034 condicional: [A:matcher-colombia] no es el marcador (steering 11 #7 resuelto para SISBEN; la verificación es [PM]); abrir PA-008 bloqueante para pinning; [A:lector-oxygen-pinning] → [A:PA-008]; leyenda [PM] como steering 05:48; HF para 09 §5.1 #2 por etapa_afectada (supuesto) y para la leyenda [PM]. (3) Aprueba la transición acceptance→curation_flow (alto, diferida) en el orden ART-030/035 → PA-008 y ART-034 → transición; habilita solo curation_flow.
- **Decisión:** Registradas la corrección §3d y APR-014 (ART-023), APR-015 (ART-030 y ART-035), PA-008 (BIT-0098..0101). HF-46 corregido en el framework (0516f33); HF-47 al lote final. ART-034 al analista para los marcadores.
- **Bitácora:** BIT-0098..BIT-0101

## INT-045 · Analista (edf-analisis) · ART-034 marcadores · `curation_flow`

- **Cuándo:** 2026-11-24T21:30:34-05:00
- **Pedido:** Marcadores de ART-034 según INT-044 (PA-008).
- **Respuesta:** Solo los cuatro marcadores de INT-044 (matcher [C]·[PM] PA-008; lector [A:PA-008]; leyenda [PM] = pendiente-manual; fila de cambios). verificar-claves rc 0. sha256 6bec3436…
- **Decisión:** Diff verificado por el Orquestador (solo marcadores). APR-016 (ART-034, alto), APR-017 (transición acceptance→curation_flow, alto, diferida); curation_flow en_progreso (BIT-0103..0106).
- **Bitácora:** BIT-0103..BIT-0106

## INT-046 · Generador (edf-curation) · `curation_flow`

- **Cuándo:** 2026-11-24T21:31:07-05:00
- **Pedido:** Generar ART-031..033 desde ART-030/034/035 aprobados con el framework 1.9.7.
- **Respuesta:** Propuesta en propuestas/curation/: ART-031 (processor, CuratedEvent, CurationSpec, AcceptedRecord, Capa A), ART-032 (fragmentos HOCON de curation y lookup, domain dictionary 34 campos), ART-033 (19 eventos, CSV 202, Specs, expected/). Verificaciones OK; reproducible. 8 escenarios HU §5.3 cumplen en expected y en Scala (cur-0004 con espacio); 34 claves; cur-0016; tipos 4/5/8; cur-0014 curado, cur-0015 descartado; contrato ART-034 coincide. Supuestos abiertos CUR-PT01/02, C-CUR-05, C-CUR-06. Posibles defectos: HF-45 presente; prueba de COMPONENTE_FALTANTE solo camino feliz; comentario fijo de severidad 3; cita a runbook inexistente; checklist pide ASSUMPTION en outputEventType; réplica Python vs Scala con NBSP; motivos de advertencia fuera de la lista de spec §10.1; tabla 202 duplicada si el fork comparte oxygen-lookup-inner.
- **Decisión:** ART-031..033 prevalidados; auditor de Curation para la propuesta.
- **Bitácora:** —

## INT-047 · Analista (edf-analisis) · insumo de Pinning · `pinning`

- **Cuándo:** 2026-11-24T21:31:31-05:00
- **Pedido:** Preparar insumos/yaml-skill-pinning.yaml (ART-050) con lo verificable de PA-008 en Motor-Oxigen/.
- **Respuesta:** ART-050 yaml-skill-pinning.yaml (modo Colombia: piezas compartidas, sin matcher ni identifying-attributes propios; atributos standardizedId*; subscriberRelation tipo_número; reusePin false con ubicación [A:PA-009]; salida spec §14). PA-008: (a) resuelta con el motor (PinningIOUtils.scala:23-46 lee las curadas; clave ausente = ""; null lanza; matcher único por Class.forName); (b) ColombiaIdentityMatcher no está en Motor-Oxigen (solo IS/RI/Test y matchers de Brasil); (c) Ñ no verificable. Peligro silencioso: con ISIdentityMatcher por defecto todos los SISBEN caerían en un solo PIN (1.1 ≥ 0.6). Propone PA-009 (reusePin es global de la aplicación). 5 posibles defectos (P1: SKILL.md:34 manda reusePin=false en el bloque de una ingesta nueva en modo Colombia).
- **Decisión:** ART-050 prevalidado; PA-009 bloqueante para pinning; auditor independiente de Pinning.
- **Bitácora:** —

## INT-048 · Auditor independiente (pinning) · `pinning`

- **Cuándo:** 2026-11-24T21:35:47-05:00
- **Pedido:** Auditar ART-050, PA-008/PA-009 y los 5 posibles defectos del framework.
- **Respuesta:** ART-050 AJUSTES (sin P0). P1: AUD-P-01 (reusePin false listo para generar; la orden de esperar PA-009 solo en comentario; SKILL.md:34 manda escribirlo), AUD-P-02 (false contradice HU §5.4 «persona existente recibe ese PIN»: con false se renumera, SparkPinner.scala:979-985), AUD-P-03 (spec: sin bandera de fuente el matcher exige nombre). Peligro silencioso confirmado pero con otra mecánica (AUD-P-04: matcher vacío por defecto falla al arrancar; colapso por identidades iguales antes de comparar). AUD-P-05: EventPinner no se usa en batch (la evidencia es ISIdentityMatcher.scala:46-54). Defectos 1-5 reales (1 P1). PA-008: (a) resuelta con el motor; (b)(c) imposibles sin fork. PA-009: (a) no escribir la clave es la segura; (b) N4; (c) rompe el cruce entre fuentes (y spec §14 apunta a ella, AUD-P-07).
- **Decisión:** Framework (HF-57..61) a un corrector; PA-008/PA-009 al aprobador (PA-009 la cierra el PO).
- **Bitácora:** —

## INT-049 · Auditor independiente (curation) · propuesta · `curation_flow`

- **Cuándo:** 2026-11-24T21:36:45-05:00
- **Pedido:** Auditar ART-031..033 y los posibles defectos del generador.
- **Respuesta:** APROBADO ART-031..033 (N2, sin P0/P1): contrato del motor respetado (outputEventType 30, gancho tras curateEvent sin NPE ni doble curación, homologación §9.4, original sin recorte, omitir_si_nulas, sev. 3 se publica, sin PII); fragmentos aditivos y domain dictionary v2 (C-CUR-06 abierto, lo fija Synthesis); 18 casos del Spec = expected/; 8 escenarios HU §5.3. Reproducible; pyhocon OK; suite 128; verificar-claves OK. Los 8 posibles defectos son reales (P2 framework; el 8 latente): AUD-C-14(=HF-45), -17..-23. Nuevo AUD-C-24 (MANIFEST sin sha256 de los 12 principales).
- **Decisión:** HF-48..55 al lote final; consulta al aprobador: artefactos y etapa curation_flow (diferida), y curation_stock (transición, ART-040, etapa diferida).
- **Bitácora:** —

## INT-050 · Aprobador simulado SIM-DEV-01 · `curation_flow`

- **Cuándo:** 2026-11-24T21:41:25-05:00
- **Pedido:** Aprobar ART-031..033 y la etapa curation_flow con evidencia diferida; decidir curation_stock (transición, ART-040, etapa diferida).
- **Respuesta:** Aprueba ART-031..033 (medio) con hashes por archivo (AUD-C-24), supuesto HF-52 (vale el Scala), C-CUR-05/06 condicionan Synthesis, AUD-C-22 a PA-007, HF-54 al checklist B. Aprueba curation_flow con evidencia diferida (alto, RG nuevo) y tabla de escenarios. curation_stock: transición acceptance→curation_stock (alto, diferida; HF-56), ART-040 por referencia (mismo hash que ART-031) y etapa con evidencia diferida (RG propio); nota para PA-002.
- **Decisión:** Registradas APR-018 (ART-031..033), APR-019 (curation_flow, RG-023), APR-020 (transición a curation_stock), APR-021 (ART-040), APR-022 (curation_stock, RG-024); hashes por archivo en hashes/curation.sha256; antecedentes de PA-002 y PA-007 (BIT-0123).
- **Bitácora:** BIT-0111..BIT-0123

## INT-051 · Corrector del framework (pinning) · `pinning`

- **Cuándo:** 2026-11-24T21:44:59-05:00
- **Pedido:** Corregir HF-57..61 con pruebas; framework 1.9.8.
- **Respuesta:** HF-57..61 corregidos: en modo Colombia la skill no escribe reusePin (SKILL, plantilla del bloque, logica-pinning §8.0, plantilla del insumo con valor_en_fuente / valor_a_escribir texto); advertencia del colapso silencioso; SKILL sin contradicción y evals 1.2.0 con EVAL-PIN-12..14; steering 11 al día; campo modo_colombia.umbral_y_matcher_determinista; tools/verificar-insumo-pinning.py (con el insumo de la corrida da 1 error: reusePin False, AUD-P-01); HF-61 sin citas en .kiro/ (aclarado en steering 11). tests 166; suites de skills en verde; auditar-skills OK.
- **Decisión:** Más HF-62 (references/03 §3e) por el Orquestador; commit d9d7488 (framework 1.9.8). Ronda 2 del insumo de Pinning.
- **Bitácora:** —

## INT-052 · Aprobador simulado SIM-PO-01 / SIM-DEV-01 · `pinning`

- **Cuándo:** 2026-11-24T21:44:59-05:00
- **Pedido:** Decidir PA-009 (reusePin), PA-008 sin fork y los ajustes de ART-050.
- **Respuesta:** (1, SIM-PO-01) PA-009 opción (a): no escribir reusePin; «reusePIN = false» = ingesta sin PIN previos propios; supuesto del PO; PA-010 no bloqueante para la spec; RG-002 reescrito; escalamiento si en el fork el valor compartido es false. (2) PA-008: (a) resuelta; (b)(c) a verificación diferida (riesgo alto) que bloquea fork y certificación de Pinning, Synthesis y State Calculation, no la corrida; ningún artefacto puede depender de (b)(c). (3) AUD-P-03 como riesgo dentro del mismo. (4) Autoriza AUD-P-01, -02, -04..-08; ART-034 no se revisa por §3c (nota en bitácora). Pide un HF para formalizar la «verificación diferida» en references/03.
- **Decisión:** PA-009 y PA-008 resueltas, PA-010 abierta, RG-025 (verificación diferida del matcher), RG-002 y RG-019 actualizados, nota de ART-034 (BIT-0125..0131). HF-62 (formalizar la verificación diferida). Ronda 2 del analista cuando 1.9.8 esté publicado.
- **Bitácora:** BIT-0125..BIT-0131

## INT-053 · Analista (edf-analisis) · insumo de Pinning v2 · `pinning`

- **Cuándo:** 2026-11-24T21:55:07-05:00
- **Pedido:** ART-050 v2 con 1.9.8 y las decisiones de INT-052 (AUD-P-01, -02, -04..-08).
- **Respuesta:** ART-050 v2.0.0: cabecera sin estado; reusePin sin booleano (valor_en_fuente / valor_a_escribir «no se escribe»; decisión del PO, PA-009); claves compartidas «no se redefine»; advertencia del colapso con condición, mecanismo y efecto; evidencia ISIdentityMatcher:46-54 [P] (sin EventPinner); clave ausente [C]·[P]; INPUT_REF_DIR [PM]; ruta Pinned [C] con PA-010; estados al día; hechos del matcher de Colombia [PM] (RG-025), sin [A:*] en valores; campo umbral_y_matcher_determinista. verificar-insumo-pinning OK; verificar-claves OK. sha256 be720540…
- **Decisión:** ART-050 v2 prevalidado; re-auditoría con el auditor de Pinning.
- **Bitácora:** —

## INT-054 · Auditor independiente (pinning) · insumo v2 · `pinning`

- **Cuándo:** 2026-11-24T21:57:48-05:00
- **Pedido:** Re-auditar ART-050 v2 con el framework 1.9.8.
- **Respuesta:** ART-050 v2 APROBADO. Ajustes aplicados y citados; ninguna clave compartida con valor para escribir; condición del §3e cumplida (hechos de RG-025 en [PM], lista y reglas vacías); verificadores OK; el verificador detecta la v1. Nuevos: AUD-P-13 (insumo P2, «persona existente» sin marcador), AUD-P-14 (framework P1: semilla, 04-contratos y prompt 00 siguen exigiendo matcher, identifying-attributes.json y bloque con reusePIN=false como ART-051..053), AUD-P-15 (verificador acepta «false (no se escribe…)» y reusePIN con mayúsculas), AUD-P-16 (plantilla pide [A:*] donde el §3e manda [PM]), AUD-P-17 (preguntas de la etapa pinning desactualizadas).
- **Decisión:** AUD-P-14..16 al corrector (1.9.9) antes de generar Pinning; AUD-P-17 lo corrige el Orquestador; AUD-P-13 para una v3 si hiciera falta.
- **Bitácora:** —

## INT-055 · Corrector del framework (orquestador pinning) · `pinning`

- **Cuándo:** 2026-11-24T22:01:51-05:00
- **Pedido:** Corregir HF-63 (artefactos de pinning por modo en semilla, 04 y prompt 00) y HF-64 (verificador y plantilla).
- **Respuesta:** HF-63: semilla con modo Colombia por defecto, 04-contratos con tabla de dos modos, prompt 00 elige el modo desde la spec/HU (si no lo dicen, pregunta bloqueante), 01-flujo-etapas §5 alineado; forma de ART-051..053 en modo Colombia. HF-64: verificador exige «no se escribe/redefine» al inicio y compara claves sin mayúsculas; plantilla distingue [A:<ID>], [PM] (RG) y [C]. tests 173; suites en verde; auditar-skills OK; insumo de la corrida OK.
- **Decisión:** Commit 43bf3c7 (1.9.9). Expediente: ART-051/052 no obligatorios y ausentes, ART-053 bloque de verificación (BIT-0134). Consulta al aprobador.
- **Bitácora:** —

## INT-056 · Analista (edf-analisis) · insumo de Synthesis (agente nuevo) · `synthesis`

- **Cuándo:** 2026-11-24T22:01:51-05:00
- **Pedido:** Preparar yaml-skill-synthesis.yaml (ART-060) resolviendo C-CUR-05, C-CUR-06 y RG-010.
- **Respuesta:** ART-060 yaml-skill-synthesis.yaml v1.0.0: 34 campos de spec §10.1 con LFP (sin clave handler), sin dataType ni periodicity (defaults), sin synthesizer/section/enabled; name y description = ART-032; consistencyCheck solo lista de comprobación (conf único de Colombia; eventTypeList no filtra); sin reglas CONS/custom ni processors. RG-010 resuelto con spec §12/HU R-25 (frecuencia Mensual, no Unique). C-CUR-05 cerrado (LFP no escribe nulos; mapa vacío por periodo; propone PROP-SYN-1). C-CUR-06 cerrado sin fijar dataType (solo lo usan reglas de consistencia). Verificaciones OK. Riesgo propuesto (diccionario y conf únicos), VD-SYN-01..05 [PM]. Hallazgo para Validation: SISBENFileMetadata no fija minFrequency. 5 posibles defectos del framework.
- **Decisión:** ART-060 prevalidado; PA-011 (PROP-SYN-1, no bloqueante) y RG-026 registrados; auditor independiente de Synthesis (incluye el hallazgo de minFrequency).
- **Bitácora:** —

## INT-057 · Aprobador simulado SIM-DEV-01 · `pinning`

- **Cuándo:** 2026-11-24T22:05:24-05:00
- **Pedido:** Aprobar ART-050 v2 y las transiciones curation_flow/curation_stock → pinning; confirmar el modo Colombia de ART-051..053.
- **Respuesta:** (1) ART-050 condicional: v3 que solo cambie la línea 229 (AUD-P-13: cumplimiento [PM] RG-025), verificada por diferencia; AUD-P-17 (preguntas de la etapa sin PA-008/009); AUD-P-15/16 al lote (ya en 1.9.9). (2) Confirma el catálogo de Pinning en modo Colombia (ART-051/052 no obligatorios, ART-053 solo comentarios VERIFICAR y referencias, prueba con grep/pyhocon y hocon_check; DoD §6 según PA-009 y PA-010). (3) Aprueba dos transiciones (curation_flow → pinning y curation_stock → pinning), alto, diferidas, citando RG-025 y RG-002, después de ART-050 v3.
- **Decisión:** v3 al analista; AUD-P-17 aplicado (preguntas de pinning = PA-010); registro de la corrección HF-63 confirmado; luego ART-050 v3 y las dos transiciones.
- **Bitácora:** —

## INT-058 · Analista (edf-analisis) · ART-050 v3 · `pinning`

- **Cuándo:** 2026-11-24T22:06:47-05:00
- **Pedido:** Solo la línea 229 (AUD-P-13).
- **Respuesta:** v3: línea de «Persona existente» con [C] escenario · [PM] cumplimiento (RG-025); versión 3.0.0. Verificadores OK. sha256 9c548c3e…
- **Decisión:** Diff verificado por el Orquestador. APR-023 (ART-050 v3, alto), APR-024 (curation_flow→pinning), APR-025 (curation_stock→pinning), pinning en_progreso (BIT-0136..0141).
- **Bitácora:** BIT-0136..BIT-0141

## INT-059 · Generador (edf-pinning) · `pinning`

- **Cuándo:** 2026-11-24T22:07:25-05:00
- **Pedido:** Generar ART-053 (modo Colombia) desde ART-050 v3 con el framework 1.9.9.
- **Respuesta:** ART-053 = pinning-application-block.sisben.conf, solo comentarios (8 claves compartidas a verificar, reusePin NO SE ESCRIBE con citas, N4 escalado, colapso silencioso, PA-010, RG-025, [PM] del FQCN e identifying-attributes-new.json). pyhocon y hocon_check OK ({}), grep sin claves activas, EVAL-PIN-12..14 pasan a mano. Posibles defectos: falso positivo del chequeo de groupSSR en verificar-artefacto.ps1:169 (no excluye comentarios); sin chequeos automáticos para EVAL-PIN-12..14; SKILL.md:28 y plantilla :27 nombran ColombiaIdentityMatcher / IdentityImpl; source-spec-curation.yaml:26 cita EventPinner (expediente).
- **Decisión:** ART-053 prevalidado; auditor de Pinning.
- **Bitácora:** —

## INT-060 · Auditor independiente (pinning) · propuesta · `pinning`

- **Cuándo:** 2026-11-24T22:12:02-05:00
- **Pedido:** Auditar ART-053 y los posibles defectos del generador.
- **Respuesta:** ART-053 APROBADO (P2 no bloqueantes): sin claves activas (pyhocon {}; hocon_check OK); claves prohibidas solo en comentarios (el criterio de INT-057 debe decir «fuera de comentarios»); sin {{; RG-025 solo [PM]; EVAL-PIN-12..14 pasan; fiel a la plantilla pero no reproducible mecánicamente. P2: AUD-P-18 (nombres de la lista VERIFICAR que no son la clave del motor: paths.identifyingAttributesFile/domainDictionaryFile, INPUT_REF_DIR constante, clusterSplitter), AUD-P-19 (faltan curatedDir, HBase estático y *-common-application.conf), AUD-P-20 (cabecera genérica falsa y mecanismo sin [P]), AUD-P-21 (sin generador), AUD-P-22 (prevalidación de ART-050), AUD-P-23/24 (verificar-artefacto.ps1), AUD-P-25 (EventPinner en ART-030). Defecto 3 descartado.
- **Decisión:** AUD-P-22 y -25 registrados (BIT-0150); HF-65..67; consulta al aprobador sobre ART-053 y la etapa pinning.
- **Bitácora:** —

## INT-061 · Auditor independiente (synthesis) · `synthesis`

- **Cuándo:** 2026-11-24T22:13:27-05:00
- **Pedido:** Auditar ART-060, C-CUR-05/06, RG-010, minFrequency de Validation y los 5 posibles defectos.
- **Respuesta:** ART-060 AJUSTES (sin P0): valores fieles (34 claves = ART-030/032, sin handler/dataType/synthesizer, version 2, sin claves HOCON activas); afirmaciones del motor confirmadas salvo dos citas P2 (Synthesis toma la última carpeta *-pin-hc; «dataType inválido rompe» es [P]). P1: AUD-S-01 (riesgo N3, no N2: plan de reversión y prueba de no afectación), AUD-S-02 (falta VD-SYN-06: reglas de consistencia, custom y migración de Colombia se aplican a todos los eventos). Framework: defectos 1, 2 y 5 reales P1 (bloque compartido sin modo Colombia en verificador, 04 y semilla; eventTypeList guarda tipos de archivo y la plantilla pre-llena ["sisben"]; verificar-claves sisben da falso verde), 3 y 4 P2; otros P2 (EVAL-SYN-04, README inexistente, reference de nivel 3). minFrequency real P2 (probable mensual por la clase homóloga; confirmar en el fork). C-CUR-05/06 bien cerrados; RG-010 resuelto; PA-011 + AUD-S-08; RG-026 ampliar.
- **Decisión:** HF-68..72 a un corrector (1.9.10); AUD-S-01/02 y los riesgos al aprobador; luego v2 del analista.
- **Bitácora:** —

## INT-062 · Aprobador simulado SIM-DEV-01 · `pinning`

- **Cuándo:** 2026-11-24T22:16:27-05:00
- **Pedido:** Aprobar ART-053 (¿v2 con AUD-P-18/19 primero?) y la etapa pinning con evidencia diferida.
- **Respuesta:** (1) ART-053 condicional: v2 corta con rutas HOCON completas (AUD-P-18), tres VERIFICAR nuevas —curatedDir, HBase estático, *-common-application.conf— citando RG-025 (AUD-P-19), cabecera aclarada y [P] en el mecanismo (AUD-P-20); verificación por diferencia con «0 claves activas fuera de comentarios». (2) Aprueba la etapa pinning con evidencia diferida (alto) después de ART-053 v2, con riesgo nuevo (sugerido RG-026; asignado RG-027 porque RG-026 ya existía), citando RG-025 y RG-002, y tabla de escenarios HU §5.4 y DoD §6.
- **Decisión:** v2 de ART-053 al generador; después, aprobación de ART-053 y de la etapa.
- **Bitácora:** —

## INT-063 · Generador (edf-pinning) · ART-053 v2 · `pinning`

- **Cuándo:** 2026-11-24T22:17:54-05:00
- **Pedido:** AUD-P-18/19/20 en ART-053.
- **Respuesta:** ART-053 v2: cabecera literal + aclaración de modo Colombia, [P] en el mecanismo (AUD-P-20); rutas completas (AUD-P-18; domainDictionaryFile solo como texto en SparkPinner.scala:193; pinnedReferenceDir en oxygen-config-default.conf:355); tres VERIFICAR nuevas (AUD-P-19). pyhocon {}; hocon_check OK; 0 líneas activas; EVAL-PIN-12..14 pasan. sha256 cccd06cf…
- **Decisión:** Diff verificado por el Orquestador. Registradas la aprobación de ART-053 v2, pinning pendiente_evidencia → aprobación de contexto (alto, diferida) → aprobada; RG-027 abierto.
- **Bitácora:** —

## INT-064 · Corrector del framework (synthesis) · `synthesis`

- **Cuándo:** 2026-11-24T22:25:56-05:00
- **Pedido:** Corregir HF-68..72 con pruebas; framework 1.9.10.
- **Respuesta:** HF-68..72 corregidos: modo Colombia en edf-synthesis (SKILL, plantilla con 11 VERIFICAR), verificador con --modo colombia, semilla/04/00 con ART-061/062 por modo; eventTypeList corregido en references, plantillas y evals; verificar-claves <id> sale con 2 sin expediente; GDD v2 alineado y campos_a_generar {name, description, handler}; EVAL-SYN-04 y -13, cita inexistente, reference de nivel 3 superada. tests 210; suites en verde; auditar-skills OK.
- **Decisión:** Commit a7c10f9 (1.9.10). Expediente: ART-061/062 en modo Colombia (BIT-0161). v2 del insumo de Synthesis al analista.
- **Bitácora:** —

## INT-065 · Aprobador simulado SIM-DEV-01 · `synthesis`

- **Cuándo:** 2026-11-24T22:25:56-05:00
- **Pedido:** AUD-S-01 (N3), AUD-S-02 (VD-SYN-06), ajustes P2 del insumo, RG-010, RG-026, PA-011.
- **Respuesta:** (1) ART-060 a N3 con plan de reversión (retirar solo las entradas de SISBEN, hash del GDD antes y después; reprocesar si ya se generaron sintéticos) y prueba de no afectación estática (diff solo agrega, ningún handler existente cambia, JSON v2, --gdd OK, ART-062 sin claves) y dinámica diferida. (2) VD-SYN-06 en un riesgo propio alto de verificación diferida (RG-028) que bloquea fork y certificación de Synthesis y State Calculation. (3) Autoriza AUD-S-03..07, -09..11 (nueva description en ART-061; ART-061 reemplaza las entradas de ART-032 al integrar). (4) RG-010 mitigado al aprobar ART-060 y cerrado al aprobar el insumo de State Calculation; RG-026 redactado; PA-011 ampliada. (5) minFrequency: verificación diferida (VD-SYN-04 en RG-028), no se revisa ART-011.
- **Decisión:** RG-028 abierto, RG-026 redactado, ART-060 a alto, antecedentes en PA-002/010/011 (BIT-0157..0160). v2 del insumo al analista cuando el framework 1.9.10 esté publicado.
- **Bitácora:** —

## INT-066 · Analista (edf-analisis) · insumo de State Calculation (agente nuevo) · `state_calculation`

- **Cuándo:** 2026-11-24T22:26:47-05:00
- **Pedido:** Preparar yaml-skill-statecalc.yaml (ART-070) e investigar en el motor el mecanismo de PA-002.
- **Respuesta:** ART-070: 6 atributos de spec §13 (4 de nivel A tal cual; fechaEncuesta y fechaModificacion de nivel B necesitan alias —aliasEnabled solo en el fork— o CALCULATED con Nashorn [A:OQ-TR-2]: excluidos); ART-071 con los 4 de nivel A; ART-072 con generar false. PA-002: (d) descartada con evidencia (Foundation solo excluye isVisible=false; numberOfPeriods/MOST_RECENT_VALUE por PIN; enricher por defecto nulo; voidIndicator en Synthesis); además hace falta filtrar por fuente; recomienda (c1): máximo fechaCorte de DNP/999722 por corrida, el enricher de Colombia marca isSisbenCorteVigente y la sección filtra con where (código Scala en el fork, [A:CQ-006] y Nashorn [A:OQ-TR-2]). §3e no aplica (los artefactos dependen de hechos del fork). Propone PA-012 (forma de la sección), PA-013 (hechos del fork), R-SC-1/2. 4 posibles defectos del framework.
- **Decisión:** ART-070 prevalidado (crítico); PA-012 y PA-013 bloqueantes; RG-029 y RG-030; auditor independiente de State Calculation; PA-002 al PO simulado tras la auditoría.
- **Bitácora:** —

## INT-067 · Analista (edf-analisis) · insumo de Synthesis v2 · `synthesis`

- **Cuándo:** 2026-11-24T22:37:02-05:00
- **Pedido:** ART-060 v2 con 1.9.10 y las decisiones de INT-065.
- **Respuesta:** ART-060 v2.0.0 con las decisiones de INT-065: modo_colombia (aplica true), N3 con análisis de impacto, plan de reversión y prueba de no afectación; VD-SYN-01..06 en RG-028; 11 claves de consistencyCheck con se_escribe false; ART-061 con 34 {name, description nueva, handler null}; RG-010 mitigado; RG-026 y PA-011 al día. Verificaciones OK (--modo colombia ARTEFACTOS OK). sha256 1097a0fa…
- **Decisión:** ART-060 v2 prevalidado; re-auditoría con el auditor de Synthesis.
- **Bitácora:** —

## INT-068 · Auditor independiente (state_calculation) · `state_calculation`

- **Cuándo:** 2026-11-24T22:38:51-05:00
- **Pedido:** Auditar ART-070, PA-002 (opciones c1/d), PA-012, PA-013 y los 4 posibles defectos.
- **Respuesta:** ART-070 AJUSTES; no aprobable hasta cerrar PA-002/012/013. 7 valores fieles; ART-072 bien retenido; 10 afirmaciones del motor confirmadas, 2 citas a precisar (alias en :166-171; orden de DERIVED irrelevante). Filtro por fuente necesario. P1: AUD-SC-01 (máximo fechaCorte ≠ último archivo aceptado: entregas repetidas del mismo periodo, archivos fuera de orden), AUD-SC-02 (modo delta conserva el XPM anterior; faltan delta, histórico y appenders en PA-013), AUD-SC-03 (una sección con where no puede llamarse «#sisben»: QueryFunctions.scala:75-82), AUD-SC-04 (con groupBy la sección sale [] y no null). PA-002: (d) descartada; (c1) la más segura pero incompleta (requiere cambiar la App o el motor), con 6 condiciones. PA-012/013 bien planteadas y bloqueantes; §3e no aplica y N4 no admite evidencia diferida. ART-071 (4 campos de nivel A) correcto en contenido, pero solo generable si el PO aprueba el insumo limitado. Defectos 1-3 reales P1, 4 P2; nuevo P1: edf-statecalc sin modo Colombia y cita a un README inexistente.
- **Decisión:** HF-73..75 suben a P1, HF-76 confirmado, HF-77 nuevo; corrector 1.9.11; PA-002/012/013 y el alcance de ART-070 al PO simulado.
- **Bitácora:** —

## INT-069 · Auditor independiente (synthesis) · insumo v2 · `synthesis`

- **Cuándo:** 2026-11-24T22:40:57-05:00
- **Pedido:** Re-auditar ART-060 v2 con 1.9.10.
- **Respuesta:** ART-060 v2 APROBADO (sin P0/P1): ajustes de INT-065 aplicados y citados; valores que llegan a ART-061/062 iguales a la v1 salvo la description; nada depende de RG-025/RG-028; ninguna clave para escribir. Verificaciones OK (verificar-claves sisben ahora rc 2). P2: AUD-S-12 (base de la reversión: GDD de Colombia sin ninguna entrada de SISBEN), AUD-S-13 (handler: null literal en el JSON podría romper la lectura del GDD: el verificador lo detecta), AUD-S-14 (datasetFormat [P]; orchestration y datasetFormat del conf por defecto = N4), AUD-S-15 (expediente), AUD-S-16 (cita de minFrequency).
- **Decisión:** AUD-S-15 aplicado (BIT-0167); consulta al aprobador: ART-060 y transición pinning→synthesis, con AUD-S-12/13 como instrucciones de generación.
- **Bitácora:** —

## INT-070 · Aprobador simulado SIM-DEV-01 · `synthesis`

- **Cuándo:** 2026-11-24T22:44:50-05:00
- **Pedido:** Aprobar ART-060 v2 y la transición pinning→synthesis.
- **Respuesta:** (1) ART-060 condicional: v3 con AUD-S-12 (base = GDD de Colombia sin entradas de SISBEN; reversión de las 34 entradas; efecto sobre Curation [PM] en RG-028), AUD-S-13 (nunca handler: null en el JSON [P]; criterio de aprobación de ART-061), AUD-S-14 (datasetFormat [P]; orchestration y datasetFormat del conf por defecto = N4), AUD-S-16 (cita); verificación por diferencia; AUD-S-15 ya aplicado. (2) Aprueba la transición pinning→synthesis (alto, diferida RG-027) después de la v3, citando RG-025, RG-028, RG-026 y RG-010; criterios para aprobar ART-061 y ART-062. Para ART-070 actuará como SIM-PO-01.
- **Decisión:** v3 al analista; después ART-060 v3 y la transición.
- **Bitácora:** —

## INT-071 · Analista (edf-analisis) · insumo de Synthesis v3 · `synthesis`

- **Cuándo:** 2026-11-24T22:46:05-05:00
- **Pedido:** AUD-S-12/13/14/16 en ART-060 (nada más).
- **Respuesta:** v3.0.0 con AUD-S-12 (base sin entradas de SISBEN; reversión de las 34; efecto sobre Curation [PM]), AUD-S-13 (regla_handler_null), AUD-S-14, AUD-S-16. Verificaciones OK; la transcripción literal con handler null da 34 hallazgos. sha256 0b6d46cc…
- **Decisión:** Diff verificado por el Orquestador. Aprobados ART-060 v3 (alto) y la transición pinning→synthesis (alto, diferida); RG-010 mitigado; synthesis en_progreso.
- **Bitácora:** —

## INT-072 · Generador (edf-synthesis) · `synthesis`

- **Cuándo:** 2026-11-24T22:47:05-05:00
- **Pedido:** Generar ART-061 y ART-062 en modo Colombia desde ART-060 v3 con el framework 1.9.10.
- **Respuesta:** ART-061 domaindictionary.json (domain sisben, version 2, 34 {name, description} en orden de spec §10.1, sin handler/dataType/synthesizer), ART-062 bloque solo de comentarios (11 VERIFICAR + nota informativa, AUD-S-14 N4, VD-SYN-01..06 en RG-028, reversión en 6 pasos, prueba de no afectación). --modo colombia OK (en modo propio da los 2 hallazgos esperados); verificar-claves OK; prueba estática sin fork OK (reemplazo de ART-032 solo cambia description). EVAL-SYN-04/13 pasan. Posibles defectos: verificar-artefacto.ps1 sin modo (exige la raíz); precondición desactualizada en el insumo (:85); python vs python3 en SKILL.md:76.
- **Decisión:** ART-061 y ART-062 prevalidados; auditor de Synthesis.
- **Bitácora:** —

## INT-073 · Auditor independiente (synthesis) · propuesta · `synthesis`

- **Cuándo:** 2026-11-24T22:50:40-05:00
- **Pedido:** Auditar ART-061 y ART-062.
- **Respuesta:** APROBADO ART-061 y ART-062 (sin P0/P1): ART-061 sin hallazgos con --modo colombia, sin handler/dataType/synthesizer, 34 entradas en orden, regeneración idéntica byte a byte, prueba estática de no afectación OK (simulación de integración y reversión sobre base sintética); ART-062 sin líneas activas (pyhocon {}), 11 VERIFICAR (8 coinciden con el motor; datasetFormat y fieldHandlerHashFunction como inferencia). P2: AUD-S-17 (cita oxygen-config-default-local.conf mal: es :459-468), AUD-S-18 (faltan shouldFilterDeletedEvents, stateCheckEnabled, disabledCategories, referenceEventsWindowInMonths, emitStockMetrics). Defectos: 1 real P1 (verificar-artefacto.ps1 de Synthesis sin modo), 2 y 3 P2.
- **Decisión:** AUD-S-18 en la mitigación de RG-028 (BIT-0185); HF-78/79 al lote final; consulta al aprobador para ART-061/062 y la etapa.
- **Bitácora:** —

## INT-074 · Corrector del framework (statecalc) · `state_calculation`

- **Cuándo:** 2026-11-24T22:51:12-05:00
- **Pedido:** Corregir HF-73..77 con pruebas; framework 1.9.11.
- **Respuesta:** HF-73..77 corregidos: verificador statecalc con alias, --source-spec, field.X, nombres válidos y --modo colombia; verificar-claves por name y campos Foundation; golden con DERIVED previo; steering 07 condicionado a OQ-TR-2; modo Colombia de edf-statecalc (SKILL, references/modo-colombia.md, plantillas, EVAL-SC-14) y Orquestador (semilla, 04, 00). tests 240; suites en verde; auditar-skills OK; verificar-claves del insumo de la corrida coherente (119 citas).
- **Decisión:** Commit 8db046a (1.9.11). Re-auditoría de ART-070 v2 con los verificadores corregidos.
- **Bitácora:** —

## INT-075 · PO simulado SIM-PO-01 · `state_calculation`

- **Cuándo:** 2026-11-24T22:51:12-05:00
- **Pedido:** Decidir PA-002, PA-012, alcance de ART-070 (CQ-006) y PA-013.
- **Respuesta:** (SIM-PO-01) No aprueba nada de State Calculation (opción iii): la etapa queda bloqueada al cierre. PA-002: dirección (c1-a) con filtro por fuente en el where y corte vigente en el enricher; no se cierra (condiciones 1-6); CQ-006 en el alcance como entrega manual de Ingesta 3 (backlog), sin regla de corte; supuesto «vigente = mayor periodo aceptado» y control operativo de re-entrega; abre PA-014 (negocio, bloqueante para la certificación). PA-012 resuelta (sisben, un objeto, null = sin sección; supuestos del PO). PA-013 sigue abierta y se amplía. RG-007 aceptado; RG-011 abierto (producción); RG-003 abierto. Escalamiento N4 registrado.
- **Decisión:** APR-030 (rechazo de ART-070, PO), PA-012 resuelta, PA-014 abierta, RG-003/007/011 actualizados, RG-031 (dos documentos en un PIN), BKL-001..003, PA-013 ampliada, escalamiento BIT-0184 (BIT-0177..0184). v2 de ART-070 al analista (solo prevalidado).
- **Bitácora:** —

## INT-076 · Aprobador simulado SIM-DEV-01 · `synthesis`

- **Cuándo:** 2026-11-24T22:54:31-05:00
- **Pedido:** Aprobar ART-061 y ART-062 y la etapa synthesis con evidencia diferida.
- **Respuesta:** Aprueba ART-061 y ART-062 sin v2 (AUD-S-17 comentario; AUD-S-18 cubierto por RG-028 al que remite el bloque), con condiciones (VD-SYN-02 con shouldReportAllDeletedEvents y stateNames; shouldFilterDeletedEvents ↔ inferDelete y [A:PT-12]; HF-78 antes de usar el .ps1). Aprueba la etapa synthesis con evidencia diferida (alto, RG nuevo), con la tabla de HU §5.5 y DoD §6; no se registra la transición a state_calculation. Pide corregir las preguntas de la etapa state_calculation.
- **Decisión:** APR-031 (ART-061/062), RG-028 ampliado, synthesis pendiente_evidencia → APR-032 (diferida) → aprobada, RG-032, preguntas de state_calculation corregidas (BIT-0187..0193).
- **Bitácora:** —

## INT-077 · Analista (edf-analisis) · insumo de State Calculation v2 · `state_calculation`

- **Cuándo:** 2026-11-24T22:54:31-05:00
- **Pedido:** ART-070 v2 con las decisiones de INT-075 y AUD-SC-01..13 (quedará prevalidado, sin aprobación).
- **Respuesta:** ART-070 v2.0.0: PA-002 (c1-a) con filtros separados (fuente en el where con campos inyectados; corte vigente en el enricher + cambio de App/motor, CQ-006/BKL-001), supuesto de vigencia y BKL-003, seis condiciones; sección propuesta fuera de la instrucción de generación (AUD-SC-13); PA-012 aplicada; PA-013/014; RG-007 aceptado (SC-8), RG-011 (BKL-002), RG-003/025/028 como precondiciones; escenarios SC-7, SC-8, SC-9 y SC-5 ampliado; citas corregidas; ART-071 con 4 campos (no se genera por APR-030); ART-072/073 generar false. Parse y verificar-claves OK. sha256 e6ca460b…
- **Decisión:** ART-070 v2 prevalidado (sin aprobación, APR-030); re-auditoría con el framework 1.9.11.
- **Bitácora:** —

## INT-078 · Corrector del framework (lote final) · `state_calculation`

- **Cuándo:** 2026-11-24T22:59:11-05:00
- **Pedido:** Corregir HF-13, 42, 44, 45, 47..55, 65..67, 78, 79 (HF-56 solo propuesta).
- **Respuesta:** (pendiente)
- **Decisión:** (pendiente)
- **Bitácora:** —

## INT-079 · Auditor independiente (state_calculation) · insumo v2 · `state_calculation`

- **Cuándo:** 2026-11-24T23:06:59-05:00
- **Pedido:** Re-auditar ART-070 v2 con los verificadores de 1.9.11.
- **Respuesta:** (pendiente)
- **Decisión:** (pendiente)
- **Bitácora:** —
