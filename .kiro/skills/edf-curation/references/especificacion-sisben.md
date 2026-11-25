# Especificación SISBEN V4 para Curation

> **Histórico (HF-40, AUD-C-07).** Describe el SISBEN del piloto con el que se armó el ejemplo de la skill
> (`assets/examples/sisben/`): requirements y HU-SISBEN anteriores a la spec 1.0.2 del expediente. Varios
> valores ya no son los vigentes (prefijos `RT_`/`EP_` y `(\.txt)?`, Ñ → N, «PEP sin letras», «PPT original»,
> «pasaporte conserva ceros», delimitador `,`, columnas `tipo_sisben`/`tipo_experian`). **No es fuente** para
> ninguna ingesta: los valores salen del `source-spec-curation.yaml` aprobado y de la spec del expediente.

Valores prellenados en `assets/examples/sisben/source-spec.sisben.yaml`. Estados: **cerrado**,
**propuesta de frontera**, **inferido**, **supuesto** (sale con `ASSUMPTION:`), **abierto** (no se
genera).

## Identidad y archivo

| Atributo | Valor | Estado |
|---|---|---|
| Fuente / datasource / subscriber | SISBEN / DNP / 999722 | cerrado |
| `outputEventType` | 30 | inferido (CUR-PA04) |
| Nombre | `[RT_|EP_]SISBEN_EXPERIAN_AAAAMM.AAAAMMDDHHMMSS±ZZZZ.txt` | propuesta de frontera (D8, D8-a) |
| `filePattern` | `.*(RT_|EP_)?SISBEN_EXPERIAN_\d{6}\.\d{14}[+-]\d{4}(\.txt)?` | propuesta (F-07); termina en `(\.txt)?` porque el motor compara la ruta del dataset sin extensión y el evento por `lineage.fileName` |
| Tipo de archivo | REGULAR / RT / EP por prefijo | propuesta (CUR-PA03) |
| Fecha de corte | último día del mes `AAAAMM`, 00:00, epoch ms | propuesta (IN-02) |
| Entrada | aceptados de Acceptance (ruta en disputa, N1) | abierto |
| Salida | `curatedDir/flow` y `curatedDir/stock`; texto en QA/STG, Avro en PRO | documentado |

## Campos de entrada (22, `validatedRecordJson`)

`ID_FICHA_ORIGEN, ID_HOGAR, COD_DPTO, NOM_DPTO, COD_MPIO, NOM_MPIO, ID_CLASE, ID_PERSONA_ORDEN,
ID_NACIONAL, PRI_APELLIDO, SEG_APELLIDO, PRI_NOMBRE, SEG_NOMBRE, TIP_DOCUMENTO, NUM_DOCUMENTO,
ID_GRUPO, ID_NIVEL, FECHA_DIGITACION, FECHA_ACTUALIZACION_PERSONA, ID_MARCA, ID_ESTADO,
FECHA_ACTUALIZACION_CNS`. SUPUESTO: las claves del JSON son estos nombres de dominio.

Códigos de Validation por campo (D12): `SISBEN_ERR_0xx` (severidad 1) y `SISBEN_INC_011/013/019/020`
(severidad 3); `SISBEN_ERR_023` salvoconducto (F-08). Un evento aceptado **no** trae errores de
severidad 1 (Acceptance los rechaza), pero sí puede traer severidad 3.

## Transformaciones (RF-CUR-04..10)

| ID | Regla | Implementación |
|---|---|---|
| RF-CUR-04 | Nombres → mayúsculas, sin tildes ni especiales | `nombre`: NFD, quita diacríticos, `[^A-Z ]` → espacio, espacios simples. Ñ → N (SUPUESTO C-CUR-03, `conservar_enie`) |
| RF-CUR-05 | Tipos de dato | `convertir` a texto / entero / largo / decimal / booleano |
| RF-CUR-06 | Fechas → epoch `Long` | `dd/MM/uuuu` y `uuuu-MM-dd HH:mm:ss`, estrictos; zona `UTC` (C-CUR-04) |
| RF-CUR-07 | Homologación `TIP_DOCUMENTO` persistida | tabla de abajo; el original se conserva en `tipoDocumentoSisben` |
| RF-CUR-08 | Número de documento | sin espacios, sin ceros a la izquierda; PEP sin letras; PPT original; pasaporte conserva ceros (SUPUESTO); padding sin dato |
| RF-CUR-09 | DANE con padding | `COD_DPTO` a 2, `COD_MPIO` a 5 |
| RF-CUR-10 | Severidad 3 → no se almacena; 0 → se anula | sev. 3: la clave se omite del JSON; sev. 0: valor `null` (hoy Validation no emite sev. 0, F-10) |

Valor no transformable → `null` + entrada `campo:MOTIVO` en `advertenciasCuracion` (D13). Nunca
excepción.

