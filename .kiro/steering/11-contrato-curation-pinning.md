---
inclusion: auto
name: Contrato Curation a Pinning EDF
description: Contrato de frontera entre la salida de Curation y la entrada de Pinning en EDF/Oxygen. Formato del evento curado, campos que Pinning consume, regla de normalización obligatoria en Curation y ejemplo sintético. Activar al generar o revisar Curation, al mapear atributos de identidad, al construir yaml-skill-pinning.yaml o al generar identifying-attributes.json.
---

# 11 — Contrato de frontera Curation → Pinning

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01`; complementa `07-contratos-frontera.md` (fila Curation → Pinning) |
| Cierra | `[A:OQ-IL-3]` **parcialmente**: formato y regla de normalización confirmados; quedan los pendientes de §6 |
| Fuente | Aportado por el usuario en sesión (2026-11-04), con citas a `PinningIOUtils.scala::readCuratedEvents`, `OxygenIOReaderUtils.scala::taggedCuratedEventsFromWritable`, `09_diseño_processor/CURATION_DISEÑO_PROCESSOR.md` §149-150, `logica-pinning.md` §3, §5, §7.3-7.4, §11, `01_contratos_frontera.md`, HU-SISBEN-005 |

## 1. Cómo lee Pinning la salida de Curation `[C]`

```
disco (curatedDir/*/*)
  └─ TaggedWritableEvent                        esquema de lectura
       └─ TaggedWritableEvent.toTaggedCuratedEvent
            └─ TaggedCuratedEventWithMap
                 └─ curatedRecordJson: Map[String, Any]   ← claves = nombres publicados por CuratedEvent.write
```

- Lectura: `curatedDir/*/*` (incluye `flow` y `stock`).
- El mapa `curatedRecordJson` lo produce `<Fuente>CuratedEvent.write` en Curation.
- Fuente del lector: copia del helper que trae el repo de Validation. El repo `oxygen-pinning` no está en el workspace → el lector queda `[A:lector-oxygen-pinning]` hasta verlo en ese repo.

## 2. Qué toma Pinning del evento `[C]`

1. **Identity Extraction → `TaggedIdentity`.** De cada evento curado toma **solo** las claves listadas en `identifying-attributes.json` (array plano de strings, cargado desde el classpath, broadcast `identifyingAttributesBC`; solo se usa el primer elemento de `identifyingAttributesFile`).
2. **Matching y clustering** trabajan solo sobre `Identity.identityId`, `Identity.attributes: Map[String, Any]`, `TaggedIdentity.subscriber` y `TaggedIdentity.subscriberRelation`. Esto incluye `IdentityMatcher.doesMatch`, `nearNeighborKeys` y `pin-index`.
3. El evento completo solo se vuelve a usar en la fase HC (une pin↔identidad e identidad↔evento).

### 2.1 Cómo lo hace la ingesta certificada de Colombia (MURIC) `[P]`

Fuente: `.kiro/skills/edf-analisis/references/fuentes/pinning-muric.md` y `curation-muric.md` (nivel 3, describen el código de `bureau.colombia_innersource`).

- Lista de atributos compartida por Colombia: `identifying-attributes-new.json` (`env/application-conf/pinning/`). Incluye `standardizedIdType`, `standardizedIdNumber`, `standardizedIdName`, teléfonos y direcciones estandarizados, `pinningKeySSR` y banderas de fuente (`isOfficialSource`, `isSuperIntendencia`, `isINFP`, `isTable1QP`, `isMigratedAmnesty`, `isPinningSpecialEventAlpha`, `isMuric`).
- Matcher compartido: `ColombiaIdentityMatcher` (`modules/pinning/.../custommatch/`). `curation-pinning-sisben.md` §7.4 nombra otra clase, `IdentityImpl`: la contradicción queda abierta en §6 #7.
- Atributo ausente en el evento → `""`; bandera ausente → `"false"`. `standardizedIdType` o `standardizedIdNumber` nulos → no match inmediato.
- `nearNeighborKeys` = solo `standardizedIdNumber`.
- Reglas de `doesMatch`, en orden: (0) tipo o número nulo → no match; (1) tipo + número + al menos una bandera de fuente en `true` → match; (2) tipo + número + (nombre completo o parcial, dirección o teléfono) → match; (3) tipo + número + `pinningKeySSR` → match; (4) no match. Antes de todo, `super.doesMatch` por `subscriber` / `subscriberRelation`.
- Para incorporar una fuente con bandera propia (caso MURIC) se tocan tres piezas compartidas: `identifying-attributes-new.json`, `IdentityAttributesKey` y `doesMatch`. Curation publica la bandera como constante (`isMuric = true`).

## 3. Regla crítica: Curation normaliza, Pinning no `[C]`

Pinning compara **texto exacto**. No pasa a mayúsculas, no quita tildes ni espacios, no toca ceros.

- `ISIdentityMatcher` / `RIIdentityMatcher`: `nationalId1 == nationalId2`, `firstName1 == firstName2 && firstLastName1 == firstLastName2`.
- La plantilla de `edf-pinning` lee `identity.attributes.get(...).map(_.toString)` y compara con `==`.
- `nearNeighborKeys` arma bloques con `nationalId` tal cual y con los 5 primeros caracteres de nombre y apellido. `"José"` vs `"JOSE"` o `"0012345"` vs `"12345"` caen en bloques distintos y **nunca se comparan**, sin error.

**Obligación de Curation:** todo campo que vaya a `identifying-attributes.json` sale de Curation **ya normalizado**: mayúsculas, sin tildes, sin caracteres especiales, sin espacios sobrantes, ceros a la izquierda tratados según `reglas_documento`. (`01_contratos_frontera.md`, HU-SISBEN-005.)

Consecuencia operativa: un error aquí no falla en build ni en ejecución; falla en los datos. Riesgo **N3** (`06` §2).

## 4. Tabla del contrato por campo

`edf-analisis` llena esta tabla por ingesta en `insumos/contrato-curation-pinning.md` y es la fuente para `yaml-skill-pinning.yaml` → `mapeo_identidad.atributos`.

| Clave publicada en `curatedRecordJson` | Tipo publicado | ¿Puede ser `null`? | Normalización aplicada en Curation | PII | Slot del motor (`atributo_motor`) | Estado |
|---|---|---|---|---|---|---|

Reglas:
- Una fila por clave que Pinning consume. Las demás claves no entran.
- El **tipo** se toma de `campos_curados[].tipo_salida` del `source-spec-curation.yaml`.
- Si el slot del motor no está confirmado, la fila queda `[A:…]` y el atributo **no** entra al array activo.

## 5. Ejemplo sintético (SISBEN) `[C]`

Registro curado de referencia, 100 % sintético:

```json
{
  "eventId": "evt-1001",
  "lineage": {"dataSource": "DNP", "receivedDate": 1707147000000,
              "fileName": "SISBEN_EXPERIAN_202401.20240205103000-0500.txt",
              "subscriber": "999722", "fileAsOfDate": 1707147000000,
              "datasetId": "ds-mock-cur-0001", "recordHash": "hash-evt-1001"},
  "eventType": 30,
  "payload": {"fixture": "SINTETICO"},
  "asOfDate": 1705332600000,
  "subscriber": "999722",
  "subscriberRelation": "9990101",
  "curatedRecordJson": {
    "idFichaOrigen": "0999000101", "idHogar": 1,
    "codDepartamentoSisben": "05", "nomDepartamento": "ANTIOQUIA",
    "codMunicipioSisben": "05001", "nomMunicipio": "MEDELLIN",
    "idClase": 1, "idPersonaOrden": 1, "idNacional": 999000101,
    "primerApellido": "PRUEBANEZ", "segundoApellido": "FICTICIO",
    "primerNombre": "SINTETICO", "segundoNombre": "JOSE MARIA",
    "tipoDocumento": 1, "tipoDocumentoSisben": "3", "numeroDocumento": "9990101",
    "grupoSisben": "A", "nivelSisben": 1,
    "fechaDigitacion": 1705294800000, "fechaActualizacionPersona": 1705294800000,
    "idMarca": 0, "idEstado": 10, "fechaActualizacionCns": 1705332600000,
    "fechaCorte": 1706677200000, "tipoArchivo": "REGULAR",
    "currentPartyType": 1, "currentPartyIdNumber": "9990101",
    "subscriberBlockStatus": "NO_BLOQUEADO", "subscriberBlockReason": null,
    "advertenciasCuracion": []
  }
}
```

## 6. Pendientes de este contrato

| # | Pendiente | Estado |
|---|---|---|
| 1 | Ruta S3 completa de **entrada** a Pinning. La conocida (`s3://dev-167753196070-emr-coslabureau-data/dev_sla_co_ebp_1/colombia-app/generations/`) es la de **salida** (generación EPOC). De la entrada solo consta el patrón `curatedDir/*/*` | `[PM]` |
| 2 | Lector confirmado en `oxygen-pinning` (hoy se cita la copia del repo de Validation) | `[A:lector-oxygen-pinning]` |
| 3 | ~~Cuál clave es `nationalId`~~ | **Cerrada (usuario, 2026-11-04):** el identificador es `standardizedIdNumber`, derivado de `NUM_DOCUMENTO`. No es `idNacional` |
| 4 | Colisión posible entre el manejo de EP en Curation e `inferDelete` en Pinning | `[A:PT-12]` (heredado de `03`) |
| 5 | **Los nombres no coinciden entre las piezas.** El `yaml-skill-pinning.yaml` de SISBEN mapea `nationalId → standardizedIdNumber` y `firstName → PRI_NOMBRE` (y `yaml-skill-synthesis.yaml` / `yaml-skill-statecalc.yaml` usan `standardizedIdNumber` / `standardizedIdType` como campos curados); Curation publica `numeroDocumento` y `primerNombre` (source-spec de Curation y ejemplo de §5); `identifying-attributes.json` usa los slots del motor (`nationalId`, `firstName`). Falta confirmar en qué punto la clave publicada por Curation se convierte en el slot del motor (¿Curation publica con el nombre del slot?, ¿Pinning tiene un mapeo?) | **Decidido (usuario, 2026-11-04): opción A.** Curation publica además `standardizedIdType` (código Experian del catálogo 202, entero), `standardizedIdNumber` (texto, ceros a la izquierda eliminados) y `standardizedIdName` (CUR-SIS-03). Para PPT se crea además `NUM_DOCUMENTO_SISBEN` con el valor original. Pinning reutiliza las piezas compartidas de Colombia, sin generar matcher (P1). Pendiente de aplicar en `source-spec` y `yaml-skill-*` hasta cerrar #6 y #7 |
| 6 | Bandera de fuente para SISBEN en la regla 1 de `ColombiaIdentityMatcher` (§2.1): (a) `isOfficialSource = true`, que depende de PA-003 "¿SISBEN es fuente oficial?"; (b) bandera nueva `isSisben`, que toca tres piezas compartidas (riesgo N3, `06` §2.2); (c) sin bandera: regla 2, que exige `standardizedIdName` comparable con el resto de fuentes | `[A:OQ-TR-6]` — **bloqueante** |
| 7 | Clase del matcher configurada: `ColombiaIdentityMatcher` (`pinning-muric.md`) o `IdentityImpl` (`curation-pinning-sisben.md` §7.4) | `[A:matcher-colombia]` |
| 8 | Formato de `subscriberRelation` para SISBEN. Hoy `edf-curation` publica `NUM_DOCUMENTO` sin normalizar (`edf-curation/references/interpretar-salida.md`); PIN-SIS-04 pide `standardizedIdType + "_" + standardizedIdNumber`. Afecta `super.doesMatch`. Cambiarlo toca el contrato de frontera | `[A:OQ-IL-6]` — requiere aprobación antes de modificar `edf-curation` |
