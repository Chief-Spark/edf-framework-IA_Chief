// CUR-TPL-04 · parametrizada · generado por el skill edf-curation-stage v{{version_skill}}
// Evento curado de {{Fuente}} (CUR-P15). Las claves del JSON de write son los nombres publicados
// y coinciden exactamente con el domain dictionary (CUR-RC05, CUR-T05).
// Campos con severidad de no almacenamiento (RF-CUR-10) se omiten del JSON; los nulos se
// escriben como null (ASSUMPTION: C-CUR-05), salvo las claves de identidad de
// parametros.omitir_si_nulas (por defecto standardizedId*), que se omiten si son nulas (AUD-C4).
package {{paquete_fuente}}

import com.experian.edf.oxygen.domain.internal.CuratedEvent
import com.google.gson.GsonBuilder

/** Campos curados, generados desde {{Fuente}}CurationSpec.Mapeos. */
final case class {{Fuente}}CuratedRecord(
{{campos_record_scala}},
  {{campo_advertencias}}: Seq[String],
  omitidos: Set[String] = Set.empty
) {

  /** Mapa ordenado (orden de la especificación) listo para serializar. */
  def toJavaMap: java.util.LinkedHashMap[String, Object] = {
    val m = new java.util.LinkedHashMap[String, Object]()
{{campos_to_map_scala}}
    m.put("{{campo_advertencias}}", java.util.Arrays.asList({{campo_advertencias}}: _*))
    m
  }
}

object {{Fuente}}CuratedRecord {

  /** Construye el registro desde los valores calculados campo a campo (tipos ya convertidos). */
  def fromMap(valores: Map[String, Option[Any]], advertencias: Seq[String], omitidos: Set[String]): {{Fuente}}CuratedRecord =
    {{Fuente}}CuratedRecord(
{{campos_from_map_scala}},
      {{campo_advertencias}} = advertencias,
      omitidos = omitidos
    )

  /** Registro sin valores (solo advertencias): salida de último recurso, nunca una excepción. */
  def vacio(advertencias: Seq[String]): {{Fuente}}CuratedRecord = fromMap(Map.empty, advertencias, Set.empty)
}

/** Evento que el motor serializa con write y publica como Map[String, Any]. */
class {{Fuente}}CuratedEvent(curatedRecord: String) extends CuratedEvent(curatedRecord) {
  override def read(curatedRecord: String): CuratedEvent = new {{Fuente}}CuratedEvent(curatedRecord)
  override def write: String = curatedRecord
}

object {{Fuente}}CuratedEvent {
  private lazy val gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create()

  def toJson(record: {{Fuente}}CuratedRecord): String = gson.toJson(record.toJavaMap)

  def apply(record: {{Fuente}}CuratedRecord): {{Fuente}}CuratedEvent = new {{Fuente}}CuratedEvent(toJson(record))
}
