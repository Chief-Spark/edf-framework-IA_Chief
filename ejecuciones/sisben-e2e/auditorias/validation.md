# Auditoría independiente — Etapa 1 (`validation`) · ingesta `sisben`

| Campo | Valor |
|---|---|
| Auditor | Agente auditor independiente (corrida autónoma del framework EDF 1.9.1, sin Kiro) |
| Fecha | 2026-11-24 |
| Alcance | Propuesta de `edf-validation` en `expedientes/sisben/propuestas/validation/` (18 archivos + `manifest.json`), registrada como ART-011 a ART-015; los 10 posibles defectos que reportó el generador; búsqueda de defectos no reportados |
| Fuentes válidas | `fuentes/hu-sisben.md` 1.0.2, `fuentes/spec-sisben.md` 1.0.2, `fuentes/muestra/`. Insumo aprobado: `insumos/source-spec-validation.yaml` (ART-010, igual al Anexo A salvo la cabecera de comentarios). Contexto aprobado: `analisis/*.md` |
| Referencias de control | `.kiro/skills/edf-validation/` (SKILL.md, `assets/checklist-entrega.md`, plantillas, esquema y scripts), `.kiro/steering/`, código del motor en `Motor-Oxigen/oxygen-validation` (más `oxygen-curation` para el consumo de `validatedRecordJson`) |
| Riesgo de la etapa | **N2** (riesgo típico "medio": `estado-ingesta.json`, etapa `validation`; definición de N2 en `06-riesgo-y-aprobaciones.md:35`). La aprobación exige artefacto, alternativas, qué se rompe si está mal y prueba asociada (`06:73`) |
| **Dictamen global** | **RECHAZADO (no aprobable tal como está; se corrige con cambios acotados en el framework y una regeneración).** La propuesta se puede reproducir byte a byte, es fiel al insumo y reproduce la spec §15. El problema es que el bloque HOCON generado le pasa al motor `validate[]` y `metrics[]` sin `filePattern`. El motor lee esa clave con `.get` y con eso cae en todos los archivos SISBEN (AUD-V-01, P0). Además hay cinco P1: no se implementan RA-02 a RA-04; RV-SIS-05 no normaliza; el `filePattern` de la spec impide el rechazo con ERR_024 del archivo sin marca de tiempo; el evento validado recorta los valores originales que Curation necesita; y los lookups no tienen ruta |

## 1. Dictamen por artefacto

| Artefacto | Dictamen | Razones |
|---|---|---|
| ART-011 processor + `SISBENSpec` + `SISBENValidatedEvent` + `SISBENFileMetadata` | **AJUSTES** | Cumple el contrato del motor. Constructor `(ProcessorContext)`, logger y catálogo `@transient lazy val`, sin `fileMetadata` en el constructor, solo `createValidatedEvents(rawRecord, ctx)`, `ObjectError` con argumentos con nombre, `DomainData` siempre `Some` y `asOfDate = None` si la fecha es inválida (`SISBENValidationProcessor.scala:42-112`). `listDuplicateEvents` agrega `OxygenDuplicate` como lo exige `EXTN` (`SparkValidator.scala:682-705`). Códigos de 14 caracteres: `decodeErrorObject` (`ValidationProcessor.scala:355-364`, se aplica siempre en `Validator.scala:209`) no los altera. Ajustes: RA-02 a RA-04 (AUD-V-02), el recorte de los valores en `validatedRecordJson` (AUD-V-05), la pérdida de `fileTag` en los duplicados (AUD-V-17) y las marcas `ASSUMPTION` de valores que la spec ya fija (AUD-V-08) |
| ART-012 Capa A (`FieldSpec`, `FieldValidators`, `FieldValidatorsSpec`) | **AJUSTES** | El orden de evaluación es el de spec §3 (`FieldValidators.scala:131-140`). Un código por campo. Las reglas entre campos se omiten si hay error previo (`:218`). Comparación sin tildes ni mayúsculas (`:36-40`). `validarRegistro` es total. Ajustes: `validarNombre` no implementa RA-02 a RA-04 (`:193-211`, AUD-V-02) y `ValorConCodigoPropio` compara el valor sin normalizar (`:224-227`, AUD-V-03) |
| ART-013 `SISBENValidationProcessorSpec` + fixture + catálogos sintéticos + `codigos-error-sisben.csv` | **AJUSTES** | Los 24 casos del Anexo A están todos y la prevalidación del fixture da los códigos esperados (§4). Los catálogos coinciden con el Anexo A. Faltan casos de la HU: duplicado con ceros a la izquierda, header con los nombres fuente, nombres de archivo inválidos de la HU y bordes de RV-SIS-05 (AUD-V-09). `codigos-error-sisben.csv` está en la carpeta de configuración y no lista `OxygenDuplicate` (AUD-V-19). Cuando se corrijan AUD-V-02/03/05 hay que agregar sus pruebas |
| ART-014 HOCON aditivo (`validation-configs-sisben.conf`, `lookups-sisben.conf`) | **RECHAZADO** | Las claves son correctas (`headerRows`/`footerRows`, `footerRows = 0`, sin `encoding`, `computeMetrics`, `dupKeyCheckEnabled`, `deDupVersion = "EXTN"`, `contexual.enabled = false`). Pero `validate[0]` y `metrics[0]` no tienen `filePattern` y el motor hace `data.filePattern.get` (AUD-V-01, P0). Además, los `file` de los lookups no llevan ruta, cuando el motor usa `${oxygen.application.paths.lookupHome}` (AUD-V-06), y el `filePattern` heredado de la spec deja sin rechazo el archivo sin marca de tiempo (AUD-V-04) |
| ART-015 `manifest.json` | **AJUSTES** | Los 18 sha256 coinciden con los archivos. Los `supuestos` coinciden 1:1 con las 17 marcas `ASSUMPTION` del código, y la compilación figura como "NO CERTIFICADA (F-01)". Ajustes: `skill: "edf-validation-stage"` no es el nombre de la skill (AUD-V-10). Tampoco se aprueba un manifiesto que declara como supuestos abiertos valores que la spec definitiva ya resuelve (AUD-V-08). Hay que regenerarlo de todos modos cuando se corrijan ART-011/012/014 |

## 2. Hallazgos

