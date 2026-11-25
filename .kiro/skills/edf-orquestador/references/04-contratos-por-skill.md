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
**Gate de cierre:** no se habilita `validation` si queda alguna pregunta abierta `bloqueante: true` con `etapa_afectada` ∈ {`analisis`, `validation`} (o sin `etapa_afectada`), o si `mapeo-campos.md` no cubre todos los campos declarados en el contrato del archivo. El mapeo a **XPM no se hace aquí** (se arma al final del proceso). Una pregunta bloqueante de una etapa posterior no frena este gate: frena la aprobación del insumo de su `etapa_afectada` y la transición hacia ella (`03-gobierno-riesgo-aprobacion.md` §3, condición 4).

## Etapa 1 — Validation (`validation`) · Aceptación · N2 `medio` mínimo

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/source-spec-validation.yaml`, válido contra `.kiro/skills/edf-validation/assets/schema/source-spec.schema.yaml` | ✅ |
| Out | `<Fuente>ValidationProcessor.scala`, `config/<Fuente>Spec.scala`, `<Fuente>ValidatedEvent.scala`, `validator/<Fuente>FileMetadata.scala` | ✅ |
| Out | Capa A: `FieldSpec.scala`, `FieldValidators.scala`, `FieldValidatorsSpec.scala` (una vez por fork) | ✅ |
| Out | `<Fuente>ValidationProcessorSpec.scala` + fixture sintético + catálogos sintéticos + `codigos-error-<fuente>.csv` | ✅ |
| Out | HOCON aditivo: `propuesta/validation-configs-<fuente>.conf`, `propuesta/lookups-<fuente>.conf` | ✅ |
| Out | `manifest.json` (archivos, sha256, supuestos, requisitos de ambiente) | ✅ |
| Evidencia | `python scripts/render_templates.py --solo-verificar` → `source-spec OK`; `checklist-entrega.md` recorrido. Generado sin compilar nada | ✅ |
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
| Out | Artefactos según el **modo de Pinning** de la ingesta (tabla siguiente) | ✅ |
| Evidencia | `evals/verificar-artefacto.ps1` sobre los artefactos generados | ✅ |

Los artefactos de salida dependen del modo, que el Orquestador fija al abrir el expediente (prompt `00`, paso 2) y que coincide con `modo_colombia.aplica` del insumo (HF-63):

| Artefacto (id de la semilla) | Modo propio (aplicación de Pinning propia) | Modo Colombia (aplicación compartida, `edf-pinning` SKILL «Modo Colombia») |
|---|---|---|
| ART-051 `<Ingesta>IdentityMatcher.scala` (solo atributos `[C]` activos) con la verificación aritmética del umbral en comentario | ✅ | `obligatorio: false` · no aplica: se reutiliza el matcher compartido de Colombia |
| ART-052 `identifying-attributes.json` | ✅ | `obligatorio: false` · no aplica: se reutiliza `identifying-attributes-new.json` |
| ART-053 Bloque HOCON de la ingesta | ✅ con las claves de la ingesta y `reusePin` declarado (`[C]`) | ✅ **sin claves compartidas**: solo la lista de verificación de las claves compartidas en el conf de Colombia (matcher compartido y lista referenciados), sin redefinirlas y **sin escribir `reusePin`** (HF-57, HF-59) |
| `reusePIN` | valor declarado en el bloque (`false` si la ingesta no tiene generación previa) | valor de la fuente registrado como **dato** (`valor_en_fuente`); no se escribe. Cambiar la clave global es N4 y se escala |

La semilla (`templates/estado-ingesta.seed.json`) trae el modo Colombia. En modo propio, el Orquestador pone `obligatorio: true` en ART-051 y ART-052 y cambia el nombre de ART-053 a «bloque HOCON de pinning-application.conf con reusePin declarado».

Llave de identidad: **tipo doc + número doc + nombre estandarizado**; tipo y número no pueden ser nulos. Fallback de regla de corte permitido.

## Etapa 5 — Synthesis / Consistency (`synthesis`) · Éxito · N3 `alto`

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/yaml-skill-synthesis.yaml` | ✅ |
| Out | Artefactos según el **modo de Synthesis** de la ingesta (tabla siguiente) | ✅ |
| Evidencia | `evals/verificar-artefacto.ps1` y `tools/verificar-artefactos-etapa.py synthesis --modo <propio\|colombia>` | ✅ |

