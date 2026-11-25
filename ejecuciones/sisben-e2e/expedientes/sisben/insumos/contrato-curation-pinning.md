# Contrato Curation → Pinning — SISBEN IV

| Campo | Valor |
|---|---|
| Ingesta | `sisben` |
| Versión / Fecha | 1.0.0 / 2026-11-25 |
| Estado | PROPUESTO (pasa a APROBADO con la aprobación registrada por `edf-orquestador`) |
| Artefacto | ART-034 (`curation_flow`) |
| Contrato general | `.kiro/steering/11-contrato-curation-pinning.md` (formato del evento, regla de normalización, pendientes) |
| Fuentes de esta tabla | `insumos/source-spec-curation.yaml` v1 (`campos_curados[]`, `parametros.omitir_si_nulas`, `extension_subscriber_relation`); `fuentes/spec-sisben.md` 1.0.2 §10.1, §10.2, §11, §14; `fuentes/hu-sisben.md` 1.0.2 R-23, R-24, §5.3, §5.4; `analisis/mapeo-campos.md`; `.kiro/skills/edf-analisis/references/logica-pinning.md` §5, §7, §12.1; `.kiro/skills/edf-analisis/references/fuentes/pinning-muric.md` |
| Skill | `edf-analisis` (Modo 2, tipo T2) |

Marcadores (steering `05`): `[C]` confirmado por la fuente · `[P]` patrón o decisión registrada · `[PM]` propuesta del modelo · `[A:*]` abierto. La spec no trae un anexo con el contenido de este contrato, así que no hay nada «que se aparte de un anexo».

## 1. Claves que Pinning consume

Una fila por clave de `curatedRecordJson` que entra a `identifying-attributes.json`. La columna «Clave publicada» se copia **literal** de `campos_curados[].destino`. La lista sale de spec §11 («Claves que entrega Curation»). SISBEN no publica bandera de fuente (spec §11; HU R-24), así que no hay más filas.

| Clave publicada en `curatedRecordJson` | Tipo publicado | ¿Puede ser `null`? | Normalización aplicada en Curation | PII | Slot del motor (`atributo_motor`) | Estado | Fuente |
|---|---|---|---|---|---|---|---|
| `standardizedIdType` | entero (`tipo_salida: entero`) | No. Si es nulo, la clave se omite (`omitir_si_nulas`; steering `11` §3). Un registro aceptado siempre la trae: los tipos sin homologación (`7`, desconocidos) se rechazan en Validation con `SISBEN_ERR_023` o `SISBEN_ERR_014` | Copia de `tipoDocumento`: homologación `cod_sisben` → `cod_experian` del catálogo `202_SISBEN_PROCESS_ID_TYPE_HO` (`3` → `1`, `PPT` → `11`…) | Sí | `standardizedIdType`, la misma clave: atributo de la lista compartida de Colombia `identifying-attributes-new.json` | `[C]` clave, tipo y regla · `[P]` slot | spec §10.1, §9.4, §11; HU §5.3 «Homologación»; steering `11` §2.1, §6 #5 (opción A) |
| `standardizedIdNumber` | texto | No. Si es nulo, se omite. Un registro aceptado siempre la trae: vacío o solo ceros se rechaza en Validation (`SISBEN_ERR_015`, `SISBEN_ERR_029`) | Copia de `numeroDocumento`: mayúsculas, sin espacios (también internos), sin ceros a la izquierda, para **todos** los tipos (PPT y pasaporte incluidos) | Sí | `standardizedIdNumber`, la misma clave (también es la llave de `nearNeighborKeys`) | `[C]` clave, tipo y regla · `[P]` slot | spec §10.1, §11; HU §5.3 «Número de documento estandarizado»; steering `11` §6 #3, #5; `pinning-muric.md` §nearNeighborKeys |
| `standardizedIdName` | texto | Sí puede faltar: se omite si los cuatro nombres quedan nulos (p. ej. `pri_apellido` y `pri_nombre` «!!!»). La spec dice «nulo»; ver §5, RG-019 sugerido | Concatenación de `primerNombre segundoNombre primerApellido segundoApellido` **ya curados** (mayúsculas, sin tildes ni diéresis, **conserva `Ñ`**, signos a espacio, espacios colapsados), omitiendo nulos o vacíos, con un espacio | Sí | `standardizedIdName`, la misma clave (regla 2 del matcher) | `[C]` clave, tipo y regla · `[P]` slot | spec §10.1, §10.2, §11; HU R-23, R-24, §5.3 «Nombre completo estandarizado» |

## 2. Lo que Pinning usa fuera de `curatedRecordJson`

| Elemento | Valor en SISBEN | Estado | Fuente |
|---|---|---|---|
| subscriberRelation (del evento, no de `curatedRecordJson`) | `<standardizedIdType>_<standardizedIdNumber>` (p. ej. `1_1000000101`), armado por Curation con `extension_subscriber_relation`. Si falta un componente se conserva el del evento aceptado y el registro advierte `subscriberRelation:COMPONENTE_FALTANTE`; nunca sale vacío | `[C]` | spec §10.1; steering `11` §3, §6 #8 (resuelto para SISBEN) |
| subscriber (del evento) | `999722` (lo copia Curation del evento aceptado) | `[C]` | spec §1 |
| Bandera de fuente | Ninguna (`isOfficialSource` no se usa; sin bandera propia). SISBEN entra por la regla 2 del matcher | `[C]` | spec §11; HU R-24; steering `11` §6 #6 (resuelto para SISBEN) |
| Matcher | `ColombiaIdentityMatcher` compartido; prioridad 1 `standardizedIdType` + `standardizedIdNumber` (exacta), prioridad 2 `standardizedIdName` (NearNeighborKeys con el umbral vigente); `reusePIN = false` | `[C]` | spec §11; HU R-24; steering `11` §6 #7 (resuelto para SISBEN) |
| Ruta de entrada a Pinning | `s3://colombia-data/sisben/curation/curated/` (`flow/`, `stock/`), que Pinning lee como `curatedDir/*/*` | `[C]` ruta de la ingesta · `[PM]` que sea la que configura el ambiente de Pinning | spec §14; steering `11` §1, §6 #1 |
| Lector del evento en `oxygen-pinning` | Copia del helper del repo de Validation | `[A:lector-oxygen-pinning]` (pendiente del framework, no de la ingesta) | steering `11` §1, §6 #2 |

