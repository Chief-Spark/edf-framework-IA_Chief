# Changelog

## 1.9.12 — 2026-11-25

Lote final de la corrida E2E SISBEN (HF-13, 42, 44, 45, 47..55, 65..67, 78, 79; HF-56 queda como propuesta de diseño).

### Lote final de la corrida E2E SISBEN (defectos del framework; `ejecuciones/sisben-e2e/hallazgos-framework.md`)

**edf-validation**
- HF-13: `archivo.nombre` admite `supuestos` (IDs de `decisiones-y-abiertos.md`). El generador escribe `// ASSUMPTION <ID>` junto a `NombreArchivoSpec` (hueco `marcas_supuesto_nombre`, vacío sin la clave: el ejemplo no cambia), `manifest["supuestos"]` lo lista y `check_supuestos_resueltos` lo cruza. Pruebas: `test_hf13_*`.
- HF-42: `references/interpretar-salida.md` ya no fija `customReasonForAutomaticRejectionEnabled = true` (SISBEN histórico): lo fija el insumo de Acceptance.
- HF-54: advertencia en SKILL y checklist sobre la tabla de lookup duplicada si el fork comparte un `oxygen-lookup-inner.conf`.

**edf-acceptance**
- HF-44: el esquema declara la convención `ruta_` de `archivo.claves_de_prueba[].clave`. `verify_spec` falla con una clave `ruta_*` sin separador de directorio; un valor con ruta sin el prefijo da un aviso (no bloquea insumos ya aprobados: el de la corrida da el aviso por `otra_fuente`, AUD-A-19). El ejemplo renombra `otra_fuente` → `ruta_otra_fuente` y se regenera (`SISBENAcceptanceExpressionsSpec.scala`: el nombre de la clave y un comentario). Pruebas: `scripts/tests/test_lote_final.py`.

**edf-curation**
- HF-45: `resumen.json` separa `claves_omitidas_por_severidad` (niveles_no_almacenar) de `claves_omitidas_por_nulas` (omitir_si_nulas).
- HF-48: con `extension_subscriber_relation`, el Spec Scala trae un caso sintético FIJO del respaldo (componente faltante → aceptado + `subscriberRelation:COMPONENTE_FALTANTE`), fuera del fixture; sigue `copia_de` hasta el campo de entrada.
- HF-49: comentario de RF-CUR-10 según `NivelesNoAlmacenar`/`NivelesAnular`, no «severidad 3 / 0» fijo.
- HF-50: cita `runbook-ejecucion.md §2` en lugar de «§fork».
- HF-51: checklist: `outputEventType` = event type de la spec con `ASSUMPTION` o `RESUELTO CUR-PA04`.
- HF-52: la réplica Python sigue a Java: `String.trim` (solo ≤ U+0020, el NBSP se conserva) y regex ASCII (`re.ASCII`). Caso NBSP en las dos suites (`TransformationsSpec.scala.tpl` y `test_lote_final.py`).
- HF-53: `NIVEL_DESCONOCIDO` va con el campo curado (`<destino>:NIVEL_DESCONOCIDO`, uno por destino); `ERROR_INTERNO`, `NIVEL_DESCONOCIDO` y `COMPONENTE_FALTANTE` documentados como motivos defensivos fuera de la lista cerrada de la spec (anotar en PA-007).
- HF-54: misma advertencia de lookup duplicado en SKILL y checklist.
- HF-55: `MANIFEST.json` con `sha256` en cada entrada de `archivos` y de `marcas_sintetico` (que pasa a `{ruta, sha256}`).
- Ejemplo regenerado: MANIFEST (hashes), `resumen.json` (clave nueva vacía), `FieldMapping.scala` (comentario de motivos), `SISBENCurationProcessor.scala` (comentario y `NIVEL_DESCONOCIDO` con el destino), `TransformacionesSpec.scala` (caso NBSP), `SISBENCurationProcessorSpec.scala` (cita del runbook). `curated-events.json` no cambia.

**edf-pinning**
- HF-65: la sección «Modo Colombia» de la plantilla del bloque tiene cabecera propia y una VERIFICAR por ruta HOCON que lee el motor (`oxygen.stage.pinning.paths.*`, `identityMatcher`, `reusePin`, `nearNeighborBlockSize`, `pinningVleRange`, `groupSSR`, `paths.pinnedReferenceDir`, `oxygen.stage.curation.paths.curatedDir`, integración estática con HBase, `*-common-application.conf`; `clusterSplitter` e `INPUT_REF_DIR` con nombre `[PM]`); `[P]` en el mecanismo del colapso.
- HF-66: generador mínimo `scripts/render_bloque_colombia.py --insumo … [--contrato …]`: valida el insumo con `tools/verificar-insumo-pinning.py`, copia literal la sección y rellena sus huecos; reproducible.
- HF-67: `evals/verificar-artefacto.ps1` con `-Modo propio|colombia` y `-PropuestaDir`: `groupSSR` evaluado fuera de comentarios, chequeos de EVAL-PIN-12..14. Equivalente Python: `render_bloque_colombia.py --verificar <bloque> [--dir-propuesta <dir>]`. Pruebas: `tests/test_lote_final_framework.py`.

**edf-synthesis**
- HF-78: `evals/verificar-artefacto.ps1` con `-Modo propio|colombia` (en Colombia: 0 líneas activas y las 11 VERIFICAR, sin exigir la raíz) y chequeo de `"handler": null`; advertencia «nunca `handler: null` en el JSON» en el SKILL (paso 3) y en la plantilla del GDD.
- HF-79: cita del default local de `consistencyCheck` (`oxygen-config-default-local.conf:459-468`; `orchestration :460-464`, `datasetFormat :465-468`) en la plantilla del bloque.

**edf-analisis y transversal**
- HF-42: plantillas de la etapa 0 (`contexto-ingesta`, `mapeo-campos`, `dependencias`, `plan-de-ruta`) sin fila «Estado | PROPUESTO» (HF-32).
- HF-47: la cabecera de un insumo lleva la leyenda literal del steering `05` §3 (`[PM]` = pendiente-manual; lo que propone el modelo es `[P]`); leyenda en `contrato-curation-pinning.md.tpl`.
- HF-79: `precondiciones_bloqueantes` nombra la condición, no su valor del momento (`_cabecera-yaml-skill.yaml.tpl`); `python` → `python3` en los SKILL, references, checklists y LEEME de las skills (no en los hooks: los instaladores reescriben el intérprete, ni en `edf-statecalc`/`edf-orquestador`/`.kiro/specs`, fuera de este lote).

**No corregido en este lote**
- HF-56 (diseño, Orquestador) — propuesta: admitir en el esquema de `etapa` un `ref_aprobacion` por sucesora (`transiciones: [{a: <etapa>, habilitada, ref_aprobacion}]`) y que la regla 7a valide cada una.
  Mientras tanto, documentar en `references/03` que con dos sucesoras la segunda transición solo consta en `aprobaciones[]` y el validador debe buscarla ahí.

## 1.9.11 — 2026-11-25

Correcciones de la auditoría del insumo de State Calculation de la corrida E2E SISBEN (`ejecuciones/sisben-e2e/auditorias/statecalc.md` §3, HF-73 a HF-77). Mismo patrón que Pinning (1.9.8/1.9.9) y Synthesis (1.9.10). No se tocó nada de `ejecuciones/`.

### Herramientas
- **HF-73 (P1, defecto 1). `tools/verificar-artefactos-etapa.py statecalc`.** `select`/`groupBy`/`aggregates[].field` se cruzan con el registro Foundation: el nombre final de cada campo (un `name` `a.b` queda como `b`, `FoundationFunctions.scala:68-71`) y los inyectados; un `aliases[]` pasa con aviso «solo con aliasEnabled = true [PM]» (`FoundationStateCalculator.scala:103-105`). Antes rechazaba la configuración válida con alias y empujaba a un DERIVED con el nombre del alias. Opción nueva `--source-spec <source-spec-curation.yaml>`: un DERIVED cuyo `name` no es clave curada publicada es hallazgo (vale null siempre); un `value` en un DERIVED es aviso. También: `field.X` de un CALCULATED sin campo Foundation (o de un CALCULATED posterior), nombres repetidos en `fields[]` y `name` de un CALCULATED o de una sección con `where` que no sirve como sufijo de una función JavaScript (`QueryFunctions.scala:75-82`, `FoundationFunctions.scala:52-56`: una sección `#sisben` con `where` daba `ARTEFACTOS OK`). La salida imprime avisos `!` que no cambian el código. `verificar_calculation` sigue devolviendo solo hallazgos; `verificar_statecalc` devuelve hallazgos y avisos.
- **HF-74 (P1, defecto 2). `tools/verificar-claves.py`.** Un DERIVED se cruza por `name` (`FoundationFunctions.scala:24-35`; en `campos_a_generar` y el `nombre_en_foundation` de un DERIVED de `mapeo_campos`), no por `value`. Las referencias `field.X` de un CALCULATED se cruzan con los campos Foundation del insumo (DERIVED, no DERIVED declarados antes, alias con aviso) y los inyectados (`pin`, `dataSource`, `subscriber`, `subscriberRelation`, `asOfPeriod`, `parentEventIds`), no con las claves curadas: nueva salida `X NO ES CAMPO FOUNDATION` (rc 1). `field.pin` ya no se marca. Sobre el insumo de la corrida: `Claves publicadas por Curation: 34 · citas revisadas: 119` / `RESULTADO: claves coherentes.` (rc 0; las 4 citas nuevas son los `name` de sus 4 DERIVED).
- **HF-77. `verificar-artefactos-etapa.py statecalc --modo {propio,colombia}`** (por defecto `propio`). Con `--modo colombia`: toda sección habilitada tiene `where` y el `where` lee `dataSource`, `subscriber` o un campo de `--campo-fuente` (repetible); con `--campos-existentes` / `--secciones-existentes` (el foundation / calculation compartido en JSON, o un nombre por línea) ningún campo, alias ni sección repite un nombre existente; sin esas listas, aviso.

### edf-statecalc (HF-75, HF-77)
- **HF-75 (P1, defecto 3).** En un CALCULATED `field` es el registro Foundation (`FoundationStateCalculator.scala:52-63`): `return field.standardizedIdType` sin el DERIVED `standardizedIdType` vale null. El golden `sisben-camino-feliz` (y `-corrida2`) declara los DERIVED `standardizedIdType` (INTEGER) y `standardizedIdNumber` antes de los CALCULATED, como el fixture del motor (`statecalc-foundation-local.json:369-379`); igual los fixtures defectuosos que copian el camino feliz (siguen con un solo defecto). `references/esquema-foundation-calculation.md` §1, `edf-analisis/references/logica-statecalc.md` §5.1, `assets/foundation-fields.json.template` y el paso 2 del SKILL lo explican. `verificar-artefacto.ps1` (solo texto, no se ejecutó) espera en P04 4 campos (2 DERIVED de origen + 2 CALCULATED) y P07 admite los DERIVED; `probar-arnes.ps1` ajusta los patrones. `evals.json` 1.2.0 (EVAL-SC-04, -07).
- **HF-77 (P1, defecto nuevo). Modo Colombia.** `SKILL.md` sección «Modo Colombia» y `references/modo-colombia.md` (nuevo): la ingesta entrega (1) entradas `fields[]` para el foundation compartido sin repetir un `name` ni alias (un nombre duplicado gana para todas las fuentes, `ContextUtils.scala:15-19`), (2) secciones con `where` de filtro por fuente (sin él MOST_RECENT_VALUE toma el registro más reciente de cualquier fuente, `MostRecentValue.scala:20-24`; el `where` depende de `[A:OQ-TR-2]`) y `name` sin `#`, y (3) la lista de verificación del fork, con el **modo delta** (`SparkStateCalculator.scala:187-188, 207-213, 246-271`: un PIN sin cambios conserva su XPM anterior). Plantillas de Foundation y Calculation con la sección «MODO COLOMBIA». `esquema-foundation-calculation.md` §2.2 y `logica-statecalc.md` §5.6: con `groupBy`, un `where` vacío da `[]`, no `null`. EVAL-SC-14 nuevo.
- Se quitó la afirmación «para SISBEN ya se confirmó que el passthrough resuelve el caso» y la cita a `Diseño/templates-scala/statecalc/README.md` (no existe; contradice HU SISBEN R-26) del SKILL (descripción y «Qué nunca hace») y de `decisiones-pendientes.md` §5; el ejemplo histórico de `edf-analisis` la marca SUPERADO. El caso de 25 campos queda rotulado como ejemplo histórico del piloto.

