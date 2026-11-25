# Decisiones, supuestos y puntos abiertos (Validation)

**Alcance (1.9.2, AUD-V-11 y AUD-V-13).** Este documento es del **mantenimiento de la skill**: explica los IDs
de las marcas `ASSUMPTION` y las decisiones de diseño. Las tablas F-xx y VAL-SPxx describen el ejemplo de forma
(`assets/examples/sisben/`), no la spec vigente de una ingesta: en una ingesta manda su spec. Las
discrepancias que encuentre una ingesta **no se escriben aquí** (ni en ningún archivo de `.kiro/`): van en la
entrega al Orquestador (lista de artefactos de `expedientes/<id>/propuestas/validation/`), que las registra en
el expediente. Si la spec de la ingesta ya fija el valor de un supuesto, el insumo lo declara en
`supuestos_resueltos` y la marca sale como `RESUELTO <ID> (<fuente>)` (AUD-V-08).

Precedencia: decisiones confirmadas (D1–D14) > HU > entendimiento funcional > product-definition >
reglas EDF > código Oxygen > sesiones. **Excepción:** para el comportamiento técnico del motor, el
código Oxygen gana sobre los documentos. Una discrepancia documental se registra; el skill no la
resuelve.

**Supuestos activos y manifiesto (ADR-G1, 2026-10-21).** Un supuesto es **activo** si su valor llega al
código o al HOCON generado. Todo supuesto activo lleva una marca `ASSUMPTION <ID>:` con el ID en la misma
línea, en la plantilla o en el fragmento que el generador arma desde el source-spec (lista `supuestos` de
`campos[]`, `reglas_entre_campos[]` y `catalogos[]`). `manifest.json["supuestos"]` **se deriva** de las
marcas halladas en la salida (orden lexicográfico); no es una lista fija. La columna "En el código
generado" de las tablas de abajo dice cuáles son activos: **sí** = activo (debe estar en el manifest);
**no** = supuesto sin huella en el código, con la razón (no entra al manifest); **—** = no es un supuesto
(decisión, hecho verificado o remisión a otro ID). La prueba `test_supuestos_del_manifest_en_decisiones`
(`scripts/tests/test_render_templates.py`) comprueba la biyección entre los "sí" y el manifest (AC-18b).

## Faltantes F-01..F-17 (todos aplican a Validation salvo nota)

