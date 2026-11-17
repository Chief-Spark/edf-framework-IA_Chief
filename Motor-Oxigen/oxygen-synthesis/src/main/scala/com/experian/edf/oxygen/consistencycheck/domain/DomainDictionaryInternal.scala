
package com.experian.edf.oxygen.consistencycheck.domain

import java.lang.reflect.Type
import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.domain.SynthesisProcessor
import com.experian.edf.oxygen.global.schemas.SchemaElement
import com.experian.edf.oxygen.global.utils.JacksonUtil
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.gson._
import org.slf4j.LoggerFactory
import oxygen.consistencycheck.dd.{DomainDictionaryV1, DomainDictionaryV2}

import scala.io.BufferedSource

/**
 * This is the case class which holds information about fields in the domain dictionary.
 *
 * @param name        - Name of the field
 * @param handler     - The handler
 *                  - EUC : Earliest Until Corrected
 *                  - LAP : Latest
 *                  - LFP : Latest for a Period
 * @param periodicity - Is the event a periodic event (Yes / No)
 * @param description - A description field. By default "" (not mandatory)
 */
case class DictionaryFieldInternal(name: String,
                                   handler: String = DomainDictionaryInternal.LFPFieldHandler,
                                   periodicity: Boolean = true,
                                   complexTypeDefinition: SchemaElement,
                                   fieldType: String = DomainDictionaryInternal.StringType,
                                   synthesizer: Option[SynthesisProcessor] = None,
                                   description: String = "") extends Serializable {
  assert(handler == DomainDictionaryInternal.EUCFieldHandler ||
    handler == DomainDictionaryInternal.LAPFieldHandler ||
    handler == DomainDictionaryInternal.LFPFieldHandler
  )
}

case class DomainDictionaryInternal(
                                     domain: String,
                                     fields: Array[DictionaryFieldInternal],
                                     version: Int = 0
                                   ) extends Serializable {
  val fieldsMap: Map[String, DictionaryFieldInternal] = fields.map(elem => (elem.name, elem)).toMap
}

object DomainDictionaryInternal extends Serializable {
  private lazy val logger = LoggerFactory.getLogger(DomainDictionaryInternal.getClass)
  val EUCFieldHandler = "EUC"
  val LAPFieldHandler = "LAP"
  val LFPFieldHandler = "LFP"

  val StringType = "String"
  val IntType = "Int"
  val IntegerType = "Integer"
  val LongType = "Long"
  val DoubleType = "Double"
  val BooleanType = "Boolean"
  val ComplexType = "Complex"
  val PeriodType = "Period"

  def getAllEUCFields(domainDict: DomainDictionaryInternal): Array[DictionaryFieldInternal] = {
    domainDict.fields.filter(f => f.handler == EUCFieldHandler) ++
      Array(DictionaryFieldInternal("subscriberRelationStartDate", handler = EUCFieldHandler, periodicity = false, null),
        DictionaryFieldInternal("subscriberRelationEndDate", handler = EUCFieldHandler, periodicity = false, null),
        DictionaryFieldInternal("firstEventDate", handler = EUCFieldHandler, periodicity = false, null))
  }

  def getAllLAPFields(domainDict: DomainDictionaryInternal): Array[DictionaryFieldInternal] = {
    domainDict.fields.filter(f => f.handler == LAPFieldHandler)
  }

  /**
   * Method to read in a domain dictionary
   *
   * @param file
   * @return
   */
  private[oxygen] def readDomainDictionary(file: String): DomainDictionaryInternal = {
    val dict = scala.io.Source.fromURL(getClass.getClassLoader.getResource(file))
    readDomainDictionary(dict)
  }

  private[oxygen] def readDomainDictionary(sourceFile: BufferedSource): DomainDictionaryInternal = {
    val ddAsString = sourceFile.mkString.replace("\n", "")
    val ddNode: JsonNode = ScalaJacksonUtil.toJsonNode(ddAsString).get
    if (ddNode.asInstanceOf[ObjectNode].get("version") != null) {
      val ddVersion = ddNode.asInstanceOf[ObjectNode].get("version").intValue()
      val dict = (ddVersion match {
        case 1 =>
          logger.warn("Version 1 will be deprecated soon. Please migrate to latest version of Domain Dictionary: Refer https://pages.experian.com/display/ODC/Domain+Dictionary")
          JacksonUtil.fromJson(ddAsString, classOf[DomainDictionaryV1]).get.toDomainDictionary
        case 2 =>
          JacksonUtil.fromJson(ddAsString, classOf[DomainDictionaryV2]).get.toDomainDictionary
        case _ => throw new OxygenException("Unknown Domain Dictionary version. Refer https://pages.experian.com/display/ODC/OxygenException%3A+Domain+Dictionary+version+not+defined")

      })
      val inferredDeletedField = DictionaryFieldInternal("inferredAsDeleted", DomainDictionaryInternal.LAPFieldHandler, true, null, DomainDictionaryInternal.BooleanType, None, "")
      val deleteDirective = DictionaryFieldInternal("shouldDelete", DomainDictionaryInternal.LAPFieldHandler, true, null, DomainDictionaryInternal.BooleanType, None, "")
      val fileTagField = DictionaryFieldInternal("fileTag", DomainDictionaryInternal.LAPFieldHandler, true, null, DomainDictionaryInternal.StringType, None, "")
      val fields = dict.fields :+ inferredDeletedField :+ deleteDirective :+ fileTagField
      DomainDictionaryInternal(dict.domain, fields)
    } else {
      throw new OxygenException("Domain Dictionary version not defined. Refer https://pages.experian.com/display/ODC/OxygenException%3A+Domain+Dictionary+version+not+defined")
    }


  }
}

object DictionaryFieldDeser extends JsonDeserializer[DictionaryFieldInternal] {
  override def deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): DictionaryFieldInternal = {
    val handler = {
      if (json.getAsJsonObject.get("handler") == null) DomainDictionaryInternal.LFPFieldHandler
      else json.getAsJsonObject.get("handler").getAsString
    }
    val periodicity = {
      if (json.getAsJsonObject.get("periodicity") == null) true
      else json.getAsJsonObject.get("periodicity").getAsBoolean
    }
    val description = {
      if (json.getAsJsonObject.get("description") == null) ""
      else json.getAsJsonObject.get("description").getAsString
    }

    val complexTypeDefinition = {
      if (json.getAsJsonObject.get("complexTypeDefinition") == null) None
      else ScalaJacksonUtil.fromJson(json.getAsJsonObject.get("complexTypeDefinition").toString, classOf[SchemaElement])
    }
    val fieldType = {
      if (json.getAsJsonObject.get("fieldType") == null) DomainDictionaryInternal.StringType
      else json.getAsJsonObject.get("fieldType").getAsString
    }
    val synthesizer: Option[SynthesisProcessor] = {
      if (json.getAsJsonObject.get("synthesizer") == null)
        None
      else {
        val synthesizerClassName = Some(json.getAsJsonObject.get("synthesizer").getAsString)
        try {
          Some(Class.forName(synthesizerClassName.get.trim).newInstance.asInstanceOf[SynthesisProcessor])
        } catch {
          case x: ClassNotFoundException => {
            None
          }
        }
      }
    }
    DictionaryFieldInternal(
      json.getAsJsonObject.get("name").getAsString,
      handler,
      periodicity,
      complexTypeDefinition.orNull,
      fieldType,
      synthesizer,
      description
    )

  }
}
