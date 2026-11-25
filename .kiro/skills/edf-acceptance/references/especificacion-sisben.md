# Especificación SISBEN V4 para Acceptance · EJEMPLO HISTÓRICO

> **Histórico, no es la spec de ninguna ingesta (HF-19, 1.9.5; igual que AUD-V-13 en `edf-validation`).** Esta
> página explica el ejemplo de forma `assets/examples/sisben/source-spec.sisben.yaml` tal como se escribió en la
> versión 0.1.0 de la skill. Difiere de la spec SISBEN vigente: aquí `RT_`/`EP_` son tipos válidos, el
> `filePattern` no acepta `TXT`, los códigos son `SISBEN_ERR_001…` y se supone `inferDelete`. En una ingesta
> mandan su HU y su spec (`expedientes/<id>/fuentes/`) a través del insumo aprobado de `edf-analisis`; esta página
> no se cita como fuente ni fija precedencias (Paso 1 de `SKILL.md`). Para el comportamiento del motor, el código
> gana.

## Identidad (contexto)

`Fuente = SISBEN`, `fuente = sisben`, Datasource `DNP`, Subscriber `999722`, `eventType = 30`
(inferido), zona horaria `America/Bogota`, epochs en milisegundos.

## IN-01 Nombre de archivo (D8, propuesta de frontera)

- Convención: `[RT_|EP_]SISBEN_EXPERIAN_AAAAMM.AAAAMMDDHHMMSS±ZZZZ.txt` (tipo por prefijo: sin prefijo = REGULAR, `RT`, `EP`; su semántica de borrado depende de D2).
- `filePattern` (valor lógico): `.*(RT_|EP_)?SISBEN_EXPERIAN_\d{6}\.\d{14}[+-]\d{4}\.txt`
- En HOCON (lo escapa la plantilla): `".*(RT_|EP_)?SISBEN_EXPERIAN_\\d{6}\\.\\d{14}[+-]\\d{4}\\.txt"`
- Debe coincidir **entero** con: (a) la ruta del validado sin su extensión (`….txt.900001` → `….txt`;
  con marcador `….txt.errorInHeader.900003` → `….txt`) y (b) `lineage.fileName` (`….txt`).
- Los dos patrones de HU-004 §7 y de la descripción de la etapa no coinciden entre sí y el segundo
  (`.*/` con `^`) nunca coincide (ACC-PA02): no usarlos.

## IN-02 / IN-03 Calidad del archivo (D7, cerrado)

| Concepto | Valor |
|---|---|
| Quality score | `RQS` por registro, escala 0..1 (1 = sin error de severidad 1) |
| FQS | `average("qs.RQS")` ≡ `(Total − Menores − Sev1) / (Total − Menores)` porque los menores no llegan |
| Acepta | `average("qs.RQS") >= 0.90` |
| Rechaza | `anyOf(RQS_BAJO_UMBRAL_SISBEN)` con condición `average("qs.RQS") < 0.90` |
| Código de razón | `SISBFA00001` (severidad sin definir, ACC-PT03) |
| Error de escala | El `< 90` de HU-004 se descarta |

El patrón propone el nombre `RQS_BAJO_UMBRAL`; se usa `RQS_BAJO_UMBRAL_SISBEN` (inventario IN-03)
porque `fileConditions` es global.

## IN-04 Regla de registro (cerrado)

`recordAcceptance = "errorlevelNotIn(\"1\") and errorcodeNotIn(\"OxygenDuplicate\")"`

| Caso | Resultado |
|---|---|
| Sin errores sev. 1 y no duplicado | Aceptado → Curation |
| Solo sev. 3 (`SISBEN_INC_011/013/019/020`) | Aceptado; Curation limpia los campos |
| Con sev. 1 (`SISBEN_ERR_0xx`, incluye DANE y salvoconducto `SISBEN_ERR_023`) | Rechazado |
| Marcado `OxygenDuplicate` (nivel `""`) | Rechazado |
| Menor de edad (`TIP_DOCUMENTO` 1 o 2) | No llega (filtrado en Validation) |

Compatibilidad con ACC-R01: todos los códigos SISBEN tienen 14 caracteres y ninguno es subcadena de
otro; el único nivel que rechaza es `"1"` y Validation solo emite `"1"`, `"3"` y `""`. Con ACC-R02: el
nombre `RQS` no aparece en `recordAcceptance`.

## IN-05 Diagnóstico heredado (contrato desde Validation)

Ver `diagnostico_validation` en la spec: QS `[RQS]`; códigos sev. 1 `SISBEN_ERR_001…010, 012, 014…018,
021, 022, 023`; sev. 3 `SISBEN_INC_011, 013, 019, 020`; motor `OxygenDuplicate`; marcadores
`errorInFileName`, `errorInHeader`, `errorInFooter`, `errorInEmptyFileBody`.

## IN-06 / IN-07

`customReasonForAutomaticRejectionEnabled = true`; `accept` sin `isEmptyBodyFile` ⇒ un cuerpo vacío
se rechaza. `manualReject`/`manualAccept` no se configuran.

## IN-08 Borrados (D2, abierto)

En este ejemplo histórico, SISBEN usaría `inferDelete` (carga completa) y/o `explicitDelete` (EP). Faltan modos, clave
(`deletePredicates`) y ventana (`lookBackPeriod`), pendientes con el equipo Ingesta 3 / Pinning. El
skill **no** emite `contextualOverride` hasta que exista una decisión registrada.

## Salidas esperadas

| Salida | Ruta (default del framework, relativa a `appHome`) |
|---|---|
| Registros aceptados | `acceptance/accepted/<archivo>` → entrada de Curation |
| Registros rechazados | `acceptance/rejected/records/<archivo>.<datasetId>` |
| Archivo rechazado | `acceptance/rejected/files/<archivo>.<datasetId>` |
| Borrados inferidos | `acceptance/accepted/inferred-<archivo>-asDeleted.<id>` (solo con D2) |
| Validado procesado | `processed/validated/<archivo>` |
| Métricas | `acceptedRecords`, `rejectedRecords`, `totalRecords` (Kafka) |
