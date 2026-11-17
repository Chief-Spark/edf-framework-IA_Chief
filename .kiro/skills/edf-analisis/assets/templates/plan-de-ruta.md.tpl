# Plan de ruta — {{Ingesta}}

| Campo | Valor |
|---|---|
| Ingesta | `{{ingesta-id}}` |
| Versión / Fecha | 1.0.0 / {{AAAA-MM-DD}} |
| Estado | PROPUESTO |

## 1. Ruta por etapa

| # | Etapa | Insumo que producirá `edf-analisis` | Skill que genera | Riesgo esperado (`06`) | Preguntas abiertas que la afectan | ¿Candidata a regla de corte? |
|---|---|---|---|---|---|:-:|
| 1 | Validation | `insumos/source-spec-validation.yaml` | `edf-validation` | N2 | | No (Aceptación) |
| 2 | Acceptance | `insumos/source-spec-acceptance.yaml` | `edf-acceptance` | N2 | | No (Aceptación) |
| 3 | Curation (Flow y Stock) | `insumos/source-spec-curation.yaml` + `insumos/contrato-curation-pinning.md` | `edf-curation` | N2 | | Sí |
| 4 | Pinning | `insumos/yaml-skill-pinning.yaml` | `edf-pinning` | N3 | | Sí |
| 5 | Synthesis | `insumos/yaml-skill-synthesis.yaml` | `edf-synthesis` | N3 | | Sí |
| 6 | State Calculation | `insumos/yaml-skill-statecalc.yaml` | `edf-statecalc` | N4 | | Sí (aprueba PO) |

## 2. Orden de cierre de preguntas abiertas

| Orden | Pregunta | Debe cerrarse antes de | Quién la cierra |
|---|---|---|---|

## 3. Patrones previos reutilizables

| Etapa | Patrón (ingesta de referencia) | Qué cambia para esta ingesta |
|---|---|---|

## 4. Riesgos de la ruta

| ID | Riesgo | Etapa | Mitigación |
|---|---|---|---|
