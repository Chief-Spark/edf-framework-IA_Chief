# spec-SISBEN — Especificación técnica de la ingesta SISBEN IV

| Campo | Valor |
|---|---|
| Versión / fecha | 1.0.2 / 2026-11-18 |
| Cambios en 1.0.2 | Formato de las advertencias de curación: `<campo curado>:<MOTIVO>` (§10.1, §10.2 y §10.4). Sin cambios de reglas |
| Cambios en 1.0.1 | §10.4 (nuevo): Curation publica estandarizados los campos con inconsistencia de severidad 3. §10.1: `standardizedIdName` se arma con los campos ya curados. Sin cambios en Validation ni en Acceptance |
| Estado | **Definitiva.** Especificación completa y aprobada por el Desarrollador; no tiene preguntas abiertas. Las reglas de negocio están en `hu-sisben.md` §4 |
| HU | `hu-sisben.md` (misma carpeta) |
| Fuentes de la ingesta | Únicamente esta spec y `hu-sisben.md`. Ningún otro documento es fuente |
| Verificación | El Anexo A se verificó el 2026-11-17 con el generador de `edf-validation` (`render-templates.jar --solo-verificar` → `source-spec OK (22 campos, 29 códigos)`; generación completa: 18 archivos) y con `prevalidar_archivo.py` sobre la muestra sintética (§15) |
| Datos | Todos los ejemplos y filas de catálogo de esta spec son **sintéticos**, salvo los códigos DIVIPOLA, que son los reales del DANE |

## 1. Identidad

| Atributo | Valor |
|---|---|
| Id de la ingesta | `sisben` |
| Fuente (clase / paquete) | `SISBEN` / `sisben` |
| Proveedor (datasource) | `DNP` |
| Subscriber | `999722` |
| Event type | `30` |
| Zona horaria | `America/Bogota` |
| Frecuencia | Mensual. Carga completa: cada archivo `REGULAR` reemplaza todo lo anterior |
| Volumen de referencia | ~28 millones de registros por archivo |
| Fuente oficial | `mixto` (oficial para clasificación socioeconómica; no oficial para identidad) |
| Business Light Code | `batch` |
| Ingesta nueva / reusePIN | Sí / `false` |
| Llegada | Secure Transport: zip en `1069731964/900422614/otrasestructuras/SISBEN`; extraído en `/trabajo/stotrasestructuras` (servidor `172.24.2.8`). La recepción agrega la marca de tiempo al nombre |

## 2. Contrato del archivo

| Dimensión | Valor |
|---|---|
| Nombre | `SISBEN_EXPERIAN_AAAAMM.yyyyMMddHHmmss±ZZZZ.txt` (extensión `txt` o `TXT`) |
| Regex del nombre | `()SISBEN_EXPERIAN_(\d{6})\.(\d{14})([+-]\d{4})\.(txt\|TXT)` — grupos: 1 prefijo (siempre vacío; lo exige el generador), 2 periodo, 3 marca de tiempo, 4 offset |
| `filePattern` (HOCON, compara la ruta completa) | `.*SISBEN_EXPERIAN_\d{6}\.\d{14}[+-]\d{4}\.(txt\|TXT)` |
| Tipo de archivo | Siempre `REGULAR` (carga completa). Los archivos `RT` y `EP` están fuera del alcance de esta ingesta; un nombre con prefijo `RT_` o `EP_` se rechaza por RA-01 |
| Fecha de corte (`fileAsOfDate`) | Último día del periodo `AAAAMM` |
| Fecha de recepción | La marca de tiempo del nombre con su offset |
| Delimitador | `;` |
| Encoding | UTF-8 (un BOM inicial se ignora) |
| Header / footer | 1 fila de header / sin footer |
| Columnas | 22 por fila |
| Líneas vacías | Se ignoran |

Reglas de archivo (si una falla, el archivo se rechaza y no se lee ningún registro):

| ID | Regla | Código |
|---|---|---|
| RA-01 | El nombre cumple la regex | `SISBEN_ERR_024` |
| RA-02 | `AAAAMM` es un mes válido y ≤ mes actual | `SISBEN_ERR_024` |
| RA-03 | Fecha de recepción ≤ ahora | `SISBEN_ERR_024` |
| RA-04 | Fecha de recepción ≥ primer día del periodo | `SISBEN_ERR_024` |
| RA-05 | El header tiene los 22 nombres de §3 en ese orden (sin distinguir mayúsculas ni tildes) | `SISBEN_ERR_025` |

Header exacto:

```
ide_ficha_origen;ide_hogar;Cod_dpto;nom_dpto;Cod_mpio;nom_mpio;cod_clase;ide_persona;ide_nacional;pri_apellido;seg_apellido;pri_nombre;seg_nombre;tip_documento;num_documento;Grupo;Nivel;Fec_digitacion;fec_actualizacion_persona;marca;Estado;fec_actualizacion_cns
```

## 3. Layout y validación por campo

Reglas comunes: se quitan los espacios a los lados antes de validar. Un campo opcional vacío no se valida. Orden de evaluación: vacío → tipo/formato → longitud → rango → dominio → no futura → catálogo; un solo código por campo. **Sev. 1** = error, el registro se rechaza (RQS 0). **Sev. 3** = inconsistencia, el registro sigue (RQS no cambia).

