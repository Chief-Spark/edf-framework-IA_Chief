// VAL-TPL-10 · tests de la fuente {{Fuente}} (parametrizada desde source-spec {{spec_version}}).
// Generado por el skill edf-validation-stage. No editar a mano: agregar casos en el source-spec.
// Prueba la especificación + Capa A, que es exactamente la lógica que ejecuta {{Fuente}}ValidationProcessor.
// ASSUMPTION VAL-SP10: no importa clases del framework Oxygen; compila y corre con Scala 2.12 + ScalaTest, sin clúster ni JAR.
// La prueba de integración del processor (ProcessorContext, lookup real) requiere el JAR (F-01).
// DATOS SINTÉTICOS: catálogos y registros inventados; DIVIPOLA no verificada contra DANE.
package {{paquete_fuente}}

import java.time.LocalDate

import {{paquete_comun}}._
import {{paquete_fuente}}.config.{{Fuente}}Spec
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class {{Fuente}}ValidationProcessorSpec extends AnyFunSuite {

  private val spec = {{Fuente}}Spec.Spec
  private val hoy = {{fecha_referencia_scala}}

  /** Filas sintéticas del source-spec y una consulta con la semántica de Util.getLookup del motor (VAL-PT03):
    * devuelve los valores de la columna pedida en las filas que cumplen el filtro, o List("") si no hay coincidencia. */
  private object TablasSinteticas {
    val tablas: Map[String, Seq[Map[String, String]]] = Map(
{{bloque_catalogo_sintetico}}
    )
    def consultar(tabla: String, filtro: Map[String, String], columna: String): Seq[String] = {
      val valores = tablas.getOrElse(tabla, Seq.empty)
        .filter(fila => filtro.forall { case (k, v) => fila.get(k).contains(v) })
        .flatMap(_.get(columna))
      if (valores.isEmpty) List("") else valores
    }
  }

  /** Catálogo de las pruebas sobre CatalogoDeConsulta: el mismo adaptador que OxygenCatalogo usa con el lookup
    * real, así que estas pruebas también ejercitan su lectura de la respuesta ("" = sin coincidencia) (AUD-V5). */
  private val CatalogoSintetico: Catalogo = new CatalogoDeConsulta(TablasSinteticas.consultar _)

  private val RegistroBase: Seq[String] = Seq({{registro_base_scala}})

  private def linea(valores: Seq[String]): String = valores.mkString("{{delimitador_scala}}")

  private def conValor(posicion: Int, valor: String): String = linea(RegistroBase.updated(posicion - 1, valor))

  private def codigos(fila: String): Seq[String] =
    RecordValidator.validarRegistro(spec, fila, CatalogoSintetico, hoy).errores.map(_.codigo)

  private val HeaderCorrecto: String = "{{header_linea_scala}}"

  test("la especificación declara {{numero_campos}} campos con posiciones consecutivas") {
    assert(spec.numeroCampos == {{numero_campos}})
    assert(spec.campos.map(_.posicion) == (1 to {{numero_campos}}))
  }

  test("los códigos de error tienen longitud fija, no son subcadena de otro y no miden 11") {
    val todos = (spec.campos.map(_.codigoError) ++ spec.reglas.map(_.codigo) ++
      Seq(spec.codigos.nombreArchivo, spec.codigos.header, spec.codigos.estructura, spec.codigos.interno)).distinct
    assert(todos.map(_.length).distinct.size == 1)
    assert(!todos.exists(_.length == 11))
    for (a <- todos; b <- todos if a != b) assert(!b.contains(a), s"$a es subcadena de $b")
  }

  test("el registro base es válido") {
    assert(codigos(linea(RegistroBase)).isEmpty)
    assert(RecordValidator.validarRegistro(spec, linea(RegistroBase), CatalogoSintetico, hoy).qualityScores.forall(_._2 == 1.0))
  }

{{bloque_casos_campo}}

  test("línea truncada, vacía o con campos de más no lanza excepción") {
    assert(codigos(linea(RegistroBase.take(3))) == Seq(spec.codigos.estructura))
    assert(codigos(linea(RegistroBase :+ "EXTRA")) == Seq(spec.codigos.estructura))
    assert(codigos("") == Seq(spec.codigos.estructura))
    assert(codigos(null) == Seq(spec.codigos.estructura))
  }

  test("filtro y header con filas degeneradas no lanzan (AUD-V1)") {
    for (fila <- Seq(null, "", "   ", "{{delimitador_scala}}", linea(RegistroBase.take(1)))) {
      assert(!RecordValidator.esHeader(spec, fila))
      RecordValidator.debeFiltrar(spec, fila)
    }
    assert(RecordValidator.debeFiltrar(spec, null) == spec.filtro.lineasVacias)
    assert(RecordValidator.debeFiltrar(spec, "") == spec.filtro.lineasVacias)
    spec.filtro.posicionCampo.filter(_ > 1).foreach { pos =>
      assert(!RecordValidator.debeFiltrar(spec, linea(RegistroBase.take(pos - 1))), "fila truncada sin el campo del filtro")
    }
    assert(!RecordValidator.debeFiltrar(spec, linea(RegistroBase)))
  }

  test("catálogo sintético sobre CatalogoDeConsulta: List(\"\") es sin coincidencia (AUD-V5)") {
    assert(TablasSinteticas.consultar("TABLA_INEXISTENTE_SINTETICA", Map("c" -> "v"), "c") == List(""))
    for ((tabla, filas) <- TablasSinteticas.tablas; fila <- filas.headOption; (columna, valor) <- fila.headOption) {
      assert(CatalogoSintetico.existe(tabla, columna, valor))
      assert(!CatalogoSintetico.existe(tabla, columna, "VALOR_INEXISTENTE_SINTETICO"))
      assert(CatalogoSintetico.buscar(tabla, columna, "VALOR_INEXISTENTE_SINTETICO", columna).isEmpty)
    }
  }

  test("header correcto se acepta y se reconoce como fila de header") {
    assert(RecordValidator.validarHeader(spec, Seq(HeaderCorrecto)).isEmpty)
    assert(RecordValidator.esHeader(spec, HeaderCorrecto))
    assert(!RecordValidator.esHeader(spec, linea(RegistroBase)))
  }

  test("header con una columna distinta, con columnas de menos o vacío produce el código de header") {
    val cols = HeaderCorrecto.split(java.util.regex.Pattern.quote("{{delimitador_scala}}"), -1)
    assert(RecordValidator.validarHeader(spec, Seq(cols.updated(1, "OTRA").mkString("{{delimitador_scala}}"))).map(_.codigo) == Seq(spec.codigos.header))
    assert(RecordValidator.validarHeader(spec, Seq(cols.dropRight(1).mkString("{{delimitador_scala}}"))).map(_.codigo) == Seq(spec.codigos.header))
    assert(RecordValidator.validarHeader(spec, Seq.empty).map(_.codigo) == Seq(spec.codigos.header))
  }

  test("nombre de archivo del fixture es válido y uno distinto no") {
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/{{fixture_nombre_archivo}}").isEmpty)
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/OTRO_ARCHIVO.txt").map(_.codigo) == Seq(spec.codigos.nombreArchivo))
  }

  test("la clave de duplicado nunca es vacía") {
    assert(RecordValidator.claveDuplicado(spec, Campos.separar(linea(RegistroBase), "{{delimitador_scala}}")).nonEmpty)
    assert(RecordValidator.claveDuplicado(spec, Campos.separar("", "{{delimitador_scala}}")).nonEmpty)
  }

  // Casos del fixture (filtro de filas, reglas entre campos, duplicados), declarados en el source-spec.
{{bloque_casos_fixture}}
}
