// CUR-TPL-03 · Capa B · parametrizada · generado por el skill edf-curation v0.1.0
// Especificación de mapeo de SISBEN: fuente única de verdad del esquema de salida. De aquí
// salen los campos de SISBENCuratedEvent y las entradas del domain dictionary (CUR-RC05,
// RF-CUR-13). No editar a mano: cambiar source-spec y regenerar. Ningún nombre publicado se
// renombra después de su primera publicación.
//
// Supuestos aplicados (detalle en references/decisiones-y-abiertos.md):
//   - paquete compartido: propuesta_de_frontera (CUR-PT01)
//   - reglas de documento: cerrado (RF-CUR-08)
//   - outputEventType: cerrado (CUR-PA04)
//   - convención de nombre de archivo: cerrado (D8)
package com.experian.edf.oxygen.curation.source.sisben.config

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.util.matching.Regex
import com.experian.edf.oxygen.curation.source.common.{Catalogo, FieldMapping, ReglaDocumento, TipoSalida, Transformacion, Transformaciones}

object SISBENCurationSpec {

  val VersionSkill: String = "0.1.0"

  /** Una entrada por campo publicado, en el orden en que se escriben. */
  val Mapeos: Seq[FieldMapping] = Seq(
    FieldMapping("idFichaOrigen", "ID_FICHA_ORIGEN", Transformacion.Texto, TipoSalida.Texto, None),
    FieldMapping("idHogar", "ID_HOGAR", Transformacion.Texto, TipoSalida.Texto, None),
    FieldMapping("codDepartamentoSisben", "COD_DPTO", Transformacion.Texto, TipoSalida.Texto, None),
    FieldMapping("nomDepartamento", "NOM_DPTO", Transformacion.Nombre, TipoSalida.Texto, None),
    FieldMapping("codMunicipioSisben", "COD_MPIO", Transformacion.Texto, TipoSalida.Texto, None),
    FieldMapping("nomMunicipio", "NOM_MPIO", Transformacion.Nombre, TipoSalida.Texto, None),
    FieldMapping("idClase", "ID_CLASE", Transformacion.Texto, TipoSalida.Entero, None),
    FieldMapping("idPersonaOrden", "ID_PERSONA_ORDEN", Transformacion.Texto, TipoSalida.Texto, None),
    FieldMapping("idNacional", "ID_NACIONAL", Transformacion.Texto, TipoSalida.Texto, None),
    FieldMapping("primerApellido", "PRI_APELLIDO", Transformacion.Nombre, TipoSalida.Texto, None),
    FieldMapping("segundoApellido", "SEG_APELLIDO", Transformacion.Nombre, TipoSalida.Texto, None),
    FieldMapping("primerNombre", "PRI_NOMBRE", Transformacion.Nombre, TipoSalida.Texto, None),
    FieldMapping("segundoNombre", "SEG_NOMBRE", Transformacion.Nombre, TipoSalida.Texto, None),
    FieldMapping("tipoDocumento", "TIP_DOCUMENTO", Transformacion.Homologacion, TipoSalida.Entero, None),
    FieldMapping("tipoDocumentoSisben", "TIP_DOCUMENTO", Transformacion.Original, TipoSalida.Texto, None),
    FieldMapping("numeroDocumento", "NUM_DOCUMENTO", Transformacion.Documento, TipoSalida.Texto, None),
    FieldMapping("numeroDocumentoOriginal", "NUM_DOCUMENTO", Transformacion.Original, TipoSalida.Texto, None),
    FieldMapping("grupoSisben", "ID_GRUPO", Transformacion.Texto, TipoSalida.Texto, None),
    FieldMapping("nivelSisben", "ID_NIVEL", Transformacion.Texto, TipoSalida.Entero, None),
    FieldMapping("fechaDigitacion", "FECHA_DIGITACION", Transformacion.FechaEpoch, TipoSalida.Largo, None),
    FieldMapping("fechaActualizacionPersona", "FECHA_ACTUALIZACION_PERSONA", Transformacion.FechaEpoch, TipoSalida.Largo, None),
    FieldMapping("idMarca", "ID_MARCA", Transformacion.Texto, TipoSalida.Entero, None),
    FieldMapping("idEstado", "ID_ESTADO", Transformacion.Texto, TipoSalida.Entero, None),
    FieldMapping("fechaActualizacionCns", "FECHA_ACTUALIZACION_CNS", Transformacion.FechaEpoch, TipoSalida.Largo, None),
    FieldMapping("fechaCorte", "", Transformacion.DesdeArchivo("fecha_corte"), TipoSalida.Largo, None),
    FieldMapping("tipoArchivo", "", Transformacion.Constante(Some("REGULAR")), TipoSalida.Texto, None),
    FieldMapping("standardizedIdType", "", Transformacion.CopiaDe("tipoDocumento"), TipoSalida.Entero, None),
    FieldMapping("standardizedIdNumber", "", Transformacion.CopiaDe("numeroDocumento"), TipoSalida.Texto, None),
    FieldMapping("standardizedIdName", "", Transformacion.Concatenacion(Seq("primerNombre", "segundoNombre", "primerApellido", "segundoApellido"), " "), TipoSalida.Texto, None),
    FieldMapping("currentPartyType", "", Transformacion.CopiaDe("tipoDocumento"), TipoSalida.Entero, None),
    FieldMapping("currentPartyIdNumber", "", Transformacion.CopiaDe("numeroDocumento"), TipoSalida.Texto, None),
    FieldMapping("subscriberBlockStatus", "", Transformacion.Constante(Some("NO_BLOQUEADO")), TipoSalida.Texto, None),
    FieldMapping("subscriberBlockReason", "", Transformacion.Constante(None), TipoSalida.Texto, None)
  )

