# Auditoría independiente — Etapa 0 (`analisis`) · ingesta `sisben`

| Campo | Valor |
|---|---|
| Auditor | Agente auditor independiente (corrida autónoma, Kiro emulado) |
| Fecha | 2026-11-24 |
| Alcance | ART-001 a ART-004 (`analisis/`), ART-010 (`insumos/source-spec-validation.yaml`), PA-001 y seis posibles defectos del framework reportados por el analista |
| Fuentes válidas | `fuentes/hu-sisben.md` 1.0.2, `fuentes/spec-sisben.md` 1.0.2, `fuentes/muestra/` (idénticas a `expedientes/sisben/fuentes/` del framework: `diff -q` sin diferencias) |
| Riesgo de la etapa | N1 (`06` §2.2, "Análisis de la etapa 0 sin generación") |
| **Dictamen global** | **AJUSTES.** El contenido factual es correcto y trazable; el insumo de Validation es fiel a la spec. Lo único que impide aprobar es cómo se planteó PA-001: está mal acotada (la HU y la spec ya dicen qué etapa cumple R-26) y está clasificada contra la etapa equivocada |

## 1. Dictamen por artefacto

| Artefacto | Dictamen | Razones |
|---|---|---|
| ART-001 `contexto-ingesta.md` | **AJUSTES** | Identidad, contrato del archivo (6 dimensiones), 27 reglas y riesgos están bien citados. Revisé 14 citas al azar contra la HU, la spec y el steering `06`/`07` y todas son correctas. Recalculé los epoch de HU §5.3 (`1768021200000`, `1769835600000`) y son correctos. No inventa reglas. Ajustes necesarios: la ficha de PA-001 (AUD-E0-01, -02), la fila 1 de §4 (AUD-E0-04) y los IDs de riesgo, que no coinciden con los del expediente (AUD-E0-05) |
| ART-002 `mapeo-campos.md` | **APROBADO** | 22 de 22 campos con las 9 dimensiones y los 10 campos que agrega el proceso. La cuenta (24 + 10 = 34 claves de spec §10.1) es correcta. Las clasificaciones PII (R-28) y los códigos y severidades coinciden con spec §3 y §8. Los marcadores `[C]`/`[PM]` son correctos, y el `[PM]` del motivo de advertencia de las fechas está bien declarado. Observación menor sin hallazgo: la fila 13 (`seg_nombre`) no repite de forma explícita "si queda vacío: nulo y advertencia", aunque lo cubre al remitir a §10.2. Las celdas con varios hechos vienen de la plantilla (AUD-E0-13) |
| ART-003 `dependencias.md` | **AJUSTES** | Catálogos, configuración y dueños correctos; los dueños inferidos van como `[PM]`, como pide la plantilla. Arrastra el planteamiento de PA-001: C-10 dice "Bloquea: Sí" para Validation, P-01 dice "antes de generar Validation" y E-05 menciona metadatos de Validation (AUD-E0-03) |
| ART-004 `plan-de-ruta.md` | **AJUSTES** | La ruta, los riesgos por etapa (N2/N2/N2/N3/N3/N4) y los patrones son correctos. Además advierte bien que no se copie el ejemplo de la skill. Pero asigna PA-001 a Validation y a Acceptance (alternativas a y b) y la ordena "antes de aprobar la etapa 0 y generar Validation" (AUD-E0-03) |
| ART-010 `source-spec-validation.yaml` | **APROBADO** | Es idéntico, byte a byte, al bloque YAML del Anexo A (spec:388-560); `diff` solo muestra la cabecera de comentarios que agregó el analista. Esa cabecera es veraz: la declara BORRADOR porque la etapa 0 no está aprobada (Modo 2 de `edf-analisis`). `--solo-verificar` da `source-spec OK (22 campos, 29 códigos)` y la prevalidación reproduce la spec §15. Las observaciones AUD-E0-07 y -09 son informativas y no cambian el insumo |

## 2. Hallazgos

