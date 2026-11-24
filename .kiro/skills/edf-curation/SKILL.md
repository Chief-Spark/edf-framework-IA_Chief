---
name: edf-curation
description: |
  Genera y verifica los artefactos de la etapa Curation de EDF/Oxygen para una fuente nueva
  (piloto SISBEN V4): processor Scala, evento curado, especificación de mapeo, bloque HOCON,
  lookups, domain dictionary, fixture sintético y tests. Curation PERSISTE las transformaciones
  (normalización, homologación de TIP_DOCUMENTO, fechas a epoch, padding DANE). Automatiza lo
  posible y guía al experto en lo demás; propone y espera aprobación antes de entregar.
metadata:
  stage: curation
  framework: EDF/Oxygen
  pilot: SISBEN V4
  version: "0.1.0"
  human-clarification: "true"
  artefact-verification: "true"
---

# EDF/Oxygen · Curation stage

## Paso 0 · Habilitación por el expediente (obligatorio, framework EDF)

Esta skill es parte del framework `edf-framework-IA` y opera **debajo del Orquestador** (steering `00-framework-edf.md` §3). Antes de generar cualquier artefacto:

1. **Identifica la ingesta** (`<ingesta-id>`) y abre `expedientes/<ingesta-id>/estado-ingesta.json`. Si no existe → no generes; pide al Desarrollador que inicie el expediente con `edf-orquestador`.
2. **Verifica la habilitación:** en `etapas[]`, la etapa `acceptance` debe estar en `aprobada`, `completada_manualmente` u `omitida` (esta última solo con aprobación de omisión del PO, `edf-orquestador/references/03` §4b), con `transicion_habilitada: true`. Tu etapa en el expediente es `curation_flow` y `curation_stock` (un mismo processor sirve a ambas).
3. **Verifica el insumo:** `expedientes/<ingesta-id>/insumos/source-spec-curation.yaml` (valida contra `assets/schema/source-spec.schema.yaml` de esta skill) debe existir y figurar como `aprobado` en los artefactos de tu etapa. Si falta → no generes; deriva a `edf-analisis` ("prepara el insumo de Curation para la ingesta <id>").
4. **Si algo falla:** responde con lo que falta, sin generar nada y sin ofrecer cómo saltarse el control. Deriva a `edf-orquestador`.

Excepción única: trabajo sobre fixtures sintéticos de evaluación (`evals/` o `assets/examples/`) fuera de una ingesta real. Decláralo al inicio de tu respuesta.

Al terminar: escribe la propuesta en `expedientes/<ingesta-id>/propuestas/curation/` y entrega al Desarrollador la lista de artefactos (ruta, tipo, supuestos abiertos, resultado de las verificaciones) **para que el Orquestador la registre**. Esta skill **no escribe** `estado-ingesta.json` ni `bitacora.md`.

## Requisito del framework · Claves de identidad para Pinning (steering `11`)

Pinning compara texto exacto y lee solo las claves de `identifying-attributes` de Colombia (`standardizedId*`). Por decisión del 2026-11-04 (opción A, `docs/arquitectura.md` DA-11 y DA-13), todo `source-spec-curation.yaml` de una ingesta de Colombia **agrega** estas entradas a `campos_curados` (aditivo: las claves actuales se mantienen):

```yaml
  - {destino: standardizedIdType,   origen: "", transformacion: {tipo: copia_de, campo: tipoDocumento},   tipo_salida: entero, estado: cerrado}
  - {destino: standardizedIdNumber, origen: "", transformacion: {tipo: copia_de, campo: numeroDocumento}, tipo_salida: texto,  estado: cerrado}
  - {destino: standardizedIdName,   origen: "", transformacion: {tipo: concatenacion, campos: [primerNombre, segundoNombre, primerApellido, segundoApellido], separador: " "}, tipo_salida: texto, estado: cerrado}
```

`concatenacion` (desde la 1.7.0 del framework) une los valores **ya curados** de campos anteriores del mapeo, en el orden de `campos`, con `separador` (por defecto un espacio). Un componente nulo, vacío u omitido no aparece; si todos faltan, el campo sale nulo sin advertencia propia (los nombres que lo componen ya advirtieron). Los nombres de `campos` deben ser los `destino` de la spec de la ingesta.

- `standardizedIdNumber` hereda la regla de `reglas_documento`. Decisión DA-13: **se eliminan los ceros a la izquierda**. Si se aplica a todos los tipos (incluidos PPT y pasaporte, que en el ejemplo SISBEN los conservan) está pendiente de confirmar: hasta entonces se deja como en el ejemplo y se reporta como supuesto.