  /** Campo de advertencias de curación (D13). RESUELTO CUR-PT05 (spec-sisben.md §10.1 (advertenciasCuracion)): nombre del campo. */
  val CampoAdvertencias: String = "advertenciasCuracion"

  /** Nombres publicados, en orden: deben coincidir con el domain dictionary. */
  val ClavesPublicadas: Seq[String] = Seq("idFichaOrigen", "idHogar", "codDepartamentoSisben", "nomDepartamento", "codMunicipioSisben", "nomMunicipio", "idClase", "idPersonaOrden", "idNacional", "primerApellido", "segundoApellido", "primerNombre", "segundoNombre", "tipoDocumento", "tipoDocumentoSisben", "numeroDocumento", "numeroDocumentoOriginal", "grupoSisben", "nivelSisben", "fechaDigitacion", "fechaActualizacionPersona", "idMarca", "idEstado", "fechaActualizacionCns", "fechaCorte", "tipoArchivo", "standardizedIdType", "standardizedIdNumber", "standardizedIdName", "currentPartyType", "currentPartyIdNumber", "subscriberBlockStatus", "subscriberBlockReason", "advertenciasCuracion")

  /** Claves de identidad que se omiten del evento si son nulas: ausentes, nunca null (AUD-C4, C-CUR-05). */
  val OmitirSiNulas: Set[String] = Set("standardizedIdType", "standardizedIdNumber", "standardizedIdName")

  // --- Homologación del tipo de documento (RF-CUR-07): Validation la consulta, Curation la persiste.
  val TablaHomologacion: String = "202_SISBEN_PROCESS_ID_TYPE_HO"
  val ColumnaClaveHomologacion: String = "cod_sisben"   // RESUELTO F-12 (spec-sisben.md §9 (delimitador) y §9.4 (columnas y llave del catálogo 202))
  val ColumnaValorHomologacion: String = "cod_experian"   // RESUELTO F-12 (spec-sisben.md §9 (delimitador) y §9.4 (columnas y llave del catálogo 202))
  /** Tabla embebida: respaldo si el lookup no responde y base de los tests. */
  val Homologacion: Map[String, String] = Map("1" -> "10", "2" -> "7", "3" -> "1", "4" -> "4", "5" -> "8", "6" -> "5", "8" -> "9", "PPT" -> "11")
  /** Códigos sin equivalente Experian todavía: nunca se les asigna un valor arbitrario. */
  val Pendientes: Set[String] = Set("7")
  /** Claves a consultar UNA vez en el lookup (CUR-RC04). */
  val ClavesHomologacion: Seq[String] = Seq("1", "2", "3", "4", "5", "6", "8", "PPT")

