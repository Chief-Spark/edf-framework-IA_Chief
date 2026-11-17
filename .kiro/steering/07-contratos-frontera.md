---
inclusion: manual
---

# 07 — Contratos de frontera

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-09-23 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; flujo en `03-pipeline.md`; nombres en `02-lexico-edf.md` |
| Inclusión | **Manual.** Se carga al generar o revisar un artefacto de etapa |
| Fuentes | Las 6 fuentes vinculantes (abreviaturas en `02` §Pie) |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

El contrato exacto de cada costura: qué entra, qué sale, en qué formato, con qué marcador, en qué ruta, quién produce y quién consume.

**Cubre:** contrato del archivo de entrada, contratos etapa-a-etapa, reglas de negocio de frontera, y las convenciones de ubicación del código generado.
**No cubre:** el flujo y las dependencias de alto nivel (`03-pipeline.md`), ni la definición de los términos (`02-lexico-edf.md`).

Regla de uso: **un artefacto de etapa no se genera sin leer la fila correspondiente de §4.** Si la fila está marcada `[A:<ID>]`, el artefacto no se genera (`01` §6).

## 2. Contrato del archivo de entrada — caso SISBEN `[C]`

SISBEN es el piloto. Este contrato es específico de la fuente, no genérico: para otra fuente se levanta su propio contrato con las mismas dimensiones.

| Dimensión | Valor | Estado | Fuente |
|---|---|---|---|
| Patrón de nombre | `SISBEN_EXPERIAN_AAAAMM` | `[C]` | 12/vision |
| **Delimitador** | **`;`** | `[C]` | decisión H-5 (2026-09-23) |
| Encoding | UTF-8 | `[C]` | 12/vision |
| Encabezado | Sí | `[C]` | 12/vision |
| Pie de archivo | No | `[C]` | 12/vision |
| Tipo de evento | 30 — compartido con la fuente judicial, ambigüedad registrada | `[A:12/vision]` | 12/vision |
| **Campos de entrada** | **22** | `[C]` | decisión H-5 |
| **Campos de salida** | **25** = 22 de entrada + 3 campos internos agregados por el proceso | `[C]` | decisión H-5 |
| Archivos por periodo | 1, con 3 nomenclaturas: REGULAR / RT / EP | `[C]` | PD/open-questions OQ-10 |
| Frecuencia | Mensual incremental, semestral full | `[C]` | PD/open-questions GAP-01 |
| Volumen | ~28M registros históricos; volumen medido por carga sin instrumentar | `[A:GAP-01]` | PD/vision |

**Nota de conciliación.** La cifra de 22 campos y el delimitador `;` **superan** las variantes previas del corpus (17 / 18 / 22 campos; `\|` / `;`). Toda fuente que afirme lo contrario queda `[S]`. El registro global `PT-08` permanece abierto hasta que esta decisión se comunique formalmente. `[C]`

### 2.1 Los 3 campos internos `[PM]`

Los 3 campos que el proceso agrega para llegar a 25 **no están nombrados en ninguna fuente**. Pendiente manual del equipo.

| # | Nombre | Etapa que lo agrega | Tipo | Propósito |
|---|---|---|---|---|
| 23 | `[PM]` | `[PM]` | `[PM]` | `[PM]` |
| 24 | `[PM]` | `[PM]` | `[PM]` | `[PM]` |
| 25 | `[PM]` | `[PM]` | `[PM]` | `[PM]` |

Hasta que se completen: **prohibido generar el `CuratedEvent` ni el mapeo al XPM asumiendo la identidad de estos campos.**

## 3. Contrato de campo — dimensiones obligatorias `[C]`

Todo campo, de cualquier fuente, se documenta con estas nueve dimensiones. Es el contrato que el `mapeo-campos.md` de la etapa 0 debe satisfacer (`04-reglas-analisis.md` §4).