Declara en `campos_abiertos` solo lo que la HU o la spec de la ingesta no definen (los campos abiertos no se generan).

Pendientes que **no** se resuelven aquí y se reportan en la entrega:
- Formato de `subscriberRelation` (por defecto `NUM_DOCUMENTO` sin normalizar; PIN-SIS-04 pide `standardizedIdType_standardizedIdNumber`): steering `11` §6 #8. Solo se cambia si el `source-spec-curation.yaml` aprobado declara el bloque opcional `extension_subscriber_relation: {campos: [...], separador: "_"}` (D14): el processor arma `subscriberRelation` con esos campos curados y, si falta alguno, conserva el del evento aceptado y advierte `subscriberRelation:COMPONENTE_FALTANTE` (AUD-C3). La firma del gancho está verificada en la copia del motor (AUD-C8); la compilación contra el JAR sigue sin certificar (F-01).
- Claves de identidad (`standardizedId*` o `parametros.omitir_si_nulas`): si son nulas se omiten del evento, nunca `null` (AUD-C4, steering `11` §3).
- Fechas ISO: `parametros.formatos_fecha` admite `uuuu-MM-dd` además de `dd/MM/uuuu` y `uuuu-MM-dd HH:mm:ss`. `homologacion_tipo_documento.pendientes` puede ir vacío (`[]`).
- `standardizedIdNumber` y el resto de los números de documento salen en mayúsculas (`reglas_documento` con `conservar_original: true` los devuelve tal como vinieron).
- Bandera de fuente para el matcher de Colombia (`isOfficialSource` o una nueva): `OQ-TR-6`. Si se decide, Curation la publica como `constante`.

## Campos con error de severidad 3 (RF-CUR-10)

La política la fija la spec de la ingesta en `parametros` (ver `assets/schema/source-spec.schema.yaml`):

| La spec dice | Configuración | Efecto en el evento curado |
|---|---|---|
| "no se almacena" | el nivel `"3"` en `niveles_no_almacenar` | la clave se omite |
| "se anula" | el nivel en `niveles_anular` | la clave sale nula |
| "se publica estandarizado" (SISBEN, spec 1.0.1 §10.4) | `niveles_no_almacenar: []` y el nivel fuera de `niveles_anular` | el valor se transforma como cualquier otro; si no se puede, sale nulo con advertencia `campo:MOTIVO` |

Si la spec de la ingesta no lo dice, es una pregunta abierta: no se elige por defecto.

El ejemplo `assets/examples/sisben/source-spec.sisben.yaml` **no** incluye las entradas `standardizedId*` y usa la política "no se almacena", a propósito: sus salidas esperadas y pruebas se generaron así. Si se cambia, hay que regenerarlo y actualizar la copia versionada:

```bash
python scripts/render_templates.py --write-expected assets/examples/sisben/expected
python -m pytest scripts/tests -q
```


Skill portable y autocontenido. Todas las rutas son relativas a la carpeta del skill.

## Regla de ejecución: generar, nunca compilar

Esta skill **solo genera archivos**, con Python 3:

- **Nunca** ejecutes `mvn`, `javac`, `java -jar`, `sbt`, `gradle`, `spark-submit` ni Docker, ni compiles el código generado. Integrar en el fork, compilar y ejecutar lo hace el ingeniero, fuera de esta skill (`references/runbook-ejecucion.md` §1 a §5).
- La carpeta `scripts/java/` es el generador Java anterior, conservado solo como referencia histórica (`scripts/DEPRECADOS.md`). No se usa.
- Si falta PyYAML o jsonschema, detente y pide al Desarrollador que los instale (`pip install pyyaml jsonschema`). No busques otra forma de generar.

## Cuándo usarlo

- Un ingeniero de datos debe enchufar una fuente nueva en Curation (fork de oxygen-curation).
- Hay que revisar o regenerar los artefactos de Curation de SISBEN V4.
- Hay que explicar qué hace Curation frente a Validation y Acceptance.

No usarlo para: diagnosticar registros (Validation), aceptar/rechazar (Acceptance), consolidar
(Synthesis), ni modificar el motor `SparkCurator`.

## Qué hace Curation (y qué no)

Curation transforma **uno a uno** los eventos aceptados al modelo canónico y **persiste** el
resultado. Validation solo normaliza **en memoria** para validar. Curation no filtra, no deduplica,
no infiere borrados y no tiene canal de errores propio: lo que no se puede transformar queda `null`
y se lista en el campo de advertencias del propio evento (D13).