  // --- Número de documento (RF-CUR-08). RESUELTO C-CUR-08 (spec-sisben.md §10.1 (numeroDocumento sin padding ni excepción por tipo)): reglas de reglas_documento.
  val CampoTipoDocumento: String = "TIP_DOCUMENTO"
  val ReglaDocumentoPorDefecto: ReglaDocumento = ReglaDocumento(quitarCerosIzquierda = true, soloDigitos = false, conservarOriginal = false, padding = None)
  val ReglasDocumento: Map[String, ReglaDocumento] = Map(
    // Sin reglas por tipo: todos los tipos usan ReglaDocumentoPorDefecto.
  )
  def reglaDocumento(tipoOriginal: Option[String]): ReglaDocumento =
    tipoOriginal.map(_.trim.toUpperCase).flatMap(ReglasDocumento.get).getOrElse(ReglaDocumentoPorDefecto)

  // --- Limpieza por severidad (RF-CUR-10): código de Validation → campo de entrada.
  val CodigosError: Map[String, String] = Map(
    "SISBEN_INC_001" -> "ID_FICHA_ORIGEN",
    "SISBEN_INC_002" -> "ID_HOGAR",
    "SISBEN_ERR_003" -> "COD_DPTO",
    "SISBEN_ERR_004" -> "NOM_DPTO",
    "SISBEN_ERR_005" -> "COD_MPIO",
    "SISBEN_ERR_006" -> "NOM_MPIO",
    "SISBEN_ERR_007" -> "ID_CLASE",
    "SISBEN_INC_008" -> "ID_PERSONA_ORDEN",
    "SISBEN_INC_009" -> "ID_NACIONAL",
    "SISBEN_ERR_010" -> "PRI_APELLIDO",
    "SISBEN_INC_011" -> "SEG_APELLIDO",
    "SISBEN_ERR_012" -> "PRI_NOMBRE",
    "SISBEN_INC_013" -> "SEG_NOMBRE",
    "SISBEN_ERR_014" -> "TIP_DOCUMENTO",
    "SISBEN_ERR_015" -> "NUM_DOCUMENTO",
    "SISBEN_ERR_016" -> "ID_GRUPO",
    "SISBEN_ERR_017" -> "ID_NIVEL",
    "SISBEN_INC_018" -> "FECHA_DIGITACION",
    "SISBEN_INC_019" -> "FECHA_ACTUALIZACION_PERSONA",
    "SISBEN_INC_020" -> "ID_MARCA",
    "SISBEN_ERR_021" -> "ID_ESTADO",
    "SISBEN_ERR_022" -> "FECHA_ACTUALIZACION_CNS",
    "SISBEN_ERR_023" -> "TIP_DOCUMENTO",
    "SISBEN_ERR_029" -> "NUM_DOCUMENTO"
  )
  val NivelesNoAlmacenar: Set[String] = Set()
  val NivelesAnular: Set[String] = Set()

  // --- Fechas (RF-CUR-06). RESUELTO C-CUR-04 (spec-sisben.md §10.1 (inicio del día en Bogotá); HU R-23): zona America/Bogota.
  val FormatosFecha: Seq[String] = Seq("dd/MM/uuuu", "uuuu-MM-dd")
  lazy val Formateadores: Seq[DateTimeFormatter] = Transformaciones.formateadores(FormatosFecha)
  val ZonaFechas: ZoneId = ZoneId.of("America/Bogota")

  /** RF-CUR-04: true conserva la Ñ; false la translitera a N. RESUELTO C-CUR-03 (spec-sisben.md §10.2 regla 2 (la Ñ se conserva); HU R-23). */
  val ConservarEnie: Boolean = true

  // --- Archivo (IN-02, RF-CUR-03): tipo por prefijo y fecha de corte desde lineage.fileName.
  val RegexArchivo: Regex = """()SISBEN_EXPERIAN_(\d{6})\.(\d{14})([+-]\d{4})\.(txt|TXT)""".r
  val TipoPorPrefijo: Map[String, String] = Map("" -> "REGULAR")

  // Sin campos abiertos.
}
