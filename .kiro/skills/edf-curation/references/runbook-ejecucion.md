# Runbook de ejecución

Requisitos de ejecución de oxygen-curation (`Motor-Oxigen/oxygen-curation`: `pom.xml`, `Dockerfile`,
`README.md` y `migrate.md` coinciden): JDK 11, Scala 2.12.15, Spark 3.3.1,
Hadoop 3, Maven para compilar. El skill no compila nada: genera con Python (§0) y el ingeniero compila y ejecuta en el fork (§1 a §5). **La compilación no está certificada desde el skill** (F-01: sin
acceso a Artifactory ni a los JAR del framework). Todo lo que sigue se ejecuta en el fork, con el
classpath que el equipo ya tiene.

Prohibido en cualquier paso: copiar, leer o adjuntar `settings-artifactory.xml` u otro archivo con
credenciales; usar datos reales en ambientes locales. Si un archivo con credenciales quedó expuesto,
pedir su rotación.

## 0. Generar y revisar (lo que hace la skill; sin framework y sin compilar)

```bash
cd edf-curation
python -m pip install pyyaml jsonschema pytest     # una vez
python scripts/render_templates.py \
  --spec assets/examples/sisben/source-spec.sisben.yaml \
  --out  assets/examples/sisben/generated          # ATENCIÓN: --out se borra y se recrea
python -m pytest scripts/tests -q
python scripts/hocon_check.py assets/examples/sisben/generated/fragmentos/*.conf
```

La skill nunca ejecuta `mvn`, `javac` ni `java -jar`: `scripts/java/` es el generador anterior, deprecado
(`scripts/DEPRECADOS.md`). Los pasos 1 a 5 los hace el ingeniero en el fork.

Para otra fuente: copiar el source-spec, ajustarlo, y pasar `--spec`/`--out` propios. La carpeta de
lookups del fixture debe traer `LEEME.md` (marca de sintético); sin él el generador sale con código 1
(desde el 2026-10-21).

Si `parametros.zona_horaria_fechas` difiere de `parametros.zona_asofdate_etapa_anterior` (por defecto
`America/Bogota`), el generador escribe una `ADVERTENCIA: … (C-CUR-04)` en stderr y en
`MANIFEST.json["advertencias"]`; no detiene la generación (código 0, stdout termina en `OK: …`). Con la
spec SISBEN (`UTC`) aparece siempre mientras C-CUR-04 siga abierta (ADR-G4).

## 1. Integrar en el fork

1. Copiar `generated/src/...` a las mismas rutas del fork.
2. Fusionar `generated/fragmentos/curation-common-application-inner.sisben.conf` dentro de
   `oxygen.stage.curation.configs` del inner (agregar el elemento; no reemplazar la lista).
3. Fusionar `oxygen-lookup-inner.sisben.conf` en `oxygen.lookups` y subir
   `202_SISBEN_PROCESS_ID_TYPE_HO.csv` (real, no el sintético) a `lookupHome/sisben/`.
4. Agregar las entradas de `colombia-domain-dictionary-inner.sisben.json` al dictionary del inner.
5. Verificar en el ambiente: `emitStockMetrics` y `consistency.paths.domainDictionaryFile` existen;
   `enable.defaultCurationProcessor` apagado; ningún `excludes` cubre SISBEN.

## 2. Compilar y probar (en el fork)

```bash
mvn -DskipTests clean package       # o ./build_curation.sh
mvn test                            # TransformacionesSpec y SISBENCurationProcessorSpec
```

`TransformacionesSpec` (Capa A) solo necesita Scala + ScalaTest, con `org.scalatestplus:junit-4-13_2.12`
y JUnit 4 por su `@RunWith(classOf[JUnitRunner])`; puede compilarse aparte como check
parcial. `SISBENCurationProcessorSpec` necesita el framework.

## 3. Local

```bash
spark-submit --master local[2] --driver-memory 2g --executor-memory 2g \
  --class com.experian.edf.oxygen.curation.app.CurationApp \
  target/oxygen-curation-innersource-1.0-jar-with-dependencies.jar \
  env.conf curation-application.conf oxygen-lookup.conf
```

En `env.conf`/`curation-application.conf` locales: `oxygen.stage.acceptance.paths.acceptedDir` →
carpeta con el fixture sintético (un subdirectorio cuyo nombre cumpla el `filePattern`, p. ej.
`SISBEN_EXPERIAN_202401.20240205103000-0500/` con `accepted-events.json` como `part-00000...json`),
`oxygen.stage.curation.paths.curatedDir` → carpeta de salida, `oxygen.application.paths.lookupHome`
→ carpeta con `sisben/*.csv`. Usar rutas relativas o variables; no versionar rutas personales.

## 4. Docker

```bash
./build_docker.sh        # imagen con JDK 11, Scala 2.12.15, Spark 3.3.1, Hadoop 3
./run_docker.sh          # monta el repo en /data
# dentro del contenedor: mismo spark-submit del paso 3, con rutas bajo /data
```

## 5. EMR

1. Subir el JAR a `s3://<bucket>/<app>/lib/`, los tres `.conf` a `.../conf/`, los lookups a
   `.../lookup/` (permisos por rol del clúster, sin claves embebidas).
2. En el nodo primario:

```bash
spark-submit --master yarn --deploy-mode cluster \
  --executor-cores 2 --executor-memory 4G --num-executors 2 --driver-memory 4G \
  --conf spark.dynamicAllocation.enabled=false \
  --conf "spark.driver.extraJavaOptions=-Dfile.encoding=utf-8" \
  --conf "spark.executor.extraJavaOptions=-Dfile.encoding=utf-8" \
  --files "$(echo s3://<bucket>/<app>/conf/*.* | tr ' ' ',')" \
  --class com.experian.edf.oxygen.curation.app.CurationApp \
  s3://<bucket>/<app>/lib/oxygen-curation-innersource-1.0-jar-with-dependencies.jar \
  env.conf curation-application.conf oxygen-lookup.conf
```

3. `runMode`: FLOW para el archivo del periodo, STOCK para regenerar; lo fija la orquestación.

## 6. Diagnóstico rápido

| Síntoma | Causa probable | Acción |
|---|---|---|
| No arranca: `Unable to Cast ... to Processor` | clase no está en el JAR o constructor distinto | revisar FQCN del bloque y el constructor `(CurationProcessorContext)` |
| No arranca: `NoSuchElementException` | falta `emitStockMetrics` o `domainDictionaryFile` | agregar al conf del ambiente |
| Dataset éxito pero sin salida | `excludes` lo cubre, o `filePattern` no coincide con la ruta sin extensión | revisar `excludes`; probar el patrón con el nombre del directorio |
| Menos eventos curados que aceptados | `lineage.fileName` no coincide con el patrón, o excepción por registro | comparar conteos; buscar `Curation of Event ... failed IGNORING` (no debería ocurrir con el processor generado) |
| Salida con todos los campos del default | `defaultCurationProcessor` activo | apagar la feature |
| `tipoDocumento` nulo en todos | lookup 202 vacío y tabla embebida desactualizada | revisar el CSV y el `warn` de arranque |
