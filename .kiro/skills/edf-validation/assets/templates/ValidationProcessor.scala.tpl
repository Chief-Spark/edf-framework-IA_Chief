// VAL-TPL-06 · Capa C · plantilla (esqueleto convertido en parametrizada: las reglas entre campos,
// el filtro, los duplicados y la normalización vienen como datos del source-spec).
// Generado por la skill edf-validation desde source-spec ({{spec_version}}). No editar a mano.
// Imports de la versión 2404 del framework (no mezclar con 2306).
package {{paquete_fuente}}.processor

import java.time.{Instant, LocalDate, ZoneId}

import scala.util.control.NonFatal

import {{paquete_comun}}.{Catalogo, CatalogoDeConsulta, FieldError, RecordValidator}
import {{paquete_fuente}}.{{Fuente}}ValidatedEvent
import {{paquete_fuente}}.config.{{Fuente}}Spec
import {{paquete_fuente}}.validator.{{Fuente}}FileMetadata
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.internal.{DomainData, FileMetadata}
import com.experian.edf.oxygen.domain.internal.events.QualityScore
import com.experian.edf.oxygen.domain.processors.ProcessorContext
import com.experian.edf.oxygen.exception.MalformedFileException
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.validation.context.ValidationContext
import com.experian.edf.oxygen.validation.domain.{ValidatedEvent, ValidatedEventValueObject, ValidationProcessor}
import com.experian.edf.oxygen.validation.utils.Util
import com.experian.edf.oxygen.validation.validator.{ValidationData, ValidationError, ValidationResult, ValidationSuccess}
import com.experian.edf.oxygen.validator.internal.ObjectError
import org.slf4j.LoggerFactory.getLogger

/** Catálogo sobre el lookup del framework (VAL-RC05).
  * ASSUMPTION VAL-PT03: se usa Util.getLookup del motor 2404, que devuelve "" si no hay coincidencia;
  * la columna consultada debe estar declarada en keys[].index del registro oxygen.lookups[].
  * ASSUMPTION VAL-SP09: el valor (ya con trim) se compara exacto contra el catálogo; no se normalizan
  * mayúsculas ni tildes porque no está verificado cómo lo hace el lookup real.
  * ADR-01: no envuelve la consulta en Try. Una falla del lookup se propaga y validarRegistro la convierte en el
  * código interno de nivel 1 (antes se tragaba como "valor fuera de catálogo"). La lógica está en
  * CatalogoDeConsulta (Capa A), que se prueba sin el JAR. */
final class OxygenCatalogo(lookup: () => OxygenLookup)
  extends CatalogoDeConsulta((tabla, filtro, columna) => Util.getLookup(tabla, filtro, columna, lookup()))

/** Processor de {{Fuente}}. Constructor exacto (ProcessorContext) (VAL-RC01). Serializable: el motor lo
  * difunde por broadcast; logger y catálogo son @transient lazy val (VAL-RC02). No accede a fileMetadata
  * en el constructor (VAL-RC03). Nunca lanza excepciones por registro (VAL-RC04). */
class {{Fuente}}ValidationProcessor(processorContext: ProcessorContext) extends ValidationProcessor(processorContext) {

  @transient private lazy val logger = getLogger(this.getClass)

  private val spec = {{Fuente}}Spec.Spec
  private val nombreArchivo: String = Option(processorContext.fileName).getOrElse("")
  // Con searchAllHeaderFooter = true el motor instancia el processor con Array(""): no debe fallar.
  private val headers: Array[String] = Option(processorContext.headers).getOrElse(Array.empty[String])

  @transient private lazy val catalogo: Catalogo = new OxygenCatalogo(() => lookupService)

  private val MarcaDuplicado = ObjectError(
    errorCodeName = "OxygenDuplicateError", errorCodeValue = "OxygenDuplicate", errorLevel = "", messageKey = "")

  // ASSUMPTION VAL-PA04: outputEventType = fuente.event_type del source-spec.
  override val outputEventType: Int = {{event_type}}

  private def hoy: LocalDate = LocalDate.now(ZoneId.of(spec.zonaHoraria))

  private def aObjectError(e: FieldError): ObjectError = ObjectError(
    errorCodeName = e.campo, errorCodeValue = e.codigo, errorLevel = e.nivel, messageKey = e.causa, errorType = "event")

  private def resultado(errores: Seq[FieldError]): ValidationResult =
    if (errores.isEmpty) new ValidationSuccess(new ValidationData, Array.empty[ObjectError])
    else new ValidationError(new ValidationData, errores.map(aObjectError).toArray)

  @throws(classOf[MalformedFileException])
  override def createFileMetadata: FileMetadata = new {{Fuente}}FileMetadata(nombreArchivo, headers)

