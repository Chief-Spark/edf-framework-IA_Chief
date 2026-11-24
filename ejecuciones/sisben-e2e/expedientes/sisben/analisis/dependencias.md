# Dependencias — SISBEN IV

| Campo | Valor |
|---|---|
| Ingesta | `sisben` |
| Versión / Fecha | 1.1.0 / 2026-11-24 (ronda 2: ajustes AUD-E0-03, -05) |
| Estado | PROPUESTO |
| Fuentes | `fuentes/hu-sisben.md` 1.0.2; `fuentes/spec-sisben.md` 1.0.2; steering `00`, `03`, `06`, `07`; `contexto-ingesta.md` |
| Skill | `edf-analisis` (modo etapa 0, tipo T1) |

## Objetivo y alcance

Lo que la ingesta necesita de fuera de sí misma para recorrer las seis etapas: catálogos, configuración, ambiente, personas y decisiones, y lo que pasa de una etapa a otra. Dueño nominal solo donde una fuente lo nombra; si no, el rol y `[PM]`.

**No cubre:** la ejecución ni el despliegue, que son humanos y fuera del framework (`01` §2.2).

## 1. Dependencias de datos y catálogos

| # | Dependencia | Tipo (catálogo / tabla / lookup / otra fuente) | Etapa que la necesita | Dueño | Estado | Bloquea |
|---|---|---|---|---|---|:-:|
| D-01 | `102_DANE_DIVIPOLA_STATES_CODES_NH` (`cod_dpto;nom_dpto`, llave `cod_dpto`). Pruebas: 6 filas de la spec. Producción: 33 departamentos desde Collibra a S3 | catálogo | `validation` | Gobierno de datos de Experian (Collibra) — nominal `[PM]` | `[C]` estructura y filas de prueba · `[PM]` carga de producción | No |
| D-02 | `2_DANE_DIVIPOLA_CITIES_CODES_NH` (`cod_mpio;nom_mpio;cod_dpto`, llave `cod_mpio`). Pruebas: 6 filas. Producción: todos los municipios DANE | catálogo | `validation` | Gobierno de datos de Experian (Collibra) — nominal `[PM]` | `[C]` estructura y filas de prueba · `[PM]` carga de producción | No |
| D-03 | `201_SISBEN_ZONES_NH` (`cod_clase;descripcion`); contenido completo en la spec | catálogo | `validation` | Gobierno de datos de Experian (Collibra) — nominal `[PM]` | `[C]` | No |
| D-04 | `202_SISBEN_PROCESS_ID_TYPE_HO` (`cod_sisben;cod_experian;descripcion`); contenido completo. Validation lo usa como dominio; Curation, para homologar a `cod_experian` | catálogo y homologación | `validation`, `curation_flow`, `curation_stock` | Gobierno de datos de Experian (Collibra) — nominal `[PM]` | `[C]` | No |
| D-05 | `234_SISBEN_GROUP_SUBGROUP_NH` (`grupo;nivel_max;descripcion`); contenido completo | catálogo | `validation` | Gobierno de datos de Experian (Collibra) — nominal `[PM]` | `[C]` | No |
| D-06 | `203_SISBEN_RECORD_STATUS_AND_MARK_NH` (`codigo;tipo;descripcion`); contenido completo; lo comparten `marca` y `Estado` | catálogo | `validation` | Gobierno de datos de Experian (Collibra) — nominal `[PM]` | `[C]` | No |
| D-07 | Tipo documental Experian para el salvoconducto de refugiado (`cod_sisben 7`, hoy sin `cod_experian`). Mientras no exista, tipo `7` → `SISBEN_ERR_023` | otra fuente (catálogo de Experian) | `validation`, `curation_flow` | Experian (`06` §6, riesgo 5) | `[A:OQ-TR-2b]` creación · `[C]` tratamiento mientras tanto | No |
| D-08 | Consulta binaria a Registraduría (`BDIIREGI`) y proceso nocturno de compra RNEC para cédulas sin coincidencia | lookup y proceso existente | `pinning` | Experian — nominal `[PM]` | `[C]` | No |
| D-09 | Matcher compartido `ColombiaIdentityMatcher` (sin matcher propio ni bandera de fuente), con el umbral NearNeighborKeys vigente | clase compartida existente | `pinning` | Ingesta 3 (dueño del patrón, `06` §6) | `[C]` uso sin cambios | No |
| D-10 | Configuración estándar de Colombia para NAMES, BEST NAME, IDENTIFICATIONS y BEST IDENTIFICATIONS, sin cambios | configuración existente | `state_calculation` | Ingesta 3 — nominal `[PM]` | `[C]` | No |
| D-11 | Reporting estándar de EDF (Experian Data Framework): `Log_Sisben.*` y los `RPT_SISBEN_*` de la spec §14. Las skills no los generan | otra fuente (componente de plataforma) | transversal | Equipo de plataforma EDF — nominal `[PM]` | `[C]` alcance · riesgo RG-004 | No |

