# Migration from the Oxygen Pipeline StateCalc Stage<a name="pagetop"></a>

1. [Confirming the Oxygen Version](#version)
2. [Transferring the Extension Code](#extension)
3. [Adjusting Configurations](#config)
4. [Building and Using the InnerSource StateCalc JAR](#use)

---
## 1. Confirming the Oxygen Version <a name="version"></a>
Migrating an Oxygen Pipeline StateCalc stage to InnerSource StateCalc becomes straightforward when their dependencies align, allowing StateCalc to function within the same environment as the other stages, either in an on-premises or cloud computing cluster.

InnerSource StateCalc operates exclusively on Java 11, Scala 2.12, and Spark 3.3. Hence, before proceeding with the migration, ensure that the version of the Oxygen Pipeline currently in use is compatible and shares the same dependencies. 

For more information on dependency requirements, please refer to:
> Prerequisites 
> - JDK 11
> - Scala 2.12.15
> - Spark 3.3.1
> - Hadoop 3
>
> [AWS EMR Environment](example/images/emr_applications.png)

## 2. Transferring the Extension Code <a name="extension"></a>
Transfer the extension classes, like the *ISIdentityMatcher.scala* used in the "Get Started" example, into the InnerSource Pinning source code, preferably under the designated directory for extensions:
> src/main/scala/com/experian/edf/oxygen/statecalc/extension/

Modify its package name and import class paths as needed. For instance, the package name of *ISIdentityMatcher.scala* would change to:
> package com.experian.edf.oxygen.pinning.extension

## 3. Adjusting Configurations <a name="config"></a>
Direct the identityMatcher to your ported extension IdentityMatcher class:
> oxygen.stage.pinning.identityMatcher = "com.experian.edf.oxygen.pinning.extension.ISIdentityMatcher"

Retain the default output structure:
> oxygen.features.enable.defaultOutputStructure = true

Maintain the rest of the configurations consistent with those in the existing Oxygen Pipeline Pinning stage, particularly for the following parameters:
> oxygen.application.paths.userHome
> 
> oxygen.application.paths.appName

## 4. Building and Using the InnerSource Pinning JAR <a name="use"></a>
Build the InnerSource Pinning JAR:
> mvn -DskipTests clean package

Prepare the command line to run the InnerSource Pinning JAR with your configurations.
- Example command line to run locally
> spark-submit --master local[2] --driver-memory 2g --executor-memory 2g --class com.experian.edf.oxygen.pinning.app.PinningApp target/oxygen-pinning-innersource-1.0-jar-with-dependencies.jar env.conf pinning-application.conf

- Example command line to run on AWS
> spark-submit --verbose \
> --master yarn \
> --deploy-mode cluster \
> --executor-cores 2 \
> --driver-memory 4g \
> --executor-memory 4g \
> --conf "spark.yarn.submit.waitAppCompletion=true" \
> --files $(echo s3://oxygen-innersource/demo_statecalc/conf//*.* | tr ' ' ',') \
> --jars "s3://oxygen-innersource/demo_statecalc/lib/*.jar" \
> --conf "spark.dynamicAllocation.enabled=false" \
> --conf "spark.executor.extraJavaOptions=-XX:+UseG1GC" \
> --conf "spark.hadoop.mapreduce.fileoutputcommitter.algorithm.version=2" \
> --conf "spark.driver.extraJavaOptions=-Dfile.encoding=utf-8" \
> --conf "spark.executor.extraJavaOptions=-Dfile.encoding=utf-8" \
> --conf "spark.executor.extraJavaOptions=-Dlog4j.debug=true" \
> --conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.properties" \
> --class com.experian.edf.oxygen.statecalculation.app.StateCalcApp \
> s3://oxygen-innersource/demo_statecalc/lib/oxygen-statecalc-innersource-1.0-jar-with-dependencies.jar env.conf statecalc-application.conf

Substitute this command line for the one in your Oxygen Pipeline DAGS (e.g. Airflow) specifically for the Pinning stage.

[Return to Top](#pagetop)
