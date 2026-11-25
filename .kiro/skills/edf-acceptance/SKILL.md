---
name: edf-acceptance
description: |
  Genera y verifica la configuración de la etapa Acceptance de EDF/Oxygen para una fuente nueva
  (bloque HOCON de ingesta, condiciones con nombre, lookup de severidades opcional, pruebas de
  expresiones y fixture sintético), a partir de una source-spec. Acceptance es 100 % configuración:
  no genera código Scala de producción. Ejemplo de forma: SISBEN (no es fuente). Úsalo cuando un
  ingeniero de datos deba decidir qué archivos y registros validados pasan a Curation.
metadata:
  stage: acceptance
  framework: EDF/Oxygen
  pilot: SISBEN V4
  version: "0.1.0"
  human-clarification: "true"
  plan-creation: "false"
  artefact-verification: "true"
---
# EDF/Oxygen · Acceptance stage

## Paso 0 · Habilitación por el expediente (obligatorio, framework EDF)

Esta skill es parte del framework `edf-framework-IA` y opera **debajo del Orquestador** (`.kiro/steering/00-framework-edf.md` §3). Antes de generar cualquier artefacto:

1. **Identifica la ingesta** (`<ingesta-id>`) y abre `expedientes/<ingesta-id>/estado-ingesta.json`. Si no existe → no generes; pide al Desarrollador que inicie el expediente con `edf-orquestador`.
2. **Verifica la habilitación:** en `etapas[]`, la etapa `validation` debe estar en `aprobada`, `completada_manualmente` u `omitida` (esta última solo con aprobación de omisión del PO, `edf-orquestador/references/03` §4b), con `transicion_habilitada: true`. Tu etapa en el expediente es `acceptance`.
3. **Verifica el insumo:** `expedientes/<ingesta-id>/insumos/source-spec-acceptance.yaml` (valida contra `assets/schema/source-spec.schema.yaml` de esta skill) debe existir y figurar como `aprobado` en los artefactos de tu etapa. Si falta → no generes; deriva a `edf-analisis` ("prepara el insumo de Acceptance para la ingesta <id>").
4. **Si algo falla:** responde con lo que falta, sin generar nada y sin ofrecer cómo saltarse el control. Deriva a `edf-orquestador`.

Excepción única: trabajo sobre fixtures sintéticos de evaluación (`evals/` o `assets/examples/`) fuera de una ingesta real. Decláralo al inicio de tu respuesta.

Al terminar: escribe la propuesta en `expedientes/<ingesta-id>/propuestas/acceptance/` y entrega al Desarrollador la lista de artefactos (ruta, tipo, supuestos abiertos, resultado de las verificaciones) **para que el Orquestador la registre**. Esta skill **no escribe** `estado-ingesta.json` ni `bitacora.md`.

## Requisito del framework · Revisión manual a nivel de archivo (DIF-1 / DA-14)

El PO aceptó que el estado `MANUAL_REVIEW` de la especificación se implemente **solo a nivel de archivo**, sin modificar el motor:

| Estado | Configuración | Resultado |
|---|---|---|
| `REJECTED` | `reject`: `average("qs.RQS") < <piso>` | `rejected/files` |
| `MANUAL_REVIEW` | entre `<piso>` y el umbral de aceptación: ni `reject` ni `accept` | el archivo queda **pendiente** en `validated/` (la cola) |
| `ACCEPTED` | `accept`: `average("qs.RQS") >= <umbral>` | `accepted/` |
| Decisión humana | `manualAccept` / `manualReject` con `externalAction("<clave>") == "<valor>"` | sale de la cola en la corrida siguiente |

Tres parámetros siguen **abiertos** (DIF-1.2 piso, DIF-1.3 de dónde lee el motor `externalAction`, DIF-1.4 quién decide y plazo). Mientras lo estén:
- genera con `intervencion_manual.habilitada: false` y `reject`/`accept` complementarios (decisión D7 vigente);
- en la entrega, declara que `MANUAL_REVIEW` **no** está configurado y por qué;
- no inventes el piso ni la clave de `externalAction`.

Cambiar el umbral o agregar el piso es riesgo N3 (steering `06` §2.2).