## 2. Dependencias de configuración y ambiente

| # | Dependencia (clave HOCON, feature, ruta S3, JAR) | Etapa | Dueño | Estado | Bloquea |
|---|---|---|---|---|:-:|
| C-01 | Recepción (Secure Transport) agrega `.yyyyMMddHHmmss±ZZZZ` al nombre antes de Validation | `validation` | Responsable de recepción — nominal `[PM]` | `[C]` regla (`hu-sisben.md` R-01) · `[PM]` verificación en ambiente | No |
| C-02 | Preparation entrega el archivo con delimitador `;` y UTF-8 a `s3://colombia-data/sisben/preparation/prepared/` | `validation` | Responsable de Preparation (`06` §6, riesgo 10) | `[C]` contrato (`spec-sisben.md` §2, §14) · `[PM]` verificación del delimitador entregado (`07` §4) | No |
| C-03 | Rutas S3 de solo lectura de la spec §14 (validados, aceptados, rechazados, curados `flow/` y `stock/`, pinned, sintetizados, XPM, logs) | todas | Operación / Cloud Engineering (Z3, `01` §2) | `[C]` | No |
| C-04 | Bloque aditivo en el `*-inner.conf` de Validation (`filePattern`, `validate[]`, `deDupVersion = EXTN`, `computeMetrics`) y registro de los 6 lookups. El `*-common-application.conf` no se toca (`01` P10) | `validation` | Desarrollador (transcribe al fork) | `[C]` (`spec-sisben.md` §2, §6; Anexo A) | No |
| C-05 | Ambiente de Validation: `defaultOutputStructure = true`, `historicAttributes = false`, `lambda = batch` (Anexo A, bloque `ambiente`). El generador lo deja en el manifiesto para verificarlo, no lo escribe | `validation` | Operación / ingeniero de datos | `[C]` valores de la spec · verificación en ambiente `[PM]` | No |
| C-06 | Umbral `qualityThreshold = 0.90` en configuración, parametrizable | `acceptance` | Desarrollador | `[C]` (`hu-sisben.md` R-22; `01` P4) | No |
| C-07 | Sección nueva de SISBEN en el domain dictionary (`colombia-domain-dictionary-inner.json`), sin handlers propios | `curation_flow`, `synthesis` | Desarrollador | `[C]` (`spec-sisben.md` §12; `07` §6) | No |
| C-08 | `reusePIN = false` en la configuración de Pinning | `pinning` | Desarrollador | `[C]` (`hu-sisben.md` §3, R-24) | No |
| C-09 | Sección nueva de la familia `foundation-stage.json` para `#sisben` (aditiva) | `state_calculation` | Desarrollador; aprueba el PO (N4) | `[C]` aditividad (`07` §6) · `[A:PA-002]` filtro del corte vigente | Sí (solo `state_calculation`) |
| C-10 | Miembros adicionales de `FileMetadata` para la carga completa (`archivo.metadata`). **No aplica sin versión nueva de la spec**: PA-001 resuelta descartó la alternativa (a); el Anexo A no cambia | `validation` | — | No aplica sin versión nueva de la spec (PA-001 resuelta) | No |
| C-11 | Fork `bureau.colombia_innersource` y JAR de Oxygen para compilar y probar lo generado. El framework no compila (F-01 de `edf-validation`) | todas | Desarrollador / ingeniero de datos | `[C]` (`.kiro/skills/edf-validation/SKILL.md` §Regla de ejecución) | No |
| C-12 | DAGs existentes `daily-validation`, `acceptance-curation-flow`, `collection-stock`, `state-generation`; no se modifican (`01` X5) | todas | Operación | `[C]` nombres (expediente, `etapas[].dag_airflow`) · horario de `collection-stock` `[PM]` (`03` §9 #2) | No |

## 3. Dependencias de personas y decisiones

| # | Decisión pendiente | Quién decide (`06` §4) | Fecha límite | Pregunta abierta |
|---|---|---|---|---|
| P-01 | Cómo se determina el corte vigente de SISBEN y cómo se excluye `#sisben` de los PIN cuyo último evento no pertenece a ese corte (R-26, dentro de State Calculation) | Decide el Desarrollador con el dueño del dominio (Ingesta 3); aprueba el PO o Aprobador designado (N4) | Antes de aprobar `yaml-skill-statecalc.yaml` (bloqueante solo para `state_calculation`) | PA-002 |
| P-02 | Aprobación de la etapa 0 | Desarrollador; PO para el cierre formal. En esta corrida, aprobador simulado | Sin pregunta abierta que la bloquee (PA-001 resuelta; PA-002 solo bloquea `state_calculation`) | — |
| P-03 | Aprobación de los artefactos de State Calculation (N4) | PO o Aprobador designado | Antes de producción | — |
| P-04 | Base legal del envío al DNP de reportes con tipo y número de documento | PO; DevSecOps si corresponde (`06` §5) | Antes de certificar los reportes de Validation y Acceptance | — (riesgo RG-004) |
| P-05 | Creación del tipo documental del salvoconducto en Experian | Experian | Sin fecha en las fuentes `[PM]` | `[A:OQ-TR-2b]` (registro global) |
| P-06 | Aprobador designado del patrón generado | PO | 2026-11-19 (`06` §7), vencida | `[A:OQ-06]` (registro global) |

## 4. Dependencias entre etapas

Orden fijo del framework (`00-framework-edf.md` §2). Aquí solo lo específico de SISBEN.

| # | De | A | Qué se pasa | Estado |
|---|---|---|---|---|
| E-01 | `validation` | `acceptance` | Códigos `SISBEN_ERR_*` (sev. 1) y `SISBEN_INC_*` (sev. 3), `qs.RQS`, marca `OxygenDuplicate`, marcadores `fileName` y `header`; regla de registro y umbral 0.90 (Anexo A, `handoff_acceptance`) | `[C]` |
| E-02 | `validation` | `curation_flow` | Los códigos de sev. 3 por campo: Curation publica el campo igual y anota la advertencia si no puede transformarlo | `[C]` (`spec-sisben.md` §10.4) |
| E-03 | `validation` | `curation_flow` | `subscriberRelation = NUM_DOCUMENTO`, que Curation reemplaza por `<standardizedIdType>_<standardizedIdNumber>`; `asOfDate` = `FECHA_ACTUALIZACION_CNS` | `[C]` (`spec-sisben.md` §3 guard-rails, §10.1) |
| E-04 | `validation` | `curation_flow` | Catálogo 202 compartido: dominio en Validation, homologación en Curation | `[C]` |
| E-05 | `validation` (metadatos) | `synthesis`, `state_calculation` | Marca de carga completa en `FileMetadata`. **No aplica**: PA-001 resuelta descartó la alternativa (a) sin versión nueva de la spec | No aplica |
| E-06 | `curation_flow` / `curation_stock` | `pinning` | `standardizedIdType`, `standardizedIdNumber`, `standardizedIdName` con los nombres literales de `spec-sisben.md` §10.1 (`11` §6 #5) | `[C]` |
| E-07 | `curation_flow` / `curation_stock` | `state_calculation` | Campos curados que consume la sección `#sisben`: `grupoSisben`, `nivelSisben`, `fechaDigitacion`, `fechaActualizacionCns`, `codDepartamentoSisben`, `codMunicipioSisben`. El mapeo al XPM se hace en el insumo de State Calculation | `[C]` (`spec-sisben.md` §13) |
| E-08 | `pinning` | `synthesis` | Eventos con PIN; la recencia (LFP) se decide con `asOfDate` | `[C]` (`spec-sisben.md` §12) |

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Dueños nominales de catálogos, recepción, Preparation y reporting | `[PM]` |
| 2 | Configuración que depende del mecanismo de R-26 dentro de State Calculation (C-09) | `[A:PA-002]` |

## Pie de trazabilidad

- Catálogos: `spec-sisben.md` §9. Rutas: §14. Identidad: §11. Synthesis: §12. Sección `#sisben`: §13. Anexo A: bloques `ambiente`, `handoff_acceptance`, `catalogos`.
- Reglas: `hu-sisben.md` R-01, R-17, R-22, R-24, R-26, R-27.
- Matriz de aprobadores y escalamiento: `06` §4, §5, §6. Ubicación aditiva: `07` §6. DAGs: `estado-ingesta.json` (`etapas[].dag_airflow`).
- Última verificación de referencias: 2026-11-24.