| ID | Artefacto | Clasificación | Sev. | Evidencia | Corrección sugerida |
|---|---|---|:-:|---|---|
| AUD-V-01 | ART-014 (`validation-configs-sisben.conf`) | framework (plantilla y referencia) → artefacto | **P0** | `validation-configs-sisben.conf:25-44`: `validate = [{ fileName, header, contexual }]` y `metrics = [{ file = [...] }]` **sin `filePattern`**. El motor elige el elemento con `fillPathFileName.matches(data.filePattern.get)` (`Motor-Oxigen/oxygen-validation/.../engines/SparkValidator.scala:1436-1442`) y lo llama en `validateFilename`, fuera de `Try` (`:385`), y en métricas (`:955`, `elem.filePattern.get`). El cargador devuelve `None` si la clave falta (`dsl-config-reference.md:3-5`), así que `None.get` lanza `NoSuchElementException` en cada archivo SISBEN. Todos los confs del motor la declaran (`oxygen-validation/src/main/resources/local/validation-application-local.conf:39-41,75-77`). Origen: `assets/templates/validation-block.conf.tpl:25,34` y `references/dsl-config-reference.md:25-26`, que la marcan como opcional (`filePattern?`) sin evidencia. `verificar_hocon.py:45-48` no lo revisa, por eso dio "HOCON OK". Confirmación final: `ValidationConfigs` de oxygen-core, que no está en el repo | En la plantilla: emitir `filePattern = "{{file_pattern_hocon}}"` dentro de `validate[0]` y de `metrics[0]`. Corregir `dsl-config-reference.md:25-26`. Agregar la regla a `verificar_hocon.py` (y su prueba). Regenerar |
| AUD-V-02 | ART-012 (`FieldValidators.scala`), ART-011 | framework (esquema, plantilla y `prevalidar_archivo.py`) | **P1** | Spec RA-02 a RA-04 (`spec-sisben.md:52-54`), HU §5.1 «Periodo futuro o recepción anterior al periodo se rechaza» (`hu-sisben.md:125-127`) y contexto aprobado (`contexto-ingesta.md:62`). `validarNombre` solo valida la regex y que la fecha se pueda leer (`FieldValidators.scala:193-211`). El esquema no da forma de declarar estas reglas (`assets/schema/source-spec.schema.yaml:129-150`, `additionalProperties: false`). `prevalidar_archivo.py:205-219` tampoco. Prueba (`--hoy 2026-11-24`): `SISBEN_EXPERIAN_202701.20270105093000-0500.txt` (periodo futuro), `…202601.20251205093000-0500.txt` (recepción antes del periodo) y `…202611.20261230093000-0500.txt` (recepción futura) salen `nombre_valido: true, VALIDADO`. Solo se rechaza el mes inválido (`202613`) | Extender el esquema, por ejemplo con `archivo.nombre.reglas: [periodo_no_futuro, recepcion_no_futura, recepcion_desde_inicio_periodo]`. Implementarlas en `parsearNombre`/`validarNombre`, con el reloj inyectado en la zona de la fuente, y en `prevalidar_archivo.py`. Agregar casos al fixture o a las pruebas. Regenerar |
| AUD-V-03 | ART-012 (`FieldValidators.scala`), ART-011 (`SISBENSpec.scala:160`) | framework + insumo | **P1** | RV-SIS-05: «`num_documento` formado solo por ceros» (`spec-sisben.md:106`); la longitud se mide sobre el valor normalizado (`:83`). `aplicarRegla` compara `Texto.comparable(v(campo))`, que es `Campos.valor` con trim pero **sin** `normalizar` (`FieldValidators.scala:215,224-227`), contra la lista literal de 1 a 15 ceros del insumo (`source-spec-validation.yaml`, RV-SIS-05). Prueba: `0000000000000000` (16 ceros) y `00 00` salen **sin código** (el campo normaliza a `0` y pasa la longitud), y `" 0000"` sí da ERR_029. Un documento en ceros queda aceptado | Framework: aplicar la `normalizacion` del campo en `ValorConCodigoPropio` (o crear un tipo de regla por patrón, `^0+$`) en Scala y en Python. Insumo/spec: expresar RV-SIS-05 como «normalizado = `0`» y no como lista. Agregar casos de borde |
| AUD-V-04 | ART-014 (`filePattern`), insumo | spec | **P1** | El `filePattern` exige la marca de tiempo (`spec-sisben.md:37`). Si un archivo no coincide con ningún `filePattern`, el motor registra «No validation configuration found», restaura el archivo y no emite marcador ni código (`SparkValidator.scala:176-190`). Por eso `SISBEN_EXPERIAN_202601.txt` nunca llega al processor y el escenario de la HU «Archivo sin marca de tiempo se rechaza con SISBEN_ERR_024» (`hu-sisben.md:120-123`; RA-01, `spec-sisben.md:51`) no se puede cumplir | Versión nueva de la spec y del Anexo A con un `filePattern` más amplio que la regex (por ejemplo `.*SISBEN_EXPERIAN_.*\.(txt\|TXT)`), para que `validateFilename` rechace con ERR_024, o ajustar el escenario de la HU. Lo decide el PO |
| AUD-V-05 | ART-011 (`SISBENValidatedEvent.scala`) | framework (`ValidatedEvent.scala.tpl`) → artefacto | **P1** | Los accesores usan `Campos.valor`, que hace trim (`SISBENValidatedEvent.scala:18-39`; `FieldValidators.scala:54-55`), y `write` publica esos valores como `validatedRecordJson` (`:71`; `Validator.scala:208`). Curation lee `validatedRecordJson` (`oxygen-curation/.../DefaultOxygenCurationProcessor.scala:24`). La spec pide `numeroDocumentoOriginal` y `tipoDocumentoSisben` = «valor original» (`spec-sisben.md:261-263`), y la HU §5.3 exige `numeroDocumentoOriginal = " 0001000000101"` con el espacio inicial (`hu-sisben.md:261-263`). Curation no puede recuperar ese espacio | Publicar en el evento los valores sin recortar (el trim de spec §3 es solo para validar) o agregar un campo con el original. Agregar una prueba de `write` con un valor con espacios |
| AUD-V-06 | ART-014 (`lookups-sisben.conf`) | framework (`lookups.conf.tpl`) → artefacto | **P1** (confianza media) | `file = "102_DANE_DIVIPOLA_STATES_CODES_NH.csv"` y similares, sin ruta (`lookups-sisben.conf:10,19,28,37,46,55`). Los confs del motor usan `file = ${oxygen.application.paths.lookupHome}"/…csv"` (`oxygen-curation/aws/conf/oxygen-lookup-aws.conf:5,13`), y la propia skill dice que los CSV van en `lookupHome` (`references/runbook-ejecucion.md:65`; `interpretar-salida.md:34`). Si el servicio no encuentra el archivo, el catálogo falla. Con ADR-01 eso da ERR_027 en todos los registros, o la carga cae. `OxygenLookupService` está en oxygen-core (no disponible). Queda marcado `ASSUMPTION F-12` | Emitir `file = ${oxygen.application.paths.lookupHome}"/<ruta declarada en la spec>/<archivo>"`, con la ruta tomada del insumo, y confirmar contra oxygen-core |
| AUD-V-07 | ART-011/012 (duplicados) | spec | P2 | Spec §6 conserva el de mayor `fec_actualizacion_cns` sin excluir los registros con error sev. 1 (`spec-sisben.md:119-125`). Un registro con ERR_021, o con ERR_022 por fecha **futura** (aun así `asOfDate = Some(futuro)`, `FieldValidators.scala:272-276`, aunque el comentario VAL-SP02 de `SISBENValidationProcessor.scala:101-103` diga lo contrario), le gana al válido: el válido queda `OxygenDuplicate` y ninguno se acepta. Prueba: una fila de 23 columnas (ERR_026) participa en el agrupamiento | Pregunta al PO para una versión nueva de la spec: excluir del agrupamiento los registros con sev. 1, o no dejar que conserven. Alinear el comentario VAL-SP02 |
| AUD-V-08 | ART-011, ART-015 | framework | P2 | La spec es definitiva y «no tiene preguntas abiertas» (`spec-sisben.md:8`), pero el código marca como supuestos valores que ya fija: F-11 «códigos provisionales» (`SISBENSpec.scala:188`; spec §8 `:156-157`), F-09 (`:191`; `:158`), VAL-SP01 (`:193`; `:159`), VAL-PA04 «inferido» (`SISBENValidationProcessor.scala:56`; spec §1 `:22`), VAL-SP07 (`SISBENFileMetadata.scala:101`; `:39`), F-07 (`:106`; `:38`), F-14 (`FieldValidators.scala:278`; `:122`), VAL-SP03 (`:259`; `:124`) y F-12 en lo que define §9. `manifest.json` declara 17 supuestos | Que la plantilla escriba la marca solo cuando el insumo no traiga el valor con procedencia de la spec, por ejemplo con un indicador `resuelto_por_spec` o la lista `supuestos` por elemento que ya prevé ADR-G1 |
| AUD-V-09 | ART-013 | insumo (fixture del Anexo A) + framework (`fixture.txt.tpl`, `SourceProcessorSpec.scala.tpl`) | P2 | Faltan casos de la HU §5.1: (a) duplicado `1000000199` / `0001000000199` (`hu-sisben.md:215-218`). El Anexo A usa el mismo número en los dos; la lógica normaliza bien (en la prevalidación, las líneas 5-6 dan un duplicado y uno conservado), pero no hay prueba. (b) Header con los nombres fuente (`spec-sisben.md:59-61`): `HeaderCorrecto` y el header del fixture usan los nombres EDF (`SISBENValidationProcessorSpec.scala:103`; fixture línea 1). Además `validarHeader` acepta los dos juegos (`aliasHeader`, `SISBENSpec.scala:36-145`), algo más laxo que RA-05. (c) Nombres de archivo de la HU (`SISBEN_EXPERIAN_202601.txt`, prefijos `RT_`/`EP_`, periodo futuro): solo se prueba `OTRO_ARCHIVO.txt` (`:255-256`). (d) La fila de 23 columnas **sí** está cubierta por la prueba genérica (`:213`), aunque no por el fixture | Agregar al bloque `fixture` del insumo (versión nueva de la spec) el duplicado con ceros y los nombres inválidos. En la plantilla, generar la prueba del header con los nombres fuente (`alias`) además de los de dominio |
| AUD-V-10 | ART-015 y cabeceras de los `.scala`/`.conf` | framework | P2 | `manifest.json` `"skill": "edf-validation-stage"`; `render_templates.py:852`; 11 cabeceras «Generado por el skill edf-validation-stage». El nombre es `edf-validation` (`SKILL.md:2`) | Corregir la constante y las plantillas. Regenerar |
| AUD-V-11 | — | framework | P2 | La regla dura 7 (`SKILL.md:59-60`), el checklist §1 (`assets/checklist-entrega.md:10`) y el Paso 1 (`SKILL.md:76-77`) piden escribir las discrepancias en `references/decisiones-y-abiertos.md`, dentro de la skill. En una ingesta la skill escribe en `expedientes/<id>/propuestas/validation/` (`SKILL.md:31`; `00-framework-edf.md:72`) | Registrar las discrepancias de una ingesta en el expediente (por ejemplo en la entrega al Orquestador) y dejar `decisiones-y-abiertos.md` para el mantenimiento de la skill |
| AUD-V-12 | — | framework | P2 | `checklist-entrega.md:62-66` («`render_templates.py` … quedan deprecados» frente al generador Java), `:71` y `:84-86`. Lo contradicen `scripts/DEPRECADOS.md:3-12` (Python vigente desde la 1.4.0, `test_render_templates.py` en la suite y en la cobertura), `render_templates.py:4` y `SKILL.md:62-68`. ADR-04 (`decisiones-y-abiertos.md:90`) sigue diciendo que `render_templates.py` sale de la cobertura, sin marcarse como superado | Agregar al checklist una nota vigente de la 1.4.0 y rotular las anteriores como históricas. Marcar ADR-04 como superado por DEPRECADOS 2026-11-18 |
| AUD-V-13 | — | framework | P2 | El Paso 1 cita `references/especificacion-sisben.md` (`SKILL.md:89`), que está desactualizada frente a la spec 1.0.2: prefijos `RT_/EP_` (`:18-19`), todos los campos en sev. 1 con `SISBEN_ERR_001…` (`:31-52`), ERR_023 a ERR_027 como supuestos (`:63-67`). También: `interpretar-salida.md:35` (`SISBEN_ERR_018`, hoy `SISBEN_INC_018`), `decisiones-y-abiertos.md:29` (F-08 «provisional», regla RV-SIS-06; hoy es RV-SIS-04 y definitiva) y `runbook-ejecucion.md:124` («7 tablas»; la spec tiene 6) | Rotular `especificacion-sisben.md` como «ejemplo histórico, no es la spec de la ingesta», o quitarla del Paso 1. Actualizar los demás |
| AUD-V-14 | — | spec | P2 | La cabecera de la spec cita `render-templates.jar --solo-verificar` (`spec-sisben.md:11`); el generador Java está deprecado. Ya conocido como HS-01 / AUD-E0-08. El conteo «18 archivos» coincide con lo que genera el generador Python (§4) | Corregir en la próxima versión de la spec |
| AUD-V-15 | — | framework (steering) | P2 | `07-contratos-frontera.md:71` («Quality Score por campo»), cuando la spec define un `RQS` por registro (`spec-sisben.md:131`). `07:110` («tipos documentales 7 y 10 se rechazan sin sumar al puntaje»), cuando los menores son los tipos SISBEN `1` y `2` (`spec-sisben.md:117`), y `7` y `10` son sus `cod_experian` (`:204-205`). Además el tipo SISBEN `7` es ERR_023, sev. 1, y sí cuenta (`:105`) | Corregir `07` §4 y §5.3: «RQS por registro» y «menores: tipos `1` y `2` del archivo (SISBEN)» |
| AUD-V-16 | — | framework (`prevalidar_archivo.py`) | P2 | La prevalidación no valida el offset: `SISBEN_EXPERIAN_202601.20260205093000-2500.txt` sale `nombre_valido: true` (§4), mientras que Scala lo rechaza con `ZoneOffset.of` (`FieldValidators.scala:204`, rango ±18:00). La réplica Python deja de ser equivalente | Validar el offset en `validate_name` (`prevalidar_archivo.py:205-219`) |
| AUD-V-17 | ART-011 (`listDuplicateEvents`) | motor / framework | P2 | El processor reconstruye `TaggedValidatedEvent` con 15 argumentos (`SISBENValidationProcessor.scala:131-134`). El motor lo crea con 17, incluido `fileMetadata.fileTag` (`Validator.scala:208-210`), así que los duplicados pierden `fileTag`. El motor hace lo mismo en RANDOM y AS-IS (`SparkValidator.scala:664-675`); el impacto es bajo porque `tipoArchivo` es constante | Pasar `tve` completo (copiar también los argumentos 16 y 17) cuando la firma de oxygen-core se confirme |
| AUD-V-18 | — | spec | P2 (observación) | La HU pide que el log incremente `REGISTROS MENORES DE EDAD` (`hu-sisben.md:140`; `spec-sisben.md:117`). En Validation el filtro no deja rastro: las filas filtradas se descartan sin contador (`SparkValidator.scala:1128-1130`; `oxygenCounters` no se usa). No hay evidencia de cómo lo obtendrá el reporting estándar | Que la spec diga de dónde sale el conteo (por ejemplo total leído − header − validados − vacías) o que el processor lo emita con `oxygenCounters` |
| AUD-V-19 | ART-013 (`codigos-error-sisben.csv`) | artefacto / framework | P2 | Es un catálogo documental, pero está en `env/application-conf/innersource/propuesta/`, junto al HOCON que se pega en el fork. Le falta `OxygenDuplicate`, que la spec lista en §8 (`spec-sisben.md:162`) | Moverlo a `docs/` o a `src/test/resources/<fuente>/` y agregar la fila `OxygenDuplicate` |
| AUD-V-20 | ART-010 (informativo) | insumo | P2 | La cabecera dice «Estado: PROPUESTO … Etapa 0 NO aprobada todavía: este insumo es un BORRADOR» (`source-spec-validation.yaml:2-3`), cuando ya está aprobado (APR-003/APR-004). `spec_version: sisben-iv-1.0.0` pasa a todas las cabeceras y al manifiesto, aunque la spec es la 1.0.2 (AUD-E0-09, conocido) | Actualizar la cabecera de comentarios. El contenido no cambia |
| AUD-V-21 | ART-014 (cabecera) | framework | P2 | El destino figura como `validation-common-application-inner.conf` (`validation-configs-sisben.conf:4`; 6 apariciones en la skill), pero el steering dice `validation-common-application.inner.conf` (`10-piloto-sisben.md:30`) | Confirmar el nombre real en el fork y unificarlo |