Responde: **¿qué datos tienen calidad suficiente para seguir, y cuáles no?** Lee los
`TaggedValidatedEvent` de Validation, decide por archivo y por registro, y entrega a Curation.
El motor (`SparkAcceptor`) no se modifica: todo lo que produce este skill es configuración HOCON,
datos y pruebas.

> Advertencia central: en Acceptance **un error de sintaxis no falla**. Una expresión de registro
> rota **acepta** todos los registros; una de archivo rota deja el archivo **pendiente**. Por eso
> cada expresión sale con pruebas y con simulación.

Este skill es autocontenido: todo lo que necesita está en esta carpeta.

## Estructura

| Ruta | Contenido |
|---|---|
| `references/alcance-y-glosario.md` | Qué hace y qué no; glosario |
| `references/patrones.md` | Patrones ACC-P*, antipatrones ACC-AP*, superficie de extensión, techo |
| `references/inventario-plantillas.md` | IN-01..IN-08, ACC-TPL-01..06, orden, motor de plantillas |
| `references/especificacion-sisben.md` | EJEMPLO HISTÓRICO: explica el ejemplo de forma; no es la spec de ninguna ingesta ni se cita como fuente |
| `references/dsl-config-reference.md` | Claves HOCON, `orden_real_de_evaluacion`, `expresiones_dsl` |
| `references/runbook-ejecucion.md` | Local, Docker, EMR; problemas frecuentes |
| `references/interpretar-salida.md` | Logs, rutas, métricas, señales de alarma |
| `references/decisiones-y-abiertos.md` | D*, C-ACC-*, F-01..F-17, supuestos S-ACC-*, abiertos |
| `assets/templates/` | Plantillas `.tpl` + `manifest.yaml` (orden de generación) |
| `assets/schema/source-spec.schema.yaml` | Schema de la spec (define todos los huecos) |
| `assets/examples/sisben/` | Spec de ejemplo (solo forma, no es fuente), resumen esperado y salida generada de ejemplo |
| `assets/checklist-entrega.md` | Checklist previo a la propuesta |
| `scripts/` | `render_templates.py` (generador), `simular_decision.py`, `hocon_check.py`, `tests/` (pytest) |
| `scripts/java/` | Generador Java anterior: solo referencia histórica, no se usa (`scripts/DEPRECADOS.md`) |

Requisitos locales: Python 3.10+ con PyYAML y jsonschema para generar y simular; `pytest` para las
pruebas del skill. Sin Java, Maven, Scala ni el classpath de Oxygen.

## Regla de ejecución: generar, nunca compilar

Esta skill **solo genera archivos**, con Python 3:

- **Nunca** ejecutes `mvn`, `javac`, `java -jar`, `sbt`, `gradle`, `spark-submit` ni Docker, ni compiles el código generado. Integrar en el fork, compilar y ejecutar lo hace el ingeniero, fuera de esta skill (`references/runbook-ejecucion.md` §1 a §4).
- La carpeta `scripts/java/` es el generador Java anterior, conservado solo como referencia histórica (`scripts/DEPRECADOS.md`). No se usa.
- Si falta PyYAML o jsonschema, detente y pide al Desarrollador que los instale (`pip install pyyaml jsonschema`). No busques otra forma de generar.

## Guardrails (no negociables)

1. **Aditivo**: solo elementos nuevos en `acceptance-common-application-inner.conf`
   (`configs[]`, `fileConditions[]`, `lookups[]`). Nunca editar el base ni el motor.
2. **Solo datos sintéticos** en fixtures y ejemplos; nunca registros, documentos o nombres reales.
3. **Sin credenciales**: no leer, copiar ni citar `settings-artifactory.xml` ni tokens. Si aparecen
   en el contexto, detenerse y pedir rotación.
4. **Sin PII** en logs, mensajes o reportes.
5. **`contextualOverride` solo con decisión humana registrada** (D2); nunca con valores por defecto.
6. **Compilación no certificada (F-01)**: decláralo siempre; no afirmes que el Scala compila.
7. **HITL**: no entregues ni integres nada sin aprobación explícita.
8. Todo valor sin evidencia lleva `ASSUMPTION <ID>` (p. ej. `ASSUMPTION S-ACC-02`) y aparece en el
   reporte: la spec lo declara en el bloque `supuestos` (`id`, `supuesto`, `impacto`, `donde`) y el
   generador lo lista (AC-27).