| ID | Artefacto | Clasificación | Sev. | Evidencia | Corrección sugerida |
|---|---|---|:-:|---|---|
| AUD-E0-01 | ART-001 (§6, ficha PA-001) y expediente (`preguntas_abiertas[0]`) | artefacto | P1 | `contexto-ingesta.md:112,121-124`. La HU responde qué etapa: `hu-sisben.md:95` (R-26: «**State Calculation** genera la sección `#sisben` solo para los PIN presentes en el archivo vigente»), `hu-sisben.md:346` (DoD de State Calculation con los escenarios de §5.6) y `spec-sisben.md:338`. Las alternativas (a) y (b) contradicen fuentes definitivas: `spec-sisben.md:386` (Anexo A «Es el contenido que `edf-analisis` debe dejar en `insumos/source-spec-validation.yaml`»), `hu-sisben.md:342` (DoD de Acceptance: umbral y regla de registro), `hu-sisben.md:42` (reglas estables) y `spec-sisben.md:323-325` (Synthesis por defecto). Además, `etapa_afectada` es `validation` en el artefacto y `analisis` en `estado-ingesta.json`; ninguna es la etapa real | Reformular según §3 de este documento: etapa = `state_calculation`; quitar (a) y (b) como alternativas admisibles o marcarlas como "requieren una versión nueva de HU/spec"; citar R-26 de forma textual en "Qué dice cada fuente" y en "Por qué no la resuelvo" |
| AUD-E0-02 | ART-001 (ficha PA-001) | artefacto | P2 | `contexto-ingesta.md:118` clasifica PA-001 como patrón 6 («dimensión ausente»). Pero el patrón 6 es una de las nueve dimensiones de un campo o una de las seis del contrato (`deteccion-ambiguedades.md:47`). El mecanismo interno de una etapa es «una decisión de implementación de una etapa posterior. No es del análisis» (`deteccion-ambiguedades.md:126`) | Llevar el mecanismo residual al análisis T4 de State Calculation (`yaml-skill-statecalc.yaml`), con el riesgo RG-003 como recordatorio |
| AUD-E0-03 | ART-003, ART-004 | artefacto | P1 | `dependencias.md:46` (C-10 «Bloquea: Sí» en `validation`), `:54` (P-01 «Antes de generar Validation (bloqueante)»), `:71` (E-05); `plan-de-ruta.md:109-110,113` (PA-001 en Validation, Acceptance y Synthesis), `:122` («Aprobar la etapa 0 y generar Validation») y `:140` | Una vez corregido AUD-E0-01: quitar C-10 y E-05 o marcarlos "no aplica sin una versión nueva de la spec"; dejar P-01 con fecha límite «antes del insumo de State Calculation»; dejar PA-001 solo en la fila 6 del plan |
| AUD-E0-04 | ART-001 (§4) | artefacto | P2 | `contexto-ingesta.md:94` registra como contradicción entre fuentes una diferencia con `references/fuentes/curation-pinning-sisben.md`, que no es fuente (`hu-sisben.md:12`; `deteccion-ambiguedades.md:128-129`) | Quitar la fila de §4 (dejar «ninguna contradicción entre HU, spec y muestra»); el riesgo RG-010 ya lo cubre |
| AUD-E0-05 | ART-001 §7, ART-004 §4 frente a `estado-ingesta.json` | artefacto (registro del orquestador) | P2 | Los artefactos proponen RG-003 a RG-011, y el expediente registró RG-003 a RG-006 con otro contenido. Ejemplos: RG-005 es «Fec_digitacion futura» en el artefacto y «Ruíz + INC_011» en el expediente; RG-006 es «re-identificación» en el artefacto y «F-01» en el expediente. RG-008 a RG-011 no están registrados | Alinear la numeración: renumerar los artefactos con los IDs del expediente y registrar los faltantes, o al revés. Un ID debe significar lo mismo en todos lados (`05` §6, `09`) |
| AUD-E0-06 | ART-001 §3 / ART-002 fila 19 | artefacto | P2 (observación) | La HU R-08 dice que `fec_actualizacion_persona` «se toma como llega» (`hu-sisben.md:62`), pero spec §10.1 la convierte a epoch (`spec-sisben.md:267`). El analista concilió las dos fuentes (nota de la fila 19) y la marcó `[C]` | Es una lectura defendible (R-08 está en el bloque de Validation; R-23 rige Curation). Basta con que la nota diga que es una lectura conjunta. No hace falta abrir una pregunta |
| AUD-E0-07 | ART-010 / insumo de State Calculation | motor | P2 (informativo) | `file_tag_por_prefijo: {"": REGULAR}` (`spec-sisben.md:416`) → `fileTag = "REGULAR"` (`edf-validation/assets/templates/FileMetadata.scala.tpl:35`). El borrado inferido V2 del motor se basa en `fileTag` por fuente: `Motor-Oxigen/oxygen-synthesis/.../SparkConsistencyChecker.scala:75-120`, `.../HCUtil.scala:76-85` y `consistency-application.conf:34-35` (`checkFileTagEnabled = false`, `eventTypeList = ["sisben",...]`). En `oxygen-statecalculation` no hay ningún filtro por `voidIndicator` | Si el análisis T4 elige `checkFileTag`, hará falta un `fileTag` propio de la fuente: eso cambia el Anexo A y exige una versión nueva de la spec. La vía compatible con las fuentes actuales es filtrar en State Calculation con `fechaCorte` (spec §10.1) frente al corte vigente |
| AUD-E0-08 | spec | spec | P2 | `spec-sisben.md:11` cita `render-templates.jar --solo-verificar`; el generador Java está deprecado (`edf-validation/SKILL.md:67`) | Corregir en la próxima versión de la spec. No afecta: el generador Python da el mismo resultado (§5) |
| AUD-E0-09 | ART-010 | spec | P2 (informativo) | `spec_version: "sisben-iv-1.0.0"` (`spec-sisben.md:390`) frente a la spec 1.0.2. Las versiones 1.0.1 y 1.0.2 declaran «sin cambios en Validation» (`spec-sisben.md:6-7`) | No tocar el insumo (sería apartarse del Anexo A); anotar en la spec si `spec_version` versiona el contrato de Validation o el documento |
| AUD-E0-10 | — (framework) | framework | P1 | Defecto 1, ver §4 | Ver §4 |
| AUD-E0-11 | — (framework) | framework | P1 | Defecto 2, ver §4 | Ver §4 |
| AUD-E0-12 | — | spec | P2 | Defecto 3 = AUD-E0-08 | — |
| AUD-E0-13 | — (framework) | framework | P2 | Defecto 4, ver §4 | Ver §4 |
| AUD-E0-14 | — (framework) | framework | P2 | Defecto 5, ver §4 | Ver §4 |
| AUD-E0-15 | — (framework) | framework | P2 | Defecto 6, ver §4 | Ver §4 |

