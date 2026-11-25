// CUR-TPL-06 · Capa C · esqueleto · generado por el skill edf-curation v0.1.0
// Processor de SISBEN que el motor (SparkCurator) carga por reflexión desde el bloque
// configs[] (CUR-P05). Compilación NO CERTIFICADA: sin JAR del framework (F-01).
//
// Contrato (CUR-RC01..06):
//  - constructor exacto (CurationProcessorContext);
//  - Serializable (se hace broadcast): logger y lookups como @transient lazy val;
//  - curateEvent nunca lanza: un valor no transformable queda nulo + advertencia (D13); un fallo
//    inesperado deja el registro vacío con registro:ERROR_INTERNO y un log sin datos (AUD-C2);
//  - lookups cargados una vez por ejecutor, sin consultas por registro;
//  - sin println, sin datos de negocio en logs;
//  - subscriberRelation se copia del evento aceptado (D3), salvo que el source-spec declare
//    extension_subscriber_relation (D14): entonces se arma con los campos curados indicados.
//    Si falta algún componente se conserva el aceptado y el registro lo advierte (AUD-C3).
package com.experian.edf.oxygen.curation.source.sisben.processor

import scala.util.Try
import scala.util.control.NonFatal
import com.experian.edf.oxygen.curation.engines.CurationProcessor
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.internal.{CuratedEvent, Payload}
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext
import com.experian.edf.oxygen.lookup.OxygenLookup
import org.slf4j.LoggerFactory.getLogger
import com.experian.edf.oxygen.curation.source.common.{FieldMapping, Motivos, Resultado, Transformacion, Transformaciones}
import com.experian.edf.oxygen.curation.source.sisben.{ SISBENAcceptedRecord, SISBENCuratedEvent, SISBENCuratedRecord }
import com.experian.edf.oxygen.curation.source.sisben.config.SISBENCurationSpec

class SISBENCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context) {

  @transient private lazy val logger = getLogger(this.getClass)

  /**
   * Homologación cargada una sola vez por ejecutor (CUR-RC04). AUD-C6: fusión, no reemplazo. El valor del
   * lookup gana; la tabla embebida completa las claves que el lookup no devolvió.
   */
  @transient private lazy val homologacion: Map[String, String] = {
    val desdeLookup = SISBENCurationProcessor.cargarHomologacion(context.lookup)
    val sinRespuesta = SISBENCurationSpec.ClavesHomologacion.count(c => !desdeLookup.contains(c))
    if (sinRespuesta > 0) {
      // ASSUMPTION CUR-PT02: si el lookup no responde se usa la tabla embebida (misma tabla de la HU).
      logger.warn("Lookup {}: {} claves sin respuesta; se completan con la tabla de homologación embebida",
        SISBENCurationSpec.TablaHomologacion, sinRespuesta.toString)
    }
    SISBENCurationSpec.Homologacion ++ desdeLookup
  }

  // outputEventType: @deprecated en CurationProcessor pero el motor lo usa. ASSUMPTION CUR-PA04; confirmar el valor inferido.
  override val outputEventType: Int = 30

  /** El default Payload.empty pierde el payload de etapas anteriores (CUR-AP08). */
  override def enrichEventWithPayload(event: TaggedValidatedEvent): Payload = event.payload

  // subscriberRelation no se sobrescribe: se copia del evento aceptado (D3).
  private def recordarCurado(event: TaggedValidatedEvent, record: SISBENCuratedRecord): Unit = ()

  override def curateEvent(event: TaggedValidatedEvent): CuratedEvent = {
    val errores = Option(event.errorCodes).map(_.toSeq).getOrElse(Seq.empty)
      .flatMap(Option(_)).map(e => (e.errorCodeValue, e.errorLevel))
    val fileName = Option(event.lineage).map(_.fileName).orNull
    val record = SISBENCurationProcessor.curar(event.validatedRecordJson, fileName, errores, homologacion,
      String.valueOf(event.eventId))
    recordarCurado(event, record)  // HF-35: subscriberRelation lo reutiliza (no se cura dos veces)
    SISBENCuratedEvent(record)
  }

  // TODO(manual) IN-06 / CUR-PT06: Business Line Code (catálogo de terceras fuentes) y Type of Data
  //   (catálogo 170) sin valores. Agregarlos en source-spec cuando se cierren; no inventarlos aquí.
  // TODO(manual) CUR-PA01 / P2: nombre completo bloqueado hasta confirmar el orden de concatenación.
}

/** Núcleo puro (sin Spark ni framework salvo los tipos del evento): se prueba sin motor. */
object SISBENCurationProcessor {

  import SISBENCurationSpec._

  @transient private lazy val logger = getLogger(classOf[SISBENCurationProcessor])

  /**
   * Consulta cada clave conocida UNA vez. ASSUMPTION CUR-PT02: API de Util.getLookup. Si el lookup falla
   * devuelve un mapa vacío: el processor avisa las claves sin respuesta y usa la tabla embebida (AUD-C6).
   */
  def cargarHomologacion(lookup: OxygenLookup): Map[String, String] =
    // SILENCIO-INTENCIONAL C-CUR-09: lookup caído → tabla embebida; el warn de claves sin respuesta lo hace visible.
    Try {
      ClavesHomologacion.flatMap { clave =>
        Option(lookup).flatMap(_.get(TablaHomologacion, Map(ColumnaClaveHomologacion -> clave)))
          .toSeq.flatten.headOption
          .flatMap(_.get(ColumnaValorHomologacion)).flatMap(v => Option(v))
          .map(v => clave -> v.toString.trim)
      }.toMap
    }.getOrElse(Map.empty)

