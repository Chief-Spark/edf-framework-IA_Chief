# Catálogo de decisiones de negocio que este skill NUNCA resuelve solo

Lee esto completo antes de generar cualquier artefacto. Es el documento más importante del skill, igual que en `edf-pinning` y `edf-synthesis` — pero State Calculation tiene el bloqueo de completitud **más severo de las tres etapas**: en el ejemplo histórico del piloto SISBEN (no es fuente: la ingesta SISBEN vigente se rige por su spec §13), de 25 campos de negocio esperados, **solo 2 tienen cadena de mapeo verificada de principio a fin**.

## 1. Mapeo de campos: el bloqueo principal, con precedente numérico exacto

Para que un campo entre a `fields[]` de Foundation o a `select`/`aggregates` de una sección de Calculation, necesitas una fuente que confirme **específicamente ese campo**: el nombre de campo curado SISBEN real de origen, Y el nombre que debe tener en Foundation/Calculation. Un nombre XPM de destino propuesto por un documento de negocio, sin esa cadena completa, no es suficiente.

El precedente exacto de este proyecto (`Diseño/yaml-skill-statecalc.yaml`, sección `mapeo_campos`) es la referencia de cómo tratar esto:
- `tipoDocumento` → `personIdType` y `numeroDocumento` → `personIdNumber`: **nivel A, `[C]`**. Tienen cadena completa — campo curado SISBEN confirmado (`standardizedIdType`/`standardizedIdNumber`, ya usado en Pinning), nombre Foundation confirmado por aparecer literalmente en el fixture real (`personIdType`) o por construcción directa por analogía exacta con un ejemplo real (`personIdNumber`).
- Los otros 23 campos (`primerNombre`, `segundoNombre`, `primerApellido`, `segundoApellido`, `fechaNacimiento`, `genero`, `nivelSisben`, `estrato`, `codMunicipio`, y el resto del listado de 25): **nivel B o `[A:sin-clasificar]`**. Solo tienen un nombre XPM de destino propuesto por `Docs-General/SISBEN_V4_Synthesis_StateCalculation_Levantamiento.md` §8.3 — ninguno tiene su nombre de campo Foundation confirmado por una fuente verificable.
- El propio YAML de referencia **no inventó** el nombre Foundation de esos 23 campos a partir del nombre XPM en español (ej. nunca asumió que `primerNombre` se llamaría `firstName` o literalmente `primerNombre` en Foundation) — los dejó como hueco colectivo explícito, no uno por uno simulando una completitud que no existe.

Replica exactamente este nivel de rigor. Si el usuario insiste en que generes un campo de nivel B, inclúyelo solo si insiste explícitamente, y márcalo en tu respuesta de texto como "no confirmado por fuente" — nunca lo presentes con el mismo nivel de certeza que `personIdType`/`personIdNumber`.

## 2. `groupBy` plano vs. `grouping.groupBy` anidado — no es una decisión pendiente, es una regla ya fijada

A diferencia del punto anterior, esto **no se vuelve a plantear como pregunta abierta**. `Diseño/logica-statecalc.md` §5.4 y `Diseño/yaml-skill-statecalc.yaml` (`precondiciones_bloqueantes`) ya fijaron la regla dura: generar siempre `groupBy` plano, nunca `grouping.groupBy` anidado, hasta que el equipo verifique empíricamente (con una ejecución real controlada, fuera del alcance de este framework) cuál de las dos formas funciona de verdad. Este skill aplica esa regla sin excepción y sin preguntarle al usuario — ver `references/esquema-foundation-calculation.md` §2.1 para el detalle completo.

## 3. Discrepancia de nombre de destino XPM entre dos documentos de negocio

`Docs-General/contratos_frontera.md` §2 propone nombres de destino para campos de ubicación (`codMunicipioSisben`, `codDepartamentoSisben`) distintos a los que usa `Docs-General/SISBEN_V4_..._Levantamiento.md` §8.3 (`codMunicipio`, `codDepartamento`) para, aparentemente, los mismos campos de origen. **Esta discrepancia no se resuelve aquí.** Si el usuario pide generar uno de estos campos, se señala la discrepancia entre ambas fuentes de negocio en vez de elegir una en silencio — ninguna de las dos tiene además su cadena de mapeo completa a un campo curado SISBEN confirmado, así que de todas formas caen en la regla de nivel B/no generar (§1).

## 4. Frontera con Hydration/XPM: fuera de alcance de este skill, no una ausencia a rellenar

El documento de negocio describe decisiones que pertenecen a la capa de prioridad entre fuentes del XPM compartido (`ColombiaCustomOnSectionProcessorAlpha.scala`, priorización de SISBEN frente a RNEC/Migración Colombia/DAS) y a la pregunta abierta de si SISBEN participa en `bestName`. **Ninguna de las dos es responsabilidad de este skill.** Ver `Diseño/yaml-skill-statecalc.yaml` (`fuera_de_alcance_frontera_xpm`) para el detalle completo de qué se excluyó y por qué. Si el usuario pide generar configuración de prioridad de fuentes o resolver `bestName`, el skill señala que eso vive en una capa distinta (frontera con Hydration/XPM, no State Calculation per se) y no genera nada para ese pedido.

## 5. Sin código Scala — la necesidad de lógica propia es una decisión de alcance

Este skill nunca genera código Scala: ni un enricher (`FoundationStateEnrichmentProcessor`), ni un `CustomOnSectionProcessor` del motor genérico, ni `ColombiaCustomOnSectionProcessorAlpha` (Z2, no verificable). Que la ingesta lo necesite **no** se da por descartado: el passthrough `MOST_RECENT_VALUE` describe la forma de una sección, pero no cumple por sí solo una regla de vigencia ni de fuente. Para SISBEN, la regla del corte vigente (HU SISBEN R-26) exige filtrar por fuente y por corte (`references/modo-colombia.md`), y si eso requiere lógica propia en el fork es `[A:CQ-006]`, una decisión de alcance del PO (o una regla de corte, `edf-orquestador/references/03` §4). La versión anterior de este párrafo daba el passthrough por suficiente para SISBEN citando `Diseño/templates-scala/statecalc/README.md`, que no existe en el repositorio y que R-26 contradice (HF-77).

## 6. Ausencia de motor de calidad de datos de 4 dimensiones

`Diseño/logica-statecalc.md` §2 reconfirma, con una segunda búsqueda exhaustiva en esta sesión, que no existe en este repo ningún motor de completitud/validez/unicidad/consistencia. Si el usuario pide generar configuración de "quality threshold" o similar para State Calculation, no existe tal mecanismo en el código real de esta etapa — señala la ausencia en vez de inventar una clave de configuración que no se usa en ningún lugar verificado.

## Formato de la nota en el artefacto generado

Cuando un campo o sección queda sin resolver, dilo en tu respuesta de texto con esta forma, no solo en un comentario que después se pierde (igual formato que `edf-pinning` y `edf-synthesis`, para consistencia entre los tres skills de la iniciativa):

> **Pendiente de confirmar:** `<campo o sección>` — `<qué falta exactamente>`. Fuente consultada: `<cita o "ninguna">`.

Si el usuario confirma la decisión en la misma conversación, inclúyela en el artefacto citando "confirmado por el usuario en esta sesión" en vez de inventar una fuente de código o de negocio que no existe.
