Voy a buscar documentación específica sobre las etapas de Curation y Pinning para SISBEN en el pipeline EDF IFR.Con base en la documentación recuperada de las fuentes disponibles (CCBR — EBP-EDF, EBP-Arquitectura, Nueva Data/SCEND y Explicación Básica XPM), aquí está la especificación completa de las etapas **Curation** y **Pinning** para SISBEN V4:

---

# 🔧 Especificación de Skills — SISBEN V4
## Etapas: Curation (Stage 3) · Pinning (Stage 4)
### Pipeline EDF · High Throughput (Batch)

---

## PARTE 1 — CURATION (Stage 3)

### 1. Contexto y Rol de la Etapa

La etapa **Curation** recibe los eventos aceptados por Acceptance y tiene como responsabilidad **estandarizar, enriquecer y normalizar** todos los campos antes de que sean procesados en Pinning. Es el punto de transformación funcional del dato crudo hacia el modelo de dominio EDF Colombia.

| Responsabilidad | Descripción |
|---|---|
| **Estandarización de identidad** | Homologar `tip_documento` + estandarizar `num_documento` (stripeo de letras para PEP, guardado de original para PPT) |
| **Estandarización de nombres** | Concatenar y normalizar `pri_nombre + seg_nombre + pri_apellido + seg_apellido` |
| **Conversión de tipos de dato** | Todos los campos que llegaron como `String` desde Validation se convierten al tipo correcto (Numeric, Date, etc.) |
| **Normalización de fechas** | Convertir de `dd/mm/yyyy` a formato epoch (para XPM) |
| **Enriquecimiento de evento** | Añadir `businessLineCode`, `typeOfData`, `cutoffDate`, `subscriberCode` |
| **Preparación para Pinning** | Todos los atributos de identidad deben quedar estandarizados antes de llegar al stage de pinning |

> **Principio clave del framework Oxygen:** La estandarización de ID type, ID number e ID name es **obligatoria** en Curation para que la comparación en Pinning se haga sobre valores normalizados.

---

### 2. Lógica de Curación de Datos — SISBEN V4

#### 2.1 Módulo de Estandarización Central (Colombia Standardization)

La arquitectura del procesador Curation para Colombia opera en dos capas:

```
Extension Layer:
  └── ColombiaCurationProcessor
       ├── Colombia Standardization  ← módulo central compartido con todas las fuentes
       └── Colombia Curation         ← reglas específicas de la fuente SISBEN

Oxygen Layer:
  └── CurationProcessor (framework base)
       └── Parallel Processing
```

**El módulo `Colombia Standardization`** centraliza las reglas de estandarización por tipo de campo y aplica las mismas reglas a todos los eventos independientemente de la fuente. Para SISBEN aplica:

| Tipo de Estandarización | Campo SISBEN | Lógica |
|---|---|---|
| **ID Type Standardization** | `tip_documento` (campo fuente) → `standardizedIdType` | Homologación vía catálogo `202_SISBEN_PROCESS_ID_TYPE_HO`: SISBEN IV tipo 1 (CC)→1, tipo 3 (CE)→4, tipo 5 (PEP)→9, etc. |
| **ID Number Standardization** | `num_documento` → `standardizedIdNumber` | Para PEP (tipo 9 homologado): eliminar letras del número antes de almacenar. Para PPT: guardar valor original en campo `NUM_DOCUMENTO_SISBEN`; guardar número estandarizado en `standardizedIdNumber` |
| **ID Number Truncado** | `num_documento` corto | Colombia-specific: completar con ceros a la izquierda cuando aplique. Permite que eventos con ID truncado se pinen con los que tienen ID completo |
| **Name Standardization** | `pri_nombre`, `seg_nombre`, `pri_apellido`, `seg_apellido` | Concatenar en formato `"PriNombre SegNombre PriApellido SegApellido"` (nombres separados por espacio). Aplicar catálogos `111_NAME_STANDARDIZATION_CHARACTERS_NH` y `113_PREPOSITIONS_FOR_SPANISH_NAMES_NH` |
| **Date Standardization** | `fecha`, `fec_actualizacion_cns`, `fec_actualizacion_persona` | Convertir formato `dd/mm/yyyy` → epoch datetime. Validar separador decimal para `puntaje_sisben_3` (debe ser punto `.`) |
| **Datatype Standardization** | Todos los campos | Todos los campos llegan como `String` desde Validation; en Curation se convierten al tipo de dato definitivo (`Numeric`, `Date`, etc.) |

