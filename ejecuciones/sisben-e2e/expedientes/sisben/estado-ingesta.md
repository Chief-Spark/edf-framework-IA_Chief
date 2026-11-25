# Estado de la ingesta — SISBEN IV (`sisben`)

> Vista regenerada desde `estado-ingesta.json` el 2026-11-24T21:18:22-05:00. No se edita a mano: la fuente de verdad es el JSON.
> **Corrida autónoma: todas las aprobaciones son SIMULADAS** (SIM-DEV-01, SIM-PO-01).

| Campo | Valor |
|---|---|
| Etapa actual | `acceptance` |
| Fuente oficial | mixto |
| reusePIN | `false` |
| Business Light Code | batch |
| Bitácora | 95 eventos, último BIT-0095 |

## Etapas

| # | Etapa | Estado | Riesgo | Artefactos aprobados | Aprobación | Transición |
|---|---|---|---|---|---|---|
| 0 | Analisis de Requerimiento y Contexto | `aprobada` | bajo | 4/4 | APR-006 | ✅ |
| 1 | Validation | `aprobada` | alto | 6/6 | APR-011 | ✅ |
| 2 | Acceptance | `aprobada` | alto | 3/4 | APR-013 | 🔒 |
| 3 | Curation - Flow | `no_iniciada` | medio | 0/6 | — | 🔒 |
| 4 | Curation - Stock | `no_iniciada` | medio | 0/1 | — | 🔒 |
| 5 | Pinning | `no_iniciada` | alto | 0/4 | — | 🔒 |
| 6 | Synthesis / Consistency | `no_iniciada` | alto | 0/3 | — | 🔒 |
| 7 | State Calculation | `no_iniciada` | critico | 0/4 | — | 🔒 |

## Artefactos

| Etapa | ID | Nombre | Estado | Hash |
|---|---|---|---|---|
| analisis | ART-001 | contexto-ingesta.md | `aprobado` | sha256:c2b8b52c4a4b |
| analisis | ART-002 | mapeo-campos.md (todos los campos) | `aprobado` | sha256:edbbebb7c179 |
| analisis | ART-003 | dependencias.md | `aprobado` | sha256:d4c111cc090d |
| analisis | ART-004 | plan-de-ruta.md | `aprobado` | sha256:12aa223e4598 |
| validation | ART-010 | insumo: source-spec-validation.yaml (edf-analisis) | `aprobado` | sha256:dd707688bc92 |
| validation | ART-011 | SISBENValidationProcessor.scala + Spec, ValidatedEvent, FileMetadata | `aprobado` | sha256:16dc1080831f |
| validation | ART-012 | Capa A: FieldSpec, FieldValidators, FieldValidatorsSpec | `aprobado` | sha256:a44e4dec6df1 |
| validation | ART-013 | SISBENValidationProcessorSpec + fixture y catalogos sinteticos + codigos-error-<fuente>.csv | `aprobado` | sha256:e7990c96146f |
| validation | ART-014 | HOCON aditivo: validation-configs-<fuente>.conf, lookups-<fuente>.conf | `aprobado` | sha256:7f80dd2717c3 |
| validation | ART-015 | manifest.json | `aprobado` | sha256:735a2624cf47 |
| acceptance | ART-020 | insumo: source-spec-acceptance.yaml (edf-analisis) | `aprobado` | sha256:d8ac9e7d2422 |
| acceptance | ART-021 | bloque HOCON de la ingesta (fileActions, recordAcceptance; umbral 0.90 parametrizable) | `aprobado` | sha256:957f935d18a5 |
| acceptance | ART-022 | condiciones con nombre (fileConditions[]) | `aprobado` | sha256:520b19dc9bfd |
| acceptance | ART-023 | AcceptanceExpressionsSpec + fixture de eventos validados sinteticos | `prevalidado` | sha256:f9b879a8002f |
| curation_flow | ART-030 | insumo: source-spec-curation.yaml (edf-analisis) | `prevalidado` | — |
| curation_flow | ART-031 | SISBENCurationProcessor + CuratedEvent + CurationSpec + lector del evento aceptado | `ausente` | — |
| curation_flow | ART-032 | bloque HOCON (filePattern + processor), lookups, entradas del domain dictionary | `ausente` | — |
| curation_flow | ART-033 | fixture sintetico y tests de curation | `ausente` | — |
| curation_flow | ART-034 | contrato Curation -> Pinning de la ingesta (steering 11 §4) | `prevalidado` | — |
| curation_flow | ART-035 | insumo: fixture-curation/ (accepted-events.input.yaml + lookups/) | `prevalidado` | — |
| curation_stock | ART-040 | mismo processor de curation_flow corriendo en runMode stock (evidencia del DAG collection-stock) | `ausente` | — |
| pinning | ART-050 | insumo: yaml-skill-pinning.yaml (edf-analisis) | `ausente` | — |
| pinning | ART-051 | SISBENIdentityMatcher.scala (o matcher compartido) (con verificacion aritmetica del umbral) | `ausente` | — |
| pinning | ART-052 | identifying-attributes.json | `ausente` | — |
| pinning | ART-053 | bloque HOCON de pinning-application.conf con reusePIN = false (ingesta nueva) | `ausente` | — |
| synthesis | ART-060 | insumo: yaml-skill-synthesis.yaml (edf-analisis) | `ausente` | — |
| synthesis | ART-061 | GDD domaindictionary.json (campo -> handler LFP/LAP/EUC, solo confirmados) | `ausente` | — |
| synthesis | ART-062 | bloque HOCON oxygen.stage.consistencyCheck | `ausente` | — |
| state_calculation | ART-070 | insumo: yaml-skill-statecalc.yaml (edf-analisis) | `ausente` | — |
| state_calculation | ART-071 | fields[] de statecalc-foundation-local.json | `ausente` | — |
| state_calculation | ART-072 | CalculationSection de statecalc-calculation-local.json | `ausente` | — |
| state_calculation | ART-073 | CA1: Data Provisioning consulta la seccion de la ingesta en el XPM | `ausente` | — |