### Orquestador y edf-analisis (HF-77)
- `templates/estado-ingesta.seed.json` trae el modo Colombia de State Calculation: ART-071 = entradas `fields[]` para el foundation compartido sin nombres existentes; ART-072 = secciones con `where` de filtro por fuente y `name` sin `#` (riesgo `critico`); ART-073 = CA1 en el XPM compartido con la lista de verificación del fork como precondición (nombres, modo delta, `aliasEnabled`, JDK/Nashorn). `references/04-contratos-por-skill.md`, etapa 6: tabla por modo y nombres de modo propio; DIF-4 ya no dice «passthrough confirmado para SISBEN». `prompts/00-iniciar-ingesta.md`, paso 2: «Modo de State Calculation» (si las fuentes no lo dicen, pregunta bloqueante de `state_calculation`).
- `edf-analisis/assets/templates/yaml-skill-statecalc.yaml.tpl` declara `modo_colombia` (`aplica`, `filtro_fuente`, `nombres_existentes_verificados`, `modo_delta`) y anota que un DERIVED se lee por `name` y que `field.X` necesita su DERIVED. `logica-statecalc.md` §5.6 remite al modo Colombia.

### Steering (HF-76, P2, defecto 4)
- `07` §5.7: el patrón de flag por ingesta (`isSisben`) queda **condicionado a `[A:OQ-TR-2]`** (solo filtra dentro de un `where` Nashorn); una configuración solo DERIVED no depende de Nashorn; el passthrough no basta para R-26. `migrate.md:12-16` (JDK 11 del motor InnerSource) figura como antecedente, no como cierre: faltan el JDK del runtime del fork de Colombia y la decisión de Experian. `06` §6 #3 lo repite.

### Pruebas
- `tests/test_verificar_artefactos_etapa.py` (`test_hf73_*`, `test_hf74_*`, `test_hf75_*`, `test_hf77_*`), `tests/test_verificar_claves.py` (`test_hf74_*`, `test_hf75_*`; `test_statecalc_derived_y_scripts` reescrita con la semántica nueva) y `tests/test_regresion_e2e_statecalc.py` (nuevo: textos, fixtures, `.ps1` como texto, `evals.json`, semilla contra el esquema y validador con una etapa `state_calculation` de modo Colombia aprobada, 04, 00 y la plantilla del insumo).

## 1.9.10 — 2026-11-25

Correcciones de la auditoría del insumo de Synthesis de la corrida E2E SISBEN (`ejecuciones/sisben-e2e/auditorias/synthesis.md` §2-§3, HF-68 a HF-72). Mismo patrón que Pinning en 1.9.8/1.9.9 (HF-57, HF-59, HF-63). No se tocó nada de `ejecuciones/`.

### edf-synthesis y Orquestador
- **HF-68 (P1, defecto 1). Modo Colombia de Synthesis.** Una corrida de Synthesis lee un solo GDD, un solo diccionario y un solo `customRulesFile` y procesa todo el HC de Colombia (`SparkConsistencyChecker.scala:241-245`); el SKILL y la plantilla suponían un bloque por ingesta. Ahora:
  - `SKILL.md`, sección nueva «Modo Colombia»: no se escribe ninguna clave compartida; la entrega es (1) las entradas del GDD de la ingesta (ART-061), que se integran en el GDD compartido sin duplicar ni modificar `name` existentes, (2) el bloque solo de comentarios con una línea `VERIFICAR <ruta HOCON completa>` por clave (ART-062), incluidas las lecturas `.get` sin valor por defecto (AUD-S-03), y (3) la verificación de las reglas compartidas (AUD-S-02). `references/decisiones-pendientes.md` §6.
  - `assets/consistency-application-block.conf.template`: sección «Modo Colombia» (todo comentario) con 11 claves `VERIFICAR oxygen.stage.consistencyCheck.*` y sección «Fuera de modo Colombia» con el bloque propio.
  - Orquestador: `templates/estado-ingesta.seed.json` trae el modo Colombia (ART-061 = entradas del GDD para el GDD compartido; ART-062 = lista de verificación, obligatorio, riesgo alto); `references/04-contratos-por-skill.md`, etapa 5: tabla de artefactos por modo y qué cambia en modo propio; `prompts/00-iniciar-ingesta.md`, paso 2: «Modo de Synthesis» (si las fuentes no lo dicen, pregunta bloqueante de `synthesis`). `edf-analisis/assets/templates/yaml-skill-synthesis.yaml.tpl` declara `modo_colombia.aplica`.
- **`tools/verificar-artefactos-etapa.py synthesis --modo {propio,colombia}`** (por defecto `propio`, que sigue exigiendo la raíz y `customRulesFile` no nulo). Con `--modo colombia` exige que el bloque sea solo comentarios, sin `{{huecos}}`, con cada clave de `CLAVES_VERIFICAR_COLOMBIA` en una línea `VERIFICAR` con su ruta completa; no exige raíz ni `customRulesFile`. El modo se indica explícitamente, no se detecta por un comentario del bloque. En modo propio, un bloque solo de comentarios da la pista «verifícalo con --modo colombia» (AUD-S-11).

### Semántica de claves (HF-69, P1, defecto 2)
- `eventTypeList` guarda **tipos de archivo** (`listFileTypes`, `SparkConsistencyChecker.scala:46`) y no filtra eventos: solo se usa con `checkFileTagEnabled` para el `voidIndicator` (`:87`, `:119-131`). `dateType` y `checkFileTagEnabled` no agrupan por periodo (el periodo sale de `getPeriod`, `HCUtil.scala:109-124`). Corregido en `edf-analisis/references/logica-synthesis.md` §4.1, `edf-synthesis/SKILL.md` paso 4, `references/convenciones-oxygen-synthesis.md` §2, la plantilla del bloque y la plantilla del insumo, que ya no pre-llena `["{{ingesta}}"]` (queda `""` con la explicación). `evals.json` EVAL-SYN-10 y el fixture Jurad hablan de fileTag.

### Herramientas (HF-70, P1, defecto 5)
- `tools/verificar-claves.py <ingesta-id>` sale con **2** si no existe `expedientes/<id>` o su carpeta `insumos/` en la raíz (antes: «nada que cruzar» con 0, falso verde), y `--dir <carpeta>` inexistente también da 2. «Nada que cruzar» (0) queda solo para una carpeta que existe sin pares. Los dos verificadores documentan `--dir` para corridas fuera de la raíz; `verificar-insumo-pinning.py` ya salía con 2 y ahora lo dice con la pista de `--dir`.

### GDD v2 (HF-71, P2, defectos 3 y 4)
- `logica-synthesis.md` §5.1 y `diccionarios-oxygen-synthesis.md` §1: raíz `"version": 2` y tabla de claves de `DictionaryFieldV2` (`DomainDictionaryV2.java:46-56`); v1, `struct` y `singleValued` solo como históricos.
- `campos_a_generar[]` tiene forma: `{name, description, handler}` en la plantilla del insumo. `verificar-claves.py` acepta `nombre_en_gdd` como alias deprecado (con aviso) y avisa de entradas sin nombre. El fixture `yaml-skill-synthesis-jurad-sintetico.yaml` usa `name` + `description`.

### Evals y references (HF-72, P2)
- `evals/evals.json` 1.2.0: EVAL-SYN-04 ya no espera un GDD vacío para SISBEN (spec §12, HU R-25: sus campos van LFP, sin `handler`); EVAL-SYN-13 cubre el modo Colombia. `verificar-artefacto.ps1` (solo texto) ya no presenta `-ExpectEmptyFields` como el caso SISBEN.
- `SKILL.md` y EVAL-SYN-02 ya no citan `Diseño/templates-scala/synthesis/README.md` (no existe): el precedente es la spec de la ingesta §12. El ejemplo SISBEN de `edf-analisis` se rotula histórico en el SKILL y en `LEEME.md`.
- `edf-analisis/references/fuentes/curation-pinning-sisben.md` §6 y §8 llevan la nota «SUPERADO PARA SISBEN (RG-010, HF-72)»: manda la spec.

### Pruebas
- `tests/test_regresion_e2e_synthesis.py` (nuevo: texto de SKILL, 04, 00 y plantillas; la semilla valida contra el esquema y el validador acepta una etapa `synthesis` de modo Colombia aprobada; `evals.json`; references), `tests/test_verificar_artefactos_etapa.py` (`test_hf68_*`: los dos modos, cada clave VERIFICAR, CLI) y `tests/test_verificar_claves.py` (`test_hf70_*`, `test_hf71_*`).

## 1.9.9 — 2026-11-25

Correcciones de la re-auditoría del insumo de Pinning de la corrida E2E SISBEN (`ejecuciones/sisben-e2e/auditorias/pinning.md` §6, AUD-P-14 a AUD-P-16): HF-57/HF-59 no habían llegado al Orquestador.

### Orquestador
- **HF-63 (P1, AUD-P-14). Artefactos de Pinning según el modo.** La semilla, el contrato de la etapa 4 y el prompt 00 exigían en modo Colombia un matcher propio, un `identifying-attributes.json` y un bloque «con reusePIN = false», que `edf-pinning` ya no genera: la etapa no se podía aprobar (regla 7e) o empujaba a generarlos. Ahora:
  - `templates/estado-ingesta.seed.json` trae el modo Colombia: ART-051 (matcher) y ART-052 (`identifying-attributes.json`) con `obligatorio: false` y «no aplica en modo Colombia»; ART-053 es el bloque HOCON de la ingesta sin claves compartidas, con la lista de verificación de las claves compartidas, sin redefinirlas ni escribir `reusePin` (obligatorio, riesgo alto).
  - `references/04-contratos-por-skill.md`, etapa 4: tabla de artefactos por modo (propio / Colombia) y qué cambia el Orquestador en modo propio (ART-051 y ART-052 obligatorios; ART-053 «con reusePin declarado»). `reusePIN` en modo Colombia es el valor de la fuente como dato; no se escribe.
  - `prompts/00-iniciar-ingesta.md`, paso 2: el modo se toma de la spec y la HU (matcher compartido de Colombia → modo Colombia; si no lo dicen, pregunta bloqueante de `pinning`); «ingesta nueva» ya no se traduce en escribir `reusePIN = false` en modo Colombia. `references/01-flujo-etapas.md` §5 dice lo mismo de `reusePIN`.
  - Prueba: `tests/test_regresion_e2e_pinning.py` (`test_hf63_*`: la semilla valida contra el esquema; el validador acepta una etapa `pinning` de modo Colombia aprobada sin matcher propio y la rechaza con 7e si el matcher es obligatorio; 04 y 00 nombran los dos modos).