| # | Dimensión | Nota |
|---|---|---|
| 1 | Nombre en origen y nombre curado | Ambos, siempre |
| 2 | Clasificación **público / PII** | Determina enmascaramiento y exclusión de muestras (`08`) |
| 3 | Tipo de dato | En origen y en destino |
| 4 | Regla de transformación | Incluida la **regla EPOCH**: toda fecha a epoch UTC `Long` |
| 5 | Catálogo asociado | DIVIPOLA, 202 (tipo de documento), 111 y 103 (nombres estandarizados), estado/marca |
| 6 | Obligatoriedad | Y qué pasa si falta |
| 7 | Efecto de la violación de la regla | Ver §5.1 |
| 8 | Pertenencia a la llave de identidad | `tipo doc + número doc + nombre estandarizado` |
| 9 | Field Handler de reconciliación | LFP / LAP / EUC — semántica sin confirmar `[A:OQ-TR-3]` |

## 4. Contratos etapa a etapa

Formato de salida por ambiente: `text` en QA/STG, `avro` en Producción. `[C]`

| Costura | Produce | Consume | Artefacto de salida | Ruta S3 | Marcador | Estado |
|---|---|---|---|---|---|---|
| Preparation → **Validation** | Externo al framework | Validation | Archivo preparado, delimitado por `;` | (entrada externa) | — | `[C]` frontera · `[PM]` verificación del delimitador real entregado |
| **Validation** → Acceptance | Validation | Acceptance | Registros validados + Quality Score por campo + reporte de inconsistencias | `validated/`, `reports/validation/` | `_SUCCESS` | `[C]` |
| **Acceptance** → Curation | Acceptance | Curation | Eventos aceptados; rechazados aparte; FQS del archivo; notificaciones sin PII | `accepted/` (permanece), `rejects/`, `reports/acceptance/` | `_SUCCESS` | `[C]` |
| **Curation** → Pinning | Curation | Pinning | Eventos curados en `flow` y `stock`; registro en el domain dictionary | `curated/` | `ready-curate` | `[A:OQ-IL-3]` parcial — contrato en `11-contrato-curation-pinning.md`; pendientes en su §6 |
| **Pinning** → Synthesis | Pinning | Synthesis | Eventos con PIN; fase HC aparte | `pinning/pinned/`, `pinning/pinned/{id}-pin-hc/`, `reports/pinning/` | `_SUCCESS` | `[C]` |
| **Synthesis** → State Calculation | Synthesis | State Calculation | Eventos sintetizados + resultados de consistencia | `consistency/synthesized/`, `reports/synthesis/` | `_SUCCESS` | `[C]` |
| **State Calculation** → Hydration | State Calculation | Hidratador (automático) | Estado calculado + configuración adicional de foundation | `statecalc/calculated/{id}`, `statecalc/additionalFoundation/`, `reports/statecalc/` | `_SUCCESS` | `[C]` |
| Hydration → XPM | Externo | Data Provisioning | Estado en HBase, consultable vía `getXPM` | HBase `<prefix>:states-batch` | — | `[C]` fuera de alcance |

Logs de ejecución de todas las etapas: `JobLogs/<fecha>/`. `[C]`

## 5. Reglas de negocio de frontera

### 5.1 Efecto de la violación de una regla `[C]`

Las reglas se expresan **por su efecto**, no por su código de severidad (el vocabulario de códigos está excluido del léxico, `02` §9).

| Efecto | Descripción | Suma al puntaje de calidad |
|---|---|---|
| **Rechazo del registro** | El registro no avanza | Sí |
| **Inconsistencia registrada** | Se anota y el registro avanza | No |
| **Vaciado del campo con preservación** | El campo se vacía y se crea `<campo>_original` con el valor recibido | No |

### 5.2 Validation `[C]`

- Validación estructural: encabezado, pie, nombre de archivo, archivo vacío, llaves duplicadas, validación contextual cruzada entre campos.
- Validación declarativa por campo vía DSL `validate[]` / `metrics[]`.
- Catálogo inexistente en DIVIPOLA ⇒ rechazo.
- Tipo y número de documento **no pueden ser nulos** (integran la llave de identidad).
- Formatos de entrada soportados por el motor: DELIMITED, JSON, FIXED_LENGTH, XML, CUSTOM, AVRO, PARQUET.

### 5.3 Acceptance `[C]`