| Id | Tema | Decisión / supuesto aplicado | Dónde se refleja | En el código generado |
|---|---|---|---|---|
| F-01 | JAR / repositorio de artefactos | Sin acceso → **descartado**. Compilación "no certificada". Compile-check opcional: Capa A + Spec + tests puros con Scala 2.12; parse-check HOCON con el parser real solo si se dispone de su fuente | manifiesto, runbook §5 | — (decisión; se declara en `manifest["compilacion"]`) |
| F-02 | Credencial expuesta en `settings-artifactory.xml` | El skill nunca lo lee, copia ni referencia salvo para prohibirlo; pedir rotación | guardrails, runbook §0, `scan_forbidden` | — |
| F-03 | Claves HOCON | Resuelto: `headerRows`/`footerRows`, `historicAttributeArgs`, `fullScanSchema`, `deDupVersion`, `duplicateThreshold`, `computeMetrics` | `validation-block.conf.tpl` | — (verificado) |
| F-04 | Motor y flags en QA/PRO | `historicAttributes = false`; `defaultOutputStructure = true` (SUPUESTO; el default del código es `false` → CustomMode). Se verifica, no se escribe como clave | `ambiente` del spec, manifiesto | sí · `validation-block.conf.tpl` (cabecera de requisitos de ambiente, con el valor de `ambiente.default_output_structure`) |
| F-05 | Fork `bureau.colombia_innersource` | Capa A propia y aislada; paquete `...validation.source.<fuente>` | `paquetes` | — |
| F-06 | Header literal | Nombres fuente o dominio, sin mayúsculas ni tildes; exactamente 22 columnas; campo 18 admite `fecha` o `fec_digitacion` | `alias`, `RecordValidator.validarHeader` | — |
| F-07 | Nombre RT/EP | `filePattern` con `.*`; `fileTag` por prefijo (`REGULAR`/`RT`/`EP`) | `archivo.nombre` | sí · `FileMetadata.scala.tpl` (`fileTag`) |
| F-08 | Código del salvoconducto | `SISBEN_ERR_023` provisional, severidad 1 (así en el ejemplo de forma; en la spec SISBEN 1.0.2 es la regla RV-SIS-04 y es definitivo) | `reglas_entre_campos` RV-SIS-06 del ejemplo | sí · regla RV-SIS-06 de `<Fuente>Spec.scala` (`reglas_entre_campos[].supuestos`) |
| F-09 | Fila con ≠ 22 columnas | Error sev. 1 `SISBEN_ERR_026`, sin excepción | `codigos_especiales.estructura` | sí · `SourceSpec.scala.tpl` (`codigos.estructura`) |
| F-10 | Severidad 0 / código por campo | Un código por campo; causa en `messageKey`; sin sev. 0 | Capa A | — |
| F-11 | Códigos de nombre/header | **Verificado en motor:** sin expresión `validate[].fileName/header` no hay marcador. Se emiten `errorlevelNotIn("1")` y códigos provisionales `SISBEN_ERR_024`/`025` | HOCON, processor | sí · `SourceSpec.scala.tpl` (`codigos.nombreArchivo`, `codigos.header`) y `validation-block.conf.tpl` (`validate`) |
| F-12 | Estructura de los 7 lookups | Columnas, archivo, delimitador y `keys` supuestos; filas sintéticas | `catalogos` | sí · `lookups.conf.tpl` |
| F-13 | DIVIPOLA oficial | Muestra sintética mínima "no verificada contra DANE" | `catalogos` | sí · catálogo sintético de `<Fuente>ValidationProcessorSpec.scala` (`catalogos[].supuestos`). No llega a `lookups-<fuente>.conf`: el HOCON solo registra tabla, archivo y `keys` (F-12) |
| F-14 | "Más reciente" | Mayor `asOfDate` (`FECHA_ACTUALIZACION_CNS`); desempate por mayor `eventId`. "Última línea" no es implementable: el orden del grupo no refleja el archivo y no hay número de línea. La pre-validación local desempata por número de línea | processor, `prevalidar_archivo.py` | sí · `ValidationProcessor.scala.tpl` (`listDuplicateEvents`) y `capa-a/FieldValidators.scala.tpl` (`indiceAConservar`) |
| F-15 | `duplicateThreshold` y nivel de la marca | Default `Integer.MAX_VALUE` (se omite la clave); marca con nivel `""` | HOCON, processor | — (decisión) |
| F-16 | Reglas de negocio con dos versiones | Se usa la versión de `04_Etapas` §4.1; D10 prevalece | `campos` | — (decisión) |
| F-17 | UTF-8 | Sin clave `encoding` (D5) | HOCON | — (decisión D5) |

## Contradicciones documentales registradas

| Id | Contradicción | Tratamiento |
|---|---|---|
| C19 / C34 | `ID_FICHA_ORIGEN`: obligatorio (reglas §4.1, requisitos) vs `mandatory = false` (§3) vs "No" (especificación §6.6) | Se aplica obligatorio sev. 1 (precedencia). Si se confirma opcional: `obligatorio: false`, `severidad: "3"` y código `SISBEN_INC_001` |
| — | `ID_GRUPO` admite `null/nan` (especificación) vs A–D (reglas §4.1) | Se aplica A–D |
| — | Numeración RV-SIS distinta entre reglas §6 y requisitos §4.3 | Se usa la de requisitos |
| — | Carpeta `fields/` vs `field/`, `SISBENEventValidationProcessor` vs `SISBENValidationProcessor` (VAL-PT08) | Carpetas `config/`, `processor/`, `validator/`; nombre `SISBENValidationProcessor` |
| — | Patrón Scala de reglas §8.3 (no extiende `ValidationProcessor`) | No se usa |
| — | El patrón proponía marcar menores con código/QS; D6.a/D14 lo cambian a filtro | Filtro `shouldFilterRow` |