### tools/verificar-insumo-pinning.py
- **HF-64 (AUD-P-15).** En modo Colombia, `valor_a_escribir` de una clave compartida debe **empezar** por «no se escribe» / «no se redefine»: `"false (no se escribe hasta cerrar PA-009)"` o `"0 (no se redefine)"` ya no pasan. El nombre de la clave se compara sin distinguir mayúsculas (`reusePIN` = `reusePin`). El insumo de la corrida sigue dando «insumo de Pinning OK». Pruebas: `tests/test_verificar_insumo_pinning.py` (`test_hf64_*`). No se revisa todavía `pendientes_de_configuracion` (sus entradas llevan `estado`, no `valor_a_escribir`).

### edf-analisis
- **HF-64 (AUD-P-16).** `assets/templates/yaml-skill-pinning.yaml.tpl`, `modo_colombia.umbral_y_matcher_determinista.estado`: `[A:<ID>]` mientras la pregunta siga abierta y bloquee la etapa; con verificación diferida (`edf-orquestador/references/03` §3e), `[PM] (RG-nnn)` con el riesgo que la registra; `[C]` solo con el código del fork citado. Prueba: `test_hf64_plantilla_distingue_a_de_pm_con_verificacion_diferida`.

## 1.9.8 — 2026-11-25

### Orquestador
- **HF-62. Verificación diferida de hechos fuera del repositorio** (`references/03` §3e): la parte no verificable de una pregunta (p. ej. una clase compartida del fork) pasa a un riesgo alto que bloquea la integración y la certificación, siempre que ningún artefacto dependa del hecho (corrida E2E SISBEN, PA-008). Prueba: `tests/test_regresion_e2e_gobierno.py`.

Correcciones de la auditoría del insumo de Pinning de la corrida E2E SISBEN (`ejecuciones/sisben-e2e/auditorias/pinning.md`, HF-57 a HF-61).

### edf-pinning
- **HF-57 (P1, AUD-P-01).** En modo Colombia la skill **nunca escribe `reusePin`**. La clave es global de la aplicación de Pinning (`SparkPinner.scala:223, 409`): `false` renumera los PIN de todas las fuentes (`:979-985`) y el escenario «persona existente recibe ese PIN» solo se cumple con `true` (`:888-905`). «ingesta nueva → `reusePIN = false`» significa que la ingesta no tiene generación previa propia; cambiar la clave de la aplicación compartida es N4 y se escala. Cambios: SKILL «Modo Colombia», `assets/pinning-application-block.conf.template` (sección «Modo Colombia», solo comentarios de verificación; el valor de la fuente va como dato, `{{REUSE_PIN_VALOR_FUENTE}}`), `edf-analisis/assets/templates/yaml-skill-pinning.yaml.tpl` (`valor_en_fuente` + `valor_a_escribir: "no se escribe en modo Colombia …"`, ya no un booleano) y `edf-analisis/references/logica-pinning.md` §4.1 y §8.0 (nuevo).
- **HF-58 (AUD-P-09).** Advertencia del colapso silencioso: con `ISIdentityMatcher` o la lista de ejemplo (`nationalId`, `firstName`, `cellPhone`) y eventos de Colombia, todos los atributos valen `""` y todas las identidades se agrupan en una antes de compararse (`SparkPinner.scala:1756-1770`; `:676`, `:719-723`). Nunca se heredan como «arranque» en modo Colombia. En el SKILL, la plantilla del bloque, `logica-pinning.md` §9 y la plantilla del insumo.
- **HF-59 (AUD-P-10, -11).** `SKILL.md` ya no se contradice: en modo Colombia no se redefine **ninguna** clave compartida (`identifyingAttributesFile`, `identityMatcher`, `reusePin`, `domainDictionaryFile`, `clusterSplitter`, `nearNeighborBlockSize`, `pinningVleRange`, `groupSSR`); solo se verifica. `evals/evals.json` 1.2.0 agrega EVAL-PIN-12 (reusePin), EVAL-PIN-13 (colapso) y EVAL-PIN-14 (claves compartidas), con el fixture `evals/fixtures/yaml-skill-pinning-colombia-sintetico.yaml`. `references/decisiones-pendientes.md` registra HF-57 a HF-59.

### Steering y edf-analisis
- **HF-60.** Steering `11` §1 y §6 #2: el lector está en `oxygen-pinning` del workspace (`PinningIOUtils.scala:23-46`, `OxygenIOReaderUtils.scala:72-75`, con la rama `INPUT_REF_DIR`); queda `[A:oxygen-core]` lo que vive en oxygen-core (`TaggedWritableEvent.toTaggedCuratedEvent`, construcción de `TaggedIdentity`). `yaml-skill-pinning.yaml.tpl` agrega `modo_colombia.umbral_y_matcher_determinista` (texto de la spec, lectura del matcher, diferencia, estado) y `reglas_matching_por_campo.aplica`. Modo 2 de `edf-analisis` documenta el verificador nuevo.
- **HF-61 (AUD-P-05).** El framework no citaba `EventPinner.scala:64-65` (solo los insumos de la corrida, que no se tocan). Steering `11` §1 y §3 dejan dicho que `EventPinner` no se usa en el batch y que la evidencia de «un `null` lanza» es `ISIdentityMatcher.scala:46-54`, solo para el matcher de referencia.

### Herramientas
- **Nuevo `tools/verificar-insumo-pinning.py` (HF-60).** `python3 tools/verificar-insumo-pinning.py <ingesta-id>` (o `--dir <carpeta>`, o `--archivo <yaml> [--contrato <md>]`). Errores: secciones de la plantilla, estado/aprobador en la cabecera, `valor_resultante` con slots no `[C]` o con `[A:*]`, en modo Colombia una clave compartida (también `reusePin`) con valor listo para escribir, el matcher o la lista de referencia, lista o pesos regenerados, y claves de identidad ausentes de la tabla §1 del contrato. Avisos: contrato ausente, claves del contrato no citadas, `reusePin` sin `valor_en_fuente`, citas de `EventPinner`. Sobre el insumo de la corrida reporta exactamente AUD-P-01.

### Pruebas
- `tests/test_regresion_e2e_pinning.py` (texto y plantillas de HF-57 a HF-61; `evals.json` válido con los casos nuevos) y `tests/test_verificar_insumo_pinning.py` (herramienta nueva).

## 1.9.7 — 2026-11-25

Correcciones de la corrida E2E SISBEN: lote de cierre (commit a205649) y Curation (commit 955bed3).

### Steering
- **HF-46. `09` §5.1 #2 (artefacto que depende de `[A:<ID>]`) se mide por la `etapa_afectada` de la pregunta**, como la condición 4 desde 1.9.1; un `[A:<ID>]` sin pregunta registrada bloquea siempre (corrida E2E SISBEN, INT-044). Prueba: `tests/test_regresion_e2e_gobierno.py`.

### Orquestador
- **HF-43. §3d no decía qué estado tiene el artefacto durante la revisión.** Sigue `aprobado` con el hash anterior hasta la nueva aprobación (regla 7e). Prueba: `tests/test_regresion_e2e_gobierno.py`.

### Corregido (lote de cierre de la corrida E2E SISBEN: HF-26, HF-27, HF-29 a HF-33)

- **HF-26 (`edf-acceptance`).** El schema admite `validatedRecordJson: null`, que es lo que emite Validation en los
  eventos marcador (`SparkValidator.scala:303`). La plantilla del fixture ya lo escribía como `null` (filtro `json`);
  `SKILL.md` lo documenta en el contrato «Marcadores». Los casos existentes no cambian. Pruebas: `test_hf26_*`
  (`scripts/tests/test_correcciones_197.py`).
- **HF-27 (`edf-validation`).** `references/interpretar-salida.md` tiene una sección nueva, «Comparar dos corridas»:
  se excluyen `eventId` y `keys` (UUID de los registros `SIN_CLAVE`) y se compara por `lineage.recordHash`
  (multiconjunto). Prueba: `tests/test_regresion_e2e_validation.py::test_hf27_*`.
- **HF-29 (`edf-acceptance`).** La tabla de supuestos de `decisiones-y-abiertos.md` es un catálogo genérico y ya no
  cita datos de un fixture («900005», «5 directorios»). El checklist pide declarar en la entrega los supuestos que
  imprime el generador para esa spec. Se ajustó `test_supuestos_de_la_spec_iguales_a_los_del_documento` y hay
  pruebas nuevas, `test_hf29_*`, que cubren una spec sin S-ACC-02 y con texto propio. El ejemplo usa el texto del
  catálogo.
- **HF-30 (`edf-acceptance`).** Los restos del SISBEN histórico quedan rotulados «ejemplo histórico (no es fuente)»
  en `inventario-plantillas.md`, `dsl-config-reference.md` §4 y `patrones.md`. F-07 a F-09 remiten a la spec de la
  ingesta. Prueba: `tests/test_regresion_e2e_acceptance.py::test_hf30_*`.
- **HF-31 (`edf-acceptance`).** El checklist acepta documentos sintéticos declarados como tales por la spec o con
  prefijo `SINT`, y mantiene `eventId` `SINT-*`. Prueba: `test_hf31_*`.
- **HF-32 (`edf-analisis`).** La cabecera de los insumos no lleva estado ni aprobador: el estado vive solo en
  `estado-ingesta.json`. Cambios: SKILL.md Modo 2, `_cabecera-yaml-skill.yaml.tpl`, `contrato-curation-pinning.md.tpl`
  y `yaml-skill-synthesis.yaml.tpl`. Los ejemplos quedan rotulados como históricos (`LEEME.md` y un comentario en la
  cabecera). Prueba: `tests/test_regresion_e2e_analisis.py`.
- **HF-33 (`edf-acceptance`, AUD-A-16, -17).** ACC-TPL-05 construye `AcceptanceCondition` con el quinto argumento
  `result = false` explícito y tiene una prueba nueva del `filePattern` con `archivo.claves_de_prueba`, que usa el
  mismo recorte de extensión del motor (`FilenameUtils.removeExtension`, dos veces con `errorIn`) y `String.matches`.
  Se regeneró el ejemplo `assets/examples/sisben/generated/.../SISBENAcceptanceExpressionsSpec.scala`. Pruebas:
  `test_hf33_*`.

### edf-curation y tools/verificar-claves.py (auditoría de Curation, corrida E2E SISBEN)

#### Corregido
- **HF-34 (AUD-C-01, P1):** la transformación `original` ya no recorta espacios. Publica el valor recibido tal cual y solo da nulo si falta o es `null`. Por eso el escenario de HU SISBEN §5.3 da `numeroDocumentoOriginal = " 0001000000101"`. Hay `Transformaciones.original` y `<F>AcceptedRecord.crudo` en Scala, y `original()` en `curation_reference.py`. Las demás transformaciones siguen recortando. La prueba Scala generada ahora espera el valor con su espacio.
- **HF-35 (AUD-C-05):** el override `subscriberRelation` descarta los `errorCode` nulos, como `curateEvent`, y hace toda la extracción dentro del `try`, así que ya no lanza. Además reutiliza el registro que acaba de curar `curateEvent` (memoria por hilo, `relacionDesde`) y no cura cada registro dos veces.
- **HF-37 (AUD-C-08):** `tools/verificar-claves.py` lee del contrato solo la tabla de §1 (desde `## 1` hasta el siguiente `## `; sin esa sección, la primera tabla). No toma en cuenta `subscriber`, `subscriberRelation` ni el resto de los campos inyectados. `parametros.campo_advertencias` cuenta como clave publicada.
- **HF-39 (AUD-C-10):** `MANIFEST.json` y las 11 plantillas firman `edf-curation`, no `edf-curation-stage`.
- **HF-40 (AUD-C-04, -07):** en el fixture generado, `lineage.fileAsOfDate` es el último día del periodo AAAAMM a las 00:00 en `zona_asofdate_etapa_anterior` y `fileTag` sale de `tipo_por_prefijo`, como los emite Validation. Si el nombre no coincide quedan en `0` y `""`. `references/especificacion-sisben.md`, `dsl-config-reference.md`, el SKILL y `curation-block.conf.tpl` rotulan las referencias del SISBEN del piloto como históricas, y el SKILL ya no las cita como fuente.
- **HF-41 (AUD-C-13):** el efecto «Vaciado del campo con preservación» (`<campo>_original`) queda documentado como no soportado por `edf-curation`, en el SKILL, en `references/alcance-y-glosario.md` y en la tabla de efectos de steering `07` §5.1.

