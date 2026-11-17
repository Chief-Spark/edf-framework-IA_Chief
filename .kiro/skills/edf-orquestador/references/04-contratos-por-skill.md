# Contratos de entrada/salida por skill — lo que el Orquestador exige antes de aprobar

Subordinado a `.kiro/steering/00-framework-edf.md` §2, `07-contratos-frontera.md` y `11-contrato-curation-pinning.md`.
Trazabilidad: `especificacion_requerimientos_skills.md` §4–§7 · `arquitectura_framework.md` §5 · `hallazgos-sesiones.md` §6 · `SKILL.md` de cada skill de etapa.

El Orquestador **no genera** ninguno de estos artefactos: los **exige, verifica que estén completos y los registra** en el expediente. Una etapa no puede pasar a `pendiente_aprobacion` si falta un artefacto obligatorio de su contrato.

Cada etapa tiene **dos aprobaciones**, en este orden:
1. **Insumo** (`tipo: artefacto`): el `source-spec-*.yaml` o `yaml-skill-*.yaml` que produce `edf-analisis`. Sin insumo aprobado, la skill de etapa no arranca (su Paso 0 lo verifica).
2. **Transición** (`tipo: transicion`): los artefactos generados por la skill de etapa, con sus verificaciones.

Rutas relativas a `expedientes/<ingesta-id>/`.

## Etapa 0 — Análisis de Requerimiento y Contexto (`analisis`) · Habilitador · N1 `bajo`

| Dir. | Elemento | Formato | Oblig. |
|------|----------|---------|:------:|
| In | HU de la fuente | documento | ✅ |
| In | Spec de Confluence | documento | ✅ |
| In | Archivo de muestra (**sintético**) | delimitado | ➖ |
| In | Layout del archivo | documento | ➖ |
| Out | `analisis/contexto-ingesta.md` | `.md` | ✅ |
| Out | `analisis/mapeo-campos.md` (**todos** los campos) | `.md` | ✅ |
| Out | `analisis/dependencias.md` | `.md` | ✅ |
| Out | `analisis/plan-de-ruta.md` | `.md` | ✅ |
| Out | Preguntas abiertas | lista en `.md` + `preguntas_abiertas[]` | ✅ |

Skill: `edf-analisis`, modo etapa 0.
**Gate de entrada:** La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. Se verifican en `expedientes/<ingesta-id>/fuentes/` (archivo `HU*` y archivo `spec*`, o su ruta en `fuentes/rutas-fuentes.json`); lo hace cumplir el hook `.kiro/hooks/compuerta-etapa0.json`.
**Gate de cierre:** no se habilita `validation` si queda alguna pregunta abierta `bloqueante: true`, o si `mapeo-campos.md` no cubre todos los campos declarados en el contrato del archivo. El mapeo a **XPM no se hace aquí** (se arma al final del proceso).

