// VAL-TPL-04 · plantilla parametrizada.
// Generado por la skill edf-validation desde source-spec (sisben-v4-0.1.0). No editar a mano.
// Evento tipado de SISBEN construido desde la línea cruda. Ningún accesor lanza excepción (VAL-P11).
package com.experian.edf.oxygen.validation.source.sisben

import com.experian.edf.oxygen.validation.source.common.Campos
import com.experian.edf.oxygen.validation.domain.ValidatedEvent
import com.google.gson.Gson

class SISBENValidatedEvent(rec: String) extends ValidatedEvent(rec) {

  @transient private lazy val gson: Gson = new Gson()

  /** split(";", -1) una sola vez; serializable (no depende de rawRecord transitorio). */
  val valores: Array[String] = Campos.separar(rec, ";")

  // Un accesor por campo, en el orden del layout, con trim (para validar). Posición fuera de rango → "".
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

  /** Mapa ordenado nombre de dominio → valor RECIBIDO, sin trim (es el validatedRecordJson que leen Acceptance y
    * Curation; Curation necesita el valor original, p. ej. un documento con espacios, AUD-V-05). */
  def comoMapa: java.util.LinkedHashMap[String, String] = {
    val m = new java.util.LinkedHashMap[String, String]()
    m.put("ID_FICHA_ORIGEN", Campos.crudo(valores, 1))
    m.put("ID_HOGAR", Campos.crudo(valores, 2))
    m.put("COD_DPTO", Campos.crudo(valores, 3))
    m.put("NOM_DPTO", Campos.crudo(valores, 4))
    m.put("COD_MPIO", Campos.crudo(valores, 5))
    m.put("NOM_MPIO", Campos.crudo(valores, 6))
    m.put("ID_CLASE", Campos.crudo(valores, 7))
    m.put("ID_PERSONA_ORDEN", Campos.crudo(valores, 8))
    m.put("ID_NACIONAL", Campos.crudo(valores, 9))
    m.put("PRI_APELLIDO", Campos.crudo(valores, 10))
    m.put("SEG_APELLIDO", Campos.crudo(valores, 11))
    m.put("PRI_NOMBRE", Campos.crudo(valores, 12))
    m.put("SEG_NOMBRE", Campos.crudo(valores, 13))
    m.put("TIP_DOCUMENTO", Campos.crudo(valores, 14))
    m.put("NUM_DOCUMENTO", Campos.crudo(valores, 15))
    m.put("ID_GRUPO", Campos.crudo(valores, 16))
    m.put("ID_NIVEL", Campos.crudo(valores, 17))
    m.put("FECHA_DIGITACION", Campos.crudo(valores, 18))
    m.put("FECHA_ACTUALIZACION_PERSONA", Campos.crudo(valores, 19))
    m.put("ID_MARCA", Campos.crudo(valores, 20))
    m.put("ID_ESTADO", Campos.crudo(valores, 21))
    m.put("FECHA_ACTUALIZACION_CNS", Campos.crudo(valores, 22))
    m
  }

  override def read(rawRecord: String): ValidatedEvent = new SISBENValidatedEvent(rawRecord)

  override def write: String = gson.toJson(comoMapa)
}
