# Contexto de la ingesta — SISBEN IV

| Campo | Valor |
|---|---|
| Ingesta (`id` del expediente) | `sisben` |
| Versión | 1.1.0 (ronda 2: ajustes AUD-E0-01, -02, -04, -05) |
| Fecha | 2026-11-24 |
| Estado | PROPUESTO (pasa a APROBADO con aprobación registrada por `edf-orquestador`) |
| Fuentes | Historia de usuario (HU): #[[file:ejecuciones/sisben-e2e/expedientes/sisben/fuentes/hu-sisben.md]] 1.0.2 (obligatoria, presente). Spec de Confluence: #[[file:ejecuciones/sisben-e2e/expedientes/sisben/fuentes/spec-sisben.md]] 1.0.2 (obligatoria, presente). Muestra sintética: #[[file:ejecuciones/sisben-e2e/expedientes/sisben/fuentes/muestra/SISBEN_EXPERIAN_202601.20260205093000-0500.txt]]. Sin layout aparte: el layout está en `spec-sisben.md` §3 |
| Skill | `edf-analisis` (modo etapa 0, tipo T1) |
| Aprobador | Desarrollador; PO para el cierre formal (`06` §4). En esta corrida, aprobador simulado |

## Objetivo y alcance

Dejar el contexto de la ingesta SISBEN IV en el framework EDF (Experian Data Framework) para que las etapas 1 a 6 partan de una base trazable: identidad, contrato del archivo, reglas, contradicciones, supuestos, preguntas abiertas y riesgos.

**Cubre:** lo que declaran la HU y la spec 1.0.2 de la ingesta, contrastado con el steering del framework.
**No cubre:** el mapeo de campos (`mapeo-campos.md`), las dependencias (`dependencias.md`), el plan por etapas (`plan-de-ruta.md`) ni el mapeo al XPM (Experian Profile Map), que no se hace en la etapa 0 (`04` §4.4).

Las fuentes de la ingesta son solo la HU y la spec (`hu-sisben.md` cabecera, fila "Fuentes de la ingesta"). Las `references/` del framework se citan solo para detectar huecos, nunca como fuente de un valor.

## 1. Identidad de la fuente

| Atributo | Valor | Estado | Fuente |
|---|---|---|---|
| Entidad que entrega | DNP (Departamento Nacional de Planeación), `datasource = DNP` | `[C]` | `hu-sisben.md` §3; `spec-sisben.md` §1 |
| Subscriber | `999722` | `[C]` | `hu-sisben.md` §3; `spec-sisben.md` §1 |
| ¿Fuente oficial? — opcional, informativo | `mixto`: oficial y única para la clasificación socioeconómica (grupo, subgrupo, estado); no oficial para identidad | `[C]` | `hu-sisben.md` §3; `spec-sisben.md` §1 |
| Posición en la jerarquía de best data para PIN — opcional, informativo | Para identidad, por debajo de Registraduría y Cámaras de comercio (nacionales) y de Migración (extranjeros) | `[C]` | `hu-sisben.md` §3 |
| Business Light Code | `batch` | `[C]` | `hu-sisben.md` §3; `spec-sisben.md` §1 |
| ¿Ingesta nueva? → `reusePIN` | Sí, nueva en EDF (hoy la procesa un COBOL fuera de EDF) → `reusePIN = false` | `[C]` | `hu-sisben.md` §3; `spec-sisben.md` §1, §11 |
| Periodicidad | Mensual, carga completa: cada archivo `REGULAR` reemplaza todo lo anterior | `[C]` | `hu-sisben.md` §3, R-02; `spec-sisben.md` §1, §10.3 |
| Volumen | ~28 millones de registros por archivo (referencia) | `[C]` | `spec-sisben.md` §1 |
| Zona horaria | `America/Bogota` | `[C]` | `spec-sisben.md` §1 |
| Estabilidad de las reglas | Estables en 1.0.2; un cambio entra como versión nueva de HU y spec | `[C]` | `hu-sisben.md` §3 |
| Clase / paquete de la fuente | `SISBEN` / `sisben` | `[C]` | `spec-sisben.md` §1 |

## 2. Contrato del archivo (las seis dimensiones de `04` §4.2)

