# Decisiones, supuestos y puntos abiertos que aplican a Curation

Precedencia: decisiones confirmadas > HU > entendimiento funcional > reglas EDF > código Oxygen >
sesiones; **para el comportamiento técnico del motor, el código Oxygen gana**. Una discrepancia
documental se señala; el skill no la resuelve.

## Decisiones confirmadas o de frontera

| ID | Decisión | Efecto en el skill | Estado |
|---|---|---|---|
| D13 | Valor nulo + campo de advertencias; sin excepciones | `Resultado`, `advertenciasCuracion`, `NonFatal` | cerrada |
| D11 | 4 campos curados con equivalente, 3 abiertos | `grupoSisben`, `nivelSisben`, `codMunicipioSisben`, `codDepartamentoSisben`; los otros no se generan | propuesta, con abiertos |
| D3 | Guard-rails `NUM_DOCUMENTO`, `FECHA_ACTUALIZACION_CNS`, 999722, DNP | se copian; `subscriberRelation` no se sobrescribe, salvo que la spec declare D14 | propuesta |
| D14 | `subscriberRelation` por extensión de la spec (E2E `sisben-e2e`, PA-019 `[P]`: `standardizedIdType_standardizedIdNumber`) | bloque opcional `extension_subscriber_relation {campos, separador}`: el processor arma `subscriberRelation` con esos campos curados y, si falta alguno, conserva el del evento aceptado. Sin el bloque, se aplica D3. La firma del gancho `CurationProcessor.subscriberRelation(event)` no se pudo verificar contra el JAR (F-01) | propuesta, firma sin verificar |
| D2 | Borrado RT/EP en Acceptance | Curation solo cura los borrados que recibe | propuesta |
| D8 / D8-a | Nombre con marca de tiempo; prefijo RT_/EP_ se conserva | `filePattern`, tipo de archivo, fecha de corte | propuesta |
| D10 | `TIP_DOCUMENTO` alfanumérico (PPT) | homologación sobre texto | cerrada |
| D12 | Códigos `SISBEN_ERR/INC_0xx` | mapa código → campo para RF-CUR-10 | cerrada |
| RF-CUR-12 | FLOW periodo / STOCK regeneración | no se emite `runMode` | cerrada |

## Faltantes F-01..F-17 que aplican

| ID | Tema | Supuesto aplicado en Curation |
|---|---|---|
| F-01 | JAR / Artifactory no disponibles | Compilación **no certificada**. Verificación posible: tests Python + parse-check HOCON; `TransformacionesSpec` compila solo con Scala + ScalaTest |
| F-02 | Credencial expuesta en `settings-artifactory.xml` | Nunca se lee, copia ni referencia salvo para prohibirlo; pedir rotación |
| F-03 | Claves HOCON | `configs[] {filePattern, processor}`, `runMode`, `paths`, `partitionSize`, `enableSSRWrite` verificadas; `includeQualityCodes`, `includeErrorCodes`, `excludes`, `INCSYNC` siguen SUPUESTO (no se generan) |
| F-05 | Fork `bureau.colombia_innersource` sin acceso | Capa A propia y aislada en `...curation.source.common` (también CUR-PT01) |
| F-07 | Nombre RT/EP | patrón de D8; tipo por prefijo |
| F-08 | Código del salvoconducto | `SISBEN_ERR_023` (sev. 1) → no debería llegar aceptado; defensivo: `HOMOLOGACION_PENDIENTE` |
| F-10 | Severidad 0 | no se emite hoy; la regla de anulación queda implementada por si aparece |
| F-12 | Estructura de los 7 lookups | columnas, archivo, clave y delimitador supuestos; CSV sintéticos |
| F-13 | DIVIPOLA oficial | muestra pequeña no verificada contra DANE |
| F-17 | UTF-8 | archivos y lookups en UTF-8 |

F-04, F-06, F-09, F-11, F-14, F-15, F-16 son de Validation/Acceptance: no cambian el código de
Curation (sus efectos llegan ya resueltos en el evento aceptado).

