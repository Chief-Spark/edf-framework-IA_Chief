// CUR-TPL-04 · parametrizada · generado por el skill edf-curation-stage v0.1.0
// Evento curado de SISBEN (CUR-P15). Las claves del JSON de write son los nombres publicados
// y coinciden exactamente con el domain dictionary (CUR-RC05, CUR-T05).
// Campos con severidad de no almacenamiento (RF-CUR-10) se omiten del JSON; los nulos se
// escriben como null (ASSUMPTION: C-CUR-05), salvo las claves de identidad de
// parametros.omitir_si_nulas (por defecto standardizedId*), que se omiten si son nulas (AUD-C4).
package com.experian.edf.oxygen.curation.source.sisben

import com.experian.edf.oxygen.domain.internal.CuratedEvent
import com.google.gson.GsonBuilder

/** Campos curados, generados desde SISBENCurationSpec.Mapeos. */
final case class SISBENCuratedRecord(
  idFichaOrigen: Option[String],
  idHogar: Option[Int],
  codDepartamentoSisben: Option[String],
  nomDepartamento: Option[String],
  codMunicipioSisben: Option[String],
  nomMunicipio: Option[String],
  idClase: Option[Int],
  idPersonaOrden: Option[Int],
  idNacional: Option[Long],
  primerApellido: Option[String],
  segundoApellido: Option[String],
  primerNombre: Option[String],
  segundoNombre: Option[String],
  tipoDocumento: Option[Int],
  tipoDocumentoSisben: Option[String],
  numeroDocumento: Option[String],
  grupoSisben: Option[String],
  nivelSisben: Option[Int],
  fechaDigitacion: Option[Long],
  fechaActualizacionPersona: Option[Long],
  idMarca: Option[Int],
  idEstado: Option[Int],
  fechaActualizacionCns: Option[Long],
  fechaCorte: Option[Long],
  tipoArchivo: Option[String],
  currentPartyType: Option[Int],
  currentPartyIdNumber: Option[String],
  subscriberBlockStatus: Option[String],
  subscriberBlockReason: Option[String],
  advertenciasCuracion: Seq[String],
  omitidos: Set[String] = Set.empty
) {

  /** Mapa ordenado (orden de la especificación) listo para serializar. */
  def toJavaMap: java.util.LinkedHashMap[String, Object] = {
    val m = new java.util.LinkedHashMap[String, Object]()
    if (!omitidos("idFichaOrigen")) m.put("idFichaOrigen", idFichaOrigen.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("idHogar")) m.put("idHogar", idHogar.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("codDepartamentoSisben")) m.put("codDepartamentoSisben", codDepartamentoSisben.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("nomDepartamento")) m.put("nomDepartamento", nomDepartamento.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("codMunicipioSisben")) m.put("codMunicipioSisben", codMunicipioSisben.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("nomMunicipio")) m.put("nomMunicipio", nomMunicipio.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("idClase")) m.put("idClase", idClase.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("idPersonaOrden")) m.put("idPersonaOrden", idPersonaOrden.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("idNacional")) m.put("idNacional", idNacional.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("primerApellido")) m.put("primerApellido", primerApellido.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("segundoApellido")) m.put("segundoApellido", segundoApellido.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("primerNombre")) m.put("primerNombre", primerNombre.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("segundoNombre")) m.put("segundoNombre", segundoNombre.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("tipoDocumento")) m.put("tipoDocumento", tipoDocumento.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("tipoDocumentoSisben")) m.put("tipoDocumentoSisben", tipoDocumentoSisben.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("numeroDocumento")) m.put("numeroDocumento", numeroDocumento.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("grupoSisben")) m.put("grupoSisben", grupoSisben.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("nivelSisben")) m.put("nivelSisben", nivelSisben.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("fechaDigitacion")) m.put("fechaDigitacion", fechaDigitacion.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("fechaActualizacionPersona")) m.put("fechaActualizacionPersona", fechaActualizacionPersona.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("idMarca")) m.put("idMarca", idMarca.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("idEstado")) m.put("idEstado", idEstado.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("fechaActualizacionCns")) m.put("fechaActualizacionCns", fechaActualizacionCns.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("fechaCorte")) m.put("fechaCorte", fechaCorte.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("tipoArchivo")) m.put("tipoArchivo", tipoArchivo.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("currentPartyType")) m.put("currentPartyType", currentPartyType.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("currentPartyIdNumber")) m.put("currentPartyIdNumber", currentPartyIdNumber.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("subscriberBlockStatus")) m.put("subscriberBlockStatus", subscriberBlockStatus.map(_.asInstanceOf[AnyRef]).orNull)
    if (!omitidos("subscriberBlockReason")) m.put("subscriberBlockReason", subscriberBlockReason.map(_.asInstanceOf[AnyRef]).orNull)
    m.put("advertenciasCuracion", java.util.Arrays.asList(advertenciasCuracion: _*))
    m
  }
}

