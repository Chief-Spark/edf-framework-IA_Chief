# Catálogo de decisiones de negocio que este skill NUNCA resuelve solo

Este es el documento más importante de todo el skill. Léelo completo antes de generar cualquier artefacto.

El skill genera **estructura parametrizada**, no decisiones de negocio. Cada vez que el código o la configuración de Pinning dependen de algo que ninguna fuente verificada confirma, el skill debe:

1. **Detenerse en ese punto** y marcarlo con un `TODO` explícito en el código, citando qué fuente lo bloquea.
2. **Nunca** elegir el valor "más razonable" o "el que usa el matcher de referencia" como si fuera una decisión de negocio tomada — un valor de arranque heredado del motor genérico debe declararse como tal, no disfrazarse de decisión confirmada.
3. **Nunca** inventar un nombre de campo curado, un peso de scoring, o una regla de validación cruzada que ninguna fuente mencione.

Esta regla viene directamente de `04-reglas-analisis.md` §4.4 (P8: no rellenar un hueco con el valor más probable) y de cómo se construyó `Diseño/templates-scala/pinning/SisbenIdentityMatcher.scala.template` — ese archivo es el precedente a seguir exactamente.

## Las cinco categorías de decisión pendiente que ya se conocen de este dominio

Úsalas como checklist al generar para cualquier ingesta nueva — probablemente reaparezcan, aunque con nombres distintos:

### 1. Elección de `IdentityMatcher`: genérico vs. a medida vs. de otro fork

Para SISBEN, no hubo decisión cerrada entre usar `ISIdentityMatcher` (genérico, verificable en Z1) o `IdentityImpl` (propuesto por un documento de negocio, pero viviendo en el fork `buro.colombia_innersource`, Z2, no verificable desde este workspace). **Regla:** si el usuario propone una clase de un repositorio que el skill no puede leer directamente (fuera del workspace activo), el skill genera la estructura basada en el contrato verificado (`IdentityMatcher` del repo Z1 presente), y deja como comentario que la alternativa de Z2 existe pero no se puede verificar ni replicar su lógica interna.

### 2. Defectos conocidos del código de referencia: ¿replicar o corregir?

El matcher de referencia (`ISIdentityMatcher`) tiene un defecto confirmado por lectura directa: la variable que debería comparar el apellido en realidad vuelve a comparar el nombre (copy-paste). **Regla:** el skill no decide esto por su cuenta. Genera el código con la corrección aplicada por defecto (es la opción más segura), pero deja un comentario `TODO` explícito ofreciendo la alternativa de replicar el defecto por paridad con otras ingestas ya desplegadas, y por qué alguien podría querer eso (consistencia operativa entre ingestas del mismo bureau).

### 3. Validación cruzada contra fuentes externas (ej. registros oficiales)

Si un documento de negocio afirma que la ingesta requiere validar la identidad contra una fuente externa (ej. "Registraduría"/"BDIIREGI" en el caso SISBEN), pero el código del repo no tiene ninguna implementación de esa validación, el skill **no la implementa**. La declara como pendiente de confirmación con el dueño de dominio, citando ambas fuentes en conflicto (la de negocio que la afirma, y la ausencia de evidencia en código).

### 4. Reglas de negocio retiradas verbalmente

Si en algún documento aparece una regla de negocio descrita como "ya no aplica" o "fue retirada", pero esa retirada no consta en ninguna fuente formal (HU, ticket, decisión registrada), el skill trata la regla como **prohibida de codificar**, no como "opcional". No se asume que la ausencia de la regla en el código es evidencia de que fue retirada correctamente — podría simplemente no haberse implementado nunca.

### 5. Campos del contrato de identidad sin uso confirmado en el algoritmo real

Si un campo aparece en el archivo de ejemplo de atributos identificadores pero no se usa en ningún lugar del algoritmo de scoring (`doesMatch`) ni de blocking (`nearNeighborKeys`) que el skill puede verificar en el código, ese campo se declara en el código generado como comentado, con nota explícita, no se incluye en el array activo.

## Formato del `TODO` en el código generado

Siempre con esta forma, para que sea buscable y auditable:

```scala
// TODO(decisión de negocio pendiente, <fuente que lo documenta>):
// <una frase describiendo la decisión exacta que falta>
// Este skill asume <valor de arranque usado>, NO es una decisión técnica cerrada.
```

Si el usuario que invoca el skill ya tiene la decisión tomada (porque la aportó explícitamente como parámetro), el `TODO` no se genera para ese punto — se usa el valor confirmado directamente, con una cita de dónde vino esa confirmación.
