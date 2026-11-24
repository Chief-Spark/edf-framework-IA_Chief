# HU-SISBEN — Ingesta mensual de SISBEN IV en EDF (Validation a State Calculation)

| Campo | Valor |
|---|---|
| ID | HU-SISBEN |
| Épica | EPIC-SISBEN-02 |
| Versión / fecha | 1.0.2 / 2026-11-18 |
| Cambios en 1.0.2 | §5.3: las advertencias de curación se citan con el formato de `spec-sisben.md` §10.1 (`<campo curado>:<MOTIVO>`). Sin cambios de reglas |
| Cambios en 1.0.1 | R-23: Curation publica estandarizados los campos con inconsistencia de severidad 3; nuevo escenario en §5.3. Sin cambios en Validation ni en Acceptance |
| Estado | **Definitiva.** Requerimiento completo y aprobado por el Desarrollador; no tiene preguntas abiertas |
| Especificación técnica | `spec-sisben.md` (misma carpeta). Esta HU dice **qué** se construye y **cómo se acepta**; la spec dice **cómo**, campo a campo |
| Fuentes de la ingesta | Únicamente esta HU y `spec-sisben.md`. Ningún otro documento es fuente |
| Datos | Todos los ejemplos son **sintéticos** |

## 1. Narrativa

> Yo, como **Desarrollador EDF**, quiero ingerir cada mes el archivo SISBEN IV que publica el DNP a través de las seis etapas de EDF (Validation, Acceptance, Curation, Pinning, Synthesis y State Calculation), para que la clasificación socioeconómica de cada persona (grupo y subgrupo Sisbén IV) quede disponible en la sección `#sisben` del XPM y se pueda consultar por Data Provisioning, sin almacenar información de menores de edad.

## 2. Alcance

| Dentro | Fuera |
|---|---|
| Archivo mensual del DNP, carga completa | Data Reception y Preparation (no son etapas del framework) |
| Las 6 etapas EDF, de Validation a State Calculation | Scoring de State Calculation (risk, trust, completeness) |
| Sección `#sisben` del XPM | Planos Teradata y BDM del proceso COBOL actual (`BDIASISB.CBL`) |
| Reportes de ingesta con el reporting estándar de EDF | Puntaje Sisbén: Sisbén IV no lo publica y el archivo no lo trae |
| | Archivos de reenvío (`RT`) y de eliminación (`EP`): cada archivo mensual es una carga completa |

## 3. Datos de la fuente

| Atributo | Valor |
|---|---|
| Fuente oficial | **Mixta.** SISBEN es la fuente oficial y única de la clasificación socioeconómica (grupo, subgrupo, estado). Para identidad no es oficial: está por debajo de Registraduría y Cámaras de comercio (nacionales) y de Migración (extranjeros) |
| Business Light Code | `batch` |
| Ingesta nueva | Sí: es nueva en EDF (hoy la procesa el COBOL `BDIASISB.CBL`, fuera de EDF). `reusePIN = false` |
| Proveedor (datasource) | `DNP` |
| Subscriber | `999722` |
| Event type | `30`. Junto con `datasource = DNP` y `subscriber = 999722` identifica a SISBEN sin ambigüedad frente a cualquier otra fuente que use el mismo event type |
| Frecuencia | Mensual, carga completa |
| Contrato del archivo | `spec-sisben.md` §2: `SISBEN_EXPERIAN_AAAAMM.yyyyMMddHHmmss±ZZZZ.txt`, delimitador `;`, UTF-8, 1 fila de header, sin footer, 22 columnas |
| Rutas del pipeline (solo lectura) | `spec-sisben.md` §14 |
| Estabilidad de las reglas | Las reglas de esta versión 1.0.2 son estables: ningún campo, catálogo ni obligatoriedad cambia durante la construcción. Un cambio futuro entra como versión nueva de esta HU y de la spec |

## 4. Reglas de negocio

### 4.1 Archivo

