// VAL-TPL-05 · plantilla parametrizada.
// Generado por el skill edf-validation-stage desde source-spec ({{spec_version}}). No editar a mano.
// Guard-rails de archivo de {{Fuente}} extraídos del nombre. Nunca lanza si el nombre no coincide (VAL-RC03):
// el diagnóstico del nombre lo hace validateFilename; aquí solo se usan valores de respaldo.
package {{paquete_fuente}}.validator

import java.time.ZoneId

import {{paquete_comun}}.RecordValidator
import {{paquete_fuente}}.config.{{Fuente}}Spec
import com.experian.edf.oxygen.domain.internal.FileMetadata{{imports_metadata}}

// ASSUMPTION VAL-PT04: los miembros que se sobrescriben son los que usa el ejemplo del framework
// (headerRowCount, dataSource, subscriber, receivedDate, fileAsOfDate, fileTag); la clase base no está
// disponible para confirmar cuáles son abstractos.
class {{Fuente}}FileMetadata(fileName: String, headers: Array[String]) extends FileMetadata(fileName, headers) {

  override def headerRowCount: Int = {{filas_header}}

  override val dataSource: String = "{{datasource}}"

  override val subscriber: String = "{{subscriber}}"

  /** Fecha de recepción ({{received_date_desc}}) en epoch ms; 0L si el nombre no coincide. */
  override val receivedDate: Long = RecordValidator.parsearNombre({{Fuente}}Spec.Spec, fileName)
    .map(i => i.recibido.atOffset(i.offset).toInstant.toEpochMilli)
    .getOrElse(0L)

  /** ASSUMPTION VAL-SP07: fecha de corte = {{file_as_of_date_desc}} (inferida del nombre), en epoch ms; 0L si no coincide. */
  override val fileAsOfDate: Long = RecordValidator.parsearNombre({{Fuente}}Spec.Spec, fileName)
    .map(i => {{expr_file_as_of_date}}.atStartOfDay(ZoneId.of("{{zona_horaria}}")).toInstant.toEpochMilli)
    .getOrElse(0L)

  /** ASSUMPTION F-07: tipo de archivo según el prefijo del nombre. */
  override val fileTag: String = RecordValidator.parsearNombre({{Fuente}}Spec.Spec, fileName)
    .map(_.fileTag)
    .getOrElse(""){{bloque_metadata_adicional}}
}
