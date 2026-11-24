# Contrato real de `IdentityMatcher` y dimensiones de parametrización

Fuente de verdad verificada por lectura directa de código: `oxygen-pinning/src/main/scala/.../pinning/util/IdentityMatcher.scala` y `extension/ISIdentityMatcher.scala`. Citado también en `Diseño/logica-pinning.md` §7 y aplicado en `Diseño/templates-scala/pinning/SisbenIdentityMatcher.scala.template`.

## 1. El contrato abstracto (no modificar su forma)

```scala
abstract class IdentityMatcher extends Serializable {
  def nearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] =
    List(taggedIdentity.subscriber + ":" + taggedIdentity.subscriberRelation)

  def defaultNearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] =
    List(taggedIdentity.subscriber + ":" + taggedIdentity.subscriberRelation)

  val celebrityIdentityThreshold: Int = 700

  def matchingKeysForCelebrity(taggedIdentity: TaggedIdentity): List[String]  // ABSTRACTO — único miembro obligatorio

  def doesMatch(taggedIdentity1: TaggedIdentity, taggedIdentity2: TaggedIdentity): (Boolean, Double) = {
    if (taggedIdentity1.subscriberRelation == taggedIdentity2.subscriberRelation &&
        taggedIdentity1.subscriber == taggedIdentity2.subscriber) (true, 0.0)
    else (false, 1.0)
  }

  def doesMatchWithProfiling(...): ((Boolean, Double), String) = { ... }  // envoltura de medición, no tocar

  def doesMatchKA(taggedIdentity1: TaggedIdentity, taggedIdentity2: TaggedIdentity): Double = { ... }  // default: +1.0 por atributo igual
}
```

Solo `matchingKeysForCelebrity` es abstracto. El resto tiene default razonable — una implementación concreta puede sobrescribir solo lo que necesita.

### 1.1 Atributos ausentes, nunca `null` (AUD-P2)

Desde la auditoría 2026-11-24 (steering `11` §3, `edf-curation` AUD-C4), Curation **omite** las claves de identidad nulas (`standardizedId*` o `parametros.omitir_si_nulas`). Un atributo puede faltar en el evento; nunca llega `null`.

- El motor entrega `""` por un atributo que el evento no trae (comentario de `IdentityMatcher.scala:46-49`).
- El matcher lee cada atributo con `attributes.get(k).map(_.toString).getOrElse("")` u `Option`, **nunca** con `.get` (`ISIdentityMatcher.scala:46-54` hace `.get.toString`: un `null` rompe la ejecución). Es lo que ya hace `assets/IdentityMatcher.scala.template`.
- `""` es dato faltante, no coincidencia: un término de scoring solo suma si el valor no está vacío (`thisX.exists(_.nonEmpty) && thisX == thatX`). Igual en `nearNeighborKeys`: una clave vacía no forma bloque.
- `subscriberRelation` nunca llega vacío de Curation (AUD-C3): `super.doesMatch` puede seguir comparándolo.

## 2. Las nueve dimensiones que este skill debe parametrizar

Derivadas del esquema real de `Diseño/yaml-skill-pinning.yaml` (`mapeo_identidad.atributos`, `reglas_matching_por_campo`). Por cada atributo identificador de la ingesta nueva, el usuario (o el YAML intermedio que genere para esa ingesta) debe aportar:

| # | Dimensión | Ejemplo (SISBEN) | Obligatoria para generar código |
|---|---|---|---|
| 1 | Nombre del slot en el motor (`atributo_motor`) | `nationalId` | Sí |
| 2 | Campo curado de origen (`campo_curado_sisben` / `campo_curado_<ingesta>`) | `standardizedIdNumber` | Sí |
| 3 | Peso en `doesMatch` (0.0 si no participa) | `0.5` | Sí — si no está confirmado, el skill NO asigna un peso por defecto (ver `decisiones-pendientes.md`) |
| 4 | Participación en `nearNeighborKeys` (blocking) | sí, clave propia | Sí |
| 5 | Detalle de la clave de blocking (tal cual, prefijo, combinación) | "tal cual, sin transformar" | Sí si participa en blocking |
| 6 | Participación en `doesMatchKA` (Keep Apart) | sí, por defecto | No — hereda el default si no se especifica |
| 7 | Pertenencia a la llave de identidad | `true` | Sí |
| 8 | Clasificación PII | `true` | Sí (afecta comentarios de seguridad en el código generado) |
| 9 | Estado de confirmación (`[C]` / `[A:sin-clasificar]` / `[P]`) | `[C]` | Sí — determina si el campo se incluye en el código generado o se excluye con un TODO |

**Regla dura: la dimensión 9 gobierna a todas las demás.** Un atributo en `[A:sin-clasificar]` o `[P]` no se declara en el array de slots activos del `IdentityMatcher` generado, aunque el usuario haya aportado las demás dimensiones — se documenta como comentado/excluido, con la razón citada. Esto replica exactamente lo que se hizo para SISBEN con `middleName`/`secondLastName`.

## 3. El umbral global (`similarityThreshold`)

El motor de referencia (`ISIdentityMatcher`) usa `0.6` como valor literal hardcodeado en Scala — no es una clave HOCON real en el código verificado. Al generar un `IdentityMatcher` nuevo, el skill:
- Mantiene esta constante como `private val` nombrado dentro de la clase (no HOCON), consistente con el código real.
- La deja marcada con un comentario explícito indicando que es el valor del matcher de referencia, no una decisión de negocio confirmada para la ingesta nueva — igual que se hizo en `SisbenIdentityMatcher.scala.template`.
- Si el usuario aporta un umbral distinto y confirmado para su ingesta, el skill lo usa en su lugar, citando la fuente de esa confirmación.

## 4. Verificación aritmética obligatoria antes de generar

Antes de dar por cerrado el `IdentityMatcher` generado, el skill debe calcular: con los pesos de los slots activos (dimensión 9 = `[C]`), ¿es posible alcanzar `similarityThreshold` sin que `nationalId` (o el identificador equivalente) coincida exactamente? Si la respuesta es no, esto debe declararse explícitamente en un comentario del código generado — es la misma advertencia aritmética que `Diseño/yaml-skill-pinning.yaml` (`instruccion_generacion.advertencia_consecuencia`) ya documentó para SISBEN. No es opcional: generar el código sin esta verificación deja al revisor humano sin la información que necesita para aprobar el artefacto con criterio.
