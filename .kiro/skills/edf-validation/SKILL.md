---
name: edf-validation
description: |
  Genera, verifica y acompaña la ejecución de la etapa Validation de una ingesta EDF/Oxygen
  (piloto SISBEN V4). A partir de un source-spec YAML produce el processor Scala, la especificación,
  el evento, los metadatos, los tests ScalaTest, el fixture sintético y los bloques HOCON aditivos;
  verifica códigos, huecos, PII y credenciales; presenta la propuesta y espera la aprobación del
  técnico antes de copiar o ejecutar nada. Incluye runbook (spark-submit, Docker, EMR), lectura de la
  salida y el contrato de entrega a Acceptance. Úsalo cuando un ingeniero de datos deba configurar o
  revisar la validación de una fuente en Oxygen.
metadata:
  stage: validation
  framework: EDF/Oxygen
  pilot: SISBEN V4
  version: "0.1.0"
---

# EDF/Oxygen · Etapa Validation

## Paso 0 · Habilitación por el expediente (obligatorio, framework EDF)

Esta skill es parte del framework `edf-framework-IA` y opera **debajo del Orquestador** (`.kiro/steering/00-framework-edf.md` §3). Antes de generar cualquier artefacto:

1. **Identifica la ingesta** (`<ingesta-id>`) y abre `expedientes/<ingesta-id>/estado-ingesta.json`. Si no existe → no generes; pide al Desarrollador que inicie el expediente con `edf-orquestador`.
2. **Verifica la habilitación:** en `etapas[]`, la etapa `analisis` debe estar en `aprobada`, `completada_manualmente` u `omitida` (esta última solo con aprobación de omisión del PO, `edf-orquestador/references/03` §4b), con `transicion_habilitada: true`. Tu etapa en el expediente es `validation`.
3. **Verifica el insumo:** `expedientes/<ingesta-id>/insumos/source-spec-validation.yaml` (valida contra `assets/schema/source-spec.schema.yaml` de esta skill) debe existir y figurar como `aprobado` en los artefactos de tu etapa. Si falta → no generes; deriva a `edf-analisis` ("prepara el insumo de Validation para la ingesta <id>").
4. **Si algo falla:** responde con lo que falta, sin generar nada y sin ofrecer cómo saltarse el control. Deriva a `edf-orquestador`.

Excepción única: trabajo sobre fixtures sintéticos de evaluación (`evals/` o `assets/examples/`) fuera de una ingesta real. Decláralo al inicio de tu respuesta.

Al terminar: escribe la propuesta en `expedientes/<ingesta-id>/propuestas/validation/` y entrega al Desarrollador la lista de artefactos (ruta, tipo, supuestos abiertos, resultado de las verificaciones) **para que el Orquestador la registre**. Esta skill **no escribe** `estado-ingesta.json` ni `bitacora.md`.


Skill autocontenido. Todas las rutas son relativas a esta carpeta. Idioma de trabajo: español.

## Qué hace y hasta dónde llega

Validation es **diagnóstica**: escribe todos los registros en `validated/` con sus códigos de error,
severidades y quality scores. No acepta ni rechaza (eso es Acceptance). El motor no se modifica: la
fuente se enchufa con un processor Scala (reflexión) y un bloque HOCON aditivo.

El skill entrega una **propuesta revisable** para el fork de Colombia de oxygen-validation. La
compilación contra el framework es **no certificada** (F-01: sin acceso al JAR); el resto se verifica
aquí. Alcance y glosario: `references/alcance-y-glosario.md`.

## Reglas duras (guardrails)

1. **Human-in-the-Loop.** Nada se copia al fork, se ejecuta en un clúster ni se commitea sin
   aprobación explícita del técnico. Mostrar siempre el diff y el manifiesto primero.
2. **Solo aditivo.** Nunca modificar `*-common-application.conf` base ni los motores
   (`SparkValidator*`, `Validator`). Solo agregar elementos nuevos en los archivos *inner*.
3. **Nunca lanzar por registro.** Solo `MalformedEventException` la captura el motor; cualquier otra
   excepción tumba el job. Todo error de dato es `ObjectError`.
