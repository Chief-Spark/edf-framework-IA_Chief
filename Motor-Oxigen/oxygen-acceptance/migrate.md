# Migration from the Oxygen Pipeline Acceptance Stage<a name="pagetop"></a>

1. [Confirming the Oxygen Version](#version)
2. [Transferring the Extension Code](#extension)
3. [Adjusting Configurations](#config)
4. [Building and Using the InnerSource Acceptance JAR](#use)

---
## 1. Confirming the Oxygen Version <a name="version"></a>
Migrating an Oxygen Pipeline Acceptance stage to InnerSource Acceptance becomes straightforward when their 
dependencies align, allowing Acceptance to function within the same environment as the other stages, either 
in an on-premises or cloud computing cluster.

InnerSource Acceptance operates exclusively on Java 11, Scala 2.12, and Spark 3.3. 
Hence, before proceeding with the migration, ensure that the version of the Oxygen Pipeline currently 
in use is compatible and shares the same dependencies. 

For more information on dependency requirements, please refer to:
> Prerequisites 
> - JDK 11
> - Scala 2.12.15
> - Spark 3.3.1
> - Hadoop 3
>
> [AWS EMR Environment](example/images/emr_applications.png)

## 2. Adjusting Configurations <a name="config"></a>
Retain the default output structure:
> defaultOutputStructure = true

Maintain the rest of the configurations consistent with those in the existing Oxygen Pipeline Acceptance stage, 
particularly for the following parameters in env.conf file:
> userHome
> 
> appName


## 3. Building and Using the InnerSource Acceptance JAR <a name="use"></a>
Build the InnerSource Acceptance JAR:
> mvn -DskipTests clean package

Prepare the command line to run the InnerSource Acceptance JAR with your configurations.
- Example command line to run locally
> spark-submit --master local --class com.experian.edf.oxygen.acceptance.app.AcceptanceApp target/oxygen-acceptance-innersource-1.0-jar-with-dependencies.jar env.conf acceptance-application.conf

- Example command line to run on AWS
> spark-submit --verbose \\  
> --master yarn \\  
> --deploy-mode cluster \\  
> --executor-cores 2 \\  
> --executor-memory 4G \\  
> --num-executors 2 \\  
> --driver-memory 4G \\  
> --conf "spark.yarn.submit.waitAppCompletion=true" \\  
> --files $(echo s3://oxygen-innersource-test/demo-acceptance/conf//\*.\* | tr ' ' ',') \\  
> --jars "s3://oxygen-innersource-test/demo-acceptance/lib/\*.jar" \\  
> --conf "spark.dynamicAllocation.enabled=false" \\  
> --conf "spark.driver.extraClassPath=/usr/lib/hadoop-lzo/lib/\*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-> sdk/\*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/\*:/usr/share/aws/emr/emrfs/auxlib/\*:/usr/share/aws/emr/goodies/lib/emr-spark-goodies.jar:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/\*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar" \\  
> --conf "spark.executor.extraClassPath=/usr/lib/hadoop-lzo/lib/\*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-sdk/\*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/\*:/usr/share/aws/emr/emrfs/auxlib/\*:/usr/share/aws/emr/goodies/lib/emr-spark-goodies.jar:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/\*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar" \\  
> --conf "spark.executor.extraJavaOptions=-XX:+UseG1GC" \\  
> --conf "spark.hadoop.mapreduce.fileoutputcommitter.algorithm.version=2" \\  
> --conf "spark.driver.extraJavaOptions=-Dfile.encoding=utf-8" \\  
> --conf "spark.executor.extraJavaOptions=-Dfile.encoding=utf-8" \\  
> --conf "spark.executor.extraJavaOptions=-Dlog4j.debug=true" \\  
> --conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.properties" \\  
> --class com.experian.edf.oxygen.acceptance.app.AcceptanceApp \\  
> s3://oxygen-innersource-test/demo-acceptance/lib/oxygen-acceptance-innersource-1.0-jar-with-dependencies.jar \\  
> env.conf \\  
> acceptance-application.conf

Substitute this command line for the one in your Oxygen Pipeline DAGS (e.g. Airflow) specifically for the
Acceptance stage.

[Return to Top](#pagetop)
