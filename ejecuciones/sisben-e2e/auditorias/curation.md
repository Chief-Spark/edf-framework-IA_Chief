# Auditoría independiente — Etapa 3 (`curation_flow` / `curation_stock`) · ingesta `sisben`

| Campo | Valor |
|---|---|
| Auditor | Agente auditor independiente (corrida autónoma del framework EDF 1.9.6, sin Kiro) |
| Fecha | 2026-11-25 |
| Alcance | Insumos de Curation que preparó `edf-analisis` (Modo 2) para `edf-curation` y para Pinning: ART-030 `insumos/source-spec-curation.yaml` (v1, `prevalidado`, sha256 `b357acfd…`), ART-035 `insumos/fixture-curation/` (`accepted-events.input.yaml` sha256 `82919b31…` y `lookups/LEEME.md`) y ART-034 `insumos/contrato-curation-pinning.md` (1.0.0, sha256 `0ac94ee9…`). Contrato de entrada con la Validation aprobada (`propuestas/validation/`) y con Acceptance (`propuestas/acceptance/`). Coherencia con el motor (`Motor-Oxigen/oxygen-curation`, `oxygen-pinning`) y con `.kiro/steering/11-contrato-curation-pinning.md`. Los 6 posibles defectos que reportó el analista y los riesgos RG-019..021. **No** se auditan ART-031..033 (ausentes) |
| Fuentes válidas | `fuentes/hu-sisben.md` 1.0.2 (R-05, R-08, R-09, R-14, R-18, R-19, R-23..R-25, §5.3, §6) y `fuentes/spec-sisben.md` 1.0.2 (§1, §2, §3, §6, §8, §9.4, §10.1..§10.4, §11, §14), `fuentes/muestra/`. La spec no trae un anexo de Curation. Las fuentes de `ejecuciones/sisben-e2e/expedientes/sisben/fuentes/` son idénticas a las de `expedientes/sisben/fuentes/` (`diff -rq` vacío) |
| Referencias de control | `.kiro/skills/edf-curation/` (SKILL.md, esquema, plantillas, scripts, references), `.kiro/steering/00`, `06`, `07`, `11`; `.kiro/skills/edf-analisis/references/fuentes/pinning-muric.md` y `curation-muric.md` (nivel 3); `estado-ingesta.json` |
| Riesgo de la etapa | **N2** (`riesgo_tipico: medio` de `curation_flow`; `06-riesgo-y-aprobaciones.md:35`: contrato de una costura). ART-034 toca identidad: las decisiones del lado de Pinning que derivan de él (RG-020, RG-021) son N3 (`06:36`, `06:58`) |
| **Dictamen global** | **AJUSTES.** Los valores de negocio de ART-030 son fieles a la spec y a la HU, fila por fila: las 34 claves del evento curado son las de spec §10.1, con su nombre, su orden y su tipo, en los 14 eventos generados. La homologación, las fechas, la Ñ, la política de severidad 3 y `subscriberRelation` también coinciden. El contrato ART-034 es coherente con el steering `11` y con el motor de Pinning. Los cuatro chequeos dan rc=0 y la suite de la skill pasa (106 pruebas, 2 omitidas por falta de `pyhocon`). No hay P0. Hay **un P1 (framework)**: la transformación `original` recorta los espacios, y el escenario «Número de documento estandarizado» de HU §5.3, que la DoD de HU §6 exige, no se cumple. Además, la prueba Scala generada fija como esperado el valor errado (AUD-C-01). El insumo no puede evitarlo, porque ningún tipo del esquema conserva el valor crudo. Los P2 son de fidelidad del fixture, de marcadores y documentales, y defectos latentes del framework. La generación de ART-031..033 queda bloqueada por dos razones. La primera, que `acceptance` sigue con `transicion_habilitada: false` (`estado-ingesta.json`, etapa `acceptance`; `edf-curation/SKILL.md:25`). La segunda, que conviene corregir AUD-C-01 antes de generar, para no regenerar dos veces |

## 1. Dictamen por insumo

### ART-030 `source-spec-curation.yaml` — **AJUSTES**

| Bloque del insumo | Fuente | Verificación | Resultado |
|---|---|---|---|
| `fuente` (`:55-62`) | spec §1 (`spec-sisben.md:18-22`) | `SISBEN`/`sisben`, `DNP`, `999722`, event type 30 | Fiel |
| `archivo.file_pattern` (`:77`) | spec §2 (`:37`); Validation (`validation-configs-sisben.conf`) | Literal `.*SISBEN_EXPERIAN_\d{6}\.\d{14}[+-]\d{4}\.(txt\|TXT)`. El motor busca el processor con la ruta sin su última extensión (`SparkCurator.scala:188`), que conserva `.txt`, y cada evento por `lineage.fileName` (`:270-275`) | Fiel. cur-0014 (`.TXT`) se cura y cur-0015 (otra fuente) se descarta |
| `archivo.regex_derivacion`, `tipo_por_prefijo` (`:79-81`) | spec §2 (`:36`, `:38-39`) | Grupos 1 (vacío) y 2 (AAAAMM). `infoArchivo` usa `findFirstMatchIn` (`Transformations.scala.tpl:132`), así que quitar los grupos 3-4 y la extensión no cambia nada | Fiel en efecto. Difiere en forma de la regex de spec §2 sin declararlo (AUD-C-12) |
| `parametros` zonas y formatos (`:91-96`) | HU R-05, R-23; spec §3 (`:92`), §10.1 | `America/Bogota` con `origen: requisito`; `dd/MM/uuuu`, `uuuu-MM-dd`. Epoch de HU §5.3: 1768021200000 y 1769835600000 | Fiel (las dos cifras se reproducen) |
| `conservar_enie: true` (`:97`) | spec §10.2 regla 2 (`:289`); HU R-23, §5.3 | `ANA PEÑA RUIZ` | Fiel |
| `niveles_no_almacenar: []`, `niveles_anular: []` (`:98-99`) | spec §10.4 (`:306`), configuración literal | Ninguna clave omitida por severidad 3 (`resumen.json`: `claves_omitidas_por_severidad: {}`) | Fiel |
| `campo_advertencias` (`:100`) | spec §10.1 (`:280`) | `advertenciasCuracion`, formato `<campo curado>:<MOTIVO>` | Fiel |
| `omitir_si_nulas` (`:102`) | steering `11` §3 (`:60-65`); esquema `:104-107` (valor por defecto) | Las tres `standardizedId*` | Correcto según el steering. Contradice la letra de spec §10.1 solo en un caso (RG-019, §4). El marcador `[PM]` debería ser `[P]` (AUD-C-12) |
| `campos_entrada` (`:105-127`) | spec §3; `SISBENValidatedEvent.scala:43-66` | 22 nombres de dominio en el orden del layout | Fiel |
| `codigos_error` (`:132-156`) | spec §3 y §8 (`:143-161`) | 24 códigos → campo | Fiel. Con las dos listas vacías, solo sirve para advertir un nivel nulo (AUD-C7) |
| `homologacion_tipo_documento` (`:159-184`) | spec §9.4 (`:198-212`); HU R-15, R-17 | 8 pares, `7` en `pendientes`, columnas `cod_sisben`/`cod_experian` | Fiel. Al CSV sintético le falta la fila `7` por una restricción del esquema (defecto 4, AUD-C-09) |
| `reglas_documento` (`:188-196`) | spec §10.1 (`:262`), R-18 | Mayúsculas, sin espacios, sin ceros, para todos los tipos | Fiel (cur-0011 PPT y cur-0012 pasaporte) |
| `catalogos` (`:200-207`) | spec §9.4 | Archivo, `;`, columnas y llave | Fiel |
| `campos_curados` (`:211-246`) + `campo_advertencias` | spec §10.1 (`:247-280`) | Script sobre `expected/curated-events.json`: los 14 eventos tienen exactamente las 34 claves de §10.1, en ese orden, y cada valor no nulo con el tipo declarado (texto/entero/largo/lista) | Fiel |
| `campos_abiertos: []` (`:250`) | spec §10.1 (`:282`, «lista completa») | — | Fiel |
| `extension_subscriber_relation` (`:254-258`) | spec §10.1 (`:284`); steering `11` §6 #8 (resuelto por la spec, `:118`) | `1_1000000101`, `11_1000000111`, `5_PA1000112` | Fiel |
| Cabecera, diferencias 1 y 2 (`:30-39`) | — | La 1 es real (AUD-C-01), aunque omite el segundo recorte (`AcceptedEventReader.scala.tpl:17-18`). La 2 es RG-019 | Bien declaradas |

Copia del ejemplo de la skill: no hay. El ejemplo usa `conservar_enie: false` (C-CUR-03), la política «no se almacena» y no trae las `standardizedId*`. El insumo usa los valores de la spec.

### ART-035 `fixture-curation/` — **AJUSTES**

Cubre los 8 escenarios de HU §5.3. Uno de ellos no pasa, por el defecto del framework (AUD-C-01).

| Escenario HU §5.3 (`hu-sisben.md:249-281`) | Evento | Salida esperada (implementación de referencia) | Resultado |
|---|---|---|---|
| Estandarización de nombres | cur-0002 | `GARCIA LOPEZ`, `JOSE` | Cumple |
| Nombre completo estandarizado | cur-0003 | `standardizedIdName = "ANA PEÑA RUIZ"`, `segundoNombre` nulo sin advertencia | Cumple |
| Homologación del tipo de documento | cur-0001 | `standardizedIdType = 1`, `tipoDocumentoSisben = "3"` | Cumple |
| Número de documento estandarizado | cur-0004 | `standardizedIdNumber = "1000000101"`; `numeroDocumentoOriginal = "0001000000101"` (la HU pide `" 0001000000101"`) | **No cumple** (AUD-C-01) |
| Fechas a epoch | cur-0001, cur-0010 | `fechaActualizacionCns = 1768021200000` con los dos formatos | Cumple |
| Fecha de corte y tipo de archivo | cur-0001, cur-0014 | `fechaCorte = 1769835600000`, `tipoArchivo = "REGULAR"` | Cumple |
| Nombre que queda vacío | cur-0005 | `primerApellido` nulo y `primerApellido:NO_NORMALIZABLE` | Cumple |
| Campo con inconsistencia sev. 3 | cur-0006 | `idMarca` nulo e `idMarca:FORMATO_INVALIDO`, `segundoApellido = "RUIZ"`, 34 claves | Cumple |

