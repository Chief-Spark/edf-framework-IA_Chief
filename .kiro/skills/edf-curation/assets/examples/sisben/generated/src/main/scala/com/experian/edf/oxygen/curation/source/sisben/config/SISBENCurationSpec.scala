// CUR-TPL-03 · Capa B · parametrizada · generado por el skill edf-curation v0.1.0
// Especificación de mapeo de SISBEN: fuente única de verdad del esquema de salida. De aquí
// salen los campos de SISBENCuratedEvent y las entradas del domain dictionary (CUR-RC05,
// RF-CUR-13). No editar a mano: cambiar source-spec y regenerar. Ningún nombre publicado se
// renombra después de su primera publicación.
//
// Supuestos aplicados (detalle en references/decisiones-y-abiertos.md):
//   - idFichaOrigen: supuesto
//   - idHogar: supuesto
//   - codDepartamentoSisben: propuesta_de_frontera
//   - nomDepartamento: supuesto
//   - codMunicipioSisben: propuesta_de_frontera
//   - nomMunicipio: supuesto
//   - idClase: supuesto
//   - idPersonaOrden: supuesto
//   - idNacional: supuesto
//   - primerApellido: supuesto
//   - segundoApellido: supuesto
//   - primerNombre: supuesto
//   - segundoNombre: supuesto
//   - numeroDocumento: supuesto
//   - grupoSisben: propuesta_de_frontera
//   - nivelSisben: propuesta_de_frontera
//   - fechaDigitacion: supuesto
//   - fechaActualizacionPersona: supuesto
//   - idMarca: supuesto
//   - idEstado: supuesto
//   - fechaActualizacionCns: supuesto
//   - fechaCorte: propuesta_de_frontera
//   - tipoArchivo: propuesta_de_frontera
//   - currentPartyType: inferido
//   - currentPartyIdNumber: inferido
//   - subscriberBlockStatus: supuesto
//   - subscriberBlockReason: inferido
//   - paquete compartido: supuesto (CUR-PT01)
//   - reglas de documento: supuesto (RF-CUR-08)
//   - outputEventType: inferido (CUR-PA04)
//   - convención de nombre de archivo: propuesta_de_frontera (D8)
package com.experian.edf.oxygen.curation.source.sisben.config

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.util.matching.Regex
import com.experian.edf.oxygen.curation.source.common.{Catalogo, FieldMapping, ReglaDocumento, TipoSalida, Transformacion, Transformaciones}

object SISBENCurationSpec {

  val VersionSkill: String = "0.1.0"