## Etapa 1 — Validation (`validation`) · Aceptación · N2 `medio` mínimo

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/source-spec-validation.yaml`, válido contra `.kiro/skills/edf-validation/assets/schema/source-spec.schema.yaml` | ✅ |
| Out | `<Fuente>ValidationProcessor.scala`, `config/<Fuente>Spec.scala`, `<Fuente>ValidatedEvent.scala`, `validator/<Fuente>FileMetadata.scala` | ✅ |
| Out | Capa A: `FieldSpec.scala`, `FieldValidators.scala`, `FieldValidatorsSpec.scala` (una vez por fork) | ✅ |
| Out | `<Fuente>ValidationProcessorSpec.scala` + fixture sintético + catálogos sintéticos + `codigos-error-<fuente>.csv` | ✅ |
| Out | HOCON aditivo: `propuesta/validation-configs-<fuente>.conf`, `propuesta/lookups-<fuente>.conf` | ✅ |
| Out | `manifest.json` (archivos, sha256, supuestos, requisitos de ambiente) | ✅ |
| Evidencia | `render-templates.jar --solo-verificar` → `source-spec OK`; `checklist-entrega.md` recorrido | ✅ |
| Evidencia | Compilación en el fork: **no certificada** (F-01). Se registra como `prevalidacion.compila: null` hasta que el Desarrollador la reporte | ➖ |

## Etapa 2 — Acceptance (`acceptance`) · Aceptación · N2 `medio` (N3 si cambia el umbral)

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/source-spec-acceptance.yaml`, válido contra `.kiro/skills/edf-acceptance/assets/schema/source-spec.schema.yaml`; su `diagnostico_validation` sale del source-spec de Validation | ✅ |
| Out | Bloque HOCON de la ingesta (`fileActions`, `recordAcceptance`) | ✅ |
| Out | Condiciones con nombre (`oxygen.stage.acceptance.fileConditions[]`) | ✅ |
| Out | Lookup de severidades | ➖ (solo si `reject` usa `hasErrorlevelIn/NotIn`) |
| Out | `contextualOverride` | ➖ (solo con decisión humana registrada) |
| Out | `AcceptanceExpressionsSpec.scala` + fixture de eventos validados sintéticos | ✅ |
| Evidencia | `simular_decision.py` sobre el fixture; `checklist-entrega.md` recorrido | ✅ |

Parámetros de dominio que el expediente registra: umbral **0.90, parametrizable** (`average("qs.RQS") >= 0.90`); registro aceptado si `errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate")`; marcadores de archivo → rechazo automático.

## Etapa 3 — Curation Flow y Stock (`curation_flow`, `curation_stock`) · Éxito · N2 `medio`

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/source-spec-curation.yaml`, válido contra `.kiro/skills/edf-curation/assets/schema/source-spec.schema.yaml` | ✅ |
| Out | `<Fuente>CurationProcessor.scala`, `<Fuente>CuratedEvent.scala`, `<Fuente>CurationSpec.scala`, lector del evento aceptado | ✅ |
| Out | Capa A: `FieldMapping.scala`, `Transformations.scala` + specs | ✅ |
| Out | Bloque HOCON (`filePattern` + `processor`), lookups, entradas del domain dictionary | ✅ |
| Out | Fixture sintético y tests | ✅ |
| Out | Tabla del contrato hacia Pinning (`11` §4) con los campos de identidad normalizados | ✅ |
| Evidencia | Generador + implementación de referencia Java (conciliación `aceptados = curados + descartados + fallidos`); `checklist-entrega.md` | ✅ |

Flow procesa **solo la fuente en curso**; Stock procesa **todo lo acumulado en `accepted/`**. El mismo processor sirve a los dos modos; cada entrada se aprueba con su propia evidencia de DAG. Ambas deben estar aprobadas antes de `pinning`.

## Etapa 4 — Pinning (`pinning`) · Éxito · N3 `alto`

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/yaml-skill-pinning.yaml` (formato de `edf-analisis/assets/templates/yaml-skill-pinning.yaml.tpl`) | ✅ |
| In | Contrato Curation → Pinning de la ingesta (`insumos/contrato-curation-pinning.md`) | ✅ |
| Out | `<Ingesta>IdentityMatcher.scala` (solo atributos `[C]` activos) con la verificación aritmética del umbral en comentario | ✅ |
| Out | `identifying-attributes.json` | ✅ |
| Out | Bloque HOCON de `pinning-application.conf` | ✅ |
| Out | Valor declarado de `reusePIN` (**`false` en ingesta nueva**) | ✅ |
| Evidencia | `evals/verificar-artefacto.ps1` sobre los artefactos generados | ✅ |

Llave de identidad: **tipo doc + número doc + nombre estandarizado**; tipo y número no pueden ser nulos. Fallback de regla de corte permitido.