Transformaciones SISBEN (RF-CUR-04..10): nombres en mayúsculas sin tildes ni especiales; tipos de
dato; fechas a epoch ms; **homologación de `TIP_DOCUMENTO`** SISBEN→Experian
`1→10, 2→7, 3→1, 4→4, 5→8, 6→5, 7→pendiente (nunca un código arbitrario), 8→9, PPT→11`, con el
original en `tipoDocumentoSisben`; número de documento (espacios, ceros, PEP sin letras, PPT
original); padding DANE; concatenación de campos curados (nombre completo); campos con severidad 3 según
la política de la spec (sección anterior). Enriquecimiento:
tipo de archivo y fecha de corte desde el nombre. De los 7 lookups SISBEN, Curation persiste la
homologación (`202_SISBEN_PROCESS_ID_TYPE_HO`); los otros seis los consulta Validation.

## Guardarraíles (no negociables)

1. **Configuración aditiva**: solo se agrega un elemento a `oxygen.stage.curation.configs[]`,
   `oxygen.lookups[]` y entradas al domain dictionary del *inner*. Nunca se edita el conf base, ni
   claves de etapa (`runMode`, `partitionSize`, `enableSSRWrite`, `includeQualityCodes`,
   `includeErrorCodes`, `excludes`), ni bloques de otras ingestas.
2. **Solo datos sintéticos** en fixtures, lookups de prueba, ejemplos y tests. Nunca datos reales.
3. **Nunca** leer, copiar, citar valores ni adjuntar `settings-artifactory.xml` ni otro archivo con
   credenciales. Si aparece uno expuesto, pedir rotación.
4. Sin PII en logs ni en advertencias (solo `campo:MOTIVO`).
5. Compilación **no certificada** (F-01): no hay acceso a los JAR del framework. Decirlo siempre.
6. Todo valor sin evidencia sale con `ASSUMPTION:` y queda en `references/decisiones-y-abiertos.md`.
   Los campos abiertos no se inventan: no se generan.
7. No se integra nada sin **aprobación explícita** del humano (HITL).

## Flujo

### 1. Contexto

Leer `references/alcance-y-glosario.md` y `references/patrones.md`. Confirmar con el usuario:
fuente, repo destino (fork de oxygen-curation) y si es la primera ingesta (se generan Capa A y sus
tests) o una más (se reutilizan).

### 2. Source-spec

- Ingesta real (también SISBEN): el source-spec es el insumo aprobado de `edf-analisis` (Paso 0.3); sus valores
  salen de la HU y la spec de la ingesta (steering `00-framework-edf.md` §6).
- `assets/examples/sisben/source-spec.sisben.yaml` es solo referencia de **forma**: nunca se copian sus
  valores, tampoco para SISBEN. Si difiere del insumo, manda el insumo.
