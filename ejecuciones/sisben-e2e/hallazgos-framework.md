# Hallazgos del framework durante la corrida

Defectos del framework (skills, herramientas, plantillas) que encontraron los auditores durante la corrida. Regla
acordada: se registran, se corrigen con su prueba de regresión (commit aparte en esta rama) y la corrida sigue.

| ID | Etapa | Hallazgo | Evidencia | Corrección | Commit |
|---|---|---|---|---|---|
| HF-01 (AUD-E0-10) | analisis | P1. Una pregunta bloqueante frena toda la ingesta aunque solo afecte a una etapa posterior | `edf-analisis/references/deteccion-ambiguedades.md:65`, `edf-orquestador/references/04-contratos-por-skill.md:30` frente a `03-gobierno-riesgo-aprobacion.md:50` (condición 4 «en la etapa N») y `03-pipeline.md:200` | El bloqueo se mide por `etapa_afectada`; el gate de la etapa 0 considera solo `analisis` y `validation` | 7da4ca4 |
| HF-02 (AUD-E0-11) | analisis | P1. `edf-validation/SKILL.md:74` manda partir del ejemplo SISBEN, que difiere de la spec 1.0.2 (mismo riesgo en `edf-analisis/SKILL.md:75,84`) | Diff YAML del auditor: prefijos `RT_`/`EP_`, `SISBEN_ERR_001` longitud 50, otro catálogo; contradice `00` §6 | El ejemplo es solo de forma; los valores salen siempre de la HU y la spec | 7da4ca4 |
| HF-03 (AUD-E0-13) | analisis | P2. La plantilla de mapeo pone varios hechos por celda, contra `05-convenciones.md:113` | `mapeo-campos.md.tpl:12` | Excepción explícita para la tabla de mapeo y marcador `[A:*]`/`[PM]` por dimensión | 7da4ca4 |
| HF-04 (AUD-E0-14) | analisis | P2. `07-contratos-frontera.md:87` no tiene descarte silencioso, nulo + advertencia ni duplicado no aceptado | `07:87-91` frente a `07:105` y spec §5, §6, §10.4 | Agregar los tres efectos y alinear la plantilla | 7da4ca4 |
| HF-05 (AUD-E0-15) | analisis | P2. Falta la casilla «Insumos obligatorios presentes y leídos» en la plantilla de contexto | `contexto-ingesta.md.tpl:63-74` frente a `04-reglas-analisis.md:84` | Agregar la casilla | 7da4ca4 |
| HF-06 (AUD-V-01) | validation | **P0.** `validate[]` y `metrics[]` del HOCON generado sin `filePattern`; el motor hace `data.filePattern.get` y fallaría en cada archivo | `validation-block.conf.tpl:25,34`, `dsl-config-reference.md:25-26`; motor `SparkValidator.scala:1417-1446`; `verificar_hocon.py` no lo revisa | Plantilla con `filePattern` obligatorio, referencia corregida y chequeo en `verificar_hocon.py` | 872c07c |
| HF-07 (AUD-V-02) | validation | P1. RA-02..04 (fechas del nombre de archivo) sin soporte en esquema, Capa A y prevalidador | `FieldValidators.scala.tpl:193-211`, `prevalidar_archivo.py:205-219`; spec §2 | Claves nuevas en el source-spec y su implementación | 872c07c |
| HF-08 (AUD-V-03) | validation | P1. Una regla «solo ceros» solo se puede expresar como lista literal y no se evalúa sobre el valor normalizado | `FieldValidators.scala:224-227`; spec RV-SIS-05 | Tipo de regla sobre el valor normalizado | 872c07c |
| HF-09 (AUD-V-05) | validation | P1. El evento validado recorta los valores; Curation pierde el original | `ValidatedEvent.scala.tpl`; `DefaultOxygenCurationProcessor.scala:24`; HU §5.3 | Conservar el valor recibido en `validatedRecordJson` | 872c07c |
| HF-10 (AUD-V-06) | validation | P1. Lookups sin `lookupHome` | `lookups.conf.tpl`; confs del motor; runbook de la skill | Ruta con `${oxygen.application.paths.lookupHome}` | 872c07c |
| HF-11 (AUD-V-08..13, -15, -16, -19, -21) | validation | P2. Supuestos que la spec ya resuelve; nombre de skill `edf-validation-stage`; escritura en `references/` contra la regla de solo expediente; checklist y references desactualizados; `07` §5; offset no validado en el prevalidador; ubicación del catálogo de códigos; nombre del conf destino | `auditorias/validation.md` §2 | Ver informe de la corrección | 872c07c |
| HF-12 (Orquestador) | validation | P1. No hay procedimiento para revisar un insumo ya aprobado de una etapa en curso; el validador (7b) rechaza el expediente y la guía no dice qué hacer | Regla 7b con ART-010 en `propuesto` (BIT-0041); `references/03` sin caso | `references/03` §3c: insumo a `propuesto`, transición suspendida, etapa siguiente a `no_iniciada`, re-aprobación del insumo y de la transición (BIT-0042, BIT-0043) | 7a6ded2 |
| HF-13 (AUD-V-22) | validation | P2. El esquema del source-spec no admite `supuestos` en `archivo.nombre`: un supuesto sobre las reglas del nombre no deja marca con ID en el código | `source-spec.schema.yaml` (bloque `archivo.nombre`) | Registrado; se corrige al cierre de la corrida si no bloquea | pendiente |
| HF-14 (D1 generador) | validation | P2. Una prueba de la suite de la skill dependía del insumo del expediente E2E y falló al pasar a v2 | `test_render_templates.py:951` `KeyError: 'valores'` | Prueba válida para v1 y v2 | 5ae3380 |
| HF-15 (D3 generador) | validation | P2. `RESUELTO F-12` seguía diciendo «confirmar con el dueño del catálogo» | `lookups-sisben.conf:7`; `lookups.conf.tpl:7` | `resolver_supuestos` quita el pedido de confirmación al resolver | 5ae3380 |
| HF-16 (AUD-V-27) | validation | P2. Dos filas idénticas sin número de documento se agrupan como duplicadas aunque VAL-SP03 sale RESUELTO | `auditorias/validation.md` §7 | Lote de correcciones al cierre | 6cdd122 |
| HF-17 (AUD-V-28) | validation | P2. Ninguna prueba generada aísla RA-02 (todas violan también RA-04); una regresión que ignore RA-02 no se detectaría | §7; RA-02 es aislable con offset adelantado (`…202612.20261201000000+1400.txt`, hoy 2026-11-30) | Lote de correcciones al cierre | 6cdd122 |
| HF-18 (AUD-V-29) | validation | P2. `Campos.crudo` no recorta el `\r` final de una línea CRLF; Curation lo recibiría en el último campo | §7 | Lote de correcciones al cierre | 6cdd122 |
| HF-19 (AUD-A defecto 1) | acceptance | P2. `edf-acceptance/SKILL.md:139-147` y `references/especificacion-sisben.md` describen otra versión de SISBEN (RT_/EP_, sin TXT, inferDelete) | `auditorias/acceptance.md` §3 | Corrector 1.9.5 | 6cdd122 |
| HF-20 (AUD-A defecto 2) | acceptance | P2. Plantillas, manifiesto, esquema y `render_templates.py` firman «edf-acceptance-stage» | §3 | Corrector 1.9.5 | 6cdd122 |
| HF-21 (AUD-A defecto 3) | acceptance | P2. El esquema genérico exige la clave `expresiones_propuestas_sisben` | `source-spec.schema.yaml:10,200` | Corrector 1.9.5 | 6cdd122 |
| HF-22 (AUD-A defecto 4) | acceptance | P2. `severidades` exige todos sus campos aunque `habilitado: false` | `source-spec.schema.yaml:164` | Corrector 1.9.5 | 6cdd122 |
| HF-23 (AUD-A-01, defecto 5) | acceptance | P2 (framework) → P1 en la ingesta. No hay forma de declarar y probar un umbral alternativo (HU §5.2 «umbral parametrizable») | `render_templates.py:367`; §2 AUD-A-01 | Corrector 1.9.5 | 6cdd122 |
| HF-24 (AUD-A-08..10) | acceptance | P2. El simulador no compara con lo esperado; comentario inexacto del reasonCode en la plantilla del Spec | §2 | Corrector 1.9.5 | 6cdd122 |
| HF-25 (Orquestador) | validation | P2. No hay procedimiento para regenerar artefactos de una etapa ya aprobada cuando se corrige la skill (HF-16..18 cambian código aprobado de Validation) | 03 §3c solo cubre insumos; regla 7e | `references/03` §3d: regenerar, re-auditar por diferencia, nueva aprobación tipo artefacto | 6cdd122 |
| HF-26 (analista, Acceptance v2) | acceptance | P2. El esquema del fixture de Acceptance no admite `validatedRecordJson: null`, que es lo que emite Validation en los eventos marcador (`SparkValidator.scala:303`) | `edf-acceptance/assets/schema/source-spec.schema.yaml` (`validatedRecordJson: {type: string}`) | Lote de cierre | a205649 |
| HF-27 (AUD-V-31) | validation | P2 (documentación). Para comparar corridas hay que excluir `eventId` y `keys` (UUID de registros sin tipo/número) y comparar por `lineage.recordHash` | `auditorias/validation.md` §8 | Lote de cierre: documentar en `interpretar-salida.md` | a205649 |
| HF-28 (AUD-A-14) | acceptance | P2. `acceptance-block.conf.tpl:29` deja el comentario «registra códigos de razón también en el rechazo automático» encima de `customReason… = false` | `auditorias/acceptance.md` §6.2 | Plantilla corregida (1.9.6) | b69fc62 |
| HF-29 (AUD-A §7 defecto 1) | acceptance | P2. S-ACC-03/05 citan datos fijos del ejemplo y el checklist exige «los mismos supuestos»; la prueba solo mira el ejemplo | `decisiones-y-abiertos.md:60,62`; `checklist-entrega.md:19`; `test_render_templates.py:510-518` | Lote de cierre | a205649 |
| HF-30 (AUD-A §7 defecto 2) | acceptance | P2. Restos del SISBEN histórico que HF-19 no alcanzó | `inventario-plantillas.md:16,21,23`; `dsl-config-reference.md` §4 (98-112); `decisiones-y-abiertos.md:39-41` | Lote de cierre | a205649 |
| HF-31 (AUD-A §7 defecto 3) | acceptance | P2. El checklist pide documentos `SINT*` y la spec usa números sintéticos `1000000xxx` | `checklist-entrega.md:34` | Lote de cierre | a205649 |
| HF-32 (AUD-A §7 defecto 4) | analisis | P2 (diseño). Los insumos llevan «Estado: PROPUESTO» en la cabecera: tras aprobar queda desactualizado y cambiarlo altera el hash | `source-spec-acceptance.yaml:2`, `source-spec-validation.yaml:2`; plantilla/guía de `edf-analisis` Modo 2 | Lote de cierre: el estado vive solo en el expediente | a205649 |
| HF-33 (AUD-A-16, -17) | acceptance | P2. El Spec construye `AcceptanceCondition` con 4 argumentos (el motor usa 5) y no prueba el `filePattern` en Scala | `AcceptanceAST.scala:75`, `SparkDatasetAcceptor.scala:284`; ACC-TPL-05 | Lote de cierre | a205649 |
| HF-34 (AUD-C-01) | curation | **P1.** La transformación `original` recorta espacios (también al leer el evento aceptado); la prueba generada fija el valor errado; no cumple HU §5.3 `numeroDocumentoOriginal = " 0001000000101"` | `Transformations.scala.tpl:21,27`, `CurationProcessor.scala.tpl:137`, `AcceptedEventReader.scala.tpl:17-18`, `curation_reference.py:95-96,417-418` | Corrector 1.9.7 | pendiente |
| HF-35 (AUD-C-05) | curation | P1/P2. El override de `subscriberRelation` no filtra `errorCode` nulos (posible NPE aunque dice «Nunca lanza») y cura cada registro dos veces | `render_templates.py:505-507` | Corrector 1.9.7 | pendiente |
| HF-36 (AUD-C-06) | curation | P2. Supuestos que la spec ya fija sin `supuestos_resueltos` | `CurationSpec.scala.tpl:25,36-37,45,61,66`; `lookups.conf.tpl:7`; `CurationProcessor.scala.tpl:48`; `curation-block.conf.tpl:15`; `AcceptedEventReader.scala.tpl:4` | Corrector 1.9.7 | pendiente |
| HF-37 (AUD-C-08) | herramientas | P2. `verificar-claves.py` lee todas las tablas del contrato y no exime `subscriber`/`subscriberRelation`; `advertenciasCuracion` no cuenta como publicada | `tools/verificar-claves.py:14,152-157` | Corrector 1.9.7 | pendiente |
| HF-38 (AUD-C-09) | curation | P2. `homologacion_tipo_documento.tabla` exige valores numéricos: no se puede declarar el 7 pendiente | `source-spec.schema.yaml:128` | Corrector 1.9.7 | pendiente |
| HF-39 (AUD-C-10) | curation | P2. Firma `edf-curation-stage` | `render_templates.py:834` y 11 plantillas | Corrector 1.9.7 | pendiente |
| HF-40 (AUD-C-04, -07) | curation | P2. El fixture generado pone `fileAsOfDate = receivedDate` y `fileTag = ""`; referencias y comentarios del SISBEN histórico | `render_templates.py:272,294`; `auditorias/curation.md` §2 | Corrector 1.9.7 | pendiente |
| HF-41 (AUD-C-13) | curation | P2. El efecto «vaciado con preservación» del steering 07 no está implementado en `edf-curation` (no aplica a SISBEN; resuelve la observación de HF-04) | `07-contratos-frontera.md` §5.1 | Corrector 1.9.7: documentar como no soportado | pendiente |
| HF-42 (resto del lote) | analisis / validation | P2. Las plantillas de etapa 0 de `edf-analisis` siguen con «Estado \| PROPUESTO»; `edf-validation/references/interpretar-salida.md:71` dice `customReason… = true` (SISBEN histórico) | INT-037 | Lote final | pendiente |
| HC-04 (AUD-A-11) | — | Herramienta de la corrida: `etapa --agregar-riesgo` duplicaba RG-015 | Corregido en `orquestador_e2e.py` (BIT-0063) | — |