## Aprobaciones

| ID | Tipo | Desde → hacia | Decisión | Actor | Riesgo | Bitácora |
|---|---|---|---|---|---|---|
| APR-001 | artefacto | — → — | aprobada | Desarrollador `SIM-DEV-01` | bajo | BIT-0020 |
| APR-002 | contexto | — → — | aprobada | Desarrollador `SIM-DEV-01` | bajo | BIT-0021 |
| APR-003 | artefacto | — → — | aprobada | Desarrollador `SIM-DEV-01` | medio | BIT-0023 |
| APR-004 | transicion | analisis → validation | aprobada | Desarrollador `SIM-DEV-01` | medio | BIT-0024 |
| APR-005 | artefacto | — → — | aprobada | Desarrollador `SIM-DEV-01` | medio | BIT-0047 |
| APR-006 | transicion | analisis → validation | aprobada | Desarrollador `SIM-DEV-01` | medio | BIT-0048 |
| APR-007 | artefacto | — → — | aprobada | Desarrollador `SIM-DEV-01` | medio | BIT-0054 |
| APR-008 | contexto | — → — | aprobada | Desarrollador `SIM-DEV-01` | alto | BIT-0057 |
| APR-009 | artefacto | — → — | aprobada | Desarrollador `SIM-DEV-01` | medio | BIT-0071 |
| APR-010 | artefacto | — → — | aprobada | Desarrollador `SIM-DEV-01` | medio | BIT-0073 |
| APR-011 | transicion | validation → acceptance | aprobada | Desarrollador `SIM-DEV-01` | alto | BIT-0074 |
| APR-012 | artefacto | — → — | aprobada | Desarrollador `SIM-DEV-01` | medio | BIT-0084 |
| APR-013 | contexto | — → — | aprobada | Desarrollador `SIM-DEV-01` | alto | BIT-0086 |

## Preguntas abiertas