| Pos. | Columna | Nombre EDF | Tipo | Long. máx. | Oblig. | Regla | Catálogo | Sev. | Código |
|:-:|---|---|---|:-:|:-:|---|---|:-:|---|
| 1 | `ide_ficha_origen` | `ID_FICHA_ORIGEN` | numérico | 20 | No | Solo dígitos; puede empezar por 0 | — | 3 | `SISBEN_INC_001` |
| 2 | `ide_hogar` | `ID_HOGAR` | numérico | 20 | No | Solo dígitos (identificador) | — | 3 | `SISBEN_INC_002` |
| 3 | `Cod_dpto` | `COD_DPTO` | numérico | 2 | Sí | Existe en el catálogo | `102_…` (`cod_dpto`) | 1 | `SISBEN_ERR_003` |
| 4 | `nom_dpto` | `NOM_DPTO` | alfanumérico | 60 | Sí | Igual al nombre del catálogo para `Cod_dpto` (RV-SIS-01) | `102_…` | 1 | `SISBEN_ERR_004` |
| 5 | `Cod_mpio` | `COD_MPIO` | numérico | 5 | Sí | Existe en el catálogo (5 dígitos DDMMM) | `2_…` (`cod_mpio`) | 1 | `SISBEN_ERR_005` |
| 6 | `nom_mpio` | `NOM_MPIO` | alfanumérico | 60 | Sí | Igual al nombre del catálogo para `Cod_mpio` (RV-SIS-02) | `2_…` | 1 | `SISBEN_ERR_006` |
| 7 | `cod_clase` | `ID_CLASE` | numérico | 1 | Sí | `1`, `2`, `3` | `201_…` (`cod_clase`) | 1 | `SISBEN_ERR_007` |
| 8 | `ide_persona` | `ID_PERSONA_ORDEN` | numérico | 20 | No | Solo dígitos (identificador) | — | 3 | `SISBEN_INC_008` |
| 9 | `ide_nacional` | `ID_NACIONAL` | numérico | 20 | No | Solo dígitos | — | 3 | `SISBEN_INC_009` |
| 10 | `pri_apellido` | `PRI_APELLIDO` | alfanumérico | 30 | Sí | No vacío. Validation no restringe caracteres (§3.1) | — | 1 | `SISBEN_ERR_010` |
| 11 | `seg_apellido` | `SEG_APELLIDO` | alfanumérico | 30 | No | — | — | 3 | `SISBEN_INC_011` |
| 12 | `pri_nombre` | `PRI_NOMBRE` | alfanumérico | 30 | Sí | No vacío. Validation no restringe caracteres (§3.1) | — | 1 | `SISBEN_ERR_012` |
| 13 | `seg_nombre` | `SEG_NOMBRE` | alfanumérico | 30 | No | — | — | 3 | `SISBEN_INC_013` |
| 14 | `tip_documento` | `TIP_DOCUMENTO` | alfanumérico | 3 | Sí | `1`–`8` o `PPT`. `1` y `2` se filtran (§5). `7` tiene código propio (RV-SIS-04) | `202_…` (`cod_sisben`) | 1 | `SISBEN_ERR_014` |
| 15 | `num_documento` | `NUM_DOCUMENTO` | alfanumérico | 15 | Sí | Primero se quitan espacios y ceros a la izquierda; la longitud máxima de 15 se mide sobre ese valor normalizado. En ceros → RV-SIS-05 | — | 1 | `SISBEN_ERR_015` |
| 16 | `Grupo` | `ID_GRUPO` | alfanumérico | 1 | Sí | `A`, `B`, `C`, `D` | `234_…` (`grupo`) | 1 | `SISBEN_ERR_016` |
| 17 | `Nivel` | `ID_NIVEL` | numérico | 2 | Sí | Entero 1–21 | — | 1 | `SISBEN_ERR_017` |
| 18 | `Fec_digitacion` | `FECHA_DIGITACION` | fecha | — | No | `yyyy-MM-dd` o `dd/MM/yyyy`; no futura | — | 3 | `SISBEN_INC_018` |
| 19 | `fec_actualizacion_persona` | `FECHA_ACTUALIZACION_PERSONA` | fecha | — | No | `yyyy-MM-dd` o `dd/MM/yyyy` | — | 3 | `SISBEN_INC_019` |
| 20 | `marca` | `ID_MARCA` | numérico | — | No | `0`, `1`, `10`, `20` | `203_…` (`codigo`) | 3 | `SISBEN_INC_020` |
| 21 | `Estado` | `ID_ESTADO` | numérico | — | Sí | `0`, `10`, `40`, `41`, `42`, `43`, `60`, `61`, `69`, `70`, `90`, `91` | `203_…` (`codigo`) | 1 | `SISBEN_ERR_021` |
| 22 | `fec_actualizacion_cns` | `FECHA_ACTUALIZACION_CNS` | fecha | — | Sí | `yyyy-MM-dd` o `dd/MM/yyyy`; no futura. Es la *As-Of-Date* | — | 1 | `SISBEN_ERR_022` |

Guard-rails de Validation: `Subscriber = 999722`, `Datasource = DNP`, `subscriberRelation = NUM_DOCUMENTO` (Curation lo reemplaza, §10.1), `asOfDate = FECHA_ACTUALIZACION_CNS` (epoch ms del inicio del día en Bogotá), `receivedDate` = marca de tiempo del nombre.

### 3.1 Caracteres en nombres y apellidos

Validation **no** valida el juego de caracteres de nombres y apellidos: solo exige que el primer nombre y el primer apellido no vengan vacíos y que ningún nombre supere 30 caracteres. La limpieza de caracteres la hace Curation (§10.2).

## 4. Reglas entre campos

| ID | Regla | Sev. | Código |
|---|---|:-:|---|
| RV-SIS-01 | `nom_dpto` = `nom_dpto` del catálogo `102_…` para `Cod_dpto` (sin distinguir mayúsculas ni tildes) | 1 | `SISBEN_ERR_004` |
| RV-SIS-02 | `nom_mpio` = `nom_mpio` del catálogo `2_…` para `Cod_mpio` | 1 | `SISBEN_ERR_006` |
| RV-SIS-03 | `Cod_dpto` = `cod_dpto` del catálogo `2_…` para `Cod_mpio` (el municipio pertenece al departamento) | 1 | `SISBEN_ERR_028` |
| RV-SIS-04 | `tip_documento = 7` → causa `TIPO_PENDIENTE_CREACION` | 1 | `SISBEN_ERR_023` |
| RV-SIS-05 | `num_documento` formado solo por ceros (`0` a `000000000000000`) → causa `DOCUMENTO_CERO` | 1 | `SISBEN_ERR_029` |

Una regla entre campos no se evalúa si alguno de sus campos ya tiene error.

## 5. Filtros silenciosos

Los registros filtrados no generan error, no se emiten y **no cuentan** en RQS ni en FQS.

| ID | Condición | Conteo en el log |
|---|---|---|
| RF-01 | Línea vacía o solo espacios | — |
| RF-02 | `tip_documento` = `1` (registro civil) o `2` (tarjeta de identidad): menores | `REGISTROS MENORES DE EDAD` |

## 6. Duplicados

- Clave: `tip_documento` + `num_documento` sin espacios ni ceros a la izquierda. Se evalúa después de los filtros.
- Se conserva el registro con mayor `fec_actualizacion_cns`; si empatan, el de mayor `eventId` (en la prevalidación local, la última línea).
- Los demás reciben `ObjectError("OxygenDuplicateError", "OxygenDuplicate", "", "")`: RQS no cambia y el registro no se acepta.
- Un registro sin tipo o sin número no se agrupa con otros.
- Motor: `deDupVersion = EXTN`; `duplicateThreshold` sin configurar (por defecto).

## 7. Calidad y aceptación

| ID | Regla |
|---|---|
| RQ-01 | `RQS = 1.0` si el registro no tiene errores sev. 1; `0.0` si tiene alguno |
| RQ-02 | `FQS = average("qs.RQS")` sobre los registros no filtrados (= registros con RQS 1 / (total − filtrados)) |
| RQ-03 | Archivo aceptado si `FQS ≥ 0.90`. Umbral parametrizable. Si se rechaza, ningún registro avanza |
| RQ-04 | Registro aceptado: `errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate")` |
| RQ-05 | Marcadores de archivo (`fileName`, `header`): `errorlevelNotIn("1")`; un marcador rechaza el archivo |

