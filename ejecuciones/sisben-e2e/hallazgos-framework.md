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
| HF-16 (AUD-V-27) | validation | P2. Dos filas idénticas sin número de documento se agrupan como duplicadas aunque VAL-SP03 sale RESUELTO | `auditorias/validation.md` §7 | Lote de correcciones al cierre | pendiente |
| HF-17 (AUD-V-28) | validation | P2. Ninguna prueba generada aísla RA-02 (todas violan también RA-04); una regresión que ignore RA-02 no se detectaría | §7; RA-02 es aislable con offset adelantado (`…202612.20261201000000+1400.txt`, hoy 2026-11-30) | Lote de correcciones al cierre | pendiente |
| HF-18 (AUD-V-29) | validation | P2. `Campos.crudo` no recorta el `\r` final de una línea CRLF; Curation lo recibiría en el último campo | §7 | Lote de correcciones al cierre | pendiente |

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

Observación abierta de la corrección HF-04: el corrector asignó «vaciado con preservación» a Curation por inferencia; lo verificará el auditor de la etapa de Curation.

Observación de diseño (para el informe final, no se corrige): una etapa tiene un solo `ref_aprobacion`; al habilitar la transición apunta a la aprobación `transicion` (regla 7a) y la de `contexto` de la etapa solo queda en `aprobaciones[]` (BIT-0028).