#### Nuevo
- **HF-36 (AUD-C-06):** clave opcional `supuestos_resueltos: [{id, fuente}]`, el mismo mecanismo que `edf-validation` 1.9.2 (AUD-V-08). Cada marca `ASSUMPTION <ID>` sale como `RESUELTO <ID> (<fuente>)`, sin su «; confirmar …» o «; verificar …», y `MANIFEST.json["supuestos_resueltos"]` lista los IDs con su fuente. La generación falla si el ID no tiene marca en la salida, si se repite o si es `C-CUR-02`. Las marcas de las plantillas se normalizaron a `ASSUMPTION <ID>: …`. Sin la clave, nada cambia.
- **HF-38 (AUD-C-09):** `homologacion_tipo_documento.tabla` admite un valor vacío o `null` para un código que esté en `pendientes` (p. ej. `"7": null`). Si el código no está en `pendientes`, la generación falla. El CSV sintético trae la fila con `cod_experian` vacío. El comportamiento no cambia: ese código no entra a la tabla embebida ni se consulta en el lookup.

#### Ejemplo regenerado
- `assets/examples/sisben/generated` y `expected`: cambian por la firma de la skill, el texto de las marcas, el código nuevo (HF-34, HF-35) y las pruebas Scala nuevas. En `fileAsOfDate`/`fileTag` del fixture cambian por HF-40. El CSV 202 trae la fila `7` porque el ejemplo declara ahora `"7": null` (HF-38). Los `curatedRecordJson` no cambian.

#### Pruebas
- `edf-curation/scripts/tests/test_correcciones_197.py` (HF-34, HF-35, HF-36, HF-38, HF-39, HF-40), `tests/test_regresion_e2e_curation.py` (texto de HF-40 y HF-41, claves nuevas y el escenario cur-0004 de la corrida E2E) y cuatro pruebas nuevas de HF-37 en `tests/test_verificar_claves.py`.

## 1.9.6 — 2026-11-25

### Corregido
- **HF-28 (AUD-A-14). Comentario engañoso sobre `customReasonForAutomaticRejectionEnabled`** en `edf-acceptance/assets/templates/acceptance-block.conf.tpl`: ahora explica qué hace con `true` y con `false` (default del motor). Ejemplo de referencia regenerado. Prueba: `test_hf28_comentario_e4_no_engana`.

## 1.9.5 — 2026-11-25


### Orquestador
- **HF-25. Revisión de artefactos de una etapa ya aprobada** (corrida E2E SISBEN: las correcciones HF-16..18 cambian código de Validation ya aprobado). `references/03` §3d: regenerar, re-auditar por diferencia y nueva aprobación `tipo: artefacto` con hashes nuevos; la etapa no se desaprueba si no cambian insumo, alcance ni riesgo. Prueba: `tests/test_regresion_e2e_gobierno.py`.
Defectos del framework que encontró la corrida E2E SISBEN en la etapa Acceptance (`ejecuciones/sisben-e2e/auditorias/acceptance.md` §2-§3) y el lote pendiente de la re-auditoría de Validation (`validation.md` §7). Las claves nuevas son opcionales y la clave vieja sigue aceptada: el insumo de Validation de la corrida sigue dando `source-spec OK (22 campos, 29 códigos)` y el de Acceptance genera con `--audit` (rc 0) y simula 9/9. Las propuestas de `ejecuciones/` no se regeneraron.

### Corregido (`edf-acceptance`)
- **HF-19 (corrida E2E SISBEN, AUD-A-10 / defecto 1). La skill presentaba otra versión de SISBEN como «SISBEN».** La columna de la tabla de expresiones del `SKILL.md` pasa a «Ejemplo de forma (no es fuente)» y dice que los valores salen de la HU y la spec de la ingesta. `references/especificacion-sisben.md` queda rotulada «EJEMPLO HISTÓRICO» (como AUD-V-13 en Validation), ya no fija precedencias y el SKILL no la cita como fuente. Se quitó «prellenado» de la descripción. Prueba: `tests/test_regresion_e2e_acceptance.py`.
- **HF-20 (defecto 2).** Plantillas, `manifest.yaml`, el schema, `render_templates.py` y el ejemplo firman `edf-acceptance` (antes `edf-acceptance-stage`), como AUD-V-10. Prueba: `test_hf20_*`.
- **HF-21 (defecto 3).** El schema genérico exigía `expresiones_propuestas_sisben`. La clave es `expresiones_propuestas`; la vieja se acepta como alias deprecado (una de las dos, no ambas) y el generador imprime `aviso  expresiones_propuestas_sisben está deprecada…`. Prueba: `test_hf21_*`.
- **HF-22 (defecto 4).** Con `severidades.habilitado: false` basta `{habilitado, estado}`; el resto se exige solo al habilitarla (`if/then`, como `contextual_override`). Prueba: `test_hf22_*`.
- **HF-23 (AUD-A-01 / defecto 5). Umbral alternativo.** Clave opcional `fixture.umbrales_alternativos: [{umbral, casos: [{id, registros_validos, registros_totales, esperado: aceptado|rechazado, reason_code_esperado?}]}]`. ACC-TPL-05 genera la prueba «umbral alternativo …», que evalúa `accept` y las condiciones con el umbral sustituido sobre registros sintéticos con QS binario, sin otra configuración desplegable. `simular_decision.py` evalúa los mismos casos. La regla de una sola escala de la configuración no cambia. El generador exige que el umbral esté en [0, 1], sea distinto de `calidad.umbral` y se pueda sustituir, y que `registros_validos <= registros_totales`. `reason_code_esperado` es obligatorio con `rechazado`. Sin la clave, la salida es igual. Prueba: `test_hf23_*` (HU §5.2: con 0.95, 19/20 se acepta y 47/50 se rechaza con `SISBFA00001`).
- **HF-24 (AUD-A-08, -09).** `simular_decision.py` compara cada archivo con `decision_esperada`, `reason_code_esperado` y el `esperado_registro` de cada evento: agrega `coincide`/`diferencias` a la salida JSON, imprime un resumen en stderr y sale con 1 si algo no coincide. El comentario de ACC-TPL-05 ya no dice que con `customReasonForAutomaticRejectionEnabled` el motor siempre registra el `reasonCode`: lo registra solo si alguna condición de `reject` es verdadera sobre los eventos del marcador. Prueba: `test_hf24_*`.

### Corregido (`edf-validation`)
- **HF-16 (corrida E2E SISBEN, AUD-V-27).** Sin tipo o sin número, la clave de duplicado era el contenido de la fila, así que dos filas idénticas sin número se agrupaban (spec §6, VAL-SP03). Ahora es única por registro: `claveDuplicado(spec, valores, unico)` y la sobrecarga del processor con un UUID (el motor la calcula una vez por registro), y en la réplica el número de línea. Pruebas: Scala en `FieldValidatorsSpec` y `<Fuente>ValidationProcessorSpec`; Python `test_hf16_dos_filas_identicas_sin_numero_no_se_agrupan`.
- **HF-17 (AUD-V-28).** Ninguna prueba aislaba RA-02. `nombres_de_prueba` deriva ahora un nombre que viola solo RA-02, con offset adelantado (`+1400`, el 1.º del mes siguiente a las 00:00:00 y el reloj en el último día del mes de referencia). Cada prueba generada del nombre comprueba además que sin su regla el nombre es válido. `FieldValidatorsSpec` prueba las tres reglas aisladas. Pruebas: `test_hf17_*` (generador y réplica, incluido `SISBEN_EXPERIAN_202612.20261201000000+1400.txt` con reloj 2026-11-30).
- **HF-18 (AUD-V-29).** `Campos.separar` y `split_row` quitan solo el `\r` final de una línea CRLF antes de partir. Con `encoding`, el motor parte el archivo por `"\n"` (`SparkValidator.scala:503`) y dejaba el `\r` en el último campo de `validatedRecordJson`. Pruebas: Scala en `FieldValidatorsSpec`; Python `test_hf18_*`, donde CRLF da lo mismo que LF.

### Pruebas
- Acceptance 146 + 1 omitida (antes 127 + 1), 147 con `pyhocon`. Validation 220 + 7 omitidas (antes 209 + 7), 227 con `pyhocon`. Curation 106 + 2, sin cambios. Herramientas 126 (antes 123). `auditar-skills.py`: 0 silencios y 0 obsoletas.
- Ejemplos regenerados. En Acceptance (`assets/examples/sisben/generated/`) cambian la firma de las cabeceras (HF-20) y el comentario del Spec (AUD-A-09); `expected-output.yaml` no cambia. En Validation cambian `FieldValidators.scala` (HF-16, HF-18), `FieldValidatorsSpec.scala` (pruebas de HF-16, -17 y -18), `SISBENValidationProcessorSpec.scala` (prueba de HF-16) y los hashes de `manifest.json`; `resumen-prevalidacion.json` no cambia.

### Sin corregir aquí
- AUD-A-02..07 y AUD-A-11 son del insumo o del expediente, AUD-A-12 es del motor y PA-006 la decide el Desarrollador. Para cubrir AUD-A-01 en la corrida, el insumo debe declarar `fixture.umbrales_alternativos` y la propuesta debe regenerarse.
- La compilación de los Scala generados sigue sin certificar (F-01).

## 1.9.4 — 2026-11-24

### Corregido
- **HF-14. La suite de `edf-validation` dependía del insumo de la corrida E2E.** `test_insumo_e2e_sigue_verificando_y_acepta_las_claves_nuevas` fallaba con `KeyError: 'valores'` en cuanto el insumo pasó a la v2. Ahora sirve con v1 y v2.
- **HF-15. Supuesto resuelto que seguía pidiendo confirmación.** Al marcar `RESUELTO <ID>`, `resolver_supuestos` quita el «; confirmar …» de la misma línea (lo dejaba en `lookups-<fuente>.conf`, F-12). Prueba: `test_hf15_supuesto_resuelto_no_pide_confirmacion`.

## 1.9.3 — 2026-11-24

### Corregido
- **HF-12. Revisión de un insumo ya aprobado** (corrida E2E SISBEN, Validation). El framework no decía qué hacer cuando el insumo de una etapa que ya empezó tiene que cambiar: el validador (regla 7b) rechazaba el expediente y no había procedimiento. `edf-orquestador/references/03-gobierno-riesgo-aprobacion.md` §3c lo define: el insumo vuelve a `propuesto`, se suspende la transición y la etapa siguiente vuelve a `no_iniciada`, se re-aprueba el insumo y luego la transición. Prueba: `tests/test_regresion_e2e_gobierno.py`.

## 1.9.2 — 2026-11-24