Métricas de archivo: `AverageRQS = average("qs.RQS")`, `TotalRegistros = count(*)`.

## 8. Códigos de error

Todos miden 14 caracteres y ninguno es subcadena de otro.

| Código | Sev. | Uso |
|---|:-:|---|
| `SISBEN_INC_001`, `SISBEN_INC_002` | 3 | `ide_ficha_origen`, `ide_hogar` |
| `SISBEN_ERR_003` … `SISBEN_ERR_007` | 1 | `Cod_dpto`, `nom_dpto`, `Cod_mpio`, `nom_mpio`, `cod_clase` |
| `SISBEN_INC_008`, `SISBEN_INC_009` | 3 | `ide_persona`, `ide_nacional` |
| `SISBEN_ERR_010`, `SISBEN_ERR_012` | 1 | `pri_apellido`, `pri_nombre` |
| `SISBEN_INC_011`, `SISBEN_INC_013` | 3 | `seg_apellido`, `seg_nombre` |
| `SISBEN_ERR_014`, `SISBEN_ERR_015` | 1 | `tip_documento`, `num_documento` |
| `SISBEN_ERR_016`, `SISBEN_ERR_017` | 1 | `Grupo`, `Nivel` |
| `SISBEN_INC_018`, `SISBEN_INC_019` | 3 | `Fec_digitacion`, `fec_actualizacion_persona` |
| `SISBEN_INC_020` | 3 | `marca` |
| `SISBEN_ERR_021`, `SISBEN_ERR_022` | 1 | `Estado`, `fec_actualizacion_cns` |
| `SISBEN_ERR_023` | 1 | Tipo `7` pendiente de creación |
| `SISBEN_ERR_024` | 1 | Nombre de archivo inválido |
| `SISBEN_ERR_025` | 1 | Header inválido |
| `SISBEN_ERR_026` | 1 | Fila con ≠ 22 columnas |
| `SISBEN_ERR_027` | 1 | Error interno al validar un registro |
| `SISBEN_ERR_028` | 1 | Municipio de otro departamento |
| `SISBEN_ERR_029` | 1 | Número de documento en ceros |
| `OxygenDuplicate` | — | Marca de duplicado del motor |

## 9. Catálogos

Seis catálogos, delimitador `;`, con header. En producción se cargan desde Collibra a S3 con estos nombres y columnas. Las filas de abajo son las que usan las pruebas.

### 9.1 `102_DANE_DIVIPOLA_STATES_CODES_NH` — departamentos

Columnas `cod_dpto;nom_dpto`, llave `cod_dpto`. Producción: los 33 departamentos DANE, nombres en mayúsculas como los escribe el DNP (sin tildes; Bogotá como `BOGOTA D.C.`).

| cod_dpto | nom_dpto |
|:-:|---|
| 05 | ANTIOQUIA |
| 08 | ATLANTICO |
| 11 | BOGOTA D.C. |
| 13 | BOLIVAR |
| 68 | SANTANDER |
| 76 | VALLE DEL CAUCA |

### 9.2 `2_DANE_DIVIPOLA_CITIES_CODES_NH` — municipios

Columnas `cod_mpio;nom_mpio;cod_dpto`, llave `cod_mpio`. Producción: todos los municipios DANE, mismo criterio de nombres.

| cod_mpio | nom_mpio | cod_dpto |
|:-:|---|:-:|
| 05001 | MEDELLIN | 05 |
| 08001 | BARRANQUILLA | 08 |
| 11001 | BOGOTA D.C. | 11 |
| 13001 | CARTAGENA | 13 |
| 68001 | BUCARAMANGA | 68 |
| 76001 | CALI | 76 |

### 9.3 `201_SISBEN_ZONES_NH` — clase

Columnas `cod_clase;descripcion`, llave `cod_clase`: `1` CABECERA · `2` CENTRO POBLADO · `3` RURAL DISPERSO. Contenido completo.

### 9.4 `202_SISBEN_PROCESS_ID_TYPE_HO` — tipo de documento y homologación

Columnas `cod_sisben;cod_experian;descripcion`, llave `cod_sisben`. Contenido completo.

| cod_sisben | cod_experian | descripcion | Tratamiento |
|:-:|:-:|---|---|
| 1 | 10 | REGISTRO CIVIL | Filtrado (menor) |
| 2 | 7 | TARJETA DE IDENTIDAD | Filtrado (menor) |
| 3 | 1 | CEDULA DE CIUDADANIA | Válido |
| 4 | 4 | CEDULA DE EXTRANJERIA | Válido |
| 5 | 8 | DNI PAIS DE ORIGEN | Válido |
| 6 | 5 | PASAPORTE | Válido |
| 7 | (vacío) | SALVOCONDUCTO PARA REFUGIADO | Error `SISBEN_ERR_023` |
| 8 | 9 | PERMISO ESPECIAL DE PERMANENCIA | Válido |
| PPT | 11 | PERMISO DE PROTECCION TEMPORAL | Válido |

### 9.5 `234_SISBEN_GROUP_SUBGROUP_NH` — grupos

Columnas `grupo;nivel_max;descripcion`, llave `grupo`: `A;5;POBREZA EXTREMA` · `B;7;POBREZA MODERADA` · `C;18;VULNERABLE` · `D;21;NO POBRE NO VULNERABLE`. Contenido completo.

### 9.6 `203_SISBEN_RECORD_STATUS_AND_MARK_NH` — estado y marca

Columnas `codigo;tipo;descripcion`, llave `codigo`. `tipo` dice si el código aplica a `ESTADO`, `MARCA` o `AMBOS`; el dominio de cada campo (§3) restringe cuáles acepta. Contenido completo.

| codigo | tipo | descripcion |
|:-:|---|---|
| 0 | AMBOS | REGISTRO VALIDO |
| 1 | MARCA | EXCLUIDO DE PUBLICACION |
| 10 | AMBOS | ESTADO: EXCLUIDO FALLECIDO · MARCA: EN VERIFICACION |
| 20 | MARCA | VERIFICACION POR DISTANCIA |
| 40 | ESTADO | VERIFICACION DESACTUALIZACION SECCION E SALUD Y FECUNDIDAD |
| 41 | ESTADO | VERIFICACION DESACTUALIZACION SECCION G NIVEL EDUCATIVO |
| 42 | ESTADO | VERIFICACION DESACTUALIZACION SECCION G FONDO DE PENSIONES |
| 43 | ESTADO | VERIFICACION DESACTUALIZACION SECCION H OCUPACION E INGRESOS |
| 60 | ESTADO | CALIDAD ENCUESTA DATOS NO VALIDOS |
| 61 | ESTADO | CALIDAD ENCUESTA PARENTESCOS INCONSISTENTES |
| 69 | ESTADO | VERIFICACION POR DISTANCIA MAYOR A 600 M |
| 70 | ESTADO | CALIDAD ENCUESTA DOCUMENTO NO VALIDO |
| 90 | ESTADO | DESMEJORAMIENTO EN VARIABLES DE VIVIENDA |
| 91 | ESTADO | DESMEJORAMIENTO EN VARIABLES DE NIVEL EDUCATIVO |

