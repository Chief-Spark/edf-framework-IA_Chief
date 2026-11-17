# Checklist de entrega · Validation

Marcar cada punto antes de pedir la aprobación del técnico. Un punto sin marcar bloquea la entrega.

## 1. Source-spec

- [ ] `java -jar scripts/java/target/render-templates.jar --spec <spec> --solo-verificar` termina en `source-spec OK`.
- [ ] `mvn -q -f scripts/java/pom.xml verify` en verde (pruebas del generador, HOCON con Typesafe Config, cobertura ≥ 80 %).
- [ ] Cada `ASSUMPTION` del spec fue revisado con el técnico (confirmado o ajustado).
- [ ] Discrepancias documentales nuevas registradas en `references/decisiones-y-abiertos.md`.
- [ ] Fixture y `ejemplo_invalido` son sintéticos; ningún registro real.

## 2. Códigos y DSL

- [ ] Todos los códigos con la misma longitud; ninguno de 11 caracteres.
- [ ] Ningún código es subcadena de otro ni de `OxygenDuplicate`.
- [ ] Niveles de un carácter (`1`, `3`); sin severidad 0.
- [ ] Nombres de quality score fuera de operadores, literales y códigos.
- [ ] `validate[].fileName` y `validate[].header` presentes (F-11).

## 3. Processor y código Scala

- [ ] `extends ValidationProcessor` con constructor exacto `(ProcessorContext)`.
- [ ] Serializable; logger y catálogo `@transient lazy val`; sin `fileMetadata` en el constructor.
- [ ] Solo `createValidatedEvents(rawRecord, ctx)`; ninguna firma deprecada; sin `decodeErrorObject`,
      contextual ni atributos históricos.
- [ ] Ningún camino lanza por registro; errores como `ObjectError` con argumentos con nombre.
- [ ] `domainData` siempre `Some(DomainData(Some(asOfDate), subscriberRelation))`.
- [ ] `keysForDuplicate` nunca vacío; `listDuplicateEvents` agrega `OxygenDuplicate` a los no conservados.
- [ ] Sin `println`; logs sin PII (solo códigos y conteos).
- [ ] Imports de la versión 2404 del framework (no mezclar con 2306).

## 4. HOCON

- [ ] Bloque nuevo en `oxygen.stage.validation.configs[]` del inner; base `*-common-application.conf` intacto.
- [ ] `filePattern` empieza con `.*`; `processor` = FQCN generado.
- [ ] `headerRows`/`footerRows` (no `numberOf*`); `footerRows = 0` si no hay footer.
- [ ] `computeMetrics = true` y `dupKeyCheckEnabled = true` con `deDupVersion = "EXTN"`.
- [ ] Sin clave `encoding` (UTF-8, D5); `contexual.enabled = false`.
- [ ] Lookups nuevos en `oxygen.lookups[]` con `tableName`, `file`, `delimiter`, `keys[].index`.
- [ ] Ambiente verificado: `lambda = batch`, `historicAttributes = false`, `emitStockMetrics` presente,
      `defaultOutputStructure` confirmado.

## 5. Seguridad y portabilidad

- [ ] La salida no contiene credenciales (el renderer lo verifica) ni `settings-artifactory.xml`.
- [ ] Ningún archivo real de entrada/ejemplo copiado al fork o al skill.
- [ ] Compilación declarada **no certificada** si no hubo JAR (F-01).

## 6. Human-in-the-Loop

- [ ] Manifiesto, diff, fragmentos HOCON y supuestos abiertos presentados al técnico.
- [ ] Aprobación explícita recibida antes de copiar al fork.
- [ ] Aprobación explícita recibida antes de cada ejecución contra un clúster.
- [ ] Contrato de entrega a Acceptance compartido (`references/interpretar-salida.md`).

---

## Nota de verificación · generador en Java 17 (2026-10-20)

`render_templates.py` se reemplazó por el generador Java de `scripts/java/` (Temurin 17.0.20.1, Maven
3.10.0). `render_templates.py` y `tests/test_render_templates.py` quedan **deprecados**
(`scripts/DEPRECADOS.md`). La tabla de la versión 0.1.0, más abajo, queda como registro histórico.

| # | Verificación | Comando | Resultado |
|---|---|---|---|
| j1 | Equivalencia con el generador Python | ambos con la spec SISBEN, `--solo-verificar` y las 24 specs negativas de los casos de prueba | 26/26 iguales: mismo código de salida y mismos archivos byte a byte (incluido `manifest.json`); solo cambia la redacción de los mensajes del JSON Schema |
| j2 | Pruebas del generador | `mvn -q -f scripts/java/pom.xml verify` | 28 pruebas JUnit en verde; cobertura de líneas 91 % |
| j3 | HOCON parsea con el parser del motor | prueba `hoconGeneradoParseaConElParserDelMotor` (Typesafe Config 1.3.4) | OK: claves requeridas, sin claves ignoradas, `filePattern` acepta rutas con y sin `RT_` |
| j4 | Ejemplo versionado = regeneración | prueba `ejemploPersistidoIgualARegeneracion` | OK |
| j5 | Pruebas Python | `python -m pytest scripts/tests` | 82 passed, 3 skipped (sin pyhocon): 61 de los scripts vigentes, más las 21 deprecadas de `test_render_templates.py` |