Hallazgos de la spec (no del framework; se dejan para la próxima versión de la spec, no se corrigen en la corrida):

| ID | Hallazgo | Evidencia |
|---|---|---|
| HS-01 (AUD-E0-08) | La spec cita `render-templates.jar --solo-verificar`; el generador vigente es Python | `spec-sisben.md:11` |
| HS-02 (AUD-E0-09) | `spec_version: "sisben-iv-1.0.0"` en el Anexo A frente a la spec 1.0.2 | `spec-sisben.md:390`, `:6-7` |
| HS-03 (AUD-V-04) | El escenario HU §5.1 «sin marca de tiempo → ERR_024» es inalcanzable con el `filePattern` de spec §2 (PA-003) | `spec-sisben.md:37`; `hu-sisben.md:120-123`; `SparkValidator.scala:176-190` |
| HS-04 (AUD-V-02/03) | Para la próxima versión de la spec: incorporar al Anexo A las claves de RA-02..04 y la regla de solo ceros sobre valor normalizado (decisión del aprobador, INT-012) | `spec-sisben.md` §2, §4 |

Hallazgos de la herramienta de la corrida (`herramientas/orquestador_e2e.py`):

| ID | Hallazgo | Corrección |
|---|---|---|
| HC-01 | `riesgo` cambiaba el JSON sin evento en la bitácora | Ahora registra un evento `riesgo` (BIT-0011 en adelante) |
| HC-02 | `vista` dejaba los contadores desfasados (detectado por el analista) | El evento de regeneración se registra antes de escribir la vista |
| HC-03 | `etapa` cambiaba el estado sin evento en la bitácora, y la etapa 0 quedó en `no_iniciada` aunque ya había empezado | `--porque` registra el evento; corrección BIT-0018 |

Observación de la corrección HF-04 (resuelta por AUD-C-13): «vaciado con preservación» se asignó a Curation por inferencia; el auditor de Curation confirma que `edf-curation` no lo implementa (HF-41).

Observación de diseño (para el informe final, no se corrige): una etapa tiene un solo `ref_aprobacion`; al habilitar la transición apunta a la aprobación `transicion` (regla 7a) y la de `contexto` de la etapa solo queda en `aprobaciones[]` (BIT-0028).
