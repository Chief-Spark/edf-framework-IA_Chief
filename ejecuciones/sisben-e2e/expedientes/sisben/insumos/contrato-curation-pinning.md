# Contrato Curation → Pinning — SISBEN IV

| Campo | Valor |
|---|---|
| Ingesta | `sisben` |
| Versión / Fecha | 2.0.0 / 2026-11-25 |
| Artefacto | ART-034 (`curation_flow`) |
| Contrato general | `.kiro/steering/11-contrato-curation-pinning.md` (formato del evento, regla de normalización, pendientes) |
| Fuentes de esta tabla | `insumos/source-spec-curation.yaml` v2 (`campos_curados[]`, `parametros.omitir_si_nulas`, `extension_subscriber_relation`); `fuentes/spec-sisben.md` 1.0.2 §10.1, §10.2, §11, §14; `fuentes/hu-sisben.md` 1.0.2 R-23, R-24, §5.3, §5.4; `analisis/mapeo-campos.md`; `.kiro/skills/edf-analisis/references/logica-pinning.md` §5, §7, §12.1; `.kiro/skills/edf-analisis/references/fuentes/pinning-muric.md`; `Motor-Oxigen/oxygen-pinning/.../util/EventPinner.scala:64-65`, `IdentityMatcher.scala:46-49` |
| Skill | `edf-analisis` (Modo 2, tipo T2) |
| Cambios respecto de 1.0.0 | Cabecera sin estado (HF-32). Encabezado de §1 igual al de steering `11` §4. Cita del motor para «se omite, nunca `null`» (AUD-C-11 b). `ColombiaIdentityMatcher` declarado como decisión de la spec no verificada en `Motor-Oxigen/` (AUD-C-11 a). Rutas relativas al expediente (AUD-C-11 c). RG-019 aceptado (INT-038) |

El estado y la aprobación de este contrato viven solo en `estado-ingesta.json` (HF-32). Marcadores (steering `05`): `[C]` confirmado por la fuente · `[P]` patrón o decisión registrada · `[PM]` propuesta del modelo · `[A:*]` abierto. La spec no trae un anexo con el contenido de este contrato, así que no hay nada «que se aparte de un anexo».

## 1. Claves que Pinning consume

Una fila por clave de `curatedRecordJson` que entra a `identifying-attributes.json`. La columna «Clave publicada» se copia **literal** de `campos_curados[].destino`. La lista sale de spec §11 («Claves que entrega Curation»). SISBEN no publica bandera de fuente (spec §11; HU R-24), así que no hay más filas.

