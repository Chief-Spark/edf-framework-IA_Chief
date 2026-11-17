# Changelog

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