| ID | Regla |
|---|---|
| R-01 | El DNP entrega `SISBEN_EXPERIAN_AAAAMM.txt`. La recepción (Secure Transport) le agrega la marca de tiempo `.yyyyMMddHHmmss±ZZZZ` antes de Validation. Validation solo procesa archivos con la marca |
| R-02 | Solo hay un tipo de archivo: `REGULAR`, la carga completa del mes, que reemplaza todo lo anterior. Un archivo con prefijo `RT_` o `EP_` no cumple el nombre y se rechaza |
| R-03 | El archivo tiene exactamente las 22 columnas del diccionario del DNP, en el orden de la spec §3. Delimitador `;`, UTF-8 (un BOM inicial se ignora), 1 fila de header, sin footer |
| R-04 | `Cod_dpto` llega siempre con 2 dígitos y `Cod_mpio` con 5 (DDMMM) |

### 4.2 Campos

| ID | Regla |
|---|---|
| R-05 | Las fechas llegan como texto en `yyyy-MM-dd` o `dd/MM/yyyy`; ningún otro formato es válido. Esto incluye `Fec_digitacion`, que es texto con formato de fecha, no un número |
| R-06 | `Fec_digitacion` es la fecha de la encuesta; es opcional y, si viene inválida o futura, es una inconsistencia (sev. 3) |
| R-07 | `fec_actualizacion_cns` es la fecha de modificación del registro y la *As-Of-Date*; es obligatoria y no puede ser futura (sev. 1) |
| R-08 | `fec_actualizacion_persona` es opcional (sev. 3) y se toma como llega |
| R-09 | `ide_hogar`, `ide_persona` e `ide_nacional` son identificadores numéricos de hasta 20 dígitos, opcionales (sev. 3). Curation los publica como texto, sin convertirlos a número |
| R-10 | `Estado` llega con el código numérico del DNP (spec §9.6) y es obligatorio (sev. 1). Todos los estados se cargan; el estado viaja como dato |
| R-11 | `marca` llega con el código numérico del DNP (`0`, `1`, `10`, `20`), es opcional (sev. 3) y no filtra registros |
| R-12 | `Grupo` es `A`, `B`, `C` o `D` y `Nivel` es un entero de 1 a 21 (sev. 1). La coherencia entre grupo y subgrupo la garantiza la metodología del DNP y no se valida |
| R-13 | Nombre de departamento y de municipio deben corresponder a su código en DIVIPOLA, sin distinguir mayúsculas ni tildes (sev. 1); el municipio debe pertenecer al departamento (sev. 1) |
| R-14 | Nombres y apellidos: hasta 30 caracteres. Primer nombre y primer apellido son obligatorios (sev. 1). Validation no restringe caracteres; Curation los limpia (R-23) |

### 4.3 Documento de identidad y menores

| ID | Regla |
|---|---|
| R-15 | Tipos válidos: `1` a `8` y `PPT`, con la homologación Experian de la spec §9.4 |
| R-16 | Los tipos `1` (registro civil) y `2` (tarjeta de identidad) son de menores: se descartan sin error y no cuentan en ningún indicador de calidad |
| R-17 | El tipo `7` (salvoconducto) se rechaza con el código propio `SISBEN_ERR_023` hasta que exista su tipo en Experian |
| R-18 | `num_documento` es alfanumérico. Primero se quitan espacios y ceros a la izquierda, y el resultado puede tener hasta 15 caracteres. Un número formado solo por ceros se rechaza (`SISBEN_ERR_029`) |
| R-19 | Duplicados: misma clave tipo + número sin espacios ni ceros a la izquierda. Se conserva el de mayor `fec_actualizacion_cns` (si empatan, el de mayor `eventId`); los demás se marcan `OxygenDuplicate` y no se aceptan |

### 4.4 Calidad

| ID | Regla |
|---|---|
| R-20 | Solo hay dos severidades: sev. 1 rechaza el registro (RQS 0); sev. 3 es inconsistencia y no afecta el RQS. Ningún valor se reemplaza |
| R-21 | `FQS` = registros con RQS 1 / (total − registros descartados). Los duplicados cuentan con su RQS |
| R-22 | El archivo se acepta si `FQS ≥ 0.90` (umbral parametrizable); si no, se rechaza completo |