Riesgos del expediente: **RG-005**. cur-0006 declara `SISBEN_INC_011` como precondición inyectada (Validation no la emite para `Ruíz`: spec §3.1, `:96`) y cur-0007 usa el ejemplo alcanzable de §10.4 regla 1 (31 caracteres → `SINTETICOAPELLIDOMUYLARGOXXXXXX`). **RG-007**: cur-0008 publica `01/01/2099` como 4070926800000. Ambos están bien cubiertos. También se cubren §10.4 regla 2 (cur-0009) y R-09 (cur-0013, identificadores de 20 dígitos como texto).

Ajustes: cur-0001 y cur-0004 son duplicados para Validation, así que no pueden llegar los dos aceptados (AUD-C-02). Faltan casos para RG-019 y para los tipos 4, 5 y 8 (AUD-C-03). Hay que reescribir la descripción de cur-0004 cuando se corrija AUD-C-01. `lookups/LEEME.md` cumple la marca de sintético (AC-30) y explica por qué no trae CSV.

### ART-034 `contrato-curation-pinning.md` — **AJUSTES (documentales)**

| Punto | Verificación | Resultado |
|---|---|---|
| Claves de §1 | Las tres de spec §11 (`:312`), literales en `campos_curados[].destino` (`verificar-claves.py`: coherentes, 3 citas) | Fiel |
| Tipos | `entero`/`texto`/`texto` = `tipo_salida` del insumo (steering `11` §4, `:76`) | Fiel |
| Normalización | Coincide con spec §10.1 y §10.2 y con la salida generada (cur-0003, cur-0004, cur-0011, cur-0012) | Fiel |
| «¿Puede ser null?» | Las claves se omiten y nunca salen `null` (steering `11` §3). Lo respalda el motor: `EventPinner.scala:64-65` hace `eachVal.getAsString` cuando el valor no es `null`, y `JsonNull.getAsString` lanza; una clave ausente da `""`, como dicen `IdentityMatcher.scala:46-49` y `pinning-muric.md` §3.2 | Correcto. Conviene citar la evidencia (AUD-C-11) |
| `subscriberRelation` y `subscriber` | El `doesMatch` base une identidades por igual `subscriber` y `subscriberRelation` (`IdentityMatcher.scala:53-57`), y `nearNeighborKeys` por defecto es `subscriber:subscriberRelation` (`:13-15`). Con `<tipo>_<número>` normalizados, la misma persona en dos cortes queda unida y no se mezclan personas distintas. El gancho existe (`CurationProcessor.scala:59` del motor) y `Curator.scala:89` lo invoca por evento | Coherente |
| Matcher | `ColombiaIdentityMatcher` lo fija la spec §11 (`[C]` de la ingesta, steering `00` §6). **No está en la copia del motor**: solo hay `IdentityMatcher`, `ISIdentityMatcher`, `RIIdentityMatcher` y `TestIdentityMatcher` (`oxygen-pinning/src/main/scala/.../pinning/`). Solo lo describe `pinning-muric.md` (nivel 3) | El `[C]` vale para la decisión, pero no está verificado en el motor (AUD-C-11) |
| Lector `[A:lector-oxygen-pinning]`, ruta `[PM]` | steering `11` §6 #1, #2 | Bien marcados |
| Formato frente al steering `11` §4 | Encabezado «¿Puede ser `null`?» en lugar de «¿Puede faltar? (nunca `null`, §3)» y columna extra «Fuente» | Aceptable; alinear (AUD-C-11) |

## 2. Hallazgos

| ID | Insumo | Clasificación | Sev. | Evidencia | Corrección |
|---|---|---|:-:|---|---|
| AUD-C-01 | ART-030/035 (efecto); plantillas | framework | **P1** | HU §5.3 pide `numeroDocumentoOriginal = " 0001000000101"` (`hu-sisben.md:262-263`) y spec §10.1 dice «Valor original» (`spec-sisben.md:261,263`). La Validation aprobada entrega el valor crudo (`FieldValidators.scala:58-62`; `FieldValidatorsSpec.scala:202-204`; el `accepted-events.json` generado trae `" 0001000000101"`). Curation lo recorta **dos veces**. Primero `AcceptedRecord.get` hace `.trim` (`AcceptedEventReader.scala.tpl:17-18`, no lo reportó el analista). Después `Original` cae en `texto` → `limpio` (`CurationProcessor.scala.tpl:137`; `Transformations.scala.tpl:21,27`). La referencia hace lo mismo (`curation_reference.py:95-96,417-418`). Resultado: `"0001000000101"`. La prueba Scala generada **fija el valor errado** como esperado (`SISBENCurationProcessorSpec.scala:39`, cur-0004): el test pasaría y el escenario de la DoD de HU §6 (`:343`) fallaría. También afecta a `tipoDocumentoSisben` | En la Capa A, que `original` devuelva el crudo sin recortar (nulo solo si falta o es `null`). En `AcceptedRecord`, exponer un `crudo(campo)` sin `trim` y usarlo solo para `original`. En la referencia, `original` aparte de `texto`. Agregar la prueba de regresión con `" 0001000000101"` en `test_curation_reference.py` y `TransformacionesSpec.scala.tpl`. Regenerar; el insumo no cambia (solo la descripción de cur-0004) |
| AUD-C-02 | ART-035 | insumo | P2 | cur-0001 (`NUM_DOCUMENTO "1000000101"`, base `:24`) y cur-0004 (`" 0001000000101"`, `:55`) tienen la misma clave de duplicado: tipo 3 + número sin espacios ni ceros (spec §6, `:121`; HU R-19). Además tienen la misma `fec_actualizacion_cns`. Validation marcaría uno como `OxygenDuplicate` y Acceptance no lo aceptaría (`:123`). El fixture dice ser «eventos ya ACEPTADOS» (`:3`) y los dos dan `subscriberRelation = 1_1000000101` | Cambiar el `NUM_DOCUMENTO` del `base_registro` (p. ej. `1000000100`) y dejar el literal de la HU en cur-0004 |
| AUD-C-03 | ART-035 | insumo | P2 | Ningún evento muestra lo que ART-030 declara para RG-019 (cabecera `:35-39`) y ART-034 afirma (§1 `:23`): los cuatro nombres nulos → `standardizedIdName` ausente. Tampoco hay casos de las homologaciones `4→4`, `5→8` y `8→9` de spec §9.4 (solo 3, 6 y PPT) | Agregar un evento con `PRI_NOMBRE` y `PRI_APELLIDO` `"!!!"` y los segundos vacíos (esperado: clave ausente, dos `NO_NORMALIZABLE`, `subscriberRelation` armado) y tres eventos para los tipos 4, 5 y 8 |
| AUD-C-04 | — (fixture generado) | framework | P2 | `construir_eventos` pone `lineage.fileAsOfDate = receivedDate` y `fileTag = ""` (`render_templates.py:272,294`). Validation emite `fileAsOfDate` = último día del periodo (`SISBENFileMetadata.scala:30`; spec §2 `:39`; el fixture de Acceptance trae `1769835600000`) y `fileTag = REGULAR`. Curation copia `lineage` tal cual, así que `expected/` muestra `fileAsOfDate = 1770301800000` | Derivar `fileAsOfDate` con `regex_derivacion` y `fileTag` con `tipo_por_prefijo` |
| AUD-C-05 | — | framework | P2 | El override `subscriberRelation` arma los errores con `.map(e => (e.errorCodeValue, e.errorLevel))`, sin el `.flatMap(Option(_))` de `curateEvent` (`render_templates.py:505-507` frente a `CurationProcessor.scala.tpl:57-58`, AUD-C5). Un `errorCode` nulo lanza `NullPointerException` fuera del `try` de `relacion`, a pesar del «Nunca lanza» (`:501`), y `Curator.scala:89` no lo atrapa. Además, `relacion` vuelve a ejecutar `curar` por evento (dos curaciones por registro, ~28 M por archivo) | Agregar `.flatMap(Option(_))` y meter la extracción dentro del `try`. Opcional: memorizar el registro curado por `eventId` o calcular la relación desde el mismo resultado |
| AUD-C-06 | — | framework | P2 | Defecto 2 (§3). Marcas `ASSUMPTION` de valores que la spec o la Validation aprobada ya fijan, y no hay `supuestos_resueltos` en el esquema de Curation (sí en el de Validation: `edf-validation/.../source-spec.schema.yaml:41`) | Portar el mecanismo de AUD-V-08 (`supuestos_resueltos: [{id, fuente}]` → `RESUELTO <ID> (<fuente>)`) y declarar en ART-030 CUR-PT05, F-12, C-CUR-04, C-CUR-03, CUR-PA04, F-07/D8, C-CUR-07 y C-CUR-08 con su fuente |
| AUD-C-07 | — | framework | P2 | Restos del SISBEN histórico, como HF-19 y HF-30 en Acceptance. `dsl-config-reference.md:20` (`(RT_\|EP_)?…(\\.txt)?`) y `:63-67` (`delimiter = ","`, `index = "tipo_sisben"`). `curation-block.conf.tpl:13` dice «termina en (\.txt)?», pero el patrón generado exige la extensión. `especificacion-sisben.md:35,39` dice «Ñ → N», «PEP sin letras», «PPT original» y «pasaporte conserva ceros». `SKILL.md:105` también dice «PEP sin letras, PPT original» | Actualizar a la spec 1.0.2 y regenerar el ejemplo si cambia |
| AUD-C-08 | — (herramienta) | framework | P2 | Defecto 3 (§3). Lo reproduje en el scratchpad: con `` `subscriberRelation` ``/`` `subscriber` `` en la primera columna de §2 y `` `advertenciasCuracion` `` en otra tabla, sale rc=1 con 3 «NO EXISTE» falsos. Además `publicadas` (`:130`) no incluye `parametros.campo_advertencias`, así que una cita aguas abajo de `advertenciasCuracion` (clave real de spec §10.1) también fallaría | Leer solo la tabla de §1 (hasta el siguiente `## `), saltar `INYECTADOS` también en el contrato y sumar `campo_advertencias` a `publicadas` |
| AUD-C-09 | — | framework | P2 | Defecto 4 (§3). `source-spec.schema.yaml:128` obliga a que cada valor de la `tabla` sea numérico y `csv_homologacion` (`render_templates.py:763-770`) solo escribe la `tabla`: el CSV sintético sale sin la fila `7;;SALVOCONDUCTO PARA REFUGIADO` (spec §9.4 `:210`, «Contenido completo»). No cambia el comportamiento: `homologar` mira `pendientes` antes que la tabla (`Transformations.scala.tpl:64`) y `ClavesHomologacion` solo consulta las claves de la `tabla` | Escribir también las filas de `pendientes` con `cod_experian` vacío (y su `descripcion`) |
| AUD-C-10 | — | framework | P2 | Defecto 5 (§3). `MANIFEST.json` `"skill": "edf-curation-stage"` (`render_templates.py:834`) y 11 cabeceras de plantilla con «edf-curation-stage». El nombre es `edf-curation` (`SKILL.md:2`). Es el mismo defecto que AUD-V-10 (HF-11) y HF-20 | Corregir la constante y las plantillas |
| AUD-C-11 | ART-034 | insumo | P2 | (a) §2 `:32`: `ColombiaIdentityMatcher` figura `[C]` sin decir que no está en la copia del motor. (b) §1 y §5: no cita la evidencia del motor para omitir claves nulas (`EventPinner.scala:64-65`). (c) La ruta `--dir expedientes/sisben/insumos` (`:38`, `:77`) no es la de esta corrida (`ejecuciones/sisben-e2e/expedientes/sisben/insumos`). (d) El encabezado de §1 difiere del de steering `11` §4 | (a) Agregar «decisión `[C]` de spec §11; clase no verificada en `Motor-Oxigen` (solo nivel 3), se confirma en el fork con `yaml-skill-pinning.yaml`». (b) Citar `EventPinner.scala:64-65`. (c) Ruta relativa al expediente. (d) Usar el encabezado del steering |
| AUD-C-12 | ART-030 | insumo | P2 | `omitir_si_nulas` está marcado `[PM]` (`:29`, `:101`), pero es una regla `[C]` del steering `11` §3 y el valor por defecto del esquema (`:104-107`): debería ser `[P]`. `regex_derivacion` (`:79`) no es la de spec §2 (`:36`) y no se declara la diferencia (equivalente en efecto, §1) | Cambiar a `[P]` y anotar la diferencia de forma de la regex |
| AUD-C-13 | — | framework | P2 | Observación abierta de HF-04 (`hallazgos-framework.md:60`). `07-contratos-frontera.md:91` asigna a Curation el efecto «Vaciado del campo con preservación» (`<campo>_original`), pero `edf-curation` no lo implementa: `niveles_anular` deja nulo sin `_original` (`source-spec.schema.yaml:95-102`; `CurationProcessor.scala.tpl:117,125`). Para SISBEN no aplica, porque spec §10.4 usa «nulo con advertencia» (`07:92`) | Agregar un `parametros.niveles_preservar` (con `<campo>_original`) o marcar el efecto en `07` como «sin soporte en `edf-curation`» |