  // esHeader y debeFiltrar son totales (fila nula, vacía o truncada no lanzan): sin Try, una falla no se disfraza
  // de "no es header" o de "no se filtra" (AUD-V1).
  override def isHeaderRow(row: String): Boolean = RecordValidator.esHeader(spec, row)

  override def isFooterRow(row: String): Boolean = {{is_footer_row_expr}}

  /** Filtro silencioso, fuera de todo quality score ({{filtro_descripcion}}). */
  override def shouldFilterRow(row: String): Boolean = RecordValidator.debeFiltrar(spec, row)

  /** Nombre de archivo, con las reglas de fecha de la spec respecto del reloj del sistema (AUD-V-02). */
  override def validateFilename(validationContext: ValidationContext): ValidationResult =
    resultado(RecordValidator.validarNombre(spec, nombreArchivo, Instant.now()))

  override def validateHeader(validationContext: ValidationContext): ValidationResult =
    resultado(RecordValidator.validarHeader(spec, headers.toSeq)){{bloque_validate_footer}}

  override def createValidatedEvents(rawRecord: String, validationContext: ValidationContext): Array[ValidatedEventValueObject] = {
    val linea = Option(rawRecord).getOrElse("")
    val evento = new {{Fuente}}ValidatedEvent(linea)
    val (errores, scores) = try {
      val r = RecordValidator.validarRegistro(spec, linea, catalogo, hoy)
      (r.errores, r.qualityScores)
    } catch {
      // SILENCIO-INTENCIONAL VAL-RC04: segunda barrera (validarRegistro ya no lanza, ADR-01); el registro sale con el código interno.
      case NonFatal(_) =>
        // Sin PII: solo el código. El registro sale con error severidad 1, no se pierde ni tumba el job.
        logger.warn("Error interno al validar un registro; codigo={}", spec.codigos.interno)
        val interno = Seq(FieldError("REGISTRO", spec.codigos.interno, "1", "INTERNO"))
        (interno, RecordValidator.qualityScores(spec, interno))
    }
    // Guard-rails explícitos (VAL-P13): subscriberRelation siempre presente.
    // ASSUMPTION VAL-SP02: si el campo de as-of-date es inválido (ya diagnosticado), asOfDate = None. El motor usa
    // entonces la fecha de recepción del lineage y marca didCreateAsOfDateFromReceivedDate = true
    // (Validator.createTaggedValidatedEvent). Nunca se inventa 0L (1970) sin avisar al motor (AUD-V2).
    val asOfDate: Option[Long] = RecordValidator.asOfDate(spec, linea)
    val relacion: String = spec.campo(spec.campoSubscriberRelation)
      .map(c => evento.valores.lift(c.posicion - 1).map(_.trim).getOrElse("")).getOrElse("")
    Array(new ValidatedEventValueObject(
      evento,
      Some(DomainData(asOfDate, relacion)),
      scores.map { case (n, s) => QualityScore(name = n, score = s) }.toArray,
      errores.map(aObjectError).toArray))
  }

  /** Clave de duplicado ({{dup_descripcion}}). Nunca vacía con dupKeyCheckEnabled (VAL-AP03). */
  override def keysForDuplicate(rawRecord: ValidatedEvent): Array[Any] = rawRecord match {
    case e: {{Fuente}}ValidatedEvent => RecordValidator.claveDuplicado(spec, e.valores)
    case otro                        => Array[Any]("SIN_CLAVE", System.identityHashCode(otro))
  }

  /** deDupVersion = EXTN: el motor delega aquí y NO agrega la marca; se conserva uno y se marca el resto.
    * ASSUMPTION F-14: "más reciente" = mayor asOfDate ({{dup_campo_fecha_desc}}); desempate por mayor eventId
    * (el orden de la lista no refleja el orden de las líneas). */
  override def listDuplicateEvents(events: List[TaggedValidatedEvent]): List[TaggedValidatedEvent] =
    if (events.size < 2) events
    else {
      val conservar = RecordValidator.indiceAConservar(events.map(e => (e.asOfDate, Option(e.eventId).getOrElse(""))))
      events.zipWithIndex.map {
        case (tve, i) if i == conservar => tve
        case (tve, _) =>
          // Mismos 17 argumentos con los que el motor 2404 crea el evento (Validator.createTaggedValidatedEvent):
          // se copian también inferredAsDeleted y fileTag, que el motor llena y Curation lee (AUD-V-17).
          new TaggedValidatedEvent(tve.lineage, tve.eventId, tve.validatedRecordJson, tve.payload,
            tve.qualityScores, tve.errorCodes :+ MarcaDuplicado, tve.eventType, tve.minFrequency,
            tve.asOfDate, tve.didCreateAsOfDateFromReceivedDate, tve.subscriberRelation, tve.subscriber,
            tve.headerErrors, tve.footerErrors, tve.keys, tve.inferredAsDeleted, tve.fileTag)
      }
    }
}
