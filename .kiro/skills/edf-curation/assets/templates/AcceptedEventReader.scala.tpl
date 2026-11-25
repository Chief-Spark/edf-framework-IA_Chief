// CUR-TPL-05 · parametrizada · generado por el skill edf-curation v{{version_skill}}
// Lector propio de validatedRecordJson (CUR-PT04: se asume lector propio, no la librería de
// Validation). Acceso seguro: nunca lanza; un JSON ilegible devuelve Left (CUR-RC03).
// ASSUMPTION C-CUR-07: claves de validatedRecordJson = campos_entrada, sin distinguir mayúsculas; verificar con la etapa anterior.
package {{paquete_fuente}}

import java.util.Locale
import scala.collection.JavaConverters._
import scala.util.Try
import com.google.gson.{JsonElement, JsonParser}
import {{paquete_compartido}}.Motivos

final class {{Fuente}}AcceptedRecord private (campos: Map[String, String]) extends Serializable {

  /** Valor recortado; None si falta, es null o queda vacío. */
  def get(campo: String): Option[String] =
    campos.get(campo.toUpperCase(Locale.ROOT)).map(_.trim).filter(_.nonEmpty)

  /** HF-34 (AUD-C-01): valor tal cual llegó, sin recortar; None solo si falta o es null. Lo usa `original`. */
  def crudo(campo: String): Option[String] = campos.get(campo.toUpperCase(Locale.ROOT))

  def camposPresentes: Set[String] = campos.keySet
}

object {{Fuente}}AcceptedRecord {

  /** Campos de entrada esperados (IN-03). */
  val CamposEntrada: Seq[String] = Seq({{campos_entrada_scala}})

  val vacio: {{Fuente}}AcceptedRecord = new {{Fuente}}AcceptedRecord(Map.empty)

  def parse(json: String): Either[String, {{Fuente}}AcceptedRecord] =
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
        Right(new {{Fuente}}AcceptedRecord(campos))
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