Defectos del framework que encontró la auditoría independiente de la etapa Validation de la corrida E2E SISBEN (`ejecuciones/sisben-e2e/auditorias/validation.md`, dictamen RECHAZADO). Las claves nuevas del source-spec son opcionales: el insumo actual de la corrida sigue dando `source-spec OK (22 campos, 29 códigos)` y su prevalidación sigue igual a la spec §15. Para que la propuesta cambie hay que regenerarla.

### Corregido (P0)
- **AUD-V-01. `validate[]` y `metrics[]` sin `filePattern`** (corrida E2E SISBEN, AUD-V-01). El motor elige cada elemento con `data.filePattern.get` (`SparkValidator.scala:1436-1449`, `:955`): sin la clave fallaba en cada archivo. `validation-block.conf.tpl` lo emite en `validate[0]` y `metrics[0]`, `dsl-config-reference.md` lo marca obligatorio y `verificar_hocon.py` falla si falta (la propuesta rechazada ahora da «HOCON con problemas»).

### Corregido (P1)
- **AUD-V-02. Reglas de fecha del nombre de archivo.** Claves opcionales en `archivo.nombre`: `periodo_no_futuro`, `recepcion_no_futura` y `recepcion_desde_inicio_periodo`. Usan los grupos `periodo`, `marca_tiempo` y `offset` del regex y, si no se cumplen, dan `codigos_especiales.nombre_archivo`. Capa A: `NombreArchivoSpec` con las tres banderas (por defecto `false`) y `RecordValidator.validarNombre(spec, ruta, ahora)`. El processor usa `Instant.now()`, `prevalidar_archivo.py` usa `--hoy` (fin del día en la zona de la fuente) y las pruebas generadas usan `fixture.fecha_referencia`. Se genera una prueba por regla activa con un nombre derivado del fixture, y el generador comprueba que el fixture cumpla las reglas.
- **AUD-V-03. «Solo ceros» sobre el valor normalizado.** Nuevo tipo de regla `solo_ceros` (`ReglaEntreCampos.SoloCeros`). Da el código si el valor sin espacios no está vacío y es solo `0`: 16 ceros y `00 00` ahora lo dan. `valor_con_codigo_propio` no cambia.
- **AUD-V-05. `validatedRecordJson` con el valor recibido.** `comoMapa` usa `Campos.crudo`, sin trim. La validación sigue con `Campos.valor`.
- **AUD-V-06. Lookups con `lookupHome`.** `file = ${oxygen.application.paths.lookupHome}"/<archivo>"`, la forma de todos los confs del motor. `catalogos[].archivo` es una ruta relativa, y una absoluta o con `..` se rechaza. `verificar_hocon.py` resuelve la sustitución con un valor sintético. Cómo resuelve oxygen-core un nombre relativo sigue sin verificar: no está en el repo.

### Corregido (P2)
- **AUD-V-08.** Clave opcional `supuestos_resueltos: [{id, fuente}]`. Si la spec de la ingesta ya fija un valor, la marca sale como `RESUELTO <ID> (<fuente>)` y el ID no queda en `manifest["supuestos"]`. Se reescribieron sin «provisional» ni «inferido» las marcas F-11, VAL-PA04 y VAL-SP07.
- **AUD-V-09 (parte framework).** `fixture.casos[].esperado.grupo_duplicado` (una prueba por grupo, por ejemplo con ceros a la izquierda), `fixture.nombres_invalidos` (prueba generada y verificada) y prueba del header con los nombres fuente.
- **AUD-V-10.** `manifest.skill` y las cabeceras dicen `edf-validation`.
- **AUD-V-11.** La regla dura 7, el checklist §1 y el Paso 1 mandan las discrepancias de una ingesta a la entrega al Orquestador, nunca a `.kiro/`.
- **AUD-V-12.** El checklist tiene una nota vigente del generador Python. Las notas Java y de la corrida priorizada quedan rotuladas como históricas, y ADR-04 como superado.
- **AUD-V-13.** `especificacion-sisben.md` queda rotulada «ejemplo histórico» y el Paso 1 ya no la cita como fuente. Se actualizaron `interpretar-salida.md`, la fila F-08 y el runbook («7 tablas»).
- **AUD-V-15.** `07-contratos-frontera.md` §4 dice «quality score por registro» y §5.3 dice menores = tipos `1` y `2` del archivo (la spec de la ingesta manda). Mismo ajuste en `02-lexico-edf.md`.
- **AUD-V-16.** `prevalidar_archivo.py` valida el offset como `ZoneOffset.of`: `-2500` se rechaza.
- **AUD-V-17.** `listDuplicateEvents` reconstruye `TaggedValidatedEvent` con los 17 argumentos del motor (`Validator.scala:208-210`): se conservan `inferredAsDeleted` y `fileTag`. Compilación no certificada (F-01).
- **AUD-V-19.** `codigos-error-<fuente>.csv` se genera en `docs/`, fuera de la carpeta que se pega en el fork, y lista `OxygenDuplicate`.
- **AUD-V-21.** El inner se llama `validation-common-application-inner.conf` también en `10-piloto-sisben.md`.

### Pruebas
- Validation: 215 (antes 165 con `pyhocon`). Sin `pyhocon`: 208 pasan y 7 se omiten. Pruebas nuevas en `test_render_templates.py`, `test_prevalidar_archivo.py` y `test_verificar_hocon.py`. El ejemplo `assets/examples/sisben/generated/` se regeneró: cambian las cabeceras, el HOCON (`filePattern` y `lookupHome`), la Capa A, el evento, el processor, las pruebas, los hashes y la ruta `docs/codigos-error-sisben.csv`. `resumen-prevalidacion.json` no cambia.
- `tests/test_regresion_e2e_validation.py` (nuevo, 7 pruebas): las correcciones de texto. Herramientas: 121 (antes 114). Acceptance 127 y Curation 106, sin cambios. `auditar-skills.py`: 0 silencios y 0 obsoletas.

### Sin corregir aquí
- AUD-V-04, -07, -14, -18 y -20 son de la spec o del insumo y los decide el PO. AUD-V-09 también tiene una parte de insumo: los casos que falten en el Anexo A.
- No se regeneró la propuesta de `ejecuciones/`. Hay que regenerarla con el insumo actualizado.

## 1.9.1 — 2026-11-24

Cinco defectos del framework que encontró la etapa 0 de la corrida E2E SISBEN y que confirmó un auditor independiente (AUD-E0-10, -11, -13, -14 y -15).

### Corregido (P1)
- **AUD-E0-10. El bloqueo era global y no por etapa.** `edf-analisis/references/deteccion-ambiguedades.md` §4 trataba como bloqueante de la etapa 0 toda pregunta que cambiara «ningún insumo de etapa», y el gate de `edf-orquestador/references/04-contratos-por-skill.md` (y `01-flujo-etapas.md`) no distinguía etapa. Ahora el bloqueo se mide por `etapa_afectada`: una pregunta bloqueante detiene solo su etapa (el insumo, la generación, la aprobación y la transición hacia ella). El gate de la etapa 0 considera solo `etapa_afectada` ∈ {`analisis`, `validation`}. Una pregunta sin `etapa_afectada` cuenta para todas. Se alinearon la condición 4 de `03-gobierno-riesgo-aprobacion.md` («`etapa_afectada` N o N+1»), la plantilla `estado-ingesta.template.md` y el Paso 0 de `edf-analisis/SKILL.md`. `tools/validar-expediente.py`, regla 5: antes contaba las preguntas listadas en cada etapa y solo al aprobarla. Ahora cuenta las bloqueantes abiertas por `etapa_afectada` y revisa también la transición N → N+1 (preguntas de N o de N+1).
- **AUD-E0-11. Las skills mandaban partir del ejemplo SISBEN.** `edf-validation/SKILL.md` («Modo SISBEN pre-llenado») y `edf-analisis/SKILL.md` («Parte del ejemplo SISBEN de la etapa») contradecían `00-framework-edf.md` §6. El ejemplo difiere de la spec 1.0.2 (prefijos RT/EP, `SISBEN_ERR_001`, catálogo). Ahora el ejemplo es solo referencia de forma: los valores salen siempre de la HU y la spec, y nunca se copian del ejemplo, tampoco para SISBEN. El mismo ajuste se hizo en `edf-acceptance/SKILL.md` y `edf-curation/SKILL.md`, que tenían la misma instrucción, y en sus `LEEME`/`alcance-y-glosario.md`. Los seis ejemplos SISBEN de insumo empiezan con la cabecera `EJEMPLO DE FORMA, NO ES FUENTE`.

### Corregido (P2)
- **AUD-E0-13. Varios hechos por celda en `mapeo-campos.md.tpl`.** `05-convenciones.md` §8 declara la excepción explícita para la tabla de mapeo (formato de contrato). La plantilla explica en su cabecera el marcador `[A:*]`/`[PM]` por dimensión dentro de la celda, y que `Estado` resume la fila.
- **AUD-E0-14. Faltaban efectos en `07-contratos-frontera.md` §5.1.** Se agregan «nulo con advertencia» (sin `<campo>_original`), «descarte silencioso» (filtro) y «marca de duplicado no aceptado», más la columna «Etapa que lo produce». La plantilla de mapeo usa ese vocabulario exacto.
- **AUD-E0-15. Faltaba una casilla en `contexto-ingesta.md.tpl`.** El checklist de cierre empieza ahora por «Insumos obligatorios presentes y leídos» (`04-reglas-analisis.md` §4.3).

### Pruebas
- `tests/test_validar_expediente.py`: 7 pruebas nuevas de la regla 5. Una bloqueante de `state_calculation` no frena `analisis` ni la transición a `validation`, pero sí la transición `synthesis` → `state_calculation` y la aprobación de esa etapa.
- `tests/test_regresion_e2e_etapa0.py` (nuevo, 22 pruebas): comprueba en los archivos las cinco correcciones.
- Herramientas: 114 (antes 85). Validation 160, Acceptance 127 y Curation 106, sin cambios. `auditar-skills.py`: 0 silencios y 0 obsoletas.

## 1.9.0 — 2026-11-24

Auditoría de defectos del framework en todas las skills, las herramientas, los hooks y los instaladores. Detalle completo en `auditoria/informe-auditoria.md`: 37 hallazgos, 35 corregidos con prueba de regresión, 1 descartado tras verificarlo y 1 conocido y documentado.

### Nuevo
- `tools/auditar-skills.py`: auditoría repetible. Reporta errores silenciados sin la marca `SILENCIO-INTENCIONAL <ID>` y referencias al generador Java deprecado. Línea base: 19 y 11; al final, 0 y 0.
- `tools/verificar-artefactos-etapa.py`: reglas del motor para los artefactos de Synthesis (GDD v2, `customRulesFile`) y State Calculation (AGGREGATES).
- `auditoria/prompt-comparacion-manual.md`: revisión contra las implementaciones manuales, para hacer en el workspace con acceso a Bitbucket.

### Corregido (P0)
- Validation: un filtro que fallaba validaba menores de edad; `asOfDate` caía a 1970 sin avisar al motor.
- Curation: el domain dictionary usaba claves que el motor ignora; la red de seguridad tragaba errores sin log; `subscriberRelation` mezclaba formatos sin aviso; claves de identidad nulas que Pinning no tolera.
- Synthesis y State Calculation: nombres de campo errados que el motor no reporta (`verificar-claves.py` los cruza).
- Orquestador: el validador del expediente no exigía dependencias, insumo aprobado, evidencia o evidencia diferida, ni State Calculation crítico.
- Hooks: con un intérprete inexistente la compuerta de la etapa 0 fallaba abierta; los instaladores ahora configuran el que existe (y copian desde `.kiro/`, antes no copiaban nada).