## Flujo de trabajo

### Paso 1 · Recoger la source-spec

- En una ingesta real (también SISBEN), la source-spec es el insumo aprobado de `edf-analisis` (Paso 0.3); sus
  valores salen de la HU y la spec de la ingesta (`.kiro/steering/00-framework-edf.md` §6).
- `assets/examples/sisben/source-spec.sisben.yaml` es solo referencia de **forma**: nunca se copian sus
  valores, tampoco para SISBEN. Si difiere del insumo, manda el insumo. `references/especificacion-sisben.md`
  es un ejemplo histórico (otra versión de SISBEN): no se cita como fuente (HF-19).
- Para revisar un borrador de otra fuente, sigue la forma del ejemplo y pide al ingeniero cada entrada IN-01..IN-08
  (`references/inventario-plantillas.md`). Pregunta solo lo que falte; muestra el estado de cada
  valor (`cerrado`, `propuesta_de_frontera`, `inferido`, `abierto`).
- IN-05 (diagnóstico) **no se inventa**: se toma de la especificación de Validation de la fuente
  (quality scores, códigos con su nivel, marcadores). Si no existe, detente: es el contrato de entrada.
- Un valor `abierto` bloquea solo su hueco: ACC-TPL-03 con `severidades.habilitado = false`,
  ACC-TPL-04 con `contextual_override.decision_humana = false`.

### Paso 2 · Generar los artefactos (orden real del inventario)

```bash
python scripts/render_templates.py --spec <spec>.yaml --out <salida> --audit
```

En macOS/Linux usa `python3` si `python` no existe. Para una ingesta del framework, `<salida>` es `expedientes/<id>/propuestas/acceptance/`.

Orden del `manifest.yaml`: (1) ACC-TPL-02 condiciones con nombre → (2) ACC-TPL-03 severidades, si
aplica → (3) ACC-TPL-01 bloque de ingesta → (4) ACC-TPL-04 `contextualOverride`, solo con decisión →
(5) ACC-TPL-06 fixture y ACC-TPL-05 pruebas. Las condiciones van primero porque `reject` las cita.

Las expresiones siguen el orden en que el motor las evalúa
(`references/dsl-config-reference.md` §2). La tercera columna muestra solo la **forma** de cada valor, tomada del
ejemplo histórico `assets/examples/sisben/`; no son los valores de SISBEN ni de ninguna ingesta: los valores salen
de la HU y la spec de la ingesta, a través del insumo aprobado (Paso 1).

| Etapa | Qué se configura | Ejemplo de forma (no es fuente) |
|---|---|---|
| E3 | `filePattern` (ruta sin extensión y `lineage.fileName`) | `.*<NOMBRE>_\d{6}\.\d{14}[+-]\d{4}\.txt` (prefijos y extensiones, los de la spec de la ingesta) |
| E4 | Rechazo automático por marcadores; `customReasonForAutomaticRejectionEnabled` | `true`; `accept` sin `isEmptyBodyFile`. Con `true` el motor evalúa además `reject` sobre los eventos del marcador: registra el `reasonCode` solo si alguna condición es verdadera sobre ellos (sin el QS, no registra ninguno) |
| E5/E7 | `manualReject` / `manualAccept` | no se configuran |
| E8 | `reject` en sintaxis nueva sobre `fileConditions` | `anyOf(RQS_BAJO_UMBRAL_SISBEN)`; condición `average("qs.RQS") < 0.90`, `SISBFA00001` |
| E9 | `accept` en sintaxis antigua, misma escala | `average("qs.RQS") >= 0.90` |
| E10 | `recordAcceptance` | `errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate")` |
| E12 | `contextualOverride` | sin decisión humana (D2) ⇒ no se emite |

El generador falla (código 1) si la spec no valida contra el schema, si una verificación del inventario falla o si queda
un hueco sin resolver. Reglas que verifica: ACC-R01 (subcadenas), ACC-R02 (nombres de QS),
ACC-R03 (dos claves del `filePattern`), escala única del umbral, umbral en [0, 1], `reject` en
sintaxis nueva, condiciones citadas definidas y con nombre de la fuente, `manual*` solo con
`externalAction`, QS por registro binario (0 o 1) en el fixture (AC-ACC-05) y, si hay
`fixture.umbrales_alternativos`, que cada umbral esté en [0, 1], sea distinto de `calidad.umbral` y se pueda
sustituir en `accept` y en cada condición (HF-23).

