# Mapeo de campos — SISBEN IV

| Campo | Valor |
|---|---|
| Ingesta | `sisben` |
| Versión / Fecha | 1.0.1 / 2026-11-24 |
| Estado | PROPUESTO |
| Fuentes | `fuentes/hu-sisben.md` 1.0.2; `fuentes/spec-sisben.md` 1.0.2 (§3 layout, §4 a §8 reglas, §9 catálogos, §10 Curation, §11 Pinning, §12 Synthesis) |
| Skill | `edf-analisis` (modo etapa 0, tipo T1) |
| Cobertura | **22 de 22** campos del contrato del archivo (`spec-sisben.md` §2, §3) |

## Objetivo y alcance

Una fila por campo de la fuente, con las **nueve dimensiones** de `07-contratos-frontera.md` §3. **No se mapea al XPM** (Experian Profile Map) aquí (`04` §4.4): la sección `#sisben` de `spec-sisben.md` §13 se usa recién en el insumo de State Calculation.

Convenciones de esta tabla:

- **Nombre en origen**: columna del header (`spec-sisben.md` §2) y, entre paréntesis, el nombre EDF que usa Validation (`spec-sisben.md` §3).
- **Nombre curado**: el de `spec-sisben.md` §10.1, que la spec declara definitivo («Pinning, Synthesis y State Calculation los citan literalmente»). Por eso es `[C]` y no una propuesta.
- **PII**: información personalmente identificable, según `hu-sisben.md` R-28 y `spec-sisben.md` §10.1.
- **Efecto de la violación** con el vocabulario de `07` §5.1. Sev. 1 = rechazo del registro (RQS 0); sev. 3 = inconsistencia registrada (el RQS no cambia) (`hu-sisben.md` R-20). En Curation, un valor con sev. 3 que no se puede transformar sale nulo y `advertenciasCuracion` anota `<campo curado>:<MOTIVO>`, sin el valor (`spec-sisben.md` §10.4).
- **Llave de identidad** (`07` §3, dimensión 8): prioridad 1 `standardizedIdType` + `standardizedIdNumber`; prioridad 2 `standardizedIdName` (`hu-sisben.md` R-24; `spec-sisben.md` §11).
- **Field Handler**: la spec responde el punto que `07` §3 deja abierto (`[A:OQ-TR-3]`): SISBEN no declara handlers y todos los campos usan el comportamiento por defecto del motor, LFP (Latest For Period), valor del evento más reciente (`spec-sisben.md` §12; `hu-sisben.md` R-25). Para esta ingesta es `[C]` (`00` §6).
- **EPOCH**: toda fecha a epoch UTC `Long` en milisegundos, desde el inicio del día en `America/Bogota` (`hu-sisben.md` R-23; `07` §3, dimensión 4).

## Tabla de campos