## 3. Evaluación de los 10 posibles defectos del generador

| # | Posible defecto | Veredicto | Sev. | Clasificación | Evidencia propia |
|:-:|---|---|:-:|---|---|
| 1 | RA-02, RA-03 y RA-04 no implementadas | **Real** | P1 | framework (esquema, plantilla Capa A y prevalidación) | AUD-V-02. Lo comprobé con tres nombres que deberían rechazarse y salen `VALIDADO`. Solo se rechaza el mes inválido (`202613`), porque `YearMonth.parse` falla, y por eso RA-02 queda cubierta solo en parte |
| 2 | Regla dura 7 y checklist §1 escriben dentro del framework | **Real** | P2 | framework | AUD-V-11 (`SKILL.md:59-60,76-77`; `checklist-entrega.md:10` frente a `SKILL.md:31`) |
| 3 | Las plantillas marcan como `ASSUMPTION` valores que la spec resuelve | **Real y más amplio de lo reportado** | P2 | framework | AUD-V-08. A F-09, F-11, VAL-SP01, VAL-PA04 y F-12 se suman VAL-SP07, F-07, F-14 y VAL-SP03, que la spec también resuelve (§2 y §6) |
| 4 | El checklist da `render_templates.py` por deprecado; ADR-04 sin marcar superado | **Real** | P2 | framework | AUD-V-12 (`checklist-entrega.md:62-66,71,84-86`; `decisiones-y-abiertos.md:90` frente a `DEPRECADOS.md:3-12`) |
| 5 | Referencias desactualizadas frente a la spec 1.0.2 | **Real** | P2 | framework | AUD-V-13. A las tres citadas se suma `runbook-ejecucion.md:124` («7 tablas») |
| 6 | `skill: "edf-validation-stage"` en el manifiesto | **Real** | P2 | framework | AUD-V-10 (`render_templates.py:852` y 11 cabeceras) |
| 7 | La cabecera de la spec cita el JAR y «18 archivos» (HS-01) | **Real en cuanto al JAR; descartado en cuanto a «18 archivos»** | P2 | spec | AUD-V-14. La regeneración da «Generados 18 archivos» y el manifiesto lista 18 |
| 8 | RV-SIS-05 compara el valor sin normalizar | **Real** | P1 | framework + insumo | AUD-V-03. 16 ceros y `00 00` salen sin código, mientras que `" 0000"` sí da ERR_029 |
| 9 | Faltan casos de la HU en el fixture | **Real en parte** | P2 | insumo + framework | AUD-V-09. Faltan el duplicado con ceros, el header con nombres fuente y los nombres inválidos. La fila de 23 columnas sí está probada (`SISBENValidationProcessorSpec.scala:213`), aunque no en el fixture. La lógica de duplicados con ceros funciona (prevalidación) |
| 10 | `07-contratos-frontera.md:110` y `:71` contradicen la spec | **Real** | P2 | framework (steering) | AUD-V-15 |

