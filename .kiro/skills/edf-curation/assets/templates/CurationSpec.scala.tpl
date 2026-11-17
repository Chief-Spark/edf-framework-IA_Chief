// CUR-TPL-03 · Capa B · parametrizada · generado por el skill edf-curation-stage v{{version_skill}}
// Especificación de mapeo de {{Fuente}}: fuente única de verdad del esquema de salida. De aquí
// salen los campos de {{Fuente}}CuratedEvent y las entradas del domain dictionary (CUR-RC05,
// RF-CUR-13). No editar a mano: cambiar source-spec y regenerar. Ningún nombre publicado se
// renombra después de su primera publicación.
//
// Supuestos aplicados (detalle en references/decisiones-y-abiertos.md):
{{supuestos_comentario}}
package {{paquete_fuente}}.config

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.util.matching.Regex
import {{paquete_compartido}}.{Catalogo, FieldMapping, ReglaDocumento, TipoSalida, Transformacion, Transformaciones}

object {{Fuente}}CurationSpec {

  val VersionSkill: String = "{{version_skill}}"

  /** Una entrada por campo publicado, en el orden en que se escriben. */
  val Mapeos: Seq[FieldMapping] = Seq(
{{mapeos_scala}}
  )

  /** Campo de advertencias de curación (D13). ASSUMPTION: nombre provisional (CUR-PT05). */
  val CampoAdvertencias: String = "{{campo_advertencias}}"

  /** Nombres publicados, en orden: deben coincidir con el domain dictionary. */
  val ClavesPublicadas: Seq[String] = Seq({{claves_publicadas_scala}})

  // --- Homologación del tipo de documento (RF-CUR-07): Validation la consulta, Curation la persiste.
  val TablaHomologacion: String = "{{tabla_homologacion}}"
  val ColumnaClaveHomologacion: String = "{{columna_clave_homologacion}}"   // ASSUMPTION: F-12
  val ColumnaValorHomologacion: String = "{{columna_valor_homologacion}}"   // ASSUMPTION: F-12
  /** Tabla embebida: respaldo si el lookup no responde y base de los tests. */
  val Homologacion: Map[String, String] = Map({{homologacion_scala}})
  /** Códigos sin equivalente Experian todavía: nunca se les asigna un valor arbitrario. */
  val Pendientes: Set[String] = Set({{pendientes_scala}})
  /** Claves a consultar UNA vez en el lookup (CUR-RC04). */
  val ClavesHomologacion: Seq[String] = Seq({{claves_homologacion_scala}})

  // --- Número de documento (RF-CUR-08). ASSUMPTION: C-CUR-08 — longitudes estándar sin dato (padding = None).
  val CampoTipoDocumento: String = "{{campo_tipo_documento}}"
  val ReglaDocumentoPorDefecto: ReglaDocumento = {{regla_documento_defecto_scala}}
  val ReglasDocumento: Map[String, ReglaDocumento] = Map(
{{reglas_documento_scala}}
  )
  def reglaDocumento(tipoOriginal: Option[String]): ReglaDocumento =
    tipoOriginal.map(_.trim.toUpperCase).flatMap(ReglasDocumento.get).getOrElse(ReglaDocumentoPorDefecto)

  // --- Limpieza por severidad (RF-CUR-10): código de Validation → campo de entrada.
  val CodigosError: Map[String, String] = Map(
{{codigos_error_scala}}
  )
  val NivelesNoAlmacenar: Set[String] = Set({{niveles_no_almacenar_scala}})
  val NivelesAnular: Set[String] = Set({{niveles_anular_scala}})

  // --- Fechas (RF-CUR-06). ASSUMPTION: zona {{zona_fechas}} (C-CUR-04).
  val FormatosFecha: Seq[String] = Seq({{formatos_fecha_scala}})
  lazy val Formateadores: Seq[DateTimeFormatter] = Transformaciones.formateadores(FormatosFecha)
  val ZonaFechas: ZoneId = ZoneId.of("{{zona_fechas}}")

  /** RF-CUR-04. ASSUMPTION: C-CUR-03 (la Ñ se translitera salvo decisión contraria). */
  val ConservarEnie: Boolean = {{conservar_enie}}

  // --- Archivo (IN-02, RF-CUR-03): tipo por prefijo y fecha de corte desde lineage.fileName.
  val RegexArchivo: Regex = """{{regex_derivacion_scala}}""".r
  val TipoPorPrefijo: Map[String, String] = Map({{tipo_por_prefijo_scala}})

{{campos_abiertos_comentario}}
}