| # | Nombre en origen | Nombre curado propuesto | Público / PII | Tipo origen → destino | Regla de transformación (incl. EPOCH) | Catálogo | Obligatorio (y qué pasa si falta) | Efecto de la violación (`07` §5.1) | ¿Llave de identidad? | Field Handler (LFP/LAP/EUC) | Estado | Fuente |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `ide_ficha_origen` (`ID_FICHA_ORIGEN`) | `idFichaOrigen` | Público | numérico, solo dígitos, hasta 20, puede empezar por 0 → texto | Ninguna; conserva el cero inicial | — | No; vacío no se valida | Inconsistencia registrada `SISBEN_INC_001` (sev. 3); Curation la publica igual | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 1, §10.1, §12; `hu-sisben.md` R-28 |
| 2 | `ide_hogar` (`ID_HOGAR`) | `idHogar` | Público | numérico, hasta 20 dígitos → texto | Ninguna; no se convierte a número (no cabe siempre en 64 bits) | — | No; vacío no se valida | Inconsistencia registrada `SISBEN_INC_002` (sev. 3) | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 2, §10.1; `hu-sisben.md` R-09, R-28 |
| 3 | `Cod_dpto` (`COD_DPTO`) | `codDepartamentoSisben` | Público | numérico, 2 dígitos → texto (2) | Ninguna | `102_DANE_DIVIPOLA_STATES_CODES_NH` (`cod_dpto`) | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_003` (no existe en el catálogo); `SISBEN_ERR_028` si el municipio no pertenece al departamento (RV-SIS-03) | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 3, §4, §9.1, §10.1; `hu-sisben.md` R-04, R-13 |
| 4 | `nom_dpto` (`NOM_DPTO`) | `nomDepartamento` | Público | alfanumérico, hasta 60 → texto | Estandarización de nombre (`spec-sisben.md` §10.2) | `102_DANE_DIVIPOLA_STATES_CODES_NH` (`nom_dpto`, para RV-SIS-01) | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_004` (vacío, o no corresponde al nombre del catálogo para `Cod_dpto`, sin distinguir mayúsculas ni tildes) | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 4, §4 RV-SIS-01, §10.1; `hu-sisben.md` R-13 |
| 5 | `Cod_mpio` (`COD_MPIO`) | `codMunicipioSisben` | Público | numérico, 5 dígitos DDMMM → texto (5) | Ninguna | `2_DANE_DIVIPOLA_CITIES_CODES_NH` (`cod_mpio`) | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_005`; `SISBEN_ERR_028` si su `cod_dpto` del catálogo difiere de `Cod_dpto` (RV-SIS-03) | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 5, §4, §9.2, §10.1; `hu-sisben.md` R-04, R-13 |
| 6 | `nom_mpio` (`NOM_MPIO`) | `nomMunicipio` | Público | alfanumérico, hasta 60 → texto | Estandarización de nombre (§10.2) | `2_DANE_DIVIPOLA_CITIES_CODES_NH` (`nom_mpio`, para RV-SIS-02) | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_006` (vacío o no corresponde al catálogo para `Cod_mpio`) | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 6, §4 RV-SIS-02, §10.1; `hu-sisben.md` R-13 |
| 7 | `cod_clase` (`ID_CLASE`) | `idClase` | Público | numérico, 1 dígito → entero | A número | `201_SISBEN_ZONES_NH` (`cod_clase`); dominio `1`, `2`, `3` | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_007` | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 7, §9.3, §10.1 |
| 8 | `ide_persona` (`ID_PERSONA_ORDEN`) | `idPersonaOrden` | **PII** | numérico, hasta 20 dígitos → texto | Ninguna; no se convierte a número | — | No; vacío no se valida | Inconsistencia registrada `SISBEN_INC_008` (sev. 3) | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 8, §10.1; `hu-sisben.md` R-09, R-28 |
| 9 | `ide_nacional` (`ID_NACIONAL`) | `idNacional` | **PII** | numérico, hasta 20 dígitos → texto | Ninguna; no se convierte a número | — | No; vacío no se valida | Inconsistencia registrada `SISBEN_INC_009` (sev. 3) | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 9, §10.1; `hu-sisben.md` R-09, R-28 |
| 10 | `pri_apellido` (`PRI_APELLIDO`) | `primerApellido` | **PII** | alfanumérico, hasta 30 → texto | Estandarización de nombre (§10.2); si queda vacío: nulo y advertencia `primerApellido:NO_NORMALIZABLE`, el registro sigue | — | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_010` (vacío o más de 30). Validation no restringe caracteres | Sí, prioridad 2 (parte de `standardizedIdName`) | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 10, §3.1, §10.1, §10.2; `hu-sisben.md` R-14, R-23, §5.3 |
| 11 | `seg_apellido` (`SEG_APELLIDO`) | `segundoApellido` | **PII** | alfanumérico, hasta 30 → texto | Estandarización de nombre (§10.2); con sev. 3 se publica estandarizado; si queda vacío: nulo y advertencia | — | No; vacío no se valida | Inconsistencia registrada `SISBEN_INC_011` (sev. 3, más de 30) | Sí, prioridad 2 (parte de `standardizedIdName`) | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 11, §10.1, §10.4; `hu-sisben.md` R-14, R-23 |
| 12 | `pri_nombre` (`PRI_NOMBRE`) | `primerNombre` | **PII** | alfanumérico, hasta 30 → texto | Estandarización de nombre (§10.2); si queda vacío: nulo y advertencia | — | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_012` (vacío o más de 30) | Sí, prioridad 2 (parte de `standardizedIdName`) | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 12, §3.1, §10.1; `hu-sisben.md` R-14 |
| 13 | `seg_nombre` (`SEG_NOMBRE`) | `segundoNombre` | **PII** | alfanumérico, hasta 30 → texto | Estandarización de nombre (§10.2); con sev. 3 se publica estandarizado | — | No; vacío no se valida | Inconsistencia registrada `SISBEN_INC_013` (sev. 3, más de 30) | Sí, prioridad 2 (parte de `standardizedIdName`) | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 13, §10.1, §10.4; `hu-sisben.md` R-14 |
| 14 | `tip_documento` (`TIP_DOCUMENTO`) | `tipoDocumento` (homologado) y `tipoDocumentoSisben` (original). Alimenta `standardizedIdType` y `currentPartyType` | **PII** | alfanumérico, hasta 3 (`1`–`8`, `PPT`) → entero (`tipoDocumento`) y texto (`tipoDocumentoSisben`) | `tipoDocumento` = `cod_experian` del catálogo 202 para `cod_sisben`; `tipoDocumentoSisben` = valor original | `202_SISBEN_PROCESS_ID_TYPE_HO` (`cod_sisben` → `cod_experian`) | Sí; vacío → rechazo del registro | `1` y `2` (menores): descartado sin error, no cuenta en RQS ni FQS. `7`: rechazo del registro `SISBEN_ERR_023` (`TIPO_PENDIENTE_CREACION`). Otro valor: rechazo `SISBEN_ERR_014`. Parte de la clave de duplicados (`OxygenDuplicate`: no se acepta) | Sí, prioridad 1 (`standardizedIdType`) | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 14, §4 RV-SIS-04, §5, §6, §9.4, §10.1; `hu-sisben.md` R-15 a R-17, R-19, §5.3 |
| 15 | `num_documento` (`NUM_DOCUMENTO`) | `numeroDocumento` (normalizado) y `numeroDocumentoOriginal` (original). Alimenta `standardizedIdNumber` y `currentPartyIdNumber` | **PII** | alfanumérico, hasta 15 medido después de normalizar → texto | Validation: quita espacios y ceros a la izquierda antes de medir. Curation: mayúsculas, sin espacios, sin ceros a la izquierda; `numeroDocumentoOriginal` = valor original | — | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_015` (vacío o más de 15); solo ceros: `SISBEN_ERR_029` (`DOCUMENTO_CERO`). Parte de la clave de duplicados | Sí, prioridad 1 (`standardizedIdNumber`) | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 15, §4 RV-SIS-05, §6, §10.1; `hu-sisben.md` R-18, R-19, §5.3 |
| 16 | `Grupo` (`ID_GRUPO`) | `grupoSisben` | Público | alfanumérico, 1 → texto (1) | Ninguna | `234_SISBEN_GROUP_SUBGROUP_NH` (`grupo`); dominio `A`–`D` | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_016`. La coherencia grupo–subgrupo no se valida | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 16, §9.5, §10.1; `hu-sisben.md` R-12 |
| 17 | `Nivel` (`ID_NIVEL`) | `nivelSisben` | Público | numérico, hasta 2 → entero | A número | — (rango 1–21; el catálogo 234 no se consulta) | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_017` | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 17, §10.1; `hu-sisben.md` R-12 |
| 18 | `Fec_digitacion` (`FECHA_DIGITACION`) | `fechaDigitacion` | Público | fecha como texto (`yyyy-MM-dd` o `dd/MM/yyyy`) → largo | EPOCH: ms UTC del inicio del día en `America/Bogota`; nulo si vacía o inválida, con advertencia `fechaDigitacion:<MOTIVO>` si no se pudo transformar | — | No; vacía es válida (fecha de encuesta) | Inconsistencia registrada `SISBEN_INC_018` (sev. 3: formato inválido o futura) | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 18, §10.1, §10.4; `hu-sisben.md` R-05, R-06, §5.1 |
| 19 | `fec_actualizacion_persona` (`FECHA_ACTUALIZACION_PERSONA`) | `fechaActualizacionPersona` | Público | fecha como texto → largo | EPOCH: ms UTC del inicio del día en `America/Bogota`; nulo si vacía o inválida | — | No; vacío no se valida | Inconsistencia registrada `SISBEN_INC_019` (sev. 3, formato). Sin control de fecha futura | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 19, §10.1; `hu-sisben.md` R-05, R-08, R-23 |
| 20 | `marca` (`ID_MARCA`) | `idMarca` | Público | numérico → entero | A número; si no es número, nulo y advertencia `idMarca:FORMATO_INVALIDO` | `203_SISBEN_RECORD_STATUS_AND_MARK_NH` (`codigo`); dominio `0`, `1`, `10`, `20` | No; vacío no se valida | Inconsistencia registrada `SISBEN_INC_020` (sev. 3). No filtra registros | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 20, §9.6, §10.1, §10.4; `hu-sisben.md` R-11, §5.3 |
| 21 | `Estado` (`ID_ESTADO`) | `idEstado` | Público | numérico → entero | A número | `203_SISBEN_RECORD_STATUS_AND_MARK_NH` (`codigo`); dominio `0`, `10`, `40`–`43`, `60`, `61`, `69`, `70`, `90`, `91` | Sí; vacío → rechazo del registro | Rechazo del registro `SISBEN_ERR_021` (incluye un estado textual). Todos los estados válidos se cargan | No | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 21, §9.6, §10.1; `hu-sisben.md` R-10, §5.1 |
| 22 | `fec_actualizacion_cns` (`FECHA_ACTUALIZACION_CNS`) | `fechaActualizacionCns` | Público | fecha como texto → largo | EPOCH: ms UTC del inicio del día en `America/Bogota`. Es la *As-Of-Date* (guard-rail `asOfDate` de Validation) | — | Sí; vacía → rechazo del registro | Rechazo del registro `SISBEN_ERR_022` (vacía, formato inválido o futura). Decide cuál duplicado se conserva | No (criterio del duplicado vigente, no de identidad) | Sin handler propio: LFP por defecto | `[C]` | `spec-sisben.md` §3 fila 22 y guard-rails, §6, §10.1; `hu-sisben.md` R-07, R-19, §5.3 |

