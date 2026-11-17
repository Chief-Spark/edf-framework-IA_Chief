---
inclusion: auto
name: Lexico EDF
description: Diccionario oficial de terminos del dominio EDF/Oxygen, pipeline, etapas, artefactos, calidad e identidad. Activar cuando se nombren etapas, siglas o artefactos del pipeline EDF, o cuando haya duda sobre que significa un termino del dominio.
---

# 02 — Léxico EDF

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-09-23 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; formato según `05-convenciones.md` |
| Fuentes | Las 6 fuentes vinculantes (ver pie); decisiones H-4, H-5, H-6, H-8 del 2026-09-23 |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

Un solo significado por término en toda la iniciativa, y una lista explícita de términos que no se pueden usar sin calificar.

**Cubre:** vocabulario de plataforma, etapas, artefactos, calidad, identidad y gobierno.
**No cubre:** reglas de negocio (viven en `07-contratos-frontera.md`) ni el flujo (vive en `03-pipeline.md`). Aquí solo se nombran las cosas.

**Cómo usarlo.** Si un término falta o está marcado `[PM]`, el agente **no lo define por inferencia**: lo usa con la marca, lo registra como pendiente y lo escala. Es un hueco declarado, no una invitación a completarlo.

## 2. Tabla de equivalencias de etapas `[C]`

El framework configura **6 etapas** del pipeline, más **1 skill de análisis externo al pipeline**, más el Orquestador. (decisión H-4, 2026-09-23)

| # | Nombre canónico | Skill | Sub-grupo | Compromiso | Estado |
|---|---|---|---|---|---|
| 0 | Análisis de Requerimiento y Contexto | Skill de Análisis | Requerimientos y Orquestador | Aceptación (F2) | `[C]` |
| 1 | Validation | Skill de Validation | Ingesta y Limpieza | Aceptación (F3) | `[C]` |
| 2 | Acceptance | Skill de Acceptance | Ingesta y Limpieza | Aceptación (F3) | `[C]` |
| 3 | Curation | Skill de Curation | Ingesta y Limpieza | **Éxito** (F10) | `[C]` |
| 4 | Pinning | Skill de Pinning | Transformes | Éxito (F10) | `[C]` |
| 5 | Synthesis / Consistency | Skill de Synthesis | Transformes | Éxito (F10) | `[C]` |
| 6 | State Calculation | Skill de State Calculation | Transformes | Éxito (F10) | `[C]` |
| — | Orquestación del trabajo | Skill Orquestador | Requerimientos y Orquestador | Aceptación (F1) | `[C]` |

**La etapa 0 es externa al pipeline y al flujo de ingesta.** Es un paso de análisis previo, no una etapa de procesamiento de datos. `[C]`

**Fuera del pipeline que el framework configura** (no son etapas de este alcance): Data Reception, Preparation, Hydration, validación de cédula contra Registraduría posterior al pipeline, RT, CUPES, Relationalize, DOT. `[C]`

## 3. Plataforma y entorno

