---
inclusion: always
---

# 00 — Framework de IA EDF sobre Kiro: mapa y reglas de operación

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`. Gana sobre las `references/` de cualquier skill |
| Arquitectura | `docs/arquitectura.md` del repo `edf-framework-IA` (opción A: orquestador arriba, skills de etapa abajo) |

Este documento dice **qué pieza hace qué** y **en qué orden**. Las reglas de fondo (perímetro, PII, riesgo, trazabilidad) están en `01`–`09`.

## 1. Las piezas

| Pieza | Rol | Genera artefactos de pipeline |
|---|---|:---:|
| `edf-orquestador` | Puerta de entrada. Lleva el expediente y la bitácora, clasifica riesgo, pide aprobación y habilita o bloquea cada etapa | No |
| `edf-analisis` | Etapa 0 (contexto y mapeo de campos) y análisis técnico de cada etapa: produce los insumos que consumen las skills de etapa | No (solo `.md` y `.yaml` de insumo) |
| `edf-validation`, `edf-acceptance`, `edf-curation` | Generan la propuesta de las etapas 1–3 | Sí |
| `edf-pinning`, `edf-synthesis`, `edf-statecalc` | Generan la propuesta de las etapas 4–6 | Sí |
| Hook `validar-expediente` | Valida el expediente contra su esquema e invariantes cada vez que se guarda | No |

Jerarquía: **el Orquestador habilita; `edf-analisis` prepara el insumo; la skill de etapa genera; el Desarrollador aprueba.** Ninguna skill aprueba ni habilita etapas.

## 2. Modelo de etapas (6 etapas + etapa 0)

Data Reception y Preparation **no** son etapas del framework (decisiones H-3 y H-4).

| # | Etapa | `id` en el expediente | Skill que genera | Insumo que debe existir y estar aprobado | Depende de |
|---|---|---|---|---|---|
| 0 | Análisis de Requerimiento y Contexto | `analisis` | `edf-analisis` (modo etapa 0) | HU y spec de la fuente (obligatorias); layout (opcional) | — |
| 1 | Validation | `validation` | `edf-validation` | `insumos/source-spec-validation.yaml` | `analisis` |
| 2 | Acceptance | `acceptance` | `edf-acceptance` | `insumos/source-spec-acceptance.yaml` | `validation` |
| 3 | Curation (Flow y Stock) | `curation_flow`, `curation_stock` | `edf-curation` (un processor sirve a los dos modos) | `insumos/source-spec-curation.yaml` | `acceptance` |
| 4 | Pinning | `pinning` | `edf-pinning` | `insumos/yaml-skill-pinning.yaml` | `curation_flow` y `curation_stock` |
| 5 | Synthesis / Consistency | `synthesis` | `edf-synthesis` | `insumos/yaml-skill-synthesis.yaml` | `pinning` |
| 6 | State Calculation | `state_calculation` | `edf-statecalc` | `insumos/yaml-skill-statecalc.yaml` | `synthesis` |

Rutas relativas a `expedientes/<ingesta-id>/`.

Compuerta de entrada de la etapa 0: La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. Las fuentes van en `expedientes/<ingesta-id>/fuentes/` (archivo `HU*` y archivo `spec*`), o su ruta se registra en `fuentes/rutas-fuentes.json`. La hace cumplir el hook `.kiro/hooks/compuerta-etapa0.json`.

## 3. Regla de habilitación (vale para todas las skills de etapa)

Antes de generar cualquier artefacto, toda skill de etapa ejecuta su **Paso 0**:

1. Identifica la ingesta y abre `expedientes/<ingesta-id>/estado-ingesta.json`. Si no existe → se detiene y deriva a `edf-orquestador` ("inicia el expediente de la ingesta …").
2. Busca la entrada de su etapa en `etapas[]` y verifica que **todas** las etapas de `depende_de` estén en `aprobada`, `completada_manualmente` u `omitida` (esta última solo con aprobación de omisión del PO, `edf-orquestador/references/03` §4b), y con `transicion_habilitada: true`.
3. Verifica que su insumo (§2) exista. Si falta → deriva a `edf-analisis`.
4. Si algo falla: **no genera**, dice exactamente qué falta y deriva al Orquestador. No hay excepción por urgencia ni por pedido directo del usuario.

Excepción única: trabajo sobre **fixtures sintéticos de evaluación** (`evals/`, `assets/examples/`) fuera de una ingesta real. La skill lo declara al inicio de la respuesta.

Las skills de etapa **no escriben** en `estado-ingesta.json` ni en `bitacora.md`. Al terminar entregan al Desarrollador la lista de artefactos (ruta, tipo, supuestos abiertos, resultado de verificaciones) para que el Orquestador los registre.

## 4. Dónde vive cada cosa en el workspace

```
.kiro/steering/              reglas (este pack)
.kiro/skills/                las 8 skills
.kiro/hooks/                 validación automática del expediente
tools/                       validar-expediente.py, validar-expedientes.py, verificar-claves.py
expedientes/<ingesta-id>/
├── estado-ingesta.json      fuente de verdad (lo escribe solo el Orquestador)
├── estado-ingesta.md        vista derivada
├── bitacora.md              append-only, sin PII
├── analisis/                salida de la etapa 0 (edf-analisis)
├── insumos/                 source-spec-*.yaml y yaml-skill-*.yaml (edf-analisis)
└── propuestas/<etapa>/      artefactos generados por cada skill de etapa
```

## 5. Correspondencias entre vocabularios

| Concepto | Steering `06` (regla) | Valor en el expediente (`estado-ingesta.json`) |
|---|---|---|
| Riesgo bajo | N1 | `bajo` |
| Riesgo medio | N2 | `medio` |
| Riesgo alto | N3 | `alto` |
| Riesgo crítico | N4 | `critico` — no se aprueba como acción normal: requiere escalamiento y actor `PO` o `Aprobador designado` |
| Etapa | `02` §2, numeración 0–6 | `id` de §2 (texto, no número) |

La clasificación se hace con los criterios de `06` §2 y se registra con el valor de la columna derecha.

## 6. Precedencia dentro del framework

1. `01-restricciones-principios.md` (rector).
2. Este documento.
3. `02`–`09` y `11` según su tema.
4. `references/` de cada skill (conocimiento operativo de esa skill).
5. Recomendación del agente.

Si una `reference` de skill contradice el steering, **manda el steering** y la contradicción se registra como pregunta abierta.

**Datos de una ingesta.** El steering fija las reglas del framework (gobierno, etapas, formatos, convenciones). Los datos de cada ingesta (contrato del archivo, campos, catálogos, nombres curados, atributos de identidad, bandera de fuente, si pasa por cada etapa, rutas) los declaran su HU y su spec en `expedientes/<id>/fuentes/`. Cuando el steering o una `reference` dejan abierto un punto de ese tipo (`[A:*]`, `[PM]`, `OQ-*`) y la HU o la spec de la ingesta lo responden, **la respuesta de la HU o la spec es `[C]` para esa ingesta**: se cita la fuente y no se registra como supuesto ni como pregunta. Solo es pregunta abierta si la HU y la spec tampoco lo responden, o si se contradicen entre sí.