Los artefactos dependen del modo, que el Orquestador fija al abrir el expediente (prompt `00`, paso 2) y que coincide con `modo_colombia.aplica` del insumo (HF-68). En modo Colombia una corrida de Synthesis lee un solo GDD, un solo diccionario de consistencia y un solo `customRulesFile` y procesa todo el HC de Colombia (`SparkConsistencyChecker.scala:241-245`), así que la ingesta no escribe claves compartidas:

| Artefacto (id de la semilla) | Modo propio (configuración de Synthesis propia) | Modo Colombia (configuración compartida, `edf-synthesis` SKILL «Modo Colombia») |
|---|---|---|
| ART-061 GDD | ✅ `domaindictionary.json` con el mapeo campo → handler (LFP / LAP / EUC) **solo para campos confirmados** | ✅ **entradas del GDD de la ingesta** (`"version": 2`, solo sus `fields[]`) que se integran en el GDD compartido de Colombia, sin duplicar ni modificar entradas existentes: es la pieza que aporta la ingesta |
| ART-062 Bloque HOCON | ✅ bloque `oxygen.stage.consistencyCheck` con la raíz y `customRulesFile` no nulo (`--modo propio`) | ✅ **lista de verificación**: solo comentarios `VERIFICAR <ruta HOCON completa>` de las claves compartidas (diccionario, GDD, `customRulesFile`, `dateType`, `checkFileTagEnabled`, `eventTypeList` y las lecturas `.get` sin valor por defecto), **sin escribir claves** (`--modo colombia`); riesgo `alto` |

La semilla (`templates/estado-ingesta.seed.json`) trae el modo Colombia. En modo propio, el Orquestador cambia el nombre de ART-061 a «GDD domaindictionary.json (campo -> handler LFP/LAP/EUC, solo confirmados)» y el de ART-062 a «bloque HOCON oxygen.stage.consistencyCheck con raíz y customRulesFile».

## Etapa 6 — State Calculation (`state_calculation`) · Éxito · **N4 `critico`**

| Dir. | Elemento | Oblig. |
|------|----------|:------:|
| In (insumo) | `insumos/yaml-skill-statecalc.yaml` | ✅ |
| Out | Artefactos según el **modo de State Calculation** de la ingesta (tabla siguiente) | ✅ |
| Evidencia | `evals/verificar-artefacto.ps1`, `evals/probar-arnes.ps1` y `tools/verificar-artefactos-etapa.py statecalc --modo <propio\|colombia> --source-spec insumos/source-spec-curation.yaml` | ✅ |

Los artefactos dependen del modo, que el Orquestador fija al abrir el expediente (prompt `00`, paso 2) y que coincide con `modo_colombia.aplica` del insumo (HF-77). En modo Colombia la configuración de State Calculation es compartida: la corrida lee todo el sintetizado de Colombia, el registro Foundation de un PIN mezcla todas las fuentes y los foundation y calculation se fusionan (`ContextUtils.scala:15-30`), así que la ingesta aporta entradas, no archivos propios (`edf-statecalc` SKILL «Modo Colombia», `references/modo-colombia.md`):