## Campos que agrega el proceso (`07` §2.1)

No vienen en el archivo. La spec declara que §10.1 es la lista completa del evento curado: 24 claves salen de los 22 campos de origen y 10 las agrega el proceso. No hay campos internos adicionales.

| Campo curado | Público / PII | Tipo | Regla | Estado | Fuente |
|---|---|---|---|---|---|
| `fechaCorte` | Público | largo | Epoch ms del último día del periodo `AAAAMM` del nombre del archivo | `[C]` | `spec-sisben.md` §10.1; `hu-sisben.md` §5.3 |
| `tipoArchivo` | Público | texto | Constante `REGULAR` | `[C]` | `spec-sisben.md` §10.1 |
| `standardizedIdType` | **PII** | entero | = `tipoDocumento` | `[C]` | `spec-sisben.md` §10.1, §11 |
| `standardizedIdNumber` | **PII** | texto | = `numeroDocumento` | `[C]` | `spec-sisben.md` §10.1, §11 |
| `standardizedIdName` | **PII** | texto | `primerNombre segundoNombre primerApellido segundoApellido` ya curados, sin nulos ni vacíos, separados por un espacio; nulo si todos faltan | `[C]` | `spec-sisben.md` §10.1; `hu-sisben.md` R-24, §5.3 |
| `currentPartyType` | **PII** | entero | = `tipoDocumento` | `[C]` | `spec-sisben.md` §10.1 |
| `currentPartyIdNumber` | **PII** | texto | = `numeroDocumento` | `[C]` | `spec-sisben.md` §10.1 |
| `subscriberBlockStatus` | Público | texto | Constante `NO_BLOQUEADO` | `[C]` | `spec-sisben.md` §10.1 |
| `subscriberBlockReason` | Público | texto | Constante nula | `[C]` | `spec-sisben.md` §10.1 |
| `advertenciasCuracion` | Público | lista de texto | Una advertencia por campo, `<campo curado>:<MOTIVO>`, sin el valor. Motivos: `NO_NORMALIZABLE`, `FORMATO_INVALIDO`, `NO_HOMOLOGABLE`, `HOMOLOGACION_PENDIENTE`, `ARCHIVO_NO_RECONOCIDO`; `registro:REGISTRO_ILEGIBLE` | `[C]` | `spec-sisben.md` §10.1, §10.4 |