  // subscriberRelation: sin extension_subscriber_relation en la spec (D3).
  private def advertenciaRelacion(valores: Map[String, Option[Any]]): Option[String] = None

  /**
   * Cura un registro. Nunca lanza (D13, CUR-RC03): ante un fallo inesperado devuelve un registro vacío marcado y
   * registra la clase de la excepción y el eventId, nunca valores del registro (AUD-C2, CUR-RC06).
   */
  def curar(json: String, fileName: String, errores: Seq[(String, String)], homologacion: Map[String, String],
            eventId: String = ""): SISBENCuratedRecord =
    try curarSinGuarda(json, fileName, errores, homologacion)
    catch {
      // SILENCIO-INTENCIONAL D13: red de seguridad por registro; el error queda en el log y en registro:ERROR_INTERNO.
      case NonFatal(e) =>
        logger.error("Error interno al curar el evento {} ({}); se publica vacío con advertencia", eventId, e.getClass.getName)
        SISBENCuratedRecord.vacio(Seq("registro:" + Motivos.ErrorInterno))
    }

  private def curarSinGuarda(json: String, fileName: String, errores: Seq[(String, String)], homologacion: Map[String, String]): SISBENCuratedRecord = {
    val (registro, advIniciales) = SISBENAcceptedRecord.parse(json) match {
      case Right(r) => (r, Vector.empty[String])
      case Left(_) => (SISBENAcceptedRecord.vacio, Vector("registro:" + Motivos.RegistroIlegible))
    }
    // RF-CUR-10: un nivel de NivelesNoAlmacenar omite la clave y uno de NivelesAnular la anula; los dos conjuntos
    // salen de parametros del source-spec (vacíos = el campo se publica estandarizado; HF-49). errores nulo = sin
    // errores (AUD-C5).
    val porCampo = Option(errores).getOrElse(Seq.empty).flatMap(Option(_)).flatMap { case (codigo, nivel) =>
      CodigosError.get(codigo).map(_ -> Option(nivel))
    }
    // AUD-C7: un nivel nulo conserva el campo, pero el registro lo advierte (antes quedaba implícito). HF-53: la
    // advertencia va con el campo CURADO (formato <campo curado>:<MOTIVO>), una por destino del campo de entrada.
    val advNivel = porCampo.collect { case (c, None) => c }.flatMap(c =>
      Mapeos.filter(_.origen == c).map(m => s"${m.destino}:${Motivos.NivelDesconocido}"))
    val omitirEntrada = porCampo.collect { case (c, Some(n)) if NivelesNoAlmacenar(n) => c }.toSet
    val anularEntrada = porCampo.collect { case (c, Some(n)) if NivelesAnular(n) => c }.toSet

    val (tipoArchivo, fechaCorte) = Transformaciones.infoArchivo(fileName, RegexArchivo, TipoPorPrefijo, ZonaFechas)

    val inicial = (Map.empty[String, Option[Any]], advIniciales ++ advNivel, Set.empty[String])
    val (valores, advertencias, omitidos) = Mapeos.foldLeft(inicial) { case ((acc, advs, omit), m) =>
      if (m.origen.nonEmpty && omitirEntrada(m.origen)) (acc, advs, omit + m.destino)
      else {
        // HF-34 (AUD-C-01): `original` lee el valor sin recortar; las demás transformaciones, el recortado.
        val crudo =
          if (m.origen.isEmpty || anularEntrada(m.origen)) None
          else if (m.transformacion == Transformacion.Original) registro.crudo(m.origen)
          else registro.get(m.origen)
        val r = aplicar(m, crudo, registro, acc, tipoArchivo, fechaCorte, homologacion)
        (acc + (m.destino -> r.valor), advs ++ r.advertencia.map(mot => s"${m.destino}:$mot"), omit)
      }
    }
    SISBENCuratedRecord.fromMap(valores, advertencias ++ advertenciaRelacion(valores), omitidos)
  }

  private def aplicar(m: FieldMapping, crudo: Option[String], registro: SISBENAcceptedRecord,
                      acc: Map[String, Option[Any]], tipoArchivo: Option[String], fechaCorte: Option[Long],
                      homologacion: Map[String, String]): Resultado[Any] = {
    val transformado: Resultado[Any] = m.transformacion match {
      case Transformacion.Texto => Transformaciones.texto(crudo)
      case Transformacion.Original => Transformaciones.original(crudo)
      case Transformacion.Nombre => Transformaciones.nombre(crudo, ConservarEnie)
      case Transformacion.PaddingIzquierda(n) => Transformaciones.paddingIzquierda(crudo, n)
      case Transformacion.FechaEpoch => Transformaciones.fechaEpoch(crudo, Formateadores, ZonaFechas)
      case Transformacion.Homologacion => Transformaciones.homologar(crudo, homologacion, Pendientes)
      case Transformacion.Documento => Transformaciones.documento(crudo, reglaDocumento(registro.get(CampoTipoDocumento)))
      case Transformacion.DesdeArchivo(atributo) =>
        val v: Option[Any] = if (atributo == "tipo_archivo") tipoArchivo else fechaCorte
        if (v.isEmpty) Resultado.advertir(Motivos.ArchivoNoReconocido) else Resultado(v)
      case Transformacion.CopiaDe(campo) => Resultado(acc.getOrElse(campo, None))
      case Transformacion.Concatenacion(campos, separador) =>
        Transformaciones.concatenar(campos.map(c => acc.getOrElse(c, None)), separador)
      case Transformacion.Constante(valor) => Resultado(valor)
    }
    if (transformado.advertencia.isDefined) transformado
    else Transformaciones.convertir(transformado.valor, m.tipoSalida)
  }
}
