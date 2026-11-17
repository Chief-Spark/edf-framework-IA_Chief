# Especificación SISBEN V4 (caso piloto)

Versión ejecutable: `assets/examples/sisben/source-spec.sisben.yaml`. Esta página la explica; si hay
diferencia, manda el YAML.

## Identidad (IN-01, cerrado)

`Fuente = SISBEN`, `fuente = sisben`, `eventType = 30` (inferido), `datasource = DNP`,
`subscriber = 999722`, zona horaria `America/Bogota`, epochs en milisegundos.

## Contrato del archivo (IN-02)

| Atributo | Valor | Estado |
|---|---|---|
| Formato / delimitador | texto, `;` | cerrado |
| Header / footer | 1 fila de header (22 nombres), 0 footer → `headerRows = 1`, `footerRows = 0`, `isFooterRow = false` | cerrado |
| Encoding | UTF-8, sin clave `encoding` | propuesta de frontera (D5, F-17) |
| Nombre | `[RT_|EP_]SISBEN_EXPERIAN_AAAAMM.AAAAMMDDHHMMSS±ZZZZ.txt` | propuesta de frontera (D8) |
| `filePattern` | `.*(RT_|EP_)?SISBEN_EXPERIAN_\d{6}\.\d{14}[+-]\d{4}\.txt` (en HOCON `\\d`, `\\.`) | propuesta de frontera (F-07) |
| `fileTag` | `REGULAR` / `RT` / `EP` según prefijo | ASSUMPTION (F-07) |
| `fileAsOfDate` | último día del periodo AAAAMM | ASSUMPTION |
| `receivedDate` | marca de tiempo del nombre con su offset | propuesta de frontera (D3) |

## Layout de 22 campos (IN-03)

Header: se aceptan nombres de dominio o nombres fuente, sin distinguir mayúsculas ni tildes, y se exige
que haya 22 columnas (F-06). El campo 18 admite `fecha` o `fec_digitacion`.

| # | Campo | Alias fuente | Oblig. | Sev. | Tipo | Regla | Código |
|---|---|---|---|---|---|---|---|
| 1 | ID_FICHA_ORIGEN | ide_ficha_origen | Sí | 1 | Alfanum. ≤50 | no nulo | SISBEN_ERR_001 |
| 2 | ID_HOGAR | ide_hogar | Sí | 1 | Numérico | 1–15; catálogo HOME_ID | SISBEN_ERR_002 |
| 3 | COD_DPTO | cod_dpto | Sí | 1 | Alfanum. ≤2 | existe en DANE deptos | SISBEN_ERR_003 |
| 4 | NOM_DPTO | nom_dpto | Sí | 1 | Alfanum. ≤60 | corresponde con COD_DPTO (RV-SIS-02) | SISBEN_ERR_004 |
| 5 | COD_MPIO | cod_mpio | Sí | 1 | Alfanum. ≤5 | existe en DANE municipios | SISBEN_ERR_005 |
| 6 | NOM_MPIO | nom_mpio | Sí | 1 | Alfanum. ≤60 | corresponde con COD_MPIO (RV-SIS-03) | SISBEN_ERR_006 |
| 7 | ID_CLASE | cod_clase | Sí | 1 | Numérico 1 | 1, 2, 3; catálogo zonas | SISBEN_ERR_007 |
| 8 | ID_PERSONA_ORDEN | ide_persona | Sí | 1 | Numérico | 1–25 | SISBEN_ERR_008 |
| 9 | ID_NACIONAL | ide_nacional | Sí | 1 | Numérico | no nulo | SISBEN_ERR_009 |
| 10 | PRI_APELLIDO | pri_apellido | Sí | 1 | Alfanum. ≤50 | no nulo (tildes y ñ) | SISBEN_ERR_010 |
| 11 | SEG_APELLIDO | seg_apellido | No | 3 | Alfanum. ≤50 | puede ser vacío | SISBEN_INC_011 |
| 12 | PRI_NOMBRE | pri_nombre | Sí | 1 | Alfanum. ≤50 | no nulo | SISBEN_ERR_012 |
| 13 | SEG_NOMBRE | seg_nombre | No | 3 | Alfanum. ≤50 | puede ser vacío | SISBEN_INC_013 |
| 14 | TIP_DOCUMENTO | tip_documento | Sí | 1 | Alfanum. (D10) | 1–8 o PPT; catálogo 202 (RV-SIS-07, RV-GEN-06) | SISBEN_ERR_014 |
| 15 | NUM_DOCUMENTO | num_documento | Sí | 1 | Alfanum. ≤20 | normalización en memoria (RV-GEN-07) | SISBEN_ERR_015 |
| 16 | ID_GRUPO | grupo | Sí | 1 | Alfanum. | A, B, C, D | SISBEN_ERR_016 |
| 17 | ID_NIVEL | nivel | Sí | 1 | Numérico | 1–21 | SISBEN_ERR_017 |
| 18 | FECHA_DIGITACION | fecha / fec_digitacion | Sí | 1 | Fecha | `dd/MM/yyyy` o `yyyy-MM-dd HH:mm:ss`; no futura (RV-SIS-04) | SISBEN_ERR_018 |
| 19 | FECHA_ACTUALIZACION_PERSONA | fec_actualizacion_persona | No | 3 | Fecha | `dd/MM/yyyy` | SISBEN_INC_019 |
| 20 | ID_MARCA | marca | No | 3 | Numérico | 0, 1, 10 | SISBEN_INC_020 |
| 21 | ID_ESTADO | estado | Sí | 1 | Numérico | 0, 10, 40–43, 60, 61, 70, 90, 91 | SISBEN_ERR_021 |
| 22 | FECHA_ACTUALIZACION_CNS | fec_actualizacion_cns | Sí | 1 | Fecha | `dd/MM/yyyy` o `yyyy-MM-dd HH:mm:ss` | SISBEN_ERR_022 |