object SISBENCuratedRecord {

  /** Construye el registro desde los valores calculados campo a campo (tipos ya convertidos). */
  def fromMap(valores: Map[String, Option[Any]], advertencias: Seq[String], omitidos: Set[String]): SISBENCuratedRecord =
    SISBENCuratedRecord(
      idFichaOrigen = valores.get("idFichaOrigen").flatten.collect { case v: String => v },
      idHogar = valores.get("idHogar").flatten.collect { case v: Int => v },
      codDepartamentoSisben = valores.get("codDepartamentoSisben").flatten.collect { case v: String => v },
      nomDepartamento = valores.get("nomDepartamento").flatten.collect { case v: String => v },
      codMunicipioSisben = valores.get("codMunicipioSisben").flatten.collect { case v: String => v },
      nomMunicipio = valores.get("nomMunicipio").flatten.collect { case v: String => v },
      idClase = valores.get("idClase").flatten.collect { case v: Int => v },
      idPersonaOrden = valores.get("idPersonaOrden").flatten.collect { case v: Int => v },
      idNacional = valores.get("idNacional").flatten.collect { case v: Long => v },
      primerApellido = valores.get("primerApellido").flatten.collect { case v: String => v },
      segundoApellido = valores.get("segundoApellido").flatten.collect { case v: String => v },
      primerNombre = valores.get("primerNombre").flatten.collect { case v: String => v },
      segundoNombre = valores.get("segundoNombre").flatten.collect { case v: String => v },
      tipoDocumento = valores.get("tipoDocumento").flatten.collect { case v: Int => v },
      tipoDocumentoSisben = valores.get("tipoDocumentoSisben").flatten.collect { case v: String => v },
      numeroDocumento = valores.get("numeroDocumento").flatten.collect { case v: String => v },
      grupoSisben = valores.get("grupoSisben").flatten.collect { case v: String => v },
      nivelSisben = valores.get("nivelSisben").flatten.collect { case v: Int => v },
      fechaDigitacion = valores.get("fechaDigitacion").flatten.collect { case v: Long => v },
      fechaActualizacionPersona = valores.get("fechaActualizacionPersona").flatten.collect { case v: Long => v },
      idMarca = valores.get("idMarca").flatten.collect { case v: Int => v },
      idEstado = valores.get("idEstado").flatten.collect { case v: Int => v },
      fechaActualizacionCns = valores.get("fechaActualizacionCns").flatten.collect { case v: Long => v },
      fechaCorte = valores.get("fechaCorte").flatten.collect { case v: Long => v },
      tipoArchivo = valores.get("tipoArchivo").flatten.collect { case v: String => v },
      currentPartyType = valores.get("currentPartyType").flatten.collect { case v: Int => v },
      currentPartyIdNumber = valores.get("currentPartyIdNumber").flatten.collect { case v: String => v },
      subscriberBlockStatus = valores.get("subscriberBlockStatus").flatten.collect { case v: String => v },
      subscriberBlockReason = valores.get("subscriberBlockReason").flatten.collect { case v: String => v },
      advertenciasCuracion = advertencias,
      omitidos = omitidos
    )

  /** Registro sin valores (solo advertencias): salida de último recurso, nunca una excepción. */
  def vacio(advertencias: Seq[String]): SISBENCuratedRecord = fromMap(Map.empty, advertencias, Set.empty)
}

/** Evento que el motor serializa con write y publica como Map[String, Any]. */
class SISBENCuratedEvent(curatedRecord: String) extends CuratedEvent(curatedRecord) {
  override def read(curatedRecord: String): CuratedEvent = new SISBENCuratedEvent(curatedRecord)
  override def write: String = curatedRecord
}

object SISBENCuratedEvent {
  private lazy val gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create()

  def toJson(record: SISBENCuratedRecord): String = gson.toJson(record.toJavaMap)

  def apply(record: SISBENCuratedRecord): SISBENCuratedEvent = new SISBENCuratedEvent(toJson(record))
}
