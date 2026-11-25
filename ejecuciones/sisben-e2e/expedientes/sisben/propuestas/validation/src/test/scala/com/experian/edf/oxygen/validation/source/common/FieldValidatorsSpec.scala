// VAL-TPL-09 · Capa A · plantilla fija (solo se rellena el paquete).
// Generado por la skill edf-validation. Tests puros: corren sin clúster y sin el JAR de Oxygen.
// DATOS SINTÉTICOS: ningún valor proviene de una fuente real.
package com.experian.edf.oxygen.validation.source.common

import java.time.LocalDate

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class FieldValidatorsSpec extends AnyFunSuite {

  private val hoy = LocalDate.of(2024, 6, 30)

  private object CatalogoVacio extends Catalogo {
    def existe(tabla: String, columna: String, valor: String): Boolean = false
    def buscar(tabla: String, columnaClave: String, clave: String, columnaSalida: String): Option[String] = None
  }

  private object CatalogoQueFalla extends Catalogo {
    def existe(tabla: String, columna: String, valor: String): Boolean = throw new IllegalStateException("sintético")
    def buscar(tabla: String, columnaClave: String, clave: String, columnaSalida: String): Option[String] =
      throw new IllegalStateException("sintético")
  }

  /** Acepta todo valor, pero la búsqueda de la regla entre campos lanza (ADR-01). */
  private object CatalogoQueFallaAlBuscar extends Catalogo {
    def existe(tabla: String, columna: String, valor: String): Boolean = true
    def buscar(tabla: String, columnaClave: String, clave: String, columnaSalida: String): Option[String] =
      throw new IllegalStateException("sintético")
  }

  private def campo(
    tipo: TipoDato = TipoDato.Alfanumerico,
    obligatorio: Boolean = true,
    longitudMax: Option[Int] = None,
    rango: Option[RangoEntero] = None,
    dominio: Option[Set[String]] = None,
    catalogo: Option[CatalogoRef] = None,
    formatos: Seq[String] = Seq.empty,
    noFutura: Boolean = false,
    normalizacion: Seq[Normalizacion] = Seq.empty
  ): FieldSpec = FieldSpec("CAMPO_X", 1, Seq("CAMPO_X"), obligatorio,
    if (obligatorio) Severidad.Error else Severidad.Inconsistencia, tipo, longitudMax, rango, dominio, catalogo,
    formatos, noFutura, normalizacion, "TEST_ERR_0001")

  private def causa(spec: FieldSpec, v: String, cat: Catalogo = CatalogoVacio): Option[String] =
    FieldValidators.validar(spec, v, cat, hoy).map(_.causa)

  /** Fuente genérica mínima para probar RecordValidator.validarRegistro (sin datos de una fuente real). */
  private def fuente(campos: Seq[FieldSpec], reglas: Seq[ReglaEntreCampos] = Seq.empty): SourceSpec = SourceSpec(
    "GENERICA", campos, ";", 0, NombreArchivoSpec("^(X)([0-9]{6})([0-9]{14})([+-][0-9]{4})$", 1, 2, 3, 4, Map.empty),
    "000000", "SINTETICO", 30, "America/Bogota", reglas, FiltroFilas(lineasVacias = false, None, Set.empty),
    Seq(QualityScoreSpec("CALIDAD", Set("1"))), DuplicadosSpec(habilitado = false, Seq.empty, None),
    "CAMPO_X", "CAMPO_X", CodigosEspeciales("TEST_ERR_0091", "TEST_ERR_0092", "TEST_ERR_0098", "TEST_ERR_0099"))

  test("obligatorio vacío produce OBLIGATORIO con nivel 1") {
    val e = FieldValidators.validar(campo(), "  ", CatalogoVacio, hoy)
    assert(e.map(_.causa).contains(Causa.Obligatorio))
    assert(e.map(_.nivel).contains("1"))
  }

  test("opcional vacío no produce error") {
    assert(causa(campo(obligatorio = false), "").isEmpty)
  }

  test("numérico con letras produce TIPO") {
    assert(causa(campo(tipo = TipoDato.Numerico), "12A").contains(Causa.Tipo))
  }

  test("longitud excedida produce LONGITUD") {
    assert(causa(campo(longitudMax = Some(2)), "ABC").contains(Causa.Longitud))
  }

  test("rango fuera de límites produce RANGO") {
    assert(causa(campo(tipo = TipoDato.Numerico, rango = Some(RangoEntero(1, 15))), "16").contains(Causa.Rango))
    assert(causa(campo(tipo = TipoDato.Numerico, rango = Some(RangoEntero(1, 15))), "15").isEmpty)
  }

  test("dominio compara sin mayúsculas") {
    assert(causa(campo(dominio = Some(Set("PPT"))), "ppt").isEmpty)
    assert(causa(campo(dominio = Some(Set("A", "B"))), "Z").contains(Causa.Dominio))
  }

  test("fecha con formato inválido produce FORMATO_FECHA") {
    val f = campo(tipo = TipoDato.Fecha, formatos = Seq("dd/MM/yyyy"))
    assert(causa(f, "31/02/2024").contains(Causa.FormatoFecha))
    assert(causa(f, "1/2/2024").contains(Causa.FormatoFecha))
    assert(causa(f, "01/02/2024").isEmpty)
  }

  test("fecha futura produce FECHA_FUTURA") {
    val f = campo(tipo = TipoDato.Fecha, formatos = Seq("dd/MM/yyyy", "yyyy-MM-dd HH:mm:ss"), noFutura = true)
    assert(causa(f, "2024-07-01 00:00:00").contains(Causa.FechaFutura))
    assert(causa(f, "30/06/2024").isEmpty)
  }

  test("catálogo inexistente produce CATALOGO") {
    val f = campo(catalogo = Some(CatalogoRef("TABLA_SINTETICA", "codigo")))
    assert(causa(f, "1").contains(Causa.Catalogo))
  }

  test("catálogo que lanza produce el código interno de nivel 1 en validarRegistro, sin excepción (ADR-01)") {
    // Campo opcional (severidad 3): antes quedaba CATALOGO con nivel 3 y el registro con calidad 1.0.
    val opcional = campo(obligatorio = false, catalogo = Some(CatalogoRef("TABLA_SINTETICA", "codigo")))
    intercept[IllegalStateException](FieldValidators.validar(opcional, "1", CatalogoQueFalla, hoy))
    val interno = Seq(FieldError("REGISTRO", "TEST_ERR_0099", "1", Causa.Interno))
    val r = RecordValidator.validarRegistro(fuente(Seq(opcional)), "1", CatalogoQueFalla, hoy)
    assert(r.errores == interno)
    assert(r.qualityScores == Seq("CALIDAD" -> 0.0))
    // Regla CorrespondenciaCatalogo cuya búsqueda lanza: mismo tratamiento.
    val nombre = campo().copy(nombre = "CAMPO_N", posicion = 2, aliasHeader = Seq("CAMPO_N"), codigoError = "TEST_ERR_0002")
    val regla = ReglaEntreCampos.CorrespondenciaCatalogo("RV-01", "CAMPO_X", "CAMPO_N", "TABLA_SINTETICA", "codigo",
      "nombre", "TEST_ERR_0003", Severidad.Error)
    val conRegla = fuente(Seq(opcional, nombre), Seq(regla))
    assert(RecordValidator.validarRegistro(conRegla, "1;NOMBRE", CatalogoQueFallaAlBuscar, hoy).errores == interno)
  }

  test("CatalogoDeConsulta lee la respuesta del lookup (\"\" = sin coincidencia)") {
    val cat = new CatalogoDeConsulta((_, filtro, _) => if (filtro.values.toSeq == Seq("1")) List("UNO") else List(""))
    assert(cat.existe("TABLA_SINTETICA", "codigo", "1"))
    assert(!cat.existe("TABLA_SINTETICA", "codigo", "2"))
    assert(cat.buscar("TABLA_SINTETICA", "codigo", "1", "nombre").contains("UNO"))
    assert(cat.buscar("TABLA_SINTETICA", "codigo", "2", "nombre").isEmpty)
  }

  test("CatalogoDeConsulta no oculta la falla del lookup: el registro sale con el código interno (ADR-01)") {
    val caido = new CatalogoDeConsulta((_, _, _) => throw new IllegalStateException("lookup no disponible (sintético)"))
    intercept[IllegalStateException](caido.existe("TABLA_SINTETICA", "codigo", "1"))
    val opcional = campo(obligatorio = false, catalogo = Some(CatalogoRef("TABLA_SINTETICA", "codigo")))
    val r = RecordValidator.validarRegistro(fuente(Seq(opcional)), "1", caido, hoy)
    assert(r.errores == Seq(FieldError("REGISTRO", "TEST_ERR_0099", "1", Causa.Interno)))
  }

  test("normalización en memoria quita espacios y ceros a la izquierda") {
    assert(FieldValidators.normalizar(" 00 12 3 ", Seq(Normalizacion.QuitarEspacios, Normalizacion.QuitarCerosIzquierda)) == "123")
    assert(FieldValidators.normalizar("000", Seq(Normalizacion.QuitarCerosIzquierda)) == "0")
  }

  test("acceso por posición fuera de rango devuelve vacío") {
    assert(Campos.valor(Campos.separar("a;b", ";"), 5) == "")
    assert(Campos.valor(Campos.separar(null, ";"), 1) == "")
    assert(Campos.separar("a;b", null).toSeq == Seq("a;b"))
    assert(Campos.separar(null, "").toSeq == Seq(""))
  }

  test("valores nulos no lanzan excepción") {
    assert(causa(campo(), null).contains(Causa.Obligatorio))
  }

  test("debeFiltrar y esHeader son totales: fila nula, vacía, truncada o sin el campo no lanzan (AUD-V1)") {
    val conFiltro = fuente(Seq(campo(), campo().copy(nombre = "CAMPO_N", posicion = 2, aliasHeader = Seq("CAMPO_N"))))
      .copy(filtro = FiltroFilas(lineasVacias = true, Some(2), Set("MENOR")))
    assert(RecordValidator.debeFiltrar(conFiltro, null))
    assert(RecordValidator.debeFiltrar(conFiltro, "   "))
    assert(RecordValidator.debeFiltrar(conFiltro, "1;MENOR"))
    assert(!RecordValidator.debeFiltrar(conFiltro, "1"))       // truncada: sin el campo del filtro
    assert(!RecordValidator.debeFiltrar(conFiltro, "1;OTRO"))
    assert(!RecordValidator.debeFiltrar(conFiltro.copy(filtro = FiltroFilas(lineasVacias = false, Some(0), Set(""))), "1;2"))
    assert(!RecordValidator.debeFiltrar(conFiltro.copy(filtro = FiltroFilas(lineasVacias = false, None, Set.empty)), null))
    assert(!RecordValidator.debeFiltrar(conFiltro.copy(delimitador = null), "1;MENOR"))
    assert(RecordValidator.esHeader(conFiltro, "\uFEFFcampo_x;CAMPO_N"))
    for (fila <- Seq(null, "", ";", "1")) assert(!RecordValidator.esHeader(conFiltro, fila))
    assert(!RecordValidator.esHeader(conFiltro.copy(delimitador = ""), "1;2"))
  }

  test("asOfDate es total: fecha inválida, fila nula o truncada dan None (AUD-V2)") {
    val f = campo(tipo = TipoDato.Fecha, formatos = Seq("dd/MM/yyyy"))
    val s = fuente(Seq(f))
    assert(RecordValidator.asOfDate(s, "30/06/2024").isDefined)
    for (fila <- Seq(null, "", "31/02/2024", "x")) assert(RecordValidator.asOfDate(s, fila).isEmpty)
    assert(RecordValidator.asOfDate(s.copy(zonaHoraria = "Zona/Inexistente"), "30/06/2024").isEmpty)
  }

  test("validarFooter: cantidad exacta de filas y todas reconocidas; si no, el código de footer (AUD-V3)") {
    val esFooter = (r: String) => Option(r).exists(_.trim.startsWith("TOTAL"))
    val error = Seq(FieldError("FOOTER", "TEST_ERR_0097", "1", Causa.Footer))
    assert(RecordValidator.validarFooter(Seq("TOTAL;1"), 1, esFooter, "TEST_ERR_0097").isEmpty)
    assert(RecordValidator.validarFooter(Seq("1;2"), 1, esFooter, "TEST_ERR_0097") == error)
    assert(RecordValidator.validarFooter(Seq.empty, 1, esFooter, "TEST_ERR_0097") == error)
    assert(RecordValidator.validarFooter(null, 1, esFooter, "TEST_ERR_0097") == error)
    assert(RecordValidator.validarFooter(Seq("TOTAL;1", "TOTAL;2"), 1, esFooter, "TEST_ERR_0097") == error)
  }

  test("SoloCeros evalúa el valor sin espacios: 16 ceros y \"00 00\" emiten el código; vacío o con otro dígito no (AUD-V-03)") {
    val regla = ReglaEntreCampos.SoloCeros("RV-00", "CAMPO_X", "TEST_ERR_0029", Severidad.Error, "DOCUMENTO_CERO")
    val s = fuente(Seq(campo(obligatorio = false)), Seq(regla))
    val error = Seq(FieldError("CAMPO_X", "TEST_ERR_0029", "1", "DOCUMENTO_CERO"))
    for (v <- Seq("0", "0000000000000000", "00 00", " 0000 ")) assert(RecordValidator.validarRegistro(s, v, CatalogoVacio, hoy).errores == error, v)
    for (v <- Seq("", "   ", "0001", "10", "0O")) assert(RecordValidator.validarRegistro(s, v, CatalogoVacio, hoy).errores.isEmpty, v)
  }

  test("ValorConCodigoPropio sigue comparando contra la lista literal (sin la normalización del campo)") {
    val regla = ReglaEntreCampos.ValorConCodigoPropio("RV-01", "CAMPO_X", Set("0000"), "TEST_ERR_0029", Severidad.Error, "DOCUMENTO_CERO")
    val s = fuente(Seq(campo(obligatorio = false)), Seq(regla))
    assert(RecordValidator.validarRegistro(s, " 0000", CatalogoVacio, hoy).errores.map(_.codigo) == Seq("TEST_ERR_0029"))
    assert(RecordValidator.validarRegistro(s, "00000", CatalogoVacio, hoy).errores.isEmpty)
  }

  test("Campos.crudo conserva los espacios del valor recibido; Campos.valor los quita (AUD-V-05)") {
    val valores = Campos.separar("a; 0001000000101 ;b", ";")
    assert(Campos.crudo(valores, 2) == " 0001000000101 ")
    assert(Campos.valor(valores, 2) == "0001000000101")
    assert(Campos.crudo(valores, 9) == "")
  }

  test("Campos.separar quita solo el \\r final de una línea CRLF; los demás espacios se conservan (AUD-V-29)") {
    val crlf = Campos.separar("a; 0001 ;10/01/2026\r", ";")
    assert(crlf.toSeq == Seq("a", " 0001 ", "10/01/2026"))
    assert(Campos.separar("a;b \r", ";").toSeq == Seq("a", "b "))
    assert(Campos.separar("a;b\r\r", ";").toSeq == Seq("a", "b\r"))
    assert(Campos.separar("a;\rb", ";").toSeq == Seq("a", "\rb"))
    assert(Campos.separar("a;b", ";").toSeq == Seq("a", "b"))
  }

  test("claveDuplicado: sin un campo de la clave es única por registro aunque la fila sea idéntica (VAL-SP03, AUD-V-27)") {
    val clave = Seq(campo().copy(nombre = "TIPO", posicion = 1, aliasHeader = Seq("TIPO")),
      campo().copy(nombre = "NUMERO", posicion = 2, aliasHeader = Seq("NUMERO")))
    val s = fuente(clave).copy(duplicados = DuplicadosSpec(habilitado = true, Seq("TIPO", "NUMERO"), None))
    def k(fila: String): String = RecordValidator.claveDuplicado(s, Campos.separar(fila, ";")).mkString(":")
    assert(k("1;123") == k("1;123"))
    for (fila <- Seq("1;", ";123", ";", "1; ")) assert(k(fila) != k(fila), fila)
    assert(RecordValidator.claveDuplicado(s, Campos.separar("1;", ";"), "R1").toSeq == Seq("SIN_CLAVE", "R1"))
  }

  test("cada regla de fecha del nombre se prueba aislada: el nombre viola solo esa regla (AUD-V-28)") {
    val ahora = java.time.ZonedDateTime.of(2024, 6, 30, 23, 59, 59, 0, java.time.ZoneId.of("America/Bogota")).toInstant
    val base = fuente(Seq(campo()))
    val todas = base.copy(nombreArchivo = base.nombreArchivo.copy(periodoNoFuturo = true, recepcionNoFutura = true,
      recepcionDesdeInicioPeriodo = true))
    val n = todas.nombreArchivo
    val casos = Seq(
      // RA-02: periodo 2024-07 futuro en Bogotá; recibido 2024-07-01 00:00 +14:00 = 2024-06-30 10:00Z (no futuro) y
      // no anterior al 1.º del periodo. El offset adelantado aísla la regla.
      "X20240720240701000000+1400" -> todas.copy(nombreArchivo = n.copy(periodoNoFuturo = false)),
      "X20240620240703120000-0500" -> todas.copy(nombreArchivo = n.copy(recepcionNoFutura = false)), // RA-03
      "X20240620240531120000-0500" -> todas.copy(nombreArchivo = n.copy(recepcionDesdeInicioPeriodo = false))) // RA-04
    for ((nombre, sinLaRegla) <- casos) {
      assert(RecordValidator.validarNombre(todas, nombre, ahora).map(_.codigo) == Seq("TEST_ERR_0091"), nombre)
      assert(RecordValidator.validarNombre(sinLaRegla, nombre, ahora).isEmpty, nombre)
    }
  }

  test("reglas de fecha del nombre: apagadas no cambian nada; encendidas rechazan periodo futuro y recepción fuera de rango (AUD-V-02)") {
    val ahora = java.time.ZonedDateTime.of(2024, 6, 30, 23, 59, 59, 0, java.time.ZoneId.of("America/Bogota")).toInstant
    val base = fuente(Seq(campo()))
    val reglas = base.copy(nombreArchivo = base.nombreArchivo.copy(periodoNoFuturo = true, recepcionNoFutura = true,
      recepcionDesdeInicioPeriodo = true))
    val codigo = Seq("TEST_ERR_0091")
    val valido = "X20240620240630120000-0500"
    val invalidos = Seq(
      "X20990120240630120000-0500", // periodo futuro
      "X20240620240703120000-0500", // recepción futura
      "X20240620240531120000-0500") // recepción anterior al primer día del periodo
    for (n <- valido +: invalidos) assert(RecordValidator.validarNombre(base, n, ahora).isEmpty, n)
    assert(RecordValidator.validarNombre(reglas, valido, ahora).isEmpty)
    for (n <- invalidos) assert(RecordValidator.validarNombre(reglas, n, ahora).map(_.codigo) == codigo, n)
    assert(RecordValidator.validarNombre(base, "X20240620240630120000-2500", ahora).map(_.codigo) == codigo) // offset fuera de ±18:00
    val zonaMala = reglas.copy(zonaHoraria = "Zona/Inexistente")
    assert(RecordValidator.validarNombre(zonaMala, valido, ahora).map(_.codigo) == codigo)
  }

  test("desempate de duplicados: mayor fecha y luego mayor desempate") {
    assert(RecordValidator.indiceAConservar(Seq((10L, "b"), (20L, "a"), (20L, "c"))) == 2)
    assert(RecordValidator.indiceAConservar(Seq.empty) == -1)
  }
}