Orden de evaluación por campo (un solo código por campo, F-10): obligatorio → tipo/formato → longitud →
rango → dominio → no futura → catálogo. La causa va en `messageKey` (`OBLIGATORIO`, `TIPO`,
`LONGITUD`, `RANGO`, `DOMINIO`, `FORMATO_FECHA`, `FECHA_FUTURA`, `CATALOGO`, `CORRESPONDENCIA`), nunca el
valor.

## Códigos adicionales

| Código | Sev. | Uso | Estado |
|---|---|---|---|
| SISBEN_ERR_023 | 1 | TIP_DOCUMENTO = 7 (salvoconducto, RV-SIS-06), causa `TIPO_PENDIENTE_CREACION` | ASSUMPTION F-08 |
| SISBEN_ERR_024 | 1 | Nombre de archivo inválido (`validateFilename`) | ASSUMPTION F-11 |
| SISBEN_ERR_025 | 1 | Header inválido (`validateHeader`) | ASSUMPTION F-11 |
| SISBEN_ERR_026 | 1 | Fila con ≠ 22 columnas | ASSUMPTION F-09 |
| SISBEN_ERR_027 | 1 | Error interno inesperado al validar un registro (red de seguridad, no debería ocurrir) | ASSUMPTION |

Todos miden 14 caracteres: longitud fija, ninguno es subcadena de otro, ninguno mide 11
(`decodeErrorObject`), ninguno choca con `OxygenDuplicate`.

## Filtro, guard-rails, duplicados, QS

- **Filtro (IN-07, D6.a/D14):** `shouldFilterRow` descarta líneas vacías y filas con campo 14 = `1` o
  `2` (menores). Silencioso, fuera de todo QS. Una fila con campo 14 ilegible no se filtra.
- **Guard-rails (IN-05, D3):** `Subscriber = 999722`, `Datasource = DNP`,
  `subscriberRelation = NUM_DOCUMENTO`, `asOfDate = FECHA_ACTUALIZACION_CNS` (epoch ms, inicio del día en
  Bogotá). `domainData` siempre `Some(...)`. Si la fecha es inválida (ya diagnosticada) se usa la
  fecha de recepción (ASSUMPTION).
- **Duplicados (IN-06, D9):** clave `TIP_DOCUMENTO + NUM_DOCUMENTO` normalizados; `deDupVersion = EXTN`;
  se conserva el de mayor `asOfDate` y, en empate, el de mayor `eventId` (F-14). Los demás reciben
  `ObjectError("OxygenDuplicateError", "OxygenDuplicate", "", "")`. Un registro sin tipo o número no se
  agrupa con otros.
- **Quality score (IN-09):** `RQS` = 1.0 sin errores de nivel 1, 0.0 con alguno. Métricas de archivo:
  `AverageRQS = average("qs.RQS")`, `TotalRegistros = count(*)`.

## Catálogos (IN-08)

Siete tablas: `SISBEN_HOME_ID_CLASIFICATION_NH`, `102_DANE_DIVIPOLA_STATES_CODES_NH`,
`2_DANE_DIVIPOLA_CITIES_CODES_NH`, `201_SISBEN_ZONES_NH`, `202_SISBEN_PROCESS_ID_TYPE_HO`,
`SISBEN_GROUP_SUBGROUP_NH`, `203_SISBEN_RECORD_STATUS_AND_MARK_NH`. Columnas, archivo, delimitador y
claves son ASSUMPTION (F-12); las filas del ejemplo son sintéticas y la DIVIPOLA no está verificada
contra DANE (F-13).

Homologación de TIP_DOCUMENTO (la persiste Curation; aquí solo se consulta): `1→10`, `2→7`, `3→1`,
`4→4`, `5→8`, `6→5`, `7→pendiente`, `8→9`, `PPT→11`.

## Discrepancias documentales (se registran, no se resuelven)

Ver `decisiones-y-abiertos.md` (C19/C34 sobre `ID_FICHA_ORIGEN`, `ID_GRUPO` con `null/nan`, numeración
RV-SIS).
