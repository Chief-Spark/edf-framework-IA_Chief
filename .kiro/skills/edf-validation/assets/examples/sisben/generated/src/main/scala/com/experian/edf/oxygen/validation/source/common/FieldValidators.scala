// VAL-TPL-02 · Capa A · plantilla fija (solo se rellena el paquete).
// Generado por la skill edf-validation. No editar a mano: regenerar desde el source-spec.
// Funciones puras sin Spark ni framework Oxygen. RecordValidator.validarRegistro nunca lanza (VAL-RC04):
// una excepción del catálogo sale de FieldValidators.catalogo y validarRegistro la convierte en el código
// interno de nivel 1 (ADR-01, 2026-10-21).
package com.experian.edf.oxygen.validation.source.common

import java.text.Normalizer
import java.time.format.{DateTimeFormatter, ResolverStyle}
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneId, ZoneOffset}
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
  val Footer = "FOOTER"
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
  /** split(delimitador, -1) una sola vez; delimitador literal (no regex). Total: fila nula → Array(""),
    * delimitador nulo o vacío → la fila entera como un solo campo (AUD-V1). Antes de partir quita SOLO el "\r"
    * final de una línea CRLF (el motor, al leer con encoding, parte el archivo por "\n" y lo deja), para que no
    * llegue al último campo de validatedRecordJson; no toca ningún otro espacio (AUD-V-29). */
  def separar(fila: String, delimitador: String): Array[String] = {
    val original = Option(fila).getOrElse("")
    val texto = if (original.endsWith("\r")) original.dropRight(1) else original
    Option(delimitador).filter(_.nonEmpty).map(d => texto.split(Pattern.quote(d), -1)).getOrElse(Array(texto))
  }

  /** Valor tal como llegó, sin trim: es el que se publica en validatedRecordJson, porque Curation necesita el
    * valor recibido (AUD-V-05). Posición fuera de rango devuelve vacío. Posiciones 1-based. */
  def crudo(valores: Array[String], posicion: Int): String =
    if (posicion >= 1 && posicion <= valores.length) Option(valores(posicion - 1)).getOrElse("") else ""

  /** Valor con trim, para validar (VAL-TPL-04). Posición fuera de rango devuelve vacío. Posiciones 1-based. */
  def valor(valores: Array[String], posicion: Int): String = crudo(valores, posicion).trim
}

