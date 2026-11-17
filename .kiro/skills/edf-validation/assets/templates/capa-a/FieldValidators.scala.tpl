// VAL-TPL-02 · Capa A · plantilla fija (solo se rellena el paquete).
// Generado por el skill edf-validation-stage. No editar a mano: regenerar desde el source-spec.
// Funciones puras sin Spark ni framework Oxygen. RecordValidator.validarRegistro nunca lanza (VAL-RC04):
// una excepción del catálogo sale de FieldValidators.catalogo y validarRegistro la convierte en el código
// interno de nivel 1 (ADR-01, 2026-10-21).
package {{paquete_comun}}

import java.text.Normalizer
import java.time.format.{DateTimeFormatter, ResolverStyle}
import java.time.{LocalDate, LocalDateTime, YearMonth, ZoneId, ZoneOffset}
import java.util.regex.Pattern

import scala.util.Try
import scala.util.control.NonFatal

/** Causas (messageKey). Nunca contienen el valor del campo (sin PII). */
object Causa {
  val Obligatorio = "OBLIGATORIO"
  val Tipo = "TIPO"
  val Longitud = "LONGITUD"
  val Rango = "RANGO"
  val Dominio = "DOMINIO"
  val Catalogo = "CATALOGO"
  val FormatoFecha = "FORMATO_FECHA"
  val FechaFutura = "FECHA_FUTURA"
  val Correspondencia = "CORRESPONDENCIA"
  val Columnas = "COLUMNAS"
  val Interno = "INTERNO"
  val NombreArchivo = "NOMBRE_ARCHIVO"
  val Header = "HEADER"
}

object Texto {
  /** Comparación tolerante a mayúsculas, tildes y espacios extremos (RV-GEN-08). */
  def comparable(s: String): String = {
    val sinTildes = Normalizer.normalize(Option(s).getOrElse("").trim, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
    sinTildes.toUpperCase(java.util.Locale.ROOT)
  }

  def sinBom(s: String): String = Option(s).getOrElse("").replace("\uFEFF", "")
}

object Campos {
  /** split(delimitador, -1) una sola vez; delimitador literal (no regex). */
  def separar(fila: String, delimitador: String): Array[String] =
    Option(fila).getOrElse("").split(Pattern.quote(delimitador), -1)

  /** Acceso seguro: posición fuera de rango devuelve vacío (VAL-TPL-04). Posiciones 1-based. */
  def valor(valores: Array[String], posicion: Int): String =
    if (posicion >= 1 && posicion <= valores.length) Option(valores(posicion - 1)).getOrElse("").trim else ""
}

object Fechas {
  private def formateador(patron: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(patron.replace("yyyy", "uuuu")).withResolverStyle(ResolverStyle.STRICT)

  /** Intenta cada formato en orden; nunca lanza. */
  def parsear(valor: String, formatos: Seq[String]): Option[LocalDateTime] =
    formatos.view.flatMap { patron =>
      val f = formateador(patron)
      if (patron.contains("HH")) Try(LocalDateTime.parse(valor, f)).toOption
      else Try(LocalDate.parse(valor, f).atStartOfDay()).toOption
    }.headOption

  /** Epoch en milisegundos del inicio del día en la zona dada. */
  def epochMs(fecha: LocalDateTime, zona: String): Option[Long] =
    Try(fecha.toLocalDate.atStartOfDay(ZoneId.of(zona)).toInstant.toEpochMilli).toOption
}

object FieldValidators {

  def normalizar(valor: String, normalizaciones: Seq[Normalizacion]): String =
    normalizaciones.foldLeft(valor.trim) {
      case (v, Normalizacion.QuitarEspacios)       => v.replaceAll("\\s+", "")
      case (v, Normalizacion.QuitarCerosIzquierda) => v.replaceFirst("^0+(?=.)", "")
    }

  private val SoloDigitos = "^[0-9]+$".r

  private def error(spec: FieldSpec, causa: String): Option[FieldError] =
    Some(FieldError(spec.nombre, spec.codigoError, spec.severidad.nivel, causa))

  def obligatorio(spec: FieldSpec, v: String): Option[FieldError] =
    if (v.isEmpty && spec.obligatorio) error(spec, Causa.Obligatorio) else None

  def tipoDato(spec: FieldSpec, v: String): Option[FieldError] = spec.tipo match {
    case TipoDato.Numerico if SoloDigitos.findFirstIn(v).isEmpty => error(spec, Causa.Tipo)
    case TipoDato.Fecha if Fechas.parsear(v, spec.formatosFecha).isEmpty => error(spec, Causa.FormatoFecha)
    case _ => None
  }

  def longitud(spec: FieldSpec, v: String): Option[FieldError] =
    spec.longitudMax.filter(max => v.length > max).flatMap(_ => error(spec, Causa.Longitud))

  def rango(spec: FieldSpec, v: String): Option[FieldError] = spec.rango.flatMap { r =>
    Try(v.toLong).toOption match {
      case None                                => error(spec, Causa.Tipo)
      case Some(n) if n < r.min || n > r.max   => error(spec, Causa.Rango)
      case _                                   => None
    }
  }

