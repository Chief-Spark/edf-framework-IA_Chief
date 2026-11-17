# Patrones de Acceptance (derivado de `acceptance_patrones.yaml`)

Uso: **se_escribe** (código propio) · **se_configura** (HOCON) · **se_consume** (lo aplica el motor) ·
**no_usar**. Evidencia: verificado en código salvo indicación.

Mecanismo de extensión: **solo configuración HOCON**; proporción declarativa 100 %, cero clases Scala.
Advertencia central: un error de sintaxis **no falla**; acepta todo (registro) o no decide (archivo).

## Patrones

| Id | Patrón | Uso | Regla para el skill |
|---|---|---|---|
| ACC-P01 | Template Method (Acceptor → SparkAcceptor, E1-E13) | consume | No se modifica el motor. |
| ACC-P02 | Factory por `oxygen.application.lambda` | consume | `batch` (riesgo: construye dos motores). |
| ACC-P03 | Bootstrap App → Initializer | consume | — |
| ACC-P04 | Configuración como datos (`AcceptanceElement` por `filePattern`) | **configura** | Es todo lo que se genera para una fuente. |
| ACC-P05 | Parser combinators en capas (QSP → AGG → EAP archivo; QSP → RVP registro) | **configura** | Se escriben expresiones, no gramáticas. |
| ACC-P06 | Pipeline de compilador (Lexer → Parser → AST → Evaluator) | **configura** | Sintaxis recomendada para `reject`: registra `reasonCode`. |
| ACC-P07 | AST sellado (`AnyOf`/`AllOf`[`LevelIn`]) | consume | `anyOf` = OR, `allOf` = AND. |
| ACC-P08 | Composite/Interpreter de condiciones con nombre | consume | Condiciones reutilizables + severidad por lookup. |
| ACC-P09 | Fallback de sintaxis nueva → antigua | consume | **Silencioso**: probar que `reject` compila en la nueva. |
| ACC-P10 | Try + pattern matching por decisión | consume | Defaults: archivo → `false`, registro → `true`. |
| ACC-P11 | Singleton con broadcast (`OxygenLookupService`) | consume | Lookup de severidades de `reasonCode`. |
| ACC-P12 | Generador de IDs sin coordinación | consume | Eventos de borrado inferido (E12). |
| ACC-P13 | Escritura temporal + move | consume | — |
| ACC-P14 | `lazy val` del histórico curado | consume | Solo se lee si hay `contextualOverride`. |

## Antipatrones del motor que la configuración debe esquivar

| Id | Defecto | Mitigación en el skill |
|---|---|---|
| ACC-AP01 | `match` sin caso de fallo al buscar configuración | El `filePattern` se prueba contra claves reales (`claves_de_prueba`). |
| ACC-AP02 | Default permisivo por registro | ACC-TPL-05 prueba cada fila de la tabla de registro. |
| ACC-AP03 | `catch Throwable` en la inferencia de borrados | ACC-TPL-04 exige un caso que detecte el vacío silencioso. |
| ACC-AP07 | `emitStockMetrics` leída con `.get` | Requisito de ambiente en el runbook. |
| ACC-AP08 | `.get` sin guarda sobre `fileActions`, `accept`, `getAcceptanceInfo` | `fileActions.accept` y `reject` siempre presentes. |
| ACC-AP04/05/06/09-13 | Doble motor, parámetro ignorado, `println`, `count()` en debug, move sobre sí mismo, código muerto, paquete erróneo | Solo informativos; no se tocan. |

## Superficie de extensión (campos de `AcceptanceElement`)

| Campo | Oblig. | Etapas | SISBEN |
|---|---|---|---|
| `filePattern` | sí | E3 | sí |
| `fileActions.accept` | sí (E4 lo lee con `.get`) | E4, E9 | sí |
| `fileActions.reject` | sí (`.get`) | E8 | sí |
| `fileActions.manualReject/manualAccept` | no | E5, E7 | no (IN-07) |
| `recordAcceptance` | no (sin él acepta todo) | E10 | sí |
| `customReasonForAutomaticRejectionEnabled` | no (default `false`) | E4 | `true` |
| `contextualOverride.inferDelete/explicitDelete` | no | E12 | por decidir (D2) |
| `lookupMapping {tableName, mapFromColumn, mapToColumn}` | no | E8, E9 | solo con severidades |
| `fileConditions[]` (nivel etapa, **global**) | no | E8, E9 | sí; nombres/códigos únicos |

## Techo de abstracción

- ACC-T01: no hay paso programable; lo que no quepa en expresiones requiere tocar el motor (prohibido).
- ACC-T02: tres salidas por registro (aceptado, rechazado, borrado vía E12); no hay rechazo silencioso.
- ACC-T03: las reglas de registro solo ven errores y QS, **no valores de campos**.
- ACC-T04: las reglas de archivo solo ven agregados de QS, errores, header/footer y acciones externas.
- ACC-T05: sin transformación.

## No usar como plantilla

`dsl/acceptance/*` (segundo motor no invocado), `ingestion/*`, `util/dsl/ValidatedEventParser`
(compara nivel contra código), `AcceptanceProcessor` (dos copias, nadie lo invoca), modo streaming
(`SparkHighFrequencyAcceptor`, `hf/*`), copias locales de infraestructura, y el documento
`Acceptance-Patterns.md` (sus cifras y flujo no coinciden con el código).