## 10. Curation

Un solo processor sirve a los modos flow y stock.

### 10.1 Campos curados (los nombres son definitivos: Pinning, Synthesis y State Calculation los citan literalmente)

| Origen | Campo curado | Tipo de salida | Transformación | Público / PII |
|---|---|---|---|---|
| `ide_ficha_origen` | `idFichaOrigen` | texto | Ninguna (conserva el cero inicial) | Público |
| `ide_hogar` | `idHogar` | texto | Ninguna (identificador de hasta 20 dígitos; no cabe siempre en un entero de 64 bits) | Público |
| `Cod_dpto` | `codDepartamentoSisben` | texto | Ninguna | Público |
| `nom_dpto` | `nomDepartamento` | texto | Estandarización de nombre (§10.2) | Público |
| `Cod_mpio` | `codMunicipioSisben` | texto | Ninguna | Público |
| `nom_mpio` | `nomMunicipio` | texto | Estandarización de nombre | Público |
| `cod_clase` | `idClase` | entero | A número | Público |
| `ide_persona` | `idPersonaOrden` | texto | Ninguna (identificador de hasta 20 dígitos) | **PII** |
| `ide_nacional` | `idNacional` | texto | Ninguna (identificador de hasta 20 dígitos) | **PII** |
| `pri_apellido` | `primerApellido` | texto | Estandarización de nombre | **PII** |
| `seg_apellido` | `segundoApellido` | texto | Estandarización de nombre | **PII** |
| `pri_nombre` | `primerNombre` | texto | Estandarización de nombre | **PII** |
| `seg_nombre` | `segundoNombre` | texto | Estandarización de nombre | **PII** |
| `tip_documento` | `tipoDocumento` | entero | Homologación con `202_…` (`cod_experian`) | **PII** |
| `tip_documento` | `tipoDocumentoSisben` | texto | Valor original | **PII** |
| `num_documento` | `numeroDocumento` | texto | Mayúsculas, sin espacios, sin ceros a la izquierda | **PII** |
| `num_documento` | `numeroDocumentoOriginal` | texto | Valor original | **PII** |
| `Grupo` | `grupoSisben` | texto | Ninguna | Público |
| `Nivel` | `nivelSisben` | entero | A número | Público |
| `Fec_digitacion` | `fechaDigitacion` | largo | Epoch ms UTC del inicio del día en Bogotá; nulo si viene vacía o inválida | Público |
| `fec_actualizacion_persona` | `fechaActualizacionPersona` | largo | Epoch ms; nulo si viene vacía o inválida | Público |
| `marca` | `idMarca` | entero | A número | Público |
| `Estado` | `idEstado` | entero | A número | Público |
| `fec_actualizacion_cns` | `fechaActualizacionCns` | largo | Epoch ms | Público |
| (nombre del archivo) | `fechaCorte` | largo | Epoch ms del último día del periodo | Público |
| (nombre del archivo) | `tipoArchivo` | texto | Constante `REGULAR` | Público |
| — | `standardizedIdType` | entero | = `tipoDocumento` | **PII** |
| — | `standardizedIdNumber` | texto | = `numeroDocumento` | **PII** |
| — | `standardizedIdName` | texto | Concatenación de los campos ya curados `primerNombre segundoNombre primerApellido segundoApellido`, en ese orden, omitiendo los nulos o vacíos, con un espacio entre partes; nulo si todos faltan | **PII** |
| — | `currentPartyType` | entero | = `tipoDocumento` | **PII** |
| — | `currentPartyIdNumber` | texto | = `numeroDocumento` | **PII** |
| — | `subscriberBlockStatus` | texto | Constante `NO_BLOQUEADO` | Público |
| — | `subscriberBlockReason` | texto | Constante nula | Público |
| — | `advertenciasCuracion` | lista de texto | Advertencias de la curación del registro, una por campo, con el formato `<campo curado>:<MOTIVO>` y sin el valor del dato (p. ej. `primerApellido:NO_NORMALIZABLE`, `idMarca:FORMATO_INVALIDO`); vacía si no hay. Motivos: `NO_NORMALIZABLE`, `FORMATO_INVALIDO`, `NO_HOMOLOGABLE`, `HOMOLOGACION_PENDIENTE`, `ARCHIVO_NO_RECONOCIDO`; `registro:REGISTRO_ILEGIBLE` si el registro no se puede leer | Público |

Esta tabla es la **lista completa** de campos del evento curado de SISBEN: no hay campos internos adicionales.

`subscriberRelation` del evento curado = `<standardizedIdType>_<standardizedIdNumber>`.

### 10.2 Estandarización de nombres

1. Mayúsculas.
2. Quitar tildes y diéresis (`Á`→`A`, `Ü`→`U`); la `Ñ` se conserva.
3. Todo carácter que no sea `A`–`Z`, `Ñ` o espacio pasa a espacio.
4. Colapsar espacios y recortar.
5. Si un nombre queda vacío al estandarizar: el campo sale nulo, el registro sigue y `advertenciasCuracion` anota `<campo curado>:NO_NORMALIZABLE` (p. ej. `primerApellido:NO_NORMALIZABLE`).

### 10.3 Carga completa

Cada archivo es la foto completa del corte: reemplaza la última foto (`DATSISBEN`) y el histórico (`HIST_DATSISBEN`) acumula. No hay archivos de reenvío (`RT`) ni de eliminación (`EP`).

### 10.4 Campos con inconsistencia (severidad 3)

Un campo que llega de Acceptance con un código de severidad 3 (`SISBEN_INC_*`) **se publica** en el evento curado, con la misma transformación de §10.1 que cualquier otro valor:

1. Si el valor se puede transformar, se publica estandarizado. Ejemplo: un `seg_apellido` de 31 caracteres sale en mayúsculas y sin tildes.
2. Si no se puede transformar, el campo sale **nulo** y `advertenciasCuracion` anota `<campo curado>:<MOTIVO>`, sin el valor. Ejemplos: `marca = N` (no es número) o una `Fec_digitacion` inválida.
3. Ninguna clave de §10.1 se omite del evento curado por tener severidad 3.

Configuración de Curation que lo implementa: `parametros.niveles_no_almacenar: []` y `parametros.niveles_anular: []`.

## 11. Contrato Curation → Pinning y Pinning

