# Inventario de plantillas (implementa `acceptance_template.config`)

Acceptance es solo configuración: ninguna plantilla genera código Scala de producción. Todas viven
en `assets/templates/`; `manifest.yaml` fija el orden de generación.

## Rutas destino (fork de Colombia de oxygen-acceptance, no este skill)

- `hocon` = `env/application-conf/innersource/acceptance-common-application-inner.conf` (el base es inmutable)
- `raiz_tests` = `src/test/scala/com/experian/edf/oxygen/acceptance/source/{{fuente}}/`
- `recursos_tests` = `src/test/resources/{{fuente}}/`

## Entradas del skill (IN-xx → claves de la source-spec)

| Id | Entrada | Clave en la spec | SISBEN | Estado |
|---|---|---|---|---|
| IN-01 | Convención de nombre | `archivo.*` | `[RT_\|EP_]SISBEN_EXPERIAN_AAAAMM.AAAAMMDDHHMMSS±ZZZZ.txt` | propuesta_de_frontera |
| IN-02 | Regla de calidad | `calidad.*` | `RQS`, `average`, `0.90` | cerrado |
| IN-03 | Códigos de razón | `condiciones[]` | `RQS_BAJO_UMBRAL_SISBEN` → `SISBFA00001` | inferido (severidad sin definir) |
| IN-04 | Regla de registro | `registro.*` | niveles `["1"]`, códigos `[OxygenDuplicate]` | cerrado |
| IN-05 | Diagnóstico de Validation | `diagnostico_validation.*` | QS `RQS`, `SISBEN_ERR_0xx`/`SISBEN_INC_0xx`, marcadores | cerrado |
| IN-06 | Rechazo automático | `rechazo_automatico.*` | `customReason… = true` | cerrado |
| IN-07 | Intervención manual | `intervencion_manual.*` | no se configura | cerrado |
| IN-08 | Semántica de borrado | `contextual_override.*` | sin definir | **abierto** |

## Plantillas

| Paso | Id | Archivo `.tpl` | Tipo | Genera | Condición |
|---|---|---|---|---|---|
| 1 | ACC-TPL-02 | `file-conditions.conf.tpl` | parametrizada | elementos para `oxygen.stage.acceptance.fileConditions[]` | siempre |
| 2 | ACC-TPL-03 | `reason-severity.csv.tpl` + `reason-severity-lookup.conf.tpl` | datos | CSV código→severidad + registro en `oxygen.lookups[]` | `severidades.habilitado` |
| 3 | ACC-TPL-01 | `acceptance-block.conf.tpl` | parametrizada | elemento para `oxygen.stage.acceptance.configs[]` | siempre |
| 4 | ACC-TPL-04 | `contextual-override.conf.tpl` (parcial de ACC-TPL-01) | parametrizada | sub-bloque `contextualOverride` | `contextual_override.decision_humana` |
| 5 | ACC-TPL-06 | `validated-events.json.tpl` | datos | `src/test/resources/{{fuente}}/validated/<dir>/part-00000.json` (uno por archivo del fixture) | siempre |
| 5 | ACC-TPL-05 | `AcceptanceExpressionsSpec.scala.tpl` | esqueleto | `{{raiz_tests}}{{Fuente}}AcceptanceExpressionsSpec.scala` | siempre |

Verificaciones del inventario (las aplica el generador `scripts/render_templates.py`, función
`verify_spec`):

- ACC-TPL-01: el umbral aparece con una sola escala y `calidad.umbral` está en [0, 1] (regla "umbral en
  [0, 1]", desde 2026-10-21); todo código/nivel de `recordAcceptance` existe en
  IN-05; ningún nombre de QS aparece dentro de `recordAcceptance`; toda condición de `reject` existe
  en ACC-TPL-02; `reject` está en sintaxis nueva; `filePattern` empieza con `.*` y coincide con las
  claves de prueba (ruta sin extensión y `lineage.fileName`).
- ACC-TPL-02: nombres y `reasonCode` únicos y con el nombre de la fuente. La unicidad **contra la
  configuración compartida** solo la puede comprobar el ingeniero en el fork (checklist).
- ACC-TPL-04: nunca con valores por defecto; exige `decision_ref`.
- ACC-TPL-06: solo datos sintéticos (`eventId` con prefijo `SINT-`); cada QS declarado vale 0 o 1 en
  cada evento (regla "QS por registro binario", AC-ACC-05, desde 2026-10-21).

## Motor de plantillas

Sintaxis propia (ver el docstring de `scripts/render_templates.py`): `{{ruta}}`,
filtros `|hocon`, `|scala` (escapan `\` y `"`), `|json`; bloques `{{#cada lista}}…{{/cada}}`,
`{{#si flag}}…{{/si}}` (con `{{#si ruta?}}` una clave opcional ausente cuenta como falsa, AUD-A4); parciales
`{{> archivo.tpl}}`. Un hueco ausente o nulo es error; tras
renderizar no puede quedar `{{`. `--audit` lista cada hueco con `en_schema` y `en_spec` y, después,
los supuestos del bloque opcional `supuestos` de la spec (`supuesto  S-ACC-xx  impacto=…  donde=…`,
AC-27). Ese bloque no es un hueco: ninguna plantilla lo usa y no cambia la salida generada.

## Puntos abiertos del inventario

ACC-PT01 clave de `fileConditions` — **cerrado** (verificado en el parser) · ACC-PT02 patrón de archivo
(propuesta de frontera, D8) · ACC-PT03 severidad de `SISBFA00001` (abierto) · ACC-PT04
`contextualOverride` (abierto, D2) · ACC-PT05 forma del bloque de HU-004 (se sigue la del motor) ·
ACC-PT06 cómo ejecutar los parsers en pruebas (ACC-TPL-05 usa Spark local; sin certificar, F-01).