## 3. Dictamen sobre PA-001

**¿Está abierta?** Solo en parte. La pregunta, tal como está redactada, tiene dos partes:

1. **«¿Qué etapa lo configura?»: la responden la HU y la spec, así que no es pregunta abierta** (`00-framework-edf.md` §6; `deteccion-ambiguedades.md:128`).
   - La responde la HU R-26 (`hu-sisben.md:95`), literal: «State Calculation genera la sección `#sisben` solo para los PIN presentes en el archivo vigente».
   - Lo confirman la HU §5.6 (Feature «Stage 6», `hu-sisben.md:316-327`), la DoD de State Calculation (`hu-sisben.md:346`, escenarios de §5.6) y spec §13 (`spec-sisben.md:338`).
   - Las demás etapas quedan fijadas por fuentes definitivas:
     - Validation: el Anexo A es su contenido (`spec-sisben.md:386`).
     - Acceptance: umbral y regla de registro (`hu-sisben.md:342`).
     - Synthesis: comportamiento por defecto, sin processors propios (`spec-sisben.md:323-325`).
     - Carga completa sin RT/EP: `spec-sisben.md:296`; `hu-sisben.md:27`.
   - Por eso las alternativas (a) (`archivo.metadata`/`fileLoadType` en Validation) y (b) (`inferDelete` en Acceptance) no son lecturas admisibles de las fuentes actuales. Elegirlas exigiría una versión nueva de la HU y la spec (`hu-sisben.md:42`).
