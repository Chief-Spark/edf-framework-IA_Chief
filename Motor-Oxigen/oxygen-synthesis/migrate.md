# Migration from the Oxygen Pipeline Synthesis Stage<a name="pagetop"></a>

1. [Confirming the Oxygen Version](#version)
2. [Transferring the Extension Code](#extension)
3. [Adjusting Configurations](#config)
4. [Building and Using the InnerSource Synthesis JAR](#use)

---
## 1. Confirming the Oxygen Version <a name="version"></a>
Migrating an Oxygen Pipeline Synthesis stage to InnerSource Synthesis becomes straightforward when their dependencies align, allowing Synthesis to function within the same environment as the other stages, either in an on-premises or cloud computing cluster.

InnerSource Synthesis operates exclusively on Java 11, Scala 2.12, and Spark 3.3. Hence, before proceeding with the migration, ensure that the version of the Oxygen Pipeline currently in use is compatible and shares the same dependencies. 

For more information on dependency requirements, please refer to:
> Prerequisites 
> - JDK 11
> - Scala 2.12.15
> - Spark 3.3.1
> - Hadoop 3
>
> [AWS EMR Environment](example/images/emr_applications.png)

## 2. Transferring the Extension Code <a name="extension"></a>
Transfer the extension classes, like the *RelationshipMigrationProcessorImpl.scala* used in the "Get Started" example, into the InnerSource Synthesis source code, preferably under the designated directory for extensions:
> src/main/scala/com/experian/edf/oxygen/consistencycheck/extension/

Modify its package name and import class paths as needed. For instance, the package name of *RelationshipMigrationProcessorImpl.scala* would change to:
> package com.experian.edf.oxygen.consistencycheck.extension.migration

## 3. Adjusting Configurations <a name="config"></a>
Retain the default output structure:
> oxygen.features.enable.defaultOutputStructure = true

Maintain the rest of the configurations consistent with those in the existing Oxygen Pipeline Synthesis stage, particularly for the following parameters:
> oxygen.application.paths.userHome
> 
> oxygen.application.paths.appName

Direct the extension classes to your ported extension classes, for example:
> *oxygen.stage.consistencyCheck.relationshipMigrationProcessorClassName = "com.experian.edf.oxygen.consistencycheck.extension.migration.RelationshipMigrationProcessorImpl"


Specify the Lookup path:
> oxygen.application.paths.lookupHome

## 4. Building and Using the InnerSource Synthesis JAR <a name="use"></a>
Build the InnerSource Synthesis JAR:
> mvn -DskipTests clean package

Prepare the command line to run the InnerSource Synthesis JAR with your configurations.
- Example command line to run locally
> spark-submit --master local[2] --driver-memory 2g --executor-memory 2g --packages org.apache.spark:spark-avro_2.12:3.3.1 --class com.experian.edf.oxygen.consistencycheck.app.SynthesisApp target/oxygen-synthesis-innersource-1.0-jar-with-dependencies.jar env.conf consistency-application.conf oxygen-lookup.conf

- Example command line to run on AWS
> spark-submit \\  
> --master yarn \\  
> --deploy-mode cluster \\  
> --executor-cores 2 \\  
> --executor-memory 4G \\  
> --num-executors 2 \\  
> --driver-memory 4G \\  
> --conf "spark.yarn.submit.waitAppCompletion=true" \\  
> --files $(echo s3://oxygen-innersource/c67099a-synthesis/conf/\*.\* | tr ' ' ',') \\  
> --jars "s3://oxygen-innersource/c67099a-synthesis/lib/\*.jar" \\  
> --packages org.apache.spark:spark-avro_2.12:3.3.1 \\  
> --conf "spark.dynamicAllocation.enabled=false" \\  
> --conf "spark.driver.extraClassPath=/usr/lib/hadoop-lzo/lib/\*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-> sdk/\*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/\*:/usr/share/aws/emr/emrfs/auxlib/\*:/usr/share/aws/emr/goodies/lib/emr-spark-goodies.jar:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/\*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar" \\  
> --conf "spark.executor.extraClassPath=/usr/lib/hadoop-lzo/lib/\*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-sdk/\*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/\*:/usr/share/aws/emr/emrfs/auxlib/\*:/usr/share/aws/emr/goodies/lib/emr-spark-goodies.jar:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/\*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar" \\  
> --conf "spark.executor.extraJavaOptions=-XX:+UseG1GC" \\  
> --conf "spark.hadoop.mapreduce.fileoutputcommitter.algorithm.version=2" \\  
> --conf "spark.driver.extraJavaOptions=-Dfile.encoding=utf-8" \\  
> --conf "spark.executor.extraJavaOptions=-Dfile.encoding=utf-8" \\  
> --conf "spark.executor.extraJavaOptions=-Dlog4j.debug=true" \\  
> --conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.properties" \\  
> --class com.experian.edf.oxygen.consistencycheck.app.SynthesisApp \\  
> s3://oxygen-innersource/c67099a-synthesis/lib/oxygen-synthesis-innersource-1.0-jar-with-dependencies.jar \\  
> env.conf \\  
> consistency-application.conf \\  
> oxygen-lookup.conf

Substitute this command line for the one in your Oxygen Pipeline DAGS (e.g. Airflow) specifically for the Synthesis stage.

[Return to Top](#pagetop)
