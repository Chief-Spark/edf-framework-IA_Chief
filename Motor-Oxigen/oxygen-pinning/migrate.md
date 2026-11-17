# Migration from the Oxygen Pipeline Pinning Stage<a name="pagetop"></a>

1. [Confirming the Oxygen Version](#version)
2. [Transferring the Extension Code](#extension)
3. [Adjusting Configurations](#config)
4. [Building and Using the InnerSource Pinning JAR](#use)

---
## 1. Confirming the Oxygen Version <a name="version"></a>
Migrating an Oxygen Pipeline Pinning stage to InnerSource Pinning becomes straightforward when their dependencies align, allowing Pinning to function within the same environment as the other stages, either in an on-premises or cloud computing cluster.

InnerSource Pinning operates exclusively on Java 11, Scala 2.12, and Spark 3.3. Hence, before proceeding with the migration, ensure that the version of the Oxygen Pipeline currently in use is compatible and shares the same dependencies. 

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
> src/main/scala/com/experian/edf/oxygen/pinning/extension/

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
> spark-submit --verbose \\  
> --master yarn \\  
> --deploy-mode cluster \\  
> --executor-cores 4 \\  
> --executor-memory 8G \\  
> --num-executors 40 \\  
> --driver-memory 8G \\  
> --conf "spark.yarn.submit.waitAppCompletion=true" \\  
> --files $(echo s3://oxygen-innersource/demo-pinning/conf//\*.\* | tr ' ' ',') \\  
> --jars "s3://oxygen-innersource/demo-pinning/lib/\*.jar" \\  
> --conf "spark.dynamicAllocation.enabled=false" \\  
> --conf "spark.driver.extraClassPath=/usr/lib/hadoop-lzo/lib/\*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-> sdk/\*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/\*:/usr/share/aws/emr/emrfs/auxlib/\*:/usr/share/aws/emr/goodies/lib/emr-spark-goodies.jar:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/\*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar" \\  
> --conf "spark.executor.extraClassPath=/usr/lib/hadoop-lzo/lib/\*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-sdk/\*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/\*:/usr/share/aws/emr/emrfs/auxlib/\*:/usr/share/aws/emr/goodies/lib/emr-spark-goodies.jar:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/\*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar" \\  
> --conf "spark.executor.extraJavaOptions=-XX:+UseG1GC" \\  
> --conf "spark.hadoop.mapreduce.fileoutputcommitter.algorithm.version=2" \\  
> --conf "spark.driver.extraJavaOptions=-Dfile.encoding=utf-8" \\  
> --conf "spark.executor.extraJavaOptions=-Dfile.encoding=utf-8" \\  
> --conf "spark.executor.extraJavaOptions=-Dlog4j.debug=true" \\  
> --conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.properties" \\  
> --class com.experian.edf.oxygen.pinning.app.PinningApp \\  
> s3://oxygen-innersource/demo-pinning/lib/oxygen-pinning-innersource-1.0-jar-with-dependencies.jar \\  
> env.conf \\  
> pinning-application.conf

Substitute this command line for the one in your Oxygen Pipeline DAGS (e.g. Airflow) specifically for the Pinning stage.

[Return to Top](#pagetop)