## Nota de verificación · correcciones de la corrida priorizada (2026-10-21)

ADR-01 (catálogo que lanza → código interno de nivel 1), ADR-04 (pruebas y script deprecados fuera de la
suite y de la cobertura) y plan #5/#6. El ejemplo `assets/examples/sisben/generated/` se regeneró: cambian
`FieldValidators.scala`, `FieldValidatorsSpec.scala` y sus dos hashes en `manifest.json`.

| # | Verificación | Comando (desde la raíz del skill) | Resultado |
|---|---|---|---|
| k1 | Pruebas del generador | `mvn -q -f scripts/java/pom.xml verify` | 28 pruebas JUnit en verde (incluye `ejemploPersistidoIgualARegeneracion`); cobertura de líneas 91 % |
| k2 | Pruebas Python vigentes | `python -m pytest scripts/tests` | 88 passed, 3 skipped (sin pyhocon). `test_render_templates.py` ya no se recoge (`tests/conftest.py`) |
| k3 | Cobertura por script (P-5) | `coverage run -m pytest scripts/tests` y, por script, `coverage report --include=scripts/<script>.py --fail-under=80` (`.coveragerc` omite tests y `render_templates.py`) | `prevalidar_archivo.py` 96,45 %, `spec_utils.py` 88,68 %, `verificar_hocon.py` 96,88 %: los tres con código 0, sin pyhocon |
| k4 | Capa A con Scala 2.12.15 | `FieldValidatorsSpec` generado | 14 pruebas en verde (antes 13) |

## Nota de verificación del skill (versión 0.1.0, histórica)

Ejecutado sobre esta carpeta con Python 3.14.6, PyYAML 6.0.3, jsonschema 4.26.0, pytest 9.0.0 y
coverage 7.14.3. No se instaló Java/Maven/Scala; la compilación Scala queda **no certificada** (F-01).

| # | Verificación | Comando | Resultado |
|---|---|---|---|
| a | Todo `{{hueco}}` de las plantillas está declarado en el esquema (`x-placeholders`) y tiene valor en el spec SISBEN; el esquema no declara huecos huérfanos | tests `test_todo_hueco_de_plantilla_esta_en_el_esquema_y_en_el_contexto`, `test_el_esquema_no_declara_huecos_huerfanos` | OK |
| b1 | Render del ejemplo SISBEN | `python scripts/render_templates.py --spec assets/examples/sisben/source-spec.sisben.yaml --out assets/examples/sisben/generated` | `source-spec OK (22 campos, 27 códigos)`; 19 archivos + `manifest.json` |
| b2 | Sin huecos sin resolver en la salida | búsqueda de `{{` en `assets/examples/sisben/generated/` | 0 coincidencias |
| b3 | Pytest de los scripts | `python -m pytest scripts/tests` | 80 passed, 3 skipped (sin pyhocon); 83 passed (con pyhocon) |
| b4 | Cobertura | `coverage run --source=scripts --omit=scripts/tests/* -m pytest scripts/tests` | 93% total (render 90%, prevalidar 96%, verificar_hocon 92%) |
| b5 | Pre-validación del fixture coincide con lo esperado | `test_fixture_completo_coincide_con_el_resumen_esperado` | OK |
| c1 | YAML parsea (spec, esquema) | `yaml.safe_load` en tests y en el renderer | OK |
| c2 | Spec válido contra el esquema JSON Schema 2020-12 | `jsonschema.Draft202012Validator` (en `verify_spec`) | OK |
| c3 | HOCON parsea (bloque de validation y lookups) | `python scripts/verificar_hocon.py --generado assets/examples/sisben/generated --fuente sisben --archivo-ejemplo SISBEN_EXPERIAN_202406.20240630120000-0500.txt` con pyhocon 0.3.61 + pyparsing 3.2.5 cargados desde wheels temporales (no instalados) | `HOCON OK`; `filePattern` acepta rutas completas con y sin prefijo RT_ |
| d1 | Sin rutas del workspace de origen | búsqueda de rutas de carpetas del proyecto de origen, rutas absolutas de usuario y carpetas de agentes | 0 coincidencias |
| d2 | Sin credenciales | búsqueda de `password`, `secret`, `token`, `AKIA`, `PRIVATE KEY`, `settings-artifactory` | Solo prohibiciones en la documentación, los regex del detector y una clave falsa en un test del detector |
| d3 | Sin datos reales | búsqueda de identificadores de los archivos de ejemplo del repo (nombres de archivo de entrada, códigos y suscriptores del processor de ejemplo, notebooks, JSON de migración) y de archivos `.ipynb`, `.xml`, `.T` | 0 coincidencias |

Límites conocidos: la Capa A, la especificación y los tests Scala no se compilaron (no hay Scala en el
equipo de verificación); el processor, el evento y los metadatos dependen del JAR del framework. La
lógica de la Capa A está replicada y probada en Python (`prevalidar_archivo.py`), y los tests Scala se
generan desde los mismos casos del spec.