2. **«¿Con qué mecanismo concreto?»: sigue abierta, pero es una decisión de implementación de la etapa 6.** Por ejemplo, cómo determina el enricher el corte vigente y compara el `fechaCorte` curado del PIN (`spec-sisben.md:271`). Según `deteccion-ambiguedades.md:126`, no es una ambigüedad de la etapa 0. Toca `[A:CQ-006]` (`07` §5.7). El motor no trae un filtro listo en State Calculation (AUD-E0-07).

**¿Qué bloquea?** Solo `state_calculation`, en concreto la aprobación del insumo `yaml-skill-statecalc.yaml`. No bloquea la etapa 0 ni `validation`:
- Su respuesta no cambia ningún valor de los cuatro artefactos de la etapa 0 ni de `source-spec-validation.yaml`, `-acceptance`, `-curation` o `yaml-skill-synthesis.yaml`.
- Es coherente con la condición 4 de `edf-orquestador/references/03-gobierno-riesgo-aprobacion.md:50` («en la etapa N») y con el precedente de `03-pipeline.md:200` («bloqueante para la etapa 6»).
- Bajo la letra de `deteccion-ambiguedades.md:65` y del gate de `04-contratos-por-skill.md:30`, una pregunta que cambie *cualquier* insumo bloquea la etapa 0. El analista aplicó esa letra, pero es el defecto 1 (§4).

**Respuesta recomendada para el Desarrollador** (solo el Desarrollador cierra la pregunta; el Orquestador la registra):

> PA-001 — Resuelta por las fuentes. La etapa que cumple R-26 es **State Calculation**: `hu-sisben.md` R-26 y §6 (fila State Calculation, escenarios §5.6), y `spec-sisben.md` §13. Validation, Acceptance, Curation y Synthesis no cambian: `spec-sisben.md` Anexo A y §12, `hu-sisben.md` §6. Las alternativas (a) y (b) quedan descartadas mientras no exista una versión nueva de la HU y la spec. El mecanismo concreto dentro de State Calculation (filtro por corte vigente a partir de `fechaCorte`, spec §10.1, en `statecalc-foundation`/enricher) se define en el análisis T4 de `yaml-skill-statecalc.yaml`. Si allí sigue sin definirse, se abre como pregunta con `etapa_afectada: state_calculation`. RG-003 sigue abierto con mitigación «no aprobar el insumo de State Calculation sin ese mecanismo».

Con esa respuesta, más los ajustes AUD-E0-01, -03, -04 y -05, la etapa 0 queda en condiciones de aprobarse.

## 4. Evaluación de los seis posibles defectos del framework