### 4.5 Etapas posteriores

| ID | Regla |
|---|---|
| R-23 | Curation estandariza nombres (mayúsculas, sin tildes ni diéresis, conserva `Ñ`, signos a espacio, espacios colapsados), homologa el tipo de documento y convierte las fechas a epoch UTC en milisegundos, tomando el inicio del día en `America/Bogota`. Un campo con inconsistencia de severidad 3 se publica estandarizado; si no se puede transformar, sale nulo y se anota en las advertencias de curación. Ninguna clave se omite por severidad 3 (`spec-sisben.md` §10.4) |
| R-24 | Pinning usa el matcher compartido `ColombiaIdentityMatcher`, sin bandera de fuente: prioridad 1 `standardizedIdType` + `standardizedIdNumber`; prioridad 2 `standardizedIdName` (`Nom1 Nom2 Ape1 Ape2`). `reusePIN = false` |
| R-25 | SISBEN pasa por Synthesis con el comportamiento por defecto del motor: gana el valor del evento más reciente |
| R-26 | State Calculation genera la sección `#sisben` solo para los PIN presentes en el archivo vigente |
| R-27 | Los reportes de ingesta se construyen con el reporting estándar de EDF |

### 4.6 Datos personales

| ID | Regla |
|---|---|
| R-28 | Son datos personales (PII) nombres, apellidos, tipo y número de documento, `ide_persona` e `ide_nacional`, aunque el DNP los publique. Son públicos los demás campos |

## 5. Criterios de aceptación

### 5.1 Validation

```gherkin
Feature: Stage 1 — Validation del archivo SISBEN IV

  Background:
    Given la spec "spec-sisben.md" §2 a §9
    And el event type 30, datasource DNP y subscriber 999722

  Scenario: Archivo con nombre válido se procesa
    Given el archivo "SISBEN_EXPERIAN_202601.20260205093000-0500.txt"
    When Validation lee el nombre
    Then el periodo es 202601, la fecha de recepción es 2026-02-05 09:30:00 -05:00 y el tipo es REGULAR

  Scenario: Archivo sin marca de tiempo se rechaza
    Given el archivo "SISBEN_EXPERIAN_202601.txt"
    When Validation lee el nombre
    Then el archivo se rechaza con SISBEN_ERR_024 y no se lee ningún registro

  Scenario: Periodo futuro o recepción anterior al periodo se rechaza
    Given un archivo cuyo periodo AAAAMM es posterior al mes actual, o cuya fecha de recepción es anterior al primer día del periodo o posterior a hoy
    Then el archivo se rechaza con SISBEN_ERR_024

  Scenario: Header que no corresponde se rechaza
    Given un archivo cuyo header no tiene los 22 nombres de la spec §3, en orden
    Then el archivo se rechaza con SISBEN_ERR_025

  Scenario: Fila con un número de columnas distinto de 22
    Given una fila con 10 o con 23 columnas
    Then el registro tiene error SISBEN_ERR_026 (sev. 1)

  Scenario Outline: Menores de edad se filtran en silencio
    Given un registro con tip_documento "<tipo>"
    Then el registro se descarta sin error, no cuenta en el RQS ni en el FQS
    And el log incrementa "REGISTROS MENORES DE EDAD"
    Examples:
      | tipo |
      | 1    |
      | 2    |

  Scenario Outline: Tipos de documento adultos válidos
    Given un registro con tip_documento "<tipo>" y el resto válido
    Then el registro no tiene errores
    Examples:
      | tipo |
      | 3    |
      | 4    |
      | 5    |
      | 6    |
      | 8    |
      | PPT  |

  Scenario: Salvoconducto pendiente de creación
    Given un registro con tip_documento "7"
    Then el registro tiene error SISBEN_ERR_023 (sev. 1) con causa TIPO_PENDIENTE_CREACION

  Scenario: Tipo de documento inexistente
    Given un registro con tip_documento "9"
    Then el registro tiene error SISBEN_ERR_014 (sev. 1)

  Scenario: Número de documento en ceros
    Given un registro con num_documento "0000"
    Then el registro tiene error SISBEN_ERR_029 (sev. 1)

  Scenario: Número de documento alfanumérico
    Given un registro con tip_documento "6" y num_documento "PA1000106"
    Then el registro no tiene errores

  Scenario: Municipio de otro departamento
    Given un registro con Cod_dpto "11" y Cod_mpio "05001"
    Then el registro tiene error SISBEN_ERR_028 (sev. 1)

  Scenario: Nombre de departamento que no corresponde al código
    Given un registro con Cod_dpto "05" y nom_dpto "BOLIVAR"
    Then el registro tiene error SISBEN_ERR_004 (sev. 1)

  Scenario: Grupo y subgrupo fuera de dominio
    Given un registro con Grupo "Z", o con Nivel "22"
    Then el registro tiene error SISBEN_ERR_016 o SISBEN_ERR_017 (sev. 1)

  Scenario: Estado textual
    Given un registro con Estado "ACTIVO"
    Then el registro tiene error SISBEN_ERR_021 (sev. 1)

  Scenario: Marca fuera de dominio
    Given un registro con marca "N"
    Then el registro tiene la inconsistencia SISBEN_INC_020 (sev. 3) y RQS = 1

  Scenario: Fecha de actualización CNS vacía
    Given un registro con fec_actualizacion_cns vacía
    Then el registro tiene error SISBEN_ERR_022 (sev. 1)

  Scenario: Fecha de digitación vacía
    Given un registro con Fec_digitacion vacía y el resto válido
    Then el registro no tiene errores

  Scenario Outline: Formatos de fecha aceptados
    Given un registro con fec_actualizacion_cns "<fecha>"
    Then la fecha es válida
    Examples:
      | fecha      |
      | 2026-01-10 |
      | 10/01/2026 |

  Scenario: Nombres con tildes y Ñ
    Given un registro con pri_apellido "MUÑOZ" y pri_nombre "JOSÉ"
    Then el registro no tiene errores

  Scenario: Duplicados por tipo y número
    Given dos registros con tip_documento "3" y num_documento "1000000199" y "0001000000199"
    And fec_actualizacion_cns "01/01/2026" y "15/01/2026"
    Then el de "15/01/2026" se conserva
    And el de "01/01/2026" se marca OxygenDuplicate, mantiene RQS = 1 y no se acepta

  Scenario: RQS del registro
    Then RQS = 1 si el registro no tiene errores sev. 1, y RQS = 0 si tiene alguno
```

