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
| D14 | `subscriberRelation` por extensión de la spec (E2E `sisben-e2e`, PA-019 `[P]`: `standardizedIdType_standardizedIdNumber`) | bloque opcional `extension_subscriber_relation {campos, separador}`: el processor arma `subscriberRelation` con esos campos curados y, si falta alguno, conserva el del evento aceptado y deja `subscriberRelation:COMPONENTE_FALTANTE` en las advertencias (AUD-C3). No se devuelve `""`: el `doesMatch` base de Pinning une dos registros con igual `subscriber` y `subscriberRelation`, y `nearNeighborKeys` bloquea por `subscriber:subscriberRelation` (`IdentityMatcher.scala:55`); un vacío uniría personas distintas, el aceptado es único por persona. Sin el bloque, se aplica D3. Firma del gancho verificada en la copia del motor: `CurationProcessor.subscriberRelation(taggedAcceptedEvent): String` (`oxygen-curation/.../engines/CurationProcessor.scala:59`), invocada por evento en `Curator.scala:89` y `:179` (AUD-C8). Falta compilar contra el JAR (F-01) | propuesta, firma verificada en la copia del motor |
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
| C-CUR-05 | Severidad 3 = clave omitida (política por defecto del ejemplo); nulos escritos como `null`, salvo las claves de identidad de `parametros.omitir_si_nulas` (por defecto `standardizedId*`), que se omiten si son nulas: ausentes, nunca `null` (AUD-C4). Desde la 1.7.0 la política de severidad la fija la spec de la ingesta (`niveles_no_almacenar`; SISBEN publica estandarizado, spec 1.0.1 §10.4) | `CuratedEvent.toJavaMap` | confirmar con Synthesis |
| C-CUR-06 | Domain dictionary: verificado en la copia del motor. La versión 2 (la del GDD de Colombia) se lee con `DomainDictionaryV2.java` (Jackson): por campo `name`, `handler` (LFP), `periodicity`, `complexTypeDefinition`, `dataType` (enum `Datatype`, STRING por defecto), `synthesizer`, `description`, `section`, `enabled`. `fieldType` solo existe en la versión 1 (`DictionaryFieldDeser`, `DomainDictionaryInternal.scala:133-136`). El fragmento lleva por campo solo `name` y `description` (AUD-C1). `dataType`: **verificar en el fork** (el enum está en oxygen-global, fuera del repo; un valor inválido rompe todo el diccionario); hoy el motor usa STRING. `handler` lo fija Synthesis | `domain-dictionary.json.tpl` | valores de `Datatype` en el fork |
| C-CUR-07 | Claves de `validatedRecordJson` = nombres de dominio | `SISBENAcceptedRecord` | ver salida real de Validation |
| C-CUR-08 | Reglas de documento por tipo (pasaporte conserva ceros, sin padding) | `reglas_documento` | longitudes estándar Experian |
| C-CUR-09 | API `OxygenLookup.get(tabla, Map)` + respaldo a tabla embebida. Desde AUD-C6 se fusionan: gana el lookup y la tabla embebida completa las claves sin respuesta | processor | CUR-PT02 |
| C-CUR-10 | `subscriberBlockStatus = NO_BLOQUEADO` como literal | `campos_curados` | catálogo de estados de bloqueo |
| C-CUR-11 | `fechaCorte` a las 00:00 del último día del mes | `infoArchivo` | confirmar con Synthesis |

## Correcciones de auditoría (2026-11-24)

Evidencia en la copia del motor (`Motor-Oxigen/`, solo lectura). Toda corrección cambia igual la referencia Python
y la plantilla Scala.

| ID | Hallazgo | Corrección | Fecha |
|---|---|---|---|
| AUD-C1 | El fragmento del domain dictionary emitía `type`/`nullable`, que el motor no lee: todo campo quedaba STRING/LFP en silencio | Cada campo lleva solo `name` y `description` (texto con el tipo de salida). Sin `fieldType` (solo versión 1), sin `dataType` (enum fuera del repo, ver C-CUR-06) y sin `handler` (lo fija Synthesis). Evidencia: `DomainDictionaryV2.java:45-56`, `DomainDictionaryInternal.scala:56-63, 114-159` | 2026-11-24 |
| AUD-C2 | `curar` tragaba cualquier fallo sin log; `m.group(2)` fuera del `Try` lanzaba por registro con un regex de menos de 2 grupos | El `catch` registra `eventId` y la clase de la excepción, nunca valores (`SILENCIO-INTENCIONAL D13`); `infoArchivo` con guarda de `groupCount` y `m.group(2)` dentro del `Try`; `validar_coherencia` exige que `regex_derivacion` compile y tenga 2 grupos | 2026-11-24 |
| AUD-C3 | Con `extension_subscriber_relation` y un componente faltante, el valor caía al aceptado sin rastro | Se conserva el aceptado (ver D14: un vacío uniría personas en Pinning) y el registro lleva `subscriberRelation:COMPONENTE_FALTANTE`. Evidencia: `oxygen-pinning/.../util/IdentityMatcher.scala:55` | 2026-11-24 |
| AUD-C4 | Las claves de identidad nulas salían como `null`; los matchers hacen `.get.toString` (`ISIdentityMatcher.scala:46-54`) | `parametros.omitir_si_nulas` (por defecto `standardizedId*`): clave ausente si es nula. Pinning recibe `""` por una clave ausente (`IdentityMatcher.scala:46-49`). El ejemplo SISBEN no cambia | 2026-11-24 |
| AUD-C5 | La referencia Python leía `true` como `"True"` y arreglos/objetos como repr de Python; `errores` nulo lanzaba en Scala | La referencia imita a Gson (`comoTexto`): booleanos en minúsculas, JSON compacto con los números tal como vinieron, número de primer nivel como `BigDecimal` plano. Las dos toleran `errores` nulo y un `errorCode` nulo. Casos Scala del lector y de Capa A derivados de la referencia. Diferencia residual: Gson es *lenient* (comillas simples, claves sin comillas); la referencia es estricta | 2026-11-24 |
| AUD-C6 | Un lookup de homologación parcial reemplazaba la tabla embebida: los códigos faltantes salían `NO_HOMOLOGABLE` | Fusión: gana el lookup, la tabla embebida completa; `warn` con el número de claves sin respuesta. El `Try` restante es `SILENCIO-INTENCIONAL C-CUR-09`. La referencia Python usa solo la tabla embebida (equivale a un lookup igual a la tabla de la HU) | 2026-11-24 |
| AUD-C7 | Un código de error con nivel nulo conservaba el campo sin rastro | Se conserva el campo y se agrega `<campo curado>:NIVEL_DESCONOCIDO`, uno por destino (HF-53; antes, con el campo de entrada) | 2026-11-24 |
| AUD-C8 | D14 decía firma del gancho sin verificar | Verificada en `CurationProcessor.scala:59` y `Curator.scala:89, 179` (ver D14) | 2026-11-24 |
| AUD-C9 | `SourceProcessorSpec` prueba el núcleo, nunca `curateEvent` ni el gancho `subscriberRelation` (necesitan el JAR) | Prueba del lado del fork: `runbook-ejecucion.md` §2 y `checklist-entrega.md` | 2026-11-24 |

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