| # | Defecto reportado | Veredicto | Evidencia y razón |
|---|---|---|---|
| 1 | `deteccion-ambiguedades.md:65` + `04-contratos-por-skill.md:30`: toda pregunta que cambie un insumo es bloqueante y frena `validation` aunque afecte solo una etapa posterior | **Real (P1)** | La línea 65 dice: «No bloqueante solo si la respuesta no cambia ningún valor de los cuatro artefactos de la etapa 0 **ni de ningún insumo de etapa**». El gate de `04-contratos-por-skill.md:30` (y `01-flujo-etapas.md:35`) no distingue etapa. Esto choca con: `03-gobierno-riesgo-aprobacion.md:50` (condición 4: «en la etapa N»); `03-pipeline.md:200` («bloqueante para la etapa 6»); el campo `etapa_afectada` del esquema (`estado-ingesta.schema.json:368-374`); y `deteccion-ambiguedades.md:126` (una decisión de una etapa posterior no es ambigüedad). Corrección: que el bloqueo se mida por `etapa_afectada`, y que el gate de la etapa 0 considere solo las preguntas con `etapa_afectada` ∈ {`analisis`, `validation`} |
| 2 | `edf-validation/SKILL.md:74` manda partir del ejemplo SISBEN, contra `00` §6 | **Real (P1)** | `SKILL.md:74-75`: «Modo SISBEN pre-llenado: partir de `assets/examples/sisben/source-spec.sisben.yaml`». Ese ejemplo difiere de la spec 1.0.2: prefijos `RT_`/`EP_`, `SISBEN_ERR_001` obligatorio con longitud 50, catálogo `SISBEN_HOME_ID_CLASIFICATION_NH`, y otros (diff YAML de esta auditoría). Viola `00` §6 (los datos de la ingesta salen de la HU y la spec) y el Paso 0.3 de la misma skill (`SKILL.md:26`: el insumo es el aprobado de `edf-analisis`). El mismo riesgo aparece en `edf-analisis/SKILL.md:75,84` («Parte del ejemplo SISBEN de la etapa»), que para SISBEN invita a copiar valores obsoletos. El analista lo evitó bien |
| 3 | `spec-sisben.md:11` cita `render-templates.jar` | **Real, pero de la spec, no del framework (P2)** | La cita está, y el JAR es histórico (`edf-validation/SKILL.md:67`). El resultado declarado se reproduce con el generador Python (§5). La spec es un documento que el repositorio entrega como fuente; se corrige en su próxima versión |
| 4 | La plantilla de `mapeo-campos` obliga a poner varios hechos por celda, contra `05-convenciones.md:113` | **Real (P2)** | `mapeo-campos.md.tpl:12` junta en una celda «Tipo origen → destino» y «Obligatorio (y qué pasa si falta)», y pone una sola columna `Estado` para 11 dimensiones por fila. Así no se puede marcar `[A:*]` en una sola dimensión. `05-convenciones.md:113`: «Una fila = una afirmación. Nunca dos hechos en una celda». Corrección: eximir de forma explícita la tabla de mapeo (formato de contrato) o pasarla a formato largo (campo × dimensión) con `Estado` por dimensión |
| 5 | `07-contratos-frontera.md:87` no incluye el descarte silencioso ni «nulo + advertencia» | **Real (P2)** | La tabla de `07:87-91` tiene tres efectos: rechazo, inconsistencia y «vaciado del campo con preservación» (con `<campo>_original`). No contempla: el filtro silencioso (spec §5; el propio `07:105` habla de «rechazo silencioso de menores»), el nulo con advertencia sin preservar el original (spec §10.4) ni la marca de duplicado no aceptado (spec §6). La plantilla de mapeo obliga a usar ese vocabulario (`mapeo-campos.md.tpl:12`) |
| 6 | A `contexto-ingesta.md.tpl:61` le falta la casilla de insumos obligatorios de `04` §4.3 | **Real (P2)** | `04-reglas-analisis.md:84` tiene la primera casilla «Insumos obligatorios presentes y leídos». El checklist de la plantilla (`contexto-ingesta.md.tpl:63-74`) tiene 12 casillas y le falta esa. El analista la agregó a mano |

## 5. Verificaciones corridas