#### 2.2 Reglas de Negocio Específicas SISBEN en Curation

| ID Regla | Campo(s) | Descripción | Resultado |
|---|---|---|---|
| **CUR-SIS-01** | `tip_documento` + `num_documento` | Tipo PEP (homologado = 9): eliminar caracteres alfabéticos del número de documento | `standardizedIdNumber` sin letras |
| **CUR-SIS-02** | `num_documento` | Tipo PPT (homologado = 11): guardar el valor original en `NUM_DOCUMENTO_SISBEN` antes de estandarizar | Campo original preservado |
| **CUR-SIS-03** | `pri_nombre`, `seg_nombre`, `pri_apellido`, `seg_apellido` | Concatenar nombres; si `SEG_APELLIDO` o `SEG_NOMBRE` están vacíos (Sev.3 OK), omitirlos en la concatenación | `standardizedIdName` formado solo con los campos presentes |
| **CUR-SIS-04** | `Cod_dpto` + `Cod_mpio` | Normalizar cityCode a 5 dígitos: si `Cod_mpio` tiene < 3 dígitos → pad izquierdo con ceros. Luego concatenar `Depto + Munic_padded` para obtener el código de 5 dígitos | `cityCode = 5 dígitos` |
| **CUR-SIS-05** | `puntaje_sisben_3` | El separador decimal debe ser punto (`.`). Almacenar como campo Numeric con decimales | `sisbenScore = Decimal` |
| **CUR-SIS-06** | `fecha` | Convertir `dd/mm/yyyy` a epoch en formato `YYYY-MM-DD HH:MM:SS`. Verificar que no sea futura | `surveyDate = epoch` |
| **CUR-SIS-07** | `fec_actualizacion_cns` | Convertir `dd/mm/yyyy` a epoch. Resolución DNP 4743 de 2017 | `chipNumberModificationDate = epoch` |
| **CUR-SIS-08** | `businessLineCode` | Asignar el `subscriberCode` para SISBEN desde lookup `198_BUSINESS_LINE_CODE_THIRD_PARTY_SOURCES_NH` | `businessLineCode = "999722"` |
| **CUR-SIS-09** | `typeOfData` | Asignar el valor correspondiente a SISBEN desde catálogo `170_EXPERIAN_OFFICIAL_TABLE_OF_INFORMATION_SOURCES_NH` | `typeOfData = [valor catálogo]` |
| **CUR-SIS-10** | `cutoffDate` | Tomar la fecha de corte `AAAAMM` del nombre del archivo → convertir al **último segundo del último día** del mes especificado → formato epoch | `cutoffDate = epoch (último segundo del mes)` |

#### 2.3 Enriquecimiento del Evento (Event Enrichment)

Además de la estandarización, el procesador de Curation **enriquece** el evento curado con campos que no estaban en el archivo original:

| Campo Enriquecido | Fuente del Valor | Destino XPM |
|---|---|---|
| `businessLineCode` | `198_BUSINESS_LINE_CODE_THIRD_PARTY_SOURCES_NH` (lookup) | `#sisben.businessLineCode` |
| `typeOfData` | `170_EXPERIAN_OFFICIAL_TABLE_OF_INFORMATION_SOURCES_NH` (catálogo) | `#sisben.typeOfData` |
| `cutoffDate` | Nombre del archivo (`AAAAMM`) → último segundo del mes (epoch) | `#sisben.cutoffDate` |
| `counterpartyIdType` | Fijo: `2` (NIT para entidades gubernamentales) | `#sisben.counterpartyIdType` |
| `counterpartyIdNumber` | `null` (para fuentes gubernamentales/third parties) | `#sisben.counterpartyIdNumber` |
| `subscriberBlockStatus` | `subscriber.json` → campo `blockStatus` | `#sisben.subscriberBlockStatus` |
| `blockReason` | `subscriber.json` → campo `blockReason` | `#sisben.blockReason` |

---

### 3. Parámetros del Procesador Scala — Curation

#### 3.1 Clase Principal de Ejecución

```scala
// Clase ejecutora de Curation para Colombia
com.experian.edf.bureau.colombia.curation.ColombiaBureauCurationLocalApp
```