| Atributo | Valor |
|---|---|
| Claves que entrega Curation | `standardizedIdType`, `standardizedIdNumber`, `standardizedIdName` (§10.1) |
| Matcher | `ColombiaIdentityMatcher`, compartido de Colombia; SISBEN no genera `IdentityMatcher` propio |
| Bandera de fuente en el matcher | Ninguna: SISBEN no usa `isOfficialSource` ni una bandera propia; se identifica por la regla 2 del matcher, con `standardizedIdName` |
| Prioridad 1 | `standardizedIdType` + `standardizedIdNumber` (coincidencia exacta) |
| Prioridad 2 | `standardizedIdName` (NearNeighborKeys con el umbral vigente del matcher de Colombia) |
| `reusePIN` | `false` |
| Persona inexistente | PIN nuevo |
| CC sin coincidencia en BDIIREGI | Estado `PENDIENTE_VALIDACION_REGIS`; entra al proceso nocturno de compra RNEC (2 a. m.) |

## 12. Synthesis / Consistency

- SISBEN pasa por Synthesis.
- El `domaindictionary.json` no declara handlers para los campos de SISBEN: todos usan el comportamiento por defecto del motor (LFP, valor del evento más reciente).
- No hay `SynthesisProcessor` ni `ConsistencyCheckProcessor` propios.

## 13. State Calculation — sección `#sisben` del XPM

| Atributo XPM | Campo curado | Tipo |
|---|---|---|
| `grupoSisben` | `grupoSisben` | texto (1) |
| `nivelSisben` | `nivelSisben` | entero |
| `fechaEncuesta` | `fechaDigitacion` | epoch ms (nulo si no viene) |
| `fechaModificacion` | `fechaActualizacionCns` | epoch ms |
| `codDepartamentoSisben` | `codDepartamentoSisben` | texto (2) |
| `codMunicipioSisben` | `codMunicipioSisben` | texto (5) |

- La sección se genera solo para los PIN presentes en el archivo vigente.
- SISBEN alimenta además NAMES, BEST NAME, IDENTIFICATIONS y BEST IDENTIFICATIONS con la configuración estándar de Colombia, sin cambios.
- Sin scoring.
- Riesgo N4: aprueba el PO o el Aprobador designado.

## 14. Rutas (solo lectura) y reportes

La raíz de todas las rutas es `s3://colombia-data/sisben/`.

| Uso | Ruta |
|---|---|
| Entrada a Validation | `s3://colombia-data/sisben/preparation/prepared/` |
| Validados | `s3://colombia-data/sisben/validation/validated/` |
| Aceptados | `s3://colombia-data/sisben/acceptance/accepted/` |
| Registros rechazados | `s3://colombia-data/sisben/rejected/records/` |
| Archivos rechazados | `s3://colombia-data/sisben/rejected/files/` |
| Curados | `s3://colombia-data/sisben/curation/curated/` (`flow/`, `stock/`) |
| Pinned | `s3://colombia-data/sisben/pinning/pinned/` |
| Sintetizados | `s3://colombia-data/sisben/consistency/synthesized/` |
| XPM | `s3://colombia-data/sisben/statecalc/calculated/` |
| Logs | `s3://colombia-data/sisben/reports/logs/` |

Reportes (reporting estándar de EDF; las skills del framework no los generan):

| Reporte | Contenido |
|---|---|
| `Log_Sisben.AAAAMMDD` | Inicio y fin, archivo, `REGISTROS LEIDOS ENTRADA`, `REGISTRO OMITIDO ENCABEZADO`, `REGISTROS VALIDOS`, `REGISTROS DUPLICADOS`, `REGISTROS NO VALIDOS ÚNICOS`, `NUMERO DE RECHAZADOS`, `REGISTROS INCONSISTENTES`, `REGISTROS MENORES DE EDAD`, `REGISTROS TIPO 7 PENDIENTES` |
| `RPT_SISBEN_VALIDATION_INCONSISTENCIAS_AAAAMMDD.csv` | Por registro con error: tipo y número de documento, campo, código, descripción, severidad, catálogo, timestamp. Se envía al DNP |
| `RPT_SISBEN_ACCEPTANCE_RECHAZADOS_AAAAMMDD.csv` | Registros con RQS 0. Obligatorio por contrato con el DNP |
| `RPT_SISBEN_ACCEPTANCE_PROCESADOS_AAAAMMDD.csv` | Totales, FQS, umbral, estado del archivo |
| `RPT_SISBEN_CURATION_AAAAMMDD.csv`, `RPT_SISBEN_PINNING_AAAAMMDD.csv`, `RPT_SISBEN_STATECALC_AAAAMMDD.csv` | Conteos de cada etapa (HU-SISBEN §5) |

## 15. Muestra sintética y resultado esperado

Archivo `muestra/SISBEN_EXPERIAN_202601.20260205093000-0500.txt`: cumple el contrato del archivo y representa una entrega mensual correcta. Los casos de error se prueban con el fixture del Anexo A (bloque `fixture`), no con esta muestra. Resultado obtenido con `prevalidar_archivo.py --hoy 2026-11-17` usando el Anexo A:

| Registros | Filtrados | Validados | Con sev. 1 | Duplicados | RQS medio | Acceptance |
|:-:|:-:|:-:|:-:|:-:|:-:|---|
| 21 | 1 | 20 | 1 | 0 | 0.95 | **Acepta**; 19 registros aceptados |

| Línea | Caso | Resultado |
|:-:|---|---|
| 2 a 20 | 19 registros válidos: tipos `3`, `5`, `6` y `8`; las tres clases; los cuatro grupos con sus subgrupos límite (A5, B7, C18, D21); segundos nombres y apellidos presentes y vacíos | Válidos |
| 21 | `Grupo = Z` | `SISBEN_ERR_016` |
| 22 | Tipo `2` (tarjeta de identidad) | Filtrado (menor) |

## Anexo A — Valores del `source-spec` de Validation

Es el contenido que `edf-analisis` debe dejar en `insumos/source-spec-validation.yaml`. Está verificado contra el esquema y el generador de `edf-validation` (ver la cabecera). El bloque `fixture` es el fixture sintético que usará el `SISBENValidationProcessorSpec`.