| Verificación | Comando (desde `.kiro/skills/edf-validation`) | Salida resumida | Frente a spec |
|---|---|---|---|
| Esquema y reglas del source-spec | `python3 scripts/render_templates.py --spec <abs>/insumos/source-spec-validation.yaml --solo-verificar` | `source-spec OK (22 campos, 29 códigos).`, rc=0 | Igual a la cabecera de la spec (`spec-sisben.md:11`) |
| Prevalidación de la muestra | `python3 scripts/prevalidar_archivo.py --spec <insumo> --archivo <muestra> --hoy 2026-11-17` | `nombre_valido: true`, `header_valido: true`, `filas_filtradas: 1`, `registros_validados: 20`, `registros_con_error_sev1: 1` (`SISBEN_ERR_016`), `duplicados_marcados: 0`, `RQS: 0.95`, Acceptance `ACEPTARIA`, 19 aceptados; rc=0 | Igual a spec §15 (`spec-sisben.md:374-382`): 21 / 1 / 20 / 1 / 0 / 0.95 / acepta 19 |
| Fidelidad al Anexo A | `diff` entre el bloque YAML de `spec-sisben.md` y el insumo | Solo difieren las 23 líneas de comentario de la cabecera | Fiel |
| Ejemplo de la skill frente a la spec | `diff` YAML normalizado entre `edf-validation/assets/examples/sisben/source-spec.sisben.yaml` y el insumo | Difieren `file_pattern`, `regex`, los prefijos RT/EP, el campo 1 (código, obligatoriedad, longitud y tipo), el catálogo del campo 2, … | Evidencia del defecto 2 |
| Muestra | Lectura y `file` | 22 líneas (header + 21), ASCII, 22 columnas; línea 21 con `Grupo = Z`, línea 22 con tipo `2` | Igual a spec §15 |
| Epoch de HU §5.3 | Cálculo con `zoneinfo` `America/Bogota` | `1768021200000`, `1769835600000` | Correctos |
| Fuentes | `diff -q` contra `expedientes/sisben/fuentes/` del framework | Sin diferencias | — |
| Código del motor (PA-001) | `grep` en `Motor-Oxigen/` de `checkFileTag`, `fileTag`, `voidIndicator`, `inferDelete` | El borrado inferido V2 está en Synthesis (desactivado por defecto) y `inferDelete` en Acceptance (contextual override). En State Calculation no hay filtro por `voidIndicator` | Sustenta §3 y AUD-E0-07 |

No se ejecutaron git, mvn, java ni PowerShell. Este es el único archivo que escribió la auditoría.

## 6. Re-auditoría (ronda 2)

Fecha: 2026-11-24. Revisé ART-001 1.1.0, ART-002, ART-003 1.1.0, ART-004 1.1.0, ART-010 y `estado-ingesta.json` (`preguntas_abiertas[]`, `riesgos[]`).

### 6.1 Estado de los hallazgos de la ronda 1

| Hallazgo | Estado | Evidencia |
|---|---|---|
| AUD-E0-01 | **Resuelto** | PA-001 está resuelta en el expediente (BIT-0009) con la respuesta recomendada. La ficha cita R-26, §5.6 y HU §6 de forma literal. Las alternativas (a) y (b) quedan descartadas «sin versión nueva de HU/spec» (`contexto-ingesta.md` §6). Se abrió PA-002 con `etapa_afectada: state_calculation` y alternativas compatibles con las fuentes (c1 y d) |
| AUD-E0-02 | **Resuelto** | PA-002 ya no invoca el patrón 6; se plantea como decisión de la etapa 6 y apunta a `[A:CQ-006]` |
| AUD-E0-03 | **Resuelto** | `dependencias.md`: C-09 queda `[A:PA-002]`, bloqueante solo para `state_calculation`; C-10 y E-05 pasan a «No aplica»; P-01 tiene fecha «antes de aprobar `yaml-skill-statecalc.yaml`»; P-02 queda sin bloqueo. `plan-de-ruta.md`: las filas 1 a 5 dicen «Ninguna» y la fila 6 dice PA-002; §2 y §4 están alineados |
| AUD-E0-04 | **Resuelto** | `contexto-ingesta.md` §4 dice «Ninguna contradicción». La reference queda solo como RG-010, con la aclaración de que no es fuente |
| AUD-E0-05 | **Resuelto** | RG-001 a RG-011 tienen el mismo contenido, nivel y etapa en `contexto-ingesta.md` §7, en `plan-de-ruta.md` §4 y en `riesgos[]`. Las referencias cruzadas también coinciden: `mapeo-campos.md:82` remite a RG-007 (Fec_digitacion futura), D-11 y P-04 a RG-004, y RG-009 a D-07. PA-002 coincide en pregunta, etapa y bloqueo |
| AUD-E0-06 | **Resuelto** | `mapeo-campos.md` fila 19 de las notas: «Lectura conjunta de HU R-08 y spec §10.1…» |