### Corregido (P1 y P2)
- Footer de Validation, severidades y `manualReview` de Acceptance, lookup parcial y nivel nulo en Curation, claves de Pinning, `customRulesFile` de Synthesis, reglas AGGREGATES, validador más estricto (`--permisivo` para jsonschema ausente o PII), contadores de la bitácora, pruebas y documentación.

### Pruebas
- Validation 160 (antes 148), Acceptance 127 (116), Curation 106 (88), herramientas 85 (42).

### Sin verificar aquí
- Compilación y pruebas Scala, `instalar.ps1` y los verificadores `.ps1` (no hay JAR del framework ni PowerShell en este entorno).

## 1.8.0 — 2026-11-24

Dos defectos de `edf-validation` encontrados al comparar lo generado con la implementación manual de `oxygen-validation` (rama `feature/colombia_ifr_java17`).

### Corregido
- **El catálogo ocultaba las fallas del lookup.** `OxygenCatalogo` envolvía la consulta en `Try(...).getOrElse(false)`: si el lookup real fallaba, el registro salía como "valor fuera de catálogo" y nunca con el código interno de ADR-01. Ahora la lógica está en `CatalogoDeConsulta` (Capa A, sin `Try`), que propaga la falla a `validarRegistro`; `OxygenCatalogo` solo le pasa `Util.getLookup`. Dos pruebas nuevas en `FieldValidatorsSpec` usan ese adaptador con una consulta que responde y con una que lanza (antes las pruebas usaban catálogos en memoria y no pasaban por este código). ADR-01b.

### Nuevo
- **`archivo.metadata` en el source-spec (opcional)** para los miembros de `FileMetadata` que la plantilla no generaba: `min_frequency`, `sub_subscriber` y `additional_information` (firmas de la clase base del motor) y `miembros_adicionales` (miembro, tipo, valor y fuente) para lo que la clase base conocida no tiene, como `fileLoadType = FULLLOAD`. El framework no inventa firmas: las declara la spec, tomadas de la implementación de referencia. Sin el bloque, `FileMetadata` sale igual que antes. Validación de coherencia: un miembro no puede repetirse ni pisar los que ya genera la plantilla. ADR-05.
- Pruebas de la skill: 148 en verde, 4 omitidas sin `pyhocon` (antes 139). En el ejemplo SISBEN cambian `FieldSpec.scala`, el processor, `FieldValidatorsSpec.scala` y los hashes de `manifest.json`.

## 1.7.2 — 2026-11-18

### Corregido (`edf-curation`)
- **Comentarios del código generado que contradecían la spec.** El cierre del processor y de su prueba ya no es texto fijo: depende de la spec (`parte_manual_comentario`, `pruebas_pendientes_comentario`). Con una spec sin campos abiertos y con el nombre completo concatenado, no aparece ningún `TODO(manual)` y el comentario dice cómo se genera el nombre. Con la spec de ejemplo (que deja `nombreCompleto` y el enriquecimiento abiertos) salen los mismos `TODO(manual)` de antes.
- Comentario de `ConservarEnie` neutro (true conserva la Ñ, false la translitera), comentario del bloque HOCON sin tipos de archivo fijos (`REGULAR, RT y EP`) y nombre de la prueba de la Ñ.
- Lo anterior y `lookups_dir` (relativo a la carpeta de `fixture.entrada`) documentados en el esquema; el error de lookup faltante muestra la ruta buscada.
- Pruebas: 88 en verde, 2 omitidas sin `pyhocon`.

### Fuentes de SISBEN 1.0.2 (fuera de git)
- Formato de las advertencias de curación: `<campo curado>:<MOTIVO>`, sin el valor del dato (spec §10.1, §10.2 y §10.4; HU §5.3). Era un ejemplo de redacción distinto al que genera la skill (PA-005). Sin cambios de reglas.

## 1.7.1 — 2026-11-18

### Corregido (`edf-curation`)
- **`reglas_documento.por_tipo` vacío rompía la generación** ("huecos con valor vacío: reglas_documento_scala"; RG-014 de SISBEN). Es válido: significa la misma regla para todos los tipos. Ahora genera `ReglasDocumento` como `Map()` con un comentario.
- **`--solo-verificar` no detectaba fallas de generación.** Ahora hace en memoria el mismo cálculo que `--out` (fixture, lookups, salida esperada y las 12 plantillas rellenas) sin escribir nada: si pasa, la generación también pasa.
- Pruebas: 87 en verde, 2 omitidas sin `pyhocon`. El ejemplo SISBEN sale idéntico.

## 1.7.0 — 2026-11-18

La skill `edf-curation` solo genera artefactos: ya no compila nada ni necesita Java ni Maven. Con esto, ninguna skill del framework compila. Además, Curation puede concatenar campos curados (nombre completo) y la spec de cada ingesta fija qué pasa con los campos de severidad 3.

### Cambios
- **Generador en Python.** `scripts/render_templates.py` y `scripts/curation_reference.py` se reescribieron como traducción de `RenderTemplates.java` y `Referencia.java`. **Equivalencia comprobada:** con la spec de ejemplo de SISBEN, los 23 archivos salen idénticos byte a byte a la salida Java versionada, incluidos `MANIFEST.json` y `expected/`.
- **Transformación `concatenacion`** (RG-013 de SISBEN): une, en orden, los valores ya curados de campos anteriores con un separador, omitiendo nulos y vacíos; si todos faltan, nulo sin advertencia propia. Cambian el esquema, `FieldMapping`, `Transformaciones` (`concatenar`), el processor y `TransformacionesSpec`. `standardizedIdName` ya no va en `campos_abiertos`.
- **Política de severidad 3** documentada en el esquema y en `SKILL.md`: un nivel en `niveles_no_almacenar` omite la clave; en `niveles_anular`, la publica nula; fuera de las dos listas, la publica estandarizada (nula con advertencia si no se puede transformar). No requirió cambio de código.
- Generación todo o nada: se valida todo antes de borrar `--out`. Nuevo `--solo-verificar` (valida el source-spec sin escribir); `edf-analisis` lo usa para verificar el insumo de Curation.
- `SKILL.md`: regla "generar, nunca compilar"; pasos 3, 4 y 6 con Python; integración y compilación quedan al ingeniero en el fork. Actualizados `runbook-ejecucion.md` §0, `checklist-entrega.md`, `alcance-y-glosario.md`, `inventario-plantillas.md`, `decisiones-y-abiertos.md` (C-CUR-05, P2/CUR-PA01) y comentarios de las plantillas.
- Pruebas: `tests/test_render_templates.py` y `tests/test_curation_reference.py` traducen las suites Java y agregan concatenación, opción B y todo o nada; reemplazan `test_render.py` y `test_reference.py`. Suite del skill: 85 en verde, 2 omitidas sin `pyhocon`. Cobertura: `render_templates.py` 98,7 %, `curation_reference.py` 99,6 %, `hocon_check.py` 86,2 %.
- `scripts/java/` queda como referencia histórica (`NO-USAR.md`, `DEPRECADOS.md`).
- Steering `07`: la regla de concatenación de nombres la fija la spec de cada ingesta; para SISBEN queda resuelta. README: requisitos sin Java.

### Corregido
- `SKILL.md` de Curation citaba la ruta `.kiro/steering/…`, que su propia prueba de portabilidad prohíbe (fallaba desde la versión base).

### Fuentes de SISBEN (en `expedientes/sisben/fuentes/`, fuera de git)
- `spec-sisben.md` 1.0.1: §10.4 nuevo (los campos con severidad 3 se publican estandarizados; ninguna clave se omite) y §10.1 precisa `standardizedIdName`. `hu-sisben.md` 1.0.1: R-23 y un escenario nuevo en §5.3. Cierran PA-003 y RG-013 de Curation.

## 1.6.0 — 2026-11-18

La skill `edf-acceptance` solo genera artefactos: ya no compila nada ni necesita Java ni Maven. Mismo criterio que Validation en la 1.4.0.

### Cambios
- **Generador en Python.** `scripts/render_templates.py` pasa a ser el oficial. Se le agregó lo que tenía el generador Java: listado de supuestos (AC-27), regla "umbral en [0, 1]" (AC-04), regla "QS por registro binario" (AC-ACC-05), reporte de spec mal formada sin excepción, `jsonschema` obligatorio y lectura con o sin BOM.
- **Equivalencia comprobada:** con la spec de ejemplo de SISBEN (con y sin `--audit`), mismos archivos byte a byte y mismo reporte que Java; con 22 variantes de error, mismo código de salida y mismos errores en las mismas rutas.
- `SKILL.md`: regla "generar, nunca compilar"; el paso 2 usa Python; el paso 5 deja integración, compilación y ejecución al ingeniero. `runbook-ejecucion.md` §0 con Python.
- Pruebas: `tests/test_render_templates.py` vuelve a la suite con los casos de la suite Java. Suite del skill: 116 en verde, 1 omitida sin `pyhocon`. Cobertura: `render_templates.py` 94,46 %, `simular_decision.py` 96,40 %, `hocon_check.py` 100 %.
- Referencias actualizadas: `inventario-plantillas.md`, `decisiones-y-abiertos.md`, `checklist-entrega.md`, comentario del esquema, `simular_decision.py`. README: requisitos.
- `scripts/java/` queda como referencia histórica (`NO-USAR.md`, `DEPRECADOS.md`).

### Corregido
- Al ejemplo versionado `assets/examples/sisben/generated/` le faltaba `src/` (la prueba Scala y los eventos del fixture que cita `expected-output.yaml`). Se repuso con la salida generada. Explica las fallas de la suite Java de Acceptance registradas como F-FW-5.

### Pendiente
- `edf-curation` sigue con generador Java.

## 1.5.1 — 2026-11-18

Corrige el orden de la evidencia diferida de la 1.5.0, que dejaba un bloqueo circular.

### Corregido
- En la 1.5.0 la evidencia diferida se declaraba al aprobar la transición N → N+1. Pero esa transición exige el insumo de N+1 aprobado (condición 3), y el insumo de N+1 no se puede aprobar mientras la etapa N no esté `aprobada` (prompt `08`, paso 1). Ninguno de los tres pasos podía ir primero.
- Ahora (`edf-orquestador/references/03` §3b) la evidencia diferida se declara al **aprobar la etapa N** (`tipo: contexto`, riesgo `alto`, justificación del Desarrollador). La etapa pasa a `aprobada` con la evidencia en `null`; luego se aprueba el insumo de N+1 y luego la transición. Es el mismo orden de la etapa 0. Ajustados también `prompts/02`, `prompts/03` y `references/01` §6.

## 1.5.0 — 2026-11-18

El riesgo alto (N3) lo aprueba el Desarrollador con justificación escrita, y se puede avanzar de etapa con la evidencia del pipeline diferida. Decisión del Desarrollador.

### Cambios
- **Aprobación N3 sin visado obligatorio.** Basta la aprobación del Desarrollador con justificación escrita; el visado de los dueños del patrón (Ingesta 3) es opcional, solo si el Desarrollador lo pide. Archivos: `steering/06` §3 y §4, `steering/03` §8, `edf-orquestador/references/03` §2, `prompts/03-registrar-aprobacion.md`.
- **Evidencia diferida** (`edf-orquestador/references/03` §3b): una transición puede aprobarse sin evidencia del pipeline (condición 5) con riesgo `alto` y justificación del Desarrollador que diga "evidencia diferida". La etapa queda `aprobada` con la evidencia en `null` y se abre un riesgo "evidencia diferida de <etapa>" hasta reportarla (prompt `05`). `references/01` §6 y `prompts/02` la mencionan como vía prevista.
- Esquema y `validar-expediente.py` sin cambios: ya aceptaban una aprobación `alto` del Desarrollador con justificación.