Si la spec trae el bloque opcional `supuestos` (AC-27, ADR-G2), el generador imprime una línea
`supuesto  S-ACC-xx  impacto=…  donde=…  texto=…` por supuesto: con `--audit`, después de los huecos;
sin `--audit`, después de las líneas `generado`/`omitido`. Una spec sin el bloque no imprime nada más.

### Paso 3 · Verificar

```bash
python scripts/simular_decision.py --spec <spec>.yaml          # decisión por archivo, comparada con lo esperado
python scripts/hocon_check.py <salida>/hocon/*.conf            # parse del HOCON generado
python -m pytest scripts/tests -q                              # pruebas del skill (generador, simulador, hocon_check)
```

`simular_decision.py` compara cada archivo con `decision_esperada`, `reason_code_esperado` y el `esperado_registro`
de cada evento (`coincide`/`diferencias` en la salida JSON, resumen en stderr) y sale con código 1 si alguno no
coincide (HF-24).

**Umbral alternativo (HF-23).** La configuración desplegable tiene una sola escala de umbral (`reject` y `accept`
complementarios, D7). Para probar otro umbral sin generar otra configuración, por ejemplo el escenario «umbral
parametrizable» de una HU, declara en el fixture:

```yaml
fixture:
  umbrales_alternativos:                 # opcional; sin la clave no cambia nada
    - umbral: "0.95"                     # distinto de calidad.umbral, en [0, 1]
      casos:
        - {id: FQS_095, registros_validos: 19, registros_totales: 20, esperado: aceptado}
        - {id: FQS_094, registros_validos: 47, registros_totales: 50, esperado: rechazado, reason_code_esperado: <reasonCode>}
```

ACC-TPL-05 genera la prueba «umbral alternativo …»: evalúa `accept` y las condiciones con el umbral sustituido sobre
`registros_totales` registros sintéticos con QS binario (`registros_validos` con 1). `reason_code_esperado` es
obligatorio con `esperado: rechazado` (los `reasonCode` registrados, unidos por coma) y vale `""` si se omite.
`simular_decision.py` evalúa los mismos casos.

La cobertura de los scripts Python se mide por script: `python -m coverage run -m pytest scripts/tests -q`
y luego `python -m coverage report --include=scripts/<script>.py --fail-under=80` para
`render_templates.py`, `simular_decision.py` y `hocon_check.py` (AC-31).

Luego recorre `assets/checklist-entrega.md` (A automático, B manual, C seguridad, D HITL). Busca
expresamente en la salida: credenciales, rutas locales, datos que no sean sintéticos.

### Paso 4 · Presentar la propuesta y ESPERAR aprobación

Presenta, en este orden:

1. Bloque `configs[]` y condiciones `fileConditions[]` generados (texto HOCON).
2. Tabla de simulación: archivo → etapa que decide → decisión → `reasonCode` → aceptados/rechazados.
3. Plantillas omitidas y por qué (p. ej. ACC-TPL-03, ACC-TPL-04).
4. Supuestos con su impacto (las líneas `supuesto` que imprime el generador; los mismos de
   `references/decisiones-y-abiertos.md`) y puntos abiertos que afectan a la fuente.
5. Declaración: "Compilación del Spec Scala no certificada (F-01)".
6. Pregunta explícita: *¿Apruebas la propuesta para integrarla en el fork?*

**No continúes** hasta recibir aprobación. Si piden cambios, edita la spec (no los generados) y
vuelve al Paso 2.

### Paso 5 · Entregar al ingeniero (la skill no ejecuta)

La skill termina en el Paso 4. Integrar en el fork, compilar y ejecutar lo hace el **ingeniero**, fuera de
la skill, con `references/runbook-ejecucion.md` §1 a §4 (local, Docker, EMR). Si el ingeniero lo pide, la
skill le explica esos pasos e interpreta los resultados que le reporte (`references/interpretar-salida.md`,
contrastando con la simulación del Paso 3), pero no los ejecuta.

## Contratos de hand-off

