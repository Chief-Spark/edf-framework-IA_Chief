// VAL-TPL-05 · plantilla parametrizada.
// Generado por el skill edf-validation-stage desde source-spec (sisben-iv-1.0.0). No editar a mano.
// Guard-rails de archivo de SISBEN extraídos del nombre. Nunca lanza si el nombre no coincide (VAL-RC03):
// el diagnóstico del nombre lo hace validateFilename; aquí solo se usan valores de respaldo.
package com.experian.edf.oxygen.validation.source.sisben.validator

import java.time.ZoneId

import com.experian.edf.oxygen.validation.source.common.RecordValidator
import com.experian.edf.oxygen.validation.source.sisben.config.SISBENSpec
import com.experian.edf.oxygen.domain.internal.FileMetadata

// ASSUMPTION VAL-PT04: los miembros que se sobrescriben son los que usa el ejemplo del framework
// (headerRowCount, dataSource, subscriber, receivedDate, fileAsOfDate, fileTag); la clase base no está
// disponible para confirmar cuáles son abstractos.
class SISBENFileMetadata(fileName: String, headers: Array[String]) extends FileMetadata(fileName, headers) {

  override def headerRowCount: Int = 1

  override val dataSource: String = "DNP"

  override val subscriber: String = "999722"

  /** Fecha de recepción (marca de tiempo del nombre con su offset) en epoch ms; 0L si el nombre no coincide. */
  override val receivedDate: Long = RecordValidator.parsearNombre(SISBENSpec.Spec, fileName)
    .map(i => i.recibido.atOffset(i.offset).toInstant.toEpochMilli)
    .getOrElse(0L)

  /** ASSUMPTION VAL-SP07: fecha de corte = último día del periodo AAAAMM (inferida del nombre), en epoch ms; 0L si no coincide. */
  override val fileAsOfDate: Long = RecordValidator.parsearNombre(SISBENSpec.Spec, fileName)
    .map(i => i.periodo.atEndOfMonth.atStartOfDay(ZoneId.of("America/Bogota")).toInstant.toEpochMilli)
    .getOrElse(0L)

  /** ASSUMPTION F-07: tipo de archivo según el prefijo del nombre. */
  override val fileTag: String = RecordValidator.parsearNombre(SISBENSpec.Spec, fileName)
    .map(_.fileTag)
    .getOrElse("")
}