## Puntos abiertos de la plantilla

| Id | Tema | Estado en este skill | En el código generado |
|---|---|---|---|
| VAL-PT01 | Ubicación del código compartido (Capa A) | ASSUMPTION: `com.experian.edf.oxygen.validation.source.common` | sí · `capa-a/FieldSpec.scala.tpl` (`package`) |
| VAL-PT02 | Capa A existente en el fork | Si existe (`FunctionalFieldMetaData`), comparar y no sobrescribir | — (instrucción al técnico) |
| VAL-PT03 | API de lookup | ASSUMPTION: `Util.getLookup(tabla, Map(col → valor), colSalida, lookup)` del motor; devuelve `""` sin coincidencia | sí · `ValidationProcessor.scala.tpl` (`OxygenCatalogo`) |
| VAL-PT04 | Firmas de framework-core | `ObjectError`, `QualityScore`, `ProcessorContext`, `ValidatedEventValueObject` verificados por uso; miembros abstractos de `FileMetadata` y `DomainData` siguen SUPUESTO | sí · `FileMetadata.scala.tpl` (miembros sobrescritos) |
| VAL-PT05 | Fecha de "más reciente" | F-14 | — (ver F-14) |
| VAL-PT06 | Códigos/expresiones de archivo | F-11 | — (ver F-11) |
| VAL-PT07 | Claves HOCON | Cerrado (F-03) | — (cerrado) |
| VAL-PT08 | Carpetas y nombre del processor | Ver contradicciones | — (decisión) |
| VAL-PA01 | Motor que corre en Colombia | F-04 | — (ver F-04) |
| VAL-PA04 | `outputEventType = 30` | Inferido | sí · `ValidationProcessor.scala.tpl` (`outputEventType`) |

## Supuestos propios del skill

IDs `VAL-SP01..VAL-SP10` asignados el 2026-10-21 en el orden de la tabla (ADR-G1).

| Id | Supuesto | Motivo | Cómo cambiarlo | En el código generado |
|---|---|---|---|---|
| VAL-SP01 | `SISBEN_ERR_027` para un error interno inesperado al validar un registro (también un catálogo que lanza, ADR-01) | Red de seguridad: nunca lanzar por registro | `codigos_especiales.interno` | sí · `SourceSpec.scala.tpl` (`codigos.interno`) |
| VAL-SP02 | Si `FECHA_ACTUALIZACION_CNS` es inválida, `asOfDate` = fecha de recepción | `domainData` siempre `Some`; el error ya queda diagnosticado. Desde AUD-V2 el processor pasa `asOfDate = None` y el motor pone la fecha de recepción y marca `didCreateAsOfDateFromReceivedDate = true` | Plantilla del processor | sí · `ValidationProcessor.scala.tpl` (`asOfDate`) |
| VAL-SP03 | Registros sin tipo o número de documento no se agrupan como duplicados | Evitar marcar como duplicados filas que solo comparten la ausencia de documento | `RecordValidator.claveDuplicado` | sí · `capa-a/FieldValidators.scala.tpl` (`claveDuplicado`) |
| VAL-SP04 | `ID_NIVEL` se valida por rango 1–21 sin consultar `SISBEN_GROUP_SUBGROUP_NH` | Se desconoce si la tabla relaciona grupo y nivel | Agregar `catalogo` al campo o una regla de correspondencia | sí · `FieldSpec` de `ID_NIVEL` en `<Fuente>Spec.scala` (`campos[].supuestos`) |
| VAL-SP05 | `ID_MARCA` / `ID_ESTADO` comparten `203_...` con columna `codigo`; el dominio de cada campo distingue los valores | La tabla une marca y estado | `catalogos` | sí · `FieldSpec` de `ID_MARCA` e `ID_ESTADO` en `<Fuente>Spec.scala` (`campos[].supuestos`) |
| VAL-SP06 | RV-GEN-06 (tipo + número contra 202) se implementa como existencia del tipo en 202 | La tabla 202 es de tipos; no hay regla de formato por tipo documentada | Nueva regla en el spec | sí · `FieldSpec` de `TIP_DOCUMENTO` en `<Fuente>Spec.scala` (`campos[].supuestos`) |
| VAL-SP07 | `fileAsOfDate` = último día del periodo AAAAMM | Inferido del nombre | `archivo.nombre.file_as_of_date` | sí · `FileMetadata.scala.tpl` (`fileAsOfDate`) |
| VAL-SP08 | Fragmentos HOCON en `propuesta/`, a pegar como elementos nuevos | La fusión de listas entre archivos no es aditiva en HOCON | — | sí · `validation-block.conf.tpl` y `lookups.conf.tpl` (línea `DESTINO`) |
| VAL-SP09 | Comparación exacta (trim) en catálogos | El comportamiento de mayúsculas del lookup real no está verificado | Normalizar los CSV de catálogo | sí · `ValidationProcessor.scala.tpl` (`OxygenCatalogo.existe`) |
| VAL-SP10 | Tests de la fuente sin clases del framework | Compilan y corren sin JAR (F-01) | Agregar prueba de integración cuando haya JAR | sí · `SourceProcessorSpec.scala.tpl` (cabecera) |

