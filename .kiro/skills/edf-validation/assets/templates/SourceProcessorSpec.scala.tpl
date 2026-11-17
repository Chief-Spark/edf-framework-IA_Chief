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

  /** Catálogo en memoria con las filas sintéticas del source-spec. */
  private object CatalogoSintetico extends Catalogo {
    private val tablas: Map[String, Seq[Map[String, String]]] = Map(
{{bloque_catalogo_sintetico}}
    )
    def existe(tabla: String, columna: String, valor: String): Boolean =
      tablas.getOrElse(tabla, Seq.empty).exists(_.get(columna).contains(valor))
    def buscar(tabla: String, columnaClave: String, clave: String, columnaSalida: String): Option[String] =
      tablas.getOrElse(tabla, Seq.empty).find(_.get(columnaClave).contains(clave)).flatMap(_.get(columnaSalida))
  }

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