Defectos que el generador no vio: AUD-V-01 (P0), AUD-V-04, AUD-V-05 y AUD-V-06 (P1), y AUD-V-07, -16, -17, -18, -19, -20 y -21 (P2).

## 4. Verificaciones corridas

Raíz: `/home/user/edf-framework-IA_Chief`. EXP = `ejecuciones/sisben-e2e/expedientes/sisben`. Scratchpad: `/tmp/claude-0/-home-user-edf-framework-IA-Chief/17fd989e-8dfc-5ea8-ad37-44be188bb6cf/scratchpad/aud-val/`. No compilé Scala ni Java, no usé git y no instalé nada globalmente.

| # | Comando | Resultado |
|:-:|---|---|
| v1 | `python3 .kiro/skills/edf-validation/scripts/render_templates.py --spec EXP/insumos/source-spec-validation.yaml --solo-verificar` | `source-spec OK (22 campos, 29 códigos).` rc 0 |
| v2 | `render_templates.py --spec … --out <scratchpad>/aud-val/gen` + `diff -r EXP/propuestas/validation <scratchpad>/aud-val/gen` | «Generados 18 archivos». `diff` sin diferencias (rc 0): la propuesta es **reproducible** |
| v3 | Extracción del bloque YAML del Anexo A + `diff` con el insumo; `sha256sum` del insumo | Solo difiere la cabecera de comentarios (23 líneas). sha256 `7a8d7a6e…8dcbd8c` = el hash registrado de ART-010 |
| v4 | sha256 de cada archivo frente a `manifest.json` | 18/18 OK |
| v5 | `prevalidar_archivo.py --spec … --archivo EXP/fuentes/muestra/SISBEN_EXPERIAN_202601.20260205093000-0500.txt --hoy 2026-11-24 --detalle` | 20 validados + 1 filtrado = 21. 1 con sev. 1 (línea 21, `SISBEN_ERR_016`). 0 duplicados. RQS 0.95. `ACEPTARIA`, 19 aceptados. **Igual a la spec §15** |
| v6 | `prevalidar_archivo.py` sobre el fixture generado (`--lookups` con los CSV generados, `--hoy 2026-02-05`) | 21 validados, 3 filtrados, 1 duplicado. Cada caso da el código esperado del Anexo A (ERR_023/014/029/016/017/028/004/022/021/026, INC_018/020; DUP_ANTIGUO marcado). RQS 0.52381 |
| v7 | `prevalidar_archivo.py` sobre casos de borde propios (scratchpad `aud-val/casos/`) | 16 ceros y `00 00`: sin ERR_029 (AUD-V-03). `" 0000"`: ERR_029. Duplicado `1000000199`/`0001000000199`: se marca el más antiguo (correcto). `a` en Grupo: ERR_016. `ppt`: ERR_014. Nombres `202701.2027…`, `202601.2025…` y `202611.20261230…`: VALIDADO (AUD-V-02). `202613…`, sin marca de tiempo y `RT_…`: MARCADOR. Offset `-2500`: VALIDADO (AUD-V-16) |
| v8 | `pip install --target <scratchpad>/py pyhocon` | Falló la construcción del wheel (`install_layout`). Alternativa: `pip download` de pyhocon 0.3.63 (sdist) y pyparsing 3.3.3 (wheel), extraídos en el scratchpad y usados por `PYTHONPATH` |
| v9 | `PYTHONPATH=<scratchpad>/py python3 .kiro/skills/edf-validation/scripts/verificar_hocon.py --generado EXP/propuestas/validation --fuente sisben --archivo-ejemplo SISBEN_EXPERIAN_202601.20260205093000-0500.txt` | `HOCON OK` (rc 0). No detecta AUD-V-01: no revisa `filePattern` dentro de `validate[]` ni de `metrics[]` (`verificar_hocon.py:36-53`) |
| v10 | `PYTHONPATH=<scratchpad>/py python3 -m pytest scripts/tests -q -p no:cacheprovider` (desde la skill) | 165 passed. Nota: el intérprete pudo actualizar los `__pycache__/` que ya existían en `scripts/` |
| v11 | `python3 tools/verificar-artefactos-etapa.py -h` | Solo subcomandos `synthesis` y `statecalc`: **no hay** verificación de Validation |
| v12 | `python3 tools/auditar-skills.py` | `AUDITORIA OK` (0 silencios sin marca, 0 referencias obsoletas, 46 decisiones sin rastro, informativo) |
| v13 | Lectura del motor: `ValidationProcessor.scala`, `ValidatedEventValueObject.scala`, `Validator.scala`, `SparkValidator.scala` (`getValidateInfo`, `addErrorCodesForDuplicate`, selección de configuración, lectura del header), `Util.getLookup`, `DefaultOxygenCurationProcessor.scala` | La base de AUD-V-01, -04, -05, -06 y -17. `ValidationConfigs`, `LookupElements`, `TaggedValidatedEvent` y `OxygenLookupService` (oxygen-core) **no están en el repo**: AUD-V-01 y AUD-V-06 se confirman del todo solo con esas fuentes |
| v14 | Cálculo de epochs (HU §5.1/§5.3) | `fileAsOfDate` 2026-01-31 Bogotá = 1769835600000; recepción = 1770301800000; CNS 2026-01-10 = 1768021200000. La lógica de `SISBENFileMetadata` y de `asOfDate` los produce |

## 5. Qué hace falta para aprobar

1. **P0 (bloqueante): AUD-V-01.** Corregir `validation-block.conf.tpl` para que emita `filePattern` en `validate[0]` y en `metrics[0]`, corregir `dsl-config-reference.md:25-26`, agregar la regla a `verificar_hocon.py` con su prueba, y regenerar. Si se puede, confirmar contra `ValidationConfigs` de oxygen-core.
2. **P1 del framework, con regeneración:** AUD-V-02 (RA-02 a RA-04 en el esquema, Capa A y prevalidación), AUD-V-03 (normalización en `ValorConCodigoPropio`), AUD-V-05 (valores originales en `validatedRecordJson`) y AUD-V-06 (ruta `lookupHome` en los lookups). Cada uno con su prueba en `FieldValidatorsSpec`/`SourceProcessorSpec` y en `scripts/tests`.
3. **P1 de la spec, decisión del PO:** AUD-V-04 (`filePattern` frente al escenario «sin marca de tiempo → ERR_024»). Pide una versión nueva de la spec y del Anexo A, o ajustar la HU. Mientras tanto, registrarla en el expediente como pregunta abierta de `validation`.
4. Volver a correr v1, v2 (la nueva propuesta debe ser reproducible), v5 (debe seguir dando spec §15), v6, v7 (los casos de borde deben dar el resultado de la spec) y v9.
5. Los P2 no bloquean la aprobación N2. Conviene resolverlos en el mismo ciclo los que cambian la salida generada (AUD-V-08, -10, -16, -17, -19), para no regenerar dos veces. Los documentales (AUD-V-11 a -15, -21) y los de la spec (AUD-V-07, -18, -20) van a la bitácora y a la próxima versión de la spec o del framework.
6. Al registrar la aprobación N2: artefacto, alternativas consideradas (por ejemplo, para AUD-V-04: ampliar `filePattern` o ajustar la HU), qué se rompe si está mal (AUD-V-01: ningún archivo SISBEN se valida) y prueba asociada (`06:73`).

## 6. Insumo v2