- Umbral: `qualityThreshold = 0.90`, **parametrizable, nunca hardcodeado**.
- Fórmula del puntaje del archivo: `(total − menores − rechazados) / (total − menores)`.
- **Rechazo silencioso de menores**: tipos documentales 7 y 10 se rechazan sin sumar al puntaje.
- Duplicados por llave documental: se conserva el registro más vigente.
- **El rechazo es a nivel de archivo completo**, no de registro.
- Notificaciones por SNS/SES: **sin PII**.

### 5.4 Curation `[C]`

- Transformaciones: estandarización, homologación por catálogo, conversión a epoch, enriquecimiento.
- Manejo de RT y EP.
- Registro de los campos curados en el domain dictionary de la ingesta.
- Ambigüedad abierta: la regla de concatenación de nombres. **No implementar sin confirmar.** `[A:11/vision]`
- El manejo de EP se diseñó agnóstico a `inferDelete` de Pinning; posible colisión. `[A:PT-12]`

### 5.5 Pinning `[C]`

- `identitySufficiency` para SISBEN: `standardizedIdNumber`, `standardizedIdType`, `standardizedIdName`.
- Matching por `NearNeighborKeys`; validación binaria contra Registraduría (`BDIIREGI`).
- `reusePIN = false` para una ingesta nueva.
- Tres fases, la tercera es HC.
- **Prohibido codificar** la regla "no cross subscriber validation special": fue retirada en vivo por su autor y no consta en ninguna HU. `[A:OQ-TR-1]`
- Cambios a `ColombiaIdentityMatcher`, `ColombiaClusterSplitter` e `IdentityImpl`: **mínimos y aditivos**, nunca eliminar lógica ni accesos existentes.
- Tópicos Kafka (`oxygen_pinned`, `oxygen_unpinned`, `oxygen_backout`, `oxygen_discovery`): solo High Frequency. **No aplican a SISBEN.**

### 5.6 Synthesis / Consistency `[C]`

- Field Handler por campo según el domain dictionary: LFP (Latest For Period), LAP (Latest Across Period), EUC (Earliest Until Correct). **Semántica sin confirmar por el equipo de dominio** `[A:OQ-TR-3]`.
- Reglas de consistencia declarativas en diccionario JSON, con ID `CONS-<INGESTA>-<NNN>`. Para SISBEN: `CONS-SISBEN-001` (`FECMODFI >= FECENCUE`) y `CONS-SISBEN-002` (`FECENCUE <= AsOfDate`).
- Las reglas de consistencia son de advertencia: **registran la inconsistencia y no bloquean el procesamiento**.

### 5.7 State Calculation `[C]`

**Riesgo declarado MUY ALTO: el XPM es visible al cliente.** Toda generación aquí exige revisión reforzada (`06` §3).

- Agregación al XPM por sección, vía la familia `foundation-stage.json` (`statecalcAlpha-js-foundation-stage.json`) y un flag aditivo por ingesta (patrón `isSisben`).
- Para SISBEN el caso es **passthrough**: sin cálculos ni sumatorias propias. `[A:CQ-006]` — si requiere lógica propia en el enricher no está resuelto.
- **Certificación obligatoria de 4 dimensiones antes de producción:** completitud de mandatorios ≥90%, validez de tipo de dato 100%, unicidad 0% de duplicados por llave (`NUMERO_IDENTIFICACION` + `TIPO_IDENTIFICACION`), consistencia ≥90%.
- Sin PII en logs WARN/ERROR: enmascaramiento `***` obligatorio (`08`).
- Uso de JavaScript/Nashorn para campos calculados y predicados `where`: no confirmado en este sub-alcance. **No generar configuración que dependa de Nashorn sin resolverlo** `[A:OQ-TR-2]`.
- Tipo documental "Salvoconducto refugiado" (código 7): **no existe en el catálogo de Experian**; debe crearse antes del despliegue `[A:OQ-TR-2b]`.

## 6. Convención de ubicación del código generado `[C]`

Esta convención es **externa e inmutable**: pertenece al fork de Colombia y este pack no la redefine, solo la hace exigible.

