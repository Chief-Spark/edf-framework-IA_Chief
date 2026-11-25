# Runbook de ejecución — Acceptance

Prerrequisitos del motor (`Motor-Oxigen/oxygen-acceptance`: `pom.xml`, `Dockerfile`, `migrate.md`): JDK 11, Scala 2.12.15, Spark 3.3.1, Hadoop 3. Los usa el ingeniero en el fork; la skill no compila ni ejecuta nada (genera con Python). Clase principal
`com.experian.edf.oxygen.acceptance.app.AcceptanceApp`; argumentos: `env.conf` y el conf de la
aplicación (`acceptance-application.conf` o el inner del fork). **Compilación no certificada (F-01)**:
sin acceso al repositorio de artefactos ni a los JAR del framework, nada de lo generado se compiló.

> Nunca copie, lea ni comparta `settings-artifactory.xml` ni otras credenciales. Si necesita
> construir el JAR, use las credenciales que su equipo le asigne por el canal oficial.

## 0. Antes de ejecutar (local, sin framework)

```bash
cd <raíz del skill>
python3 scripts/render_templates.py --spec <su-spec>.yaml --out <salida> --audit   # huecos y supuestos (sin compilar)
python3 scripts/simular_decision.py --spec <su-spec>.yaml     # decisión esperada por archivo
python3 scripts/hocon_check.py <salida>/hocon/*.conf          # parse del subconjunto HOCON
python3 -m pytest scripts/tests -q                            # pruebas del skill
python3 -m coverage run -m pytest scripts/tests -q            # cobertura (opcional)
python3 -m coverage report --include=scripts/hocon_check.py --fail-under=80        # una vez por script (AC-31)
```

Con `--audit`, después de los huecos, el generador imprime una línea `supuesto  S-ACC-xx  impacto=…
donde=…  texto=…` por cada supuesto del bloque `supuestos` de la spec (AC-27); sin `--audit`, las
imprime después de `generado`/`omitido`. Llévelas a la propuesta (Paso 4 de `SKILL.md`).

El §0 lo hace la skill, solo con Python. Del §1 en adelante lo hace el **ingeniero** en el fork (JDK 11,
Maven, Spark); la skill no los ejecuta.

## 1. Integrar en el fork (regla aditiva)

1. Abra `env/application-conf/innersource/acceptance-common-application-inner.conf`.
2. Copie el elemento de `hocon/<fuente>-acc-tpl-02-file-conditions.conf` **dentro** de la lista
   `oxygen.stage.acceptance.fileConditions` existente (créela si no existe). Busque antes que `name`
   y `reasonCode` no existan.
3. Copie el elemento de `hocon/<fuente>-acc-tpl-01-acceptance-block.conf` dentro de
   `oxygen.stage.acceptance.configs`.
4. (Solo si se generó) agregue el lookup de ACC-TPL-03 en `oxygen.lookups` y el CSV en el directorio
   de lookups del ambiente.
5. Copie el Spec y el fixture a `src/test/...` y ejecute `mvn test` (requiere el JAR del framework).
6. Confirme en `env.conf` del ambiente: `oxygen.application.lambda = batch`,
   `oxygen.features.enable.emitStockMetrics` presente, `defaultOutputStructure` según el ambiente.

## 2. Local (spark-submit)

```bash
mvn -DskipTests clean package
spark-submit --master local \
  --class com.experian.edf.oxygen.acceptance.app.AcceptanceApp \
  target/oxygen-acceptance-innersource-1.0-jar-with-dependencies.jar \
  env.conf acceptance-application.conf
```

Coloque la entrada (salida de Validation) en `<appHome>/validation/validated/<archivo>.<datasetId>/`.
Para una prueba de humo use el fixture sintético de `src/test/resources/<fuente>/validated/`.

## 3. Docker

Imagen del repositorio del motor (JDK 11 + Scala 2.12.15 + Spark 3.3.1 + Hadoop 3 + Python 3.11):

```bash
./build_docker.sh      # solo la primera vez
./run_docker.sh        # monta el repo en /data
# dentro del contenedor, con rutas /data/...:
spark-submit --master local --class com.experian.edf.oxygen.acceptance.app.AcceptanceApp \
  target/oxygen-acceptance-innersource-1.0-jar-with-dependencies.jar env.conf acceptance-application.conf
```

## 4. AWS EMR

1. Suba el JAR a `s3://<bucket>/<prefijo>/lib/`, los `.conf` a `s3://<bucket>/<prefijo>/conf/` y la
   entrada a la ruta `validated` configurada.
2. Cluster EMR con Spark/Hadoop compatibles.
3. Ejecute:

```bash
spark-submit --master yarn --deploy-mode cluster \
  --executor-cores 2 --executor-memory 4G --num-executors 2 --driver-memory 4G \
  --conf spark.yarn.submit.waitAppCompletion=true \
  --conf spark.dynamicAllocation.enabled=false \
  --conf "spark.driver.extraJavaOptions=-Dfile.encoding=utf-8" \
  --conf "spark.executor.extraJavaOptions=-Dfile.encoding=utf-8" \
  --files $(echo s3://<bucket>/<prefijo>/conf/*.* | tr ' ' ',') \
  --jars "s3://<bucket>/<prefijo>/lib/*.jar" \
  --class com.experian.edf.oxygen.acceptance.app.AcceptanceApp \
  s3://<bucket>/<prefijo>/lib/oxygen-acceptance-innersource-1.0-jar-with-dependencies.jar \
  env.conf acceptance-application.conf
```

4. Revise la salida (ver `interpretar-salida.md`) y termine el cluster.

## 5. Problemas frecuentes

| Síntoma | Causa probable | Acción |
|---|---|---|
| Excepción al arrancar por `emitStockMetrics` | Falta `oxygen.features.enable.emitStockMetrics` | Agréguela al `env.conf` |
| "No acceptance configuration found" / `MatchError` | `filePattern` no coincide con la ruta sin extensión | Pruebe la ruta real en `claves_de_prueba` |
| Excepción en ejecutor dentro de `getAcceptanceInfo` | `filePattern` no coincide con `lineage.fileName` | Igual que arriba, clave `lineage_fileName` |
| Todos los registros aceptados con errores sev. 1 | `recordAcceptance` falla al evaluarse (default `true`) | Corra ACC-TPL-05; busque "Setting default evaluation to true" en el log |
| "Evaluating reject configuration using older syntax" | `reject` no compiló en la sintaxis nueva | Revise la gramática (sin `and`/`or`, nombres válidos) |
| Archivo nunca se mueve | `reject` y `accept` en `false` (pendiente), p. ej. falta `RQS` | Revise el contrato con Validation |
| No aparecen `inferred-*-asDeleted` | Inferencia falló en silencio (ACC-AP03) | Busque el error en el log; caso de prueba de ACC-TPL-04 |