### Sin cambios
- State Calculation (N4) sigue requiriendo `PO` o `Aprobador designado`.

## 1.4.0 — 2026-11-18

La skill `edf-validation` solo genera artefactos: ya no compila nada ni necesita Java ni Maven. Decisión del Desarrollador.

### Cambios
- **Generador en Python.** `scripts/render_templates.py` se reescribió como traducción del generador Java vigente (marcas `ASSUMPTION` con ID, supuestos del `manifest.json` derivados de las marcas, generación todo o nada, verificación de métricas) y pasa a ser el generador oficial. Con el ejemplo de SISBEN y con el Anexo A de `spec-sisben.md`, produce los mismos archivos byte a byte que la versión Java, incluido `manifest.json`.
- `scripts/spec_utils.py` y `prevalidar_archivo.py` verifican el source-spec con el generador Python; ya no llaman a ningún `.jar`. El prevalidador ahora muestra los errores del spec.
- `SKILL.md`: nueva regla "generar, nunca compilar" (prohibido `mvn`, `javac`, `java -jar`, `sbt`, `spark-submit`, Docker); los pasos 2 y 3 usan Python; el paso 7 deja la copia al fork, la compilación y la ejecución al ingeniero.
- `references/runbook-ejecucion.md`: tabla de quién hace cada paso (§1 a §3 la skill, con Python; §4 a §7 el ingeniero, en el fork).
- `scripts/java/` queda como referencia histórica (`NO-USAR.md`, `DEPRECADOS.md`).
- Pruebas: `tests/test_render_templates.py` traduce la suite Java (52 casos) y vuelve a la suite y a la cobertura. Suite del skill: 139 pruebas en verde, 4 omitidas sin `pyhocon`. Cobertura: `render_templates.py` 99 %, `spec_utils.py` 100 %, `prevalidar_archivo.py` 96 %, `verificar_hocon.py` 97 %.
- Referencias actualizadas: `checklist-entrega.md`, `inventario-plantillas.md`, `assets/examples/sisben/LEEME.md`, comentario del esquema, `edf-analisis/SKILL.md` (verificación mecánica) y `edf-orquestador/references/04-contratos-por-skill.md` (evidencia de Validation). README: requisitos.

### Corregido
- Al ejemplo versionado `assets/examples/sisben/generated/` le faltaba `processor/SISBENValidationProcessor.scala`, aunque su `manifest.json` lo listaba. Se repuso con la salida generada. Era la falla de "1 de 39" de `mvn verify` en Validation registrada como F-FW-5.

### Pendiente
- `edf-acceptance` y `edf-curation` siguen con generador Java: hay que pasarlos a Python igual que Validation.

## 1.3.1 — 2026-11-17

Cierra los huecos que la etapa 0 de SISBEN encontró con la versión 1.3.0.

### Cambios
- **Precedencia de los datos de la ingesta** (`steering/00` §6): cuando el steering o una `reference` dejan abierto un dato de la ingesta (`[A:*]`, `[PM]`, `OQ-*`) y la HU o la spec lo responden, la respuesta es `[C]` para esa ingesta. Ya no se registra como supuesto. `edf-analisis/references/deteccion-ambiguedades.md` §7 agrega ese caso y el de los ejemplos del framework a "qué no es una ambigüedad".
- **Registro global de preguntas** (`steering/01` §0.4): no viaja con el framework; si no está en el workspace, los IDs `OQ-*`, `PT-*`, `CQ-*` se citan como constan, sin reportarlo como hallazgo.
- `steering/07` §2.1: los campos que agrega el proceso los declara la spec de cada ingesta (se quitan los "3 campos internos sin nombre" de SISBEN).
- `steering/11` §5 y §6: el ejemplo curado se alinea con la spec de SISBEN (identificadores como texto, `standardizedId*`, `subscriberRelation`); los pendientes #4 a #8 quedan resueltos para SISBEN por su spec.
- `steering/03` §9 #11 (`OQ-TR-4`): resuelto para SISBEN por su spec (sí pasa por Synthesis).

## 1.3.0 — 2026-11-17

Flexibiliza el framework: cada ingesta se configura solo con su HU y su spec. Se quitan los valores y las preguntas pegados al piloto SISBEN. Decisión del Desarrollador.

### Cambios
- **Contrato del archivo genérico.** El framework no fija delimitador ni ningún otro valor del contrato: los declara la spec de cada ingesta (cualquier carácter). Si la spec no declara una dimensión, es pregunta abierta bloqueante. Archivos: `steering/07` §2 y §4, `steering/04` §9, `edf-orquestador/prompts/00` paso 2, `edf-analisis/SKILL.md`.
- **Fuente oficial informativa.** Deja de ser pregunta de apertura obligatoria y bloqueante; si las fuentes no la declaran se registra `indeterminado` sin abrir pregunta. Declararla o cambiarla ya no eleva el riesgo a alto. Archivos: `edf-orquestador/prompts/00` y `02`, `references/03` §1, `SKILL.md`, `templates/estado-ingesta.schema.json` (descripción), `steering/02`, `edf-analisis/references/deteccion-ambiguedades.md`, `assets/templates/contexto-ingesta.md.tpl`.
- **Sin preguntas predefinidas.** El Orquestador ya no siembra PA-001..PA-004 en SISBEN: registra solo las preguntas que dejan abiertas la HU y la spec. Archivos: `prompts/00` paso 4, `steering/10`, `specs/orquestador/certificacion.md` (C-1 a C-4), `specs/orquestador/tasks.md`.
- **Datos de apertura desde las fuentes.** El Orquestador toma de la HU y la spec los datos de apertura y pregunta solo los que falten. Business Light Code y rutas desconocidas quedan `null`.
- **Plantilla del expediente genérica.** `templates/estado-ingesta.seed.json` ya no trae valores de SISBEN (contrato, rutas, jerarquía, preguntas); los artefactos usan `<Fuente>`. Sigue validando contra el esquema 1.1.0.
- **`steering/10-piloto-sisben.md`** pasa a ser contexto histórico: no fija valores; remite a `expedientes/sisben/fuentes/`.
- **`steering/07` §5.6 y §5.7:** las reglas de consistencia y la llave de unicidad las declara la spec de cada ingesta (se quitan `CONS-SISBEN-001/002` y los nombres legados `NUMERO_IDENTIFICACION`/`TIPO_IDENTIFICACION`).

### Sin cambios
- Gobierno: aprobaciones, bitácora, checklist de bloqueo, riesgo de State Calculation (N4) y compuerta de la etapa 0.
- Esquema del expediente (sigue en 1.1.0) y skills de etapa.

## 1.2.0 — 2026-11-13

Hace cumplir la compuerta de entrada de la etapa 0: la HU y la spec de Confluence son obligatorias. Hasta ahora solo existía como texto (`docs/Compuertas-Entrada-Insumos.md` §2, A1 a A12) y en `sisben-e2e` el análisis arrancó sin la spec (SUP-T-010).

### Nuevo
- `tools/verificar-insumos-etapa0.py`: busca en `expedientes/<id>/fuentes/` un archivo que empiece por `HU` y otro por `spec` (sin distinguir mayúsculas, no vacíos). Si no están, acepta las rutas que el usuario indique y que se registren en `fuentes/rutas-fuentes.json` (`{"hu": "...", "spec": "..."}`). Si falta alguno, sale con código 2 y dice cuál o cuáles faltan.
- `.kiro/hooks/compuerta-etapa0.json` (formato `v1`, acción `command`):
  - `PromptSubmit`: bloquea el prompt que pide iniciar o continuar una ingesta si falta algún insumo. Bloquea solo en el IDE.
  - `PreToolUse` (`fs_write|str_replace|fs_append`): bloquea la escritura en `expedientes/<id>/` mientras falte algún insumo. `expedientes/<id>/fuentes/` está exenta para poder colocar las fuentes y registrar sus rutas.
- `tests/test_verificar_insumos.py` y `tests/test_hook_compuerta.py` (`python -m unittest discover -s tests`).

### Cambios
- Texto estándar de la compuerta en A1 a A12: "La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene." Archivos: `steering/00`, `03`, `04` (§3 paso 1 y casilla nueva en §4.3), `09` (§5.2), `edf-analisis/SKILL.md` (descripción, Paso 0, Modo 1), `references/deteccion-ambiguedades.md`, `assets/templates/contexto-ingesta.md.tpl`, `edf-orquestador/references/04-contratos-por-skill.md` y `prompts/00-iniciar-ingesta.md`.
- `steering/00` §2: el layout pasa a opcional en la columna de insumos (contradicción C6).
- `instalar.ps1` e `instalar.sh`: los mensajes listan el hook y el script nuevos.

### Corregido (alineación de los skills con `Motor-Oxigen/`)
- **Versión de Java del motor.** Los runbooks de `edf-validation`, `edf-acceptance` y `edf-curation` decían JDK 17 (y que el `pom.xml`/`Dockerfile` del repo lo exigían). Los `pom.xml` (`maven.compiler.source/target = 11`), los `Dockerfile` (`openjdk-11-jdk`), los README y los `migrate.md` de las seis etapas dicen JDK 11. Java 17 queda como requisito solo del generador de cada skill (`scripts/java/`).
- **Descripciones de `edf-synthesis` y `edf-statecalc`.** El front-matter tenía los acentos y la flecha convertidos en `?` ("configuraci?n", "campo?handler"). Es el texto con el que se decide cuándo activar cada skill.
- **Clave HOCON del domain dictionary en `edf-curation`.** Se citaba `oxygen.stage.consistency.paths.domainDictionaryFile`, que es la ruta del objeto Scala (`SparkCurator.scala`). La clave HOCON real es `oxygen.stage.consistencyCheck.paths.domainDictionaryFile` (`oxygen-config-default-local.conf`). Se corrigió en `curation-block.conf.tpl`, en el fragmento de ejemplo generado y en `references/dsl-config-reference.md`.

### Limitaciones
- El hook `PreToolUse` no cubre escrituras hechas con comandos de shell.
- El hook `PromptSubmit` deduce el id de la ingesta del texto del prompt. Si pide iniciar o continuar sin id, deja pasar con un aviso y el hook `PreToolUse` bloquea la primera escritura.
- Los expedientes que ya existen sin `fuentes/` (por ejemplo `sisben-e2e`) quedan bloqueados para escribir hasta tener la HU y la spec en `fuentes/` o su ruta en `rutas-fuentes.json`. Decisión del usuario: no se creó manifiesto para `sisben-e2e`.
- Siguen pendientes R1 y R2 del documento de compuertas (regla en `validar-expediente.py` y campo `fuentes_entrada[]` en el esquema).

## 1.1.2 — 2026-11-06

Versiona las correcciones que se hicieron al generador de `edf-curation` durante la prueba E2E de SISBEN (`expedientes/sisben-e2e/`, BIT-0052). Hasta ahora estaban aplicadas en `.kiro/skills/edf-curation/` sin subir `VERSION` ni `CHANGELOG`. Informe: `docs/resultados-e2e-2026-11-06.md`.