| Dimensión | Valor | Estado | Fuente |
|---|---|---|---|
| Patrón de nombre | `SISBEN_EXPERIAN_AAAAMM.yyyyMMddHHmmss±ZZZZ.txt` (extensión `txt` o `TXT`). Regex `()SISBEN_EXPERIAN_(\d{6})\.(\d{14})([+-]\d{4})\.(txt\|TXT)`; `filePattern` HOCON `.*SISBEN_EXPERIAN_\d{6}\.\d{14}[+-]\d{4}\.(txt\|TXT)`. La marca de tiempo la agrega la recepción; sin ella el archivo se rechaza (`SISBEN_ERR_024`) | `[C]` | `spec-sisben.md` §2, RA-01; `hu-sisben.md` R-01 |
| Delimitador | `;` | `[C]` | `spec-sisben.md` §2; `hu-sisben.md` §3, R-03 |
| Encoding | UTF-8; un BOM inicial se ignora | `[C]` | `spec-sisben.md` §2; `hu-sisben.md` R-03 |
| Encabezado / pie | 1 fila de header con los 22 nombres de §3 en orden (sin distinguir mayúsculas ni tildes; si no, `SISBEN_ERR_025`) / sin footer | `[C]` | `spec-sisben.md` §2, RA-05; `hu-sisben.md` R-03 |
| Tipo de evento | `30`; con `datasource = DNP` y `subscriber = 999722` identifica a SISBEN sin ambigüedad frente a otra fuente con el mismo event type | `[C]` | `hu-sisben.md` §3; `spec-sisben.md` §1 |
| Número de columnas | 22 por fila (una fila con otro número: `SISBEN_ERR_026`, sev. 1) | `[C]` | `spec-sisben.md` §2, §8; `hu-sisben.md` R-03, §5.1 |
| Formato (dato adicional) | Texto delimitado; tipo de archivo siempre `REGULAR`; líneas vacías se ignoran | `[C]` | `spec-sisben.md` §2 |
| Fecha de corte (dato adicional) | Último día del periodo `AAAAMM` del nombre | `[C]` | `spec-sisben.md` §2 |
| Fecha de recepción (dato adicional) | Marca de tiempo del nombre con su offset | `[C]` | `spec-sisben.md` §2 |

La muestra sintética cumple las seis dimensiones: header de 22 nombres, 21 registros de 22 columnas, `;`, texto ASCII (subconjunto de UTF-8). `[C]` — verificado en esta sesión leyendo el archivo y con `prevalidar_archivo.py` (`nombre_valido: true`, `header_valido: true`).

## 3. Reglas de negocio extraídas

Conteo real de reglas (`04` §6): 28 reglas de la HU (R-01 a R-28) y, en la spec, 5 de archivo (RA), 22 por campo (§3), 5 entre campos (RV-SIS), 2 filtros (RF), 5 de calidad (RQ) y 1 de duplicados (§6). Las de la spec detallan las de la HU; no son reglas nuevas.