4. **Solo datos sintéticos.** Fixtures y catálogos de ejemplo son inventados y lo dicen. No copiar
   archivos de entrada/ejemplo reales ni registros de la fuente a este skill, al fork ni a tickets.
5. **Credenciales.** Nunca leer, copiar ni citar el contenido de `settings-artifactory.xml` u otro
   archivo de credenciales. Si aparece versionado con secretos, reportarlo para rotación.
6. **Sin PII en logs ni reportes.** Solo códigos, conteos y números de línea.
7. **Discrepancias:** no se resuelven en silencio. Las de una ingesta van en la entrega al Orquestador (con la
   lista de artefactos de `expedientes/<id>/propuestas/validation/`), que las registra en el expediente; **nunca**
   se escriben en `.kiro/` (`references/decisiones-y-abiertos.md` es solo del mantenimiento de la skill). Para
   comportamiento del motor, el código Oxygen gana sobre los documentos.

## Regla de ejecución: generar, nunca compilar

Esta skill **solo genera archivos**. Todo se hace con Python 3 (PyYAML y jsonschema):

- **Nunca** ejecutes `mvn`, `javac`, `java -jar`, `sbt`, `gradle`, `spark-submit` ni Docker, ni compiles el código generado. La compilación y la ejecución del processor las hace el ingeniero en el fork, fuera de esta skill (`references/runbook-ejecucion.md` §4 a §7).
- La carpeta `scripts/java/` es el generador Java anterior, conservado solo como referencia histórica (`scripts/DEPRECADOS.md`). No se usa.
- Si falta PyYAML o jsonschema, detente y pide al Desarrollador que los instale (`pip install pyyaml jsonschema`). No busques otra forma de generar.

## Flujo de trabajo

### Paso 1 · Obtener el source-spec

- **Ingesta real (también SISBEN):** el source-spec es el insumo aprobado de `edf-analisis`
  (`expedientes/<ingesta-id>/insumos/source-spec-validation.yaml`, Paso 0.3). Sus valores salen de la HU y la
  spec de la ingesta (`.kiro/steering/00-framework-edf.md` §6), que **manda** sobre cualquier referencia de esta
  skill. Revisar con el técnico cada `ASSUMPTION` (el significado de cada ID está en
  `references/decisiones-y-abiertos.md`). Si la spec ya fija el valor, el insumo lo declara en
  `supuestos_resueltos`; si hay que cambiar un valor, se cambia el insumo (vía `edf-analisis`), y las discrepancias
  se listan en la entrega al Orquestador (regla dura 7).
- **Ejemplo SISBEN = solo referencia de FORMA.** `assets/examples/sisben/source-spec.sisben.yaml` muestra la
  estructura y el nivel de detalle; **nunca** se copian sus valores, tampoco para SISBEN: difiere de la spec
  vigente (prefijos de RT/EP, códigos, longitudes, catálogos). Si el insumo y el ejemplo difieren, manda el
  insumo. El ejemplo solo es entrada en el trabajo sobre fixtures de la excepción del Paso 0.
- **Fuente nueva sin insumo:** no se escribe aquí; se deriva a `edf-analisis` (Paso 0.3). Para revisar un
  borrador, seguir `assets/schema/source-spec.schema.yaml` y preguntar al técnico, en un solo bloque, lo
  que falte: identidad (IN-01), contrato del archivo (IN-02), tabla de campos (IN-03), reglas entre campos (IN-04), guard-rails (IN-05), duplicados
  (IN-06), filtro (IN-07), catálogos (IN-08), quality scores (IN-09), diagnóstico de archivo (IN-10).
  Cada campo necesita un `ejemplo_invalido` sintético y el fixture, casos sintéticos con su resultado
  esperado.

Referencias: `references/dsl-config-reference.md` (claves HOCON y DSL). `references/especificacion-sisben.md` es
un ejemplo histórico (versión 0.1.0): no es fuente de ninguna ingesta y no se usa para llenar el insumo.