| Campo | Valor |
|---|---|
| Alcance | Solo ART-010 v2: `expedientes/sisben/insumos/source-spec-validation.yaml` (242 líneas, sha256 `46dfbdef…c187b15`). La propuesta generada no se audita aquí: se regenerará |
| Contra qué | Anexo A de `spec-sisben.md` 1.0.2 (bloque YAML desde la línea 388; no cambió desde la primera auditoría), autorización INT-012 (`registro-interacciones.md:93-99`), esquema 1.9.2 (`assets/schema/source-spec.schema.yaml`) y las marcas `ASSUMPTION` de `assets/templates/` |
| **Dictamen del insumo** | **APROBADO, con observaciones P2 no bloqueantes** (AUD-V-22 a AUD-V-27). El diff contra el Anexo A contiene solo lo que autorizó INT-012 y cada cambio cita su regla. Los 9 `supuestos_resueltos` los fija de verdad la spec. Los casos nuevos siguen HU §5.1. Las verificaciones dan lo esperado. Conviene corregir la cabecera (AUD-V-22, -23) en la misma edición en que se registre la re-aprobación |

### 6.1 Diff contra el Anexo A

`diff <Anexo A> insumo` solo muestra:

1. La cabecera de comentarios (líneas 1-40). Se declara v2 y PROPUESTO y lista los 7 cambios con su cita.
2. `supuestos_resueltos`, nuevo en la raíz (`:44-56`).
3. `periodo_no_futuro`, `recepcion_no_futura` y `recepcion_desde_inicio_periodo: true`, nuevos en `archivo.nombre` (`:83-87`): spec RA-02 a RA-04 y la autorización (2) de INT-012.
4. RV-SIS-05 pasa de la lista literal a `tipo: solo_ceros` (`:123-124`), que es la única línea del Anexo A modificada: autorización (2).
5. Los casos `DUP_CEROS_ANTIGUO` y `DUP_CEROS_RECIENTE`, agregados después de los 24 del Anexo A, que no cambian (`:231-233`).
6. `fixture.nombres_invalidos`, nuevo (`:235-242`).

`spec_version` sigue en `sisben-iv-1.0.0`, como pidió (5) de INT-012. No hay ningún cambio sin autorizar.

### 6.2 `supuestos_resueltos` frente a las marcas de las plantillas

| ID | Marca en la plantilla | Fuente citada | ¿La spec lo resuelve? |
|---|---|---|---|
| F-07 | «tipo de archivo según el prefijo del nombre» (`FileMetadata.scala.tpl:34`) | §2 | Sí: siempre `REGULAR`; `RT_`/`EP_` se rechazan (`spec-sisben.md:38`) |
| F-09 | «fila con un número de columnas distinto → error de nivel 1» (`SourceSpec.scala.tpl:53`) | §2, §8 | Sí (`:44`, `:158`) |
| F-11 | códigos de nombre y header (`SourceSpec.scala.tpl:50`) y expresiones `validate[]` (`validation-block.conf.tpl:25`) | §2, §7 RQ-05, §8 | Sí. La segunda marca describe un hecho del motor que el código confirma (`SparkValidator.scala:229`); la expresión la fija RQ-05 (`:135`) |
| F-12 | «archivo, columnas y delimitador de cada tabla; confirmar con el dueño» (`lookups.conf.tpl:7`) | §9 y Anexo A | Sí en nombres, columnas, llave y delimitador (`:166-237`). Ver AUD-V-26 sobre la ubicación |
| F-14 | «más reciente = mayor asOfDate; desempate por mayor eventId» (`ValidationProcessor.scala.tpl:122`; `capa-a/FieldValidators.scala.tpl:302`) | §6 | Sí (`:122`) |
| VAL-PA04 | `outputEventType = fuente.event_type` (`ValidationProcessor.scala.tpl:56`) | §1 | Sí (`:22`) |
| VAL-SP01 | código de error interno (`SourceSpec.scala.tpl:55`) | §8 | Sí (`:159`) |
| VAL-SP03 | «campo de la clave vacío → clave única por contenido de la fila» (`capa-a/FieldValidators.scala.tpl:283`) | §6 | Sí, la regla («sin tipo o sin número no se agrupa», `:124`). Ver AUD-V-27 sobre el mecanismo |
| VAL-SP07 | fecha de corte (`FileMetadata.scala.tpl:29`) | §2 | Sí (`:39`) |

Quedan bien fuera de la lista F-13 (DIVIPOLA sintético), F-04, VAL-SP02, VAL-SP08, VAL-SP09, VAL-SP10 y VAL-PT*: la spec no los fija.

### 6.3 Casos del fixture frente a HU §5.1

- **`DUP_CEROS_*`:** siguen el escenario «Duplicados por tipo y número» (`hu-sisben.md:214-218`). Tipo `3`; el número sin ceros va con `01/01/2026` y se marca, el número con ceros va con `15/01/2026` y se conserva; `codigos: []`. Usan `…198` en vez del `…199` de la HU para no formar grupo con `DUP_ANTIGUO`/`DUP_RECIENTE`, y lo dicen en `:231`. Es correcto.
- **`nombres_invalidos`:** cubren `RT_`/`EP_` (RA-01; R-02 `hu-sisben.md:51`), periodo futuro (RA-02), recepción antes del periodo (RA-04), recepción futura (RA-03) y el archivo sin marca de tiempo (`hu-sisben.md:120-123`). Este último lleva la desviación PA-003 (`:241`).
- **No aplicado:** el header con los nombres fuente (AUD-V-09 b), porque el esquema no tiene clave para eso. Se verificará en la propuesta regenerada (la prueba `HeaderFuente`).

### 6.4 Verificaciones corridas (insumo v2)

| # | Comando | Resultado |
|:-:|---|---|
| w1 | `render_templates.py --spec EXP/insumos/source-spec-validation.yaml --solo-verificar` | `source-spec OK (22 campos, 29 códigos).` rc 0. Incluye la comprobación de que la réplica Python rechaza `nombres_invalidos` |
| w2 | `prevalidar_archivo.py --spec … --archivo EXP/fuentes/muestra/… --hoy 2026-11-24 --detalle` | 20 validados, 1 filtrado, 1 sev. 1 (línea 21, `SISBEN_ERR_016`), 0 duplicados, RQS 0.95, `ACEPTARIA` 19. **Igual a spec §15** |
| w3 | Nombres de v7 sobre copias en `<scratchpad>/aud-val/casos/` (`--hoy 2026-11-24`) | `202701.2027…`, `202601.20251205…`, `202611.20261230…`, `202613…`, sin marca, `RT_…` y offset `-2500` dan todos `MARCADOR errorInFileName`, `eventType -1002`, `SISBEN_ERR_024`. Así se cierran en la réplica AUD-V-02 y AUD-V-16 |
| w4 | Controles de borde (`<scratchpad>/aud-val/casos2/`, `--hoy 2026-11-24`) | `202611.20261124093000` y `202611.20261101000000`: VALIDADO. `202611.20261031235959` (un segundo antes del periodo) y `202612.…`: MARCADOR. No hay falsos rechazos |
| w5 | RV-SIS-05 (`casos/`, mismo archivo de v7) | 16 ceros: ERR_029. `00 00`: ERR_029. `" 0000"`: ERR_029. Duplicado con ceros (líneas 5-6): se marca el más antiguo. AUD-V-03 queda cerrado en la réplica |
| w6 | `reglas_nombre_violadas` de `render_templates.py` sobre `fixture.nombres_invalidos` con `fecha_referencia` 2026-02-05 (`python3 -I`, sin escribir en el repo) | `RT_`, `EP_` y sin marca: no cumplen la regex. `202603…`: `periodo_no_futuro` y `recepcion_desde_inicio_periodo`. `…20251231…`: `recepcion_desde_inicio_periodo`. `…20260206…`: `recepcion_no_futura`. El nombre del fixture: ninguna |

### 6.5 Hallazgos nuevos

