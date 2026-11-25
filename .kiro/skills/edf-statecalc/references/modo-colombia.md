# Modo Colombia — configuración de State Calculation compartida (HF-77)

Para ingestas del bureau de Colombia (`bureau.colombia_innersource`) la configuración de State Calculation es **compartida**. Esta referencia explica qué entrega la skill en ese modo y por qué. Rutas Scala abreviadas: `…/statecalculation/` = `Motor-Oxigen/oxygen-statecalculation/src/main/scala/com/experian/edf/oxygen/statecalculation/`.

## 1. Por qué no hay un archivo propio por ingesta

| Hecho del motor | Evidencia | Consecuencia |
|---|---|---|
| La entrada es todo el sintetizado: `synthesizedDir/*/*` | `utils/StateCalcIOUtils.scala:174-177` | El registro Foundation de un PIN mezcla los eventos de **todas** las fuentes (`calculators/foundation/FoundationStateCalculator.scala:37-51`) |
| Los foundation de la corrida se fusionan: `domain` del primero, `fields` de todos con `addAll` | `utils/ContextUtils.scala:15-19` | El `domain` del archivo de la ingesta se ignora (`:17`). Un `name` repetido se procesa dos veces y el último `put` gana **para todos los registros de todas las fuentes** (`FoundationStateCalculator.scala:52-59`), incluidos los campos de nivel A |
| Las secciones de Calculation se fusionan igual | `utils/ContextUtils.scala:25-30` | Dos secciones con el mismo `name` comparten la función `where` (la última registrada gana) y la clave de salida en el XPM |
| MOST_RECENT_VALUE ordena por `asOfPeriod` todos los registros que recibe y toma el primero | `calculators/calculated/types/impl/MostRecentValue.scala:16-29` | **Sin un filtro por fuente**, la sección de la ingesta toma el registro más reciente de **cualquier** fuente del PIN (p. ej. un evento de Registraduría posterior deja la sección con nulos o con datos de otra fuente) |

## 2. Qué entrega la skill

1. **ART-071 — entradas `fields[]` de la ingesta** para integrar en el foundation compartido de Colombia (no un archivo que lo reemplace):
   - ningún `name` (ni alias) que ya exista en el foundation compartido. Si el campo ya existe y lee la misma clave curada con el mismo `dataType`, se **reutiliza** (no se vuelve a declarar). Si existe con otro `dataType` u otro significado, es un bloqueo N4 que se escala, no un nombre que se repite;
   - el `name` de un DERIVED es la clave curada publicada por Curation (`FoundationFunctions.scala:24-35`), sin `value`;
   - cada `field.X` de un CALCULATED es un campo Foundation declarado (DERIVED, o no DERIVED declarado antes) o inyectado (`pin`, `dataSource`, `subscriber`, `subscriberRelation`, `asOfPeriod`, `parentEventIds`) (`FoundationStateCalculator.scala:46-63`);
   - se prefieren campos **solo DERIVED**: no dependen de Nashorn. Un CALCULATED depende de `[A:OQ-TR-2]` (steering `07` §5.7).
2. **ART-072 — secciones `calculations[]` de la ingesta** para integrar en el calculation compartido:
   - **toda sección filtra por fuente** con un `where`: `field.dataSource` y `field.subscriber` los inyecta el motor (`FoundationStateCalculator.scala:47-48`); también sirve una bandera de fuente que publique Curation o el enricher del fork. El predicado exacto sigue la regla general: solo si la fuente o el usuario lo confirman;
   - el `where` es Nashorn: depende de `[A:OQ-TR-2]`. Mientras OQ-TR-2 esté abierta, la sección no se genera: queda como pendiente bloqueante. Una sección sin filtro **no** es una alternativa (da un dato erróneo visible al cliente);
   - el `name` de una sección con `where` forma la función JavaScript `aggregated_where_<name>` (`calculators/calculated/QueryFunctions.scala:75-82`), que se compila al construir el calculador (`CalculationStateCalculator.scala:50-52`): **sin `#`**, espacios ni otros caracteres inválidos, o la etapa falla al arrancar. Si el XPM debe mostrar literalmente `#sisben`, el renombrado queda fuera de Calculation y es una decisión del PO;
   - ningún `name` de sección que ya exista en el calculation compartido;
   - «sin sección» depende de `groupBy`: sin `groupBy`, un `where` sin registros da `null`; con `groupBy`, la lista sale vacía `[]` (`CalculationStateCalculator.scala:123-143`, `:162`).
3. **Lista de verificación del fork** (en la respuesta de texto; es precondición de ART-073, la prueba CA1 en el XPM compartido):
   - nombres existentes del foundation y del calculation compartidos (contra ART-071 y ART-072);
   - **modo delta**: `oxygen.stage.stateCalculation.deltaMode`, `deltaModeAccountLevel` y `doesComputeXpm` (`engines/SparkStateCalculator.scala:187-188`). En modo delta el motor lee solo el sintetizado delta (`:207-213`) y copia desde la generación de referencia el foundation y el XPM de los PIN sin cambios (`getAndSaveUnchangedV2`, `:246-271`): **un PIN sin eventos nuevos conserva su XPM anterior**, aunque haya salido del corte vigente. Ningún filtro de la configuración lo alcanza;
   - `aliasEnabled` (`computeAliasInFoundation`, ruta viva `getSCContext`, `SparkStateCalculator.scala:166-171`) si la ingesta usa `aliases[]`;
   - el enricher del fork (la App lo fija en código: `app/StateCalcApp.scala:15`) si un `where` lee campos que no están en Foundation;
   - JDK del runtime del fork y aceptación de Nashorn por Experian (OQ-TR-2; antecedente: `Motor-Oxigen/oxygen-statecalculation/migrate.md:12-16`, JDK 11 del motor InnerSource);
   - si la corrida de la ingesta lee el sintetizado de Colombia y escribe el XPM compartido.

## 3. Verificación mecánica

```
python3 tools/verificar-artefactos-etapa.py statecalc --modo colombia \
    --calculation <secciones-de-la-ingesta.json> --foundation <campos-de-la-ingesta.json> \
    --source-spec <insumos/source-spec-curation.yaml> \
    --campos-existentes <foundation-compartido.json | nombres.txt> \
    --secciones-existentes <calculation-compartido.json | nombres.txt> \
    [--campo-fuente <bandera-de-fuente> ...]
```

Hallazgos (salida 1): sección sin `where` o con un `where` que no lee `dataSource`, `subscriber` ni un `--campo-fuente`; `name` de sección con `where` (o de un CALCULATED) que no sirve como sufijo de una función JavaScript; campo o sección con un nombre de las listas existentes; DERIVED cuyo `name` no es clave curada publicada; `field.X` sin campo Foundation. Sin `--campos-existentes` o `--secciones-existentes` sale un aviso: el choque queda en la lista de verificación del fork. El modo delta y los demás hechos del fork no se pueden verificar mecánicamente.

## 4. Lo que no cambia

- La skill no genera Scala: ni el enricher que calcule el corte vigente ni un `CustomOnSectionProcessor`. Si la ingesta los necesita (p. ej. HU SISBEN R-26, corte vigente), es una decisión de alcance del PO (`[A:CQ-006]`) o una regla de corte (`edf-orquestador/references/03` §4).
- El passthrough MOST_RECENT_VALUE describe la **forma** de la sección (los atributos sin cálculos), no basta para cumplir una regla de vigencia ni de fuente.