`subscriberRelation`: en Validation es `NUM_DOCUMENTO` (guard-rail); Curation la reemplaza por `<standardizedIdType>_<standardizedIdNumber>`. `[C]` (`spec-sisben.md` §3 guard-rails, §10.1)

## Notas por campo

| Fila | Nota | Estado | Fuente |
|---|---|---|---|
| 1, 2, 8, 9 | Identificadores numéricos de hasta 20 dígitos que Curation publica como texto, sin convertir | `[C]` | `hu-sisben.md` R-09; `spec-sisben.md` §10.1 |
| 3 a 6 | DIVIPOLA: los códigos son los reales del DANE; los nombres van en mayúsculas sin tildes, como los escribe el DNP. En pruebas, 6 departamentos y 6 municipios; en producción, el catálogo completo desde Collibra | `[C]` | `spec-sisben.md` cabecera «Datos», §9, §9.1, §9.2 |
| 10 a 13 | Los catálogos 111 y 103 de nombres estandarizados que lista `07` §3 no se usan: la spec define la estandarización como reglas (§10.2) | `[C]` | `spec-sisben.md` §10.2 |
| 14 | La homologación SISBEN → Experian explica el «rechazo silencioso de menores, tipos 7 y 10» de `07` §5.3: son los tipos Experian de los códigos SISBEN `2` y `1`. No es una contradicción | `[C]` | `spec-sisben.md` §9.4; `07` §5.3 |
| 14 | El tipo Experian del salvoconducto no existe (`cod_experian` vacío): los registros con tipo `7` se rechazan hasta que se cree | `[C]` | `hu-sisben.md` R-17; `spec-sisben.md` §9.4; `07` §5.7 |
| 18, 22 | Ninguna fecha llega como número: `Fec_digitacion` es texto con formato de fecha. Esto responde el caso histórico de `04` §5.1 | `[C]` | `hu-sisben.md` R-05 |
| 18 | Una fecha futura con formato válido es inconsistencia pero se puede transformar: se publica (riesgo RG-007 del expediente) | `[C]` | `spec-sisben.md` §10.4 regla 1; `hu-sisben.md` R-06 |
| 19 | Lectura conjunta de HU R-08 y spec §10.1: «se toma como llega» (R-08, bloque de Validation) significa sin control de fecha futura; la conversión a epoch de Curation (R-23, §10.1) aplica igual | `[C]` | `hu-sisben.md` R-08, R-23; `spec-sisben.md` §10.1 |
| 20, 21 | `marca` y `Estado` comparten el catálogo 203; el dominio de cada campo restringe qué códigos acepta | `[C]` | `spec-sisben.md` §9.6 |
| Todas | Ninguna fila depende de una pregunta abierta: PA-001 está resuelta y PA-002 trata el filtro del corte vigente en State Calculation, no un campo | `[C]` | `contexto-ingesta.md` §6 |

## Uso aguas abajo

- El **nombre curado** de esta tabla es el que `source-spec-curation.yaml` usará como `campos_curados[].destino` y el que `contrato-curation-pinning.md` y los `yaml-skill-*.yaml` deben citar **exactamente** (steering `11` §6 #5).
- Los valores PII de esta tabla son nombres de campo y reglas, nunca valores reales.

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Motivo exacto de la advertencia de una fecha que no se puede transformar (la spec da el formato y la lista de motivos, no el motivo por campo de fecha) | `[PM]` — lo fija el insumo de Curation desde §10.1; no cambia tipo, efecto ni clasificación |
| 2 | Aprobador designado del patrón | `[A:OQ-06]` |

## Pie de trazabilidad

- Layout y reglas por campo: `spec-sisben.md` §3, §3.1, §4, §5, §6, §8. Catálogos: §9. Campos curados y transformaciones: §10.1, §10.2, §10.4. Identidad: §11. Synthesis: §12.
- Reglas de negocio: `hu-sisben.md` R-04 a R-28 y escenarios §5.1, §5.3.
- Dimensiones obligatorias: `07` §3. Efectos de la violación: `07` §5.1.
- Última verificación de referencias: 2026-11-24.