| ID | Artefacto | Clasificación | Sev. | Evidencia | Corrección sugerida |
|---|---|---|:-:|---|---|
| AUD-V-22 | ART-010 (cabecera) | insumo | P2 | `:24` («Todos los valores son [C]») y `:37` («Sin `[A:*]` ni `[PM]` en este insumo»), cuando `:83-84` declara un supuesto del aprobador: «mes actual» = mes en America/Bogota (INT-012 (2); RA-02, `spec-sisben.md:52`, no dice la zona). El esquema no tiene `supuestos` en `archivo.nombre`, así que este supuesto no dejará una marca con ID en el código ni en `manifest["supuestos"]` (ADR-G1) | Marcarlo `[A:INT-012]` en la cabecera y quitar «Sin `[A:*]`». En el framework, admitir `supuestos` en `archivo.nombre` para que deje huella |
| AUD-V-23 | ART-010 (cabecera) | insumo | P2 | `:37-38` («Ninguna pregunta abierta lo afecta: PA-001 … PA-002 …») queda desactualizado: el propio insumo cita PA-003 (`:241`), y PA-003, PA-004 y PA-005 están abiertas en `validation` (`estado-ingesta.json`, `preguntas_abiertas`) | Citar PA-003 (no bloqueante, afecta el caso sin marca de tiempo) y decir que PA-004 y PA-005 no cambian el insumo |
| AUD-V-24 | Registro de ART-010 | framework (registro del orquestador) | P2 | `estado-ingesta.json` (ART-010) conserva `hash` `sha256:7a8d7a6e…` (v1) con `estado_artefacto: prevalidado`, pero el archivo v2 da `46dfbdef…` | Actualizar el hash al registrar la v2 y re-aprobarla |
| AUD-V-25 | ART-010 (`nombres_invalidos`) | spec | P2 (informativo) | El caso RA-02 (`202603.20260205…`) también viola RA-04 (w6). No hay forma de aislar RA-02: un periodo futuro obliga a violar RA-03 o RA-04, porque una recepción ≥ el primer día de un mes futuro es futura. RA-02 solo agrega, aparte, que el mes sea válido | Nada en el insumo. Si se quiere probar RA-02 sola, hacerlo con una prueba unitaria de la Capa A con las otras dos reglas apagadas |
| AUD-V-26 | ART-010 (`supuestos_resueltos` F-12) | spec | P2 (informativo) | La spec fija nombres, columnas, llave y delimitador (§9), pero no la ubicación del CSV dentro de `lookupHome` (raíz o subcarpeta; §9 solo dice «se cargan desde Collibra a S3 con estos nombres», `spec-sisben.md:166`). Con 1.9.2 el HOCON emitirá `${oxygen.application.paths.lookupHome}"/<archivo>"` con `archivo` en la raíz | Aceptable dar F-12 por resuelto. Al desplegar, confirmar que Collibra deja los seis CSV en la raíz de `lookupHome` |
| AUD-V-27 | Propuesta futura (VAL-SP03) | framework | P2 (para la re-auditoría) | La regla «un registro sin tipo o sin número no se agrupa» (`spec-sisben.md:124`) queda resuelta, pero el mecanismo genera la clave con `valores.mkString("\u0001").hashCode` (`FieldValidators.scala:266` en la propuesta v1). Dos líneas idénticas sin número reciben la misma clave y se agrupan, y un choque de `hashCode` puede unir registros distintos. Impacto bajo: los dos tienen ERR_015 | Revisarlo en la propuesta regenerada (por ejemplo, usar `recordHash`/`eventId` o un contador). No cambia el insumo |

### 6.6 Qué sigue

- Re-aprobación N2 de ART-010 v2. Se recomienda corregir antes AUD-V-22 y AUD-V-23 (solo comentarios) y actualizar el hash (AUD-V-24).
- Regenerar la propuesta con el framework 1.9.2 y volver a auditarla: AUD-V-01, -05, -06 y la prueba `HeaderFuente` (AUD-V-09 b) solo se pueden verificar en la salida generada.

## 7. Re-auditoría de la propuesta

| Campo | Valor |
|---|---|
| Alcance | Propuesta regenerada en `expedientes/sisben/propuestas/validation/` (18 archivos + `manifest.json`), generada desde el insumo v2 aprobado (APR-005, sha256 `dd707688…54f92aa65`, verificado) con el framework 1.9.4. Transición reabierta (APR-006) |
| **Dictamen global** | **APROBADO, con observaciones P2 no bloqueantes.** Se cerraron los hallazgos que bloqueaban: AUD-V-01 (P0) y AUD-V-02, -03, -05 y -06 (P1). También los P2 de framework que cambian la salida (AUD-V-08, -09, -10, -16, -17 y -19). AUD-V-04, -07 y -18 quedan como desviaciones documentadas (PA-003, PA-004 y PA-005). Riesgos residuales: compilación no certificada (F-01, RG-006); los cierres de AUD-V-01 y AUD-V-06 se apoyan en el código del motor y en sus confs, pero la confirmación contra `ValidationConfigs` y `OxygenLookupService` (oxygen-core) sigue pendiente; AUD-V-27, -28 y -29 (P2) |

### 7.1 Dictamen por artefacto

| Artefacto | Dictamen | Razones |
|---|---|---|
| ART-011 processor, Spec, ValidatedEvent, FileMetadata | **APROBADO** | `validateFilename` usa `validarNombre(spec, nombre, Instant.now())` (`SISBENValidationProcessor.scala:80-82`), y `SISBENSpec.scala:176-178` activa las tres reglas de fecha (AUD-V-02). RV-SIS-05 es `SoloCeros` (`SISBENSpec.scala:160`). `comoMapa` publica `Campos.crudo` (`SISBENValidatedEvent.scala:43-66`), y por eso `validatedRecordJson` conserva el valor recibido (AUD-V-05). `listDuplicateEvents` copia los 17 argumentos, incluidos `inferredAsDeleted` y `fileTag` (`:131-136`, AUD-V-17); esos campos existen y Curation los lee (`oxygen-curation/.../Curator.scala:103,182-183`). Las marcas dicen `RESUELTO` donde la spec fija el valor (AUD-V-08). Observaciones: AUD-V-29 |
| ART-012 Capa A | **APROBADO** | `validarNombre(…, ahora)` implementa RA-02 a RA-04 de forma total (`FieldValidators.scala:213-230`). `SoloCeros` quita los espacios y exige solo `0` (`:247-251`). `Campos.crudo` y `Campos.valor` están separados (`:55-59`). `FieldValidatorsSpec` prueba SoloCeros (`:187-193`), crudo (`:202-207`) y las reglas del nombre con offset fuera de rango (`:209-226`). Observaciones: AUD-V-27 y AUD-V-28 |
| ART-013 pruebas, fixture, catálogos, `docs/codigos-error-sisben.csv` | **APROBADO** | Agrega `HeaderFuente` con los nombres de spec §3 (`SISBENValidationProcessorSpec.scala:109,252-255`), una prueba por regla del nombre más los `nombres_invalidos` (`:269-284`), y `DUP_CEROS_*` con su prueba de grupo (`:409-436`). El fixture tiene 27 líneas. `codigos-error-sisben.csv` pasó a `docs/` y tiene la fila `OxygenDuplicate` (AUD-V-19). Observación: AUD-V-28 |
| ART-014 HOCON | **APROBADO** | `validate[0]` y `metrics[0]` llevan `filePattern` (`validation-configs-sisben.conf:29,39`; AUD-V-01). Cada lookup usa `file = ${oxygen.application.paths.lookupHome}"/<archivo>"` (`lookups-sisben.conf:11` y siguientes; AUD-V-06). `verificar_hocon.py` ahora controla ambas cosas y da `HOCON OK`. Queda pendiente la confirmación contra oxygen-core |
| ART-015 `manifest.json` | **APROBADO** | `skill: "edf-validation"`, sin ninguna cabecera con `edf-validation-stage` (AUD-V-10). Hay 8 `supuestos` abiertos (F-04, VAL-PT01, VAL-PT03, VAL-PT04, VAL-SP02, VAL-SP08, VAL-SP09, VAL-SP10) y 9 `supuestos_resueltos` con su fuente. Las marcas `ASSUMPTION`/`RESUELTO` del código coinciden 1:1 con esas listas. Los 18 sha256 coinciden |

### 7.2 Cierre de los hallazgos anteriores (sobre la salida generada)

