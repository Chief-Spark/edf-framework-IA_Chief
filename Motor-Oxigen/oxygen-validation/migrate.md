# Migration from the Oxygen Pipeline Validation Stage<a name="pagetop"></a>

1. [Confirming the Oxygen Version](#version)
2. [Transferring the Extension Code](#extension)
3. [Adjusting Configurations](#config)
4. [Building and Using the InnerSource Validation JAR](#use)

---
## 1. Confirming the Oxygen Version <a name="version"></a>
Migrating an Oxygen Pipeline Validation stage to InnerSource Validation becomes straightforward when their dependencies align, allowing Validation to function within the same environment as the other stages, either in an on-premises or cloud computing cluster.

InnerSource Validation operates exclusively on Java 11, Scala 2.12, and Spark 3.3. Hence, before proceeding with the migration, ensure that the version of the Oxygen Pipeline currently in use is compatible and shares the same dependencies. 

For more information on dependency requirements, please refer to:
> Prerequisites 
> - JDK 11
> - Scala 2.12.15
> - Spark 3.3.1
> - Hadoop 3
>
> [AWS EMR Environment](example/images/emr_applications.png)

## 2. Transferring the Extension Code <a name="extension"></a>
Transfer the custom processor classes, like the *CreditEventValidationProcessor.scala* used in the "Get Started" example, into the InnerSource Validation source code, preferably under the designated directory for extensions:
> src/main/scala/com/experian/edf/oxygen/validation/extension

Modify its package name and import class paths as needed. For instance, the package name of *CreditEventValidationProcessor.scala* would change to:
> package com.experian.edf.oxygen.validation.extension

## 3. Adjusting Configurations <a name="config"></a>
To configure validation you can use the oxygen default config or create your own configuration.
Into this stage we have a pre-run call Discovery. This is enable by default but can be disable if it's no need it.

```dtd
oxygen {
    stage {
        validation {
            discover = true //false
        }
    }
}
```
For the validation stage the default path will be used by the following configuration.

```dtd
oxygen  
  features {
     enable{
        defaultOutputStructure = true
     }
  }
}
```

We have more configuration for this stage. Here's an example how to configure custom processor for this stage.
```dtd
configs = [
    {
      fullScanSchema = false
      filePattern = ".*-.*\\..+\\.T" //This the pattern of the file names
      dupKeyCheckEnabled = true //Check duplicated keys
      processor = "com.experian.edf.oxygen.validation.extension.CreditEventValidationProcessor" //This is the custom processor that we will help us with te validation process
      searchAllHeaderFooter = true
      historicAttributeArgs = "{\"size\":1,\"status\":\"status:SUCCESS\"}"
      validate = [
        {
          filePattern = ".*A-.*\\..+\\.T" //Patern name of the file
          fileName = "(errorcodeNotIn(\"F1003\"))" //Check no code errors in the file name
          header = "((errorcodeNotIn(\"H1001\")) or count(errors) > 2)" //Check no code errors in the headers
          footer = "(errorcodeNotIn(\"F1003\"))" // Check no code error in the footer
          contexual {
            qualification = "errorcodeNotIn(\"1004\") and errorcodeIn(\"1001\") and (0.3*qs1 + 0.6*qs2) > 3 and (errorcodeIn(\"1000\")) and count(errors)>2" // Check no errors in the content
            enabled = true
            version = "v3"
          }
        }
      }
    }
]
```
You can check this file ```CreditEventValidationProcessor``` in the code and see how to implement a CustomValidationProcessor.

## 4. Building and Using the InnerSource Validation JAR <a name="use"></a>
Build the InnerSource Validation JAR:
> mvn -DskipTests clean package

Prepare the command line to run the InnerSource Validation JAR with your configurations.
- Example command line to run locally
- You can use the build_docker.sh and run_docker.sh to create a local virtual machine with all the dependencies to run by using notebooks or execute the following command:

> spark-submit --master local --files example/configs/env.conf,example/configs/validation-application.conf --class com.experian.edf.oxygen.validation.app.SparkValidationApp target/oxygen-validation-innersource-1.0-jar-with-dependencies.jar configs/env.conf configs/validation-application.conf


- Example command line to run on AWS
> spark-submit --verbose \\  
> --master yarn \\  
> --deploy-mode cluster \\  
> --executor-cores 4 \\  
> --executor-memory 8G \\  
> --num-executors 40 \\  
> --driver-memory 8G \\  
> --conf "spark.yarn.submit.waitAppCompletion=true" \\  
> --files $(echo s3://bucket-name/validation/conf//\*.\* | tr ' ' ',') \\  
> --jars "s3://bucket-name/validation/lib/\*.jar" \\  
> --conf "spark.dynamicAllocation.enabled=false" \\  
> --conf "spark.driver.extraClassPath=/usr/lib/hadoop-lzo/lib/\*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-> sdk/\*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/\*:/usr/share/aws/emr/emrfs/auxlib/\*:/usr/share/aws/emr/goodies/lib/emr-spark-goodies.jar:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/\*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar" \\  
> --conf "spark.executor.extraClassPath=/usr/lib/hadoop-lzo/lib/\*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-sdk/\*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/\*:/usr/share/aws/emr/emrfs/auxlib/\*:/usr/share/aws/emr/goodies/lib/emr-spark-goodies.jar:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/\*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar" \\  
> --conf "spark.executor.extraJavaOptions=-XX:+UseG1GC" \\  
> --conf "spark.hadoop.mapreduce.fileoutputcommitter.algorithm.version=2" \\  
> --conf "spark.driver.extraJavaOptions=-Dfile.encoding=utf-8" \\  
> --conf "spark.executor.extraJavaOptions=-Dfile.encoding=utf-8" \\  
> --conf "spark.executor.extraJavaOptions=-Dlog4j.debug=true" \\  
> --conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.properties" \\  
> --class com.experian.edf.oxygen.validation.app.SparkValidationApp \\  
> s3://bucket-name/validation/lib/oxygen-validation-innersource-1.0-jar-with-dependencies.jar \\  
> env.conf \\  
> validation-application.conf

You must change the S3 paths where you have the jar and the config files.
Substitute this command line for the one in your Oxygen Pipeline DAGS (e.g. Airflow) specifically for the Validation stage.

[Return to Top](#pagetop)