object Fechas {
  private def formateador(patron: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(patron.replace("yyyy", "uuuu")).withResolverStyle(ResolverStyle.STRICT)

  /** Intenta cada formato en orden; nunca lanza. Los patrones los valida el generador (solo dd, MM, yyyy, HH,
    * mm, ss y separadores sin significado en DateTimeFormatter), así que ofPattern no lanza (AUD-V2). */
  def parsear(valor: String, formatos: Seq[String]): Option[LocalDateTime] =
    formatos.view.flatMap { patron =>
      val f = formateador(patron)
      // SILENCIO-INTENCIONAL VAL-RC04: un valor que no parsea es un error de campo (FORMATO_FECHA), no una excepción.
      if (patron.contains("HH")) Try(LocalDateTime.parse(valor, f)).toOption
      else Try(LocalDate.parse(valor, f).atStartOfDay()).toOption // SILENCIO-INTENCIONAL VAL-RC04: ídem
    }.headOption

  /** Epoch en milisegundos del inicio del día en la zona dada. */
  def epochMs(fecha: LocalDateTime, zona: String): Option[Long] =
    // SILENCIO-INTENCIONAL VAL-SP02: sin epoch válido, asOfDate = None y el motor usa la fecha de recepción.
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
    // SILENCIO-INTENCIONAL VAL-RC04: un valor que no es entero (o desborda Long) es un error de campo (TIPO).
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

  /** ¿Es la fila de header? Total (AUD-V1): fila nula, vacía o truncada → false, sin excepción. */
  def esHeader(spec: SourceSpec, fila: String): Boolean = {
    val primero = Texto.comparable(Texto.sinBom(Campos.valor(Campos.separar(fila, spec.delimitador), 1)))
    spec.campos.find(_.posicion == 1).exists(_.aliasHeader.map(Texto.comparable).contains(primero))
  }

  /** Filtro silencioso (D6.a/D14). Total (AUD-V1): fila nula o vacía → `lineasVacias`; fila truncada o sin el
    * campo del filtro → false (no se filtra: el registro sale con el código de estructura). Nunca lanza, así que
    * el processor no lo envuelve en Try: una excepción tragada como false validaría una fila que debía filtrarse. */
  def debeFiltrar(spec: SourceSpec, fila: String): Boolean = {
    val vacia = Option(fila).forall(_.trim.isEmpty)
    if (vacia) spec.filtro.lineasVacias
    else spec.filtro.posicionCampo.exists { pos =>
      val valores = Campos.separar(fila, spec.delimitador)
      pos >= 1 && pos <= valores.length && spec.filtro.valores.contains(Campos.valor(valores, pos))
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

  /** Footer (E8): exactamente `filas` filas y todas reconocidas por `esFooter` (el isFooterRow del processor).
    * Total: lista nula, vacía o con otra cantidad → un error con el código de footer (AUD-V3). */
  def validarFooter(footers: Seq[String], filas: Int, esFooter: String => Boolean, codigo: String): Seq[FieldError] = {
    val lista = Option(footers).getOrElse(Seq.empty)
    if (lista.size == filas && lista.forall(esFooter)) Seq.empty
    else Seq(FieldError("FOOTER", codigo, Severidad.Error.nivel, Causa.Footer))
  }

  /** Nombre de archivo (sin ruta). Nunca lanza: None si no coincide o la fecha es inválida. */
  def parsearNombre(spec: SourceSpec, rutaOArchivo: String): Option[InfoNombre] = {
    val nombre = Option(rutaOArchivo).getOrElse("").split("[/\\\\]").lastOption.getOrElse("")
    val n = spec.nombreArchivo
    val m = Pattern.compile(n.regex).matcher(nombre)
    if (!m.matches()) None
    // SILENCIO-INTENCIONAL VAL-RC03: fecha u offset inválidos en el nombre → None; lo diagnostica validateFilename.
    else Try {
      val prefijo = Option(m.group(n.grupoPrefijo)).getOrElse("")
      val periodo = YearMonth.parse(m.group(n.grupoPeriodo), DateTimeFormatter.ofPattern("uuuuMM"))
      val recibido = LocalDateTime.parse(m.group(n.grupoMarcaTiempo),
        DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT))
      val offset = ZoneOffset.of(m.group(n.grupoOffset)) // acepta ±hhmm
      InfoNombre(prefijo, periodo, recibido, offset, n.fileTagPorPrefijo.getOrElse(prefijo, prefijo))
    }.toOption
  }

  /** Nombre de archivo con el reloj del sistema. */
  def validarNombre(spec: SourceSpec, rutaOArchivo: String): Seq[FieldError] =
    validarNombre(spec, rutaOArchivo, Instant.now())

  /** Nombre de archivo: regex, fechas y offset legibles (parsearNombre) y, si la spec las activa, las reglas de
    * fecha respecto de `ahora` (AUD-V-02). Sin reglas activas el resultado no depende de `ahora`. Nunca lanza. */
  def validarNombre(spec: SourceSpec, rutaOArchivo: String, ahora: Instant): Seq[FieldError] = {
    val n = spec.nombreArchivo
    val valido = parsearNombre(spec, rutaOArchivo).exists { i =>
      // SILENCIO-INTENCIONAL VAL-RC03: zona horaria inválida con periodoNoFuturo → nombre inválido (su código), sin excepción.
      def mesActual: Option[YearMonth] = Try(YearMonth.from(ahora.atZone(ZoneId.of(spec.zonaHoraria)))).toOption
      (!n.periodoNoFuturo || mesActual.exists(m => !i.periodo.isAfter(m))) &&
        (!n.recepcionNoFutura || !i.recibido.atOffset(i.offset).toInstant.isAfter(ahora)) &&
        (!n.recepcionDesdeInicioPeriodo || !i.recibido.toLocalDate.isBefore(i.periodo.atDay(1)))
    }
    if (valido) Seq.empty
    else Seq(FieldError("FILENAME", spec.codigos.nombreArchivo, Severidad.Error.nivel, Causa.NombreArchivo))
  }

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
      case r: ReglaEntreCampos.SoloCeros =>
        val sinEspacios = FieldValidators.normalizar(v(r.campo), Seq(Normalizacion.QuitarEspacios))
        if (sinEspacios.nonEmpty && sinEspacios.forall(_ == '0'))
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
        // SILENCIO-INTENCIONAL ADR-01: la falla (p. ej. del catálogo) no se pierde: sale como el código interno de nivel 1.
        case NonFatal(_) => Seq(FieldError("REGISTRO", spec.codigos.interno, Severidad.Error.nivel, Causa.Interno))
      }
    ResultadoRegistro(errores, qualityScores(spec, errores))
  }

  /** Clave de duplicado: valores normalizados de los campos clave. Nunca devuelve un arreglo vacío (VAL-AP03).
    * ASSUMPTION VAL-SP03: si algún campo de la clave está vacío (sin tipo o sin número de documento), la clave es
    * única POR REGISTRO (`unico`), así que nunca se agrupa, ni siquiera con otra fila idéntica. Antes era única por
    * contenido de la fila (hashCode) y dos filas idénticas sin número se agrupaban (AUD-V-27). */
  def claveDuplicado(spec: SourceSpec, valores: Array[String], unico: => String): Array[Any] = {
    val partes = spec.duplicados.clave.map { nombre =>
      spec.campo(nombre).map(c => FieldValidators.normalizar(Campos.valor(valores, c.posicion), c.normalizacion))
        .getOrElse("")
    }
    if (partes.isEmpty || partes.exists(_.isEmpty)) Array[Any]("SIN_CLAVE", unico)
    else partes.toArray[Any]
  }

  /** claveDuplicado con un identificador aleatorio por llamada: el motor la llama una vez por registro
    * (Validator.dupCheckForKey) y guarda el resultado en `keys`. */
  def claveDuplicado(spec: SourceSpec, valores: Array[String]): Array[Any] =
    claveDuplicado(spec, valores, java.util.UUID.randomUUID().toString)

  /** As-of-date del registro en epoch ms; None si el campo no es una fecha válida. Total: fila nula o truncada →
    * None. Con None el motor usa la fecha de recepción y marca didCreateAsOfDateFromReceivedDate (AUD-V2). */
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
