# Plan de ruta — SISBEN IV

| Campo | Valor |
|---|---|
| Ingesta | `sisben` |
| Versión / Fecha | 1.1.0 / 2026-11-24 (ronda 2: ajustes AUD-E0-03, -05) |
| Estado | PROPUESTO |
| Fuentes | `fuentes/hu-sisben.md` 1.0.2 §6 (definición de terminado); `fuentes/spec-sisben.md` 1.0.2; steering `00` §2, `06` §2; `contexto-ingesta.md`; `dependencias.md` |
| Skill | `edf-analisis` (modo etapa 0, tipo T1) |

## Objetivo y alcance

La ruta de SISBEN por las seis etapas EDF (Experian Data Framework): qué insumo prepara `edf-analisis` para cada una, qué riesgo se espera, qué preguntas abiertas la afectan y en qué orden deben cerrarse. Data Reception y Preparation no son etapas del framework (`00` §2).

Riesgo según `06` §2; la clasificación final la confirma el Desarrollador.

## 1. Ruta por etapa

| # | Etapa | Insumo que producirá `edf-analisis` | Skill que genera | Riesgo esperado (`06`) | Preguntas abiertas que la afectan | ¿Candidata a regla de corte? |
|---|---|---|---|---|---|:-:|
| 1 | Validation | `insumos/source-spec-validation.yaml` (borrador listo: `spec-sisben.md` Anexo A; `source-spec OK (22 campos, 29 códigos)`) | `edf-validation` | N2 | Ninguna | No (Aceptación) |
| 2 | Acceptance | `insumos/source-spec-acceptance.yaml` | `edf-acceptance` | N2 | Ninguna | No (Aceptación) |
| 3 | Curation (Flow y Stock) | `insumos/source-spec-curation.yaml` + `insumos/contrato-curation-pinning.md` | `edf-curation` | N2 | Ninguna | Sí |
| 4 | Pinning | `insumos/yaml-skill-pinning.yaml` | `edf-pinning` | N3 (`reusePIN`; matcher compartido sin cambios) | Ninguna | Sí |
| 5 | Synthesis | `insumos/yaml-skill-synthesis.yaml` | `edf-synthesis` | N3 | Ninguna | Sí |
| 6 | State Calculation | `insumos/yaml-skill-statecalc.yaml` | `edf-statecalc` | N4 | PA-002 (corte vigente en el enricher, `[A:CQ-006]`; bloqueante solo para esta etapa) | Sí (aprueba PO) |

Lo que cada etapa debe entregar, según la HU (`hu-sisben.md` §6), sin repetirlo aquí: Validation con el fixture del Anexo A y la prevalidación de la muestra (§15); Acceptance con umbral 0.90; Curation con su contrato hacia Pinning; Pinning con `reusePIN = false`; Synthesis sin handlers propios; State Calculation con la sección `#sisben` y aprobación del PO.

## 2. Orden de cierre de preguntas abiertas

| Orden | Pregunta | Debe cerrarse antes de | Quién la cierra |
|---|---|---|---|
| — | PA-001 · Qué etapa cumple R-26 — **resuelta por las fuentes** (State Calculation). «Aprobar la etapa 0 y generar Validation» ya no depende de ninguna pregunta abierta | — | Resuelta (aprobador simulado, BIT-0009) |
| 1 | PA-002 · Cómo se determina el corte vigente y cómo se excluye `#sisben` de los PIN fuera de ese corte (R-26, dentro de State Calculation) | Antes del insumo de State Calculation: aprobación de `insumos/yaml-skill-statecalc.yaml` y transición `synthesis` → `state_calculation` (bloqueante solo para esa etapa) | Decide el Desarrollador con el dueño del dominio (Ingesta 3); cierra el PO o Aprobador designado (N4) |

Pendientes que no son preguntas de esta ingesta pero que la ruta debe respetar: `[A:OQ-06]` (aprobador del patrón), `[A:OQ-TR-2b]` (tipo del salvoconducto en Experian), la certificación de 4 dimensiones de State Calculation (`07` §5.7; riesgo RG-011) y la base legal de los reportes al DNP (riesgo RG-004).

## 3. Patrones previos reutilizables

| Etapa | Patrón (ingesta de referencia) | Qué cambia para esta ingesta | Estado |
|---|---|---|---|
| Validation | Generador de `edf-validation` (plantillas y Capa A común) | Todo valor sale del Anexo A de la spec 1.0.2, no del ejemplo de la skill (`assets/examples/sisben/`, `deteccion-ambiguedades.md` §7) | `[P]` |
| Curation | MURIC, ingesta certificada de Colombia (`edf-analisis/references/fuentes/curation-muric.md`) | Nombres curados de `spec-sisben.md` §10.1; estandarización de §10.2; sin RT/EP | `[P]` |
| Pinning | `ColombiaIdentityMatcher` de MURIC (`edf-analisis/references/fuentes/pinning-muric.md`) | Sin bandera de fuente; prioridad 2 por `standardizedIdName`; `reusePIN = false` | `[P]` |
| Synthesis | Comportamiento por defecto del motor | Sin handlers ni reglas de consistencia propias (`spec-sisben.md` §12) | `[P]` |
| State Calculation | Familia `foundation-stage.json` con flag aditivo por ingesta (`07` §5.7) | Sección `#sisben` passthrough de 6 atributos (`spec-sisben.md` §13); filtro del corte vigente pendiente de PA-002 | `[P]` |

## 4. Riesgos de la ruta

IDs del expediente (`riesgos[]`); el detalle de todos, de RG-001 a RG-011, está en `contexto-ingesta.md` §7.

| ID | Nivel | Riesgo | Etapa | Mitigación |
|---|---|---|---|---|
| RG-003 | **crítico** | R-26 sin mecanismo definido: un PIN ausente del archivo vigente podría conservar su sección `#sisben` | `state_calculation` | PA-002 bloqueante solo para el insumo de la etapa 6 (`yaml-skill-statecalc.yaml`); decide PO o Aprobador designado (N4) |
| RG-002 | alto | `reusePIN` mal declarado corrompe la asignación de PIN | `pinning` | `reusePIN = false` explícito (`hu-sisben.md` R-24) |
| RG-009 | medio | Adultos con tipo `7` (salvoconducto) rechazados hasta que Experian cree el tipo | `validation` | Seguir el conteo `REGISTROS TIPO 7 PENDIENTES`; dependencia D-07 |
| RG-006 | medio | Compilación no certificada por el framework (F-01) | `validation` (y toda etapa que genere código) | El ingeniero compila y prueba en el fork |
| RG-010 | medio | Una reference del framework (no es fuente) contradice la spec sobre Synthesis | `synthesis` | Citar la spec en los insumos de Synthesis y State Calculation (`00` §6) |
| RG-011 | alto | Certificación de 4 dimensiones sin motor ni entregable en la HU | `state_calculation` | Registrarla en el insumo de la etapa 6 y escalar al PO |

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Mecanismo del filtro del corte vigente dentro de State Calculation | `[A:PA-002]` |
| 2 | Confirmación de la clasificación de riesgo por etapa | `[P]` (la confirma el Desarrollador, `06` §4) |

## Pie de trazabilidad

- Etapas, skills e insumos: `00` §2. Riesgo: `06` §2, §2.2. Entregables por etapa: `hu-sisben.md` §6.
- Patrones: `edf-analisis/references/fuentes/curation-muric.md`, `pinning-muric.md`; `07` §5.7.
- Riesgos: `contexto-ingesta.md` §7; `estado-ingesta.json` (`riesgos[]`, RG-001 a RG-011).
- Última verificación de referencias: 2026-11-24.