### 6.2 Verificaciones repetidas

- **ART-010:** el `diff` contra el bloque YAML del Anexo A (`spec-sisben.md:388-560`) solo muestra líneas de comentario de la cabecera; el YAML es idéntico. La cabecera nueva (l.20-21) es correcta.
- **`--solo-verificar`:** `source-spec OK (22 campos, 29 códigos)`.
- **`prevalidar_archivo.py --hoy 2026-11-17`:** 20 validados, 1 filtrado, 1 con sev. 1 (`SISBEN_ERR_016`), 0 duplicados, RQS 0.95, aceptaría 19. Es igual a spec §15.
- **ART-002:** las 22 filas de campo siguen completas (15 separadores por fila, sin celdas perdidas). Los cambios se limitan a las notas de las líneas 82, 83 y 85.

### 6.3 Observaciones nuevas (no bloquean)

| ID | Artefacto | Clasificación | Sev. | Evidencia | Corrección sugerida |
|---|---|---|:-:|---|---|
| AUD-E0-16 | ART-001 §6 | artefacto | P2 | La fila de PA-001 dice «Etapa afectada: — (resuelta)» y «Bloqueante: no». En `estado-ingesta.json`, PA-001 tiene `etapa_afectada: analisis`, `bloqueante: true` y `estado: resuelta` | Mostrar los valores registrados («`analisis` · sí · resuelta»), para que el ID signifique lo mismo en los dos lugares |
| AUD-E0-17 | ART-002 | artefacto | P2 | `mapeo-campos.md:6` sigue en «1.0.0», aunque las líneas 82, 83 y 85 cambiaron en la ronda 2 | Subir a 1.0.1 o 1.1.0, como los demás artefactos |
| AUD-E0-18 | Gate de la etapa 0 | framework | P1 (condición) | PA-002 tiene `bloqueante: true`. La letra de `04-contratos-por-skill.md:30` y de `01-flujo-etapas.md:35` («sin preguntas bloqueantes») bloquearía `validation`. Solo la condición 4 de `03-gobierno-riesgo-aprobacion.md:50` («en la etapa N») la limita a la etapa 6. El artefacto lo declara bien (`contexto-ingesta.md`, nota final del checklist) | El Orquestador debe aplicar la condición 4 por `etapa_afectada`, y dejar constancia en la bitácora de que el gate se evalúa así mientras AUD-E0-10 no se corrija en el framework |

No se introdujo nada incorrecto en el contenido factual. Las citas nuevas que revisé son correctas: R-26, §5.6, §6, §10.1, §13, Anexo A y la condición 4.

### 6.4 Dictamen de la ronda 2

| Artefacto | Dictamen |
|---|---|
| ART-001 `contexto-ingesta.md` 1.1.0 | **APROBADO** (corregir AUD-E0-16 de paso) |
| ART-002 `mapeo-campos.md` | **APROBADO** (AUD-E0-17 es cosmético) |
| ART-003 `dependencias.md` 1.1.0 | **APROBADO** |
| ART-004 `plan-de-ruta.md` 1.1.0 | **APROBADO** |
| ART-010 `source-spec-validation.yaml` | **APROBADO** (YAML idéntico al Anexo A) |

**Dictamen global (ronda 2): APROBADO, con una condición.** La etapa 0 puede aprobarse y la transición `analisis` → `validation` puede habilitarse si el Orquestador evalúa el bloqueo de PA-002 por `etapa_afectada` (AUD-E0-18). PA-002 sigue bloqueando la aprobación de `yaml-skill-statecalc.yaml` y la transición `synthesis` → `state_calculation`.