- Borrador de una fuente nueva: seguir la forma del ejemplo y completarlo según `assets/schema/source-spec.schema.yaml`. Preguntar
  solo lo que falte; cada respuesta se registra con su estado (cerrado, propuesta, inferido,
  supuesto, abierto). La carpeta de lookups del fixture (`fixture.lookups_dir`) debe traer un
  `LEEME.md` que marque los CSV como sintéticos: sin él el generador sale con código 1 ("falta la
  marca de sintético de los lookups"; desde el 2026-10-21, H-12 de `G_2026-10-21`).
- Zona de fechas (AC-CUR-10, ADR-G4): `parametros.zona_horaria_fechas` con su origen
  `parametros.zona_horaria_fechas_origen` (`requisito` | `supuesto`; por defecto `supuesto`) y la zona en
  que la etapa anterior calcula `asOfDate`, `parametros.zona_asofdate_etapa_anterior` (por defecto
  `America/Bogota`, D3). Si las dos zonas difieren, el generador lo advierte (paso 3).

Campos mínimos: identidad (`Fuente`, `eventType`), convención de nombre y `filePattern`, campos de
entrada, códigos de error → campo, homologación, reglas de documento, catálogos, mapeo
`campos_curados`, campos abiertos, rutas destino, fixture sintético.

### 3. Generar

```bash
python scripts/render_templates.py --spec <source-spec.yaml> --solo-verificar   # opcional: valida sin generar
python scripts/render_templates.py --spec <source-spec.yaml> --out <carpeta-salida>
```

`--out` se borra y se vuelve a crear: use una carpeta dedicada. Si `zona_horaria_fechas` difiere de
`zona_asofdate_etapa_anterior`, el generador escribe en **stderr** `ADVERTENCIA: zona_horaria_fechas (<z1>)
difiere de la zona de asOfDate de la etapa anterior (<z2>) (C-CUR-04)` y la guarda en
`MANIFEST.json["advertencias"]`; stdout sigue terminando en `OK: …` (con la spec SISBEN, `UTC`, sale siempre
mientras C-CUR-04 siga abierta). Sin `--out` se escribe en
`assets/examples/sisben/generated`. La generación es todo o nada: si algo falla, sale con código 1, dice
qué falta y no toca `--out`.

Valida el spec (esquema + coherencia: nombres únicos, orígenes existentes, `copia_de` y `concatenacion`
solo sobre campos anteriores, `filePattern` contra ejemplos y contraejemplos, campos abiertos excluidos), rellena las 12 plantillas
(`references/inventario-plantillas.md`), rechaza huecos sin resolver y escribe:

| Salida | Contenido |
|---|---|
| `src/main/scala/.../source/common/` | Capa A: `FieldMapping.scala`, `Transformaciones.scala` |
| `src/main/scala/.../source/<fuente>/` | Capa B `config/<F>CurationSpec.scala`, `<F>CuratedEvent.scala`, `<F>AcceptedRecord.scala`, Capa C `processor/<F>CurationProcessor.scala` |
| `src/test/scala/...` | `TransformacionesSpec.scala`, `<F>CurationProcessorSpec.scala` (casos del fixture) |
| `src/test/resources/<fuente>/` | `accepted-events.json` (JSON lines) + `lookups/*.csv` sintéticos + `lookups/LEEME.md` (marca de sintético, AC-30) |
| `fragmentos/` | HOCON del bloque, HOCON de lookups, entradas del domain dictionary (**a fusionar**) |
| `expected/` | salida esperada del fixture (implementación de referencia `scripts/curation_reference.py`). Con `--write-expected <dir>` se copia además a `<dir>`: para SISBEN, la copia versionada `assets/examples/sisben/expected/`, que es la que compara `test_salida_igual_a_la_versionada` |
| `MANIFEST.json` | plantilla → archivo → ¿fusionar? (12 entradas en `archivos`); `marcas_sintetico` lista la marca de los lookups; `otros_archivos` lista, con `ruta` y `sha256`, los CSV de lookups y `expected/` (todo lo generado salvo el propio manifest, ADR-G3); `zona_horaria_fechas: {valor, origen}` y `advertencias` (ADR-G4) |

Parte manual (`TODO(manual)` en el processor): enriquecimiento sin valores (Business Line Code,
Type of Data). El nombre completo se genera con `concatenacion` cuando la spec de la ingesta define su orden.

### 4. Verificar

```bash
python -m pytest scripts/tests -q                           # generador, referencia, hocon_check y portabilidad
python scripts/hocon_check.py <carpeta-salida>/fragmentos/*.conf
```

El parse con Typesafe Config (el parser del motor), la compilación y las pruebas Scala ocurren en el fork,
fuera de la skill: se reportan como **no verificados** hasta que el ingeniero informe el resultado.

Recorrer `assets/checklist-entrega.md`. Revisar `expected/resumen.json`
(`references/interpretar-salida.md`). Confirmar que no hay PII, credenciales ni rutas personales.

### 5. Proponer y esperar (HITL)

Presentar al usuario, en este orden:

1. Qué se generó (tabla del manifest) y qué hay que fusionar a mano.
2. Resultado de la verificación (tests, parse-check, checklist) y lo que **no** se pudo verificar
   (compilación, ejecución en Spark).
3. Supuestos aplicados (`ASSUMPTION:`, cada uno con su ID de `references/decisiones-y-abiertos.md`), la zona
   de fechas con su origen y las `advertencias` del manifest, y campos abiertos no generados.
4. Cambios requeridos en el ambiente (claves que deben existir, features a revisar).

Terminar con una pregunta explícita: *¿Apruebas integrar estos artefactos en el fork o quieres
cambios?* **No continuar** hasta recibir respuesta. Si pide cambios: editar el source-spec,
regenerar y volver al paso 4.

### 6. Integrar y ejecutar (guiado)

Lo hace el ingeniero en el fork, fuera de esta skill, siguiendo `references/runbook-ejecucion.md`:
copiar a rutas del fork, fusionar fragmentos, compilar y probar, ejecución local, Docker o EMR, y diagnóstico. Interpretar con
`references/interpretar-salida.md`.

## Contrato de entrada (hand-off desde Acceptance)

Curation consume lo que Acceptance deja en `acceptedDir`. Antes de generar, confirmar:

| Elemento | Qué espera Curation | Si no se cumple |
|---|---|---|
| Evento | `TaggedValidatedEvent` con `validatedRecordJson` (claves = nombres de dominio, SUPUESTO C-CUR-07) | `registro:REGISTRO_ILEGIBLE`, campos nulos |
| Registros | Solo aceptados: sin severidad 1 ni `OxygenDuplicate` (regla de Acceptance `errorlevelNotIn("1") and errorcodeNotIn("OxygenDuplicate")`) | Curation no filtra: curaría también los inválidos |
| `errorCodes` | Conservan los de severidad 3 (y 0) para RF-CUR-10 | No se puede omitir/anular por severidad |
| Guard-rails | `asOfDate` (epoch de `FECHA_ACTUALIZACION_CNS`), `subscriber` 999722, `subscriberRelation` (`NUM_DOCUMENTO`), `lineage` (DNP) ya resueltos en Validation | Curation los copia tal cual; no los corrige |
| `lineage.fileName` | Coincide con el `filePattern` de Curation (mismo nombre que vio Validation) | El motor descarta el evento sin log |
| Borrados | `inferredAsDeleted` marcado por `contextualOverride` solo con decisión humana (D2) | El motor los cura con su processor de borrado |
| Ruta | `acceptedDir` del ambiente; discrepancia de rutas entre HU-004 y HU-005 abierta (N1) | Confirmar antes de ejecutar |
| Archivos rechazados | No llegan a Curation | — |

Salida hacia Synthesis: `curatedDir/flow|stock` + entradas del domain dictionary (nombre y tipo de
cada campo publicado; sin renombrar después).

## Referencias

| Archivo | Para qué |
|---|---|
| `references/alcance-y-glosario.md` | alcance, frontera con otras etapas, automático vs guiado, glosario |
| `references/patrones.md` | patrones, antipatrones, contrato y capas (de `curation_patrones.yaml`) |
| `references/inventario-plantillas.md` | CUR-TPL-01..12, huecos y orden (de `curation_template.config`) |
| `references/especificacion-sisben.md` | layout, transformaciones, homologación, lookups, campos publicados |
| `references/dsl-config-reference.md` | claves HOCON, lookups, domain dictionary, constructor |
| `references/runbook-ejecucion.md` | generar, integrar, compilar, local / Docker / EMR, diagnóstico |
| `references/interpretar-salida.md` | lectura del evento curado, motivos, conciliación, ejemplo |
| `references/decisiones-y-abiertos.md` | D*, F-*, C-CUR-*, puntos abiertos, discrepancias |
| `assets/checklist-entrega.md` | checklist previo a la propuesta |
| `scripts/DEPRECADOS.md` | generador Java deprecado y evidencia de equivalencia con el Python |

## Requisitos para los scripts

Python 3.9+ con `pyyaml` y `jsonschema` para el generador (`scripts/render_templates.py`) y la
implementación de referencia (`scripts/curation_reference.py`); `pytest` para las pruebas (`pyhocon` opcional).
Sin Java ni Maven.

## Fuentes

Documentos de diseño y requisitos del piloto SISBEN V4 (citados por nombre):

- `curation_patrones.yaml` y `curation_template.config` (patrones e inventario de plantillas de Curation).
- `CURATION_DISEÑO_PROCESSOR.md` (diseño del processor de Curation).
- `requirements.md` del piloto SISBEN V4 (§4.2 layout, §4.4 homologación, §6 Curation, §9 decisiones D1-D14, §10 preguntas abiertas).
- HU-SISBEN-001, 003, 004, 005 y 006 (historias de usuario SISBEN).
- `04_Etapas_Validation-Acceptance.md` y `02_Especificación_SISBEN_V4_validacion.md` (entendimiento funcional).
- Código de oxygen-curation (`SparkCurator.scala`, `Curator.scala`, `CurationProcessor.scala`,
  `CuratorUtils.scala`, `CreditCurationProcessorJson.scala`, `CreditCuratedEventJson.scala`, `Util.scala`), su README y Dockerfile.
- Código de oxygen-core (`CurationStageParser.scala`, `CurationStageConfiguration.scala`,
  `CurationRunMode.java`, `TypesafeOxygenConfigurationParser.scala`, `oxygen-config-default.conf`, `Constants.scala`).
- Archivo de hechos compartidos de los skills EDF (claves verificadas, supuestos F-01..F-17).