```yaml
# source-spec de Validation derivado de spec-sisben.md y hu-sisben.md. DATOS SINTÉTICOS.
spec_version: "sisben-iv-1.0.0"

fuente:
  Fuente: SISBEN
  fuente: sisben
  event_type: 30
  datasource: DNP
  subscriber: "999722"
  zona_horaria: America/Bogota

paquetes:
  comun: com.experian.edf.oxygen.validation.source.common
  base_fuente: com.experian.edf.oxygen.validation.source

archivo:
  formato: text
  delimitador: ";"
  filas_header: 1
  filas_footer: 0
  footer_regex: null
  encoding: null
  file_pattern: '.*SISBEN_EXPERIAN_\d{6}\.\d{14}[+-]\d{4}\.(txt|TXT)'
  nombre:
    descripcion: "SISBEN_EXPERIAN_AAAAMM.yyyyMMddHHmmss±ZZZZ.txt"
    regex: '()SISBEN_EXPERIAN_(\d{6})\.(\d{14})([+-]\d{4})\.(txt|TXT)'
    grupos: {prefijo: 1, periodo: 2, marca_tiempo: 3, offset: 4}
    file_tag_por_prefijo: {"": REGULAR}
    file_as_of_date: ultimo_dia_periodo

guard_rails:
  subscriber_relation: NUM_DOCUMENTO
  as_of_date: FECHA_ACTUALIZACION_CNS
  received_date: marca_tiempo_nombre

campos:
  - {posicion: 1, nombre: ID_FICHA_ORIGEN, alias: [ide_ficha_origen], obligatorio: false, severidad: "3", tipo: numerico, longitud_max: 20, codigo: SISBEN_INC_001, ejemplo_invalido: "AB12"}
  - {posicion: 2, nombre: ID_HOGAR, alias: [ide_hogar], obligatorio: false, severidad: "3", tipo: numerico, longitud_max: 20, codigo: SISBEN_INC_002, ejemplo_invalido: "X1"}
  - {posicion: 3, nombre: COD_DPTO, alias: [cod_dpto], obligatorio: true, severidad: "1", tipo: numerico, longitud_max: 2, catalogo: {tabla: 102_DANE_DIVIPOLA_STATES_CODES_NH, columna: cod_dpto}, codigo: SISBEN_ERR_003, ejemplo_invalido: "99"}
  - {posicion: 4, nombre: NOM_DPTO, alias: [nom_dpto], obligatorio: true, severidad: "1", tipo: alfanumerico, longitud_max: 60, codigo: SISBEN_ERR_004, ejemplo_invalido: ""}
  - {posicion: 5, nombre: COD_MPIO, alias: [cod_mpio], obligatorio: true, severidad: "1", tipo: numerico, longitud_max: 5, catalogo: {tabla: 2_DANE_DIVIPOLA_CITIES_CODES_NH, columna: cod_mpio}, codigo: SISBEN_ERR_005, ejemplo_invalido: "99999"}
  - {posicion: 6, nombre: NOM_MPIO, alias: [nom_mpio], obligatorio: true, severidad: "1", tipo: alfanumerico, longitud_max: 60, codigo: SISBEN_ERR_006, ejemplo_invalido: ""}
  - {posicion: 7, nombre: ID_CLASE, alias: [cod_clase], obligatorio: true, severidad: "1", tipo: numerico, longitud_max: 1, dominio: ["1", "2", "3"], catalogo: {tabla: 201_SISBEN_ZONES_NH, columna: cod_clase}, codigo: SISBEN_ERR_007, ejemplo_invalido: "4"}
  - {posicion: 8, nombre: ID_PERSONA_ORDEN, alias: [ide_persona], obligatorio: false, severidad: "3", tipo: numerico, longitud_max: 20, codigo: SISBEN_INC_008, ejemplo_invalido: "X1"}
  - {posicion: 9, nombre: ID_NACIONAL, alias: [ide_nacional], obligatorio: false, severidad: "3", tipo: numerico, longitud_max: 20, codigo: SISBEN_INC_009, ejemplo_invalido: "ABC"}
  - {posicion: 10, nombre: PRI_APELLIDO, alias: [pri_apellido], obligatorio: true, severidad: "1", tipo: alfanumerico, longitud_max: 30, codigo: SISBEN_ERR_010, ejemplo_invalido: ""}
  - {posicion: 11, nombre: SEG_APELLIDO, alias: [seg_apellido], obligatorio: false, severidad: "3", tipo: alfanumerico, longitud_max: 30, codigo: SISBEN_INC_011, ejemplo_invalido: "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"}
  - {posicion: 12, nombre: PRI_NOMBRE, alias: [pri_nombre], obligatorio: true, severidad: "1", tipo: alfanumerico, longitud_max: 30, codigo: SISBEN_ERR_012, ejemplo_invalido: ""}
  - {posicion: 13, nombre: SEG_NOMBRE, alias: [seg_nombre], obligatorio: false, severidad: "3", tipo: alfanumerico, longitud_max: 30, codigo: SISBEN_INC_013, ejemplo_invalido: "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"}
  - {posicion: 14, nombre: TIP_DOCUMENTO, alias: [tip_documento], obligatorio: true, severidad: "1", tipo: alfanumerico, longitud_max: 3, dominio: ["1", "2", "3", "4", "5", "6", "7", "8", "PPT"], catalogo: {tabla: 202_SISBEN_PROCESS_ID_TYPE_HO, columna: cod_sisben}, codigo: SISBEN_ERR_014, ejemplo_invalido: "9"}
  - {posicion: 15, nombre: NUM_DOCUMENTO, alias: [num_documento], obligatorio: true, severidad: "1", tipo: alfanumerico, longitud_max: 15, normalizacion: [quitar_espacios, quitar_ceros_izquierda], codigo: SISBEN_ERR_015, ejemplo_invalido: ""}
  - {posicion: 16, nombre: ID_GRUPO, alias: [grupo], obligatorio: true, severidad: "1", tipo: alfanumerico, longitud_max: 1, dominio: [A, B, C, D], catalogo: {tabla: 234_SISBEN_GROUP_SUBGROUP_NH, columna: grupo}, codigo: SISBEN_ERR_016, ejemplo_invalido: "E"}
  - {posicion: 17, nombre: ID_NIVEL, alias: [nivel], obligatorio: true, severidad: "1", tipo: numerico, longitud_max: 2, rango: [1, 21], codigo: SISBEN_ERR_017, ejemplo_invalido: "22"}
  - {posicion: 18, nombre: FECHA_DIGITACION, alias: [fec_digitacion], obligatorio: false, severidad: "3", tipo: fecha, formatos_fecha: ["yyyy-MM-dd", "dd/MM/yyyy"], no_futura: true, codigo: SISBEN_INC_018, ejemplo_invalido: "2099-01-01"}
  - {posicion: 19, nombre: FECHA_ACTUALIZACION_PERSONA, alias: [fec_actualizacion_persona], obligatorio: false, severidad: "3", tipo: fecha, formatos_fecha: ["yyyy-MM-dd", "dd/MM/yyyy"], codigo: SISBEN_INC_019, ejemplo_invalido: "31/02/2025"}
  - {posicion: 20, nombre: ID_MARCA, alias: [marca], obligatorio: false, severidad: "3", tipo: numerico, dominio: ["0", "1", "10", "20"], catalogo: {tabla: 203_SISBEN_RECORD_STATUS_AND_MARK_NH, columna: codigo}, codigo: SISBEN_INC_020, ejemplo_invalido: "5"}
  - {posicion: 21, nombre: ID_ESTADO, alias: [estado], obligatorio: true, severidad: "1", tipo: numerico, dominio: ["0", "10", "40", "41", "42", "43", "60", "61", "69", "70", "90", "91"], catalogo: {tabla: 203_SISBEN_RECORD_STATUS_AND_MARK_NH, columna: codigo}, codigo: SISBEN_ERR_021, ejemplo_invalido: "50"}
  - {posicion: 22, nombre: FECHA_ACTUALIZACION_CNS, alias: [fec_actualizacion_cns], obligatorio: true, severidad: "1", tipo: fecha, formatos_fecha: ["yyyy-MM-dd", "dd/MM/yyyy"], no_futura: true, codigo: SISBEN_ERR_022, ejemplo_invalido: "31/02/2025"}

reglas_entre_campos:
  - {id: RV-SIS-01, tipo: correspondencia_catalogo, campo_codigo: COD_DPTO, campo_nombre: NOM_DPTO, tabla: 102_DANE_DIVIPOLA_STATES_CODES_NH, columna_clave: cod_dpto, columna_nombre: nom_dpto, codigo: SISBEN_ERR_004, severidad: "1"}
  - {id: RV-SIS-02, tipo: correspondencia_catalogo, campo_codigo: COD_MPIO, campo_nombre: NOM_MPIO, tabla: 2_DANE_DIVIPOLA_CITIES_CODES_NH, columna_clave: cod_mpio, columna_nombre: nom_mpio, codigo: SISBEN_ERR_006, severidad: "1"}
  - {id: RV-SIS-03, tipo: correspondencia_catalogo, campo_codigo: COD_MPIO, campo_nombre: COD_DPTO, tabla: 2_DANE_DIVIPOLA_CITIES_CODES_NH, columna_clave: cod_mpio, columna_nombre: cod_dpto, codigo: SISBEN_ERR_028, severidad: "1"}
  - {id: RV-SIS-04, tipo: valor_con_codigo_propio, campo: TIP_DOCUMENTO, valores: ["7"], codigo: SISBEN_ERR_023, severidad: "1", causa: TIPO_PENDIENTE_CREACION}
  - {id: RV-SIS-05, tipo: valor_con_codigo_propio, campo: NUM_DOCUMENTO, valores: ["0", "00", "000", "0000", "00000", "000000", "0000000", "00000000", "000000000", "0000000000", "00000000000", "000000000000", "0000000000000", "00000000000000", "000000000000000"], codigo: SISBEN_ERR_029, severidad: "1", causa: DOCUMENTO_CERO}

filtro_filas:
  lineas_vacias: true
  campo: TIP_DOCUMENTO
  valores: ["1", "2"]

quality_scores:
  - {nombre: RQS, anulan_niveles: ["1"]}

metricas:
  - {id: AverageRQS, expr: 'average("qs.RQS")'}
  - {id: TotalRegistros, expr: 'count(*)'}

duplicados:
  habilitado: true
  clave: [TIP_DOCUMENTO, NUM_DOCUMENTO]
  modo: EXTN
  campo_fecha: FECHA_ACTUALIZACION_CNS
  umbral: null

codigos_especiales:
  nombre_archivo: SISBEN_ERR_024
  header: SISBEN_ERR_025
  estructura: SISBEN_ERR_026
  interno: SISBEN_ERR_027

diagnostico_archivo:
  fileName: 'errorlevelNotIn("1")'
  header: 'errorlevelNotIn("1")'

ambiente:
  default_output_structure: true
  historic_attributes: false
  lambda: batch

catalogos:
  - tabla: 102_DANE_DIVIPOLA_STATES_CODES_NH
    archivo: 102_DANE_DIVIPOLA_STATES_CODES_NH.csv
    delimitador: ";"
    columnas: [cod_dpto, nom_dpto]
    keys: [cod_dpto]
    filas_sinteticas: [["05","ANTIOQUIA"],["08","ATLANTICO"],["11","BOGOTA D.C."],["13","BOLIVAR"],["68","SANTANDER"],["76","VALLE DEL CAUCA"]]
  - tabla: 2_DANE_DIVIPOLA_CITIES_CODES_NH
    archivo: 2_DANE_DIVIPOLA_CITIES_CODES_NH.csv
    delimitador: ";"
    columnas: [cod_mpio, nom_mpio, cod_dpto]
    keys: [cod_mpio]
    filas_sinteticas: [["05001","MEDELLIN","05"],["08001","BARRANQUILLA","08"],["11001","BOGOTA D.C.","11"],["13001","CARTAGENA","13"],["68001","BUCARAMANGA","68"],["76001","CALI","76"]]
  - tabla: 201_SISBEN_ZONES_NH
    archivo: 201_SISBEN_ZONES_NH.csv
    delimitador: ";"
    columnas: [cod_clase, descripcion]
    keys: [cod_clase]
    filas_sinteticas: [["1","CABECERA"],["2","CENTRO POBLADO"],["3","RURAL DISPERSO"]]
  - tabla: 202_SISBEN_PROCESS_ID_TYPE_HO
    archivo: 202_SISBEN_PROCESS_ID_TYPE_HO.csv
    delimitador: ";"
    columnas: [cod_sisben, cod_experian, descripcion]
    keys: [cod_sisben]
    filas_sinteticas: [["1","10","REGISTRO CIVIL"],["2","7","TARJETA DE IDENTIDAD"],["3","1","CEDULA DE CIUDADANIA"],["4","4","CEDULA DE EXTRANJERIA"],["5","8","DNI PAIS DE ORIGEN"],["6","5","PASAPORTE"],["7","","SALVOCONDUCTO PARA REFUGIADO"],["8","9","PERMISO ESPECIAL DE PERMANENCIA"],["PPT","11","PERMISO DE PROTECCION TEMPORAL"]]
  - tabla: 234_SISBEN_GROUP_SUBGROUP_NH
    archivo: 234_SISBEN_GROUP_SUBGROUP_NH.csv
    delimitador: ";"
    columnas: [grupo, nivel_max, descripcion]
    keys: [grupo]
    filas_sinteticas: [["A","5","POBREZA EXTREMA"],["B","7","POBREZA MODERADA"],["C","18","VULNERABLE"],["D","21","NO POBRE NO VULNERABLE"]]
  - tabla: 203_SISBEN_RECORD_STATUS_AND_MARK_NH
    archivo: 203_SISBEN_RECORD_STATUS_AND_MARK_NH.csv
    delimitador: ";"
    columnas: [codigo, tipo, descripcion]
    keys: [codigo]
    filas_sinteticas: [["0","AMBOS","REGISTRO VALIDO"],["1","MARCA","EXCLUIDO DE PUBLICACION"],["10","AMBOS","ESTADO EXCLUIDO FALLECIDO / MARCA EN VERIFICACION"],["20","MARCA","VERIFICACION POR DISTANCIA"],["40","ESTADO","VERIFICACION DESACTUALIZACION SECCION E"],["41","ESTADO","VERIFICACION DESACTUALIZACION SECCION G NIVEL EDUCATIVO"],["42","ESTADO","VERIFICACION DESACTUALIZACION SECCION G FONDO PENSIONES"],["43","ESTADO","VERIFICACION DESACTUALIZACION SECCION H"],["60","ESTADO","CALIDAD ENCUESTA DATOS NO VALIDOS"],["61","ESTADO","CALIDAD ENCUESTA PARENTESCOS INCONSISTENTES"],["69","ESTADO","VERIFICACION POR DISTANCIA MAYOR A 600 M"],["70","ESTADO","CALIDAD ENCUESTA DOCUMENTO NO VALIDO"],["90","ESTADO","DESMEJORAMIENTO VARIABLES DE VIVIENDA"],["91","ESTADO","DESMEJORAMIENTO VARIABLES DE NIVEL EDUCATIVO"]]

handoff_acceptance:
  umbral_rqs: 0.90
  record_acceptance: 'errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate")'

fixture:
  nombre_archivo: SISBEN_EXPERIAN_202601.20260205093000-0500.txt
  fecha_referencia: "2026-02-05"
  registro_base: ["0900000101","1","05","ANTIOQUIA","05001","MEDELLIN","1","1","9300000101","PRUEBAUNO","APELLIDOUNO","NOMBREUNO","SEGUNDOUNO","3","1000000101","A","1","15/11/2025","20/12/2025","0","0","10/01/2026"]
  casos:
    - {id: BASE, descripcion: "registro válido", cambios: {}, esperado: {codigos: []}}
    - {id: FECHA_ISO, descripcion: "fechas en yyyy-MM-dd", cambios: {NUM_DOCUMENTO: "1000000102", FECHA_DIGITACION: "2025-11-15", FECHA_ACTUALIZACION_CNS: "2026-01-10"}, esperado: {codigos: []}}
    - {id: MENOR_RC, descripcion: "registro civil: se filtra", cambios: {TIP_DOCUMENTO: "1", NUM_DOCUMENTO: "1000000103"}, esperado: {filtrado: true}}
    - {id: MENOR_TI, descripcion: "tarjeta de identidad: se filtra", cambios: {TIP_DOCUMENTO: "2", NUM_DOCUMENTO: "1000000104"}, esperado: {filtrado: true}}
    - {id: LINEA_VACIA, descripcion: "línea vacía: se filtra", linea: "", esperado: {filtrado: true}}
    - {id: CE_ADULTO, descripcion: "cédula de extranjería es válida", cambios: {TIP_DOCUMENTO: "4", NUM_DOCUMENTO: "1000000105"}, esperado: {codigos: []}}
    - {id: PASAPORTE_ALFA, descripcion: "pasaporte alfanumérico válido", cambios: {TIP_DOCUMENTO: "6", NUM_DOCUMENTO: "PA1000106"}, esperado: {codigos: []}}
    - {id: PPT, descripcion: "PPT válido", cambios: {TIP_DOCUMENTO: "PPT", NUM_DOCUMENTO: "1000000107"}, esperado: {codigos: []}}
    - {id: SALVOCONDUCTO, descripcion: "tipo 7 sin homologación", cambios: {TIP_DOCUMENTO: "7", NUM_DOCUMENTO: "1000000108"}, esperado: {codigos: [SISBEN_ERR_023]}}
    - {id: TIPO_INEXISTENTE, descripcion: "tipo 9", cambios: {TIP_DOCUMENTO: "9", NUM_DOCUMENTO: "1000000109"}, esperado: {codigos: [SISBEN_ERR_014]}}
    - {id: DOC_CERO, descripcion: "número de documento en ceros", cambios: {NUM_DOCUMENTO: "0000"}, esperado: {codigos: [SISBEN_ERR_029]}}
    - {id: GRUPO_INVALIDO, descripcion: "grupo Z", cambios: {ID_GRUPO: "Z", NUM_DOCUMENTO: "1000000110"}, esperado: {codigos: [SISBEN_ERR_016]}}
    - {id: NIVEL_FUERA, descripcion: "nivel 22", cambios: {ID_NIVEL: "22", NUM_DOCUMENTO: "1000000111"}, esperado: {codigos: [SISBEN_ERR_017]}}
    - {id: MPIO_OTRO_DPTO, descripcion: "municipio de otro departamento", cambios: {COD_DPTO: "11", NOM_DPTO: "BOGOTA D.C.", NUM_DOCUMENTO: "1000000112"}, esperado: {codigos: [SISBEN_ERR_028]}}
    - {id: NOM_DPTO_NO_CORRESPONDE, descripcion: "nombre de departamento no corresponde", cambios: {NOM_DPTO: "BOLIVAR", NUM_DOCUMENTO: "1000000113"}, esperado: {codigos: [SISBEN_ERR_004]}}
    - {id: CNS_VACIA, descripcion: "fecha de actualización CNS vacía", cambios: {FECHA_ACTUALIZACION_CNS: "", NUM_DOCUMENTO: "1000000114"}, esperado: {codigos: [SISBEN_ERR_022]}}
    - {id: DIGITACION_VACIA, descripcion: "fecha de digitación vacía es válida", cambios: {FECHA_DIGITACION: "", NUM_DOCUMENTO: "1000000115"}, esperado: {codigos: []}}
    - {id: FECHA_FUTURA, descripcion: "fecha de digitación futura", cambios: {FECHA_DIGITACION: "01/01/2099", NUM_DOCUMENTO: "1000000116"}, esperado: {codigos: [SISBEN_INC_018]}}
    - {id: ESTADO_TEXTO, descripcion: "estado textual", cambios: {ID_ESTADO: "ACTIVO", NUM_DOCUMENTO: "1000000117"}, esperado: {codigos: [SISBEN_ERR_021]}}
    - {id: MARCA_TEXTO, descripcion: "marca textual: inconsistencia", cambios: {ID_MARCA: "N", NUM_DOCUMENTO: "1000000118"}, esperado: {codigos: [SISBEN_INC_020]}}
    - {id: NOMBRE_TILDES, descripcion: "nombres con Ñ y tildes válidos", cambios: {PRI_APELLIDO: "MUÑOZ", PRI_NOMBRE: "JOSÉ", NUM_DOCUMENTO: "1000000119"}, esperado: {codigos: []}}
    - {id: TRUNCADA, descripcion: "fila con 10 columnas", cambios: {NUM_DOCUMENTO: "1000000120"}, columnas: 10, esperado: {codigos: [SISBEN_ERR_026]}}
    - {id: DUP_ANTIGUO, descripcion: "duplicado más antiguo: se marca", cambios: {NUM_DOCUMENTO: "1000000199", FECHA_ACTUALIZACION_CNS: "01/01/2026"}, esperado: {codigos: [], duplicado: true}}
    - {id: DUP_RECIENTE, descripcion: "duplicado más reciente: se conserva", cambios: {NUM_DOCUMENTO: "1000000199", FECHA_ACTUALIZACION_CNS: "15/01/2026"}, esperado: {codigos: [], duplicado: false}}
```