| # | Regla | Etapa donde aplica | Estado | Fuente |
|---|---|---|---|---|
| 1 | Validation solo procesa archivos con la marca de tiempo de recepción en el nombre | `validation` | `[C]` | `hu-sisben.md` R-01; `spec-sisben.md` RA-01 |
| 2 | Un solo tipo de archivo, `REGULAR` (carga completa). Un prefijo `RT_` o `EP_` no cumple el nombre y se rechaza | `validation` | `[C]` | `hu-sisben.md` R-02, §2; `spec-sisben.md` §2 |
| 3 | `AAAAMM` es un mes válido y no posterior al mes actual; recepción no futura y no anterior al primer día del periodo. Si falla, rechazo del archivo `SISBEN_ERR_024` | `validation` | `[C]` | `spec-sisben.md` RA-02 a RA-04; `hu-sisben.md` §5.1 |
| 4 | Un fallo de nombre o header rechaza el archivo completo y no se lee ningún registro | `validation` | `[C]` | `spec-sisben.md` §2, §7 RQ-05 |
| 5 | `Cod_dpto` llega con 2 dígitos y `Cod_mpio` con 5 (DDMMM) | `validation` | `[C]` | `hu-sisben.md` R-04 |
| 6 | Se quitan espacios a los lados antes de validar; un opcional vacío no se valida; un solo código por campo, en el orden vacío → tipo/formato → longitud → rango → dominio → no futura → catálogo | `validation` | `[C]` | `spec-sisben.md` §3 |
| 7 | Fechas solo en `yyyy-MM-dd` o `dd/MM/yyyy`; `Fec_digitacion` es texto con formato de fecha, no número | `validation` | `[C]` | `hu-sisben.md` R-05 |
| 8 | Sev. 1 rechaza el registro (RQS 0); sev. 3 es inconsistencia y no cambia el RQS; ningún valor se reemplaza en Validation | `validation` | `[C]` | `hu-sisben.md` R-20; `spec-sisben.md` §3, RQ-01 |
| 9 | Una regla entre campos no se evalúa si alguno de sus campos ya tiene error | `validation` | `[C]` | `spec-sisben.md` §4 |
| 10 | Nombre de departamento y de municipio deben corresponder a su código en DIVIPOLA sin distinguir mayúsculas ni tildes; el municipio debe pertenecer al departamento (RV-SIS-01 a RV-SIS-03) | `validation` | `[C]` | `hu-sisben.md` R-13; `spec-sisben.md` §4 |
| 11 | Menores (tipos `1` y `2`) y líneas vacías se descartan sin error y no cuentan en RQS ni en FQS; el log cuenta `REGISTROS MENORES DE EDAD` | `validation` | `[C]` | `hu-sisben.md` R-16; `spec-sisben.md` §5 |
| 12 | Tipo `7` (salvoconducto) se rechaza con `SISBEN_ERR_023`, causa `TIPO_PENDIENTE_CREACION`, hasta que exista su tipo en Experian | `validation` | `[C]` | `hu-sisben.md` R-17; `spec-sisben.md` RV-SIS-04 |
| 13 | `num_documento`: se quitan espacios y ceros a la izquierda y el resultado mide hasta 15; solo ceros → `SISBEN_ERR_029` | `validation` | `[C]` | `hu-sisben.md` R-18; `spec-sisben.md` §3, RV-SIS-05 |
| 14 | Duplicados: clave tipo + número normalizado; se conserva el de mayor `fec_actualizacion_cns` (empate: mayor `eventId`); los demás llevan `OxygenDuplicate`, conservan su RQS y no se aceptan; un registro sin tipo o sin número no se agrupa | `validation`, `acceptance` | `[C]` | `hu-sisben.md` R-19; `spec-sisben.md` §6 |
| 15 | FQS = registros con RQS 1 / (total − filtrados); los duplicados cuentan con su RQS. La expansión de las siglas FQS y RQS no consta (`02`) | `acceptance` | `[C]` regla · `[PM]` sigla | `hu-sisben.md` R-21; `spec-sisben.md` RQ-02 |
| 16 | El archivo se acepta si FQS ≥ 0.90 (umbral parametrizable); si no, se rechaza completo y ningún registro avanza | `acceptance` | `[C]` | `hu-sisben.md` R-22; `spec-sisben.md` RQ-03 |
| 17 | Registro aceptado: `errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate")` | `acceptance` | `[C]` | `spec-sisben.md` RQ-04 |
| 18 | Curation estandariza nombres (mayúsculas, sin tildes ni diéresis, conserva `Ñ`, signos a espacio, espacios colapsados), homologa el tipo de documento y convierte las fechas a epoch UTC en ms desde el inicio del día en `America/Bogota` | `curation_flow`, `curation_stock` | `[C]` | `hu-sisben.md` R-23; `spec-sisben.md` §10.1, §10.2 |
| 19 | Un campo con sev. 3 se publica estandarizado; si no se puede transformar sale nulo y `advertenciasCuracion` anota `<campo curado>:<MOTIVO>` sin el valor; ninguna clave se omite | `curation_flow`, `curation_stock` | `[C]` | `hu-sisben.md` R-23; `spec-sisben.md` §10.4 |
| 20 | `standardizedIdName` = `primerNombre segundoNombre primerApellido segundoApellido` ya curados, omitiendo nulos o vacíos, separados por un espacio; nulo si todos faltan | `curation_flow`, `pinning` | `[C]` | `spec-sisben.md` §10.1; `hu-sisben.md` R-24 |
| 21 | Sin archivos `RT` ni `EP`: no hay manejo de reenvío ni de eliminación | `curation_flow` | `[C]` | `hu-sisben.md` §2; `spec-sisben.md` §10.3 |
| 22 | Pinning usa `ColombiaIdentityMatcher` compartido, sin bandera de fuente; prioridad 1 `standardizedIdType` + `standardizedIdNumber` (exacta); prioridad 2 `standardizedIdName` (NearNeighborKeys con el umbral vigente del matcher) | `pinning` | `[C]` | `hu-sisben.md` R-24; `spec-sisben.md` §11 |
| 23 | Cédula sin coincidencia en BDIIREGI → `PENDIENTE_VALIDACION_REGIS` y proceso nocturno de compra RNEC | `pinning` | `[C]` | `hu-sisben.md` §5.4; `spec-sisben.md` §11 |
| 24 | SISBEN pasa por Synthesis sin handlers propios: comportamiento por defecto del motor (LFP, valor del evento más reciente); sin reglas de consistencia propias | `synthesis` | `[C]` | `hu-sisben.md` R-25; `spec-sisben.md` §12 |
| 25 | La sección `#sisben` del XPM se genera solo para los PIN presentes en el archivo vigente; sin scoring | `state_calculation` | `[C]` regla y etapa · `[A:PA-002]` mecanismo | `hu-sisben.md` R-26, §5.6; `spec-sisben.md` §13 |
| 26 | Los reportes se construyen con el reporting estándar de EDF; las skills del framework no los generan | transversal | `[C]` | `hu-sisben.md` R-27; `spec-sisben.md` §14 |
| 27 | Son PII nombres, apellidos, tipo y número de documento, `ide_persona` e `ide_nacional`; los demás campos son públicos | transversal | `[C]` | `hu-sisben.md` R-28; `spec-sisben.md` §10.1 |

