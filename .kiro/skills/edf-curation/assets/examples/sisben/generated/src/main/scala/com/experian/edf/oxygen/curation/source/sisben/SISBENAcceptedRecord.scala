// CUR-TPL-05 · parametrizada · generado por el skill edf-curation-stage v0.1.0
// Lector propio de validatedRecordJson (CUR-PT04: se asume lector propio, no la librería de
// Validation). Acceso seguro: nunca lanza; un JSON ilegible devuelve Left (CUR-RC03).
// ASSUMPTION: C-CUR-07 — las claves de validatedRecordJson son los nombres de dominio del layout de 22
// (ID_FICHA_ORIGEN...), sin distinguir mayúsculas.
package com.experian.edf.oxygen.curation.source.sisben

import java.util.Locale
import scala.collection.JavaConverters._
import scala.util.Try
import com.google.gson.{JsonElement, JsonParser}
import com.experian.edf.oxygen.curation.source.common.Motivos

final class SISBENAcceptedRecord private (campos: Map[String, String]) extends Serializable {

  /** Valor recortado; None si falta, es null o queda vacío. */
  def get(campo: String): Option[String] =
    campos.get(campo.toUpperCase(Locale.ROOT)).map(_.trim).filter(_.nonEmpty)

  def camposPresentes: Set[String] = campos.keySet
}

object SISBENAcceptedRecord {

  /** Campos de entrada esperados (IN-03). */
  val CamposEntrada: Seq[String] = Seq("ID_FICHA_ORIGEN", "ID_HOGAR", "COD_DPTO", "NOM_DPTO", "COD_MPIO", "NOM_MPIO", "ID_CLASE", "ID_PERSONA_ORDEN", "ID_NACIONAL", "PRI_APELLIDO", "SEG_APELLIDO", "PRI_NOMBRE", "SEG_NOMBRE", "TIP_DOCUMENTO", "NUM_DOCUMENTO", "ID_GRUPO", "ID_NIVEL", "FECHA_DIGITACION", "FECHA_ACTUALIZACION_PERSONA", "ID_MARCA", "ID_ESTADO", "FECHA_ACTUALIZACION_CNS")

  val vacio: SISBENAcceptedRecord = new SISBENAcceptedRecord(Map.empty)

  def parse(json: String): Either[String, SISBENAcceptedRecord] =
    // SILENCIO-INTENCIONAL CUR-RC03: un JSON ilegible no lanza; el registro sale con registro:REGISTRO_ILEGIBLE.
    Try(new JsonParser().parse(json)).toOption
      .filter(e => e != null && e.isJsonObject)
      .map(_.getAsJsonObject) match {
      case None => Left(Motivos.RegistroIlegible)
      case Some(obj) =>
        val campos = obj.entrySet.asScala.collect {
          case e if e.getValue != null && !e.getValue.isJsonNull =>
            e.getKey.trim.toUpperCase(Locale.ROOT) -> comoTexto(e.getValue)
        }.toMap
        Right(new SISBENAcceptedRecord(campos))
    }

  /**
   * Números sin ceros finales (1.0 → "1", 1.5e3 → "1500"); booleanos "true"/"false"; arreglos y objetos como
   * JSON compacto de Gson. La referencia Python (_parse_registro) da el mismo texto (AUD-C5).
   */
  private def comoTexto(v: JsonElement): String =
    if (v.isJsonPrimitive && v.getAsJsonPrimitive.isNumber)
      v.getAsJsonPrimitive.getAsBigDecimal.stripTrailingZeros.toPlainString
    else if (v.isJsonPrimitive) v.getAsString
    else v.toString
}