| Hallazgo | Estado | Evidencia |
|---|---|---|
| AUD-V-01 | Cerrado (falta confirmar en oxygen-core) | `validation-configs-sisben.conf:27-52`; `verificar_hocon.py:16-18` |
| AUD-V-02 | Cerrado | Capa A, processor y pruebas (ver 7.1). v7 de nombres: los 7 dan `MARCADOR` `SISBEN_ERR_024` (x3) |
| AUD-V-03 | Cerrado | `SoloCeros`. Con 16 ceros, `00 00` y `" 0000"` sale `SISBEN_ERR_029` (x5) |
| AUD-V-05 | Cerrado | `Campos.crudo` en `comoMapa`; prueba en `FieldValidatorsSpec.scala:202-207`. `write` no tiene prueba propia porque depende del framework (F-01) |
| AUD-V-06 | Cerrado (falta confirmar en oxygen-core) | `lookups-sisben.conf`, 6 tablas con `lookupHome` |
| AUD-V-08 | Cerrado | 8 abiertos y 9 resueltos. Ningún `RESUELTO` dice todavía "confirmar", "provisional" ni "inferido" (HF-15) |
| AUD-V-09 | Cerrado | `HeaderFuente`, `DUP_CEROS_*` y los nombres inválidos |
| AUD-V-10 | Cerrado | `manifest.json` y cabeceras |
| AUD-V-16 | Cerrado | El offset `-2500` se rechaza en Scala (`FieldValidatorsSpec.scala:223`) y en la réplica Python (x3) |
| AUD-V-17 | Cerrado | 17 argumentos |
| AUD-V-19 | Cerrado | `docs/codigos-error-sisben.csv` |
| AUD-V-27 | **Abierto (P2)** | `claveDuplicado` sigue con `valores.mkString("\u0001").hashCode` (`FieldValidators.scala:290`) y la réplica con `("SIN_CLAVE", row)` (`prevalidar_archivo.py:227`, que comenta «nunca agrupa registros sin documento»). Prueba x6: dos filas idénticas sin número se agrupan y una queda `OxygenDuplicate`, en contra de spec §6 (`spec-sisben.md:124`), aunque el código marca VAL-SP03 como `RESUELTO`. Impacto bajo: las dos tienen ERR_015 |
| AUD-V-04, -07, -18 | Documentados | PA-003, PA-004 y PA-005 (INT-012), no bloqueantes |

### 7.3 Verificaciones repetidas

| # | Comando | Resultado |
|:-:|---|---|
| x1 | `render_templates.py --spec EXP/insumos/source-spec-validation.yaml --solo-verificar` | `source-spec OK (22 campos, 29 códigos).` |
| x2 | `render_templates.py … --out <scratchpad>/aud-val/gen3` + `diff -r EXP/propuestas/validation <scratchpad>/aud-val/gen3` | 18 archivos. `diff` sin diferencias (rc 0): **reproducible** |
| x3 | `prevalidar_archivo.py` sobre la muestra (`--hoy 2026-11-24`) | 20 validados, 1 filtrado, 1 sev. 1 (`SISBEN_ERR_016`), 0 duplicados, RQS 0.95, `ACEPTARIA` 19. **Igual a spec §15** |
| x4 | `prevalidar_archivo.py` sobre el fixture generado (`--lookups` de la propuesta, `--hoy 2026-02-05`) | Nombre válido. 23 validados, 3 filtrados, 2 duplicados marcados (líneas 24 y 26: `DUP_ANTIGUO` y `DUP_CEROS_ANTIGUO`; se conservan 25 y 27). Cada caso da el código esperado. RQS 0.565217 |
| x5 | Casos v7 de nombres y de RV-SIS-05 (`<scratchpad>/aud-val/casos/`, `--hoy 2026-11-24`) | Los 7 nombres dan `MARCADOR` `SISBEN_ERR_024`. 16 ceros, `00 00` y `" 0000"` dan `SISBEN_ERR_029`. El duplicado con ceros marca el más antiguo |
| x6 | Filas idénticas sin número (`<scratchpad>/aud-val/casos5/`) | Línea 2 `ERR_015` + duplicado; línea 3 `ERR_015` (AUD-V-27) |
| x7 | D2: `reglas_nombre_violadas` (`python3 -I`) y prevalidación de `SISBEN_EXPERIAN_202612.20261201000000+1400.txt` con `--hoy 2026-11-30` (`<scratchpad>/aud-val/casos4/`) | Viola **solo** `periodo_no_futuro` → `MARCADOR` `SISBEN_ERR_024`. El caso del fixture `202603.20260205…-0500` viola `periodo_no_futuro` y `recepcion_desde_inicio_periodo` |
| x8 | `PYTHONPATH=<scratchpad>/py python3 .kiro/skills/edf-validation/scripts/verificar_hocon.py --generado EXP/propuestas/validation --fuente sisben --archivo-ejemplo SISBEN_EXPERIAN_202601.20260205093000-0500.txt` | `HOCON OK` (rc 0), con pyhocon 0.3.63 desde el scratchpad |
| x9 | `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=<scratchpad>/py python3 -m pytest scripts/tests -q -p no:cacheprovider` (desde la skill) | **216 passed** |
| x10 | Hashes de `manifest.json`; conteo de marcas `ASSUMPTION`/`RESUELTO`; búsqueda de `edf-validation-stage` | 18/18 OK; 8 abiertos y 9 resueltos (F-11 en 2 lugares, F-14 en 3); 0 apariciones |

### 7.4 Dictamen sobre D2 y hallazgos nuevos

**D2: es un hallazgo P2 del framework, en las pruebas; no es de la spec.** La premisa del generador («RA-02 no se puede aislar con RA-03 y RA-04 activas») es falsa, y con ella también mi AUD-V-25 de la §6, que queda **rectificado**. Las tres reglas comparan en marcos distintos: RA-02 compara el mes en America/Bogota, RA-03 el instante con el offset del nombre y RA-04 la fecha local del nombre. Por eso un nombre con un offset adelantado a fin de mes viola solo RA-02 (x7). El defecto real es que **ninguna prueba aísla RA-02**:

- el caso del fixture (`202603…`) y la prueba generada (`SISBEN_EXPERIAN_202702.20260205093000-0500.txt`, `SISBENValidationProcessorSpec.scala:270`) violan también RA-04;
- `FieldValidatorsSpec.scala:209-222` activa las tres reglas a la vez, y `X20990120240630…` también viola RA-04.

Una regresión que ignore `periodoNoFuturo` no se detectaría.

| ID | Artefacto | Clasificación | Sev. | Evidencia | Corrección sugerida |
|---|---|---|:-:|---|---|
| AUD-V-28 | ART-012/013 (pruebas de RA-02) | framework (`capa-a/FieldValidatorsSpec.scala.tpl`, `nombres_de_prueba` en `render_templates.py:296-321`) | P2 | Lo de arriba; x7 | En `FieldValidatorsSpec`, agregar una aserción con solo `periodoNoFuturo = true` (por ejemplo `X202407…` con `ahora` 2024-06-30), o un nombre con offset `+1400` del último día del mes. En `nombres_de_prueba`, derivar el caso de RA-02 con un offset adelantado o documentar que no aísla. Rectificar AUD-V-25 |
| AUD-V-29 | ART-011 (`Campos.crudo` en `validatedRecordJson`) | framework / contrato Validation → Curation | P2 | `Campos.crudo` no recorta nada (`FieldValidators.scala:55-56`). Con un archivo CRLF, Spark `textFile` deja `\r` al final de la línea, y `FECHA_ACTUALIZACION_CNS` (último campo) llega a Curation como `"10/01/2026\r"`. La validación no se ve afectada porque usa `valor` (trim). La spec no fija el fin de línea (§2); la muestra es LF | Quitar solo el `\r` final de la línea en `Campos.separar` (sin tocar los espacios), o declarar en el contrato que Curation recorta el último campo. Agregar una prueba con CRLF |

### 7.5 Qué sigue

- La propuesta se puede aprobar en N2 (`06:73`). Al registrarla, citar: los riesgos residuales (F-01; confirmación pendiente en oxygen-core de AUD-V-01 y AUD-V-06), PA-003, PA-004 y PA-005, y los P2 abiertos AUD-V-27, -28 y -29 para la próxima versión del framework.
- Pasos del ingeniero, fuera de la skill: compilar y probar en el fork (`FieldValidatorsSpec`, `SISBENValidationProcessorSpec`), confirmar `ValidationConfigs`/`LookupElements` y la ubicación de los CSV en `lookupHome`.

## 8. Re-auditoría por diferencia (1.9.5)