#### 3.2 Archivo de Configuración Principal

```hocon
// curation-common-application.conf
// Ubicación: env/{ambiente}/application-conf/curation/

oxygen {
  application {
    mode = "batch"                    // High Throughput
    lambda = "batch"
    storage = "text"                  // QA/STG: text | PROD: avro
  }

  stage {
    curation {
      // Input: eventos aceptados
      source = "{amb}_oxygen_accepted"

      // Output: eventos curados → carpeta HDFS
      target = "{amb}_oxygen_curated"
      error  = "{amb}_oxygen_error"

      // Carpeta marcadora para archivos ya curados (evitar re-procesos)
      alreadyCurated = "colombia-app/curation/.alreadycurated/"

      // Carpeta de archivo (eventos previos)
      archived = "colombia-app/curation/archieved/"
    }
  }

  curation {
    // Módulo de estandarización Colombia
    standardizationModule = "com.experian.edf.bureau.colombia.standardization.ColombiaStandardization"

    // Procesador específico SISBEN
    processors = [
      {
        eventType = 30                // SISBEN (EventType.SISBEN)
        processor = "com.experian.edf.bureau.colombia.curation.source.sisben.SISBENCurationProcessor"
      }
    ]
  }
}
```

#### 3.3 Parámetros de Ejecución Spark (submit.sh)

