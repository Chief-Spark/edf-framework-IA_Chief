# Runbook de ejecución

Todo paso que copia archivos al fork o ejecuta un job requiere **aprobación explícita del técnico**
(HITL).

| Pasos | Quién | Herramientas |
|---|---|---|
| §1 a §3 (verificar, generar, prevalidar) | La skill | Solo Python 3 (PyYAML y jsonschema). Sin compilar nada |
| §4 a §7 (copiar al fork, compilar, probar, ejecutar) | El **ingeniero**, en el fork | Las del repositorio Oxygen (JDK 11, Maven, Spark). La skill no los ejecuta |

## 0. Antes de empezar

- Rama nueva en el fork de Colombia de oxygen-validation; nunca en `main`.
- Compilar requiere el JAR del framework desde el repositorio de artefactos de la organización. Si el
  equipo no tiene acceso (F-01), la compilación queda **no certificada** y solo se ejecutan los pasos 1–3.
- Nunca copiar ni abrir `settings-artifactory.xml`: contiene credenciales. Usar la configuración de
  Maven que el equipo de plataforma entrega por su canal seguro. Si ese archivo está versionado con
  credenciales en claro, reportarlo para rotación.

## 1. Verificar el source-spec

El generador es `scripts/render_templates.py` (Python 3, sin compilar):

```bash
python3 scripts/render_templates.py --spec mi-source-spec.yaml --solo-verificar
```

Falla (código 2) ante: esquema inválido, códigos de longitud distinta, colisiones de subcadena,
códigos de 11 caracteres, referencias de catálogo rotas, `filePattern` sin `.*`, clave de duplicado
vacía, nombre de fixture que no cumple el regex.

## 2. Generar la propuesta

```bash
python3 scripts/render_templates.py --spec mi-source-spec.yaml --out salida/
python3 scripts/verificar_hocon.py --generado salida/ --fuente <fuente> --archivo-ejemplo <fixture>   # opcional (pyhocon)
```

Sin `pyhocon`, `verificar_hocon.py` sale con código 3: el HOCON queda **no verificado** (no es un fallo
ni un "OK"). El parse con el parser real del motor (Typesafe Config) ocurre al compilar y probar en el fork (§5).

`salida/manifest.json` lista archivos, sha256, supuestos y requisitos de ambiente. Presentar al técnico
el diff contra el fork (`git diff --no-index <fork>/src salida/src`) y **esperar aprobación**.

## 3. Pre-validar un archivo localmente (sin Spark)

```bash
python3 scripts/prevalidar_archivo.py --spec mi-source-spec.yaml --archivo <archivo> \
  [--lookups salida/src/test/resources/<fuente>/lookup] [--hoy AAAA-MM-DD] [--detalle] [--salida resumen.json]
```

Simula nombre → header → filtro → registros → duplicados → métricas. El resumen no contiene valores de
campos. Usar solo archivos autorizados para el ambiente local; nunca copiar datos reales al repo.
No modela el footer (E8): con `filas_footer > 0` el script sale con código 2 (`interpretar-salida.md`).
En Windows, si se redirige el JSON de la consola, fijar antes `PYTHONIOENCODING=utf-8` (si no, sale en
cp1252) o usar `--salida`, que siempre escribe UTF-8.

## 4. Copiar al fork (con aprobación)

1. Código Scala de `salida/src/` a las mismas rutas del fork (si la Capa A ya existe, no sobrescribir;
   comparar).
2. Pegar el objeto de `propuesta/validation-configs-<fuente>.conf` como **elemento nuevo** de
   `oxygen.stage.validation.configs = [...]` en `validation-common-application-inner.conf`.
3. Pegar los objetos de `propuesta/lookups-<fuente>.conf` en `oxygen.lookups = [...]` del inner y ubicar
   los CSV de catálogo reales (no los sintéticos) en `oxygen.application.paths.lookupHome`, con la ruta
   relativa de `catalogos[].archivo`: cada `file` es `${oxygen.application.paths.lookupHome}"/<archivo>"`
   (AUD-V-06). `docs/codigos-error-<fuente>.csv` es documentación: no se copia al fork (AUD-V-19).