No hubo hallazgos en la clasificación **spec** ni en **motor**.

Nota fuera de la escala. «Ninguna» (spec §10.1) se implementa como `texto`, que recorta los bordes (`:209-210` del insumo). Con el crudo que ahora entrega Validation, es una lectura razonable: la spec solo distingue «Ninguna» de «Valor original» en esas dos filas. Si el PO quiere «Ninguna» literal, tendría que fijarlo la próxima versión de la spec.

## 3. Los 6 posibles defectos que reportó el analista

| # | Defecto | Veredicto | Sev. | Clasificación | Evidencia y alcance |
|:-:|---|---|:-:|---|---|
| 1 | `original` recorta espacios; no cumple HU §5.3 | **Real y más amplio de lo reportado** | **P1** | framework | AUD-C-01. A las tres citas se suma `AcceptedEventReader.scala.tpl:17-18`: corregir solo `Transformaciones` no basta en Scala y la referencia Python quedaría distinta del Scala. Además, la prueba Scala generada fija el valor errado (`SISBENCurationProcessorSpec.scala:39`) |
| 2 | Marcas `ASSUMPTION` de valores que la spec fija; sin `supuestos_resueltos` | **Real** | P2 | framework | AUD-C-06. Las cuatro plantillas y líneas citadas están bien: `CurationSpec.scala.tpl:25` (CUR-PT05 → spec §10.1 `:280`), `:36-37` (F-12 → §9.4 `:200`), `:61` (C-CUR-04 → HU R-23; el insumo dice `origen: requisito` y el MANIFEST lo muestra, pero el código sigue diciendo ASSUMPTION), `:66` (C-CUR-03 → §10.2 `:289`), `lookups.conf.tpl:7` (F-12), `CurationProcessor.scala.tpl:48` (CUR-PA04 «valor inferido» → spec §1 `:22`), `curation-block.conf.tpl:15` (F-07/D8 → spec §2) y `AcceptedEventReader.scala.tpl:4` (C-CUR-07 → `SISBENValidatedEvent.scala:43-66` aprobado). Se suma C-CUR-08 (`CurationSpec.scala.tpl:45`): spec §10.1 no pide padding. Siguen como supuestos legítimos CUR-PT01 (paquete), CUR-PT02 (API del lookup) y C-CUR-05 (nulos como `null`), porque dependen del framework o del motor |
| 3 | `verificar-claves.py:152-157` lee todas las tablas y no exime `INYECTADOS` | **Real (latente)** | P2 | framework | AUD-C-08. Hoy no falla porque §2 del contrato pone `subscriberRelation` sin comillas invertidas; lo reproduje con una copia. Se suma que `advertenciasCuracion` no cuenta como publicada |
| 4 | `homologacion_tipo_documento.tabla` solo numérica; el CSV sale sin la fila `7` | **Real** | P2 | framework | AUD-C-09. Solo afecta la fidelidad del CSV de prueba frente a spec §9.4; no cambia el comportamiento |
| 5 | El manifiesto firma `edf-curation-stage` | **Real** | P2 | framework | AUD-C-10 (`render_templates.py:834`; 11 plantillas) |
| 6 | El HOCON de lookups de Curation no emite `fileFormat` | **Descartado** | — | — | El motor no lo exige para CSV. `SparkOxygenLookup.readLookupFile` solo distingue JSON y lee todo lo demás como CSV con `delimiter` (`oxygen-acceptance/.../lookup/SparkOxygenLookup.scala:34-38`). Los lookups CSV de la copia de `oxygen-curation` no llevan `fileFormat`, y `fileFormat` solo aparece comentado en el ejemplo JSON (`oxygen-curation/src/main/resources/local/oxygen-lookup-local.conf:3-10,77`; `aws/conf/oxygen-lookup-aws.conf:77`). `dsl-config-reference.md:77` da `fileFormat` por defecto `None`. El fragmento sí emite `delimiter = ";"`, que es lo que importa. Queda la salvedad de siempre: `LookupFileReadInfo` y `LookupElement` están en oxygen-core, fuera del repo (F-01) |

## 4. Riesgos RG-019..021

| ID | Veredicto | Nivel | Evaluación |
|---|---|---|---|
| RG-019 (`omitir_si_nulas` frente a «nulo si todos faltan», spec §10.1 `:275`) | **Real; se mantiene la decisión del insumo** | bajo | Hay una contradicción de letra entre la spec y una regla `[C]` del steering `11` §3 (`:60-65`), que manda sobre el formato de frontera (`00` §6, `:89-97`). El motor la respalda. Un `null` explícito llega a Pinning como `JsonNull`, y `EventPinner.scala:64-65` hace `getAsString`, que lanza; los matchers del estilo `ISIdentityMatcher.scala:46-54` usan `.get.toString`. Una clave ausente, en cambio, da `""` (`IdentityMatcher.scala:46-49`; `pinning-muric.md` §3.2). Para el matching, «ausente» y «nulo» significan lo mismo. Solo ocurre si `pri_nombre` y `pri_apellido` quedan vacíos al estandarizar, porque Validation exige que no vengan vacíos (R-14). No viola spec §10.4 regla 3 ni el último escenario de HU §5.3: la causa es `NO_NORMALIZABLE`, no la severidad 3. Mitigación: el caso de fixture de AUD-C-03 y la aceptación explícita del Desarrollador de la diferencia con la spec definitiva. La alternativa que propone ART-034 §5 (quitarla de `omitir_si_nulas` tras ver `getOrElse` en el matcher) **no basta**, porque la excepción también salta en el índice de PIN |
| RG-020 (`standardizedIdType` entero frente a `pinning-muric.md:29` «Texto (CC, NIT, CE)») | **Real, pero la descripción debe matizarse** | alto (impacto N3; probabilidad menor que la descrita) | El entero con código Experian lo fijan spec §10.1 (`:273`) y la decisión de opción A del steering `11` §6 #5 (`:126`). Hay evidencia del mismo nivel 3 en contra de la lectura «texto»: `curation-muric.md:75,114,143` publica `standardizedIdType` como `Option[Int]` (y `standardizedIdNumber` como `Option[Int]`, `:76`). En el índice de PIN, el motor compara por `getAsString`, así que un `1` numérico y un `"1"` de texto dan el mismo `"1"`. Lo que importa es el juego de códigos (Experian `1` frente a `CC`), no el tipo JSON. Corrección de la mitigación: verificar en el fork qué **valores** (no solo qué tipo) publican las otras fuentes de Colombia en `standardizedIdType`, y si `ColombiaIdentityMatcher` compara por `toString`. Es N3 (`06:58`) |
| RG-021 (la Ñ en `standardizedIdName`) | **Real** | medio | Conservar la Ñ es `[C]` para SISBEN (spec §10.2 regla 2, `:289`; HU §5.3 `:255`) y prevalece sobre el «sin tildes» genérico del steering `11` §3 (`:56`), porque es un dato de la ingesta (`00` §6). La propia skill lo dejaba como supuesto «confirmar con Pinning» (C-CUR-03, `decisiones-y-abiertos.md:45`; el ejemplo usa `false`). El repo no dice cómo tratan la Ñ las otras fuentes de Colombia (`curation-muric.md` no lo menciona). Si la transliteran, la regla 2 nunca cruza `PEÑA` con `PENA` (bloques distintos, steering `11` §3 `:54`). La prioridad 1 (tipo + número) no se afecta. Mitigación correcta: confirmarlo al preparar `yaml-skill-pinning.yaml`. Si difiere, pedir una versión nueva de la spec; no cambiar `conservar_enie` desde el análisis |