**Claves opcionales del source-spec (1.9.2).** Sin ellas el comportamiento no cambia:

| Clave | Sintaxis | Efecto |
|---|---|---|
| Reglas de fecha del nombre (AUD-V-02) | en `archivo.nombre`: `periodo_no_futuro: true`, `recepcion_no_futura: true`, `recepcion_desde_inicio_periodo: true` | Con los grupos `periodo`, `marca_tiempo` y `offset` del regex: periodo AAAAMM ≤ mes actual (zona de la fuente); marca de tiempo con su offset ≤ ahora; fecha de la marca ≥ primer día del periodo. Si no se cumple: `codigos_especiales.nombre_archivo`. Reloj: `Instant.now()` en el processor, `--hoy` (fin del día) en `prevalidar_archivo.py`, `fixture.fecha_referencia` en las pruebas |
| Solo ceros (AUD-V-03) | en `reglas_entre_campos`: `{id: RV-X, tipo: solo_ceros, campo: <CAMPO>, codigo: <COD>, severidad: "1", causa: <CAUSA>}` | Emite el código si el valor sin espacios no está vacío y es solo `0` (`0`, 16 ceros, `00 00`). Reemplaza la lista literal de ceros |
| Supuestos resueltos (AUD-V-08) | en la raíz: `supuestos_resueltos: [{id: F-11, fuente: "spec-x.md §8"}, ...]` | La marca `ASSUMPTION <ID>` sale como `RESUELTO <ID> (<fuente>)` y el ID no queda en `manifest["supuestos"]` |
| Grupos de duplicados (AUD-V-09) | en `fixture.casos[].esperado`: `grupo_duplicado: <GRUPO>` | Una prueba por grupo (p. ej. el mismo documento con y sin ceros a la izquierda) |
| Nombres inválidos (AUD-V-09) | en `fixture`: `nombres_invalidos: [<nombre>, ...]` | Prueba generada: cada nombre produce el código de nombre de archivo |

`catalogos[].archivo` es la ruta del CSV relativa a `oxygen.application.paths.lookupHome` (AUD-V-06).

### Paso 2 · Verificar el spec

```bash
python scripts/render_templates.py --spec <spec.yaml> --solo-verificar
```

Debe terminar en `source-spec OK`. Corrige el spec (no las plantillas) ante cualquier error. Lo que se
verifica: esquema; posiciones 1..N; obligatorio ⇔ severidad 1; códigos de **longitud fija**, **sin
colisiones de subcadena** (el DSL compara con `contains`), ninguno de 11 caracteres ni que choque con
`OxygenDuplicate`; nombres de quality score fuera de operadores y códigos; catálogos y columnas
declarados y en `keys`; guard-rails y clave de duplicados existentes; `filePattern` con `.*`; fixture
coherente con el regex del nombre.

### Paso 3 · Generar los artefactos

```bash
python scripts/render_templates.py --spec <spec.yaml> --out <salida>/
```

En macOS/Linux usa `python3` si `python` no existe. Para una ingesta del framework, `<salida>` es `expedientes/<id>/propuestas/validation/`.

Genera (VAL-TPL-01..11, detalle en `references/inventario-plantillas.md`):

| Capa | Archivo generado |
|---|---|
| A (una vez) | `FieldSpec.scala`, `FieldValidators.scala`, `FieldValidatorsSpec.scala` |
| B | `config/<Fuente>Spec.scala` |
| — | `<Fuente>ValidatedEvent.scala`, `validator/<Fuente>FileMetadata.scala` (con los miembros de `archivo.metadata` si la spec los declara: `minFrequency`, `subSubscriber`, `additionalInformation` y otros como `fileLoadType`, tomados de la implementación de referencia; ADR-05) |
| C | `processor/<Fuente>ValidationProcessor.scala` |
| Tests | `<Fuente>ValidationProcessorSpec.scala` (sin clases del framework) |
| Datos | fixture sintético, CSV de catálogos sintéticos (`src/test/resources/<fuente>/`) |
| Documentación | `docs/codigos-error-<fuente>.csv` (catálogo de códigos con `OxygenDuplicate`; **no** se pega en el fork, AUD-V-19) |
| HOCON | `propuesta/validation-configs-<fuente>.conf`, `propuesta/lookups-<fuente>.conf` |
| Control | `manifest.json` (archivos, sha256, supuestos, requisitos de ambiente) |

