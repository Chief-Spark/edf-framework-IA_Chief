// CUR-TPL-01 · Capa A · fija · generado por el skill edf-curation v{{version_skill}}
// Tipos con los que se declara el mapeo de una fuente. Sin Spark, sin framework, sin reflexión.
// ASSUMPTION CUR-PT01: paquete del código compartido sin definir; se propone {{paquete_compartido}}.
package {{paquete_compartido}}

/** Tipo de salida publicado en el evento curado y en el domain dictionary (RF-CUR-05). */
sealed trait TipoSalida extends Serializable
object TipoSalida {
  case object Texto extends TipoSalida
  case object Entero extends TipoSalida
  case object Largo extends TipoSalida
  case object Decimal extends TipoSalida
  case object Booleano extends TipoSalida
}

/** Transformación declarada por campo (Capa B la usa; Capa A la ejecuta). */
sealed trait Transformacion extends Serializable
object Transformacion {
  case object Texto extends Transformacion
  case object Original extends Transformacion
  case object Nombre extends Transformacion
  final case class PaddingIzquierda(longitud: Int) extends Transformacion
  case object FechaEpoch extends Transformacion
  case object Homologacion extends Transformacion
  case object Documento extends Transformacion
  final case class DesdeArchivo(atributo: String) extends Transformacion
  final case class CopiaDe(campo: String) extends Transformacion
  /** Une los valores curados no vacíos de campos anteriores con el separador (p. ej. standardizedIdName). */
  final case class Concatenacion(campos: Seq[String], separador: String) extends Transformacion
  final case class Constante(valor: Option[String]) extends Transformacion
}

final case class Catalogo(tabla: String, columna: String)

/** Una entrada por campo publicado: origen → destino, transformación, tipo y catálogo. */
final case class FieldMapping(
  destino: String,
  origen: String,
  transformacion: Transformacion,
  tipoSalida: TipoSalida,
  catalogo: Option[Catalogo] = None
)

/** Regla de normalización del número de documento según el tipo (RF-CUR-08). */
final case class ReglaDocumento(
  quitarCerosIzquierda: Boolean,
  soloDigitos: Boolean,
  conservarOriginal: Boolean,
  padding: Option[Int]
)

/**
 * Resultado de una transformación: valor (None = nulo) y advertencia opcional.
 * Ninguna acción lanza excepciones por valor de dato (CUR-RC03, D13).
 */
final case class Resultado[+A](valor: Option[A], advertencia: Option[String] = None)
object Resultado {
  def ok[A](a: A): Resultado[A] = Resultado(Some(a))
  val nulo: Resultado[Nothing] = Resultado(None)
  def advertir(motivo: String): Resultado[Nothing] = Resultado(None, Some(motivo))
}

/** Motivos de advertencia. Solo nombres de campo + motivo: nunca valores (CUR-RC06). */
object Motivos {
  val FormatoInvalido = "FORMATO_INVALIDO"
  val NoHomologable = "NO_HOMOLOGABLE"
  val HomologacionPendiente = "HOMOLOGACION_PENDIENTE"
  val NoNormalizable = "NO_NORMALIZABLE"
  val RegistroIlegible = "REGISTRO_ILEGIBLE"
  val ArchivoNoReconocido = "ARCHIVO_NO_RECONOCIDO"
  val ErrorInterno = "ERROR_INTERNO"
  /** Código de Validation con nivel nulo: el campo se conserva y el registro lo advierte (AUD-C7). */
  val NivelDesconocido = "NIVEL_DESCONOCIDO"
  /** Falta un componente de subscriberRelation: se conserva el del evento aceptado (AUD-C3, D14). */
  val ComponenteFaltante = "COMPONENTE_FALTANTE"
}
