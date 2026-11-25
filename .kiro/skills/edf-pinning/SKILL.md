---
name: edf-pinning
description: Genera, para una ingesta nueva, el paquete config-driven completo de la etapa Pinning del pipeline Oxygen (framework EDF/IFR) — un IdentityMatcher en Scala parametrizado, el archivo identifying-attributes.json, y el bloque HOCON de pinning-application.conf. Usar siempre que el usuario pida crear, generar o escribir un procesador, matcher de identidad, o configuración de Pinning para una ingesta, mencione "config-driven" junto con Pinning, pida aplicar el patrón de un YAML intermedio tipo yaml-skill-<etapa>.yaml a una ingesta nueva, o pida resolución de identidad / entity resolution para el pipeline Oxygen — incluso si no nombra "IdentityMatcher" por su nombre de clase exacto. No usar para las etapas Synthesis o State Calculation (son skills distintos), ni para análisis de requerimiento de Pinning (eso es el tipo de análisis T3, no este skill de generación).
---

# Skill ConfigGen — Pinning

## Paso 0 · Habilitación por el expediente (obligatorio, framework EDF)

Esta skill es parte del framework `edf-framework-IA` y opera **debajo del Orquestador** (`.kiro/steering/00-framework-edf.md` §3). Antes de generar cualquier artefacto:

1. **Identifica la ingesta** (`<ingesta-id>`) y abre `expedientes/<ingesta-id>/estado-ingesta.json`. Si no existe → no generes; pide al Desarrollador que inicie el expediente con `edf-orquestador`.
2. **Verifica la habilitación:** en `etapas[]`, las etapas `curation_flow` **y** `curation_stock` deben estar en `aprobada`, `completada_manualmente` u `omitida` (esta última solo con aprobación de omisión del PO, `edf-orquestador/references/03` §4b), con `transicion_habilitada: true`. Tu etapa en el expediente es `pinning`.
3. **Verifica el insumo:** `expedientes/<ingesta-id>/insumos/yaml-skill-pinning.yaml` (formato de referencia: `.kiro/skills/edf-analisis/assets/examples/sisben/yaml-skill-pinning.yaml`) y `insumos/contrato-curation-pinning.md` (steering `11`) deben existir y figurar como `aprobado` en los artefactos de tu etapa. Si falta → no generes; deriva a `edf-analisis` ("prepara el insumo de Pinning para la ingesta <id>").
4. **Si algo falla:** responde con lo que falta, sin generar nada y sin ofrecer cómo saltarse el control. Deriva a `edf-orquestador`.

Excepción única: trabajo sobre fixtures sintéticos de evaluación (`evals/` o `assets/examples/`) fuera de una ingesta real. Decláralo al inicio de tu respuesta.

Al terminar: escribe la propuesta en `expedientes/<ingesta-id>/propuestas/pinning/` y entrega al Desarrollador la lista de artefactos (ruta, tipo, supuestos abiertos, resultado de las verificaciones) **para que el Orquestador la registre**. Esta skill **no escribe** `estado-ingesta.json` ni `bitacora.md`.

## Modo Colombia · reutilizar, no generar (decisión P1 / DA-12)

Para ingestas del bureau de Colombia (`bureau.colombia_innersource`), **no generes un `IdentityMatcher`**. Colombia ya tiene piezas compartidas (steering `11` §2.1; patrón: ingesta certificada MURIC, `.kiro/skills/edf-analisis/references/fuentes/pinning-muric.md`):