4. No editar `*-common-application.conf` base ni otros elementos existentes.

## 5. Compilar y probar (requiere JAR)

Prerrequisitos del repo (`Motor-Oxigen/oxygen-validation`: `pom.xml`, `Dockerfile`, `README.MD`, `migrate.md`): **JDK 11**, Maven, Scala 2.12.15, Spark 3.3.1, Hadoop 3. El motor "opera exclusivamente" con esa combinación (`maven.compiler.source/target = 11`). El skill no necesita Java: el generador vigente es Python (`scripts/render_templates.py`); el generador Java (`scripts/java/`) está deprecado y no se ejecuta.

```bash
mvn -DskipTests clean package       # JAR: target/oxygen-validation-innersource-1.0-jar-with-dependencies.jar
mvn test                            # requiere ScalaTest 3.2 + runner JUnit 4 en el pom (VAL-AP08)
```

**Compile-check opcional sin JAR:** la Capa A, `<Fuente>Spec`, `FieldValidatorsSpec` y
`<Fuente>ValidationProcessorSpec` no importan clases del framework; compilan con Scala 2.12 + ScalaTest
en un proyecto mínimo. Si el equipo tiene la fuente del módulo de configuración del framework, se puede
además parsear el bloque HOCON con su parser real usando un stub con el FQCN del processor (la clave
`processor` hace `Class.forName`).

## 6. Ejecutar

Argumentos: `env.conf`, `validation-application.conf` (o el inner del ambiente) y opcionalmente
`spark.conf`. Clase principal: `com.experian.edf.oxygen.validation.app.SparkValidationApp`.

**Local (spark-submit):**

```bash
spark-submit --master local --class com.experian.edf.oxygen.validation.app.SparkValidationApp \
  target/oxygen-validation-innersource-1.0-jar-with-dependencies.jar env.conf validation-application.conf spark.conf
```

**Local sin spark-submit (laptop):**

```bash
java -cp target/<jar> com.experian.edf.oxygen.validation.app.SparkValidationApp env.conf validation-application.conf spark.conf
```

Ejecute el motor con JDK 11. Si `java -cp` falla con un `IllegalAccessError`, compruebe primero que `java -version` sea 11 y no la 17 del generador.

**Docker (imagen del repo: Python 3.11 + Spark 3.3.1 + Scala 2.12.15 + JDK 11):**

```bash
docker build -t innersource-oxygen:latest .
docker run -it -v "$(pwd)":/data -p 8888:8888 innersource-oxygen /bin/bash
# dentro del contenedor: el spark-submit anterior
```

El puerto 8888 expone JupyterLab sin autenticación por defecto: usar solo en local.

**EMR Serverless:** la imagen del repo parte de `emr-serverless/spark/emr-6.10.0` e instala Amazon Corretto 17. Enviar
el JAR y los `.conf` a la ubicación del ambiente y lanzar el job con la clase principal y los mismos
argumentos. Rutas y roles los define el equipo de plataforma; no incluir credenciales en los `.conf`.

## 7. Checklist de ambiente antes del primer job

- `oxygen.application.lambda = batch`.
- `oxygen.application.historicAttributes = false`.
- `oxygen.features.enable.emitStockMetrics` presente.
- `oxygen.features.enable.defaultOutputStructure` confirmado (F-04).
- `paths.validationDir` y `paths.validatedDir` del ambiente; con CustomMode, `appHome` se borra al final.
- Las tablas de `catalogos[]` del source-spec (7 en el ejemplo de forma; las que diga la spec de la ingesta)
  en `lookupHome`, con las columnas declaradas en `keys[].index`.

Luego interpretar la salida con `interpretar-salida.md`.