  def dominio(spec: FieldSpec, v: String): Option[FieldError] = spec.dominio.flatMap { d =>
    if (d.map(Texto.comparable).contains(Texto.comparable(v))) None else error(spec, Causa.Dominio)
  }

  def noFutura(spec: FieldSpec, v: String, hoy: LocalDate): Option[FieldError] =
    if (!spec.noFutura) None
    else Fechas.parsear(v, spec.formatosFecha) match {
      case Some(f) if f.toLocalDate.isAfter(hoy) => error(spec, Causa.FechaFutura)
      case _                                    => None
    }

  /** Valor ausente del catálogo ⇒ CATALOGO. Si el catálogo lanza, la excepción sale de aquí a propósito:
    * es una falla de infraestructura, no un dato inválido, y RecordValidator.validarRegistro la convierte en
    * el código interno de nivel 1 (ADR-01). */
  def catalogo(spec: FieldSpec, v: String, cat: Catalogo): Option[FieldError] = spec.catalogo.flatMap { ref =>
    if (cat.existe(ref.tabla, ref.columna, v)) None else error(spec, Causa.Catalogo)
  }

  /** Aplica las reglas del campo en orden fijo; devuelve como máximo un error (un código por campo). */
  def validar(spec: FieldSpec, crudo: String, cat: Catalogo, hoy: LocalDate): Option[FieldError] = {
    val v = normalizar(Option(crudo).getOrElse(""), spec.normalizacion)
    if (v.isEmpty) obligatorio(spec, v)
    else tipoDato(spec, v)
      .orElse(longitud(spec, v))
      .orElse(rango(spec, v))
      .orElse(dominio(spec, v))
      .orElse(noFutura(spec, v, hoy))
      .orElse(catalogo(spec, v, cat))
  }
}

/** Resultado de validar un registro. */
final case class ResultadoRegistro(errores: Seq[FieldError], qualityScores: Seq[(String, Double)])

/** Información extraída del nombre de archivo. */
final case class InfoNombre(prefijo: String, periodo: YearMonth, recibido: LocalDateTime, offset: ZoneOffset, fileTag: String)

/** Orquestación pura de un registro, del header y del nombre (usada por la Capa C). */
object RecordValidator {

  def esHeader(spec: SourceSpec, fila: String): Boolean = {
    val primero = Texto.comparable(Texto.sinBom(Campos.valor(Campos.separar(fila, spec.delimitador), 1)))
    spec.campos.find(_.posicion == 1).exists(_.aliasHeader.map(Texto.comparable).contains(primero))
  }

  def debeFiltrar(spec: SourceSpec, fila: String): Boolean = {
    val vacia = Option(fila).forall(_.trim.isEmpty)
    if (vacia) spec.filtro.lineasVacias
    else spec.filtro.posicionCampo.exists { pos =>
      val valores = Campos.separar(fila, spec.delimitador)
      pos <= valores.length && spec.filtro.valores.contains(Campos.valor(valores, pos))
    }
  }

  /** Header: número de columnas y nombre por posición (alias fuente o dominio, sin mayúsculas). */
  def validarHeader(spec: SourceSpec, headers: Seq[String]): Seq[FieldError] = {
    val errorHeader = FieldError("HEADER", spec.codigos.header, Severidad.Error.nivel, Causa.Header)
    headers.headOption.map(h => Campos.separar(Texto.sinBom(h), spec.delimitador)) match {
      case None => Seq(errorHeader)
      case Some(cols) if cols.length != spec.numeroCampos => Seq(errorHeader.copy(causa = Causa.Columnas))
      case Some(cols) =>
        val coincide = spec.campos.forall { c =>
          c.aliasHeader.map(Texto.comparable).contains(Texto.comparable(Campos.valor(cols, c.posicion)))
        }
        if (coincide) Seq.empty else Seq(errorHeader)
    }
  }

  /** Nombre de archivo (sin ruta). Nunca lanza: None si no coincide o la fecha es inválida. */
  def parsearNombre(spec: SourceSpec, rutaOArchivo: String): Option[InfoNombre] = {
    val nombre = Option(rutaOArchivo).getOrElse("").split("[/\\\\]").lastOption.getOrElse("")
    val n = spec.nombreArchivo
    val m = Pattern.compile(n.regex).matcher(nombre)
    if (!m.matches()) None
    else Try {
      val prefijo = Option(m.group(n.grupoPrefijo)).getOrElse("")
      val periodo = YearMonth.parse(m.group(n.grupoPeriodo), DateTimeFormatter.ofPattern("uuuuMM"))
      val recibido = LocalDateTime.parse(m.group(n.grupoMarcaTiempo),
        DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT))
      val offset = ZoneOffset.of(m.group(n.grupoOffset)) // acepta ±hhmm
      InfoNombre(prefijo, periodo, recibido, offset, n.fileTagPorPrefijo.getOrElse(prefijo, prefijo))
    }.toOption
  }

