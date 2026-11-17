package com.experian.edf.oxygen.pinning.standalone.classifier

import com.experian.edf.oxygen.domain.vle.VleMetadataFile
import com.experian.edf.oxygen.global.utils.JacksonUtil
import com.experian.edf.oxygen.utils.ScalaJacksonUtil

import java.io.File
import scala.io.{BufferedSource, Source}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 8/6/20 at 5:45 PM
 */
object ClassifierCommandParser {

  private def parseConfigFile(path: String): ClassifierConfig = {
    val source: BufferedSource = Source.fromFile(path)
    val string = source.mkString
    val commandLineConfig: ClassifierConfig = ScalaJacksonUtil.fromJson(string, classOf[ClassifierConfig]).get
    source.close
    commandLineConfig
  }

  def parseArgs(args: Array[String]): ClassifierConfig = {
    val argParser = new scopt.OptionParser[ClassifierConfig]("<command invocation>") {
      head("CelebrityPins RI - Your gateway to Larger than life Pins :)")

      opt[String]('f', "file")
        .action((x, c) => c.copy(celebrityFile = JacksonUtil.fromFile(new File(x), classOf[VleMetadataFile])))

      opt[String]('w', "workingDirectory")
        .action((x, c) => c.copy(workingDirectory = x))

      opt[String]('g', "generation")
        .action((x, c) => c.copy(generation = x))
    }

    argParser.parse(args.toSeq, ClassifierConfig(null, null)) match {
      case Some(config) => config
      case None => throw new Exception("Invalid Celebrity Id options")
    }
  }
}