## 3. Verificaciones antes de aprobar

- [x] Cada clave existe en `campos_curados[].destino` del source-spec de Curation (mismo texto, mismas mayúsculas). Comprobado con `python3 tools/verificar-claves.py --dir expedientes/sisben/insumos`.
- [x] Cada clave de identidad sale normalizada: mayúsculas, sin tildes ni caracteres especiales, ceros tratados (steering `11` §3). La única diferencia con la letra del steering es la `Ñ`, que se conserva porque lo pide la ingesta (spec §10.2 regla 2; HU §5.3 «ANA PEÑA RUIZ»). Ver RG-021 sugerido.
- [x] Está declarado cómo se pasa de la clave publicada al slot del motor: es la misma clave (opción A, steering `11` §6 #5; lista compartida de Colombia, `pinning-muric.md`). `[P]`: se confirma con la lista `identifying-attributes-new.json` del fork al preparar `yaml-skill-pinning.yaml`.
- [x] Ruta S3 de entrada a Pinning registrada (spec §14), con su `[PM]` de ambiente.
- [x] Ningún valor real: solo nombres de campo y reglas. Los ejemplos (`1_1000000101`) son sintéticos.
- [x] Prueba de la salida de Curation con el generador de `edf-curation` (scratchpad del analista): los escenarios de HU §5.3 sobre las claves de identidad dan `standardizedIdType = 1`, `standardizedIdNumber = "1000000101"` y `standardizedIdName = "ANA PEÑA RUIZ"`.

## 4. Ejemplo sintético (salida esperada del fixture `cur-0003`)

Solo las claves de este contrato, tal como las calcula la implementación de referencia de `edf-curation` con `insumos/source-spec-curation.yaml`:

```json
{
  "subscriber": "999722",
  "subscriberRelation": "1_1000000103",
  "curatedRecordJson": {
    "standardizedIdType": 1,
    "standardizedIdNumber": "1000000103",
    "standardizedIdName": "ANA PEÑA RUIZ"
  }
}
```

## 5. Riesgos y diferencias para el Orquestador

IDs sugeridos (los registra `edf-orquestador`).

| ID sugerido | Riesgo | Nivel · etapa | Mitigación |
|---|---|---|---|
| RG-019 | `standardizedIdName` se **omite** cuando los cuatro nombres quedan nulos (steering `11` §3: una clave de identidad nula rompe `ISIdentityMatcher`), mientras la spec §10.1 dice «nulo si todos faltan». Solo pasa si `pri_apellido` y `pri_nombre` no se pueden normalizar | bajo · `curation_flow` | Se sigue el steering (regla del motor, `deteccion-ambiguedades.md` §7). Si el PO prefiere la letra de la spec, quitar `standardizedIdName` de `parametros.omitir_si_nulas` y confirmar antes que el matcher lee el atributo con `getOrElse` |
| RG-020 | Prioridad 1 entre fuentes: SISBEN publica `standardizedIdType` como **entero** con el código Experian (spec §10.1). La descripción de MURIC dice «Texto: tipo de documento estandarizado (e.g., CC, NIT, CE)» (`pinning-muric.md:29`, nivel 3). Si otras fuentes publican otro valor, la misma persona no coincide por prioridad 1 entre fuentes y se crean PIN duplicados, sin error | alto · `pinning` | Verificar en el fork qué valor y tipo publican las otras ingestas de Colombia para `standardizedIdType` antes de aprobar `yaml-skill-pinning.yaml` (riesgo N3, `06` §2.2) |
| RG-021 | Prioridad 2 entre fuentes: SISBEN conserva la `Ñ` en `standardizedIdName` (spec §10.2), y Pinning compara texto exacto (steering `11` §3). Si otra fuente translitera `Ñ` → `N`, «PEÑA» y «PENA» nunca se comparan | medio · `pinning` | Confirmar la regla de nombres de las otras fuentes de Colombia al preparar `yaml-skill-pinning.yaml` |

Diferencia con HU §5.3 que no es de este contrato pero afecta la DoD de Curation: `numeroDocumentoOriginal` debe conservar el valor original con su espacio (`" 0001000000101"`), y la transformación `original` de `edf-curation` lo recorta (cabecera de `source-spec-curation.yaml`, diferencia 1). No toca a Pinning: `numeroDocumentoOriginal` no es atributo de identidad.

## Pie de trazabilidad

- Claves y reglas: `spec-sisben.md` §10.1, §10.2, §11, §14; `hu-sisben.md` R-23, R-24, §5.3, §5.4.
- Contrato general y decisiones previas: steering `11` §1 a §3, §6 (#3, #5 a #8 resueltos para SISBEN por su spec).
- Verificación: `tools/verificar-claves.py --dir expedientes/sisben/insumos`; `edf-curation/scripts/render_templates.py --spec insumos/source-spec-curation.yaml` (salida en el scratchpad del analista, no en `propuestas/`).
- Última verificación de referencias: 2026-11-25.