## 5. Verificaciones ejecutadas

Todas sobre los insumos sin modificar (sha256 en la cabecera). La salida quedó en `/tmp/claude-0/-home-user-edf-framework-IA-Chief/17fd989e-8dfc-5ea8-ad37-44be188bb6cf/scratchpad/aud-cur/`, fuera del repo. No se compiló Scala ni Java ni se usó git para escribir.

| # | Comando | Resultado |
|:-:|---|---|
| 1 | `python3 .kiro/skills/edf-curation/scripts/render_templates.py --spec EXP/insumos/source-spec-curation.yaml --solo-verificar` | rc=0 · `source-spec OK (33 campos curados, 1 con concatenacion; 12 plantillas y 14 eventos esperados calculados)` |
| 2 | `render_templates.py --spec … --out …/scratchpad/aud-cur` | rc=0 · `OK: 12 artefactos … (compilación no_certificada)`. MANIFEST: 12 `archivos`, 3 `otros_archivos`, `zona_horaria_fechas {America/Bogota, requisito}`, `advertencias: []` (sin C-CUR-04, porque las dos zonas coinciden) |
| 3 | Comparación de `expected/curated-events.json` con HU §5.3 y spec §10.1 | 14 curados y 1 descartado por el motor (cur-0015). 34 claves en el orden de §10.1 en los 14, con tipos correctos. 7 de 8 escenarios cumplen; cur-0004 no (AUD-C-01). Advertencias: cur-0005, cur-0006, cur-0009 (formato `<campo>:<MOTIVO>`, sin valores) |
| 4 | `python3 .kiro/skills/edf-curation/scripts/hocon_check.py …/fragmentos/*.conf` | rc=0 · `OK` en los dos `.conf`. El parser es el mínimo de la skill: no hay `pyhocon` instalado y el parse con Typesafe queda **no verificado** (F-01) |
| 5 | `python3 tools/verificar-claves.py --dir EXP/insumos` | rc=0 · `Claves publicadas por Curation: 33 · citas revisadas: 3 · RESULTADO: claves coherentes.` Prueba del defecto 3 sobre una copia en el scratchpad (`aud-cur-vk/`): rc=1 con 3 «NO EXISTE» falsos |
| 6 | `python3 -m pytest .kiro/skills/edf-curation/scripts/tests -q -p no:cacheprovider` | 106 pasadas, 2 omitidas (`pyhocon` ausente: `test_hocon_y_portabilidad.py:44`, `test_render_templates.py:126`) |
| 7 | Lectura del motor | `SparkCurator.scala:173-206,270-286,338-351`; `Curator.scala:60-100`; `CurationProcessor.scala:59`; `SparkOxygenLookup.scala:32-39`; `oxygen-lookup-local.conf`; `IdentityMatcher.scala:13-57`; `ISIdentityMatcher.scala:39-64`; `EventPinner.scala:57-66`. `ColombiaIdentityMatcher`: ausente en `Motor-Oxigen/` |
| 8 | Contrato de entrada | `SISBENValidatedEvent.scala:43-66` (claves de dominio, crudo); `FieldValidators.scala:58-62`; fixture de Acceptance (`fileAsOfDate`, `fileTag`, `subscriberRelation = NUM_DOCUMENTO`) |
| 9 | Estado | `estado-ingesta.json`: `acceptance` está `aprobada` con `transicion_habilitada: false` (evidencia diferida, RG-022). ART-030, ART-034 y ART-035 están `prevalidado`. RG-019 está en `curation_flow`; RG-020 y RG-021 en `pinning` |

### Para aprobar

1. Corregir AUD-C-01 en el framework, con su prueba de regresión, y volver a correr §5 #1-#3 y #6. El escenario «Número de documento estandarizado» debe dar `" 0001000000101"`.
2. Ajustar el fixture (AUD-C-02, AUD-C-03 y la descripción de cur-0004) y el contrato (AUD-C-11).
3. Hacer que el Desarrollador acepte de forma explícita RG-019 (diferencia con la spec definitiva) y matizar la descripción de RG-020.
4. Que el Orquestador habilite la transición de `acceptance` (Paso 0.2 de `edf-curation`) antes de generar ART-031..033.

Los P2 del framework que cambian la salida generada (AUD-C-04, -05, -06, -09, -10) conviene resolverlos en el mismo ciclo que AUD-C-01, para no regenerar dos veces. Los documentales (AUD-C-07, -08, -13) van al lote de cierre.

## 6. Insumos v2

| Campo | Valor |
|---|---|
| Fecha | 2026-11-25 (re-auditoría) |
| Alcance | ART-030 v2 (sha256 `21754c61…`), ART-035 v2 (`accepted-events.input.yaml` `050b942d…`) y ART-034 2.0.0 (`640ba4bc…`), entregados en INT-041 con las decisiones de INT-038. Framework 1.9.7 (commits 955bed3 y 21b6781: HF-32, HF-34..41). Cierre de mis hallazgos AUD-C-01..13 en la salida generada |
| **Dictamen global** | **APROBABLE.** Los tres insumos v2 aplican los cuatro ajustes autorizados (AUD-C-02, -03, -11, -12) y citan su origen. Todos los hallazgos de framework (AUD-C-01, -04..-10, -13) quedan cerrados en la salida generada. Los 8 escenarios de HU §5.3 cumplen, incluido `numeroDocumentoOriginal = " 0001000000101"`, también en la prueba Scala generada. No hay P0 ni P1. Hay tres P2 nuevos, AUD-C-14..16, que no bloquean. Sigue pendiente la habilitación de la transición de `acceptance` (`transicion_habilitada: false` en `estado-ingesta.json`; `edf-curation/SKILL.md:25`), que no depende de estos insumos |

### 6.1 Dictamen por insumo

**ART-030 v2: APROBABLE.**

| Ajuste | Verificación | Resultado |
|---|---|---|
| AUD-C-12 `[P]` | `:26-27`, `:118-120` citan steering `11` §3, `EventPinner.scala:64-65`, INT-038 y PA-007 | Aplicado |
| AUD-C-12 regex | `:96` = regex literal de spec §2 (`spec-sisben.md:36`); `--solo-verificar` la admite | Aplicado. Produce el mismo `fechaCorte` |
| HF-38 | `"7": null` (`:72` del bloque) con `pendientes: ["7"]` y la descripción del 7. El CSV sintético trae `7;;SALVOCONDUCTO PARA REFUGIADO`. Con la referencia, el `7` da `tipoDocumento:HOMOLOGACION_PENDIENTE` (nunca un código) | Aplicado, fiel a spec §9.4 |
| HF-32 | La cabecera no trae el estado (`:3`) | Aplicado |
| RG-019 | Aceptado (`estado-ingesta.json`: `aceptado`, bajo); PA-007 abierta y no bloqueante | Coherente con INT-038 |
| «Ninguna» = `texto` | Declarado `[PM]` como supuesto del aprobador, PA-007 (2) (`:29-30`, `:110-111`) | Coherente con INT-038 |
| Valores de negocio | Iguales a los de la v1 auditada (§1), salvo lo anterior | Sin cambios indebidos |

Verificación de cada `supuestos_resueltos[]` (`:53-63`) contra la marca de la plantilla (`references/decisiones-y-abiertos.md`) y la fuente citada:

| ID | Qué supone la plantilla | Fuente citada | ¿La fuente lo fija? |
|---|---|---|---|
| CUR-PA04 | `outputEventType = 30` inferido (`decisiones:80`) | spec §1 | Sí (`spec-sisben.md:22`) |
| CUR-PT05 | Nombre del campo de advertencias | spec §10.1 | Sí (`:280`) |
| C-CUR-03 | Ñ → N (`:45`) | spec §10.2 r.2; HU R-23 | Sí (`:289`; `hu-sisben.md:92`) |
| C-CUR-04 | Zona de las fechas (`:46`) | spec §10.1; HU R-23 | Sí (`:266`; `hu-sisben.md:92`) |
| C-CUR-05 | Política de severidad 3 y nulos como `null`, salvo las de identidad; «confirmar con Synthesis» (`:47`) | spec §10.2 r.5 y §10.4 r.2-3 | **En parte** (AUD-C-15). La spec fija la política para SISBEN, pero no responde lo que la marca pedía confirmar con Synthesis (que un `null` del evento sea aceptable con LFP). Además, «ninguna clave se omite» choca con la omisión aceptada en RG-019 |
| C-CUR-07 | Claves de `validatedRecordJson` = nombres de dominio (`:49`) | Validation aprobada APR-008 | Sí (`SISBENValidatedEvent.scala:43-66`). La aprobación vigente de ART-011 es APR-009 (AUD-C-15) |
| C-CUR-08 | Reglas por tipo y padding (`:50`) | spec §10.1 | Sí (`:262`; sin excepción por tipo ni padding) |
| F-07 | Nombre RT/EP, tipo por prefijo (`:29`) | spec §2 | Sí (`:38`, siempre `REGULAR`) |
| F-12 | Archivo, columnas, clave y delimitador de los lookups (`:32`) | spec §9.4 | Sí, en lo que usa Curation (solo el 202). El delimitador `;` está en la introducción de §9 (`:166`), no en §9.4 (AUD-C-15) |
| D8 | Nombre con marca de tiempo (`:16`) | spec §2 | Sí (`:35-37`) |

