# Interpretar la salida

## Estructura de un evento curado

El motor escribe `TaggedCuratedEventWithMap` en `curatedDir/<runMode>/<dataset>/`:

| Campo | De dónde sale |
|---|---|
| `lineage`, `eventId`, `asOfDate`, `subscriber`, `fileTag`, `inferredAsDeleted` | copiados del evento aceptado |
| `subscriberRelation` | por defecto el del aceptado (`NUM_DOCUMENTO`, sin normalizar); con `extension_subscriber_relation` en la spec (D14), los campos curados indicados unidos por el separador, y el del aceptado si falta alguno |
| `eventType` | `outputEventType` = 30 |
| `payload` | `enrichEventWithPayload` = payload del aceptado |
| `curatedRecordJson` | `SISBENCuratedEvent.write` → mapa con los nombres publicados |
| `qualityScores`, `errorCodes` | solo si `includeQualityCodes` / `includeErrorCodes` (claves de etapa, SUPUESTO) |

## Lectura de `curatedRecordJson`

- Valor presente: transformado y convertido al tipo publicado.
- `null` sin advertencia: el dato venía vacío, o se anuló por severidad 0, o es una constante nula.
- `null` con `campo:MOTIVO` en `advertenciasCuracion`: no se pudo transformar.
- Clave ausente: severidad 3 en el campo de origen → no se almacena (RF-CUR-10).

| Motivo | Significado | Acción típica |
|---|---|---|
| `FORMATO_INVALIDO` | no cumple formato/tipo (fecha imposible, no numérico, código DANE largo) | revisar regla en Validation: debería haberse diagnosticado |
| `NO_HOMOLOGABLE` | tipo de documento fuera de la tabla | revisar catálogo 202 |
| `HOMOLOGACION_PENDIENTE` | salvoconducto (7) sin código Experian | esperado mientras siga pendiente; no debería llegar aceptado (`SISBEN_ERR_023`) |
| `NO_NORMALIZABLE` | queda vacío tras normalizar (nombre solo con símbolos, documento solo ceros) | revisar el dato fuente |
| `ARCHIVO_NO_RECONOCIDO` | `lineage.fileName` no permite derivar tipo/fecha de corte | revisar convención de nombre |
| `REGISTRO_ILEGIBLE` | `validatedRecordJson` no es un objeto JSON | revisar Validation |
| `ERROR_INTERNO` | fallo inesperado capturado; registro vacío | bug del processor: reportar con `eventId` |

Las advertencias nunca contienen valores del registro (CUR-RC06).

## Conciliación de conteos

`aceptados = curados + descartados_sin_processor + fallidos_por_excepción`. Con el processor
generado, `fallidos_por_excepción` debe ser 0. Si `curados < aceptados`, el motor descartó en
silencio (CUR-AP01/AP02): revisar el patrón contra `lineage.fileName` de los faltantes.

## Ejemplo sintético (SISBEN)

`assets/examples/sisben/expected/resumen.json` y `curated-events.json`, calculados por la
implementación de referencia sobre 8 eventos aceptados sintéticos:

| Evento | Caso | Resultado esperado |
|---|---|---|
| evt-0001 | Regular, CC | `tipoDocumento` 1, `tipoDocumentoSisben` "3", documento sin espacios ni ceros, `codDepartamentoSisben` "05", `codMunicipioSisben` "05001", nombres en mayúsculas sin tildes, `fechaCorte` = 2024-01-31 |
| evt-0002 | PPT | `tipoDocumento` 11; documento original con ceros |
| evt-0003 | PEP | `tipoDocumento` 9; documento sin letras ni ceros |
| evt-0004 | sev. 3 en `SEG_APELLIDO` | clave `segundoApellido` ausente; `tipoDocumento` 4 |
| evt-0005 | RT, fecha imposible | `tipoArchivo` RT; `fechaActualizacionPersona` null + advertencia |
| evt-0006 | EP sin extensión, `ID_HOGAR` no numérico | `tipoArchivo` EP; `idHogar` null + advertencia; pasaporte conserva ceros |
| evt-0007 | salvoconducto (defensivo) | `tipoDocumento` null + `HOMOLOGACION_PENDIENTE` |
| evt-0008 | otro archivo | no se cura: el motor no encuentra processor (descarte sin log) |

Resumen: 8 de entrada, 7 curados, 1 descartado por el motor, 3 con advertencias, 1 con clave omitida.
Tras correr en el fork con el mismo fixture, comparar `curatedRecordJson` evento a evento con
`curated-events.json` (los tests `SISBENCurationProcessorSpec` lo hacen sin Spark).
