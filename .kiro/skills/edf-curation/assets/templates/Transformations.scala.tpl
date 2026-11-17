// CUR-TPL-02 · Capa A · fija · generado por el skill edf-curation-stage v{{version_skill}}
// Acciones de transformación genéricas como funciones puras (modelo Information Type + Action,
// CUR-P08). Sin Spark, sin framework, sin reflexión por llamada (CUR-AP03, CUR-AP04).
// Un valor no transformable devuelve nulo + advertencia; nunca una excepción (CUR-RC03).
// La implementación de referencia en Java (scripts/java/.../Referencia.java) replica esta lógica.
package {{paquete_compartido}}

import java.text.Normalizer
import java.time.{LocalDate, LocalDateTime, YearMonth, ZoneId}
import java.time.format.{DateTimeFormatter, ResolverStyle}
import java.util.Locale
import scala.util.Try
import scala.util.matching.Regex

object Transformaciones {

  private val SoloDigitos = "\\d+"
  private val Entero = "[+-]?\\d+"
  private val Decimal = "[+-]?\\d+(\\.\\d+)?" // separador decimal: punto (RF-CUR-06)

  private def limpio(v: Option[String]): Option[String] = v.map(_.trim).filter(_.nonEmpty)

  /** Formateadores estrictos (dd/MM/uuuu rechaza 31/02/2024). Crear una vez y reutilizar. */
  def formateadores(patrones: Seq[String]): Seq[DateTimeFormatter] =
    patrones.map(p => DateTimeFormatter.ofPattern(p).withResolverStyle(ResolverStyle.STRICT))

  def texto(v: Option[String]): Resultado[String] = Resultado(limpio(v))