Quedan abiertos con razón CUR-PT01 (paquete) y CUR-PT02 (API del lookup y lookup caído): son del framework y del motor, y la spec no los fija. En la salida generada solo quedan esas dos marcas `ASSUMPTION` (tres líneas); las otras diez salen como `RESUELTO <ID> (<fuente>)` y están en `MANIFEST.json["supuestos_resueltos"]`.

**ART-035 v2: APROBABLE.**

- AUD-C-02. `base_registro.NUM_DOCUMENTO = "1000000100"` y cur-0004 lleva el literal de la HU. Los 18 eventos curados tienen `subscriberRelation` distintos, así que ningún par comparte la clave de duplicado de spec §6.
- AUD-C-03, RG-019. cur-0016 (`:132-136`): `primerNombre` y `primerApellido` nulos, advertencias `primerApellido:NO_NORMALIZABLE` y `primerNombre:NO_NORMALIZABLE` y `standardizedIdName` **ausente**. `subscriberRelation = 1_1000000116`, sin `COMPONENTE_FALTANTE`.
- AUD-C-03, homologaciones. cur-0017..0019 dan `4→4`, `5→8` y `8→9` (spec §9.4 `:207-211`).
- La descripción de cur-0004 ya no menciona el defecto.
- RG-005 y RG-007 siguen cubiertos (cur-0006..0008).

**ART-034 2.0.0: APROBABLE.**

