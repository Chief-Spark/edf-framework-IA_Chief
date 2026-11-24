# Decisiones, supuestos y puntos abiertos (Validation)

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
(decisión, hecho verificado o remisión a otro ID). La prueba `supuestosDelManifestEnDecisiones`
(`RenderTemplatesTest.java`) comprueba la biyección entre los "sí" y el manifest (AC-18b).

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
| F-08 | Código del salvoconducto | `SISBEN_ERR_023` provisional, severidad 1 | `reglas_entre_campos` RV-SIS-06 | sí · regla RV-SIS-06 de `<Fuente>Spec.scala` (`reglas_entre_campos[].supuestos`) |
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
| VAL-SP02 | Si `FECHA_ACTUALIZACION_CNS` es inválida, `asOfDate` = fecha de recepción | `domainData` siempre `Some`; el error ya queda diagnosticado | Plantilla del processor | sí · `ValidationProcessor.scala.tpl` (`asOfDate`) |
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
| ADR-01b | 2026-11-24 | **Corrección de ADR-01 en Capa C.** `OxygenCatalogo` envolvía la consulta en `Try(...).getOrElse(false)`, así que una falla del lookup real se trataba como "valor fuera de catálogo" y el código interno nunca se activaba; las pruebas no lo veían porque usaban catálogos en memoria que lanzan directo. Ahora la lógica está en `CatalogoDeConsulta` (Capa A, sin `Try`), que propaga la excepción a `validarRegistro`, y `OxygenCatalogo` solo le pasa `Util.getLookup`. `FieldValidatorsSpec` prueba ese adaptador con una consulta que lanza. Origen: comparación con la implementación manual de `oxygen-validation` | `capa-a/FieldSpec.scala.tpl`, `ValidationProcessor.scala.tpl`, `capa-a/FieldValidatorsSpec.scala.tpl` |
| ADR-05 | 2026-11-24 | **Miembros adicionales de `FileMetadata` desde la spec** (`archivo.metadata`, opcional): `min_frequency`, `sub_subscriber`, `additional_information` (firmas de la clase base del motor) y `miembros_adicionales` con miembro, tipo, valor y fuente para lo que la clase base conocida no tiene (p. ej. `fileLoadType`, que la implementación manual fija en `FULLLOAD` y que Synthesis filtra). El framework no inventa firmas: las declara la spec, tomadas de la implementación de referencia. Sin el bloque, el archivo generado no cambia. Origen: misma comparación | `assets/schema/source-spec.schema.yaml` (`archivo.metadata`), `FileMetadata.scala.tpl`, `render_metadata` y `check_metadata` en `render_templates.py` |
| ADR-04 | 2026-10-21 | Las pruebas Python deprecadas salen de la suite vigente (`collect_ignore` en `scripts/tests/conftest.py`) y `render_templates.py` sale de la cobertura (`.coveragerc`). La cobertura se exige por script (`coverage report --include=scripts/<script>.py --fail-under=80`, P-5) | `scripts/DEPRECADOS.md`, `.coveragerc` |
| Plan #6 (P-2) | 2026-10-21 | `prevalidar_archivo.py` no modela E8 (footer) y lo declara; con `filas_footer > 0` falla con `SpecError` en vez de validar el footer como registro | docstring de `prevalidar_archivo.py`, `interpretar-salida.md` |
| Plan #5 (P-1 a) | 2026-10-21 | `verificar_hocon.py` separa el parseo HOCON (`pyhocon`, opcional) de la evaluación de reglas (función pura sobre `dict`), probada sin `pyhocon`. Migrarlo a Java con Typesafe Config queda como mejora posterior | `scripts/verificar_hocon.py`, `tests/test_verificar_hocon.py` |
| ADR-G1 | 2026-10-21 | **Supuesto activo = deja huella en el código generado.** Los 10 supuestos propios reciben `VAL-SP01..10`; toda marca es `ASSUMPTION <ID>:` con el ID en la misma línea; `manifest["supuestos"]` se deriva de las marcas de la salida (`RenderTemplates.renderAll`) y la columna "En el código generado" separa activos y no activos. Los supuestos de un elemento del source-spec (campo, regla, catálogo) se declaran en su lista opcional `supuestos` y el generador escribe la marca. Fuente: `Discovery/13_casos_de_prueba/decisiones/ADR_2026-10-21_fallas-G.md` | plantillas, `Contexto.java`, `RenderTemplates.java`, schema, spec SISBEN, este documento |
| Plan G #1 | 2026-10-21 | **Generación todo o nada (AC-22).** `renderAll` arma en memoria las 12 plantillas, los CSV de catálogo y `manifest.json`, busca huecos, marcas sin ID y patrones prohibidos en todo, y solo escribe si no hay problemas | `RenderTemplates.java` |
| Plan G #2 | 2026-10-21 | **Métricas con quality scores declarados (AC-VAL-54a).** `Verificaciones.checkMetrics` rechaza `qs.<X>` en `metricas[].expr` si `X` no está en `quality_scores` | `Verificaciones.java` |
