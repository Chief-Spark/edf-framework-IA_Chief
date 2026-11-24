// VAL-TPL-04 · plantilla parametrizada.
// Generado por el skill edf-validation-stage desde source-spec (sisben-iv-1.0.0). No editar a mano.
// Evento tipado de SISBEN construido desde la línea cruda. Ningún accesor lanza excepción (VAL-P11).
package com.experian.edf.oxygen.validation.source.sisben

import com.experian.edf.oxygen.validation.source.common.Campos
import com.experian.edf.oxygen.validation.domain.ValidatedEvent
import com.google.gson.Gson

class SISBENValidatedEvent(rec: String) extends ValidatedEvent(rec) {

  @transient private lazy val gson: Gson = new Gson()

  /** split(";", -1) una sola vez; serializable (no depende de rawRecord transitorio). */
  val valores: Array[String] = Campos.separar(rec, ";")

  // Un accesor por campo, en el orden del layout. Posición fuera de rango → "".
  val idFichaOrigen: String = Campos.valor(valores, 1)
  val idHogar: String = Campos.valor(valores, 2)
  val codDpto: String = Campos.valor(valores, 3)
  val nomDpto: String = Campos.valor(valores, 4)
  val codMpio: String = Campos.valor(valores, 5)
  val nomMpio: String = Campos.valor(valores, 6)
  val idClase: String = Campos.valor(valores, 7)
  val idPersonaOrden: String = Campos.valor(valores, 8)
  val idNacional: String = Campos.valor(valores, 9)
  val priApellido: String = Campos.valor(valores, 10)
  val segApellido: String = Campos.valor(valores, 11)
  val priNombre: String = Campos.valor(valores, 12)
  val segNombre: String = Campos.valor(valores, 13)
  val tipDocumento: String = Campos.valor(valores, 14)
  val numDocumento: String = Campos.valor(valores, 15)
  val idGrupo: String = Campos.valor(valores, 16)
  val idNivel: String = Campos.valor(valores, 17)
  val fechaDigitacion: String = Campos.valor(valores, 18)
  val fechaActualizacionPersona: String = Campos.valor(valores, 19)
  val idMarca: String = Campos.valor(valores, 20)
  val idEstado: String = Campos.valor(valores, 21)
  val fechaActualizacionCns: String = Campos.valor(valores, 22)

  /** Mapa ordenado nombre de dominio → valor (es el validatedRecordJson que leen Acceptance y Curation). */
  def comoMapa: java.util.LinkedHashMap[String, String] = {
    val m = new java.util.LinkedHashMap[String, String]()
    m.put("ID_FICHA_ORIGEN", idFichaOrigen)
    m.put("ID_HOGAR", idHogar)
    m.put("COD_DPTO", codDpto)
    m.put("NOM_DPTO", nomDpto)
    m.put("COD_MPIO", codMpio)
    m.put("NOM_MPIO", nomMpio)
    m.put("ID_CLASE", idClase)
    m.put("ID_PERSONA_ORDEN", idPersonaOrden)
    m.put("ID_NACIONAL", idNacional)
    m.put("PRI_APELLIDO", priApellido)
    m.put("SEG_APELLIDO", segApellido)
    m.put("PRI_NOMBRE", priNombre)
    m.put("SEG_NOMBRE", segNombre)
    m.put("TIP_DOCUMENTO", tipDocumento)
    m.put("NUM_DOCUMENTO", numDocumento)
    m.put("ID_GRUPO", idGrupo)
    m.put("ID_NIVEL", idNivel)
    m.put("FECHA_DIGITACION", fechaDigitacion)
    m.put("FECHA_ACTUALIZACION_PERSONA", fechaActualizacionPersona)
    m.put("ID_MARCA", idMarca)
    m.put("ID_ESTADO", idEstado)
    m.put("FECHA_ACTUALIZACION_CNS", fechaActualizacionCns)
    m
  }

  override def read(rawRecord: String): ValidatedEvent = new SISBENValidatedEvent(rawRecord)

  override def write: String = gson.toJson(comoMapa)
}