| Elemento | Ubicación | Mutabilidad |
|---|---|---|
| Processor, validator, fields, metrics de la ingesta | `…/<stage>/source/<ingesta>/{fields,metrics,processor,validator}/` | Nuevo, aditivo |
| Constantes de la ingesta | `<Ingesta>Constants.scala` | Nuevo |
| Bloque de configuración de la ingesta | `env/application-conf/innersource/<stage>-common-application-inner.conf` | Aditivo |
| Archivo base de configuración | `<stage>-common-application.conf` | **INMUTABLE** |
| Catálogo propio de la ingesta | `<NÚMERO>_<NOMBRE>_NH.csv` | Nuevo |
| Domain dictionary | `colombia-domain-dictionary-inner.json` | Aditivo: sección nueva |
| Configuración de State Calculation | `foundation-stage.json` y familia | Aditivo: sección nueva |
| Clases compartidas (`Colombia*`) | Existentes | Cambio **mínimo y aditivo**, nunca eliminar |
| Módulos del repo | `modules/pinning/`, `modules/statecalculation/` (Synthesis no declara módulo) | `[PM]` módulo de Synthesis |

Conexión del processor: por nombre de clase completo (FQCN) en HOCON — `configs[].processor`, `identityMatcher`, `relationshipMigrationProcessorClassName`, `appenders[].processor`. `[C]`

Convenciones de código confirmadas: contexto inyectado por constructor; `@transient lazy val logger = getLogger(this.getClass)`; excepciones `OxygenException` y `MalformedFileException`. `[C]`

### 6.1 Antipatrones del material de referencia `[C]`

Los ejemplos `Credit*` del corpus genérico **contienen defectos conocidos**. No copiarlos:

- Logs de depuración emitidos con nivel `error`.
- Puntajes de calidad generados aleatoriamente.
- `ISIdentityMatcher` leyendo `firstName` donde debería leer `firstLastName`.

Regla: el material de referencia aporta **estructura**, no contenido. `[C]`

### 6.2 Decisión de factorización `[A:OQ-T-03]`

Si el código auxiliar común entre ingestas se duplica por repo o se factoriza en un módulo **no está decidido**. Hasta que se decida, el agente **propone las dos opciones y no elige**.

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Nombre, tipo, etapa y propósito de los 3 campos internos (§2.1) | `[PM]` |
| 2 | Contrato de entrada que Pinning espera de Curation | `[A:OQ-IL-3]` |
| 3 | Semántica de los Field Handlers LFP / LAP / EUC | `[A:OQ-TR-3]` |
| 4 | Regla de concatenación de nombres en Curation | `[A:11/vision]` |
| 5 | Si SISBEN usa `inferDelete` o solo EP | `[A:PT-12]` |
| 6 | Si State Calculation de SISBEN requiere lógica propia en el enricher | `[A:CQ-006]` |
| 7 | Uso de Nashorn en State Calculation | `[A:OQ-TR-2]` |
| 8 | Creación del tipo documental "Salvoconducto refugiado" (código 7) | `[A:OQ-TR-2b]` |
| 9 | Realidad de la regla "no cross subscriber validation special" | `[A:OQ-TR-1]` |
| 10 | Módulo del repo para Synthesis | `[PM]` |
| 11 | Duplicar vs factorizar el código auxiliar común | `[A:OQ-T-03]` |
| 12 | Listas de valores de los catálogos de SISBEN (pre-condición de F8a) | `[A:DR-03]` |
| 13 | Configuración SISBEN heredada que podría colisionar en Pinning | `[A:OQ-TR-3b]` |
| 14 | Verificación del delimitador que Preparation entrega realmente | `[PM]` |

## Pie de trazabilidad

- Contrato del archivo, campos y delimitador: decisión H-5 (2026-09-23); `12/vision` §Reglas y criterios; `PD/open-questions` OQ-10, PT-08.
- Reglas de Validation y Acceptance: `11/vision` §Feature Areas; `12/tech` §Testing; `PD/tech` §Input Validation.
- Reglas de Curation: `11/vision`, `11/tech`; `PD/tech` §Example Code.
- Reglas de Pinning, Synthesis y State Calculation: `13/tech` §Input Validation, §Architecture, §Example Code; `13/vision` §Feature Areas, §Risks; HU-SISBEN-006/007/008 citadas vía 13/*.
- Convención de ubicación y antipatrones: `PD/tech` §Project Structure, §Example Code; `13/tech` §Project Structure.
- Última verificación de referencias: 2026-09-23.