### Corregido
- **F-FW-10.** El esquema `source-spec` y el generador admiten fechas ISO `uuuu-MM-dd` en `parametros.formatos_fecha`. El fixture y la implementación de referencia Java también las leen.
- **F-FW-11.** El processor generado sobrescribe `subscriberRelation` con `<standardizedIdType>_<standardizedIdNumber>` cuando el `source-spec` declara `extension_subscriber_relation`, y conserva el del evento aceptado si falta algún componente (decisión D14). La firma del gancho `CurationProcessor.subscriberRelation(event)` **no se verificó** contra Oxygen (F-01): si es otra, el Scala no compila.
- **F-FW-13.** El generador acepta `pendientes: []` en la homologación de tipo de documento y genera `Set()`.
- **F-FW-14.** `Transformaciones.documento` convierte a mayúsculas el número de documento (plantilla `Transformations.scala.tpl` y referencia Java, con pruebas en Scala y en Java). `conservar_original` no cambia.

### Archivos afectados
- Según BIT-0052 y las marcas de fecha del archivo (el workspace no es un repositorio git y no hay diff): `edf-curation/SKILL.md`, `assets/checklist-entrega.md`, `assets/schema/source-spec.schema.yaml`, `assets/templates/` (`CurationProcessor`, `SourceProcessorSpec`, `Transformations`, `TransformationsSpec`), `references/` (`decisiones-y-abiertos`, `especificacion-sisben`, `interpretar-salida`, `patrones`), `scripts/java/src/` (`Referencia`, `RenderTemplates` y sus pruebas) y las salidas esperadas de `assets/examples/sisben/` (regeneradas).
- La versión de la skill (`0.1.0` en `source-spec` y en `MANIFEST.json`) no cambia.

### Conocido, sin resolver
- **F-FW-12.** El generador no puede publicar un campo llamado `NUM_DOCUMENTO_SISBEN` (patrón de nombres). La prueba lo resolvió por decisión (`numeroDocumentoOriginal`), no por un cambio del generador.
- **F-FW-3.** `edf-curation/scripts/tests/test_hocon_y_portabilidad.py` falla: `SKILL.md` contiene `.kiro/`, que el propio test prohíbe. Vigente.
- **F-FW-5.** `mvn verify` falla en Validation (1 de 39) y Acceptance (2 de 54). Causa sin determinar.
- Hallazgos F-FW-15 a F-FW-17 y F-DOC-6 a F-DOC-8 del informe: sin cambios en esta versión.

### Sin propagar
- Este workspace no contiene la carpeta `kiro/` del repositorio fuente (solo la instalada, `.kiro/`). Si el repositorio fuente existe en otro lugar, debe recibir los cambios de `edf-curation` antes de la siguiente iteración.

## 1.1.1 — 2026-11-05

Integra en `edf-analisis` la skill externa `deteccion-ambiguedades` del paquete `analisis-edf`, como referencia. Es lo único del paquete externo que entra al framework.

### Nuevo
- `edf-analisis/references/deteccion-ambiguedades.md`: los seis patrones de ambigüedad, el criterio bloqueante / no bloqueante (en la duda, bloqueante), el formato de la pregunta abierta `PA-nnn` alineado con `preguntas_abiertas[]` y el gate de la etapa 0, y la sección de lo que no es una ambigüedad. Adaptada a las nueve dimensiones por campo (`07` §3), a los marcadores de `05` §3 y a las citas de `05` §6. Los casos reales se referencian a `04` §5.1, no se copian.

### Cambios
- `edf-analisis/SKILL.md` pasa de 1.0.0 a 1.1.0. Solo se añadieron líneas: fila en la tabla Referencias, remisión a la referencia en el Modo 1 (paso 3) y el Modo 2 (paso 2), y la regla explícita de que sin HU o sin spec de Confluence el análisis no arranca (`04` §3, paso 1; el SKILL no la decía).

### No entra
- Del paquete `analisis-edf` no se integró: el agente `analisis-requerimiento-edf`, la skill `mapeo-campos-edf`, el steering `07-reglas-por-campo-edf`, las plantillas, `certificacion.md`, `validar-analisis.py` ni el instalador. Decisión del usuario.
- Sin cambios en plantillas, steerings, esquema del expediente, `tools/` ni instaladores. Los cuatro artefactos de la etapa 0 conservan sus nombres.

### Sin ejecutar
- Ninguna herramienta `.py` (la máquina no tiene Python).

## 1.1.0 — 2026-11-04

Cierra lo que se pudo cerrar con la información disponible y deja el resto como bloqueos explícitos. Estado frente a las E2E: `docs/estado-e2e.md`.

### Nuevo
- **Omisión de etapa con firma del PO:** estado `omitida` + aprobación `tipo: omision` (actor PO o Aprobador designado, con justificación) en el esquema, el validador (regla 2d), `references/03` §4b y el prompt `09-omitir-etapa.md`. `omitida` no cuenta como aprobada por defecto. El Paso 0 de las 6 skills y el steering `00` §3 la reconocen.
- `tools/verificar-claves.py`: cruza las claves curadas entre `source-spec-curation.yaml`, `contrato-curation-pinning.md` y los tres `yaml-skill-*.yaml`.
- `edf-analisis/references/fuentes/`: `curation-pinning-sisben.md`, `curation-muric.md`, `pinning-muric.md` (copias de las fuentes aportadas por el usuario).
- `edf-analisis/assets/examples/sisben/LEEME.md`: desviaciones conocidas de los YAML originales.
- `docs/estado-e2e.md`.

### Cambios
- `edf-curation`: requisito de claves `standardizedIdType` y `standardizedIdNumber` (opción A, DA-11, DA-13); `standardizedIdName` y `NUM_DOCUMENTO_SISBEN` declarados en `campos_abiertos` porque el generador no concatena. El ejemplo SISBEN no se tocó (sus salidas esperadas se regeneran con Maven).
- `edf-pinning`: modo Colombia — reutiliza matcher y lista de atributos compartidos, no genera `IdentityMatcher` (P1, DA-12); se detiene en la bandera de fuente (`OQ-TR-6`).
- `edf-acceptance`: diseño de `MANUAL_REVIEW` a nivel archivo (DIF-1, DA-14); sin configurar hasta cerrar DIF-1.2/1.3/1.4.
- Plantilla `yaml-skill-pinning.yaml.tpl`: sección `modo_colombia`.
- Steering `03` §9: `OQ-TR-4`, `OQ-TR-5`, `OQ-TR-6`. Steering `11`: §2.1 (patrón MURIC) y §6 #3 cerrada, #5 decidida, #6–#8 nuevas.
- `references/04`: DIF-4 cerrada (scoring fuera de alcance, decisión del PO); DIF-1 parcial.
- El esquema sigue en 1.1.0: la 1.0.0 del framework no llegó a instalarse, así que no hay expedientes que migrar.

### Corregido
- Caracteres mal codificados introducidos por un script en el Paso 0 de las 6 skills.

### Sin ejecutar
- `validar-expediente.py`, `validar-expedientes.py` y `verificar-claves.py` no se han corrido (la máquina no tiene Python). Tampoco los generadores Java ni las pruebas pytest.

## 1.0.0 — 2026-11-04

Primera versión unificada. Integra en un solo paquete instalable las piezas que vivían en `Ingesta-edf/`, `transformacion-edf/` y `orquestador-edf/`. Las carpetas originales **no** se modificaron.

### Estructura
- Nuevo repo `edf-framework-IA/` con `kiro/` (se instala como `.kiro/`), `tools/`, `docs/`, `instalar.ps1`, `instalar.sh`, `VERSION`.
- Skills renombradas a `edf-<etapa>` (`name` del frontmatter = nombre de carpeta):
  `edf-validation-stage` → `edf-validation` · `edf-acceptance-stage` → `edf-acceptance` · `edf-curation-stage` → `edf-curation` ·
  `skill-configgen-pinning` → `edf-pinning` · `skill-configgen-synthesis` → `edf-synthesis` · `skill-configgen-statecalc` → `edf-statecalc` ·
  `orquestador-ingestas-edf` → `edf-orquestador`.
- No se copiaron `scripts/java/target/`, `__pycache__/` ni `.pytest_cache/` (se regeneran con `mvn verify` / `pytest`).

### Nuevo
- Skill `edf-analisis`: etapa 0 y análisis técnico por etapa. Produce los `source-spec-*.yaml` y los `yaml-skill-*.yaml`. Incluye `logica-pinning.md`, `logica-synthesis.md`, `logica-statecalc.md` (desde `transformacion-edf/Diseño/`), los tres YAML reales de SISBEN como ejemplo, y plantillas.
- Steering `00-framework-edf.md` (mapa de piezas, modelo de etapas, regla de habilitación, correspondencia de riesgo).
- Steering `11-contrato-curation-pinning.md` (contrato de frontera con la información aportada el 2026-11-04).
- Hook `validar-expediente.json` y `tools/validar-expedientes.py`.
- Paso 0 de habilitación en las 6 skills de etapa (lee el expediente; no lo escribe).
- `docs/arquitectura.md`, `docs/contratos/README.md`.

### Cambios de modelo
- **6 etapas + etapa 0**: se retiró `reception` del expediente (decisiones H-3/H-4). Esquema `estado-ingesta.schema.json` 1.0.0 → **1.1.0** (8 entradas de etapa, nivel de riesgo `critico`, roles `Dueno del patron` y `Aprobador designado`).
- Riesgo: taxonomía N1–N4 del steering `06`; `critico` (N4) exige justificación y aprobación del PO o del Aprobador designado (esquema + validador).
- State Calculation: compromiso Éxito y riesgo fijo N4. La regla de corte aplica también a `state_calculation`.
- Etapa 0: riesgo N1 `bajo` (clasificación fija de `06` §2.2).
- El steering propio del Orquestador (`00`–`04`) pasa a `edf-orquestador/references/`; `05-piloto-sisben` pasa a steering `10`.
- Contrato por skill (`references/04-contratos-por-skill.md`) alineado a lo que generan las skills reales; las diferencias con la especificación de requerimientos quedan como DIF-1..DIF-5.
- Steering `04` §1: el análisis también produce los `.yaml` de insumo (decisión del usuario, `[P]`).
- Steering `09` §3: apunta al esquema vigente y propone separar preguntas de ingesta (expediente) de preguntas del framework (registro único) `[P]`.

### Conocido / no cambiado
- Los generadores Java/Python y las plantillas de Validation, Acceptance y Curation siguen escribiendo la etiqueta histórica `generado por edf-<etapa>-stage` y `artifactId edf-<etapa>-stage-tools`. No se cambiaron porque no se pudieron correr las pruebas Maven/pytest en esta máquina (sin JDK 17 ni Maven ni Python). Es solo una etiqueta.
- Los `evals.json`, fixtures, `verificar-artefacto.ps1` y la plantilla `IdentityMatcher.scala.template` de transformación conservan `skill-configgen-*` / `Skill-ConfigGen-*` como dato de prueba o etiqueta de generación. Los `yaml-skill-*.yaml` reales de SISBEN conservan `skill_destino: "Skill-ConfigGen-*"` (son el ejemplo histórico; las plantillas nuevas usan `edf-*`).
- Hallazgo sin resolver: los nombres de campo curado no coinciden entre Curation (`numeroDocumento`, `primerNombre`) y los `yaml-skill-*.yaml` de SISBEN (`standardizedIdNumber`, `PRI_NOMBRE`). Registrado en steering `11` §6 #5 como bloqueante de punta a punta.
- `validar-expediente.py` y el esquema 1.1.0 no se ejecutaron en esta máquina (sin Python). La semilla `estado-ingesta.seed.json` se verificó solo como JSON válido con 8 etapas.
- Las referencias a `Diseño/templates-scala/`, `Docs-General/` y `Product-Definition/` son citas del workspace de origen; esos archivos no vienen en el paquete.