Verificación del Anexo A de la spec: la spec declara que se verificó con `render-templates.jar --solo-verificar` (`spec-sisben.md` cabecera). El framework 1.9.0 ya no usa ese generador Java (`.kiro/skills/edf-validation/SKILL.md` §Regla de ejecución). En esta sesión se repitió con el generador Python: `source-spec OK (22 campos, 29 códigos)`, el mismo resultado que declara la spec. `[C]`

## 4. Contradicciones entre fuentes (no se resuelven aquí)

| # | Fuente A dice | Fuente B dice | Pregunta abierta |
|---|---|---|---|
| — | Ninguna contradicción entre HU, spec y muestra | — | — |

Entre la HU, la spec y la muestra no se encontró ninguna contradicción directa: se contrastaron atributo por atributo el contrato del archivo, los 22 campos, los códigos, las fechas epoch de los escenarios de §5.3 (recalculadas: `1768021200000` y `1769835600000` son correctas) y el resultado de la muestra (§15, reproducido). Las rarezas que sí aparecen se registran como riesgos en §7 (`deteccion-ambiguedades.md` §7). La diferencia con `references/fuentes/curation-pinning-sisben.md` sobre Synthesis no es una contradicción entre fuentes, porque esa reference no es fuente (`hu-sisben.md` cabecera: «Ningún otro documento es fuente»); queda como riesgo RG-010.

## 5. Supuestos

| ID | Supuesto | Dueño | Consecuencia si es falso |
|---|---|---|---|
| — | Ninguno nuevo. Todo lo que usa este análisis lo declaran la HU o la spec; lo que no declaran quedó como pregunta abierta (§6), no como supuesto | — | — |

Los supuestos propios del generador de Validation (`VAL-SP*`, `F-*` de `.kiro/skills/edf-validation/references/decisiones-y-abiertos.md`) son de esa skill y los reporta su `manifest.json`; no se repiten aquí (`05` §7).

## 6. Preguntas abiertas

IDs registrados por `edf-orquestador` en `preguntas_abiertas[]` del expediente.

| ID | Pregunta | Etapa afectada | Bloqueante | Estado | Origen |
|---|---|---|:-:|---|---|
| PA-001 | ¿Qué etapa cumple R-26 (la sección `#sisben` solo para los PIN del archivo vigente)? | — (resuelta) | no | RESUELTA por las fuentes: State Calculation | HU R-26, §5.6, §6; spec §13, Anexo A, §12 |
| PA-002 | ¿Cómo se determina el corte vigente de SISBEN y cómo se excluye `#sisben` de los PIN cuyo último evento SISBEN no pertenece a ese corte? | `state_calculation` | sí, solo para `state_calculation` | ABIERTA | Cierre de PA-001; HU R-26, §5.6; spec §13, §10.1 |