## Homologación `TIP_DOCUMENTO` SISBEN → Experian

| SISBEN | Experian | Descripción |
|---|---|---|
| 1 | 10 | Registro civil (menor, filtrado en Validation) |
| 2 | 7 | Tarjeta de identidad (menor, filtrado en Validation) |
| 3 | 1 | Cédula de ciudadanía y NUIP |
| 4 | 4 | Cédula de extranjería |
| 5 | 8 | DNI extranjero |
| 6 | 5 | Pasaporte |
| 7 | **pendiente de creación** | Salvoconducto: `null` + `HOMOLOGACION_PENDIENTE`; nunca un código arbitrario |
| 8 | 9 | PEP |
| PPT | 11 | Permiso de Protección Temporal |

Validation la **consulta**; Curation la **persiste**. Fuente en el processor: lookup
`202_SISBEN_PROCESS_ID_TYPE_HO` (consultado una vez por ejecutor); respaldo: tabla embebida en
`SISBENCurationSpec` (misma tabla).

## Los 7 lookups

| Tabla | Uso | Registrada en Curation |
|---|---|---|
| `SISBEN_HOME_ID_CLASIFICATION_NH` | `ID_HOGAR` | No (Validation) |
| `102_DANE_DIVIPOLA_STATES_CODES_NH` | `COD_DPTO`/`NOM_DPTO` | No |
| `2_DANE_DIVIPOLA_CITIES_CODES_NH` | `COD_MPIO`/`NOM_MPIO` | No |
| `201_SISBEN_ZONES_NH` | `ID_CLASE` | No |
| `202_SISBEN_PROCESS_ID_TYPE_HO` | `TIP_DOCUMENTO` | **Sí** (homologación) |
| `SISBEN_GROUP_SUBGROUP_NH` | `ID_GRUPO`/`ID_NIVEL` | No |
| `203_SISBEN_RECORD_STATUS_AND_MARK_NH` | `ID_MARCA`/`ID_ESTADO` | No |

Columnas, archivo, clave y delimitador: SUPUESTO (F-12). Muestras sintéticas en
`assets/examples/sisben/fixture/lookups/` (DIVIPOLA no verificada contra DANE, F-13).

## Campos publicados (Capa B)

| Publicado | Origen | Transformación | Tipo | Estado |
|---|---|---|---|---|
| `codDepartamentoSisben` | `COD_DPTO` | padding 2 | texto | propuesta (D11) |
| `codMunicipioSisben` | `COD_MPIO` | padding 5 | texto | propuesta (D11) |
| `grupoSisben` | `ID_GRUPO` | original | texto | propuesta (D11) |
| `nivelSisben` | `ID_NIVEL` | — | entero | propuesta (D11) |
| `tipoDocumento` | `TIP_DOCUMENTO` | homologación | entero | cerrado |
| `tipoDocumentoSisben` | `TIP_DOCUMENTO` | original | texto | cerrado (RF-CUR-07) |
| `numeroDocumento` | `NUM_DOCUMENTO` | documento | texto | supuesto |
| `primerApellido`, `segundoApellido`, `primerNombre`, `segundoNombre`, `nomDepartamento`, `nomMunicipio` | campos de nombre | nombre | texto | supuesto (Pinning espera otros nombres, HU-006) |
| `idFichaOrigen`, `idHogar`, `idClase`, `idPersonaOrden`, `idNacional`, `idMarca`, `idEstado` | homónimos | tipo | texto/entero/largo | supuesto |
| `fechaDigitacion`, `fechaActualizacionPersona`, `fechaActualizacionCns` | fechas | epoch ms | largo | supuesto |
| `fechaCorte`, `tipoArchivo` | nombre de archivo | derivación | largo/texto | propuesta |
| `currentPartyType`, `currentPartyIdNumber` | copia de tipo y número | — | entero/texto | inferido |
| `subscriberBlockStatus` / `subscriberBlockReason` | constante `NO_BLOQUEADO` / `null` | — | texto | supuesto / inferido |
| `advertenciasCuracion` | — | lista de `campo:MOTIVO` | lista de texto | supuesto (nombre, CUR-PT05) |

**No se generan** (abiertos): `puntajeSisben`, `fechaEncuesta`, `fechaModificacion` (D11-a: sin
origen en el layout de 22), `nombreCompleto` (P2), `businessLineCode`, `typeOfData` (CUR-PT06).

## Guard-rails y borrados

`asOfDate`, `subscriber`, `lineage` y `subscriberRelation` (= `NUM_DOCUMENTO`) se copian del evento
aceptado; el processor no los sobrescribe (D3), salvo `subscriberRelation` si la spec declara
`extension_subscriber_relation` (D14). Eventos `inferredAsDeleted` los cura el
`InferredDeletedCurationProcessor` del motor (RF-CUR-11); RT/EP de borrado se deciden en Acceptance
(D2).
