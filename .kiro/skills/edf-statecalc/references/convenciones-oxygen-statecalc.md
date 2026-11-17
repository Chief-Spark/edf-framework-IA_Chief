# Convenciones de oxygen-statecalculation — nomenclatura y antipatrones a conocer

Extraído de `DevPatterns/oxygen-stateCalculation-patterns-agent.json` (análisis de patrones real del repo) y verificado contra `Diseño/logica-statecalc.md`. Como este skill solo genera **JSON declarativo** (Foundation y Calculation), no Scala, la mayoría de los patrones de código del repo no aplican directamente — pero varios sí importan para no reproducir confusión ya documentada.

## 1. Dos clases distintas se llaman `AggregateFunction` — usa solo la activa

Existen **dos tipos con el mismo nombre simple** `AggregateFunction` en el repo:
- `com.experian.edf.oxygen.statecalculation.config.calculation.AggregateFunction` — un **enum Java muerto** (`SUM, MIN, MAX, COUNT, AVERAGE`), con cero referencias de uso real en todo el código fuente.
- `com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.AggregateFunction` — la clase Scala **activa**, un Strategy genérico `AggregateFunction[T]`, la que de verdad resuelve los valores de `"function"` en el JSON.

**Por qué te importa aunque no generes Scala:** si en algún comentario o documentación generada necesitas referenciar la clase que resuelve `SUM`/`AVERAGE`/etc., nunca cites el enum Java — está muerto y no participa en la resolución real. Los valores de `function` válidos en el JSON (`SUM`, `MIN`, `MAX`, `COUNT`, `AVERAGE`) se resuelven contra el Strategy Scala, con **coincidencia exacta de mayúsculas/minúsculas**, no contra el enum.

## 2. `type` de sección es case-insensitive, `function` de agregación NO lo es

El campo `CalculationSection.type` (`AGGREGATES`, `MOST_RECENT_VALUE`, etc.) se resuelve con `.toUpperCase` — case-insensitive. Pero el campo `AggregatesConfiguration.function` (`SUM`, `AVERAGE`, etc.) se busca con **coincidencia exacta**, sin normalización. Si generas `"function": "sum"` en minúsculas, el motor real lanzará `StateCalculationConfigException` porque no encuentra esa clave exacta en el mapa de funciones registradas. Siempre genera los valores de `function` en mayúsculas, tal como aparecen en los ejemplos verificados.

## 3. El typo `erich`/`enrich` es del código real — no lo repliques en artefactos nuevos sin aclarar

El método del contrato `FoundationStateEnrichmentProcessor` se llama `erichFoundationState` (typo confirmado, no `enrichFoundationState`) en el código real. Esto es relevante solo si en algún momento el usuario pide documentar o referenciar ese contrato en un comentario del JSON generado — cítalo con el nombre real (`erichFoundationState`) si estás describiendo el código existente, pero no es necesario ni correcto introducir el mismo typo en nombres nuevos que tú generes (este skill no genera ningún archivo Scala que implemente ese contrato, así que en la práctica esto rara vez aplica).

## 4. No asumas generación dinámica de esquema Spark — está deshabilitada

El repo tiene clases (`CalculationSchemaGenerator`, `CalculationType.getSchema`) pensadas para derivar un `StructType` de Spark automáticamente a partir de la configuración JSON. **Están deshabilitadas**: el método principal de `CalculationSchemaGenerator` tiene el cuerpo completamente comentado. Si el usuario pide que el JSON generado "sea compatible con el esquema Spark automático", no existe tal mecanismo activo en este repo — no lo asumas ni lo prometas en ningún comentario generado.

## 5. No repliques el patrón `while` con efecto secundario en la condición (antipatrón confirmado, no aplica a este skill pero sí a cualquier Scala futuro)

`XpmEventDAO.saveStateEvents` usa el idioma `while ({ ...; condicion })`, que mezcla efectos secundarios dentro de la condición del bucle — confirmado como antipatrón en el propio análisis del repo. Este skill no genera Scala, así que no lo reproduce directamente, pero si en el futuro se ampliara este skill para generar algún `CustomOnSectionProcessor`, este patrón **no debe copiarse como ejemplo de buen estilo**.

## 6. Nombres de archivo de ejemplo del fixture — no son genéricos

Los JSON de ejemplo del repo (`statecalc-foundation-local.json`, `statecalc-calculation-local.json`) usan nombres de dominio y de sección específicos del fixture local (`AggregationOnBalanceGroupBySubSR`, `dataSources`, `counterparties`, etc.) — son del dominio de ejemplo del propio repositorio InnerSource, no una convención de nombres que debas copiar para una ingesta nueva. El único nombre de sección o campo que corresponde a una ingesta nueva es el que confirme el usuario o una fuente verificable para esa ingesta específica.

## 7. Verbos y nombres reales del dominio, si anotas comentarios

Si el usuario pide anotar el JSON generado con comentarios explicativos (el JSON real no admite comentarios `//` — solo aplicaría a una plantilla intermedia, nunca al archivo final), usa esta terminología real:

- Las dos fases del motor se llaman **Foundation** (enriquecimiento fila por fila) y **Calculation** (agregación tipo SQL), nunca "fase 1"/"fase 2" genéricos sin nombre.
- El resultado consolidado final que alimenta el XPM se llama **Behavioral Profile** en la documentación del propio repo.
- Los "campos base" que el motor inyecta automáticamente (`dataSource`, `subscriber`, `subscriberRelation`, `asOfPeriod`, `pin`, `parentEventIds`) se llaman internamente *guard rail fields* en el código — no los confundas con los "Guard-Rail Attributes" de la etapa Validation (`04_EDF_RULES_BY_STAGE.md`), que son un concepto de negocio completamente distinto con el mismo nombre genérico en inglés.
