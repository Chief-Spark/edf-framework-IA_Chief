# Patrones de la etapa Validation (derivado saneado de `validation_patrones.yaml`)

Se conservan los ids `VAL-*` para trazabilidad. Uso: **escribe** (código propio), **configura**
(HOCON), **consume** (lo aplica el motor), **no usar**.

## Patrones

| Id | Patrón | Uso | Regla para el skill | Dónde lo materializa |
|---|---|---|---|---|
| VAL-P01 | Template Method (motor + hooks) | consume | Solo hooks; no alterar la secuencia E1–E15 | — |
| VAL-P02 | Strategy por nombre de clase + reflexión | configura | `processor` en HOCON; constructor exacto `(ProcessorContext)` | VAL-TPL-06, 07 |
| VAL-P03 | Factory por `defaultOutputStructure` | consume | No replicar `isInstanceOf`; confirmar el motor (F-04) | runbook |
| VAL-P04 | Bootstrap App → Initializer | consume | — | — |
| VAL-P05 | Interfaz Java + clase abstracta Scala | consume | Extender solo `ValidationProcessor` | VAL-TPL-06 |
| VAL-P06 | `lazy val` para estado derivado | escribe | No tocar `fileMetadata` en el constructor | VAL-TPL-05, 06 |
| VAL-P07 | Serializable + broadcast | escribe | Logger y catálogo como `@transient lazy val` | VAL-TPL-06 |
| VAL-P08 | Jerarquía `ValidationResult` | escribe | `validateFilename`/`validateHeader` siempre devuelven un resultado | VAL-TPL-06 |
| VAL-P09 | DSL de reglas (parser combinators) | configura | Códigos de longitud fija; niveles de un carácter; comparación por subcadena | VAL-TPL-03, 07 |
| VAL-P10 | Value Object `ValidatedEventValueObject` | escribe | Un evento por registro | VAL-TPL-06 |
| VAL-P11 | Evento desde texto crudo | escribe | `split(d, -1)` una vez; accesores que nunca lanzan | VAL-TPL-04 |
| VAL-P12 | Parameter object `ValidationContext` | consume | Firmas con contexto; BATCH | VAL-TPL-06 |
| VAL-P13 | `Option` en `DomainData` | escribe | Guard-rails explícitos; no depender del fallback | VAL-TPL-06 |
| VAL-P14 | `@tailrec` + `Try` | consume | — | — |
| VAL-P15 | Cadena de deprecación | no usar | Implementar solo `createValidatedEvents(rawRecord, ctx)` | VAL-TPL-06 |

## Antipatrones

| Id | Antipatrón | Regla |
|---|---|---|
| VAL-AP01 | `println` | Solo logger |
| VAL-AP02 | Clave obligatoria leída con `.get` (`emitStockMetrics`) | Documentarla como requisito de ambiente |
| VAL-AP03 | `assert(false)` si `keysForDuplicate` es vacío | La clave nunca es un arreglo vacío |
| VAL-AP04 | Motor duplicado (CustomMode borra `appHome`) | Confirmar el motor con `defaultOutputStructure` |
| VAL-AP05 | Código muerto comentado | No dejarlo |
| VAL-AP06 | `scala.Enumeration` | `sealed trait` para severidades y tipos |
| VAL-AP07 | `logger.error` con datos de negocio | Sin PII en logs: solo códigos y conteos |
| VAL-AP08 | Repositorio sin `src/test` | El processor nace con tests |
| VAL-AP09 | Typo `contexual` | Respetarlo en HOCON (el parser acepta ambos) |

## Contrato del processor (VAL-RC)

- VAL-RC01 constructor exacto `(ProcessorContext)`.
- VAL-RC02 todo Serializable; no serializables como `@transient lazy val`.
- VAL-RC03 no acceder a `fileMetadata` en el constructor.
- VAL-RC04 nunca lanzar por registro: solo `MalformedEventException` se captura; cualquier otra
  excepción tumba el job. Todo error de dato es `ObjectError`.
- VAL-RC05 lookups solo por el lookup del contexto (API inferida, VAL-PT03).

## Techo de abstracción (VAL-T)

T01 un registro a la vez (entre registros solo duplicados por clave) · T02 reglas de archivo solo ven
header/footer · T03 el DSL lee errores y quality scores, no valores · T04 un processor por
`filePattern`, sin inyección de dependencias · T05 la severidad es texto libre.

## Modelo de capas

- **Capa A** (alta abstracción, una vez): `FieldSpec`, `SourceSpec`, validadores puros y
  `RecordValidator`. Sin Spark ni framework: compila y se prueba sin JAR.
- **Capa B** (declarativa): `<Fuente>Spec`, 1:1 desde el source-spec.
- **Capa C** (delgada): `<Fuente>ValidationProcessor`. En este skill la "parte manual" de VAL-TPL-06
  (reglas entre campos, `listDuplicateEvents`, normalización en memoria) se declara como datos en el
  source-spec, de modo que la generación es 100% desde el spec.

## No usar como plantilla

`CreditEventValidationProcessor` y eventos de ejemplo de otras fases; validadores contextuales;
utilidades de Pinning; `decodeErrorObject` (reescribe códigos de 11 caracteres); el patrón Scala de
`04_Etapas_Validation-Acceptance.md` §8.3 (no extiende `ValidationProcessor`, usa tipos inexistentes).