| Pieza | Qué haces |
|---|---|
| Lista de atributos compartida (`identifying-attributes-new.json`) | Verificar que el conf compartido de Colombia ya la referencia en `identifyingAttributesFile`. No la regeneras ni redefines la clave |
| Matcher compartido (`ColombiaIdentityMatcher`; `IdentityImpl` según otra fuente, steering `11` §6 #7) | Verificar que el conf compartido ya lo referencia en `identityMatcher`. No lo modificas ni redefines la clave |
| Claves que debe traer el evento curado | Verificar que `standardizedIdType` y `standardizedIdNumber` existan en `source-spec-curation.yaml` (las exige la regla 0 del matcher) |
| Bandera de fuente de la regla 1 | Depende de `OQ-TR-6`. Mientras esté abierta: **detente** en este punto y repórtalo como bloqueo; no elijas `isOfficialSource` ni propongas una bandera nueva por tu cuenta |

Si `OQ-TR-6` se resuelve con una bandera **nueva**, el cambio toca tres piezas compartidas (`identifying-attributes-new.json`, `IdentityAttributesKey`, `doesMatch`): riesgo N3 fijo (steering `06` §2.2). Solo entregas el diff como propuesta en `propuestas/pinning/`; lo aplica una persona en el repo.

En modo Colombia la aplicación de Pinning es **compartida**: lee todas las fuentes bajo `curatedDir/*/*` (`PinningIOUtils.scala:23-31`) y cada clave de `oxygen.stage.pinning` vale para todas (HF-59, AUD-P-11). Por eso **no se redefine ninguna clave compartida**: `identifyingAttributesFile`, `identityMatcher`, `reusePin`, `domainDictionaryFile`, `clusterSplitter`, `nearNeighborBlockSize`, `pinningVleRange`, `groupSSR`. Solo se verifica su valor vigente en el conf de Colombia. Tu entrega es:

1. el bloque HOCON de la ingesta **sin claves compartidas**: solo comentarios con cada clave a verificar en el conf compartido y su valor esperado (`assets/pinning-application-block.conf.template`, sección «Modo Colombia»);
2. la verificación de que las claves del evento curado existen;
3. la lista de bloqueos abiertos. Si la ingesta necesita otro valor en una clave compartida, no la escribes: es un bloqueo que se reporta.

Genera el bloque (1) con `python3 .kiro/skills/edf-pinning/scripts/render_bloque_colombia.py --insumo <yaml-skill-pinning.yaml> --contrato <contrato-curation-pinning.md> --out <propuestas/pinning/pinning-application-block.<ingesta>.conf>` (HF-66): valida el insumo con `tools/verificar-insumo-pinning.py`, copia literal la sección «Modo Colombia» de la plantilla y rellena sus huecos con el insumo, así que dos generaciones con el mismo insumo dan el mismo archivo. Verifícalo con `--verificar <bloque.conf> --dir-propuesta <propuestas/pinning>` (o `evals/verificar-artefacto.ps1 -Modo colombia`, HF-67): 0 líneas activas, la lista VERIFICAR completa con las rutas HOCON que lee el motor (`oxygen.stage.pinning.paths.*`, `identityMatcher`, `reusePin`, `oxygen.stage.curation.paths.curatedDir`, la integración estática con HBase, el chequeo de `*-common-application.conf`; `clusterSplitter` e `INPUT_REF_DIR` con nombre `[PM]`, HF-65) y ni matcher ni lista generados. Lo que el script no decide (explicar, escalar N4, la lista de bloqueos) sigue siendo tu respuesta.

Las secciones de `IdentityMatcher.scala.template` y de la aritmética del umbral **no aplican** (el matcher de Colombia es determinista: match / no match).

**`reusePin` en modo Colombia: nunca se escribe (HF-57).** `oxygen.stage.pinning.reusePin` se lee una sola vez para toda la aplicación (`SparkPinner.scala:223`) y decide para toda la corrida si se lee el identity-pin-map de la generación de referencia (`:409`). Con `false`, **todos** los PIN de **todas** las fuentes se renumeran como `zipWithUniqueId + pinningVleRange` (`:979-985`), sin error. Con `true`, una persona que ya existe recibe su PIN (`:888-905`) y una nueva recibe uno nuevo (`:881-887`): es la única forma de cumplir el escenario «persona existente recibe ese PIN». Cuando una spec o una HU dicen «ingesta nueva → `reusePIN = false`», eso significa que **la ingesta no tiene generación previa propia** (sus identidades no están en el identity-pin-map, centinela `-1L`, `.kiro/skills/edf-analisis/references/logica-pinning.md` §8.1), no que se cambie la clave global. Cambiar la clave en la aplicación compartida es **N4** (steering `06` §3: no se propone como acción; se documenta y se escala). Registra el valor de la fuente como dato en tu entrega y no lo conviertas en `reusePin = false`.

**Colapso silencioso con el matcher o la lista de referencia (HF-58).** Nunca heredes como «arranque» `ISIdentityMatcher` (o `RIIdentityMatcher`) ni la lista de ejemplo `identifying-attributes.json` (`nationalId`, `firstName`, `cellPhone`…) con eventos de Colombia (`standardizedId*`). Esos slots no existen en el evento, así que todos los atributos valen `""` (`IdentityMatcher.scala:46-49`) y todas las identidades quedan iguales y se agrupan por `identityId` en una sola **antes** de compararse (`SparkPinner.scala:1756-1770`). La llave de vecinos `""` se filtra con `groupSSR = true` (`:676`, valor por defecto) y los bloques de `nearNeighborBlockSize` (700) o más se descartan sin comparar (`:719-723`). Resultado: un solo PIN para todas las personas, sin error. Si el conf compartido no trae el matcher y la lista de Colombia, es un bloqueo.


Este skill convierte un mapeo de identidad ya confirmado (normalmente ya resuelto en un YAML intermedio como `yaml-skill-pinning.yaml`) en un paquete de artefactos listos para revisión humana: un `IdentityMatcher` en Scala, su `identifying-attributes.json`, y el bloque HOCON de configuración que los conecta.

El principio que gobierna todo lo que sigue: **genera estructura confirmada, nunca decisiones de negocio inventadas.** Cuando algo no está confirmado por una fuente verificable, el artefacto generado lo deja explícito con un `TODO` citado — nunca lo rellena con el valor que parece más razonable. Esto no es una preferencia de estilo: es la misma disciplina con la que se construyó el precedente de este skill, `Diseño/templates-scala/pinning/SisbenIdentityMatcher.scala.template`, y existe porque un valor inventado que se transcribe a producción sin que nadie note que era un placeholder es mucho más costoso de corregir que un hueco honesto.

## Antes de generar nada: reúne el insumo

Necesitas, como mínimo, por cada atributo identificador de la ingesta:

1. El nombre del slot que usará el motor (`atributo_motor`, ej. `nationalId`).
2. El campo curado real de origen (el nombre que trae el evento curado de la etapa Curation).
3. Si tiene peso en el scoring de `doesMatch`, y cuál — o si solo participa en blocking, o en ninguno.
4. Si está **confirmado** (estado `[C]`) o es solo una propuesta sin confirmar (`[A:...]`, `[P]`).

Si el usuario ya tiene un YAML intermedio de Pinning para su ingesta (siguiendo la estructura de `.kiro/skills/edf-analisis/assets/examples/sisben/yaml-skill-pinning.yaml`), toma el insumo directamente de ahí — es exactamente el formato que este skill espera, sección `mapeo_identidad.atributos` y `reglas_matching_por_campo`. Si no existe ese YAML, pide al usuario el insumo campo por campo; no lo infieras de un documento de negocio sin marcarlo como propuesta no confirmada.

Si el usuario no tiene claro cuáles de sus atributos están confirmados y cuáles no, trata todo lo que no venga acompañado de una cita de fuente (código real, HU formal, o decisión explícita del propio usuario en la conversación) como no confirmado. La ambigüedad se declara, no se resuelve adivinando — ver `references/decisiones-pendientes.md` para el catálogo completo de los puntos donde esto típicamente ocurre.

## El flujo de generación

### 1. Clasifica cada atributo por estado de confirmación

Separa el insumo en dos grupos: atributos `[C]` (van al código activo) y todo lo demás (`[A:...]`, `[P]`, o sin marcador — van comentados, con nota). Nunca mezcles ambos grupos en el array activo de `identifying-attributes.json` ni en los `val` descomentados del `object` de claves del matcher.

### 2. Verifica la aritmética del umbral antes de escribir una sola línea de scoring

Con los pesos de los atributos `[C]`, calcula si es posible alcanzar el umbral de similitud (`similarityThreshold`, por defecto 0.6 heredado del motor de referencia) sin que el identificador principal coincida exactamente. Lee `references/contrato-identitymatcher.md` §4 para el procedimiento exacto. Esta verificación va al código generado como comentario — es información que el revisor humano necesita antes de aprobar el artefacto, igual que se hizo para SISBEN (donde se descubrió que, sin `nationalId` exacto, el matcher nunca alcanza el umbral con los campos confirmados).

### 3. Genera el `IdentityMatcher` desde `assets/IdentityMatcher.scala.template`

Copia la plantilla y sustituye cada `{{PLACEHOLDER}}`:
- `{{INGESTA_NOMBRE}}`: nombre de la ingesta en PascalCase (ej. `Sisben`, `Jurad`).
- `{{PAQUETE_BASE}}`: normalmente `com.experian.edf.oxygen.pinning`, salvo que el usuario indique otro.
- Los bloques de atributos y pesos: expándelos a líneas reales de Scala siguiendo exactamente el patrón ya comentado en la plantilla (un `val` por atributo confirmado, un `if` por término de scoring).
- Cada `TODO` de la plantilla permanece como `TODO` **salvo que el usuario ya haya aportado esa decisión explícitamente** — en ese caso, resuélvelo con el valor dado y cita de dónde vino.

Antes de escribir el archivo final, lee `references/convenciones-oxygen-pinning.md` para la nomenclatura exacta (sufijos `BC`/`Filename`/`ClassName`, verbos de método) y la lista de antipatrones confirmados en el repo real que el código generado nunca debe reproducir (uso de `logger.error` para mensajes informativos, `null` como valor de dominio, tuplas largas sin nombre, etc.).

### 4. Genera `identifying-attributes.json` desde `assets/identifying-attributes.json.template`

Un array plano de strings con los nombres de slot `[C]`, en el mismo orden que aparecen en el matcher generado. Elimina los comentarios `//` de la plantilla — el JSON final debe ser válido. Si algún atributo quedó excluido por no estar confirmado, dilo explícitamente en tu respuesta al usuario, no solo en un comentario que luego se borra.

### 5. Genera el bloque HOCON desde `assets/pinning-application-block.conf.template`

En modo Colombia usa solo la sección «Modo Colombia» de la plantilla: ninguna clave compartida se escribe (ver arriba). Fuera de modo Colombia, completa solo las claves para las que tengas una fuente o una decisión explícita del usuario. `clusterSplitter` y `paths.domainDictionaryFile` llevan "verificar en el fork" (AUD-P1): el nombre de la primera no tiene evidencia en la copia del motor y la ausencia de la segunda solo deja un `warn`. Las claves con conflicto conocido entre defaults (como `groupSSR`, documentado en la plantilla) nunca se completan con uno de los dos valores en conflicto sin que el usuario lo decida explícitamente — preséntale el conflicto, no lo resuelvas tú.

### 6. Entrega los tres artefactos juntos, con un resumen de huecos

Cierra siempre con una lista corta de qué quedó pendiente de confirmar (los `TODO` del código, las claves sin completar del HOCON, los atributos excluidos) para que el usuario sepa de un vistazo qué falta antes de poder aprobar el paquete. No entierres esto solo en comentarios de código — repítelo en tu respuesta de texto.

## Qué este skill nunca hace

- No decide qué `IdentityMatcher` base usar (genérico, uno de otro repositorio, uno nuevo) — eso es una decisión de arquitectura del equipo.
- No implementa validación cruzada contra fuentes externas sin confirmación explícita y evidencia de que el motor la soporta.
- No codifica reglas de negocio que algún documento describe como "retiradas" o "ya no aplican" sin que esa retirada conste en una fuente formal.
- No transcribe el código generado a un repositorio protegido (fuera de Z1) — el artefacto es siempre una propuesta para revisión humana.
- No genera `pinning-application.conf` completo ni toca `env.conf`/`spark.conf` — solo el bloque de negocio parametrizable.

Si en algún punto te encuentras tentado a "simplemente elegir algo razonable" para que el código compile o se vea completo, detente: lee `references/decisiones-pendientes.md` primero. Casi siempre el punto en el que dudas ya está catalogado ahí, con la razón de por qué no se resuelve por inferencia y el formato exacto del `TODO` que debe quedar en su lugar.

## Referencias

- `references/convenciones-oxygen-pinning.md` — nomenclatura real del repo y antipatrones confirmados a evitar.
- `references/contrato-identitymatcher.md` — el contrato Scala exacto a extender y las nueve dimensiones de parametrización.
- `references/decisiones-pendientes.md` — el catálogo de puntos de decisión de negocio que este skill nunca resuelve solo, con el formato de `TODO` a usar.