  def validarNombre(spec: SourceSpec, rutaOArchivo: String): Seq[FieldError] =
    if (parsearNombre(spec, rutaOArchivo).isDefined) Seq.empty
    else Seq(FieldError("FILENAME", spec.codigos.nombreArchivo, Severidad.Error.nivel, Causa.NombreArchivo))

  private def aplicarRegla(regla: ReglaEntreCampos, spec: SourceSpec, valores: Array[String],
                           conError: Set[String], cat: Catalogo): Option[FieldError] = {
    def v(campo: String): String = spec.campo(campo).map(c => Campos.valor(valores, c.posicion)).getOrElse("")
    regla match {
      case r: ReglaEntreCampos.CorrespondenciaCatalogo =>
        if (conError.contains(r.campoCodigo) || conError.contains(r.campoNombre) || v(r.campoCodigo).isEmpty) None
        else cat.buscar(r.tabla, r.columnaClave, v(r.campoCodigo), r.columnaNombre) match { // si lanza: ADR-01
          case Some(esperado) if Texto.comparable(esperado) != Texto.comparable(v(r.campoNombre)) =>
            Some(FieldError(r.campoNombre, r.codigo, r.severidad.nivel, Causa.Correspondencia))
          case _ => None
        }
      case r: ReglaEntreCampos.ValorConCodigoPropio =>
        if (r.valores.map(Texto.comparable).contains(Texto.comparable(v(r.campo))))
          Some(FieldError(r.campo, r.codigo, r.severidad.nivel, r.causa))
        else None
    }
  }

  def qualityScores(spec: SourceSpec, errores: Seq[FieldError]): Seq[(String, Double)] =
    spec.qualityScores.map { q =>
      q.nombre -> (if (errores.exists(e => q.anulanNiveles.contains(e.nivel))) 0.0 else 1.0)
    }

  /** Valida un registro completo. Errores únicos por código, en orden de aparición. Nunca lanza: una
    * excepción al validar campos o reglas (p. ej. un catálogo que falla) produce un único error con el
    * código interno y nivel 1 (ADR-01; mismo error que el catch del processor). El registro pierde los
    * demás errores de campo, pero se rechaza igual. */
  def validarRegistro(spec: SourceSpec, fila: String, cat: Catalogo, hoy: LocalDate): ResultadoRegistro = {
    val valores = Campos.separar(fila, spec.delimitador)
    val errores: Seq[FieldError] =
      if (valores.length != spec.numeroCampos)
        Seq(FieldError("REGISTRO", spec.codigos.estructura, Severidad.Error.nivel, Causa.Columnas))
      else try {
        val deCampo = spec.campos.flatMap(c => FieldValidators.validar(c, Campos.valor(valores, c.posicion), cat, hoy))
        val conError = deCampo.map(_.campo).toSet
        val entreCampos = spec.reglas.flatMap(r => aplicarRegla(r, spec, valores, conError, cat))
        val todos = deCampo ++ entreCampos
        todos.zipWithIndex.filter { case (e, i) => todos.indexWhere(_.codigo == e.codigo) == i }.map(_._1)
      } catch {
        case NonFatal(_) => Seq(FieldError("REGISTRO", spec.codigos.interno, Severidad.Error.nivel, Causa.Interno))
      }
    ResultadoRegistro(errores, qualityScores(spec, errores))
  }

  /** Clave de duplicado: valores normalizados de los campos clave. Nunca devuelve un arreglo vacío (VAL-AP03).
    * ASSUMPTION VAL-SP03: si algún campo de la clave está vacío, la clave se vuelve única por contenido de la fila
    * para no agrupar como duplicados registros que solo comparten la ausencia de documento. */
  def claveDuplicado(spec: SourceSpec, valores: Array[String]): Array[Any] = {
    val partes = spec.duplicados.clave.map { nombre =>
      spec.campo(nombre).map(c => FieldValidators.normalizar(Campos.valor(valores, c.posicion), c.normalizacion))
        .getOrElse("")
    }
    if (partes.isEmpty || partes.exists(_.isEmpty)) Array[Any]("SIN_CLAVE", valores.mkString("\u0001").hashCode)
    else partes.toArray[Any]
  }

  /** As-of-date del registro en epoch ms; None si el campo no es una fecha válida. */
  def asOfDate(spec: SourceSpec, fila: String): Option[Long] =
    spec.campo(spec.campoAsOfDate).flatMap { c =>
      Fechas.parsear(Campos.valor(Campos.separar(fila, spec.delimitador), c.posicion), c.formatosFecha)
        .flatMap(f => Fechas.epochMs(f, spec.zonaHoraria))
    }

  /** ASSUMPTION F-14: índice del elemento a conservar = mayor fecha; empate → mayor desempate. */
  def indiceAConservar(candidatos: Seq[(Long, String)]): Int =
    if (candidatos.isEmpty) -1
    else candidatos.zipWithIndex.maxBy { case ((fecha, desempate), _) => (fecha, desempate) }._2
}