- PA-001 (resuelta, bloqueante): ¿Con qué mecanismo se cumple R-26 / HU §5.6 (la sección #sisben solo para los PIN del archivo vigente)? Opciones: (a) archivo.metadata fileLoadType en Validation; (b) inferDelete en Acceptance; (c) lógica en el enricher de State Calculation; (d) el motor ya lo resuelve.
- PA-002 (abierta, bloqueante): En State Calculation (statecalc-foundation/enricher), ¿cómo se determina el corte vigente de SISBEN y cómo se excluye la sección #sisben de los PIN cuyo último evento SISBEN no pertenece a ese corte (HU R-26, §5.6)? Opciones compatibles: (c1) filtro en el enricher comparando fechaCorte curado (spec §10.1) con el corte vigente (máximo fechaCorte o corte del último archivo aceptado; toca [A:CQ-006]); (d) el motor ya lo cumple, solo con evidencia de código o pipeline. Fuera de alcance sin nueva versión de HU/spec: cambios al Anexo A, a Acceptance o a Synthesis.
- PA-003 (abierta, no bloqueante): ¿Se amplía el filePattern de spec §2/Anexo A (p. ej. .*SISBEN_EXPERIAN_.*\.(txt|TXT)) para que validateFilename rechace con SISBEN_ERR_024 el archivo sin marca de tiempo, o se ajusta el escenario de HU §5.1 a «no se procesa y queda registrado 'No validation configuration found'»?
- PA-004 (abierta, no bloqueante): ¿Se excluyen del agrupamiento de duplicados los registros con error sev. 1 (incluida la fila de 23 columnas con ERR_026), o no pueden ser los que se conservan? Hoy (spec §6, HU R-19) gana el de mayor fec_actualizacion_cns aunque tenga error.
- PA-005 (abierta, no bloqueante): ¿De dónde obtiene el reporting estándar el contador REGISTROS MENORES DE EDAD (HU §5.1, spec §5 RF-02) si el filtro de Validation no deja contador (SparkValidator.scala:1128-1130)?
- PA-006 (resuelta, bloqueante): ¿Qué valores usa Acceptance donde la HU y la spec callan? (a) nombre de la condición y reasonCode del rechazo por FQS (propuesta de la skill: RQS_BAJO_UMBRAL_SISBEN / SISBFA00001); (b) archivo sin registros (propuesta: aceptar_cuerpo_vacio false, rechazo automático); (c) customReasonForAutomaticRejectionEnabled (propuesta: true).
- PA-007 (abierta, no bloqueante): Para la próxima versión de la spec: (1) ¿§10.1 debe decir «ausente (clave omitida) si todos faltan» en vez de «nulo» para standardizedIdName (un null rompe EventPinner)? (2) ¿El tipo «Ninguna» → texto debe publicar el valor literal sin recortar bordes? (hoy se recorta: supuesto del aprobador simulado).

## Riesgos

| ID | Nivel | Etapa | Estado | Descripción |
|---|---|---|---|---|
| RG-001 | alto | analisis | abierto | El analisis produce un mapeo incompleto por specs ambiguas; el contexto defectuoso se arrastra a las etapas siguientes. |
| RG-002 | alto | pinning | abierto | reusePIN mal declarado en una ingesta nueva corrompe la asignacion de PIN. |
| RG-003 | critico | state_calculation | abierto | R-26 sin mecanismo definido: un PIN ausente del archivo vigente podría conservar su sección #sisben (impacto N4). |
| RG-004 | medio | validation | abierto | Los reportes de ingesta enviados al DNP llevan tipo y número de documento (PII). |
| RG-005 | bajo | curation_flow | abierto | La precondición 'Ruíz + INC_011' del escenario de HU §5.3 no se puede producir desde Validation (Ruíz es válido). |
| RG-006 | medio | validation | abierto | La compilación y las pruebas Scala no las certifica el framework (F-01). |
| RG-007 | medio | curation_flow | abierto | Una Fec_digitacion futura con formato válido es inconsistencia sev. 3 pero se transforma: Curation la publica como epoch y llega a #sisben como fecha de encuesta futura (spec §10.4 regla 1, §13) |
| RG-008 | medio | curation_flow | abierto | ide_hogar e ide_ficha_origen son identificadores públicos (R-28, spec §10.1) que podrían enlazarse con una persona (re-identificación indirecta) |
| RG-009 | medio | validation | abierto | Adultos con tipo 7 (salvoconducto) se rechazan hasta que Experian cree el tipo (R-17; 07 §5.7 [A:OQ-TR-2b]) |
| RG-010 | medio | synthesis | abierto | La reference curation-pinning-sisben.md §6/§8 dice que SISBEN va sin Synthesis y periodicidad Unique, contra HU R-25 y spec §12 |
| RG-011 | alto | state_calculation | abierto | La certificación de 4 dimensiones antes de producción (07 §5.7) no figura en la DoD de la HU §6 y no hay motor que la calcule |
| RG-012 | medio | validation | abierto | Un archivo sin marca de tiempo no coincide con el filePattern: no deja marcador ni reporte de rechazo ERR_024 (HU §5.1 cumplido en parte) |
| RG-013 | medio | validation | abierto | En duplicados gana el de mayor fec_actualizacion_cns aunque tenga error sev. 1; el registro válido queda sin aceptar (dato incompleto) |
| RG-014 | medio | validation | abierto | El contador REGISTROS MENORES DE EDAD del Log_Sisben no tiene origen: el filtro de Validation no deja rastro |
| RG-016 | medio | curation_flow | mitigado | Con un archivo CRLF, FECHA_ACTUALIZACION_CNS llega a Curation con \r final (Campos.crudo, AUD-V-29) y alimenta fechaModificacion de #sisben (spec §13); la spec no fija el fin de línea |
| RG-015 | alto | validation | abierto | Evidencia diferida de validation: aprobada sin ejecución de pipeline (observada null) |
| RG-017 | medio | acceptance | abierto | Las fileConditions son globales (SparkAcceptor.scala:46): si RQS_BAJO_UMBRAL_SISBEN o SISBFA00001 ya existen en el fork, la decisión cambiaría sin avisar (06 §6 #9) |
| RG-018 | medio | acceptance | abierto | Un archivo sin RQS queda pendiente en validated/ sin otra señal que un error en el log (AUD-A-12, motor) |
| RG-019 | bajo | curation_flow | aceptado | Las claves standardizedId* se omiten cuando son nulas (omitir_si_nulas, steering 11 §3) en lugar de salir nulas como dice la letra de spec §10.1 para standardizedIdName |
| RG-020 | alto | pinning | abierto | standardizedIdType lleva el código Experian (spec §10.1). Si otras fuentes de Colombia publican otro juego de códigos (p. ej. CC) o si ColombiaIdentityMatcher no compara por toString, la prioridad 1 de R-24 no cruza la misma persona entre fuentes |
| RG-021 | medio | pinning | abierto | La Ñ se conserva en standardizedIdName (spec §10.2) y otras fuentes podrían transliterarla, lo que afecta el cruce por nombre |
| RG-022 | alto | acceptance | abierto | Evidencia diferida de acceptance: aprobada sin ejecución de pipeline (observada null) |