### 5.2 Acceptance

```gherkin
Feature: Stage 2 — Acceptance

  Scenario: Archivo aceptado
    Given 21 registros, 1 menor filtrado y 1 registro con error sev. 1
    When Acceptance calcula FQS = registros con RQS 1 / (total - filtrados) = 19 / 20 = 0.95
    Then el archivo se acepta porque 0.95 >= 0.90
    And pasan a accepted los registros sin error sev. 1 y sin marca OxygenDuplicate

  Scenario: Archivo rechazado
    Given un archivo con FQS = 0.60
    Then el archivo completo se rechaza y ningún registro avanza a Curation

  Scenario: Umbral parametrizable
    Given el umbral configurado en 0.95
    Then un archivo con FQS = 0.95 se acepta y uno con FQS = 0.94 se rechaza
```

### 5.3 Curation

```gherkin
Feature: Stage 3 — Curation (flow y stock)

  Scenario: Estandarización de nombres
    Given pri_apellido "García-López" y pri_nombre "josé"
    Then primerApellido = "GARCIA LOPEZ" y primerNombre = "JOSE"

  Scenario: Nombre completo estandarizado
    Given pri_nombre "ANA", seg_nombre "", pri_apellido "PEÑA", seg_apellido "RUIZ"
    Then standardizedIdName = "ANA PEÑA RUIZ"

  Scenario: Homologación del tipo de documento
    Given tip_documento "3"
    Then standardizedIdType = 1 y tipoDocumentoSisben = "3"

  Scenario: Número de documento estandarizado
    Given num_documento " 0001000000101"
    Then standardizedIdNumber = "1000000101" y numeroDocumentoOriginal = " 0001000000101"

  Scenario: Fechas a epoch
    Given fec_actualizacion_cns "10/01/2026"
    Then fechaActualizacionCns = 1768021200000 (2026-01-10 00:00:00 America/Bogota en ms UTC)

  Scenario: Fecha de corte y tipo de archivo
    Given el archivo "SISBEN_EXPERIAN_202601.20260205093000-0500.txt"
    Then fechaCorte = 1769835600000 (2026-01-31 00:00:00 America/Bogota) y tipoArchivo = "REGULAR"

  Scenario: Nombre que queda vacío al estandarizar
    Given pri_apellido "!!!"
    Then primerApellido es nulo, el registro sigue y advertenciasCuracion contiene "primerApellido:NO_NORMALIZABLE"

  Scenario: Campo con inconsistencia de severidad 3
    Given marca "N" con SISBEN_INC_020 y seg_apellido "Ruíz" con SISBEN_INC_011
    Then idMarca es nulo y advertenciasCuracion contiene "idMarca:FORMATO_INVALIDO"
    And segundoApellido = "RUIZ"
    And el evento curado tiene todas las claves de spec-sisben.md §10.1
```