## Decisiones posteriores a la entrega

| Id | Fecha | Decisión | Dónde se refleja |
|---|---|---|---|
| ADR-01 | 2026-10-21 | **Catálogo que lanza → código interno de nivel 1.** Una excepción del catálogo (en un campo o en la regla `CorrespondenciaCatalogo`) ya no se trata como "valor ausente del catálogo" (`CATALOGO` con el código y la severidad del campo): sale de `FieldValidators.catalogo` y `RecordValidator.validarRegistro` la captura (`NonFatal`) y devuelve `FieldError("REGISTRO", codigos.interno, "1", "INTERNO")`. `validarRegistro` no lanza; el `try/catch` del processor queda como segunda barrera. El registro pierde sus demás errores de campo, pero se rechaza igual. Motivo: el motor distingue falla de infraestructura y dato inválido (`Util.getLookup` no captura), AC-04 pide "un `ObjectError` interno de severidad 1" y la opción falla de forma segura. Fuente: `Discovery/13_casos_de_prueba/decisiones/ADR_2026-10-21_fallas-priorizados.md` | `capa-a/FieldValidators.scala.tpl` (`catalogo`, `aplicarRegla`, `validarRegistro`), `capa-a/FieldValidatorsSpec.scala.tpl`, ejemplo regenerado |
| ADR-01b | 2026-11-24 | **Corrección de ADR-01 en Capa C.** `OxygenCatalogo` envolvía la consulta en `Try(...).getOrElse(false)`, así que una falla del lookup real se trataba como "valor fuera de catálogo" y el código interno nunca se activaba; las pruebas no lo veían porque usaban catálogos en memoria que lanzan directo. Ahora la lógica está en `CatalogoDeConsulta` (Capa A, sin `Try`), que propaga la excepción a `validarRegistro`, y `OxygenCatalogo` solo le pasa `Util.getLookup`. `FieldValidatorsSpec` prueba ese adaptador con una consulta que lanza. La prueba Python `test_oxygen_catalogo_no_oculta_fallas_del_lookup` solo revisa el texto generado; el comportamiento lo cubre `FieldValidatorsSpec` (y, desde AUD-V5, las pruebas de la fuente) (AUD-V6). Origen: comparación con la implementación manual de `oxygen-validation` | `capa-a/FieldSpec.scala.tpl`, `ValidationProcessor.scala.tpl`, `capa-a/FieldValidatorsSpec.scala.tpl`, `scripts/tests/test_render_templates.py` |
| ADR-05 | 2026-11-24 | **Miembros adicionales de `FileMetadata` desde la spec** (`archivo.metadata`, opcional): `min_frequency`, `sub_subscriber`, `additional_information` (firmas de la clase base del motor) y `miembros_adicionales` con miembro, tipo, valor y fuente para lo que la clase base conocida no tiene (p. ej. `fileLoadType`, que la implementación manual fija en `FULLLOAD` y que Synthesis filtra). El framework no inventa firmas: las declara la spec, tomadas de la implementación de referencia. Sin el bloque, el archivo generado no cambia. Origen: misma comparación | `assets/schema/source-spec.schema.yaml` (`archivo.metadata`), `FileMetadata.scala.tpl`, `render_metadata` y `check_metadata` en `render_templates.py` |
| ADR-04 | 2026-10-21 | **Superado el 2026-11-18 (versión 1.4.0, `scripts/DEPRECADOS.md`):** `render_templates.py` volvió a ser el generador vigente y, con `tests/test_render_templates.py`, está otra vez en la suite y en la cobertura (`.coveragerc` ya no lo omite). Texto original: las pruebas Python deprecadas salían de la suite (`collect_ignore` en `scripts/tests/conftest.py`) y `render_templates.py` de la cobertura. Sigue vigente que la cobertura se exige por script (`coverage report --include=scripts/<script>.py --fail-under=80`, P-5) | `scripts/DEPRECADOS.md`, `.coveragerc` |
| Plan #6 (P-2) | 2026-10-21 | `prevalidar_archivo.py` no modela E8 (footer) y lo declara; con `filas_footer > 0` falla con `SpecError` en vez de validar el footer como registro | docstring de `prevalidar_archivo.py`, `interpretar-salida.md` |
| Plan #5 (P-1 a) | 2026-10-21 | `verificar_hocon.py` separa el parseo HOCON (`pyhocon`, opcional) de la evaluación de reglas (función pura sobre `dict`), probada sin `pyhocon`. Migrarlo a Java con Typesafe Config queda como mejora posterior | `scripts/verificar_hocon.py`, `tests/test_verificar_hocon.py` |
| ADR-G1 | 2026-10-21 | **Supuesto activo = deja huella en el código generado.** Los 10 supuestos propios reciben `VAL-SP01..10`; toda marca es `ASSUMPTION <ID>:` con el ID en la misma línea; `manifest["supuestos"]` se deriva de las marcas de la salida (`render_all` de `render_templates.py`) y la columna "En el código generado" separa activos y no activos. Los supuestos de un elemento del source-spec (campo, regla, catálogo) se declaran en su lista opcional `supuestos` y el generador escribe la marca. Fuente: `Discovery/13_casos_de_prueba/decisiones/ADR_2026-10-21_fallas-G.md` | plantillas, `build_context` y `render_all` de `render_templates.py`, schema, spec SISBEN, este documento |
| Plan G #1 | 2026-10-21 | **Generación todo o nada (AC-22).** `render_all` arma en memoria las 12 plantillas, los CSV de catálogo y `manifest.json`, busca huecos, marcas sin ID y patrones prohibidos en todo, y solo escribe si no hay problemas | `render_all` en `render_templates.py`; pruebas `test_credencial_*` y `test_marca_sin_id_detiene_la_generacion` |
| Plan G #2 | 2026-10-21 | **Métricas con quality scores declarados (AC-VAL-54a).** `check_metrics` rechaza `qs.<X>` en `metricas[].expr` si `X` no está en `quality_scores` | `check_metrics` en `render_templates.py`; pruebas `test_metrica_*` |