### PA-001 · ¿Qué etapa cumple R-26? — RESUELTA por las fuentes

| Dato de la ficha | Contenido | Destino |
|---|---|---|
| Estado | Resuelta (aprobador simulado SIM-DEV-01, `ref_bitacora` BIT-0009) | `estado: resuelta` |
| Respuesta | «R-26 se cumple en State Calculation (HU R-26, §5.6, §6; spec §13). Validation, Acceptance, Curation y Synthesis no cambian (spec Anexo A, §12; HU §6). Las alternativas (a) archivo.metadata/fileLoadType y (b) inferDelete quedan descartadas mientras no exista una versión nueva de la HU y la spec (HU §3). El mecanismo concreto dentro de la etapa 6 pasa a PA-002.» | `respuesta` |
| Qué dice cada fuente | HU R-26, literal: «State Calculation genera la sección `#sisben` solo para los PIN presentes en el archivo vigente». HU §5.6: «Given un PIN presente en 202512 y ausente en 202601 Then el XPM de 202601 no tiene sección "#sisben" para ese PIN». HU §6: la definición de terminado de State Calculation incluye los escenarios de §5.6. Spec §13: la regla de la sección `#sisben`. Spec Anexo A: es el contenido de `source-spec-validation.yaml`. Spec §12: Synthesis usa el comportamiento por defecto del motor (LFP, valor del evento más reciente). Muestra: no aplica | `origen` |
| Por qué la resuelven las fuentes | R-26 nombra la etapa (State Calculation) y la spec fija el contenido de las demás: Validation (Anexo A), Acceptance (umbral y regla de registro, HU §6), Synthesis (por defecto, spec §12). La pregunta «qué etapa» no es una ambigüedad (`deteccion-ambiguedades.md`: lo que declara la fuente no se pregunta) | Solo la ficha |
| Alternativas descartadas | (a) `archivo.metadata`/`fileLoadType` en Validation: **requiere versión nueva de HU/spec** (cambia el Anexo A). (b) `inferDelete` en Acceptance: **requiere versión nueva de HU/spec** (cambia la regla de Acceptance de HU §6). Reglas estables en 1.0.2 (HU §3) | Solo la ficha |
| Qué queda | El mecanismo concreto dentro de State Calculation → PA-002 | — |

### PA-002 · ¿Cómo se determina el corte vigente y cómo se excluye `#sisben` de los PIN fuera de ese corte?

| Dato de la ficha | Contenido | Destino |
|---|---|---|
| Pregunta | ¿Cómo se determina el corte vigente de SISBEN y cómo se excluye `#sisben` de los PIN cuyo último evento SISBEN no pertenece a ese corte? | `pregunta` |
| Bloqueante | Sí, **solo para `state_calculation`**: bloquea la aprobación de `insumos/yaml-skill-statecalc.yaml` y la transición `synthesis` → `state_calculation`. No cambia ningún valor de los artefactos de la etapa 0 ni de los insumos de Validation, Acceptance, Curation, Pinning o Synthesis | `bloqueante: true` |
| Campo o atributo afectado | La sección `#sisben` del XPM; usa `fechaCorte` curado (spec §10.1) | — (no cambia `mapeo-campos.md`) |
| Etapa afectada | `state_calculation` | `etapa_afectada: state_calculation` |
| Qué dice cada fuente | HU R-26 (literal arriba) y §5.6 exigen el resultado. Spec §13 define la sección; spec §10.1 publica `fechaCorte` curado. Ninguna fuente declara cómo determina el enricher el corte vigente | `origen` |
| Por qué no la resuelvo | Es una decisión de implementación de la etapa 6 que las fuentes no declaran; elegir el criterio de «corte vigente» sería rellenar un hueco con el valor más probable (`04` §4.4) | Solo la ficha |
| Alternativas compatibles con las fuentes | (c1) Filtro en el enricher que compara `fechaCorte` curado (spec §10.1) con el corte vigente (máximo `fechaCorte` o corte del último archivo aceptado); toca `[A:CQ-006]`. (d) El motor ya lo cumple; solo con evidencia de código o pipeline. Fuera de alcance sin versión nueva de HU/spec: cambios al Anexo A, a Acceptance o a Synthesis | Solo la ficha |
| Dirigida a | Desarrollador con el dueño del dominio (Ingesta 3); cierra el PO o Aprobador designado (N4, `06` §4) | Solo la ficha |
| Qué desbloquea | La aprobación de `insumos/yaml-skill-statecalc.yaml` y el escenario de HU §5.6 en State Calculation. Mitiga RG-003 | Solo la ficha |

