# Convenciones de oxygen-pinning — nomenclatura, estilo y antipatrones

Extraído de `DevPatterns/oxygen-pinning-patterns-agent.json` (análisis de patrones real del repo `oxygen-pinning`) y verificado contra el código fuente en `Diseño/logica-pinning.md`. Esta referencia existe para que el código generado por este skill **suene como código real del repo**, no como una invención genérica.

## 1. Nomenclatura de variables

| Sufijo | Significado | Cuándo usarlo |
|---|---|---|
| `BC` | Variable `Broadcast[T]` de Spark | Siempre que se difunda un valor de solo lectura a los ejecutores (ej. `identifyingAttributesBC`) |
| `Filename` | Ruta completa de un archivo de salida | `pinnedFilename`, `identityPinMapFilename` |
| `Dir` | Ruta de un directorio | `pinnedDir`, `archivedDir` |
| `Format` | String de formato de IO | `readFormat`, `writeFormat` |
| `ClassName` | FQCN cargado por reflexión | `identityMatcherClassName`, `clusterSplitterClassName` |
| `Enabled` | Flag booleano de feature | `emitStockMetricsEnabled` |

Regla de estilo: camelCase, nombres largos y descriptivos. **No** se usa el prefijo `_` para vals privados (eso es un idioma de `oxygen-curation`, no de `oxygen-pinning` — no mezclar convenciones entre repos).

## 2. Verbos de método

| Prefijo | Uso | Ejemplos reales |
|---|---|---|
| `perform` | Ejecuta el algoritmo central | `performPinning`, `performStamping` |
| `save` | Persiste un artefacto | `saveIdentityPinMap`, `saveMergedPin` |
| `read` | Lee un recurso de entrada | `readCuratedEvents`, `readIdentifyingAttributes` |
| `get` | Deriva o calcula un valor | `getExactMatches`, `getVlePins` |
| `create` | Construye una instancia o estructura | `createHC`, `PinGenerator.createPin` |
| `does` | Predicado de comparación/score en `IdentityMatcher` | `doesMatch`, `doesMatchKA` |

**No existe** un verbo `pin...()` en el código real — el dominio usa `perform`/`stamp`, no "pin" como verbo.

## 3. Clases: sufijos reconocidos

`Processor`, `Matcher` (ej. `IdentityMatcher`, `ISIdentityMatcher`), `Splitter` (`ClusterSplitter`), `Generator` (`PinGenerator`), `DAO`, `Utils`, `Initializer`. El prefijo `Spark` se reserva para la única implementación concreta del motor (`SparkPinner`), nunca para los contratos de extensión.

## 4. Contrato obligatorio al extender `IdentityMatcher`

Toda implementación real que sobrescriba `doesMatch` **debe llamar primero a `super.doesMatch`** — es un comentario explícito ("MUST CALL SUPER") en el código de referencia `ISIdentityMatcher.scala`, no una sugerencia de estilo. Habilita el match por defecto de `subscriber`/`subscriberRelation` antes de evaluar cualquier lógica de scoring adicional. El código generado por este skill debe preservar esta llamada siempre.

## 5. Antipatrones confirmados en el repo — NO replicar en código nuevo

| Antipatrón | Por qué está mal | Corrección a aplicar |
|---|---|---|
| `logger.error(...)` para mensajes informativos | Contamina los logs de error con ruido operativo | Usar `logger.info` / `logger.debug` para hitos normales |
| `println` para volcar configuración | No pasa por el logger, no tiene nivel, no se puede silenciar | Un `logger.info` estructurado, una sola vez |
| `null` como valor de dominio (tuplas de N nulls, RDDs null) | Oculta el caso de fallo, fuerza checks `!= null` dispersos | `Option`, un ADT de resultado con sellado, o un case class nombrado |
| Tupla de 6+ elementos como retorno de un método | Ilegible, sin nombres de campo | `case class PinningResult(...)` |
| Recursión sin `@tailrec` cuando es cola-recursiva | Riesgo de stack overflow en colecciones grandes | Anotar `@tailrec` o convertir a bucle explícito |
| `val x = if (cond == None) true else false` | Comparación de `Option` con `==` en vez de métodos idiomáticos | `.isEmpty` / `.isDefined` |
| Acceso directo a `configMap(key)` sin manejo de ausencia | Lanza `NoSuchElementException` sin control | `configMap.getOrElse(key, default)` |
| Definir una función de manejo de fallo que nunca se invoca | Código muerto que simula seguridad que no existe | Si se declara `logProcessFailed`, envolver el cuerpo en `try/catch` y de verdad invocarla |
| Typos congelados en nombres públicos (`ENABLE_HF_BATCH_STATIC_PIN_INTERGRATION`, `Consistecny`) | Se propagan por todo el código que los referencia | En código NUEVO, deletrear correctamente — no hay compatibilidad que proteger |

## 6. Qué sí replicar: estructura general del repo

- Repo "extensión" que reabre paquetes del core (`com.experian.edf.oxygen.pinning.extension`), no redefine tipos base.
- Un solo miembro abstracto por contrato de extensión cuando sea posible (`IdentityMatcher` solo exige `matchingKeysForCelebrity`; todo lo demás tiene default razonable).
- Scaladoc en cada método público, incluso si es breve, siguiendo el estilo del código de referencia (`@param`, `@return`).