## Etapa 5 — Synthesis / Consistency (`synthesis`) · Éxito · N3 `alto`

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/yaml-skill-synthesis.yaml` | ✅ |
| Out | GDD `domaindictionary.json` con el mapeo campo → handler (LFP / LAP / EUC) **solo para campos confirmados** | ✅ |
| Out | Bloque HOCON `oxygen.stage.consistencyCheck` | ✅ |
| Evidencia | `evals/verificar-artefacto.ps1` | ✅ |

## Etapa 6 — State Calculation (`state_calculation`) · Éxito · **N4 `critico`**

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/yaml-skill-statecalc.yaml` | ✅ |
| Out | Bloque `fields[]` de `statecalc-foundation-local.json` | ✅ |
| Out | Sección `CalculationSection` de `statecalc-calculation-local.json` (`groupBy` plano) | ✅ |
| Evidencia | `evals/verificar-artefacto.ps1` y `evals/probar-arnes.ps1` | ✅ |

**Criterio de aceptación CA1:** Data Provisioning consulta los datos de la ingesta en XPM. Es la única etapa cuya aprobación final referencia CA1. Al ser N4, la aprobación la da el PO o el Aprobador designado (`03-gobierno-riesgo-aprobacion.md` §2).

## Diferencias con la especificación de requerimientos (abiertas)

El contrato anterior se derivó de `especificacion_requerimientos_skills.md`. Las skills construidas producen lo de arriba. Estas diferencias **no se resuelven aquí**: se registran como preguntas abiertas del framework hasta que el PO decida.

| # | Requerimiento original | Qué hace hoy la skill | Pregunta |
|---|---|---|---|
| DIF-1 | Acceptance: árbol `ACCEPTED/REJECTED/MANUAL_REVIEW` y cola de revisión manual | Diseño aceptado por el PO (2026-11-04): banda de revisión **a nivel de archivo** con el estado pendiente del motor + `manualAccept`/`manualReject` (`edf-acceptance` SKILL.md, "Revisión manual") | **Parcial:** abiertos el piso de calidad (DIF-1.2), la fuente de `externalAction` (DIF-1.3) y quién decide y en qué plazo (DIF-1.4). Hasta cerrarlos, `MANUAL_REVIEW` no se configura y la etapa se aprueba registrando el faltante |
| DIF-2 | Pinning: reglas de deduplicación | `edf-pinning` no genera `keep-together.json` / `keep-apart.json` sin caso de negocio confirmado | ¿Se exige deduplicación explícita para el piloto? |
| DIF-3 | Synthesis: consistency checks C-001 → C-005 | `edf-synthesis` genera GDD + bloque HOCON; no genera `consistency-checks.json` | ¿Quién produce los checks C-001..C-005? |
| DIF-4 | State Calculation: procesador de scoring (risk, trust, completeness) y constructor del perfil XPM | `edf-statecalc` **nunca** genera Scala; solo configuración declarativa (passthrough `MOST_RECENT_VALUE` confirmado para SISBEN) | **Cerrada (2026-11-04):** el scoring queda **fuera del alcance** del framework por decisión del PO, comunicada por el usuario. El perfil XPM se cubre con la configuración declarativa; CA1 sigue vigente. El Orquestador no exige artefactos de scoring |
| DIF-5 | Curation: referencia a `nvdt_utls_edf_fork` y `Bureau.Colombia_IFR` | No consta en el source-spec de Curation | ¿Es un insumo obligatorio? |

## Requerimientos transversales que el Orquestador verifica en todo artefacto

| ID | Verificación |
|----|--------------|
| RT-01 | Dentro del perímetro EDF (Z1) |
| RT-03 | Trae contexto precargado: qué cambió, por qué, regla aplicable, comparación con patrón previo |
| RT-04 | Declara su contrato de entradas/salidas |
| RT-05 | Consume conocimiento de steering/templates, no hardcodea reglas por fuente |
| RT-06 | Su decisión quedó en la bitácora |
| RT-08 | Trae riesgo autoclasificado |
