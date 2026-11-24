// VAL-TPL-10 · tests de la fuente SISBEN (parametrizada desde source-spec sisben-v4-0.1.0).
// Generado por el skill edf-validation-stage. No editar a mano: agregar casos en el source-spec.
// Prueba la especificación + Capa A, que es exactamente la lógica que ejecuta SISBENValidationProcessor.
// ASSUMPTION VAL-SP10: no importa clases del framework Oxygen; compila y corre con Scala 2.12 + ScalaTest, sin clúster ni JAR.
// La prueba de integración del processor (ProcessorContext, lookup real) requiere el JAR (F-01).
// DATOS SINTÉTICOS: catálogos y registros inventados; DIVIPOLA no verificada contra DANE.
package com.experian.edf.oxygen.validation.source.sisben

import java.time.LocalDate

import com.experian.edf.oxygen.validation.source.common._
import com.experian.edf.oxygen.validation.source.sisben.config.SISBENSpec
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SISBENValidationProcessorSpec extends AnyFunSuite {

  private val spec = SISBENSpec.Spec
  private val hoy = LocalDate.of(2024, 6, 30)

  /** Filas sintéticas del source-spec y una consulta con la semántica de Util.getLookup del motor (VAL-PT03):
    * devuelve los valores de la columna pedida en las filas que cumplen el filtro, o List("") si no hay coincidencia. */
  private object TablasSinteticas {
    val tablas: Map[String, Seq[Map[String, String]]] = Map(
      "SISBEN_HOME_ID_CLASIFICATION_NH" -> Seq(
        Map("id_hogar" -> "1", "descripcion" -> "HOGAR 1"),
        Map("id_hogar" -> "2", "descripcion" -> "HOGAR 2"),
        Map("id_hogar" -> "3", "descripcion" -> "HOGAR 3"),
        Map("id_hogar" -> "4", "descripcion" -> "HOGAR 4"),
        Map("id_hogar" -> "5", "descripcion" -> "HOGAR 5"),
        Map("id_hogar" -> "6", "descripcion" -> "HOGAR 6"),
        Map("id_hogar" -> "7", "descripcion" -> "HOGAR 7"),
        Map("id_hogar" -> "8", "descripcion" -> "HOGAR 8"),
        Map("id_hogar" -> "9", "descripcion" -> "HOGAR 9"),
        Map("id_hogar" -> "10", "descripcion" -> "HOGAR 10"),
        Map("id_hogar" -> "11", "descripcion" -> "HOGAR 11"),
        Map("id_hogar" -> "12", "descripcion" -> "HOGAR 12"),
        Map("id_hogar" -> "13", "descripcion" -> "HOGAR 13"),
        Map("id_hogar" -> "14", "descripcion" -> "HOGAR 14"),
        Map("id_hogar" -> "15", "descripcion" -> "HOGAR 15")
      ),
      // ASSUMPTION F-13: valor supuesto en el source-spec (references/decisiones-y-abiertos.md)
      "102_DANE_DIVIPOLA_STATES_CODES_NH" -> Seq(
        Map("cod_dpto" -> "05", "nom_dpto" -> "ANTIOQUIA"),
        Map("cod_dpto" -> "11", "nom_dpto" -> "BOGOTA")
      ),
      // ASSUMPTION F-13: valor supuesto en el source-spec (references/decisiones-y-abiertos.md)
      "2_DANE_DIVIPOLA_CITIES_CODES_NH" -> Seq(
        Map("cod_mpio" -> "05001", "nom_mpio" -> "MEDELLIN", "cod_dpto" -> "05"),
        Map("cod_mpio" -> "11001", "nom_mpio" -> "BOGOTA", "cod_dpto" -> "11")
      ),
      "201_SISBEN_ZONES_NH" -> Seq(
        Map("cod_clase" -> "1", "descripcion" -> "ZONA 1"),
        Map("cod_clase" -> "2", "descripcion" -> "ZONA 2"),
        Map("cod_clase" -> "3", "descripcion" -> "ZONA 3")
      ),
      "202_SISBEN_PROCESS_ID_TYPE_HO" -> Seq(
        Map("cod_sisben" -> "1", "cod_experian" -> "10", "descripcion" -> "RC"),
        Map("cod_sisben" -> "2", "cod_experian" -> "7", "descripcion" -> "TI"),
        Map("cod_sisben" -> "3", "cod_experian" -> "1", "descripcion" -> "CC"),
        Map("cod_sisben" -> "4", "cod_experian" -> "4", "descripcion" -> "CE"),
        Map("cod_sisben" -> "5", "cod_experian" -> "8", "descripcion" -> "DNI"),
        Map("cod_sisben" -> "6", "cod_experian" -> "5", "descripcion" -> "PAS"),
        Map("cod_sisben" -> "7", "cod_experian" -> "", "descripcion" -> "SALVOCONDUCTO PENDIENTE"),
        Map("cod_sisben" -> "8", "cod_experian" -> "9", "descripcion" -> "PEP"),
        Map("cod_sisben" -> "PPT", "cod_experian" -> "11", "descripcion" -> "PPT")
      ),
      "SISBEN_GROUP_SUBGROUP_NH" -> Seq(
        Map("grupo" -> "A", "descripcion" -> "GRUPO A"),
        Map("grupo" -> "B", "descripcion" -> "GRUPO B"),
        Map("grupo" -> "C", "descripcion" -> "GRUPO C"),
        Map("grupo" -> "D", "descripcion" -> "GRUPO D")
      ),
      "203_SISBEN_RECORD_STATUS_AND_MARK_NH" -> Seq(
        Map("codigo" -> "0", "tipo" -> "AMBOS", "descripcion" -> "COD 0"),
        Map("codigo" -> "1", "tipo" -> "MARCA", "descripcion" -> "COD 1"),
        Map("codigo" -> "10", "tipo" -> "AMBOS", "descripcion" -> "COD 10"),
        Map("codigo" -> "40", "tipo" -> "ESTADO", "descripcion" -> "COD 40"),
        Map("codigo" -> "41", "tipo" -> "ESTADO", "descripcion" -> "COD 41"),
        Map("codigo" -> "42", "tipo" -> "ESTADO", "descripcion" -> "COD 42"),
        Map("codigo" -> "43", "tipo" -> "ESTADO", "descripcion" -> "COD 43"),
        Map("codigo" -> "60", "tipo" -> "ESTADO", "descripcion" -> "COD 60"),
        Map("codigo" -> "61", "tipo" -> "ESTADO", "descripcion" -> "COD 61"),
        Map("codigo" -> "70", "tipo" -> "ESTADO", "descripcion" -> "COD 70"),
        Map("codigo" -> "90", "tipo" -> "ESTADO", "descripcion" -> "COD 90"),
        Map("codigo" -> "91", "tipo" -> "ESTADO", "descripcion" -> "COD 91")
      )
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

  private val RegistroBase: Seq[String] = Seq("FICHA-SINT-0001", "1", "05", "ANTIOQUIA", "05001", "MEDELLIN", "1", "1", "900000001", "PRUEBA", "SINTETICO", "ANA", "MARIA", "3", "1000000001", "A", "3", "15/05/2024", "20/05/2024", "0", "10", "2024-06-01 00:00:00")

  private def linea(valores: Seq[String]): String = valores.mkString(";")

  private def conValor(posicion: Int, valor: String): String = linea(RegistroBase.updated(posicion - 1, valor))

  private def codigos(fila: String): Seq[String] =
    RecordValidator.validarRegistro(spec, fila, CatalogoSintetico, hoy).errores.map(_.codigo)

  private val HeaderCorrecto: String = "ID_FICHA_ORIGEN;ID_HOGAR;COD_DPTO;NOM_DPTO;COD_MPIO;NOM_MPIO;ID_CLASE;ID_PERSONA_ORDEN;ID_NACIONAL;PRI_APELLIDO;SEG_APELLIDO;PRI_NOMBRE;SEG_NOMBRE;TIP_DOCUMENTO;NUM_DOCUMENTO;ID_GRUPO;ID_NIVEL;FECHA_DIGITACION;FECHA_ACTUALIZACION_PERSONA;ID_MARCA;ID_ESTADO;FECHA_ACTUALIZACION_CNS"

  test("la especificación declara 22 campos con posiciones consecutivas") {
    assert(spec.numeroCampos == 22)
    assert(spec.campos.map(_.posicion) == (1 to 22))
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

  test("ID_FICHA_ORIGEN inválido produce SISBEN_ERR_001") {
    assert(codigos(conValor(1, "")).contains("SISBEN_ERR_001"))
  }

  test("ID_HOGAR inválido produce SISBEN_ERR_002") {
    assert(codigos(conValor(2, "16")).contains("SISBEN_ERR_002"))
  }

  test("COD_DPTO inválido produce SISBEN_ERR_003") {
    assert(codigos(conValor(3, "99")).contains("SISBEN_ERR_003"))
  }

  test("NOM_DPTO inválido produce SISBEN_ERR_004") {
    assert(codigos(conValor(4, "")).contains("SISBEN_ERR_004"))
  }

  test("COD_MPIO inválido produce SISBEN_ERR_005") {
    assert(codigos(conValor(5, "99999")).contains("SISBEN_ERR_005"))
  }

  test("NOM_MPIO inválido produce SISBEN_ERR_006") {
    assert(codigos(conValor(6, "")).contains("SISBEN_ERR_006"))
  }

  test("ID_CLASE inválido produce SISBEN_ERR_007") {
    assert(codigos(conValor(7, "4")).contains("SISBEN_ERR_007"))
  }

  test("ID_PERSONA_ORDEN inválido produce SISBEN_ERR_008") {
    assert(codigos(conValor(8, "26")).contains("SISBEN_ERR_008"))
  }

  test("ID_NACIONAL inválido produce SISBEN_ERR_009") {
    assert(codigos(conValor(9, "ABC")).contains("SISBEN_ERR_009"))
  }

  test("PRI_APELLIDO inválido produce SISBEN_ERR_010") {
    assert(codigos(conValor(10, "")).contains("SISBEN_ERR_010"))
  }

  test("SEG_APELLIDO inválido produce SISBEN_INC_011") {
    assert(codigos(conValor(11, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")).contains("SISBEN_INC_011"))
  }

  test("PRI_NOMBRE inválido produce SISBEN_ERR_012") {
    assert(codigos(conValor(12, "")).contains("SISBEN_ERR_012"))
  }

  test("SEG_NOMBRE inválido produce SISBEN_INC_013") {
    assert(codigos(conValor(13, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")).contains("SISBEN_INC_013"))
  }

  test("TIP_DOCUMENTO inválido produce SISBEN_ERR_014") {
    assert(codigos(conValor(14, "9")).contains("SISBEN_ERR_014"))
  }

  test("NUM_DOCUMENTO inválido produce SISBEN_ERR_015") {
    assert(codigos(conValor(15, "")).contains("SISBEN_ERR_015"))
  }

  test("ID_GRUPO inválido produce SISBEN_ERR_016") {
    assert(codigos(conValor(16, "E")).contains("SISBEN_ERR_016"))
  }

  test("ID_NIVEL inválido produce SISBEN_ERR_017") {
    assert(codigos(conValor(17, "22")).contains("SISBEN_ERR_017"))
  }

  test("FECHA_DIGITACION inválido produce SISBEN_ERR_018") {
    assert(codigos(conValor(18, "2099-01-01 00:00:00")).contains("SISBEN_ERR_018"))
  }

  test("FECHA_ACTUALIZACION_PERSONA inválido produce SISBEN_INC_019") {
    assert(codigos(conValor(19, "2024-05-20")).contains("SISBEN_INC_019"))
  }

  test("ID_MARCA inválido produce SISBEN_INC_020") {
    assert(codigos(conValor(20, "5")).contains("SISBEN_INC_020"))
  }

  test("ID_ESTADO inválido produce SISBEN_ERR_021") {
    assert(codigos(conValor(21, "50")).contains("SISBEN_ERR_021"))
  }

  test("FECHA_ACTUALIZACION_CNS inválido produce SISBEN_ERR_022") {
    assert(codigos(conValor(22, "31/02/2024")).contains("SISBEN_ERR_022"))
  }

  test("línea truncada, vacía o con campos de más no lanza excepción") {
    assert(codigos(linea(RegistroBase.take(3))) == Seq(spec.codigos.estructura))
    assert(codigos(linea(RegistroBase :+ "EXTRA")) == Seq(spec.codigos.estructura))
    assert(codigos("") == Seq(spec.codigos.estructura))
    assert(codigos(null) == Seq(spec.codigos.estructura))
  }

  test("filtro y header con filas degeneradas no lanzan (AUD-V1)") {
    for (fila <- Seq(null, "", "   ", ";", linea(RegistroBase.take(1)))) {
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
    val cols = HeaderCorrecto.split(java.util.regex.Pattern.quote(";"), -1)
    assert(RecordValidator.validarHeader(spec, Seq(cols.updated(1, "OTRA").mkString(";"))).map(_.codigo) == Seq(spec.codigos.header))
    assert(RecordValidator.validarHeader(spec, Seq(cols.dropRight(1).mkString(";"))).map(_.codigo) == Seq(spec.codigos.header))
    assert(RecordValidator.validarHeader(spec, Seq.empty).map(_.codigo) == Seq(spec.codigos.header))
  }

  test("nombre de archivo del fixture es válido y uno distinto no") {
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/SISBEN_EXPERIAN_202406.20240630120000-0500.txt").isEmpty)
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/OTRO_ARCHIVO.txt").map(_.codigo) == Seq(spec.codigos.nombreArchivo))
  }

  test("la clave de duplicado nunca es vacía") {
    assert(RecordValidator.claveDuplicado(spec, Campos.separar(linea(RegistroBase), ";")).nonEmpty)
    assert(RecordValidator.claveDuplicado(spec, Campos.separar("", ";")).nonEmpty)
  }

  // Casos del fixture (filtro de filas, reglas entre campos, duplicados), declarados en el source-spec.
  test("fixture BASE: registro válido") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000001;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000001;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00") == Seq())
  }

  test("fixture MENOR_RC: menor con registro civil: se filtra") {
    assert(RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;1;1000000002;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
  }

  test("fixture MENOR_TI: menor con tarjeta de identidad: se filtra") {
    assert(RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;2;1000000003;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
  }

  test("fixture LINEA_VACIA: línea vacía: se filtra") {
    assert(RecordValidator.debeFiltrar(spec, ""))
  }

  test("fixture SALVOCONDUCTO: tipo 7 sin homologación") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;7;1000000004;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;7;1000000004;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00") == Seq("SISBEN_ERR_023"))
  }

  test("fixture DPTO_NO_CORRESPONDE: nombre de departamento no corresponde") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;BOGOTA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000005;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
    assert(codigos("FICHA-SINT-0001;1;05;BOGOTA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000005;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00") == Seq("SISBEN_ERR_004"))
  }

  test("fixture MPIO_NO_CORRESPONDE: nombre de municipio no corresponde") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;BOGOTA;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000006;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;BOGOTA;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000006;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00") == Seq("SISBEN_ERR_006"))
  }

  test("fixture FECHA_FUTURA: fecha de digitación futura") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000007;A;3;01/01/2099;20/05/2024;0;10;2024-06-01 00:00:00"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000007;A;3;01/01/2099;20/05/2024;0;10;2024-06-01 00:00:00") == Seq("SISBEN_ERR_018"))
  }

  test("fixture TRUNCADA: fila con 10 columnas") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA") == Seq("SISBEN_ERR_026"))
  }

  test("fixture COLUMNA_EXTRA: fila con 23 columnas") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000009;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00;EXTRA"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000009;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00;EXTRA") == Seq("SISBEN_ERR_026"))
  }

  test("fixture PPT: permiso de protección temporal válido") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;PPT;1000000010;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;PPT;1000000010;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00") == Seq())
  }

  test("fixture NORMALIZACION: documento con espacios y ceros a la izquierda es válido") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3; 000 1000000011;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3; 000 1000000011;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00") == Seq())
  }

  test("fixture SOLO_INCONSISTENCIA: segundo apellido demasiado largo: severidad 3, RQS 1") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX;ANA;MARIA;3;1000000012;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX;ANA;MARIA;3;1000000012;A;3;15/05/2024;20/05/2024;0;10;2024-06-01 00:00:00") == Seq("SISBEN_INC_011"))
  }

  test("fixture DUP_ANTIGUO: duplicado más antiguo: se marca") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000099;A;3;15/05/2024;20/05/2024;0;10;01/01/2024"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000099;A;3;15/05/2024;20/05/2024;0;10;01/01/2024") == Seq())
  }

  test("fixture DUP_RECIENTE: duplicado más reciente: se conserva") {
    assert(!RecordValidator.debeFiltrar(spec, "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000099;A;3;15/05/2024;20/05/2024;0;10;01/06/2024"))
    assert(codigos("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000099;A;3;15/05/2024;20/05/2024;0;10;01/06/2024") == Seq())
  }

  test("fixture duplicados: misma clave y se conserva el más reciente (ASSUMPTION F-14)") {
    val grupo = Seq("FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000099;A;3;15/05/2024;20/05/2024;0;10;01/01/2024", "FICHA-SINT-0001;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;900000001;PRUEBA;SINTETICO;ANA;MARIA;3;1000000099;A;3;15/05/2024;20/05/2024;0;10;01/06/2024")
    val claves = grupo.map(l => RecordValidator.claveDuplicado(spec, Campos.separar(l, ";")).toSeq)
    assert(claves.distinct.size == 1)
    val fechas = grupo.map(l => RecordValidator.asOfDate(spec, l).getOrElse(0L))
    val candidatos = fechas.zipWithIndex.map { case (f, i) => (f, "%05d".format(i)) }
    assert(RecordValidator.indiceAConservar(candidatos) == 1)
  }
}