### 5.4 Pinning

```gherkin
Feature: Stage 4 — Pinning

  Background:
    Given reusePIN = false
    And el matcher compartido de Colombia con standardizedIdType + standardizedIdNumber (prioridad 1) y standardizedIdName (prioridad 2)

  Scenario: Persona existente
    Given una combinación tipo + número que ya existe en EDF
    Then el registro recibe ese PIN

  Scenario: Persona nueva
    Given una combinación tipo + número inexistente
    Then se crea un PIN nuevo

  Scenario: Cédula no encontrada en Registraduría
    Given standardizedIdType = 1 sin coincidencia en BDIIREGI
    Then el registro queda en "PENDIENTE_VALIDACION_REGIS" y entra al proceso nocturno de compra RNEC
```

### 5.5 Synthesis

```gherkin
Feature: Stage 5 — Synthesis / Consistency

  Scenario: Valor vigente
    Given dos eventos SISBEN del mismo PIN, de los cortes 202512 y 202601
    Then los atributos sintetizados son los del corte 202601
```

### 5.6 State Calculation

```gherkin
Feature: Stage 6 — Sección #sisben del XPM

  Scenario: Sección generada
    Given un PIN presente en el archivo vigente
    Then el XPM tiene la sección "#sisben" con grupoSisben, nivelSisben, fechaEncuesta, fechaModificacion, codDepartamentoSisben y codMunicipioSisben (spec §13)

  Scenario: Persona que ya no aparece
    Given un PIN presente en 202512 y ausente en 202601
    Then el XPM de 202601 no tiene sección "#sisben" para ese PIN

  Scenario: Fecha de encuesta ausente
    Given un evento sin Fec_digitacion
    Then fechaEncuesta queda nula y la sección se genera igual

  Scenario: Consulta en Data Provisioning
    Then la sección "#sisben" está disponible en la consulta online y batch
```

## 6. Definición de terminado

| Etapa | Entregable | Prueba |
|---|---|---|
| Validation | Processor, HOCON y catálogos generados por `edf-validation` desde la spec | `SISBENValidationProcessorSpec` con el fixture de la spec, Anexo A; prevalidación de la muestra sintética con el resultado de la spec §15 |
| Acceptance | Bloque HOCON con umbral 0.90 y regla de registro | `AcceptanceExpressionsSpec`; escenarios de §5.2 |
| Curation | Processor y HOCON de Curation; contrato Curation → Pinning | Escenarios de §5.3 |
| Pinning | Configuración con `reusePIN = false` y atributos de identidad | Escenarios de §5.4 |
| Synthesis | `domaindictionary.json` sin handlers propios | Escenario de §5.5 |
| State Calculation | `statecalc-foundation` y `statecalc-calculation` con la sección `#sisben` | Escenarios de §5.6; aprobación del PO (riesgo N4) |

Evidencia de pruebas en Zephyr.