| Término | Definición canónica | Alias (✓ aceptado / ✗ prohibido) | Estado | Fuente |
|---|---|---|---|---|
| **EDF** | Experian Data Framework. El framework de datos de Experian cuya implementación es Oxygen | ✓ "framework EDF" | `[C]` | 12/tech §T5 |
| **Oxygen** | Framework de Experian que implementa EDF: motores Spark por etapa, contratos de extensión y configuración HOCON. Se distribuye como repos InnerSource más núcleo desde Artifactory | ✗ no usar como sinónimo de EDF | `[C]` | PD/tech §Frameworks |
| **XPM** | Experian Profile Map. Salida final del pipeline: el perfil consolidado de la persona. Se consulta aguas abajo vía `getXPM` / Data Provisioning | ✗ no usar como sinónimo de "salida de State Calculation" (es su consumo, no la etapa) | `[C]` | PD/tech, 12/tech |
| ↳ almacenamiento del XPM | Declarado como JSON en HBase en una fuente; el esquema de 16 campos obligatorios de la sección SISBEN se atribuye a Redshift o a HBase sin resolver | — | `[A:OQ-IL-5]` | 11/vision OQ-B-5, 12/tech |
| **Kiro** | IDE/CLI donde viven y corren el framework y todos sus skills, **en la máquina del desarrollador** | — | `[C]` | 11/tech, 12/tech §T1 |
| **Framework** | El conjunto de skills, steering, plantillas y guard-rails de esta iniciativa. Es configuración de agente: no compila, no se despliega | ✓ "Célula para Ingestas", "EXP-023" | `[C]` | 12/tech §T2 |
| **EXP-023 / KD-EXP-034** | Identificadores del proyecto. `EXP-023` = "Célula para Ingestas"; `KD-EXP-034` = "Automatización ingestas EDF" | — | `[C]` | PD/vision |
| **Skill** | Unidad de configuración de agente en Kiro (definición + steering + prompts). Puramente declarativa: sin código compilado ni componente ejecutable | ✗ no usar para referirse a la etapa del pipeline | `[C]` | 12/tech §T2 |
| **Orquestador** | Skill que coordina el trabajo del desarrollador sobre las etapas, mantiene el expediente y bloquea el avance sin aprobación. **No ejecuta el pipeline ni dispara DAGs** | ✗ no usar como sinónimo de Airflow ni de DAG | `[C]` | 12/vision |
| **InnerSource** | Modelo de distribución interna de los repos Oxygen en Experian. Un repo por etapa, orquestados por el repo de Colombia | — | `[C]` | PD/tech |
| **`buro.colombia_innersource`** | Nombre del repositorio principal del fork de Colombia. **Zona Z2: solo lectura** | ✗ `bureau.colombia_innersource` (grafía que aparece en fuentes previas, superada) | `[C]` | decisión H-6 |
| ↳ versión de Java del repo | Una fuente dice Java 17, otra Java 11. Sin resolver, y determina si el riesgo de Nashorn está latente o materializado | — | `[A:limpieza §3]` | 12/tech vs PD/tech |
| **MWAA** | Amazon Managed Workflows for Apache Airflow. Plataforma donde corren los DAGs existentes. **Solo integración: nunca se modifica** | ✓ "Airflow" | `[C]` | PD/tech, E10 |
| **DAG** | Directed Acyclic Graph. Unidad de orquestación de Airflow: un grafo de tareas con dependencias. En EDF hay 4 DAGs ya implementados que el framework no toca | ✗ no usar como sinónimo de "pipeline" ni de "Orquestador" | `[C]` | 12/tech, PD/vision E10 |
| **EMR** | Amazon EMR. Donde corren los motores Spark de las etapas (`spark-submit`, yarn cluster); empaquetable para EMR Serverless `emr-6.10.0` | — | `[C]` | PD/tech |
| **S3** | Amazon S3. Sistema de archivos del pipeline. Raíz por ambiente: `s3://<env>-<cuenta>-emr-coslabureau-data/<env>_sla_co_ebp_1/`. Cuentas: dev 167753196070 · qa 408515789114 · stg 133974715783 · pro 612452938387 | — | `[C]` | PD/tech |
| **HBase** | Almacén clave-valor de las tablas de estado por etapa, cargado por el hidratador | — | `[C]` | PD/tech |
| **Redshift** | Almacén del lado DOT y de certificación de datos. No pertenece a la capa de etapas | — | `[C]` | PD/tech |
| **Glue / KMS / SNS / SES / CloudWatch / DataSync** | Servicios AWS en allow-list: catálogo de datos, cifrado en reposo, notificaciones sin PII, logs/monitoreo, transporte de Preparation | — | `[C]` | PD/tech, 13/tech |
| **Artifactory** | JFrog Artifactory. Repositorio de dependencias desde donde se resuelve el núcleo Oxygen. Dependencia de build, no AWS | — | `[C]` | PD/tech |
| **Nashorn** | Motor JavaScript del JDK 11, atribuido a los campos `CALCULATED` y predicados `where` de State Calculation. **Eliminado en JDK 15.** Su uso en este sub-alcance no está confirmado | — | `[A:OQ-TR-2]` | PD/tech, 13/tech |
| **Data Provisioning** | Equipo y capa aguas abajo que consume el XPM ya calculado | — | `[C]` | PD/vision |
| **Ingesta 3** | Equipo dueño de las ingestas de Colombia (Julio Morales, Jose Lozano, Andrés Pérez). Revisa y aprueba el patrón generado; no construye el framework | — | `[C]` | PD/vision, 13/vision |