| Artefacto (id de la semilla) | Modo propio (configuración de State Calculation propia) | Modo Colombia (configuración compartida) |
|---|---|---|
| ART-071 Foundation | ✅ bloque `fields[]` de `statecalc-foundation-local.json` | ✅ **entradas `fields[]` de la ingesta** para integrar en el foundation compartido, **sin repetir un `name` ni alias existente** (un nombre duplicado se procesa dos veces y el último gana para todas las fuentes, `ContextUtils.scala:15-19`) |
| ART-072 Calculation | ✅ `CalculationSection` de `statecalc-calculation-local.json` (`groupBy` plano) | ✅ **secciones de la ingesta** para integrar en el calculation compartido: **cada una con `where` de filtro por fuente** (sin él MOST_RECENT_VALUE toma el registro más reciente de cualquier fuente, `MostRecentValue.scala:20-24`), `name` válido como identificador JavaScript (sin `#`, `QueryFunctions.scala:75-82`) y sin repetir una sección existente (`--modo colombia`); el `where` depende de `[A:OQ-TR-2]`; riesgo `critico` |
| ART-073 CA1 | ✅ CA1: Data Provisioning consulta la sección en el XPM | ✅ CA1 en el **XPM compartido**, con la **lista de verificación del fork** como precondición: nombres existentes, **modo delta** (`deltaMode`, `deltaModeAccountLevel`, `doesComputeXpm`: en modo delta un PIN sin cambios conserva su XPM anterior, `SparkStateCalculator.scala:187-188, 207-213, 246-271`), `aliasEnabled`, JDK y Nashorn (OQ-TR-2), y un caso de PIN de otra fuente sin la sección |

La semilla (`templates/estado-ingesta.seed.json`) trae el modo Colombia. En modo propio, el Orquestador cambia el nombre de ART-071 a «fields[] de statecalc-foundation-local.json», el de ART-072 a «CalculationSection de statecalc-calculation-local.json» y el de ART-073 a «CA1: Data Provisioning consulta la seccion de la ingesta en el XPM». En los dos modos, el passthrough `MOST_RECENT_VALUE` es la forma de la sección; no cumple por sí solo una regla de vigencia ni de fuente (p. ej. HU SISBEN R-26), y la lógica propia que haga falta es `[A:CQ-006]`.

**Criterio de aceptación CA1:** Data Provisioning consulta los datos de la ingesta en XPM. Es la única etapa cuya aprobación final referencia CA1. Al ser N4, la aprobación la da el PO o el Aprobador designado (`03-gobierno-riesgo-aprobacion.md` §2).

## Diferencias con la especificación de requerimientos (abiertas)

El contrato anterior se derivó de `especificacion_requerimientos_skills.md`. Las skills construidas producen lo de arriba. Estas diferencias **no se resuelven aquí**: se registran como preguntas abiertas del framework hasta que el PO decida.

| # | Requerimiento original | Qué hace hoy la skill | Pregunta |
|---|---|---|---|
| DIF-1 | Acceptance: árbol `ACCEPTED/REJECTED/MANUAL_REVIEW` y cola de revisión manual | Diseño aceptado por el PO (2026-11-04): banda de revisión **a nivel de archivo** con el estado pendiente del motor + `manualAccept`/`manualReject` (`edf-acceptance` SKILL.md, "Revisión manual") | **Parcial:** abiertos el piso de calidad (DIF-1.2), la fuente de `externalAction` (DIF-1.3) y quién decide y en qué plazo (DIF-1.4). Hasta cerrarlos, `MANUAL_REVIEW` no se configura y la etapa se aprueba registrando el faltante |
| DIF-2 | Pinning: reglas de deduplicación | `edf-pinning` no genera `keep-together.json` / `keep-apart.json` sin caso de negocio confirmado | ¿Se exige deduplicación explícita para el piloto? |
| DIF-3 | Synthesis: consistency checks C-001 → C-005 | `edf-synthesis` genera GDD + bloque HOCON; no genera `consistency-checks.json` | ¿Quién produce los checks C-001..C-005? |
| DIF-4 | State Calculation: procesador de scoring (risk, trust, completeness) y constructor del perfil XPM | `edf-statecalc` **nunca** genera Scala; solo configuración declarativa (passthrough `MOST_RECENT_VALUE` de atributos; si la ingesta necesita lógica propia, como el corte vigente de SISBEN R-26, es `[A:CQ-006]`, HF-77) | **Cerrada (2026-11-04):** el scoring queda **fuera del alcance** del framework por decisión del PO, comunicada por el usuario. El perfil XPM se cubre con la configuración declarativa; CA1 sigue vigente. El Orquestador no exige artefactos de scoring |
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