## 7. Riesgos

IDs del expediente (`riesgos[]` registrados por `edf-orquestador`); cada ID significa lo mismo en todos los artefactos.

| ID | Nivel · etapa | Riesgo | Impacto | Mitigación |
|---|---|---|---|---|
| RG-001 | alto · `analisis` | El análisis produce un mapeo incompleto por specs ambiguas y el contexto defectuoso se arrastra a las etapas siguientes | Errores en todas las etapas posteriores | Detección de ambigüedades y preguntar antes de cerrar; no asumir reglas no aprobadas |
| RG-002 | alto · `pinning` | `reusePIN` mal declarado en una ingesta nueva corrompe la asignación de PIN | PIN erróneos | `reusePIN = false` explícito (`hu-sisben.md` §3, R-24) |
| RG-003 | **crítico** · `state_calculation` | R-26 sin mecanismo definido: un PIN ausente del archivo vigente podría conservar su sección `#sisben` | N4: dato erróneo visible al cliente (`06` §2.2) | PA-002 bloqueante solo para la etapa 6: no aprobar `insumos/yaml-skill-statecalc.yaml` sin su respuesta (decide PO o Aprobador designado, N4) |
| RG-004 | medio · `validation` | Los reportes de spec §14 (`RPT_SISBEN_VALIDATION_INCONSISTENCIAS_*`, «Se envía al DNP»; `RPT_SISBEN_ACCEPTANCE_RECHAZADOS_*`) llevan tipo y número de documento, que son PII (R-28). El steering pide excluir PII de los reportes (`08` §3 S6) para lo que genera el framework; estos reportes los genera el reporting estándar de EDF, no una skill (R-27) | Exposición de PII en un reporte que sale de Experian; cumplimiento de Ley 1581/2012 | Ninguna skill genera ni copia estos reportes. El Desarrollador lo escala al PO y, si corresponde, a DevSecOps (`06` §5) para confirmar la base legal del envío al DNP |
| RG-005 | bajo · `curation_flow` | El escenario «Campo con inconsistencia de severidad 3» de HU §5.3 parte de `seg_apellido "Ruíz"` con `SISBEN_INC_011`, pero Validation no restringe caracteres (spec §3.1) y `SISBEN_INC_011` solo sale por longitud > 30 (spec §3 fila 11). La precondición no se alcanza desde Validation | Prueba de Curation con una entrada que el pipeline real no produce | El fixture de Curation declara el código como precondición inyectada, o usa el ejemplo de spec §10.4 (31 caracteres). El comportamiento esperado no cambia |
| RG-006 | medio · `validation` | La compilación del código que generen las skills no la certifica el framework (sin JAR de Oxygen, F-01 de `edf-validation`) | Errores de compilación aparecen recién en el fork | El ingeniero compila y prueba fuera del framework (`edf-validation/references/runbook-ejecucion.md`) |
| RG-007 | medio · `curation_flow` | Una `Fec_digitacion` futura con formato válido es inconsistencia sev. 3, pero se puede transformar: Curation la publica como epoch (spec §10.4 regla 1) y llega a la sección `#sisben` como fecha de encuesta futura (spec §13) | Dato de calidad dudosa visible al cliente | Lo declara la fuente; se señala en la revisión de Curation y de State Calculation para que el PO lo confirme |
| RG-008 | medio · `curation_flow` | `ide_hogar` e `ide_ficha_origen` son identificadores de hogar y de ficha clasificados como públicos (R-28, spec §10.1) y podrían enlazarse con una persona | Re-identificación indirecta | La clasificación es de la fuente (`[C]`); el código generado no registra valores de ningún campo (`08` §3 S4). Revisión del PO al aprobar |
| RG-009 | medio · `validation` | Los adultos con tipo `7` (salvoconducto) se rechazan hasta que Experian cree el tipo (R-17; `07` §5.7 `[A:OQ-TR-2b]`) | Esas personas no se cargan y bajan el FQS del archivo | Dependencia con Experian (`dependencias.md` D-07); el log cuenta `REGISTROS TIPO 7 PENDIENTES` (spec §14) |
| RG-010 | medio · `synthesis` | Una reference del framework (`curation-pinning-sisben.md` §6, §8), que no es fuente de la ingesta, dice que SISBEN va sin Synthesis y con periodicidad "Unique", contra HU R-25 y spec §12 | Una skill de etapa podría tomar el valor de la reference | La spec manda (`00` §6; `03` §9 #11). Citar siempre la spec en los insumos de Synthesis y State Calculation |
| RG-011 | alto · `state_calculation` | La certificación de 4 dimensiones antes de producción (`07` §5.7) no figura en la definición de terminado de la HU (§6) y no hay motor que la calcule (`edf-analisis/references/logica-statecalc.md` §2) | State Calculation sin la evidencia de certificación que exige el steering | Registrarlo en el insumo de State Calculation y escalar al PO antes de producción |

## Checklist de cierre (`04` §4.3)

- [x] Insumos obligatorios presentes y leídos: HU y spec en `fuentes/`; muestra sintética leída.
- [x] Todos los campos mapeados con las nueve dimensiones (`mapeo-campos.md`, 22 de 22).
- [x] Contrato del archivo completo (seis dimensiones, §2).
- [x] Reglas de negocio extraídas y trazadas (§3).
- [x] Dependencias listadas con dueño donde se conoce (`dependencias.md`).
- [x] Toda afirmación con marcador de estado y cita.
- [x] Ambigüedades registradas como preguntas abiertas con ID (PA-001 resuelta por las fuentes; PA-002 abierta, bloqueante solo para `state_calculation`).
- [x] Supuestos con dueño y consecuencia (§5: ninguno nuevo).
- [x] Contradicciones explicitadas, no resueltas por el agente (§4).
- [x] Clasificación público/PII campo a campo completa (`mapeo-campos.md`).
- [x] Solo fixtures sintéticos.
- [x] Plan de ruta propuesto (`plan-de-ruta.md`).
- [x] Riesgos con impacto y mitigación (§7).

**Ninguna pregunta abierta bloquea la etapa 0 ni Validation:** PA-001 está resuelta por las fuentes y PA-002 es bloqueante solo para `state_calculation` (`etapa_afectada` del expediente; `edf-orquestador/references/03-gobierno-riesgo-aprobacion.md` condición 4, «en la etapa N»). La letra de `deteccion-ambiguedades.md` §4 y del gate de `04-contratos-por-skill.md` no distingue etapa; la auditoría lo registra como defecto del framework (AUD-E0-10).

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Mecanismo de R-26 dentro de State Calculation (corte vigente) | `[A:PA-002]` |
| 2 | Expansión de las siglas FQS y RQS | `[PM]` (`02`) |
| 3 | Aprobador designado del patrón | `[A:OQ-06]` |

## Pie de trazabilidad

- Fuentes de la ingesta: `fuentes/hu-sisben.md` 1.0.2 y `fuentes/spec-sisben.md` 1.0.2 (2026-11-18); muestra `fuentes/muestra/SISBEN_EXPERIAN_202601.20260205093000-0500.txt`.
- Reglas del framework: steering `00`, `01`, `04`, `05`, `06`, `07`, `08`; `edf-analisis/references/deteccion-ambiguedades.md`.
- PA-001 resuelta con HU R-26, §5.6, §6 y spec §13, Anexo A, §12 (expediente, `ref_bitacora` BIT-0009). Antecedente para PA-002: `07` §5.7 (`[A:CQ-006]`); auditoría AUD-E0-07.
- Verificaciones de esta sesión: `render_templates.py --solo-verificar` → `source-spec OK (22 campos, 29 códigos)`; `prevalidar_archivo.py --hoy 2026-11-17` sobre la muestra → 21 registros, 1 filtrado, 20 validados, 1 con sev. 1 (`SISBEN_ERR_016`), RQS 0.95, aceptaría 19 (igual a spec §15).
- Última verificación de referencias: 2026-11-24.