| Clave publicada en `curatedRecordJson` | Tipo publicado | ¿Puede faltar? (nunca `null`, §3) | Normalización aplicada en Curation | PII | Slot del motor (`atributo_motor`) | Estado |
|---|---|---|---|---|---|---|
| `standardizedIdType` | entero (`tipo_salida: entero`) | Si es nulo **se omite, nunca `null`** (`omitir_si_nulas`; steering `11` §3; `EventPinner.scala:64-65` hace `getAsString` sobre un valor no nulo y un `JsonNull` lanza; una clave ausente llega como `""`, `IdentityMatcher.scala:46-49`). En la práctica no falta: un tipo sin homologación (`7`, desconocido) se rechaza en Validation (`SISBEN_ERR_023`, `SISBEN_ERR_014`) | Copia de `tipoDocumento`: homologación `cod_sisben` → `cod_experian` del catálogo `202_SISBEN_PROCESS_ID_TYPE_HO` (`3` → `1`, `4` → `4`, `5` → `8`, `6` → `5`, `8` → `9`, `PPT` → `11`) | Sí | `standardizedIdType`, la misma clave: atributo de la lista compartida de Colombia `identifying-attributes-new.json` | `[C]` clave, tipo y regla (spec §9.4, §10.1, §11; HU §5.3 «Homologación») · `[P]` slot (steering `11` §2.1, §6 #5, opción A) · RG-020 |
| `standardizedIdNumber` | texto | Si es nulo **se omite, nunca `null`** (mismas citas). En la práctica no falta: vacío o solo ceros se rechaza en Validation (`SISBEN_ERR_015`, `SISBEN_ERR_029`) | Copia de `numeroDocumento`: mayúsculas, sin espacios (también internos), sin ceros a la izquierda, para **todos** los tipos (PPT y pasaporte incluidos) | Sí | `standardizedIdNumber`, la misma clave (también es la llave de `nearNeighborKeys`) | `[C]` clave, tipo y regla (spec §10.1, §11; HU §5.3 «Número de documento estandarizado») · `[P]` slot (steering `11` §6 #3, #5; `pinning-muric.md`, nearNeighborKeys) |
| `standardizedIdName` | texto | **Se omite, nunca `null`**, si los cuatro nombres quedan nulos (p. ej. `pri_nombre` y `pri_apellido` «!!!» con los segundos vacíos: fixture `cur-0016`). Mismas citas del motor. Diferencia de forma con spec §10.1 («nulo») aceptada (RG-019, INT-038; PA-007) | Concatenación de `primerNombre segundoNombre primerApellido segundoApellido` **ya curados** (mayúsculas, sin tildes ni diéresis, **conserva `Ñ`**, signos a espacio, espacios colapsados), omitiendo nulos o vacíos, con un espacio | Sí | `standardizedIdName`, la misma clave (regla 2 del matcher) | `[C]` clave, tipo y regla (spec §10.1, §10.2, §11; HU R-23, R-24, §5.3 «Nombre completo estandarizado») · `[P]` slot y omisión (RG-019) · RG-021 |

## 2. Lo que Pinning usa fuera de `curatedRecordJson`

| Elemento | Valor en SISBEN | Estado | Fuente |
|---|---|---|---|
| `subscriberRelation` | `<standardizedIdType>_<standardizedIdNumber>` (p. ej. `1_1000000100`), armado por Curation con `extension_subscriber_relation`. Si falta un componente se conserva el del evento aceptado y el registro advierte `subscriberRelation:COMPONENTE_FALTANTE`; nunca sale vacío | `[C]` | spec §10.1; steering `11` §3, §6 #8 (resuelto para SISBEN) |
| `subscriber` | `999722` (lo copia Curation del evento aceptado) | `[C]` | spec §1 |
| Bandera de fuente | Ninguna (`isOfficialSource` no se usa; sin bandera propia). SISBEN entra por la regla 2 del matcher | `[C]` | spec §11; HU R-24; steering `11` §6 #6 (resuelto para SISBEN) |
| Matcher | `ColombiaIdentityMatcher` compartido; prioridad 1 `standardizedIdType` + `standardizedIdNumber` (exacta), prioridad 2 `standardizedIdName` (NearNeighborKeys con el umbral vigente); `reusePIN = false`. **Decisión `[C]` de spec §11; la clase no está en `Motor-Oxigen/`** (solo `IdentityMatcher`, `ISIdentityMatcher`, `RIIdentityMatcher`, `TestIdentityMatcher`): la describe solo `pinning-muric.md` (nivel 3). Se confirma en el fork con `yaml-skill-pinning.yaml` (ver RG-020) | `[C]` decisión · `[PM]` clase no verificada en el motor | spec §11; HU R-24; steering `11` §6 #7 |
| Ruta de entrada a Pinning | `s3://colombia-data/sisben/curation/curated/` (`flow/`, `stock/`), que Pinning lee como `curatedDir/*/*` | `[C]` ruta de la ingesta · `[PM]` que sea la que configura el ambiente de Pinning | spec §14; steering `11` §1, §6 #1 |
| Lector del evento en `oxygen-pinning` | Copia del helper del repo de Validation | `[A:lector-oxygen-pinning]` (pendiente del framework, no de la ingesta) | steering `11` §1, §6 #2 |

## 3. Verificaciones antes de aprobar

Rutas relativas al expediente (`ejecuciones/sisben-e2e/expedientes/sisben/`).

- [x] Cada clave existe en `campos_curados[].destino` del source-spec de Curation (mismo texto, mismas mayúsculas). Comprobado desde la raíz del repo con `python3 tools/verificar-claves.py --dir <expediente>/insumos`.
- [x] Cada clave de identidad sale normalizada: mayúsculas, sin tildes ni caracteres especiales, ceros tratados (steering `11` §3). La única diferencia con la letra del steering es la `Ñ`, que se conserva porque lo pide la ingesta (spec §10.2 regla 2; HU §5.3 «ANA PEÑA RUIZ»). Ver RG-021.
- [x] Está declarado cómo se pasa de la clave publicada al slot del motor: es la misma clave (opción A, steering `11` §6 #5; lista compartida de Colombia, `pinning-muric.md`). `[P]`: se confirma con la lista `identifying-attributes-new.json` del fork al preparar `yaml-skill-pinning.yaml`.
- [x] Ruta S3 de entrada a Pinning registrada (spec §14), con su `[PM]` de ambiente.
- [x] Ningún valor real: solo nombres de campo y reglas. Los ejemplos (`1_1000000100`) son sintéticos.
- [x] Prueba de la salida de Curation con el generador de `edf-curation` 1.9.7 sobre `insumos/source-spec-curation.yaml` v2 (scratchpad del analista, no `propuestas/`): `standardizedIdType = 1`, `standardizedIdNumber = "1000000101"` (`cur-0004`), `standardizedIdName = "ANA PEÑA RUIZ"` (`cur-0003`), y `standardizedIdName` ausente en `cur-0016` con `subscriberRelation = 1_1000000116`.

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

## 5. Riesgos

Registrados en el expediente (`riesgos[]`).

| ID | Riesgo | Nivel · etapa | Mitigación |
|---|---|---|---|
| RG-019 | Las claves `standardizedId*` se omiten cuando son nulas (steering `11` §3) en lugar de salir nulas como dice la letra de spec §10.1 para `standardizedIdName`. Solo pasa si los cuatro nombres quedan nulos | bajo · `curation_flow` · **aceptado** | Aceptado por el Desarrollador (INT-038): omitir la clave equivale a nulo para el matching (`IdentityMatcher.scala:46-49`) y un `null` explícito rompe `EventPinner.scala:64-65`. PA-007 (no bloqueante) para la próxima versión de la spec |
| RG-020 | `standardizedIdType` lleva el código Experian (spec §10.1). Si otras fuentes de Colombia publican otro juego de códigos (p. ej. `CC`, como describe `pinning-muric.md:29`) o si `ColombiaIdentityMatcher` no compara por `toString`, la prioridad 1 de R-24 no cruza la misma persona entre fuentes | alto · `pinning` | Al preparar `yaml-skill-pinning.yaml`, verificar en el fork los valores que publican las otras fuentes y la comparación del matcher; sin esa verificación el insumo de Pinning no se aprueba |
| RG-021 | La `Ñ` se conserva en `standardizedIdName` (spec §10.2) y otras fuentes podrían transliterarla, lo que afecta el cruce por nombre | medio · `pinning` | Verificar la normalización del nombre en las otras fuentes antes de aprobar el insumo de Pinning |

## Pie de trazabilidad

- Claves y reglas: `spec-sisben.md` §10.1, §10.2, §11, §14; `hu-sisben.md` R-23, R-24, §5.3, §5.4.
- Contrato general y decisiones previas: steering `11` §1 a §4, §6 (#3, #5 a #8 resueltos para SISBEN por su spec).
- Motor: `Motor-Oxigen/oxygen-pinning/src/main/scala/com/experian/edf/oxygen/pinning/util/EventPinner.scala:64-65`; `.../util/IdentityMatcher.scala:46-49`.
- Verificación (rutas relativas al expediente): `tools/verificar-claves.py --dir <expediente>/insumos`; `.kiro/skills/edf-curation/scripts/render_templates.py --spec <expediente>/insumos/source-spec-curation.yaml` (salida en el scratchpad del analista, no en `propuestas/`).
- Última verificación de referencias: 2026-11-25.