  /** Una entrada por campo publicado, en el orden en que se escriben. */
  val Mapeos: Seq[FieldMapping] = Seq(
    FieldMapping("idFichaOrigen", "ID_FICHA_ORIGEN", Transformacion.Texto, TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("idHogar", "ID_HOGAR", Transformacion.Texto, TipoSalida.Entero, Some(Catalogo("SISBEN_HOME_ID_CLASIFICATION_NH", "codigo"))),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("codDepartamentoSisben", "COD_DPTO", Transformacion.PaddingIzquierda(2), TipoSalida.Texto, Some(Catalogo("102_DANE_DIVIPOLA_STATES_CODES_NH", "cod_dpto"))),  // propuesta_de_frontera
    FieldMapping("nomDepartamento", "NOM_DPTO", Transformacion.Nombre, TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("codMunicipioSisben", "COD_MPIO", Transformacion.PaddingIzquierda(5), TipoSalida.Texto, Some(Catalogo("2_DANE_DIVIPOLA_CITIES_CODES_NH", "cod_mpio"))),  // propuesta_de_frontera
    FieldMapping("nomMunicipio", "NOM_MPIO", Transformacion.Nombre, TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("idClase", "ID_CLASE", Transformacion.Texto, TipoSalida.Entero, Some(Catalogo("201_SISBEN_ZONES_NH", "codigo"))),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("idPersonaOrden", "ID_PERSONA_ORDEN", Transformacion.Texto, TipoSalida.Entero, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("idNacional", "ID_NACIONAL", Transformacion.Texto, TipoSalida.Largo, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("primerApellido", "PRI_APELLIDO", Transformacion.Nombre, TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("segundoApellido", "SEG_APELLIDO", Transformacion.Nombre, TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("primerNombre", "PRI_NOMBRE", Transformacion.Nombre, TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("segundoNombre", "SEG_NOMBRE", Transformacion.Nombre, TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("tipoDocumento", "TIP_DOCUMENTO", Transformacion.Homologacion, TipoSalida.Entero, None),
    FieldMapping("tipoDocumentoSisben", "TIP_DOCUMENTO", Transformacion.Original, TipoSalida.Texto, None),
    FieldMapping("numeroDocumento", "NUM_DOCUMENTO", Transformacion.Documento, TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("grupoSisben", "ID_GRUPO", Transformacion.Original, TipoSalida.Texto, Some(Catalogo("SISBEN_GROUP_SUBGROUP_NH", "grupo"))),  // propuesta_de_frontera
    FieldMapping("nivelSisben", "ID_NIVEL", Transformacion.Texto, TipoSalida.Entero, Some(Catalogo("SISBEN_GROUP_SUBGROUP_NH", "nivel"))),  // propuesta_de_frontera
    FieldMapping("fechaDigitacion", "FECHA_DIGITACION", Transformacion.FechaEpoch, TipoSalida.Largo, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("fechaActualizacionPersona", "FECHA_ACTUALIZACION_PERSONA", Transformacion.FechaEpoch, TipoSalida.Largo, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("idMarca", "ID_MARCA", Transformacion.Texto, TipoSalida.Entero, Some(Catalogo("203_SISBEN_RECORD_STATUS_AND_MARK_NH", "codigo"))),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("idEstado", "ID_ESTADO", Transformacion.Texto, TipoSalida.Entero, Some(Catalogo("203_SISBEN_RECORD_STATUS_AND_MARK_NH", "codigo"))),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("fechaActualizacionCns", "FECHA_ACTUALIZACION_CNS", Transformacion.FechaEpoch, TipoSalida.Largo, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("fechaCorte", "", Transformacion.DesdeArchivo("fecha_corte"), TipoSalida.Largo, None),  // propuesta_de_frontera
    FieldMapping("tipoArchivo", "", Transformacion.DesdeArchivo("tipo_archivo"), TipoSalida.Texto, None),  // propuesta_de_frontera
    FieldMapping("currentPartyType", "", Transformacion.CopiaDe("tipoDocumento"), TipoSalida.Entero, None),  // inferido
    FieldMapping("currentPartyIdNumber", "", Transformacion.CopiaDe("numeroDocumento"), TipoSalida.Texto, None),  // inferido
    FieldMapping("subscriberBlockStatus", "", Transformacion.Constante(Some("NO_BLOQUEADO")), TipoSalida.Texto, None),  // ASSUMPTION: C-CUR-02 (supuesto)
    FieldMapping("subscriberBlockReason", "", Transformacion.Constante(None), TipoSalida.Texto, None)  // inferido
  )

  /** Campo de advertencias de curación (D13). ASSUMPTION CUR-PT05: nombre del campo; confirmar el nombre provisional. */
  val CampoAdvertencias: String = "advertenciasCuracion"

  /** Nombres publicados, en orden: deben coincidir con el domain dictionary. */
  val ClavesPublicadas: Seq[String] = Seq("idFichaOrigen", "idHogar", "codDepartamentoSisben", "nomDepartamento", "codMunicipioSisben", "nomMunicipio", "idClase", "idPersonaOrden", "idNacional", "primerApellido", "segundoApellido", "primerNombre", "segundoNombre", "tipoDocumento", "tipoDocumentoSisben", "numeroDocumento", "grupoSisben", "nivelSisben", "fechaDigitacion", "fechaActualizacionPersona", "idMarca", "idEstado", "fechaActualizacionCns", "fechaCorte", "tipoArchivo", "currentPartyType", "currentPartyIdNumber", "subscriberBlockStatus", "subscriberBlockReason", "advertenciasCuracion")

  /** Claves de identidad que se omiten del evento si son nulas: ausentes, nunca null (AUD-C4, C-CUR-05). */
  val OmitirSiNulas: Set[String] = Set()

  // --- Homologación del tipo de documento (RF-CUR-07): Validation la consulta, Curation la persiste.
  val TablaHomologacion: String = "202_SISBEN_PROCESS_ID_TYPE_HO"
  val ColumnaClaveHomologacion: String = "tipo_sisben"   // ASSUMPTION F-12; confirmar con el catálogo real
  val ColumnaValorHomologacion: String = "tipo_experian"   // ASSUMPTION F-12; confirmar con el catálogo real
  /** Tabla embebida: respaldo si el lookup no responde y base de los tests. */
  val Homologacion: Map[String, String] = Map("1" -> "10", "2" -> "7", "3" -> "1", "4" -> "4", "5" -> "8", "6" -> "5", "8" -> "9", "PPT" -> "11")
  /** Códigos sin equivalente Experian todavía: nunca se les asigna un valor arbitrario. */
  val Pendientes: Set[String] = Set("7")
  /** Claves a consultar UNA vez en el lookup (CUR-RC04). */
  val ClavesHomologacion: Seq[String] = Seq("1", "2", "3", "4", "5", "6", "8", "PPT")

  // --- Número de documento (RF-CUR-08). ASSUMPTION C-CUR-08: reglas de reglas_documento; confirmar longitudes estándar (sin dato).
  val CampoTipoDocumento: String = "TIP_DOCUMENTO"
  val ReglaDocumentoPorDefecto: ReglaDocumento = ReglaDocumento(quitarCerosIzquierda = true, soloDigitos = false, conservarOriginal = false, padding = None)
  val ReglasDocumento: Map[String, ReglaDocumento] = Map(
    "6" -> ReglaDocumento(quitarCerosIzquierda = false, soloDigitos = false, conservarOriginal = false, padding = None),
    "8" -> ReglaDocumento(quitarCerosIzquierda = true, soloDigitos = true, conservarOriginal = false, padding = None),
    "PPT" -> ReglaDocumento(quitarCerosIzquierda = false, soloDigitos = false, conservarOriginal = true, padding = None)
  )
  def reglaDocumento(tipoOriginal: Option[String]): ReglaDocumento =
    tipoOriginal.map(_.trim.toUpperCase).flatMap(ReglasDocumento.get).getOrElse(ReglaDocumentoPorDefecto)

  // --- Limpieza por severidad (RF-CUR-10): código de Validation → campo de entrada.
  val CodigosError: Map[String, String] = Map(
    "SISBEN_ERR_001" -> "ID_FICHA_ORIGEN",
    "SISBEN_ERR_002" -> "ID_HOGAR",
    "SISBEN_ERR_003" -> "COD_DPTO",
    "SISBEN_ERR_004" -> "NOM_DPTO",
    "SISBEN_ERR_005" -> "COD_MPIO",
    "SISBEN_ERR_006" -> "NOM_MPIO",
    "SISBEN_ERR_007" -> "ID_CLASE",
    "SISBEN_ERR_008" -> "ID_PERSONA_ORDEN",
    "SISBEN_ERR_009" -> "ID_NACIONAL",
    "SISBEN_ERR_010" -> "PRI_APELLIDO",
    "SISBEN_INC_011" -> "SEG_APELLIDO",
    "SISBEN_ERR_012" -> "PRI_NOMBRE",
    "SISBEN_INC_013" -> "SEG_NOMBRE",
    "SISBEN_ERR_014" -> "TIP_DOCUMENTO",
    "SISBEN_ERR_015" -> "NUM_DOCUMENTO",
    "SISBEN_ERR_016" -> "ID_GRUPO",
    "SISBEN_ERR_017" -> "ID_NIVEL",
    "SISBEN_ERR_018" -> "FECHA_DIGITACION",
    "SISBEN_INC_019" -> "FECHA_ACTUALIZACION_PERSONA",
    "SISBEN_INC_020" -> "ID_MARCA",
    "SISBEN_ERR_021" -> "ID_ESTADO",
    "SISBEN_ERR_022" -> "FECHA_ACTUALIZACION_CNS",
    "SISBEN_ERR_023" -> "TIP_DOCUMENTO"
  )
  val NivelesNoAlmacenar: Set[String] = Set("3")
  val NivelesAnular: Set[String] = Set("0")

  // --- Fechas (RF-CUR-06). ASSUMPTION C-CUR-04: zona UTC; confirmar con la spec de la ingesta.
  val FormatosFecha: Seq[String] = Seq("dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss")
  lazy val Formateadores: Seq[DateTimeFormatter] = Transformaciones.formateadores(FormatosFecha)
  val ZonaFechas: ZoneId = ZoneId.of("UTC")

  /** RF-CUR-04: true conserva la Ñ; false la translitera a N. ASSUMPTION C-CUR-03; confirmar si la spec de la ingesta no lo fija. */
  val ConservarEnie: Boolean = false

  // --- Archivo (IN-02, RF-CUR-03): tipo por prefijo y fecha de corte desde lineage.fileName.
  val RegexArchivo: Regex = """(RT_|EP_)?SISBEN_EXPERIAN_(\d{6})\.\d{14}[+-]\d{4}""".r
  val TipoPorPrefijo: Map[String, String] = Map("RT_" -> "RT", "EP_" -> "EP", "" -> "REGULAR")

  // Campos pedidos por la frontera que NO se generan hasta cerrar la decisión:
  //   - puntajeSisben: Sin campo de origen en el layout de 22 (D11-a)
  //   - fechaEncuesta: Sin equivalente documentado (antes FECENCUE) (D11-a)
  //   - fechaModificacion: Sin equivalente documentado (antes FECMODFI) (D11-a)
  //   - nombreCompleto: Orden de concatenación sin confirmar (P2 / CUR-PA01)
  //   - businessLineCode: Catálogo de terceras fuentes sin valores (CUR-PT06)
  //   - typeOfData: Catálogo 170 sin valores (CUR-PT06)
}