El generador es todo o nada: sale con código 2, sin escribir ningún archivo, si queda algún `{{hueco}}`
sin resolver, una marca `ASSUMPTION` sin ID o un patrón de credenciales. No edites los archivos generados:
cambia el spec y regenera.

Verificación opcional del HOCON generado, con `pyhocon`: `python scripts/verificar_hocon.py
--generado <salida> --fuente <fuente> --archivo-ejemplo <fixture>`. Si `pyhocon` no está, se reporta
"no verificado" (no es un error).

### Paso 4 · Revisar con el checklist

Recorre `assets/checklist-entrega.md` y deja constancia de cada punto. Revisa en especial:

- El processor extiende `ValidationProcessor` con constructor `(ProcessorContext)`, es Serializable,
  logger `@transient lazy val`, no toca `fileMetadata` en el constructor, no usa `println`,
  `decodeErrorObject`, contextual ni atributos históricos, e implementa solo
  `createValidatedEvents(rawRecord, ctx)`.
- El bloque HOCON usa `headerRows`/`footerRows` (no `numberOf*`), `footerRows = 0`, sin `encoding`,
  `computeMetrics = true` con duplicados, `deDupVersion = "EXTN"`, expresiones `validate[].fileName` y
  `header` presentes (sin ellas no hay marcador, F-11) y `contexual.enabled = false`.
- Los `ASSUMPTION:` del código coinciden con el manifiesto.

### Paso 5 · Pre-validar un archivo (opcional, sin Spark)

```bash
python scripts/prevalidar_archivo.py --spec <spec.yaml> --archivo <archivo> [--lookups <dir>] --hoy AAAA-MM-DD --detalle
```

Simula el flujo del motor (vacío → nombre → header → filtro → registros → duplicados → métricas) y
muestra el conteo por código, el promedio de RQS y una vista previa de Acceptance. El resultado nunca
incluye valores de campos. Verifica el spec con el mismo generador Python, sin Java. Con el fixture SISBEN debe coincidir con
`assets/examples/sisben/esperado/resumen-prevalidacion.json`. No modela E8 (footer): con `filas_footer > 0`
sale con código 2 (`references/interpretar-salida.md`). En Windows, si rediriges la salida JSON, fija
`PYTHONIOENCODING=utf-8` o usa `--salida <archivo>`; si no, sale en cp1252.

### Paso 6 · Presentar la propuesta y ESPERAR aprobación

Presenta al técnico, en este orden: resumen del manifiesto; diff contra el fork
(`git diff --no-index <fork>/src <salida>/src`); fragmentos HOCON y dónde se pegan; lista de
`ASSUMPTION` abiertos; resultado de las verificaciones. Termina con una pregunta explícita:

> ¿Apruebas copiar estos archivos al fork en la rama `<rama>`? (sí / cambios)

No continúes sin un "sí" explícito. Ante "cambios", vuelve al Paso 1.

### Paso 7 · Entregar al ingeniero (la skill no ejecuta)

La skill termina en el Paso 6. Copiar al fork, compilar, probar y ejecutar lo hace el **ingeniero**, fuera
de la skill, siguiendo `references/runbook-ejecucion.md` §4 a §7 (copia aditiva al fork, compilación,
tests, `spark-submit`, Docker o EMR Serverless, y checklist de ambiente: `lambda = batch`,
`historicAttributes = false`, `emitStockMetrics` presente, motor confirmado). Si el ingeniero lo pide, la
skill le explica esos pasos, pero no los ejecuta.

### Paso 8 · Interpretar la salida y entregar a Acceptance

Usa `references/interpretar-salida.md`: árbol de diagnóstico (falla del job, marcadores -1002…-1005,
conteo por código), comparación con la pre-validación y **contrato de entrega a Acceptance** (códigos,
niveles, `RQS`, regla de registro `errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate")`, umbral
`average("qs.RQS") >= 0.90`, rechazo automático por marcadores). La configuración de Acceptance es de su
propio skill.

