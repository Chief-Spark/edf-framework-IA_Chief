# Convenciones de oxygen-synthesis — nomenclatura y antipatrones a conocer

Extraído de `DevPatterns/oxygen-synthesis-patterns-agent.json` (análisis de patrones real del repo) y verificado contra `Diseño/logica-synthesis.md`. Como este skill solo genera **JSON/HOCON declarativo**, no Scala, la mayoría de los antipatrones de código del repo no aplican directamente — pero dos cosas sí importan para no reproducir confusión ya documentada en el propio proyecto.

## 1. La discrepancia de nombre del producto — ten cuidado al escribir rutas y comentarios

El producto se llama "Synthesis" en toda la documentación de negocio, pero **el 100% del código real vive bajo el paquete `com.experian.edf.oxygen.consistencycheck`**, y la clase ejecutable es `SynthesisApp` dentro de ese paquete `consistencycheck`. Esto ya se documentó también en `Diseño/logica-synthesis.md` §2.1.

**Por qué te importa aunque no generes Scala:** si el usuario pide generar la clave HOCON `oxygen.stage.consistencyCheck.*` y tú escribes `oxygen.stage.synthesis.*` porque "suena más natural", el motor real no la va a leer — las claves de configuración reales usan el nombre interno `consistencyCheck`, no `synthesis`. Siempre usa el prefijo HOCON verificado en `Diseño/logica-synthesis.md` §4.1 (`oxygen.stage.consistencyCheck`), nunca lo que "parece correcto" por el nombre de producto.

## 2. Nombres de archivo de ejemplo del fixture — no son genéricos

El `.conf` de ejemplo del repo declara `eventTypeList = ["sisben","jurad","judicial"]` — estos tres valores son del fixture local de ejemplo del propio repositorio InnerSource, **no una lista genérica que debas copiar**. La clave guarda **tipos de archivo** (`fileTag`), no el `eventType`: el motor la lee como `listFileTypes` (`SparkConsistencyChecker.scala:46`), pide al Process Management la fecha más reciente de cada valor (`:87`) y solo la usa con `checkFileTagEnabled = true` para el `voidIndicator` (`:119-131`). **No filtra eventos** (HF-69). En una aplicación propia, el único valor que corresponde es el `fileTag` real confirmado de la ingesta; en modo Colombia no se escribe (la lista es de Colombia, SKILL «Modo Colombia»). Nunca copies los tres valores solo porque aparecen en el ejemplo.

## 3. Verbos y nombres que vas a encontrar citados en comentarios de configuración

Si el usuario pide que anotes el bloque HOCON con comentarios explicativos (buena práctica, consistente con cómo se documentó `yaml-skill-synthesis.yaml`), usa esta terminología real del dominio en vez de inventar sinónimos:

- El verbo del dominio es **`synthesize`** (no "consolidar" ni "reconciliar" como términos de código, aunque sean sinónimos válidos en prosa).
- El motor real que ejecuta la reconciliación es `IncrementalEventSynthesizer`, no la clase `EventSynthesizer` (que existe en el código pero no tiene invocación activa confirmada — ver `Diseño/logica-synthesis.md` §6.1). Si mencionas el motor en un comentario, nombra el correcto.
- Los tres handlers son **LFP** (Latest For Period), **LAP** (Latest Across Period), **EUC** (Earliest Until Corrected) — esta es la nomenclatura ya corregida y adoptada para toda la iniciativa (ver `decisiones-pendientes.md` §1). Nunca uses "Latest Final Provisional" ni ninguna de las otras dos versiones en conflicto que existieron antes de la corrección.

## 4. Lo que NO debes replicar aunque lo veas en el repo real

Estos son antipatrones de **código Scala** confirmados en el repo (`logger.error` para info, `println` de diagnóstico, `null` como valor de dominio, un cuarto DSL redundante para reglas custom en vez de extender el existente). No aplican directamente a este skill porque no genera Scala, pero si el usuario te pide generar un comentario explicativo dentro del JSON o HOCON, no reproduzcas el estilo de los comentarios de código del repo real que **minimizan** advertencias serias (ej. el repo real no advierte en ningún comentario visible que el import de `SynthesisProcessor` está roto en `DomainDictionaryInternal.scala`). Si tu configuración generada depende de un comportamiento con una advertencia conocida (ej. el fail-silent de `ClassNotFoundException` al resolver un `synthesizer`), dilo explícitamente en el comentario, no lo omitas por brevedad.