| Campo | Valor |
|---|---|
| Alcance | Diferencia `HEAD~1..HEAD` (commit `1660d36`) en `propuestas/validation/`, con el framework 1.9.5 (`6cdd122`; HF-16, HF-17 y HF-18). Usé git solo en lectura (`log`, `diff`) |
| Cambios | 4 archivos: `FieldValidators.scala` (HF-18: `Campos.separar` quita el `\r` final; HF-16: clave `("SIN_CLAVE", <único>)` con un UUID aleatorio), `FieldValidatorsSpec.scala` (+3 pruebas), `SISBENValidationProcessorSpec.scala` (RA-02 aislada con `+1400`, pruebas aisladas de RA-03 y RA-04, prueba de HF-16) y `manifest.json` (los 3 hashes). El insumo de Validation no cambió (sha256 `dd707688…`, `git diff HEAD~1` vacío; solo cambió `source-spec-acceptance.yaml`, que queda fuera de alcance) |
| **Dictamen** | **Se mantiene APROBADO.** Se conserva el comportamiento aprobado y quedan cerrados AUD-V-27, AUD-V-28 y AUD-V-29. La observación del generador sobre el UUID **no es un defecto** (§8.3) |

### 8.1 Dictamen por artefacto

| Artefacto | Dictamen | Razones |
|---|---|---|
| ART-011 | **APROBADO (sin cambios en sus archivos; cambia su comportamiento a través de la Capa A)** | `SISBENValidationProcessor.scala:117` llama a `claveDuplicado(spec, e.valores)` (la sobrecarga con UUID). `SISBENValidatedEvent.scala:15` usa `Campos.separar`, así que `validatedRecordJson` ya no lleva el `\r` final. Las dos cosas son lo buscado |
| ART-012 | **APROBADO** | `Campos.separar` quita solo un `\r` final (`FieldValidators.scala:50-54`). `claveDuplicado(spec, valores, unico)` hace la clave única por registro cuando falta tipo o número, y la sobrecarga de dos argumentos le pasa `UUID.randomUUID()` (`:286-301`). Las pruebas nuevas de `FieldValidatorsSpec` cubren el CRLF (5 bordes), las filas idénticas sin clave y RA-02, RA-03 y RA-04 aisladas, con la regla apagada como contraprueba |
| ART-013 | **APROBADO** | RA-02 se prueba aislada con `SISBEN_EXPERIAN_202603.20260301000000+1400.txt` y el reloj del 2026-02-28 23:59:59 en Bogotá. RA-03 y RA-04 llevan su contraprueba. La prueba nueva de HF-16 usa las posiciones de la clave declarada. El fixture y los catálogos no cambian |
| ART-015 | **APROBADO** | Cambian exactamente 3 hashes y los 18 coinciden con los archivos. Sigue habiendo 8 `supuestos` y 9 `supuestos_resueltos` |

### 8.2 Verificaciones

| # | Comando | Resultado |
|:-:|---|---|
| y1 | `render_templates.py --spec EXP/insumos/source-spec-validation.yaml --out <scratchpad>/aud-val/gen4` + `diff -r EXP/propuestas/validation <scratchpad>/aud-val/gen4` | rc 0; `diff` sin diferencias: **reproducible** |
| y2 | Hashes de `manifest.json` | 18/18 OK; 8 abiertos y 9 resueltos |
| y3 | `prevalidar_archivo.py` sobre la muestra (`--hoy 2026-11-24`) | 20 validados, 1 filtrado, 1 sev. 1 (`SISBEN_ERR_016`), 0 duplicados, RQS 0.95, `ACEPTARIA` 19: **igual a spec §15** |
| y4 | `prevalidar_archivo.py` sobre el fixture (`--lookups` de la propuesta, `--hoy 2026-02-05`) | Nombre válido, 23 validados, 3 filtrados, 2 duplicados, 14 códigos, RQS 0.565217: **igual a la ronda §7 (x4)** |
| y5 | Filas idénticas sin número (`<scratchpad>/aud-val/casos5/`) | Las dos con `ERR_015` y **ninguna** marcada como duplicado (en §7 x6 una salía duplicada): AUD-V-27 cerrado también en la réplica |
| y6 | La muestra convertida a CRLF (`<scratchpad>/aud-val/casos6/`) | Header válido, 20 validados, `SISBEN_ERR_016` x1, RQS 0.95: el mismo resultado que con LF |
| y7 | `PYTHONPATH=<scratchpad>/py verificar_hocon.py --generado EXP/propuestas/validation --fuente sisben …` | `HOCON OK` (el HOCON no cambió) |
| y8 | `PYTHONDONTWRITEBYTECODE=1 … python3 -m pytest scripts/tests -q -p no:cacheprovider` | **227 passed** (antes 216) |

### 8.3 Dictamen sobre la clave con UUID (observación del generador)

**No es un problema. No hay hallazgo de corrección, solo una nota informativa (AUD-V-31).** Razones:

1. **Duplicados en el motor.** El motor calcula `keys` una sola vez por evento, al crearlo (`Validator.scala:202,208-210`, `keysForDuplicate(...).mkString(":")`), lo guarda en `TaggedValidatedEvent.keys` y agrupa por ese campo ya guardado (`SparkValidator.scala:686-705`). Para un registro sin tipo o sin número solo hace falta que la clave sea **única**, no estable. Si Spark recalcula una partición (reintento, especulación o desalojo de caché), vuelve a generar el evento completo con otra clave igualmente única, así que el registro sigue sin agruparse en cualquier cálculo. El resultado (sin marca `OxygenDuplicate`) no cambia.
2. **Reproducibilidad de la salida.** La salida de Validation ya no se puede reproducir byte a byte: `eventId` sale de `IdGenerator.nextEventId`, que usa el reloj, la partición y una secuencia (`Validator.scala:178`). El UUID agrega otro campo variable, y solo en registros que además tienen ERR_015 o ERR_014 y por eso no pasan Acceptance. Para comparar dos corridas hay que excluir `eventId` (y ahora `keys`) o comparar por `lineage.recordHash`, igual que antes.
3. **Reproceso y etapas siguientes.** Fuera de los motores de Validation, nadie lee `keys`: `grep` de `.keys` en `oxygen-acceptance`, `oxygen-curation` y `oxygen-validation` solo encuentra la deduplicación de `SparkValidator`/`SparkValidatorCustomMode`. Acceptance decide con códigos y niveles (RQ-04), y el reproceso de un archivo vuelve a validar desde la fuente.
4. **No hay una alternativa determinística mejor dentro del contrato.** `keysForDuplicate(rawRecord: ValidatedEvent)` (`ValidationProcessor.scala:44`) no recibe `eventId` (se genera fuera del processor) ni el número de línea (la lectura con `textFile` no da índice). `lineage.recordHash` es el SHA-1 de la línea y se repite en filas idénticas, que es justo el caso de AUD-V-27. Un contador por partición depende del particionado, así que tampoco sería reproducible entre corridas.

### 8.4 Hallazgos nuevos

| ID | Artefacto | Clasificación | Sev. | Evidencia | Corrección sugerida |
|---|---|---|:-:|---|---|
| AUD-V-30 | AUD-V-29 (rectificación) | — (rectificación del auditor) | P2 (informativo) | En AUD-V-29 dije que Spark `textFile` deja el `\r`. No es así: SISBEN no declara `encoding`, así que se lee con `appContext.sc.textFile` (`SparkValidator.scala:507`), y el `LineRecordReader` de Hadoop reconoce CR, LF y CRLF como fin de línea. El `\r` solo llega en la ruta con `encoding`, que lee con `binaryFiles(...)` y `split("\n")` (`:500-503`); así lo describe bien el comentario de HF-18. HF-18 es correcto y no sobra: protege la otra ruta y la réplica Python. El riesgo real de AUD-V-29 para SISBEN era nulo | Ninguna en el código. Que la bitácora registre la rectificación |
| AUD-V-31 | ART-012 (`claveDuplicado`) | framework (documentación) | P2 (informativo) | §8.3: `keys` de los eventos `SIN_CLAVE` cambia en cada corrida (`FieldValidators.scala:298-301`), como `eventId` | En `references/interpretar-salida.md` (comparación de corridas), decir que `eventId` y `keys` no se pueden reproducir y que se compara por `lineage.recordHash` |

Quedan abiertos, sin cambios: PA-003, PA-004 y PA-005 (AUD-V-04, -07 y -18), F-01 (RG-006) y la confirmación pendiente en oxygen-core de AUD-V-01 y AUD-V-06.
