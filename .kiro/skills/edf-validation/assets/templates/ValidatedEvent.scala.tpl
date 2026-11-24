// VAL-TPL-04 · plantilla parametrizada.
// Generado por la skill edf-validation desde source-spec ({{spec_version}}). No editar a mano.
// Evento tipado de {{Fuente}} construido desde la línea cruda. Ningún accesor lanza excepción (VAL-P11).
package {{paquete_fuente}}

import {{paquete_comun}}.Campos
import com.experian.edf.oxygen.validation.domain.ValidatedEvent
import com.google.gson.Gson

class {{Fuente}}ValidatedEvent(rec: String) extends ValidatedEvent(rec) {

  @transient private lazy val gson: Gson = new Gson()

  /** split("{{delimitador_scala}}", -1) una sola vez; serializable (no depende de rawRecord transitorio). */
  val valores: Array[String] = Campos.separar(rec, "{{delimitador_scala}}")

  // Un accesor por campo, en el orden del layout, con trim (para validar). Posición fuera de rango → "".
{{bloque_accesores}}

  /** Mapa ordenado nombre de dominio → valor RECIBIDO, sin trim (es el validatedRecordJson que leen Acceptance y
    * Curation; Curation necesita el valor original, p. ej. un documento con espacios, AUD-V-05). */
  def comoMapa: java.util.LinkedHashMap[String, String] = {
    val m = new java.util.LinkedHashMap[String, String]()
{{bloque_mapa_json}}
    m
  }

  override def read(rawRecord: String): ValidatedEvent = new {{Fuente}}ValidatedEvent(rawRecord)

  override def write: String = gson.toJson(comoMapa)
}