## Hechos del motor que el skill no debe olvidar

- `filePattern` se compara contra la ruta completa: empieza con `.*`. Cada elemento de `validate[]` y de
  `metrics[]` lleva el suyo: el motor lo lee con `data.filePattern.get` y sin él falla en cada archivo (AUD-V-01).
- Un archivo que no cumple ningún `filePattern` no se procesa ni recibe marcador: el código de nombre de archivo
  solo lo emite un nombre que sí cumple `filePattern` (AUD-V-04, decisión de la spec).
- Los `file` de `oxygen.lookups[]` se arman con `${oxygen.application.paths.lookupHome}"/..."`, como en los confs
  del motor (AUD-V-06).
- Claves mal escritas se ignoran en silencio. `footerRows` vale 1 por defecto.
- Sin `deDupVersion` se marcan todos los duplicados (AS-IS). En `EXTN` la marca la pone el processor.
- Con `searchAllHeaderFooter = true` el processor se instancia con headers vacíos; aquí se usa `false`.
- Un archivo totalmente vacío tumba el job; un cuerpo vacío tras filtros (p. ej. solo menores) produce
  `errorInEmptyFileBody`.
- `defaultOutputStructure`: `true` → `SparkValidator`; `false` o ausente → `SparkValidatorCustomMode`
  (borra `appHome` al terminar).

## Estructura del skill

```
SKILL.md
references/  alcance-y-glosario · patrones · inventario-plantillas · especificacion-sisben ·
             dsl-config-reference · runbook-ejecucion · interpretar-salida · decisiones-y-abiertos
assets/templates/   plantillas .scala.tpl, .conf.tpl, .csv.tpl, .txt.tpl (capa-a/ = Capa A)
assets/schema/      source-spec.schema.yaml
assets/examples/sisben/  source-spec, input/ (fixture sintético), esperado/, generated/ (salida de referencia)
assets/checklist-entrega.md  (incluye la nota de verificación)
scripts/  render_templates.py (generador) · prevalidar_archivo.py · spec_utils.py · verificar_hocon.py ·
          tests/ (pytest)
scripts/java/  generador Java anterior: solo referencia histórica, no se usa (scripts/DEPRECADOS.md)
```

Requisitos: Python ≥ 3.10 con PyYAML y jsonschema para generar y prevalidar. Sin Java ni Maven. Para las
pruebas del skill, `pytest` (`python -m pytest scripts/tests`); `pyhocon` opcional (sin él se omiten 4
pruebas y `verificar_hocon.py` sale con código 3, "no verificado"). Cobertura: desde la raíz del skill, `coverage run -m pytest scripts/tests` (lee `.coveragerc`)
y un `coverage report --include=scripts/<script>.py --fail-under=80` por script (`assets/checklist-entrega.md`, k3).

## Fuentes

Citadas por nombre; ninguna se incluye en este skill.

- `validation_patrones.yaml` y `validation_template.config` (patrones e inventario de plantillas).
- `VALIDATION_DISEÑO_PROCESSOR.md` (diseño del processor).
- `requirements.md` §4, §8, §9 (decisiones D1–D14, reglas RV-GEN/RV-SIS, homologación).
- `04_Etapas_Validation-Acceptance.md` (layout y reglas por campo, estructura del fork).
- `02_Especificación` del archivo SISBEN (nombres fuente).
- Código del repositorio oxygen-validation (versión de framework 2404): `ValidationProcessor`,
  `SparkValidator`, `Validator`, `RecordValidationParser`, `AggregatesParser`, `ValidationResult`,
  `ValidatedEventValueObject`, `Util`, README, Dockerfiles.
- Código del framework oxygen-core (`ValidationConfigs`, `ValidationStage`, `LookupElements`,
  `oxygen-config-default.conf`) y de la variante oxygen-innersource-qa (`ObjectError`, `QualityScore`,
  `TaggedValidatedEvent`).
