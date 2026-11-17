package com.experian.edf.oxygen.acceptance.engines.context

import com.experian.edf.oxygen.configuration.configs.typesafe.TypesafeOxygenConfig
import com.experian.edf.oxygen.configuration.configs.{OxygenConfig, OxygenConfigurationFactory}
import com.experian.edf.oxygen.context.{AppContext, OxygenAppContext}
import com.experian.edf.oxygen.domain.{HeterogeneousCollection, PinnedEventWithMap}
import com.experian.edf.oxygen.domain.internal.{ContextPinMapEvent, Identity, StampedIdentity, SyntheticEvent, TaggedConsistencyInfo, TaggedCuratedEvent, TaggedCuratedEventWithMap, TaggedIdentity}
import com.experian.edf.oxygen.domain.pinning.PinnedTaggedIdentity
import com.experian.edf.oxygen.global.domain.statecalc
import com.experian.edf.oxygen.stage.statecalculation.{FoundationModel, XpmModel}
import com.typesafe.config.ConfigFactory
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}

import java.util.concurrent.atomic.AtomicReference

/**
  * Created by c20286a on 7/24/2017
  * This class extends from the main OxygenAppContext. Additionally it reads all properties which have
  * prefix "spark." and loads those into the Java environment variables. This ensures that whenever this context is
  * used to create a Spark based application, it will automatically load all spark properties which have beed
  * defined in properties files, xml, json or any other config information into the system. Spark looks for Java system
  * properties with the "spark." prefix to use to initialize the SparkConf and SparkContext.
  */
class SparkOxygenAppContext(configLocations: Array[String], myParent: AppContext = null) extends OxygenAppContext(configLocations, myParent) {

  //  @transient private[this] var _sc: SparkContext = null
  //  @transient private[this] var _sparkSession: SparkSession = null

  @transient private[this] val SPARK_OXYGEN_APP_CONTEXT_LOCK = new Object()

  private[this] val activeSparkSession: AtomicReference[SparkSession] = new AtomicReference[SparkSession](null)

  /**
   * This method loads configurations which are specific to Apache Spark into Java System properties, so that
   * they are accessible in the SparkConf and Spark Context from within the
   *
   * @return OxygenConfig
   */
  override def loadConfiguration(): OxygenConfig = {

    // Load all the properties which you see with spark. prefix into the Java system properties
    val localConfig = super.loadConfiguration()

    // Load the default spark configuration items. If the overridden properties do not contain these properties,
    // then they will exist in the System properties for Spark to use.

    // jsonbased properties
    val jsonConf = new OxygenConfigurationFactory()
    val sparkConfig = ConfigFactory.parseResources("spark-default.conf")
    logger.info("Spark app context Done")
    val jsonConfig = jsonConf.loadTypesafeConfig(sparkConfig)

    // First load all the default spark properties from the spark-default.conf file
    for (prop <- jsonConfig.spark) {
      System.getProperties.setProperty(prop._1, prop._2)
    }

    // If there are newer and later settings passed in from the invoking application, overwrite the older configs with
    // these ones
    for (prop <- localConfig.asInstanceOf[TypesafeOxygenConfig].spark) {
      System.getProperties.setProperty(prop._1, prop._2)
    }

    // Add all the properties from the default spark config into the localConfig
    localConfig
  }


  /**
   * Its important that this creation is "lazy" and "@transient". We need the configuration parameters to be loaded up before we
   * instantiate the spark context and configuration. The spark context and configuration are fields in
   * SparkOxygenAppContext and need not be part of the base class OxygenAppContext. Other application
   * contexts which derive from that class have no business being forced to use spark.
   * We also dont want to instantiate the Spark context multiple times in one application lifecycle, eg. invoking
   * two services (acceptor and curation) in one lifecycle. Encapsulating the SparkContext in one application context
   * works nicely, since the same application context can be passed to multiple services. The Spark context will be
   * initialized and invoked only once on creation of the SparkOxygenAppContext.
   * "@transient" because the SparkContext CANNOT be serialized, which of course is by design
   */
  @transient lazy val sc: SparkContext = {
    // All the Spark properties are already loaded up as System.properties in the loadConfiguration(), so all the
    // properties will be availabel to the SparkConf and SparkContext during this creation.

    initSparkSession.sparkContext
  }

  @transient lazy val sparkSession: SparkSession = {
    initSparkSession
  }

  private[this] def initSparkSession: SparkSession = {

    SPARK_OXYGEN_APP_CONTEXT_LOCK.synchronized {
      if (activeSparkSession.get() == null) {
        val sparkConf = new SparkConf()
          .set("spark.sql.warehouse.dir", "./spark-warehouse")
          .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
          .registerKryoClasses(Array(
            classOf[TaggedConsistencyInfo],
            classOf[PinnedEventWithMap],
            classOf[TaggedCuratedEventWithMap],
            classOf[SyntheticEvent],
            classOf[PinnedTaggedIdentity],
            classOf[Identity],
            classOf[TaggedIdentity],
            classOf[TaggedCuratedEvent],
            classOf[ContextPinMapEvent],
            classOf[StampedIdentity],
            classOf[HeterogeneousCollection],
            classOf[SyntheticEvent],
            classOf[statecalc.AsOfPeriod],
            classOf[statecalc.SyntheticEvents],
            classOf[statecalc.SyntheticEvent],
            classOf[XpmModel],
            classOf[FoundationModel], // =====
            classOf[Map[String, Any]]))

        val _sparkSession = SparkSession.builder().appName("OxygenSparkSession").config(sparkConf).getOrCreate()
        activeSparkSession.set(_sparkSession)
      }
      else {
        logger.warn("Using an existing SparkSession instance; some configuration may not take effect")
      }
      activeSparkSession.get
    }

  }

  /**
   * Start method for the application lifecycle
   *
   * @return
   */
  override def start: Any = {}

  /**
   * Stop method for the application lifecycle
   *
   * @return
   */
  override def stop: Any = {
    if (sc != null) {
      logger.info("Stopping Spark context")
      sc.stop
    }

  }

}