### Desde Validation (entrada)

| Contrato | Requisito | Si se rompe |
|---|---|---|
| Ubicación | `<appHome>/validation/validated/<archivo>.<datasetId>/` (sin `rejected/` en Validation) | El archivo no se procesa |
| Evento | `TaggedValidatedEvent` con `lineage.fileName`, `errorCodes[{errorCodeValue, errorLevel, …}]`, `qualityScores[{name, score}]` | Deserialización falla |
| Quality score | `RQS` en **todos** los eventos de datos, binario por registro (0 o 1); el promedio del archivo está en 0..1 | `average` null ⇒ archivo **pendiente**; un score no binario en el fixture ⇒ el generador y el simulador rechazan la spec |
| Niveles | `"1"` (rechaza), `"3"` (informa); marca de duplicado `OxygenDuplicate` con nivel `""` | Registros mal decididos |
| Códigos | Longitud fija, sin subcadenas mutuas (`SISBEN_ERR_0xx`, `SISBEN_INC_0xx`) | ACC-R01 |
| Marcadores | `errorInFileName`, `errorInHeader`, `errorInFooter`, `errorInEmptyFileBody` en el nombre | Sin marcador, se evalúa por calidad (F-11) |
| Menores | Ya filtrados (no llegan) | FQS distorsionado |
| Nombre | `lineage.fileName` coincide con el `filePattern` | Excepción en ejecutor (ACC-R03) |

### Hacia Curation (salida)

| Contrato | Garantía |
|---|---|
| Ubicación | `<appHome>/acceptance/accepted/<archivo>` (lo lee Curation como `ACCEPTED_DIRECTORY`) |
| Contenido | Solo registros con `recordAcceptance = true` de archivos aceptados; mismo `TaggedValidatedEvent` sin transformar |
| Garantías SISBEN | Sin errores de nivel `"1"`, sin `OxygenDuplicate`; pueden traer avisos de nivel `"3"` que Curation limpia |
| No pasa | Archivos rechazados o pendientes; registros rechazados (`rejected/records`) |
| Borrados | `inferred-<archivo>-asDeleted.<id>` solo si D2 se decide |
| Cuerpo vacío | No se acepta para SISBEN; Curation de todos modos omite `errorInEmptyFileBody` |

## Qué NO hacer

- Escribir clases Scala para Acceptance (`IAcceptanceProcessor` no lo invoca nadie).
- Usar `and`/`or` en `reject` con sintaxis nueva, o umbrales en escala 0..100.
- Copiar los patrones de HU-004 o su bloque `acceptance { sisben { … } }`.
- Reemplazar listas HOCON existentes (`configs = [...]` redefinida borra las demás ingestas).
- Usar como plantilla `dsl/acceptance/*`, `ingestion/*`, `ValidatedEventParser` o el modo streaming.

## Fuentes

Citadas por nombre (ninguna se incluye en el skill):

- `acceptance_patrones.yaml` y `acceptance_template.config` — patrones e inventario de la etapa (borrador 2026-09-30).
- `ACCEPTANCE_DISEÑO_PROCESSOR.md` — diseño base de la etapa.
- Repositorio `oxygen-acceptance` (innersource): `SparkAcceptor`, `RecordValidationParser`,
  `QualityScoreParser`, `AggregatesParser`, `ExternalActionParser`, `AcceptanceLexer`,
  `AcceptanceParser`, `AcceptanceAST`, `AcceptanceEvaluator`, `acceptance-application-local.conf`,
  `README.md`, `migrate.md`, `Dockerfile`.
- Fuentes del framework `oxygen-core` (`oxygen-config`): `TypesafeOxygenConfigurationParser`
  (`AcceptanceStage`, `AcceptanceConfigs`), `OxygenConfigurationElements` (`AcceptanceCondition`),
  `TypesafeOxygenPipelineConfiguration`, `oxygen-config-default.conf`.
- Repositorio `oxygen-curation` (`SparkCurator`) para el contrato de salida.
- Requisitos SISBEN V4 (`requirements.md` §5, §9: RF-ACC-01..08, D1-D14), HU-004, entendimiento
  funcional de Validation-Acceptance.
- Hechos compartidos EDF/Oxygen (verificados contra código) y faltantes F-01..F-17.
