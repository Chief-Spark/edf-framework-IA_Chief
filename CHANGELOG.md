# Changelog

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