## 4. Términos que exigen calificación

Estos cuatro términos tienen más de un significado vigente en las fuentes. **Usarlos sin calificador está prohibido.**

| Término | Sentidos vigentes | Forma obligatoria | Estado |
|---|---|---|---|
| **Pipeline** | (a) el flujo EDF completo; (b) el subconjunto de 6 etapas que el framework configura | "pipeline EDF completo" \| "pipeline configurado por el framework" | `[C]` |
| **Ingesta** | (a) la fuente de datos; (b) el proceso de incorporarla; (c) el expediente que la documenta | "ingesta-fuente" \| "proceso de ingesta" \| "expediente de ingesta" | `[C]` |
| **Curation** | (a) la etapa 3 del pipeline; (b) el análisis de selección de reglas y catálogos | "etapa Curation" \| "análisis de curación" | `[C]` |
| **Pinning** | (a) la etapa 4 del pipeline (identidad, PIN); (b) el acto de fijar/congelar un supuesto en un análisis | "etapa Pinning" \| "fijación de supuesto" | `[C]` |

## 5. Etapas y sub-grupos

| Término | Definición canónica | Alias | Estado | Fuente |
|---|---|---|---|---|
| **Validation** | Etapa 1. Valida el archivo y cada registro contra reglas por campo, catálogos y homologaciones; calcula Quality Score y emite el reporte de inconsistencias | ✓ "Validación" | `[C]` | 11/vision, HU-003 |
| **Acceptance** | Etapa 2. Decide aceptación o rechazo a nivel de archivo contra el umbral de calidad, y emite reportes de aceptados y rechazados | ✓ "Aceptación" | `[C]` | 11/vision, HU-004 |
| **Curation** | Etapa 3. Estandariza, homologa, convierte a epoch y enriquece el evento aceptado; produce el evento curado y lo registra en el diccionario de dominio. Modos `flow` y `stock` | ✓ "Curación" | `[C]` | 11/vision, HU-005 |
| **Pinning** | Etapa 4. Resuelve identidad: asigna PIN por matching de atributos identificadores, con validación contra Registraduría. Tiene una tercera fase HC | — | `[C]` | 13/*, HU-006 |
| **Synthesis / Consistency** | Etapa 5. Reconcilia el estado de la persona en el tiempo aplicando Field Handlers por campo, y ejecuta las reglas de consistencia declarativas | ✓ "Consistency Check", "Síntesis" · ✗ no usar "Hydration" | `[C]` | 13/*, HU-007 |
| **State Calculation** | Etapa 6. Calcula y agrega el estado final al XPM por sección; certifica calidad en 4 dimensiones. Riesgo declarado MUY ALTO: el XPM es visible al cliente | ✓ "StateCalc", "Cálculo de estado" | `[C]` | 13/*, HU-008 |
| **Transformes** | Nombre interno del sub-grupo de trabajo que agrupa Pinning + Synthesis + State Calculation. Es organización del equipo, no taxonomía del pipeline | ✗ "Transformador" (término excluido, ver §9) | `[C]` | decisión (2026-09-23) |
| **Ingesta y Limpieza** | Nombre interno del sub-grupo que agrupa Validation + Acceptance + Curation | — | `[C]` | decisión H-2 |
| **Data Reception** | Recepción/transporte del archivo. Infraestructura de Cloud Engineering. **Fuera del pipeline del framework y fuera de los criterios de aceptación** | — | `[C]` | decisión H-3 |
| **Preparation** | Prevalidación y conversión de formato previa al pipeline. **Fuera del pipeline del framework y fuera de los criterios de aceptación** | ✗ no usar como sinónimo de Data Reception | `[C]` | decisión H-3 |
| **Discovery** (sub-etapa) | Sub-etapa configurable dentro de `oxygen-validation`, distinta de Data Reception. Es la parte generable de lo que antes se llamaba Data Reception | ✗ no confundir con el Discovery de descubrimiento de producto | `[C]` | PD/open-questions FINDING-09 |
| **Hydration / hidratación** | Carga automática del estado a HBase, 2-3 h después de State Calculation. **Fuera del pipeline.** Confirmado por código que **no es** Synthesis | ✗ prohibido usar como sinónimo de Synthesis | `[C]` | PD/open-questions OQ-11 |
| **Flow / Stock** | Modos de ejecución de Curation: `flow` para la carga incremental, `stock` para la histórica | — | `[C]` | PD/tech |
| **High Throughput (HT)** | Modo batch. **Es el único modo en alcance** | ✓ "batch" | `[C]` | PD/vision |
| **High Frequency (HF)** | Modo streaming/Kafka de los motores Oxygen. **Fuera de alcance** desde 2026-09-09 | — | `[C]` | PD/vision |
| **DOT** | Módulo de reportería y certificación de datos del lado Redshift, con módulos Python y configuración YAML (HU-009). Fuera del MVP | — | `[C]` | PD/tech, HU-009 |
| **RT** | Reestructuración total. Tipo de archivo que se trata como ingesta aparte. Fuera de alcance | — | `[C]` | 12/tech |
| **CUPES** | Eliminación parcial. Tipo de archivo que se trata como ingesta aparte. Fuera de alcance | — | `[C]` | 12/tech |
| **EP** | Tipo de archivo/marca manejada en Curation y relacionada con borrado. **La expansión de la sigla no consta en ninguna fuente** | — | `[PM]` | 11/vision |
| **Relationalize** | Proceso que corre por su cuenta desde el XPM. Fuera de alcance | — | `[C]` | PD/vision E2 |

## 6. Artefactos y configuración

| Término | Definición canónica | Estado | Fuente |
|---|---|---|---|
| **HOCON** | Human-Optimized Config Object Notation. Formato de toda la configuración de etapa, leído con pureconfig 0.17.3 | `[C]` | PD/tech |
| **`*-common-application.conf`** | Archivo base de configuración por etapa. **Inmutable.** Nunca se modifica | `[C]` | PD/tech, 13/tech |
| **`*-inner.conf`** | Archivo de configuración aditiva del fork de Colombia. Es donde cada ingesta agrega su bloque | `[C]` | PD/tech |
| **`env.conf`** | Configuración de ambiente (cuentas, rutas, formato de salida) | `[C]` | PD/tech |
| **`oxygen-lookup.conf`** | Declaración de tablas de lookup CSV y diccionarios JSON, resueltos por `LookupProcessor` | `[C]` | PD/tech |
| **Domain dictionary / GDD** | Diccionario de dominio de la ingesta (`colombia-domain-dictionary-inner.json`). Define los campos curados y, en Synthesis, el Field Handler de cada campo | `[C]` | 11/vision, 13/vision |
| **`foundation-stage.json`** | Familia de configuración de State Calculation (incluye `statecalcAlpha-js-foundation-stage.json`). Se extiende de forma aditiva | `[C]` | PD/tech, 13/tech |
| **`identifying-attributes-<ingesta>.json`** | Configuración de atributos identificadores de Pinning | `[C]` | 13/vision |
| **`colombia-consistency-checks-dictionary.json`** | Diccionario de reglas de consistencia declarativas de Synthesis | `[C]` | 13/vision |
| **Processor** | Clase Scala que extiende el contrato abstracto de la etapa y se conecta por FQCN en HOCON (`configs[].processor`) | `[C]` | PD/tech |
| **Field Handler** | Estrategia de reconciliación por campo en Synthesis. Tres valores confirmados: **LFP** (Latest For Period), **LAP** (Latest Across Period), **EUC** (Earliest Until Correct). Su semántica exacta no está confirmada por el equipo de dominio | `[A:OQ-TR-3]` | 13/tech |
| **`_SUCCESS` / `.markers` / `ready-curate`** | Marcadores de archivo que señalan que la salida de una etapa está completa y disponible para la siguiente | `[C]` | PD/tech, 12/tech |
| **Catálogo de homologación** | Tabla CSV de valores válidos y su equivalencia. Los propios de una ingesta se nombran `<NÚMERO>_<NOMBRE>_NH.csv` | `[C]` | PD/vision |
| **`generationId` / época EPOC** | Identificador dinámico de la generación/corrida. Nombra la carpeta de generación. **Nunca se hardcodea** | `[C]` | 13/tech, 12/tech |
| **Generación candidate / reference** | Par de generaciones que Pinning compara para decidir reutilización de PIN | `[C]` | PD/tech |
| **`ValidatedEvent` / `TaggedValidatedEvent` / `CuratedEvent` / `Payload`** | Modelo de evento que atraviesa las etapas: validado → etiquetado en Acceptance → curado, con payload de enriquecimiento | `[C]` | PD/tech |
| **SP compartido** | Objeto compartido entre ingestas que el código generado **nunca** modifica. Su naturaleza exacta (stored procedure u otro) no consta | `[PM]` | PD/vision |
| **`isSisben`** | Ejemplo canónico del patrón de flag aditivo por ingesta en State Calculation | `[C]` | HU-008 §6.1 |
| **`settings-artifactory.xml`** | Archivo de configuración de build de los repos compartidos que **contiene una credencial en texto plano**. Prohibido leerlo, copiarlo o emitirlo (`01` §4) | `[C]` | PD/open-questions HALLAZGO-22 |

## 7. Calidad, identidad y reglas

| Término | Definición canónica | Estado | Fuente |
|---|---|---|---|
| **Quality Score** | Puntaje de calidad por campo/registro que produce Validation | `[C]` | 11/vision |
| **FQS** | Puntaje de calidad del archivo que calcula Acceptance y se compara contra el umbral. Fórmula: `(total − menores − rechazados_por_severidad_bloqueante) / (total − menores)`. **La expansión de la sigla no consta** | `[PM]` (sigla) / `[C]` (fórmula) | 12/tech, 11/vision |
| **RQS** | Aparece una sola vez, junto a FQS, con umbral ≥90%. **Ni la expansión ni la definición constan en ninguna fuente** | `[PM]` | 11/vision |
| **`qualityThreshold`** | Umbral de aceptación del archivo. Valor `0.90`, **parametrizable, nunca hardcodeado** | `[C]` | PD/tech, 12/tech |
| **Rechazo silencioso de menores** | Los registros de tipos documentales 7 y 10 se rechazan sin sumar al puntaje de calidad | `[C]` | 12/tech, 11/vision |
| **Certificación de 4 dimensiones** | Certificación de calidad de State Calculation: completitud de mandatorios ≥90%, validez de tipo de dato 100%, unicidad 0% de duplicados por llave, consistencia ≥90% | `[C]` | HU-008 |
| **Reglas de consistencia** | Reglas declarativas de Synthesis, con ID `CONS-<INGESTA>-<NNN>`. Severidad de advertencia: no bloquean el procesamiento | `[C]` | HU-007 |
| **Duplicados por llave** | Registros con la misma llave documental; se conserva el más vigente | `[C]` | 12/tech |
| **Llave de identidad** | Combinación `tipo de documento + número de documento + nombre estandarizado`. Tipo y número no pueden ser nulos | `[C]` | 12/tech |
| **`identitySufficiency`** | Combinación mínima de atributos necesaria para intentar identificar a una persona en Pinning. Para SISBEN: `standardizedIdNumber`, `standardizedIdType`, `standardizedIdName` | `[C]` | HU-006 |
| **`NearNeighborKeys`** | Algoritmo de matching de identidad de Pinning | `[C]` | HU-006 |
| **BDIIREGI** | Fuente de validación binaria de identidad contra Registraduría usada por Pinning | `[C]` | HU-006 |
| **PIN** | Identificador de persona que asigna Pinning | `[C]` | 13/*, PD/tech |
| **`reusePIN`** | Bandera que decide si Pinning reutiliza el PIN de la generación de referencia. `false` para una ingesta nueva | `[C]` | 12/tech |
| **`inferDelete` / borrado inferido** | Mecanismo por el cual Pinning infiere que un registro fue eliminado. Si SISBEN lo usa o maneja solo EP está sin resolver | `[A:PT-12]` | PD/open-questions |
| **Regla EPOCH** | Toda fecha se almacena en epoch UTC como `Long` | `[C]` | 12/tech |
| **DIVIPOLA** | Codificación oficial de departamento y municipio de Colombia. Código inexistente ⇒ rechazo | `[C]` | 12/vision |
| **Catálogos 202 / 111 / 103** | Catálogos de homologación: 202 tipo de documento; 111 y 103 nombres estandarizados | `[C]` | 12/vision |
| **`event type 30`** | Código de tipo de evento del archivo SISBEN. Compartido con la fuente judicial, lo que es una ambigüedad registrada | `[A:12/vision]` | 12/vision |
| **Salvoconducto refugiado (código 7)** | Tipo documental de SISBEN que **aún no existe en el catálogo de Experian**. Debe crearse antes de producción | `[A:OQ-TR-2b]` | HU-008, 13/vision |
| **"no cross subscriber validation special"** | Regla de Pinning mencionada verbalmente y **retirada en vivo** por su autor por no estar seguro de su funcionamiento. No aparece en ninguna HU formal. **Prohibido codificarla** | `[A:OQ-TR-1]` | 13/vision |
| **Business Light Code** | Decisión inicial de la ingesta mencionada junto a la jerarquía de fuente oficial. **Sin definición en las fuentes** | `[PM]` | 12/vision |
| **Fuente oficial** | Clasificación de la fuente que determina su jerarquía frente a otras. Dato informativo y opcional de cada ingesta: no bloquea ni eleva el riesgo | `[C]` | Decisión del Desarrollador, 2026-11-17 |
| **PII** | Información personalmente identificable. SISBEN lleva identificadores directos de ~28M personas | `[C]` | PD/vision |
| **Ley 1581/2012** | Régimen colombiano de protección de datos personales aplicable. No hay marco SOC2/ISO/HIPAA/PCI en alcance | `[C]` | PD/tech |
| **Fixture sintético** | Dato de prueba fabricado, sin correspondencia con persona real. **Es el default obligatorio** | `[C]` | PD/vision C-06 |

## 8. Gobierno y alcance

| Término | Definición canónica | Estado | Fuente |
|---|---|---|---|
| **Perímetro EDF** | Restricción dura N1: el framework no toca AWS, IAM, infraestructura, redes ni seguridad. Operacionalizado como las zonas Z1/Z2/Z3 de `01` §2 | `[C]` | PD/vision N1 |
| **Human-in-the-loop** | Restricción dura N2: los agentes proponen, el humano aprueba y ejecuta. Fricción proporcional al riesgo | `[C]` | PD/vision N2 |
| **Aditividad** | Restricción de diseño vinculante: lo generado agrega, nunca renombra ni elimina | `[C]` | PD/vision |
| **Criterio de aceptación** | Compromiso exigible. Su incumplimiento es defecto | `[C]` | PD/vision |
| **Criterio de éxito** | Compromiso deseable con fallback manual documentado. Su incumplimiento **no** es defecto de garantía | `[C]` | PD/vision |
| **F8a / F8b** | F8a: especificaciones ejecutables de Validation y Acceptance, solo SISBEN — criterio de aceptación. F8b: las de cualquier otra etapa o ingesta — criterio de éxito | `[C]` | PD/open-questions OQ-02 |
| **Expediente de ingesta** | Estado persistente de una ingesta que mantiene el Orquestador: etapa actual, artefactos, aprobaciones y riesgos. JSON con esquema + `estado-ingesta.md` | `[C]` | 12/tech §T4 |
| **Bitácora** | Registro append-only en `.md` de cada interacción y cada aprobación o rechazo: qué / quién / cuándo / por qué / resultado. Obligatoria por N3 | `[C]` | 12/tech §T7 |
| **Regla de 3 componentes** | Criterio de certificación de un skill: skill configurado + prompt reproducible + artefacto que pasa la prueba funcional acordada | `[C]` | 12/tech §T8 |
| **Zephyr** | Herramienta de evidencia de pruebas vigente en Experian, exigida como entregable por las HU | `[C]` | PD/tech, 13/tech |
| **Congelamiento** | 2026-11-19: fecha a partir de la cual la especificación no cambia salvo por defecto. Entrega 2026-12-03; garantía hasta 2027-01-02 | `[C]` | PD/vision |
| **SISBEN / SISBEN V4** | Sistema de Identificación de Potenciales Beneficiarios de Programas Sociales, del DNP de Colombia. Fuente del caso piloto: ~28M registros históricos, PII | `[C]` | PD/vision, 11/vision |
| **DNP** | Departamento Nacional de Planeación de Colombia. Origen de SISBEN y destinatario de reportes | `[C]` | PD/vision |
| **IFR** | Aparece en la documentación de Confluence asociada a creación de ambientes y despliegue a producción. **Sin expansión ni definición en las fuentes de este pack** | `[PM]` | PD/tech |
| **Collibra** | Herramienta de gobierno de datos citada entre los artefactos que exigen las HU. Fuera del MVP, en tensión abierta | `[A:FINDING-08]` | PD/vision |

## 9. Términos excluidos por decisión del equipo `[C]`

Los siguientes términos **no forman parte de este léxico** por decisión del 2026-09-23 (H-8). Se registran aquí para que la exclusión sea trazable y no se reinterprete como omisión:

| Término excluido | Nota |
|---|---|
| `KIDAR` | No se define en este pack |
| `sev0` / `sev1` / `sev3` | La escala de severidad no se define aquí. Las reglas que dependen de severidad se expresan en `07-contratos-frontera.md` por su efecto, no por su código |
| `banda` (Baja / Media / Alta / Muy alta) | Vocabulario de estimación, no de dominio |
| `eje 1 / eje 2 / eje 3` | Vocabulario de medición de capacidad, no de dominio. La regla de redacción que prohíbe colapsarlos sigue vigente en `05` §9 |
| `Transformador` | **Excluido.** No existe como entidad en las fuentes. El sub-grupo de trabajo se llama **Transformes** (§5) |

## Pendientes de este documento

| # | Término o decisión | Estado |
|---|---|---|
| 1 | Expansión de `FQS` | `[PM]` |
| 2 | Expansión y definición de `RQS`, y si es distinto de FQS | `[PM]` |
| 3 | Expansión de `EP` | `[PM]` |
| 4 | Definición de `Business Light Code` | `[PM]` |
| 5 | Naturaleza exacta del `SP compartido` | `[PM]` |
| 6 | Expansión y alcance de `IFR` | `[PM]` |
| 7 | Almacenamiento del esquema XPM de 16 campos (HBase o Redshift) | `[A:OQ-IL-5]` |
| 8 | Semántica exacta de los Field Handlers LFP / LAP / EUC | `[A:OQ-TR-3]` |
| 9 | Versión de Java de `buro.colombia_innersource` y efecto sobre Nashorn | `[A:limpieza §3]` |
| 10 | Etapa 7 ausente en la numeración 0-9 de Experian | `[A:OQ-11]` |
| 11 | Nombre de los 3 campos internos que se agregan a los 22 de entrada | `[PM]` (ver `07` §3) |

## Pie de trazabilidad

Fuentes vinculantes, con la abreviatura usada en las tablas:

| Abreviatura | Archivo |
|---|---|
| PD/vision | `Product-Definition/vision-document.md` |
| PD/tech | `Product-Definition/technical-environment.md` |
| PD/open-questions | `Product-Definition/open-questions.md` |
| 11/vision, 11/tech | `EXP-023_Paquete_Tecnico_Desarrollo/11_ingesta_limpieza/{vision-document,technical-environment}.md` |
| 12/vision, 12/tech | `EXP-023_Paquete_Tecnico_Desarrollo/12_requerimientos_y_orquestador/{vision-document,technical-environment}.md` |
| 13/vision, 13/tech | `EXP-023_Paquete_Tecnico_Desarrollo/13_transformadores/{vision-document,technical-environment}.md` |
| HU-00X | `EXP-023_Paquete_Tecnico_Desarrollo/09_SISBEN_HU/HU-SISBEN-00X_*.md` (citadas indirectamente vía 11 y 13) |

- Última verificación de referencias: 2026-09-23.
- Las HU-SISBEN se citan como fuente primaria de 13/* y 11/*; no fueron leídas directamente al redactar este documento.
