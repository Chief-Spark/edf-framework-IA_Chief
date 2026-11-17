package com.experian.edf.oxygen.pinning.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.{Constants, OxygenGenerationOrchestratedService}
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import com.experian.edf.oxygen.utils.JsonUtils
import org.slf4j.LoggerFactory.getLogger

import java.io.File

/**
 * Created by c15661a on 7/25/17.
 */

object Pinner {

  // By default, we create a DataCurator which uses Spark
  def create[T <: OxygenAppContext](appContext: T): Pinner = {

    val config = appContext.config
    appContext match {
      case context: SparkOxygenAppContext =>
        /*if(config.configMap("oxygen.application.lambda").equalsIgnoreCase("stream")) new SparkHighFrequencyStateCalculator(appContext.asInstanceOf[SparkOxygenAppContext])
        else*/ if (config.configMap("oxygen.application.lambda").equalsIgnoreCase("batch")) new SparkPinner(appContext.asInstanceOf[SparkOxygenAppContext])
      else throw new UnsupportedOperationException("only oxygen.application.lambda = stream / batch are supported!")
      case _ => throw new OxygenException("Currently supported context is SparkOxygenAppContext")
    }
  }
}

abstract class Pinner(appContext: OxygenAppContext) extends OxygenGenerationOrchestratedService(appContext) {

  @transient lazy val logger = getLogger(this.getClass)
  @transient lazy val puLogger = getLogger(Constants.PROCESS_UPDATES_LOGGER)

  @throws(classOf[OxygenException])
  def run: Unit

  def convertPinnedOutputToPreserveSchema(pinnedLocation: String): Unit = {}

  // private[oxygen] def performPinningWithNewCuratedRecords(newTaggedCuratedRecords: RDD[String]): RDD[(Long, TaggedCuratedEvent)]

  private[oxygen] def archivePinDir(timestamp: String): String = {
    val pinnedDir = oxygenConf.oxygen.PINNED_DIRECTORY
    val archiveDir = oxygenConf.oxygen.PINNING_ARCHIVED_DIRECTORY + "/archived-on-" + timestamp
    fileUtils.moveFile(pinnedDir, archiveDir)
    fileUtils.touch(pinnedDir, true)
    archiveDir
  }

  /**
   * @return returns the curated files containing events to be pinned.
   */
  private def retrieveFilesToPin: List[String] = {
    // Read all files from curated folder
    val curatedFilesFolder = oxygenConf.oxygen.stage.curation.get.paths.curatedDir
    val filesToPin = fileUtils.listFilenames(curatedFilesFolder, true)
      .filter(eachFile => !eachFile.matches(".*\\.alreadycurated.*") && !eachFile.matches(".*\\.currentlycurating.*"))
    filesToPin
  }

  private[oxygen] def retrieveKeepTogetherFiles: List[String] = {
    val ktFilesFolder = oxygenConf.oxygen.KEEP_TOGETHER_DIRECTORY
    val ktFiles = fileUtils.listFilenames(ktFilesFolder, true)
    ktFiles
  }

  private[oxygen] def retrieveKeepApartFiles: List[String] = {
    val kaFilesFolder = oxygenConf.oxygen.KEEP_APART_DIRECTORY
    val kaFiles = fileUtils.listFilenames(kaFilesFolder, true)
    kaFiles
  }

  def newPinFilename(timestamp: String): String = {
    val pinnedDir = oxygenConf.oxygen.PINNED_DIRECTORY
    pinnedDir + File.separator + timestamp + "-pin"
  }

  def hcFilename(timestamp: String): String = {
    val pinnedDir = oxygenConf.oxygen.PINNED_DIRECTORY
    pinnedDir + File.separator + timestamp + "-pin-hc"
  }

  def newContextPinMapFilename(pinFilename: String): String = {
    pinFilename + "-context-pin-map"
  }

  def newIdentityPinMapFilename(pinFilename: String): String = {
    pinFilename + "-identity-pin-map"
  }

  def newMergedPinFilename(pinFilename: String): String = {
    pinFilename + "-merged-pin"
  }

  def newSplitPinFilename(pinFilename: String): String = {
    pinFilename + "-split-pin"
  }

  def newCelebrityFilename(pinFilename: String): String = {
    pinFilename + "-unpinned-celebrity-events"
  }

  def newNearNeighborFilename(pinFilename: String): String = {
    pinFilename + "-nearNeighbor"
  }

  // this replaces -identity-pin-map
  def newStampedIdentityFilename(pinFilename: String): String = {
    pinFilename + "-stampedIdentity"

  }

  def getLastGenIdentityPinMapFilename(referencePinnedDirectory: String): Option[String] = {
    val files = fileUtils.listDirectories(referencePinnedDirectory)
    val filteredFiles = files.filter(_.matches(".*-identity-pin-map$"))
    if (filteredFiles.length != 1) None
    else Some(filteredFiles.head)
  }

  private[oxygen] def currentPinFilename: String = {
    val pinDir = oxygenConf.oxygen.PINNED_DIRECTORY
    val files = fileUtils.listFilenames(pinDir).filter(_.matches(".*-pin"))

    if (files.length != 1) null
    else files(0)
  }

  private[oxygen] def currentContextPinMapFilename: String = {
    val pinDir = oxygenConf.oxygen.PINNED_DIRECTORY
    val files = fileUtils.listFilenames(pinDir).filter(_.matches(".*-context-pin-map"))

    if (files.length != 1) null
    else files(0)
  }

  private[oxygen] def readIdentifyingAttributes(filename: String): Array[String] = {
    val identifyingAttributes = JsonUtils.fromJson(scala.io.Source.fromURL(
      getClass.getClassLoader.getResource(filename))
      .mkString.replace("\n", ""), classOf[Array[String]])
    identifyingAttributes
  }

  private[oxygen] def readIdentityMigrationConfAsString(filename: String): String = {
    scala.io.Source.fromURL(getClass.getClassLoader.getResource(filename)).mkString.replace("\n", "")
  }

//  private[oxygen] def readPinIndexFileAsString(filename: String): String = {
//    scala.io.Source.fromURL(getClass.getClassLoader.getResource(filename)).mkString
//  }

  private[oxygen] def readCelebrities(filename: String): List[String] = {
    val celebrityIds = scala.io.Source.fromURL(getClass
      .getClassLoader
      .getResource(filename)).getLines.map(x => x.split(",").mkString(":"))
    celebrityIds.toList
  }
}