- AUD-C-11 (a): `ColombiaIdentityMatcher` figura como decisión `[C]` de spec §11 y como clase no presente en `Motor-Oxigen/` (`:32`). Lo verifiqué: no está.
- AUD-C-11 (b): cita `EventPinner.scala:64-65` e `IdentityMatcher.scala:46-49` (`:9`, `:21`, `:69`, `:77`).
- AUD-C-11 (c): las rutas son relativas al expediente (`:38`, `:78`).
- AUD-C-11 (d): el encabezado de §1 es el de steering `11` §4 (`:19`).
- RG-020 está redactado sobre el juego de códigos y sobre la comparación del matcher (`:70`), como pidió INT-038. Sigue en nivel alto y condiciona el insumo de Pinning.
- Ejemplos y §3 corresponden a la salida v2.
- Observación menor: la clase no verificada en el motor lleva `[PM]` (`:32`). No es una propuesta del modelo; encaja mejor `[A:matcher-colombia]` (steering `11` §6 #7). Va en AUD-C-16.

### 6.2 Cierre de los hallazgos anteriores (salida generada en el scratchpad, `aud-cur-v2/`)

| Hallazgo | Evidencia en la salida 1.9.7 | Estado |
|---|---|---|
| AUD-C-01 (P1) | `Transformaciones.original` devuelve el valor sin recortar (`Transformaciones.scala:33`); `AcceptedRecord.crudo` no hace `trim` (`SISBENAcceptedRecord.scala:20`); el processor solo usa el crudo para `original` (`SISBENCurationProcessor.scala:185`). `expected/` da cur-0004 `" 0001000000101"`, y la prueba Scala generada fija `" 0001000000101"` como esperado. Con la referencia, `TIP_DOCUMENTO " 3 "` da `tipoDocumentoSisben = " 3 "` y `tipoDocumento = 1` | **Cerrado** |
| AUD-C-04 | `accepted-events.json`: `fileAsOfDate = 1769835600000` (último día del periodo) y `fileTag = "REGULAR"` | **Cerrado** |
| AUD-C-05 | El override filtra los `errorCode` nulos y envuelve todo en `try`. La curación se reutiliza con un `ThreadLocal` del último evento (`ev eq event`): en el motor, `curateEvent` va antes que `subscriberRelation` en las dos rutas (`Curator.scala:78,89`; `:156` y `:179`) | **Cerrado** |
| AUD-C-06 | 10 `RESUELTO`; solo quedan CUR-PT01 y CUR-PT02 | **Cerrado** (fuentes en §6.1, con la salvedad de AUD-C-15) |
| AUD-C-07 | `dsl-config-reference.md:17-19,68`, `especificacion-sisben.md:3-7` y `SKILL.md:114-117` marcan los ejemplos como SISBEN **histórico**, que no es fuente. El comentario del bloque HOCON ya no dice `(\.txt)?` | **Cerrado** (se dejó como histórico, no se reescribió; es suficiente) |
| AUD-C-08 | `verificar-claves.py` cuenta 34 claves publicadas (más `campo_advertencias`), lee solo §1 del contrato (3 citas, aunque §2 ya trae `` `subscriberRelation` `` entre comillas) y exime `INYECTADOS` (`:178`, `:184`) | **Cerrado** |
| AUD-C-09 | El CSV trae la fila `7;;SALVOCONDUCTO PARA REFUGIADO` | **Cerrado** |
| AUD-C-10 | `MANIFEST.json["skill"] = "edf-curation"`; 0 apariciones de `edf-curation-stage` en la salida | **Cerrado** |
| AUD-C-13 | `07-contratos-frontera.md:91`: «Sin soporte en `edf-curation` (HF-41)» | **Cerrado** |

### 6.3 Hallazgos nuevos

| ID | Insumo | Clasificación | Sev. | Evidencia | Corrección |
|---|---|---|:-:|---|---|
| AUD-C-14 | — | framework | P2 | `expected/resumen.json` informa `"claves_omitidas_por_severidad": {"cur-0016": ["standardizedIdName"]}`. La clave no se omitió por severidad (no hay códigos y las dos listas de niveles están vacías), sino por `omitir_si_nulas`. `render_templates.py:357-359,370` cuenta como «por severidad» toda clave ausente. Quien lea el resumen concluiría que se violó spec §10.4 regla 3 | Separar `claves_omitidas_por_severidad` (niveles) de `claves_omitidas_por_nulas` (`omitir_si_nulas`) |
| AUD-C-15 | ART-030 | insumo | P2 | `supuestos_resueltos` (`:58-62`). (a) C-CUR-05 se resuelve solo en parte: la spec fija la política, pero no la confirmación con Synthesis que pedía la marca, y la fuente cita «ninguna clave se omite» cuando RG-019 acepta omitir `standardizedIdName`. (b) C-CUR-07 cita APR-008; la aprobación vigente de ART-011 es APR-009. (c) F-12 cita §9.4, pero el delimitador está en §9 (`spec-sisben.md:166`) | (a) Citar además RG-019/INT-038 y dejar la confirmación con Synthesis para el insumo de Synthesis (spec §12: LFP por defecto), o no resolver C-CUR-05. (b) Citar APR-009. (c) Citar «§9 y §9.4». No cambia el código generado salvo el texto de los comentarios |
| AUD-C-16 | ART-034 | insumo | P2 | `:32`: el marcador `[PM]` para «clase no verificada en el motor» | Usar `[A:matcher-colombia]` (steering `11` §6 #7) o `[P]` con la cita de `pinning-muric.md` |

No hay hallazgos nuevos en las clasificaciones spec, motor ni artefacto.

Nota fuera de la escala: `original` publica `""` cuando el valor crudo es vacío. No se da en SISBEN, porque `tip_documento` y `num_documento` son obligatorios (spec §3).

### 6.4 Verificaciones

| # | Comando | Resultado |
|:-:|---|---|
| 1 | `render_templates.py --spec EXP/insumos/source-spec-curation.yaml --solo-verificar` | rc=0 · `source-spec OK (33 campos curados, 1 con concatenacion; 12 plantillas y 18 eventos esperados calculados)` |
| 2 | Generación completa en `/tmp/claude-0/-home-user-edf-framework-IA-Chief/17fd989e-8dfc-5ea8-ad37-44be188bb6cf/scratchpad/aud-cur-v2/` | rc=0, 12 artefactos. `MANIFEST.json`: `skill: edf-curation`, `advertencias: []`, 10 `supuestos_resueltos` |
| 3 | Comprobación de `expected/` | 19 eventos de entrada, 18 curados; cur-0015 descartado (CUR-AP02); cur-0014 (`.TXT`) curado. Las 34 claves en el orden de spec §10.1 en los 18, salvo `standardizedIdName` en cur-0016. Los 8 escenarios de HU §5.3 cumplen; tipos 4/5/8 → 4/8/9; 18 `subscriberRelation` distintos |
| 4 | `hocon_check.py fragmentos/*.conf` | rc=0, OK en los dos. Además, `pyhocon` 0.3.61, instalado solo en el scratchpad (`pylibs/`): parse OK de los dos fragmentos (`filePattern`, `processor`, `tableName`, `delimiter ";"`, `keys`) |
| 5 | `python3 tools/verificar-claves.py --dir EXP/insumos` | rc=0 · `Claves publicadas por Curation: 34 · citas revisadas: 3 · claves coherentes` |
| 6 | Suite de la skill | Sin `pyhocon`: 126 pasadas, 2 omitidas. Con `PYTHONPATH` al `pyhocon` del scratchpad: **128 pasadas, 0 omitidas** |
| 7 | Referencia, casos borde | `TIP_DOCUMENTO " 3 "` → `tipoDocumentoSisben " 3 "`, tipo 1. `7` → `HOMOLOGACION_PENDIENTE`, `standardizedIdType` ausente, `COMPONENTE_FALTANTE`. `ppt` → 11 |

No escribí ningún otro archivo del repo ni compilé Scala ni Java.

## 7. Propuesta

| Campo | Valor |
|---|---|
| Fecha | 2026-11-25 |
| Alcance | `EXP/propuestas/curation/` generada por `edf-curation` (framework 1.9.7) desde ART-030 v2.1 (APR-015, sha256 `506e8b9a…`), ART-035 (APR-015, `050b942d…`) y ART-034 2.0.0 (APR-016). ART-031: Capa A `FieldMapping`/`Transformaciones`, `SISBENCurationSpec`, `SISBENCuratedEvent`, `SISBENAcceptedRecord`, `SISBENCurationProcessor`. ART-032: los tres fragmentos. ART-033: `accepted-events.json` (19 eventos), CSV 202 + `LEEME.md`, `TransformacionesSpec`, `SISBENCurationProcessorSpec`, más `expected/` y `MANIFEST.json`. Transición `acceptance → curation_flow` aprobada (APR-017; `transicion_habilitada: true` en `acceptance`) |
| Cambios del insumo desde la v2 auditada (§6) | ART-030 v2.1 aplica AUD-C-15: C-CUR-05 vuelve a quedar abierto (sale `ASSUMPTION C-CUR-05` en `SISBENCuratedEvent.scala:5`), C-CUR-07 cita APR-009 y F-12 cita §9 y §9.4. ART-034 aplica AUD-C-16: el lector pasa a `[A:PA-008]` y `[PM]` se usa con su significado de steering `05:48` (pendiente manual). Correcto |
| **Dictamen global** | **APROBADO** (N2), con la compilación y la ejecución **no certificadas** (F-01, RG-006) y la evidencia de pipeline diferida. La propuesta es **reproducible**: `diff -r` contra una regeneración da vacío. Cumple los 8 escenarios de HU §5.3 en `expected/` y en el Spec Scala, las 34 claves de spec §10.1, el contrato con Pinning y los contratos del motor. No hay P0 ni P1. Los 8 posibles defectos son reales salvo el 8, que es real pero latente. Todos son P2 de framework y van al lote de cierre. Ninguno cambia un valor publicado con entradas que Acceptance pueda entregar |

### 7.1 Dictamen por artefacto

**ART-031 código: APROBADO** (observaciones AUD-C-18, AUD-C-21, AUD-C-22).

| Contrato | Evidencia | Resultado |
|---|---|---|
| Constructor y firma del motor | `class SISBENCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context)` (`SISBENCurationProcessor.scala:29`). La base del motor declara `curateEvent` abstracto, `outputEventType` como `val`, y `enrichEventWithPayload` y `subscriberRelation` como métodos con default (`CurationProcessor.scala:36,40,56,59`) | Coincide (compilación sin certificar) |
| `outputEventType = 30`, `payload` conservado | `:48-52` (`RESUELTO CUR-PA04`). `DefaultOxygenCurationProcessor` no se usa | Correcto (spec §1) |
| Gancho `subscriberRelation` | El motor lo invoca después de `curateEvent` en las dos rutas (`Curator.scala:78,89`; `:156,179`). El `ThreadLocal` del último evento (`:58-85`) evita curar dos veces, filtra los `errorCode` nulos y todo va dentro del `try` | Correcto |
| Homologación | Tabla embebida = spec §9.4 (`SISBENCurationSpec.scala:74-78`), `Pendientes = {7}`, fusión con el lookup (gana el lookup). `SparkOxygenLookup.readLookupFile` lee el CSV con `delimiter` (`:32-39`) | Correcto |
| `original` sin recortar | `Transformaciones.original` y `AcceptedRecord.crudo` (HF-34) | Correcto |
| `omitir_si_nulas`, severidad 3 | `OmitirSiNulas` = las tres `standardizedId*` (`:67`); `NivelesNoAlmacenar`/`NivelesAnular` vacíos (`:116-117`, spec §10.4) | Correcto. El comentario fijo de `SISBENCurationProcessor.scala:167` contradice esta configuración (AUD-C-18) |
| Sin PII ni `println` | Los únicos logs (`:42`, `:158`) llevan la tabla y el conteo, o el `eventId` y la clase de la excepción | Correcto |
| Marcas | 4 `ASSUMPTION` (CUR-PT01, CUR-PT02 ×2, C-CUR-05); 9 `RESUELTO` = `MANIFEST.json["supuestos_resueltos"]` | Coherente con ART-030 v2.1 |

**ART-032 fragmentos: APROBADO** (observación AUD-C-23).

- `curation-common-application-inner.sisben.conf`: un solo objeto `{filePattern, processor}`. El `filePattern` es literal de spec §2 y la clase existe en ART-031. No toca claves de etapa.
- `oxygen-lookup-inner.sisben.conf`: solo el 202, con `${oxygen.application.paths.lookupHome}`, `delimiter = ";"` y `keys = cod_sisben`. Sin `fileFormat`, igual que los CSV del motor (§3 defecto 6).
- `colombia-domain-dictionary-inner.sisben.json`: 34 entradas `{name, description}` en formato `DomainDictionaryV2` (`oxygen-synthesis/.../DomainDictionaryV2.java:47-55`). Sin `handler`, de modo que rige LFP, como pide spec §12. Queda abierto C-CUR-06: todos los campos llevan `dataType` por defecto `STRING` (`:51`), también los enteros, los largos y la lista `advertenciasCuracion`. Curation no usa el `dataType`: `domainDictionarType` no se lee en `Curator.scala:152-187`. Lo debe fijar el insumo de Synthesis.
- `hocon_check` y `pyhocon` real: OK.

**ART-033 pruebas y fixture: APROBADO** (observaciones AUD-C-14 = HF-45, AUD-C-17, AUD-C-19).

- 19 eventos: `fileAsOfDate = 1769835600000` y `fileTag = REGULAR`, salvo cur-0015 (otra fuente, descartado). Documentos sintéticos (`1000000xxx`, `PA…`).
- Los 18 `esperado` del Spec Scala son **idénticos**, en claves, orden y valores, a `expected/curated-events.json` (comparación automática).
- `TransformacionesSpec` (14 pruebas) prueba `original` con `" 0001000000101"` y `" 3 "` (`:56-59`).
- CSV 202 con la fila del 7; `LEEME.md` como marca de sintético.

### 7.2 HU §5.3, spec §10.1 y casos pedidos (en `expected/` y en el Spec Scala)

| Escenario | Evento | Resultado |
|---|---|---|
| Estandarización de nombres | cur-0002 | `GARCIA LOPEZ` / `JOSE` ✓ |
| Nombre completo estandarizado | cur-0003 | `ANA PEÑA RUIZ` ✓ |
| Homologación del tipo de documento | cur-0001 | `standardizedIdType 1`, `tipoDocumentoSisben "3"` ✓ |
| Número de documento estandarizado | cur-0004 | `standardizedIdNumber "1000000101"`, `numeroDocumentoOriginal " 0001000000101"` ✓ (también en el Spec Scala) |
| Fechas a epoch | cur-0001 y cur-0010 | 1768021200000 ✓ |
| Fecha de corte y tipo de archivo | cur-0001 | 1769835600000, `REGULAR` ✓ |
| Nombre que queda vacío | cur-0005 | `primerApellido` nulo y `primerApellido:NO_NORMALIZABLE` ✓ |
| Campo con inconsistencia sev. 3 | cur-0006 | `idMarca` nulo e `idMarca:FORMATO_INVALIDO`, `RUIZ`, 34 claves ✓ |
| RG-019 | cur-0016 | `standardizedIdName` ausente; `primerApellido:NO_NORMALIZABLE` y `primerNombre:NO_NORMALIZABLE`; `1_1000000116` ✓ |
| Tipos 4, 5 y 8 (spec §9.4) | cur-0017..0019 | 4, 8 y 9 ✓ |
| Extensión `.TXT` / otra fuente | cur-0014 / cur-0015 | Curado / descartado sin processor (CUR-AP02) ✓ |

### 7.3 Los 8 posibles defectos que reportó el generador

| # | Defecto | Veredicto | Sev. | Clasificación | Evidencia y corrección |
|:-:|---|---|:-:|---|---|
| 1 | HF-45: `resumen.json` llama «por severidad» a la omisión por `omitir_si_nulas` | **Real; sigue abierto** (= AUD-C-14) | P2 | framework | `render_templates.py:357-359,370`; `expected/resumen.json`: `claves_omitidas_por_severidad: {"cur-0016": ["standardizedIdName"]}`. Separar las dos causas |
| 2 | La prueba de `subscriberRelation` solo cubre el camino feliz | **Real** (AUD-C-17) | P2 | **framework** | `casos_relacion_scala` (`render_templates.py:613-633`) genera aserciones solo desde el fixture. Las 18 dan `.contains("subscriberRelation:COMPONENTE_FALTANTE") == false` (`SISBENCurationProcessorSpec.scala:114` en adelante), así que el camino que devuelve el aceptado (`"COPIADO"`) no se prueba en Scala. Solo lo prueba la referencia Python (`test_curation_reference.py:122`). No es del insumo: con registros aceptados el caso es inalcanzable, porque Validation rechaza el 7 y los tipos desconocidos (ERR_023/ERR_014) y los números vacíos o en ceros (ERR_015/ERR_029). Si se pusiera en el fixture sería una precondición inyectada. Corrección: que el generador agregue al Spec un caso sintético fijo, fuera del fixture (p. ej. `TIP_DOCUMENTO "7"`), que espere el aceptado y la advertencia |
| 3 | Comentario fijo «severidad 3 → no se almacena; severidad 0 → se anula» | **Real** (AUD-C-18) | P2 | framework | `CurationProcessor.scala.tpl:112` → `SISBENCurationProcessor.scala:167`. Para SISBEN es falso: spec §10.4 publica estandarizado y las dos listas están vacías (`SISBENCurationSpec.scala:116-117`). Es solo un comentario; el código lee la configuración. Generarlo desde `parametros` o decir «según `NivelesNoAlmacenar`/`NivelesAnular`» |
| 4 | `SourceProcessorSpec.scala.tpl:7` cita «runbook-ejecucion.md §fork» | **Real** (AUD-C-19) | P2 | framework | El runbook no tiene esa sección; la prueba del fork está en §2, «Prueba del lado del fork (AUD-C9)» (`runbook-ejecucion.md:49,60-65`). Sale en `SISBENCurationProcessorSpec.scala:7`. Citar «§2» |
| 5 | `checklist-entrega.md:25` pide `ASSUMPTION:` en `outputEventType` | **Real** (AUD-C-20) | P2 | framework | Con HF-36 sale `RESUELTO CUR-PA04` (`SISBENCurationProcessor.scala:48`). Además el checklist fija `= 30`, que es el valor de SISBEN, en una lista genérica. Cambiar a «`outputEventType` = event type de la spec, con `ASSUMPTION CUR-PA04` o `RESUELTO CUR-PA04`» |
| 6 | La referencia usa `strip()`/`\s` Unicode y Scala `trim`/`\\s` ASCII | **Real** (AUD-C-21) | P2 | framework | `curation_reference.py:77,172` frente a `Transformations.scala.tpl:21,80`. Lo comprobé con la referencia: `fecha_epoch("10/01/2026 ")` da 1768021200000, mientras que en Scala `trim` no quita el NBSP y el parseo estricto falla (nulo y `FORMATO_INVALIDO`). Es alcanzable: una fecha de severidad 3 con NBSP pasa Validation (que también recorta en ASCII) con `SISBEN_INC_018`/`019`. El oráculo `expected/` divergiría del Scala sin que ninguna prueba lo detecte (el fixture no trae NBSP). Alinear la referencia con Java (`strip` solo de `\x00-\x20`, `re.ASCII`) y agregar un caso NBSP a las dos suites |
| 7 | Motivos fuera de la lista cerrada de spec §10.1 | **Real** (AUD-C-22) | P2 | framework (+ spec) | La spec cierra la lista: `NO_NORMALIZABLE`, `FORMATO_INVALIDO`, `NO_HOMOLOGABLE`, `HOMOLOGACION_PENDIENTE`, `ARCHIVO_NO_RECONOCIDO` y `registro:REGISTRO_ILEGIBLE` (`spec-sisben.md:280`). Curation puede emitir además `NIVEL_DESCONOCIDO`, `COMPONENTE_FALTANTE` y `ERROR_INTERNO` (`FieldMapping.scala:71-75`; `SISBENCurationProcessor.scala:129,159,172`). Ninguno sale con entradas aceptadas: Validation siempre pone nivel `1`/`3` a los códigos de campo, los componentes no faltan (defecto 2) y `ERROR_INTERNO` solo aparece ante una excepción. `NIVEL_DESCONOCIDO` usa además el nombre del campo **de entrada** (`ID_MARCA:…`) y no el campo curado, contra el formato `<campo curado>:<MOTIVO>`. Corrección: mapear al destino o destinos curados y declarar los tres motivos defensivos en el contrato y en PA-007, para la próxima versión de la spec |
| 8 | El 202 queda registrado en los lookups de Validation (con `fileFormat`) y de Curation | **Real, latente** (AUD-C-23) | P2 | framework | `lookups-sisben.conf` de Validation lo agrega al «archivo inner» sin nombrarlo; el de Curation, a `oxygen-lookup-inner.conf` (ART-030 `rutas_destino.lookups_conf`; `runbook-ejecucion.md:42`). Si el fork comparte un solo inner, la tabla queda dos veces. El motor indexa por `tableName` (`loadCache(lookups: Map[String, LookupElement])`, `SparkOxygenLookup.scala:30`), así que una entrada pisa a la otra sin error. Hoy son equivalentes (mismo archivo, `;` y llave; `fileFormat` no cambia nada en CSV), pero si una cambia, la otra se pierde en silencio y contra la regla aditiva. Corrección: que la nota de fusión de los dos generadores diga «si el inner es común y ya existe `tableName`, no duplicarlo», y fijar en `edf-validation` el nombre del archivo destino |

### 7.4 Hallazgos nuevos

| ID | Artefacto | Clasificación | Sev. | Evidencia | Corrección |
|---|---|---|:-:|---|---|
| AUD-C-17 a AUD-C-23 | — | framework | P2 | Defectos 2 a 8 (§7.3) | Lote de cierre |
| AUD-C-24 | ART-033 (`MANIFEST.json`) | framework | P2 | `MANIFEST.json` solo trae `sha256` para `otros_archivos` (CSV 202 y `expected/`). Las 12 entradas de `archivos` (todo el código, el Spec y los fragmentos) y `LEEME.md` no tienen hash, aunque `SKILL.md:190` dice que lo cubre todo salvo el propio manifiesto (ADR-G3). El manifiesto de Validation sí trae hash de sus 18 archivos. La integridad de lo que se aprueba no se puede verificar con el manifiesto. Hoy se compensa con la reproducción (`diff -r` vacío) y con el hash que el Orquestador registra al aprobar | Agregar `sha256` a cada entrada de `archivos` y a `marcas_sintetico` |

No hay hallazgos de insumo, de spec (salvo la parte de spec de AUD-C-22) ni de motor. Ningún hallazgo obliga a regenerar antes de aprobar: AUD-C-18, -19 y -22 cambian comentarios o motivos inalcanzables, y AUD-C-17, -21 y -24 agregan pruebas o metadatos.

### 7.5 Verificaciones

| # | Comando | Resultado |
|:-:|---|---|
| 1 | `render_templates.py --spec EXP/insumos/source-spec-curation.yaml --solo-verificar` | rc=0 · 33 campos curados, 12 plantillas, 18 eventos esperados |
| 2 | Regeneración en `/tmp/claude-0/-home-user-edf-framework-IA-Chief/17fd989e-8dfc-5ea8-ad37-44be188bb6cf/scratchpad/aud-cur-prop/` y `diff -r` contra `EXP/propuestas/curation/` | rc=0 · **diff vacío** (reproducible). Los `sha256` de `otros_archivos` coinciden. MANIFEST: `skill: edf-curation`, `compilacion: no_certificada`, `advertencias: []`, zona `America/Bogota` (requisito) |
| 3 | `hocon_check.py` sobre los fragmentos de la propuesta, con `PYTHONPATH` al `pyhocon` 0.3.61 del scratchpad | rc=0, OK en los dos |
| 4 | Suite de la skill con `pyhocon` | 128 pasadas, 0 omitidas |
| 5 | `python3 tools/verificar-claves.py --dir EXP/insumos` | rc=0 · 34 claves publicadas, 9 citas revisadas (contrato e insumo de Pinning ART-050), coherentes |
| 6 | Comparación automática Spec Scala ↔ `expected/` | 18/18 iguales; casos de §7.2 ✓ |
| 7 | Lectura del motor | `CurationProcessor.scala:17-81`; `DefaultOxygenCurationProcessor.scala:16-35`; `Curator.scala:60-104,152-187`; `SparkCurator.scala:173-206`; `SparkOxygenLookup.scala:30-39`; `DomainDictionaryV2.java:47-55` |
| 8 | Referencia, NBSP | `texto(" 123") = "123"`, `fecha_epoch("10/01/2026 ") = 1768021200000` (divergencia con Scala, AUD-C-21) |

No escribí ningún otro archivo del repo, no usé git salvo para leer, ni compilé Scala o Java.

## 8. Re-auditoría por diferencia: ART-030 v2.1 (§3d-bis) y deriva de la propuesta aprobada

| Campo | Valor |
|---|---|
| Fecha | 2026-11-25 · framework 1.9.14 (el pedido dice 1.9.13; `VERSION` = 1.9.14) |
| Alcance | (1) ART-030: aprobado v2 (APR-015, sha256 `506e8b9a…`, copia en `SP/aprobados/`) frente a v2.1 (`EXP/insumos/`, `eb7a75a0…`), con las condiciones (a)-(d) de `edf-orquestador/references/03` §3d-bis (`:90-112`). (2) Deriva de ART-031..033 aprobados (APR-019) frente a una regeneración con el generador actual. `SP` = `/tmp/claude-0/-home-user-edf-framework-IA-Chief/17fd989e-8dfc-5ea8-ad37-44be188bb6cf/scratchpad` |
| **Dictamen (1)** | **ART-030 v2.1: revisión NO SUSTANTIVA confirmada. Se cumplen (a)-(d).** La cita nueva es correcta. Hay que corregir la evidencia que dio el analista para (a) (AUD-C-25) y conviene precisar (c) y (d) en la justificación de la aprobación (AUD-C-26) |
| **Dictamen (2)** | **Hay deriva, y una parte cambia código desplegable** (HF-53, en una rama que no se alcanza). Recomiendo **§3d ahora**, en el mismo ciclo de aprobación que el §3d-bis de ART-030 (AUD-C-27) |

### 8.1 ART-030 v2.1: condiciones de §3d-bis

`diff` entre el aprobado y v2.1: cambian la línea de versión (`:3`), la cita de la cabecera (`:26-28`), una nota de cambios nueva (`:48-50`) y el comentario de `omitir_si_nulas` (`:123-125`). Todo son comentarios: el YAML parseado es **idéntico** (`yaml.safe_load` de los dos, `==` → `True`).

| Condición | Evidencia | Dictamen |
|---|---|---|
| **(a)** Ningún valor desplegable cambia | Regeneré con el **mismo** generador (1.9.14) el aprobado v2 (`SP/aud-3dbis/out-v2/`, con el fixture ART-035 sin cambios, `050b942d…`) y v2.1 (`SP/deriva-cur/`): **`diff -r` vacío**, también en los comentarios. Coincide con lo que hizo el analista (`SP/nsub/cur-old` = `SP/nsub/cur`, `diff -r` vacío). `--solo-verificar` rc=0; `verificar-claves` coherente | **Cumple** |
| **(b)** Solo cambian comentarios, citas o metadatos | Ver el `diff` de arriba | **Cumple** |
| **(c)** Sin cambio de decisión, alcance, riesgo ni marcador | La decisión `omitir_si_nulas` y su marcador `[P]` no cambian. Lo nuevo es la **evidencia**: `[P]` para el matcher de referencia y `[PM]` «para Colombia (RG-025)». Eso no recalifica la decisión. Repite lo que el Orquestador ya registró en la mitigación de RG-019 (`estado-ingesta.json`: «…ISIdentityMatcher.scala:46-54, evidencia corregida por AUD-P-05…; para Colombia queda [PM] (RG-025)») y sigue §3e («la otra se marca `[PM]`») | **Cumple**, con la precisión de AUD-C-26 |
| **(d)** Sin preguntas ni riesgos nuevos | `curation_flow.riesgos` = RG-005, -007, -008, -016, -019, -023 y `preguntas_abiertas` = [], sin cambios. RG-025 (etapa `pinning`) entra solo como cita dentro de la mitigación de RG-019, que ya lo citaba. Ningún valor ni artefacto de Curation depende de RG-025: el código generado es idéntico por (a) | **Cumple**, con la precisión de AUD-C-26 |
| Riesgo del insumo | `medio` (no crítico) | §3d-bis aplicable |

**¿La cita nueva es correcta?** Sí. `Motor-Oxigen/oxygen-pinning/src/main/scala/com/experian/edf/oxygen/pinning/extension/ISIdentityMatcher.scala:46-54` hace `identity.attributes.get(<clave>).get.toString`. Un valor `null` presente da `Some(null)` y `toString` lanza NPE. Un atributo ausente llega como `""` (`IdentityMatcher.scala:46-49`) y no lanza. Lo de «válido solo para el matcher de referencia» también es exacto: ese matcher lee los slots `firstName`, `nationalId` y `cellPhone` de `ISIdentityAttributesKey`, no las `standardizedId*`, así que es evidencia por analogía. `ColombiaIdentityMatcher` no está en la copia (RG-025), por eso el `[PM]`. La cita anterior a `EventPinner.scala:64-65` estaba mal atribuida (`addToPinIndex`, que el batch no usa; AUD-P-05).

### 8.2 Deriva de ART-031..033 (aprobados en APR-019) frente al generador 1.9.14

`diff -r SP/deriva-cur EXP/propuestas/curation` (`EXP/propuestas/curation/` no cambió desde 5f47629). Son **idénticos**: los tres fragmentos HOCON/JSON, `expected/curated-events.json`, `accepted-events.json`, `Transformaciones.scala`, `SISBENCurationSpec.scala`, `SISBENCuratedEvent.scala` y `SISBENAcceptedRecord.scala`. **Difieren** los siguientes:

| Archivo (artefacto) | Diferencia | Origen | Clasificación |
|---|---|---|---|
| `SISBENCurationProcessor.scala:167-169` (ART-031) | Comentario de RF-CUR-10 corregido | HF-49 (AUD-C-18) | Comentario |
| `SISBENCurationProcessor.scala:173-176` (ART-031) | **Código.** `advNivel` pasa de `<CAMPO_ENTRADA>:NIVEL_DESCONOCIDO` a una advertencia por destino curado (p. ej. `ERR_015` con nivel nulo: antes `NUM_DOCUMENTO:NIVEL_DESCONOCIDO`, ahora `numeroDocumento:…` y `numeroDocumentoOriginal:…`, según la referencia actual) | HF-53 (AUD-C-22) | **Comportamiento desplegable**: cambia el contenido publicado de `advertenciasCuracion`. Solo con `errorLevel` nulo, que la Validation aprobada no emite (niveles `1`/`3`): con entradas aceptadas no se alcanza, y por eso `expected/curated-events.json` sale idéntico |
| `FieldMapping.scala:63-67` (ART-031) | Docstring de los motivos defensivos | HF-53 | Comentario |
| `TransformacionesSpec.scala` (ART-033) | Prueba nueva de NBSP | HF-52 (AUD-C-21) | Prueba |
| `SISBENCurationProcessorSpec.scala` (ART-033) | Cita del runbook (§2) y prueba fija nueva de `COMPONENTE_FALTANTE` | HF-50, HF-48 (AUD-C-19, -17) | Prueba y comentario |
| `expected/resumen.json` (ART-033) | `claves_omitidas_por_severidad: {}` y clave nueva `claves_omitidas_por_nulas` (cur-0016) | HF-45 (AUD-C-14) | Metadato de prueba |
| `MANIFEST.json` | `sha256` en las 12 `archivos` y en `marcas_sintetico`, y el hash nuevo de `resumen.json` | AUD-C-24 | Metadato |

Conclusión: la deriva no toca el HOCON, el domain dictionary ni ningún valor curado alcanzable. Sí cambia una línea de **código desplegable** (HF-53), aunque su efecto sea latente. Por la regla de §3d (`:81-88`), que el código aprobado difiera del que genera la skill corregida requiere regenerar, re-auditar por diferencia y hacer una nueva aprobación `tipo: artefacto`. No se puede tratar como §3d-bis, porque ese procedimiento es para insumos.

**Recomendación: §3d ahora, no diferido al fork.**
- La evidencia diferida RG-023 (APR-019: compilación y Specs en verde en el fork sobre los 19 aceptados) debe obtenerse sobre el código que se va a desplegar. Si se integra ART-031/033 de APR-019 y luego se regenera, hay que repetir esa evidencia.
- Los Specs nuevos (HF-48 `COMPONENTE_FALTANTE`, HF-52 NBSP) son justo la cobertura que faltaba (AUD-C-17, AUD-C-21) y deben correr en el fork.
- El costo es bajo: esta sección ya es la re-auditoría por diferencia (comportamiento alcanzable conservado: `curated-events.json`, fragmentos y `accepted-events.json` idénticos). Solo falta la aprobación `tipo: artefacto` de ART-031/032/033 con los hashes de `SP/deriva-cur/`, copiado a `propuestas/curation/`, con un evento `correccion` que cite APR-019 y HF-45/48/49/50/52/53 y AUD-C-24. Puede ir en el mismo ciclo que la re-aprobación §3d-bis de ART-030.
- Ni la etapa ni la transición se re-aprueban: no cambia ni el alcance, ni el riesgo, ni el contrato con Pinning (`subscriberRelation`, las tres claves y su normalización son iguales).

Si el aprobador prefiere diferirlo, que quede como **condición explícita antes de integrar en el fork** («integrar solo la regeneración 1.9.14 de ART-031/033, re-aprobada por §3d») y no como simple nota.

### 8.3 Hallazgos nuevos

| ID | Objeto | Clasificación | Sev. | Evidencia | Corrección |
|---|---|---|:-:|---|---|
| AUD-C-25 | Evidencia de (a) del analista | insumo (proceso) | P2 | El pedido cita «Curation regenerada con v2.1 idéntica byte a byte (SP/nsub/cur vs EXP/propuestas/curation…)». Esa comparación **no es vacía**: difiere en 6 archivos por la deriva del generador (§8.2). Lo que sí es vacío es `SP/nsub/cur-old` (v2 aprobado) frente a `SP/nsub/cur` (v2.1), ambos con 1.9.14, que es la comparación correcta para (a) | En la `justificacion` de la aprobación §3d-bis, citar como evidencia de (a) la comparación con el mismo generador (`cur-old` ↔ `cur`, o `SP/aud-3dbis/out-v2` ↔ `SP/deriva-cur`), no la comparación contra `propuestas/` |
| AUD-C-26 | ART-030 v2.1 `:26-28`, `:123-125`, leyenda `:17` | insumo | P2 | (i) El `[PM]` «para Colombia» es un marcador nuevo **sobre la evidencia**, no sobre la decisión. La justificación de (c) y (d) debe decirlo y citar que la mitigación de RG-019 en `estado-ingesta.json` ya lo recogía. (ii) La leyenda de la cabecera (`:17`) sigue definiendo `[PM]` como «propuesta del modelo», mientras que el `[PM]` nuevo se usa como pendiente manual o verificación diferida (steering `05:48`; `references/03` §3e). Es una incoherencia previa que la v2.1 hace visible | (i) En la aprobación. (ii) Próxima versión de ART-030: leyenda de `[PM]` según steering `05` (no sustantivo) |
| AUD-C-27 | ART-031..033 (APR-019) | framework → artefacto | P2 | Deriva frente a 1.9.14 (§8.2): un cambio de código desplegable latente (HF-53 en `SISBENCurationProcessor.scala:173-176`), más comentarios, pruebas y metadatos | §3d ahora (recomendado) o condición explícita antes del fork |

### 8.4 Verificaciones

| # | Comando | Resultado |
|:-:|---|---|
| 1 | `diff SP/aprobados/source-spec-curation.yaml EXP/insumos/source-spec-curation.yaml`; `yaml.safe_load` de los dos | Solo comentarios; parseados iguales (`True`) |
| 2 | `render_templates.py --spec EXP/insumos/source-spec-curation.yaml --solo-verificar` | rc=0 · 33 campos curados, 18 eventos esperados |
| 3 | Generación con v2 aprobado → `SP/aud-3dbis/out-v2/` y con v2.1 → `SP/deriva-cur/`; `diff -r` entre ellos | **Vacío** |
| 4 | `diff -r SP/nsub/cur-old SP/nsub/cur` (del analista) | Vacío |
| 5 | `diff -r SP/deriva-cur EXP/propuestas/curation` | 6 archivos distintos (§8.2); fragmentos, `curated-events.json`, `accepted-events.json` y 4 `.scala` idénticos |
| 6 | `python3 tools/verificar-claves.py --dir EXP/insumos` | rc=0, coherentes |
| 7 | Referencia con `SISBEN_ERR_015` de nivel nulo | `['numeroDocumento:NIVEL_DESCONOCIDO', 'numeroDocumentoOriginal:NIVEL_DESCONOCIDO']` (comportamiento HF-53) |
| 8 | Lectura de `ISIdentityMatcher.scala:44-56` e `IdentityMatcher.scala:46-49` | La cita es correcta (§8.1) |

Solo modifiqué este archivo del repo. No usé git salvo para leer.
