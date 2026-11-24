// VAL-TPL-09 · Capa A · plantilla fija (solo se rellena el paquete).
// Generado por el skill edf-validation-stage. Tests puros: corren sin clúster y sin el JAR de Oxygen.
// DATOS SINTÉTICOS: ningún valor proviene de una fuente real.
package {{paquete_comun}}

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
  }

  test("valores nulos no lanzan excepción") {
    assert(causa(campo(), null).contains(Causa.Obligatorio))
  }

  test("desempate de duplicados: mayor fecha y luego mayor desempate") {
    assert(RecordValidator.indiceAConservar(Seq((10L, "b"), (20L, "a"), (20L, "c"))) == 2)
    assert(RecordValidator.indiceAConservar(Seq.empty) == -1)
  }
}