```bash
spark2-submit \
  --master yarn \
  --deploy-mode cluster \
  --driver-memory 16g \
  --executor-memory 16G \
  --num-executors 16 \
  --executor-cores 6 \
  --files "`echo $(ls -m ~/Deploy/conf/*.*)| tr \" \" \"/\"`" \
  --jars "`echo $(ls -m ~/Deploy/lib/*.jar) | tr \" \" \"/\"`" \
  --queue $queue_name \
  --class com.experian.edf.bureau.colombia.curation.ColombiaBureauCurationLocalApp \
  Deploy/lib/curation*.jar
```

> **Nota:** Curation usa **6 executor-cores** (vs 4 que usa Validation/Acceptance), reflejando la mayor complejidad computacional de las transformaciones.

#### 3.4 Estructura de Módulos Scala — Curation

```
bureau.colombia_innersource/
└── modules/
    ├── curation/
    │   └── src/main/scala/com/experian/edf/bureau/colombia/curation/
    │       └── source/sisben/
    │           ├── SISBENCurationProcessor.scala     ← Procesador principal SISBEN
    │           └── SISBENCuratedEvent.scala          ← Modelo del evento curado
    │
    └── standardization/
        └── src/main/scala/com/experian/edf/bureau/colombia/standardization/
            ├── ColombiaStandardization.scala         ← Módulo central compartido
            ├── IdNumberStandardization.scala         ← Estandarización ID number
            ├── NameStandardization.scala             ← Estandarización nombres
            └── DateStandardization.scala             ← Conversión de fechas
```

#### 3.5 Estructura de Salida HDFS — Curation

```
colombia-app/
└── curation/
    ├── .alreadycurated/     ← Marcadores para evitar re-procesos
    ├── archieved/           ← Archivo de eventos previos
    └── curated/             ← Eventos curados → input para Pinning
        └── SISBEN_EXPERIAN_AAAAMM.[timestamp].curated
```

#### 3.6 Formatos de Datos por Ambiente

| Ambiente | `curation_output` | `pinning_input` |
|---|---|---|
| QA / STG | `text` | `text` |
| Producción | `avro` | `avro` |

---

### 4. Mapeo Campo Curado → XPM Section SISBEN

El resultado de Curation es el **evento curado** que se persiste y luego se pina. Los campos clave mapeados al XPM son:

| Campo Curado (CuratedEvent) | Fuente Original | XPM Path | Tipo XPM |
|---|---|---|---|
| `standardizedIdType` | `tip_documento` → homologado | `#sisben.personIdType` | Numeric |
| `standardizedIdNumber` | `num_documento` → estandarizado | `#sisben.personIdNumber` | String |
| `standardizedIdName` | Concatenación pri_nombre + apellidos | Usado en Pinning (no directamente en `#sisben`) | String |
| `businessLineCode` | Enriquecido (lookup 198) | `#sisben.businessLineCode` | String (`"999722"`) |
| `typeOfData` | Enriquecido (catálogo 170) | `#sisben.typeOfData` | Numeric |
| `cutoffDate` | Nombre archivo (epoch) | `#sisben.cutoffDate` | Long (epoch) |
| `counterpartyIdType` | Fijo: 2 | `#sisben.counterpartyIdType` | Numeric |
| `stateCode` | `Cod_dpto` | `#sisben.stateCode` | Numeric |
| `cityCode` | `Cod_mpio` (5 dígitos) | `#sisben.cityCode` | Numeric |
| `areaCode` | `cod_clase` | `#sisben.areaCode` | Numeric |
| `zoneCode` | `cod_clase` (zona) | `#sisben.zonecode` | Numeric |
| `surveyDate` | `fecha` → epoch | `#sisben.surveyDate` | Long (epoch) |
| `chipNumberModificationDate` | `fec_actualizacion_cns` → epoch | `#sisben.chipNumberModificationDate` | Long (epoch) |
| `surveyTypingDate` | `fec_actualizacion_persona` → epoch | `#sisben.surveyTypingDate` | Long (epoch) |
| `sisbenScore` | `puntaje_sisben_3` (decimal, sep=`.`) | `#sisben.sisbenScore` | Decimal |
| `recordMark` | `marca` | `#sisben.recordMark` | Numeric |
| `recordStatus` | `Estado` | `#sisben.recordStatus` | Numeric |
| `homeId` | `ide_hogar` | `#sisben.homeId` | Numeric |
| `group` | `Grupo` | `#sisben.Group` | Char |
| `groupLevel` | `Nivel` | `#sisben.SubGroup` | Char |
| `subscriberBlockStatus` | `subscriber.json` | `#sisben.subscriberBlockStatus` | Numeric |
| `blockReason` | `subscriber.json` | `#sisben.blockReason` | Numeric |

---

### 5. Nivel de Automatización Alcanzable — Curation

| Dimensión | Evaluación | Justificación |
|---|---|---|
| **Estandarización central (ColombiaStandardization)** | ✅ **100% automatizable** | El módulo ya existe y es compartido por todas las fuentes. Solo requiere que los campos estén mapeados en `config.json` con su tipo de dato correcto |
| **Conversión de tipos de dato** | ✅ **100% automatizable** | El framework convierte de `String` al tipo definido en `SISBENCuratedEvent.scala`; solo requiere definir la clase del evento curado |
| **Estandarización ID type/number** | ✅ **100% automatizable** | El catálogo `202_SISBEN_PROCESS_ID_TYPE_HO` ya existe; la lógica de homologación es genérica del framework |
| **Manejo especial PEP/PPT** | ⚠️ **Automatizable con desarrollo** | Requiere implementar la lógica de stripeo de letras (PEP) y guardado de original (PPT) en `IdNumberStandardization.scala` — desarrollo específico |
| **Concatenación de nombres** | ✅ **80% automatizable** | El módulo `NameStandardization` ya existe para otras fuentes; requiere adaptar para el caso de campos opcionales vacíos de SISBEN |
| **Enriquecimiento (cutoffDate, businessLineCode)** | ✅ **100% automatizable** | La lógica de extracción de la fecha del nombre de archivo y lookup de `subscriberCode` es genérica del framework |
| **Conversión de fechas a epoch** | ✅ **100% automatizable** | `DateStandardization.scala` ya existe en el framework |
| **Normalización cityCode (5 dígitos)** | ⚠️ **Automatizable con regla** | Requiere configurar la regla de padding izquierdo en el procesador; es un comportamiento estándar de EDF Colombia |

**Nivel global de automatización en Curation: ~90%**
> El 10% restante corresponde al desarrollo del `SISBENCurationProcessor.scala` con las reglas específicas de PEP/PPT y la adaptación de la concatenación de nombres opcionales.

---

## PARTE 2 — PINNING (Stage 4)

### 6. Contexto y Rol de la Etapa

> **SUPERADO PARA SISBEN (RG-010, HF-72).** Esta reference es evidencia de nivel 3 y no es fuente. Lo que dice aquí de «sin síntesis (no synthesis)» y de la periodicidad `"Unique"` quedó superado por la spec de la ingesta: SISBEN **sí pasa por Synthesis** con el comportamiento por defecto del motor, LFP en todos sus campos (`spec-sisben.md` §12; HU R-25; steering `03` #11 «SISBEN: resuelto»). La periodicidad del evento la fija la spec, no esta reference. Manda siempre la spec.

La etapa **Pinning** recibe los eventos curados y asigna un **PIN (Personal Identification Number)** a cada evento, agrupando los eventos que corresponden a la misma persona bajo un identificador único. Es el corazón de la deduplicación de identidades en el bureau.

> **Principio fundamental:** Para SISBEN, al ser una carga Full Load mensual y no una fuente de crédito tradicional, el pinning tiene comportamiento **especial**: se usa el pipeline EDF pero **sin síntesis (no synthesis)**. La periodicidad es `"Unique"` — similar a inquiry footprints. En state calculation solo se populan los eventos del **archivo más reciente**.

---

### 7. Proceso de Fijación de Datos (Pinning) — SISBEN V4

#### 7.1 Mecanismo General del Framework Oxygen

El pinning opera en dos pasos dentro del pipeline:

```
PASO 1 — CLUSTERING (LSH):
  Input: Eventos curados con standardizedIdType + standardizedIdNumber + standardizedIdName
  ↓
  LSH (Locality Sensitive Hash) sobre las claves de identidad
  → Crea clusters de candidatos a ser la misma persona
  ↓
  Connected Components → vincula eventos de la misma persona entre clusters

PASO 2 — EXACT MATCH + EXTENSION RULES:
  Para cada par de eventos en el mismo cluster:
  → Aplica las reglas de la Extension Colombia (IdentityImpl)
  → Si hay match → los eventos se asignan al mismo PIN
  → Si no hay match → cada evento recibe un PIN distinto
```

#### 7.2 Reglas Específicas de Pinning para SISBEN

La fuente SISBEN tiene reglas de pinning **simplificadas** respecto a fuentes de crédito, por ser una fuente gubernamental sin concepto de "subscriber relation":

| # | Atributo de Identidad | Descripción | Regla |
|---|---|---|---|
| **PIN-SIS-01** | `standardizedIdType` | Tipo de documento homologado Experian | Match exacto obligatorio |
| **PIN-SIS-02** | `standardizedIdNumber` | Número de documento estandarizado | Match exacto obligatorio |
| **PIN-SIS-03** | GDD Subscriber | Para SISBEN = `"SISBEN"` | Eventos SISBEN se pinen juntos basados en subscriber |
| **PIN-SIS-04** | GDD Subscriber Relation | Para SISBEN = `Standardized ID Type + "_" + Standardized ID Number` | Clave de agrupación |
| **PIN-SIS-05** | `standardizedIdName` | Nombre estandarizado | Se verifica en Pinning (para comparación) pero **NO** como bloqueante en la misma fuente SISBEN — sí se usa para cross-PIN en State Calculation |
| **PIN-SIS-06** | Full ID Name Present | Para SISBEN = **YES** (el archivo incluye siempre PRI_APELLIDO y PRI_NOMBRE) | Flag habilitado para validación posterior de nombre vs fuente oficial |

#### 7.3 Configuración del Procesador Pinning

```hocon
// pinning-common-application.conf
// Ubicación: env/{ambiente}/application-conf/pinning/

oxygen {
  application {
    mode = "batch"
    lambda = "batch"
  }

  stage {
    pinning {
      // Input: eventos curados
      source = "{amb}_oxygen_curated"

      // Output: eventos con PIN asignado
      target = "{amb}_oxygen_pinned"
      error  = "{amb}_oxygen_error"

      // Carpetas HDFS
      archived = "colombia-app/pinning/archived/"
    }
  }
}
```

#### 7.4 Archivo de Atributos Identificadores — `identifying-attributes-colombia.json`

```json
// identifying-attributes-colombia.json
// Este archivo define las claves de identidad que usa el LSH y el matcher

{
  "identifyingAttributes": [
    {
      "name": "standardizedIdType",
      "required": true
    },
    {
      "name": "standardizedIdNumber",
      "required": true
    },
    {
      "name": "standardizedIdName",
      "required": false
    }
  ],
  "identitySufficiency": "{standardizedIdNumber,standardizedIdType,standardizedIdName}",
  "identityMatcher": "com.experian.edf.bureau.colombia.pin.IdentityImpl",
  "partitioner": "BY_PIN"
}
```

#### 7.5 Parámetros Spark — Pinning (Old y New)

```bash
# Old Pinning (generaciones anteriores)
spark2-submit \
  --master yarn --deploy-mode cluster \
  --driver-memory 16g --executor-memory 16G \
  --num-executors 16 --executor-cores 6 \
  --queue $queue_name \
  --class com.experian.edf.bureau.colombia.pinning.ColombiaBureauPinningLocalApp \
  Deploy/lib/pinning*.jar

# New Pinning (generación actual recomendada)
spark2-submit \
  --master yarn --deploy-mode cluster \
  --driver-memory 16g --executor-memory 16G \
  --num-executors 16 --executor-cores 6 \
  --queue $queue_name \
  --class com.experian.edf.bureau.colombia.pinning.ColombiaBureauPinningTestApp \
  Deploy/lib/pinning*.jar
```

#### 7.6 Configuraciones HBase y Near-Neighbor State Stores

```hocon
// hf-pinning-application.conf (referencia para la arquitectura de stores)

oxygen {
  stage {
    pinning {
      source = "qa_sla_co_ebp_1_oxygen_curated"
      target = "qa_sla_co_ebp_1_oxygen_pinned"
      error  = "qa_sla_co_ebp_1_oxygen_error"

      nearNeighborStateStore         = "qa_sla_co_ebp:near-neighbors-batch"
      nearNeighborRealTimeStore      = "qa_sla_co_ebp:near-neighbors-realtime"
      stampedIdentityStateStore      = "qa_sla_co_ebp:stamped-identities-batch"
      stampedIdentityRealTimeStore   = "qa_sla_co_ebp:stamped-identities-realtime"
      realTimeEventStore             = "qa_sla_co_ebp:event-store-realtime"

      paths {
        identifyingAttributesFile = ["identifying-attributes-colombia.json"]
      }

      identitySufficiency = "{standardizedIdNumber,standardizedIdType,standardizedIdName}"
      identityMatcher     = "com.experian.edf.bureau.colombia.pin.IdentityImpl"
      partitioner         = "BY_PIN"
    }
  }
}
```

#### 7.7 Scores de Threshold (Pin Score)

Los thresholds de pinning aplican para **todos los tipos de ID** del bureau, incluyendo SISBEN:

| Categoría | Rango de Score |
|---|---|
| **Partial Match** (match parcial → mismo PIN) | 0.750 – 1.000 |
| **Conflict Score** (posible conflicto) | 0.350 – 0.749 |
| **Null Score** (no match → PINs separados) | 0.000 – 0.349 |

> El algoritmo **Jaro-Winkler** se usa para el match parcial de nombres. Para SISBEN, si existe una fuente oficial (Registraduría) en el mismo PIN, se valida el nombre contra ella en State Calculation.

---

### 8. Reglas Específicas de Pinning SISBEN — Full Load

> **SUPERADO PARA SISBEN (RG-010, HF-72).** Esta reference es evidencia de nivel 3 y no es fuente. Las filas «Sin Synthesis» y «Populated in State Calculation» (en lo que suponen sin Synthesis) y lo que dice esta sección de «sin síntesis (no synthesis)» y de la periodicidad `"Unique"` quedó superado por la spec de la ingesta: SISBEN **sí pasa por Synthesis** con el comportamiento por defecto del motor, LFP en todos sus campos (`spec-sisben.md` §12; HU R-25; steering `03` #11 «SISBEN: resuelto»). La periodicidad del evento la fija la spec, no esta reference. Manda siempre la spec.

| Regla | Descripción | Impacto |
|---|---|---|
| **Full Load mensual** | El archivo completo de SISBEN reemplaza al anterior; no hay delta | En cada procesamiento batch, el pipeline regenera el estado completo para los ~28M registros |
| **Sin Synthesis** | SISBEN no pasa por el stage de Consistency Check & Event Synthesis estándar | Diseño aprobado: *"Use EDF, but no synthesis for this. Periodicity will be Unique — similar to inquiry footprints"* |
| **Populated in State Calculation** | En State Calculation, solo se populan los eventos del **archivo más reciente**. Si un individuo no está en el último archivo, su sección `#sisben` en XPM **NO se popula** | Esto implementa la lógica: si XYZ aparece en enero pero no en febrero → su sección SISBEN desaparece del XPM de febrero en adelante |
| **Grain XPM** | 1 ocurrencia por: `businessLineCode + personIDType + personIDNumber` | La clave primaria de la sección SISBEN es la combinación de estos tres campos |
| **No cross-PIN validation especial** | A diferencia de fuentes de crédito, SISBEN no requiere validación de nombre contra fuente oficial para pinning (es fuente gubernamental directa) | El pinning se hace puramente por ID Type + ID Number estandarizados |

---

### 9. Estructura de Salida HDFS — Pinning

```
colombia-app/
└── pinning/
    ├── archived/           ← Eventos del ciclo anterior
    └── pinned/             ← Eventos con PIN asignado → input para Synthesis/State Calc
        └── SISBEN_EXPERIAN_AAAAMM.[timestamp].pinned
```

---

### 10. Sección XPM SISBEN — Estado Configurado (no exportado al Avro actualmente)

> **Limitación documentada:** La sección `sisben` tiene su archivo de configuración (`statecalc-aggregate-sisben.json`) pero actualmente **NO aparece en el schema Avro** de producción. Los datos de SISBEN se integran en:
> - `bestName` → campo `nameFromSisben`
> - `bestIdentifications` → campo `personIdNumberForSisben`

| Estado | Artefacto | Descripción |
|---|---|---|
| **Configurado (no en Avro)** | `statecalc-aggregate-sisben.json` | Archivo de configuración existe; sección deshabilitada o integrada en otras |
| **Schema completo definido** | `XPM Section SISBEN` (CCBR) | Estructura JSON y schema completos documentados y aprobados |
| **Campos requeridos en schema** | 16 campos (ver tabla sección 4) | Todos los campos marcados como `required` en el JSON Schema |
| **Para SISBEN V4 nuevo** | `statecalcAlpha-aggregate-sisben.json` | **Crear** — artefacto pendiente para habilitación completa de la sección |

---

### 11. Nivel de Automatización Alcanzable — Pinning

| Dimensión | Evaluación | Justificación |
|---|---|---|
| **LSH + Connected Components** | ✅ **100% automatizable** | Es parte del framework Oxygen; no requiere código de extensión para SISBEN |
| **Identifying Attributes config** | ✅ **100% automatizable** | Archivo `identifying-attributes-colombia.json` ya existe; SISBEN usa las mismas claves de identidad estándar |
| **IdentityImpl matcher** | ✅ **100% reutilizable** | La clase `IdentityImpl` es compartida por todas las fuentes del bureau Colombia |
| **Pinning Special Rules** | ✅ **100% automatizable** | La regla SISBEN (fila #27 en Pinning Special Rules) ya está definida: `Subscriber=SISBEN`, `Subscriber Relation=Standardized ID Type_Standardized ID Number` |
| **Periodicidad Unique / sin Synthesis** | ⚠️ **Requiere configuración manual** | La decisión de "no synthesis" y periodicidad "Unique" debe validarse con el equipo Oxygen en la configuración de State Calculation |
| **Habilitación sección XPM `#sisben`** | ⚠️ **Requiere desarrollo** | Crear `statecalcAlpha-aggregate-sisben.json` o habilitar `statecalc-aggregate-sisben.json` en producción |
| **State Calculation "latest file only"** | ⚠️ **Requiere regla específica** | La lógica "solo los eventos del último archivo" debe implementarse como regla de State Calculation — diseño aprobado pero pendiente de implementación para SISBEN V4 |

**Nivel global de automatización en Pinning: ~85%**
> El 15% restante corresponde a la configuración de periodicidad "Unique", la habilitación de la sección SISBEN en el schema Avro y la regla de State Calculation de "solo archivo más reciente".

---

## Resumen de Automatización — Curation + Pinning

| Etapa | Automatización | Items Pendientes de Desarrollo |
|---|---|---|
| **Curation** | ~90% | `SISBENCurationProcessor.scala`, lógica PEP/PPT, `SISBENCuratedEvent.scala` |
| **Pinning** | ~85% | Configuración periodicidad "Unique", habilitación XPM `#sisben`, regla State Calc "latest only" |
| **Artefactos comunes** | — | `statecalcAlpha-aggregate-sisben.json` (crear), `identifying-attributes-colombia.json` (reusar existente) |

---

## Validaciones de Fuentes

| Fuente | Estado | Alcance |
|---|---|---|
| **CCBR — EDF-Curation** | ✅ Confirmado | Módulo de estandarización, lógica ID/name, arquitectura Extension/Oxygen, processador Colombia Curation |
| **CCBR — EDF-Pinning** | ✅ Confirmado | Framework LSH, identifying attributes, IdentityImpl, Pinning Special Rules fila SISBEN #27, threshold scores |
| **CCBR — EDF Configuration ENGLISH** | ✅ Confirmado | Spark submit params Curation/Pinning (old y new), rutas HDFS accepted/curated/pinned/archived, HBase near-neighbor stores |
| **CCBR — XPM Section SISBEN** | ✅ Confirmado | Schema completo de la sección `#sisben`, 19 campos, payload de ejemplo, required fields |
| **CCBR — Pipeline update per instance** | ✅ Confirmado | Configuración HBase state stores, Kafka topics pinning, `hf-pinning-application.conf` |
| **SCEND — Nueva Data SISBEN section** | ✅ Confirmado | Grain `businessLineCode+personIDType+personIDNumber`, regla "latest file only", mapeo de 25 campos al XPM |
| **SCEND — Pinning and HC** | ✅ Confirmado | Configuración JSON `identifying-attributes-colombia.json`, `domainDictionaryFile`, `identitySufficiency`, `partitioner=BY_PIN` |
| **CCBR — Pinning Rules for EDF** | ✅ Confirmado | Threshold scores (Partial/Conflict/Null), Pinning Special Rules, approvals |
| **CCBR — Release 2 Design Questions** | ✅ Confirmado | Decisión aprobada: SISBEN Full Load → EDF sin Synthesis, periodicidad "Unique" |
| **SCSEYG — Explicación Básica XPM** | ✅ Complementado | Estado actual de `statecalc-aggregate-sisben.json` (configurado pero NO en Avro), integración en bestName/bestIdentifications |
| **EITS SLA CO (SCKM)** | Complementado | Contexto operativo cubierto por CCBR/SCEND |

---

## Referencias

Source 1: EBP - EDF (Confluence) — EDF-Curation, EDF-Pinning, XPM Section SISBEN, SISBEN Data Ingestion spec, Pinning Rules for EDF, Pinning Special Rules, Release 2 Design Questions, Colombia Bureau TO-BE Process, EDF Stages & Modules
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251207388/EDF-Curation
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251202178/Curation+stage
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251207390/Pinning
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251224068/XPM+Section+SISBEN
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251223064/SISBEN-+Data+Ingestion
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251228192/Pinning+Rules+for+EDF
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251226446/Pinning+Special+Rules
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251224141/Release+2+Design+Questions+-+Data+Management
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251204813/Colombia+Bureau+-+TO-BE+Process
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251206258/EDF+stages+modules

Source 2: EBP - Arquitectura (Confluence) — EDF Configuration for Colombia ENGLISH (Spark submit, HDFS paths, HBase stores), EDF Pipeline update per instance
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251223510/EDF+Configuration+for+Colombia+ENGLISH
https://experian.atlassian.net/wiki/spaces/CCBR/pages/251223018/EDF+Pipeline+update+for+each+instance.

Source 3: Nueva Data — SCEND (Confluence) — XPM SISBEN Section (grain, regla latest-file-only, 25 campos), Pinning and HC (identifying-attributes-colombia.json, identitySufficiency, IdentityImpl, BY_PIN), XPM Structure, XPM Generation - Event Filtering
https://experian.atlassian.net/wiki/spaces/SCEND/pages/344851134/SISBEN
https://experian.atlassian.net/wiki/spaces/SCEND/pages/344850684/4.+Pinning+and+HC
https://experian.atlassian.net/wiki/spaces/SCEND/pages/344850567/Nueva+Data+XPM+-+Structure
https://experian.atlassian.net/wiki/spaces/SCEND/pages/344851039/XPM+generation+-+Event+filtering

Source 4: Explicación Básica XPM — SCSEYG (Confluence) — Estado actual de statecalc-aggregate-sisben.json, secciones exportadas al Avro, procesadores personalizados Colombia, Guard-Rail Attributes
https://experian.atlassian.net/wiki/spaces/SCSEYG/pages/346853402/Bureau+Colombia+-+Documentaci%C3%B3n+del+Pipeline+Oxygen+EDF
https://experian.atlassian.net/wiki/spaces/SCSEYG/pages/346852415/Cat%2Blogos+y+campos+XPM