## Auditoría de silencios y referencias obsoletas (2026-11-24)

Convención: todo error tragado a propósito en el código generado (`Try(...).getOrElse(...)`,
`Try(...).toOption`, `case NonFatal(_) =>`) lleva `SILENCIO-INTENCIONAL <ID>` en la misma línea o en el
comentario de arriba. Los no intencionales se corrigen. Lo revisan `tools/auditar-skills.py` y la prueba
`test_silencios_intencionales_marcados`.

| Id | Fecha | Qué estaba mal | Qué cambió | Dónde |
|---|---|---|---|---|
| AUD-V1 | 2026-11-24 | `shouldFilterRow` e `isHeaderRow` envolvían la Capa A en `Try(...).getOrElse(false)`. Si el filtro lanzaba, una fila que debía filtrarse (menores, D6.a/D14) se validaba | `debeFiltrar` y `esHeader` son totales: fila nula, vacía o truncada, posición < 1 y delimitador nulo o vacío (`Campos.separar`) no lanzan. El processor los llama sin `Try` | `capa-a/FieldValidators.scala.tpl`, `ValidationProcessor.scala.tpl`; pruebas en `FieldValidatorsSpec`, `SourceProcessorSpec` y `test_filtro_y_header_sin_try_en_el_processor` |
| AUD-V2 | 2026-11-24 | `asOfDate` caía en `Try(fileMetadata.receivedDate).getOrElse(0L)` y se pasaba como `Some(...)`: podía quedar en 0L (1970) y el motor no sabía que era un respaldo | Sin fecha válida se pasa `DomainData(None, ...)`. Evidencia: `Motor-Oxigen/oxygen-validation/src/main/scala/com/experian/edf/oxygen/validation/engines/Validator.scala:180-184` (`None` → `lineage.receivedDate` y `didCreateAsOfDateFromReceivedDate = true`); `lineage.receivedDate` = `fileMetadata.receivedDate` (`SparkValidator.scala:1112` y `:1133`). Mismo valor que antes, ahora con la marca. `Fechas.parsear` es total: el generador rechaza `[ ] { } # '` en `formatos_fecha` | `ValidationProcessor.scala.tpl`, `capa-a/FieldValidators.scala.tpl`, `java_to_py_date` en `render_templates.py`, VAL-SP02 |
| AUD-V3 | 2026-11-24 | Con `filas_footer > 0` el footer nunca se validaba: no había `validateFooter` ni `validate[].footer`. El motor sí los usa (`SparkValidator.scala:440-444`; default `ValidationSuccess` en `ValidationProcessor.scala:132-145`) | Con footer, la spec exige `codigos_especiales.footer`, `diagnostico_archivo.footer` y `fixture.footer`. Se generan `validateFooter` (sobre `processorContext.footers` y `RecordValidator.validarFooter`), `validate[].footer`, la fila del CSV de códigos, las filas de footer del fixture y una prueba Capa A. Sin footer la salida del processor y del HOCON no cambia | schema, `render_footer`, `check_footer`, `ValidationProcessor.scala.tpl`, `validation-block.conf.tpl`, `capa-a/FieldValidators.scala.tpl`; pruebas `test_footer_*` |
| AUD-V4 | 2026-11-24 | `isFooterRow` era `Try(...matches("<footer_regex>")).getOrElse(false)` y el regex solo se probaba con `re` de Python | `check_file_contract` compila `footer_regex` y rechaza en los tres regex del archivo la sintaxis solo de Python (`(?P<`, `(?P=`, `(?#`, `(?(`; lista corta, `REGEX_SOLO_PYTHON`). El patrón se compila una vez al crear el processor; `isFooterRow` queda sin `Try` y un patrón inválido falla en voz alta | `render_templates.py`; `test_footer_regex_invalido_o_con_sintaxis_solo_de_python_falla` |
| AUD-V5 | 2026-11-24 | El catálogo sintético de las pruebas de la fuente implementaba `Catalogo` directo: nunca ejercitaba `CatalogoDeConsulta` (adaptador de 1.8.0, `List("")` = sin coincidencia) | `TablasSinteticas.consultar` responde como `Util.getLookup` y `CatalogoSintetico = new CatalogoDeConsulta(...)`; prueba nueva del caso sin coincidencia | `SourceProcessorSpec.scala.tpl`; `test_catalogo_de_pruebas_sobre_catalogo_de_consulta` |
| AUD-V6 | 2026-11-24 | `test_oxygen_catalogo_no_oculta_fallas_del_lookup` solo revisa texto y no lo decía | Su docstring y ADR-01b dicen que el comportamiento lo cubre `FieldValidatorsSpec` | `scripts/tests/test_render_templates.py`, fila ADR-01b |
| AUD-V7 | 2026-11-24 | Referencias al generador Java deprecado como si fuera vigente | Apuntan a `render_all`, `build_context`, `check_metrics` y a las pruebas Python, o quedan marcadas como históricas | este documento, `assets/checklist-entrega.md` (j2, k1), `references/runbook-ejecucion.md` §5, cabeceras de `test_render_templates.py`, `test_verificar_hocon.py`, `test_prevalidar_archivo.py` y `render_templates.py` |
| AUD-V8 | 2026-11-24 | Silencios intencionales sin marca | Marcados: parseo de fecha y número en `FieldValidators` (VAL-RC04: error de campo), `epochMs` (VAL-SP02), `parsearNombre` (VAL-RC03), `NonFatal` de `validarRegistro` (ADR-01) y del processor (VAL-RC04) | plantillas; `test_silencios_intencionales_marcados` |