## Supuestos propios de Curation (C-CUR)

| ID | Supuesto | Dónde | Cómo cerrarlo |
|---|---|---|---|
| C-CUR-01 | Campo de advertencias se llama `advertenciasCuracion` y es lista de `campo:MOTIVO` | `parametros.campo_advertencias` | CUR-PT05 / IN-08 con Synthesis y reportes |
| C-CUR-02 | Nombres publicados camelCase en español para los campos sin nombre acordado | `campos_curados` | HU-006 / Pinning (`APELLID1`, `NOMBRES1`...) |
| C-CUR-03 | La Ñ se translitera a N | `parametros.conservar_enie` | confirmar con Pinning |
| C-CUR-04 | Fechas curadas en UTC (RF-CUR-06) aunque `asOfDate` de Validation usa America/Bogota | `parametros.zona_horaria_fechas` | confirmar; cambiar el parámetro y regenerar |
| C-CUR-05 | Severidad 3 = clave omitida (política por defecto del ejemplo); nulos escritos como `null`. Desde la 1.7.0 la política la fija la spec de la ingesta (`niveles_no_almacenar`; SISBEN publica estandarizado, spec 1.0.1 §10.4) | `CuratedEvent.toJavaMap` | confirmar con Synthesis |
| C-CUR-06 | Formato del domain dictionary `{source, eventType, fields[{name,type,nullable}]}` | `domain-dictionary.json.tpl` | ver el archivo real del inner |
| C-CUR-07 | Claves de `validatedRecordJson` = nombres de dominio | `SISBENAcceptedRecord` | ver salida real de Validation |
| C-CUR-08 | Reglas de documento por tipo (pasaporte conserva ceros, sin padding) | `reglas_documento` | longitudes estándar Experian |
| C-CUR-09 | API `OxygenLookup.get(tabla, Map)` + respaldo a tabla embebida | processor | CUR-PT02 |
| C-CUR-10 | `subscriberBlockStatus = NO_BLOQUEADO` como literal | `campos_curados` | catálogo de estados de bloqueo |
| C-CUR-11 | `fechaCorte` a las 00:00 del último día del mes | `infoArchivo` | confirmar con Synthesis |

## Puntos abiertos (bloquean partes concretas)

| ID | Tema | Bloquea |
|---|---|---|
| D11-a | Origen de `puntajeSisben`, `fechaEncuesta`, `fechaModificacion`; nombres de Pinning | esos campos (no se generan) |
| P2 / CUR-PA01 | Orden del nombre completo | en el ejemplo, `nombreCompleto` (no se genera). Desde la 1.7.0 el generador tiene `concatenacion`: cuando la spec de la ingesta fija el orden (SISBEN: `standardizedIdName`, spec 1.0.1 §10.1), el campo se genera y el punto queda cerrado para esa ingesta |
| CUR-PA02 / CUR-PT04 | Lector propio vs librería de Validation | se asume lector propio |
| CUR-PA03 | Tipo de archivo desde `lineage.fileName` | inferencia implementada |
| CUR-PA04 | `outputEventType = 30` | inferido |
| CUR-PA05 | HU-005 usa nombres del layout antiguo de 17 campos | se usa el layout de 22 |
| CUR-PT01 | Ubicación del código compartido | se propone `...curation.source.common` |
| CUR-PT06 | Business Line Code, Type of Data sin valores | `TODO(manual)` |
| CUR-PT07 | Carpetas del fork para Curation | se replica la estructura de Validation |
| N1 | Ruta de aceptados en disputa entre HU-004 y HU-005 | `acceptedDir` del ambiente |

## Discrepancias registradas (no resueltas por el skill)

- `ID_FICHA_ORIGEN` obligatorio en unas fuentes y opcional en otras (C19/C34): Curation lo publica
  como texto nullable.
- La sesión Curation/Pinning describe la homologación en otro sentido; rige la tabla de la HU.
- `Curation-Patterns.md` reporta cifras que el código desmiente; se sigue el código.