  /** RF-CUR-04: mayúsculas, sin tildes ni caracteres especiales, espacios simples. */
  def nombre(v: Option[String], conservarEnie: Boolean): Resultado[String] = limpio(v) match {
    case None => Resultado.nulo
    case Some(s0) =>
      val marca = "\u0001"
      val s1 = s0.toUpperCase(Locale.ROOT)
      val s2 = if (conservarEnie) s1.replace("Ñ", marca) else s1
      val s3 = Normalizer.normalize(s2, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
      val s4 = if (conservarEnie) s3.replace(marca, "Ñ") else s3
      val s5 = s4.replaceAll(if (conservarEnie) "[^A-ZÑ ]" else "[^A-Z ]", " ").replaceAll("\\s+", " ").trim
      if (s5.isEmpty) Resultado.advertir(Motivos.NoNormalizable) else Resultado.ok(s5)
  }

  /** RF-CUR-09: padding con ceros a la izquierda (códigos DANE). */
  def paddingIzquierda(v: Option[String], longitud: Int): Resultado[String] = limpio(v) match {
    case None => Resultado.nulo
    case Some(s) if !s.matches(SoloDigitos) || s.length > longitud => Resultado.advertir(Motivos.FormatoInvalido)
    case Some(s) => Resultado.ok(("0" * (longitud - s.length)) + s)
  }

  /** RF-CUR-06: fecha → epoch en milisegundos (Long) en la zona indicada. */
  def fechaEpoch(v: Option[String], formatos: Seq[DateTimeFormatter], zona: ZoneId): Resultado[Long] =
    limpio(v) match {
      case None => Resultado.nulo
      case Some(s) =>
        val ms = formatos.iterator
          .map(f => Try(LocalDateTime.parse(s, f).atZone(zona)).orElse(Try(LocalDate.parse(s, f).atStartOfDay(zona))))
          .collectFirst { case scala.util.Success(z) => z.toInstant.toEpochMilli }
        ms.map(Resultado.ok(_)).getOrElse(Resultado.advertir(Motivos.FormatoInvalido))
    }

  /** RF-CUR-07: homologación por tabla; un código pendiente nunca recibe un valor arbitrario. */
  def homologar(v: Option[String], tabla: Map[String, String], pendientes: Set[String]): Resultado[String] =
    limpio(v).map(_.toUpperCase(Locale.ROOT)) match {
      case None => Resultado.nulo
      case Some(s) if pendientes.contains(s) => Resultado.advertir(Motivos.HomologacionPendiente)
      case Some(s) => tabla.get(s).map(Resultado.ok(_)).getOrElse(Resultado.advertir(Motivos.NoHomologable))
    }

  /** RF-CUR-08: número de documento según la regla del tipo. */
  def documento(v: Option[String], regla: ReglaDocumento): Resultado[String] = limpio(v) match {
    case None => Resultado.nulo
    case Some(s) if regla.conservarOriginal => Resultado.ok(s)
    case Some(s0) =>
      // Pinning compara texto exacto (steering 11 §3): las letras del número van en mayúsculas (F-FW-14).
      val s1 = s0.replaceAll("\\s+", "").toUpperCase(Locale.ROOT)
      val s2 = if (regla.soloDigitos) s1.replaceAll("\\D", "") else s1
      val s3 = if (regla.quitarCerosIzquierda) s2.replaceAll("^0+", "") else s2
      val s4 = regla.padding.map(n => ("0" * math.max(0, n - s3.length)) + s3).getOrElse(s3)
      if (s4.isEmpty) Resultado.advertir(Motivos.NoNormalizable) else Resultado.ok(s4)
  }

  /** RF-CUR-05: conversión al tipo de salida publicado. */
  def convertir(v: Option[Any], tipo: TipoSalida): Resultado[Any] = v match {
    case None => Resultado.nulo
    case Some(x) =>
      tipo match {
        case TipoSalida.Texto => Resultado.ok(x.toString)
        case TipoSalida.Entero =>
          x match {
            case i: Int => Resultado.ok(i)
            case other =>
              val s = other.toString.trim
              if (s.matches(Entero)) Try(s.toInt).toOption.map(Resultado.ok(_)).getOrElse(Resultado.advertir(Motivos.FormatoInvalido))
              else Resultado.advertir(Motivos.FormatoInvalido)
          }
        case TipoSalida.Largo =>
          x match {
            case l: Long => Resultado.ok(l)
            case i: Int => Resultado.ok(i.toLong)
            case other =>
              val s = other.toString.trim
              if (s.matches(Entero)) Try(s.toLong).toOption.map(Resultado.ok(_)).getOrElse(Resultado.advertir(Motivos.FormatoInvalido))
              else Resultado.advertir(Motivos.FormatoInvalido)
          }
        case TipoSalida.Decimal =>
          val s = x.toString.trim
          if (s.matches(Decimal)) Resultado.ok(s.toDouble) else Resultado.advertir(Motivos.FormatoInvalido)
        case TipoSalida.Booleano =>
          x.toString.trim.toLowerCase(Locale.ROOT) match {
            case "true" => Resultado.ok(true)
            case "false" => Resultado.ok(false)
            case _ => Resultado.advertir(Motivos.FormatoInvalido)
          }
      }
  }

  /**
   * Tipo de archivo (grupo 1 del regex, por prefijo) y fecha de corte (último día del mes del
   * grupo 2 = AAAAMM, 00:00 en la zona, epoch ms) desde lineage.fileName (RF-CUR-03, IN-02).
   */
  def infoArchivo(fileName: String, regex: Regex, tipoPorPrefijo: Map[String, String], zona: ZoneId): (Option[String], Option[Long]) =
    Option(fileName).flatMap(_.split("[\\\\/]").lastOption).flatMap(regex.findFirstMatchIn) match {
      case None => (None, None)
      case Some(m) =>
        val tipo = tipoPorPrefijo.get(Option(m.group(1)).getOrElse(""))
        val aaaamm = m.group(2)
        val corte = Try(YearMonth.of(aaaamm.take(4).toInt, aaaamm.drop(4).toInt)).toOption
          .map(_.atEndOfMonth.atStartOfDay(zona).toInstant.toEpochMilli)
        (tipo, corte)
    }
}