## Corrida E2E SISBEN (1.9.2, 2026-11-24)

Defectos del framework que encontró la auditoría independiente de la etapa Validation de la corrida E2E SISBEN
(`ejecuciones/sisben-e2e/auditorias/validation.md`). Las claves nuevas del source-spec son opcionales: sin ellas
la salida de una spec existente solo cambia por las correcciones de forma (AUD-V-01, -05, -06, -10, -17, -19).

| Id | Qué estaba mal | Qué cambió | Dónde |
|---|---|---|---|
| AUD-V-01 | `validate[]` y `metrics[]` sin `filePattern`; el motor hace `data.filePattern.get` (`SparkValidator.scala:1436-1449`, `:955`) | La plantilla emite `filePattern` en `validate[0]` y `metrics[0]`; la referencia la marca obligatoria; `verificar_hocon.py` falla si falta | `validation-block.conf.tpl`, `dsl-config-reference.md`, `verificar_hocon.py`; `test_aud_v01_*` |
| AUD-V-02 | Sin forma de declarar periodo no futuro, recepción no futura ni recepción desde el inicio del periodo | Claves opcionales `archivo.nombre.periodo_no_futuro`, `recepcion_no_futura`, `recepcion_desde_inicio_periodo`; `validarNombre(spec, ruta, ahora)` en la Capa A, `Instant.now()` en el processor, `--hoy` (fin del día en la zona de la fuente) en `prevalidar_archivo.py`; pruebas generadas desde el nombre del fixture | schema, `capa-a/*`, `SourceSpec.scala.tpl`, `ValidationProcessor.scala.tpl`, `SourceProcessorSpec.scala.tpl`, `render_templates.py`, `prevalidar_archivo.py` |
| AUD-V-03 | «Solo ceros» solo se podía expresar como lista literal y se comparaba sin normalizar | Tipo de regla `solo_ceros`: valor sin espacios, no vacío y formado solo por `0`. `valor_con_codigo_propio` no cambia | schema, `FieldSpec.scala.tpl` (`SoloCeros`), `FieldValidators.scala.tpl`, `prevalidar_archivo.py` |
| AUD-V-05 | `validatedRecordJson` publicaba los valores con trim | `comoMapa` usa `Campos.crudo` (valor recibido); la validación sigue con `Campos.valor` | `ValidatedEvent.scala.tpl`, `FieldValidators.scala.tpl` |
| AUD-V-06 | `file = "<nombre>.csv"` sin ruta; todos los lookups del motor usan `${oxygen.application.paths.lookupHome}` | `file = ${oxygen.application.paths.lookupHome}"/<archivo>"`; `catalogos[].archivo` es relativo a lookupHome. Cómo resuelve oxygen-core un nombre relativo sigue sin verificar | `lookups.conf.tpl`, `render_lookup`, `check_references`, `verificar_hocon.py` |
| AUD-V-08 | Marcas `ASSUMPTION` de valores que la spec de la ingesta ya fija | Clave opcional `supuestos_resueltos: [{id, fuente}]`: la marca pasa a `RESUELTO <ID> (<fuente>)` y sale de `manifest["supuestos"]` | schema, `resolver_supuestos` y `render_all` |
| AUD-V-09 | El fixture no podía declarar un segundo grupo de duplicados ni nombres inválidos; no había prueba del header fuente | `esperado.grupo_duplicado`, `fixture.nombres_invalidos` y la prueba del header con los alias | schema, `render_fixture_tests`, `render_name_tests`, `SourceProcessorSpec.scala.tpl` |
| AUD-V-10 | `skill: "edf-validation-stage"` | `SKILL_NAME = "edf-validation"` en el manifest y las cabeceras | `render_templates.py`, plantillas |
| AUD-V-11 | Regla dura 7, checklist §1 y Paso 1 mandaban escribir aquí las discrepancias de una ingesta | Van a la entrega al Orquestador; este documento es del mantenimiento de la skill | `SKILL.md`, `assets/checklist-entrega.md`, este documento |
| AUD-V-12 | Checklist y ADR-04 seguían dando por deprecado `render_templates.py` | Nota vigente en el checklist; notas anteriores y ADR-04 rotulados como históricos o superados | `assets/checklist-entrega.md`, ADR-04 |
| AUD-V-13 | `especificacion-sisben.md`, `interpretar-salida.md`, F-08 y el runbook desactualizados frente a la spec 1.0.2 | Rotulados como ejemplo histórico; el Paso 1 no los cita como fuente | `references/*`, `SKILL.md` |
| AUD-V-16 | La prevalidación aceptaba offsets que Java rechaza (`-2500`) | `java_zone_offset` replica `ZoneOffset.of` | `render_templates.py`, `prevalidar_archivo.py` |
| AUD-V-17 | `listDuplicateEvents` reconstruía `TaggedValidatedEvent` con 15 argumentos y perdía `fileTag` | Se pasan los 17 del motor (`Validator.scala:208-210`): `tve.inferredAsDeleted` y `tve.fileTag` (accesores usados por `Curator.scala:182-183`, mismo framework 24040573). Compilación no certificada (F-01) | `ValidationProcessor.scala.tpl` |
| AUD-V-19 | `codigos-error-<fuente>.csv` en la carpeta que se pega en el fork y sin `OxygenDuplicate` | Se genera en `docs/` y lista `OxygenDuplicate` cuando hay duplicados | `TEMPLATE_TARGETS`, `render_error_codes` |
| AUD-V-21 | Steering y skill con nombres distintos del inner | `validation-common-application-inner.conf`, como `07-contratos-frontera.md` §6 y la referencia del fork de Curation; el repo del motor no trae los inner | `.kiro/steering/10-piloto-sisben.md` |

## Corrida E2E SISBEN (1.9.5, 2026-11-25)

Lote pendiente de la re-auditoría de la propuesta (`ejecuciones/sisben-e2e/auditorias/validation.md` §7).

| Id | Qué estaba mal | Qué cambió | Dónde |
|---|---|---|---|
| AUD-V-27 (HF-16) | Sin tipo o sin número la clave de duplicado era el contenido de la fila (`hashCode`; en la réplica, la fila): dos filas idénticas sin número se agrupaban, contra VAL-SP03 / spec §6 | La clave es única por registro: `claveDuplicado(spec, valores, unico)`; el processor usa la sobrecarga con un UUID (el motor la llama una vez por registro, `Validator.dupCheckForKey`); la réplica usa el número de línea | `capa-a/FieldValidators.scala.tpl`, `SourceProcessorSpec.scala.tpl`, `FieldValidatorsSpec.scala.tpl`, `prevalidar_archivo.py`; `test_hf16_*` |
| AUD-V-28 (HF-17) | Ninguna prueba aislaba RA-02: el caso derivado y el de la Capa A violaban también RA-04 | RA-02 se aísla con un offset adelantado (mes siguiente, 1.º a las 00:00:00 `+1400`, reloj = último día del mes de referencia). Cada prueba generada del nombre comprueba además que sin su regla el nombre es válido; la Capa A prueba las tres reglas aisladas | `render_templates.py` (`nombres_de_prueba`, `viola_solo`, `render_name_tests`), `FieldValidatorsSpec.scala.tpl`; `test_hf17_*` |
| AUD-V-29 (HF-18) | `Campos.crudo` dejaba el `\r` de una línea CRLF en el último campo de `validatedRecordJson` (el motor, con `encoding`, parte el archivo por `"\n"`, `SparkValidator.scala:503`) | `Campos.separar` (y `split_row`) quitan SOLO el `\r` final antes de partir | `capa-a/FieldValidators.scala.tpl`, `prevalidar_archivo.py`; `test_hf18_*` |
