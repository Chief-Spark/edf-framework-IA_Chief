// VAL-TPL-10 · tests de la fuente SISBEN (parametrizada desde source-spec sisben-iv-1.0.0).
// Generado por la skill edf-validation. No editar a mano: agregar casos en el source-spec.
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
  private val hoy = LocalDate.of(2026, 2, 5)
  /** Reloj de las pruebas del nombre: fin del día de referencia en la zona de la fuente (igual que --hoy en
    * prevalidar_archivo.py, AUD-V-02). */
  private val ahora = hoy.atTime(23, 59, 59).atZone(java.time.ZoneId.of(spec.zonaHoraria)).toInstant

  /** Filas sintéticas del source-spec y una consulta con la semántica de Util.getLookup del motor (VAL-PT03):
    * devuelve los valores de la columna pedida en las filas que cumplen el filtro, o List("") si no hay coincidencia. */
  private object TablasSinteticas {
    val tablas: Map[String, Seq[Map[String, String]]] = Map(
      "102_DANE_DIVIPOLA_STATES_CODES_NH" -> Seq(
        Map("cod_dpto" -> "05", "nom_dpto" -> "ANTIOQUIA"),
        Map("cod_dpto" -> "08", "nom_dpto" -> "ATLANTICO"),
        Map("cod_dpto" -> "11", "nom_dpto" -> "BOGOTA D.C."),
        Map("cod_dpto" -> "13", "nom_dpto" -> "BOLIVAR"),
        Map("cod_dpto" -> "68", "nom_dpto" -> "SANTANDER"),
        Map("cod_dpto" -> "76", "nom_dpto" -> "VALLE DEL CAUCA")
      ),
      "2_DANE_DIVIPOLA_CITIES_CODES_NH" -> Seq(
        Map("cod_mpio" -> "05001", "nom_mpio" -> "MEDELLIN", "cod_dpto" -> "05"),
        Map("cod_mpio" -> "08001", "nom_mpio" -> "BARRANQUILLA", "cod_dpto" -> "08"),
        Map("cod_mpio" -> "11001", "nom_mpio" -> "BOGOTA D.C.", "cod_dpto" -> "11"),
        Map("cod_mpio" -> "13001", "nom_mpio" -> "CARTAGENA", "cod_dpto" -> "13"),
        Map("cod_mpio" -> "68001", "nom_mpio" -> "BUCARAMANGA", "cod_dpto" -> "68"),
        Map("cod_mpio" -> "76001", "nom_mpio" -> "CALI", "cod_dpto" -> "76")
      ),
      "201_SISBEN_ZONES_NH" -> Seq(
        Map("cod_clase" -> "1", "descripcion" -> "CABECERA"),
        Map("cod_clase" -> "2", "descripcion" -> "CENTRO POBLADO"),
        Map("cod_clase" -> "3", "descripcion" -> "RURAL DISPERSO")
      ),
      "202_SISBEN_PROCESS_ID_TYPE_HO" -> Seq(
        Map("cod_sisben" -> "1", "cod_experian" -> "10", "descripcion" -> "REGISTRO CIVIL"),
        Map("cod_sisben" -> "2", "cod_experian" -> "7", "descripcion" -> "TARJETA DE IDENTIDAD"),
        Map("cod_sisben" -> "3", "cod_experian" -> "1", "descripcion" -> "CEDULA DE CIUDADANIA"),
        Map("cod_sisben" -> "4", "cod_experian" -> "4", "descripcion" -> "CEDULA DE EXTRANJERIA"),
        Map("cod_sisben" -> "5", "cod_experian" -> "8", "descripcion" -> "DNI PAIS DE ORIGEN"),
        Map("cod_sisben" -> "6", "cod_experian" -> "5", "descripcion" -> "PASAPORTE"),
        Map("cod_sisben" -> "7", "cod_experian" -> "", "descripcion" -> "SALVOCONDUCTO PARA REFUGIADO"),
        Map("cod_sisben" -> "8", "cod_experian" -> "9", "descripcion" -> "PERMISO ESPECIAL DE PERMANENCIA"),
        Map("cod_sisben" -> "PPT", "cod_experian" -> "11", "descripcion" -> "PERMISO DE PROTECCION TEMPORAL")
      ),
      "234_SISBEN_GROUP_SUBGROUP_NH" -> Seq(
        Map("grupo" -> "A", "nivel_max" -> "5", "descripcion" -> "POBREZA EXTREMA"),
        Map("grupo" -> "B", "nivel_max" -> "7", "descripcion" -> "POBREZA MODERADA"),
        Map("grupo" -> "C", "nivel_max" -> "18", "descripcion" -> "VULNERABLE"),
        Map("grupo" -> "D", "nivel_max" -> "21", "descripcion" -> "NO POBRE NO VULNERABLE")
      ),
      "203_SISBEN_RECORD_STATUS_AND_MARK_NH" -> Seq(
        Map("codigo" -> "0", "tipo" -> "AMBOS", "descripcion" -> "REGISTRO VALIDO"),
        Map("codigo" -> "1", "tipo" -> "MARCA", "descripcion" -> "EXCLUIDO DE PUBLICACION"),
        Map("codigo" -> "10", "tipo" -> "AMBOS", "descripcion" -> "ESTADO EXCLUIDO FALLECIDO / MARCA EN VERIFICACION"),
        Map("codigo" -> "20", "tipo" -> "MARCA", "descripcion" -> "VERIFICACION POR DISTANCIA"),
        Map("codigo" -> "40", "tipo" -> "ESTADO", "descripcion" -> "VERIFICACION DESACTUALIZACION SECCION E"),
        Map("codigo" -> "41", "tipo" -> "ESTADO", "descripcion" -> "VERIFICACION DESACTUALIZACION SECCION G NIVEL EDUCATIVO"),
        Map("codigo" -> "42", "tipo" -> "ESTADO", "descripcion" -> "VERIFICACION DESACTUALIZACION SECCION G FONDO PENSIONES"),
        Map("codigo" -> "43", "tipo" -> "ESTADO", "descripcion" -> "VERIFICACION DESACTUALIZACION SECCION H"),
        Map("codigo" -> "60", "tipo" -> "ESTADO", "descripcion" -> "CALIDAD ENCUESTA DATOS NO VALIDOS"),
        Map("codigo" -> "61", "tipo" -> "ESTADO", "descripcion" -> "CALIDAD ENCUESTA PARENTESCOS INCONSISTENTES"),
        Map("codigo" -> "69", "tipo" -> "ESTADO", "descripcion" -> "VERIFICACION POR DISTANCIA MAYOR A 600 M"),
        Map("codigo" -> "70", "tipo" -> "ESTADO", "descripcion" -> "CALIDAD ENCUESTA DOCUMENTO NO VALIDO"),
        Map("codigo" -> "90", "tipo" -> "ESTADO", "descripcion" -> "DESMEJORAMIENTO VARIABLES DE VIVIENDA"),
        Map("codigo" -> "91", "tipo" -> "ESTADO", "descripcion" -> "DESMEJORAMIENTO VARIABLES DE NIVEL EDUCATIVO")
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

  private val RegistroBase: Seq[String] = Seq("0900000101", "1", "05", "ANTIOQUIA", "05001", "MEDELLIN", "1", "1", "9300000101", "PRUEBAUNO", "APELLIDOUNO", "NOMBREUNO", "SEGUNDOUNO", "3", "1000000101", "A", "1", "15/11/2025", "20/12/2025", "0", "0", "10/01/2026")

  private def linea(valores: Seq[String]): String = valores.mkString(";")

  private def conValor(posicion: Int, valor: String): String = linea(RegistroBase.updated(posicion - 1, valor))

  private def codigos(fila: String): Seq[String] =
    RecordValidator.validarRegistro(spec, fila, CatalogoSintetico, hoy).errores.map(_.codigo)

  private val HeaderCorrecto: String = "ID_FICHA_ORIGEN;ID_HOGAR;COD_DPTO;NOM_DPTO;COD_MPIO;NOM_MPIO;ID_CLASE;ID_PERSONA_ORDEN;ID_NACIONAL;PRI_APELLIDO;SEG_APELLIDO;PRI_NOMBRE;SEG_NOMBRE;TIP_DOCUMENTO;NUM_DOCUMENTO;ID_GRUPO;ID_NIVEL;FECHA_DIGITACION;FECHA_ACTUALIZACION_PERSONA;ID_MARCA;ID_ESTADO;FECHA_ACTUALIZACION_CNS"

  /** Header con los nombres fuente (primer alias de cada campo) (AUD-V-09). */
  private val HeaderFuente: String = "ide_ficha_origen;ide_hogar;cod_dpto;nom_dpto;cod_mpio;nom_mpio;cod_clase;ide_persona;ide_nacional;pri_apellido;seg_apellido;pri_nombre;seg_nombre;tip_documento;num_documento;grupo;nivel;fec_digitacion;fec_actualizacion_persona;marca;estado;fec_actualizacion_cns"

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

  test("ID_FICHA_ORIGEN inválido produce SISBEN_INC_001") {
    assert(codigos(conValor(1, "AB12")).contains("SISBEN_INC_001"))
  }

  test("ID_HOGAR inválido produce SISBEN_INC_002") {
    assert(codigos(conValor(2, "X1")).contains("SISBEN_INC_002"))
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

  test("ID_PERSONA_ORDEN inválido produce SISBEN_INC_008") {
    assert(codigos(conValor(8, "X1")).contains("SISBEN_INC_008"))
  }

  test("ID_NACIONAL inválido produce SISBEN_INC_009") {
    assert(codigos(conValor(9, "ABC")).contains("SISBEN_INC_009"))
  }

  test("PRI_APELLIDO inválido produce SISBEN_ERR_010") {
    assert(codigos(conValor(10, "")).contains("SISBEN_ERR_010"))
  }

  test("SEG_APELLIDO inválido produce SISBEN_INC_011") {
    assert(codigos(conValor(11, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")).contains("SISBEN_INC_011"))
  }

  test("PRI_NOMBRE inválido produce SISBEN_ERR_012") {
    assert(codigos(conValor(12, "")).contains("SISBEN_ERR_012"))
  }

  test("SEG_NOMBRE inválido produce SISBEN_INC_013") {
    assert(codigos(conValor(13, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")).contains("SISBEN_INC_013"))
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

  test("FECHA_DIGITACION inválido produce SISBEN_INC_018") {
    assert(codigos(conValor(18, "2099-01-01")).contains("SISBEN_INC_018"))
  }

  test("FECHA_ACTUALIZACION_PERSONA inválido produce SISBEN_INC_019") {
    assert(codigos(conValor(19, "31/02/2025")).contains("SISBEN_INC_019"))
  }

  test("ID_MARCA inválido produce SISBEN_INC_020") {
    assert(codigos(conValor(20, "5")).contains("SISBEN_INC_020"))
  }

  test("ID_ESTADO inválido produce SISBEN_ERR_021") {
    assert(codigos(conValor(21, "50")).contains("SISBEN_ERR_021"))
  }

  test("FECHA_ACTUALIZACION_CNS inválido produce SISBEN_ERR_022") {
    assert(codigos(conValor(22, "31/02/2025")).contains("SISBEN_ERR_022"))
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

  test("header con los nombres fuente (alias) se acepta y se reconoce como fila de header") {
    assert(RecordValidator.validarHeader(spec, Seq(HeaderFuente)).isEmpty)
    assert(RecordValidator.esHeader(spec, HeaderFuente))
  }

  test("header con una columna distinta, con columnas de menos o vacío produce el código de header") {
    val cols = HeaderCorrecto.split(java.util.regex.Pattern.quote(";"), -1)
    assert(RecordValidator.validarHeader(spec, Seq(cols.updated(1, "OTRA").mkString(";"))).map(_.codigo) == Seq(spec.codigos.header))
    assert(RecordValidator.validarHeader(spec, Seq(cols.dropRight(1).mkString(";"))).map(_.codigo) == Seq(spec.codigos.header))
    assert(RecordValidator.validarHeader(spec, Seq.empty).map(_.codigo) == Seq(spec.codigos.header))
  }

  test("nombre de archivo del fixture es válido y uno distinto no") {
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/SISBEN_EXPERIAN_202601.20260205093000-0500.txt", ahora).isEmpty)
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/OTRO_ARCHIVO.txt", ahora).map(_.codigo) == Seq(spec.codigos.nombreArchivo))
  }

  test("nombre de archivo con periodo posterior al mes de referencia (offset adelantado, solo esta regla) produce el código de nombre (periodo_no_futuro)") {
    val reloj = java.time.LocalDate.of(2026, 2, 28).atTime(23, 59, 59).atZone(java.time.ZoneId.of(spec.zonaHoraria)).toInstant
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/SISBEN_EXPERIAN_202603.20260301000000+1400.txt", reloj).map(_.codigo) == Seq(spec.codigos.nombreArchivo))
    assert(RecordValidator.validarNombre(spec.copy(nombreArchivo = spec.nombreArchivo.copy(periodoNoFuturo = false)), "/ruta/sintetica/SISBEN_EXPERIAN_202603.20260301000000+1400.txt", reloj).isEmpty, "sin la regla el nombre es válido: la prueba la aísla (AUD-V-28)")
  }

  test("nombre de archivo con recepción posterior a la fecha de referencia produce el código de nombre (recepcion_no_futura)") {
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/SISBEN_EXPERIAN_202601.20260208120000-0500.txt", ahora).map(_.codigo) == Seq(spec.codigos.nombreArchivo))
    assert(RecordValidator.validarNombre(spec.copy(nombreArchivo = spec.nombreArchivo.copy(recepcionNoFutura = false)), "/ruta/sintetica/SISBEN_EXPERIAN_202601.20260208120000-0500.txt", ahora).isEmpty, "sin la regla el nombre es válido: la prueba la aísla (AUD-V-28)")
  }

  test("nombre de archivo con recepción anterior al primer día del periodo produce el código de nombre (recepcion_desde_inicio_periodo)") {
    assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/SISBEN_EXPERIAN_202601.20251231120000-0500.txt", ahora).map(_.codigo) == Seq(spec.codigos.nombreArchivo))
    assert(RecordValidator.validarNombre(spec.copy(nombreArchivo = spec.nombreArchivo.copy(recepcionDesdeInicioPeriodo = false)), "/ruta/sintetica/SISBEN_EXPERIAN_202601.20251231120000-0500.txt", ahora).isEmpty, "sin la regla el nombre es válido: la prueba la aísla (AUD-V-28)")
  }

  test("nombres de archivo inválidos declarados en el fixture producen el código de nombre") {
    for (nombre <- Seq("RT_SISBEN_EXPERIAN_202601.20260205093000-0500.txt", "EP_SISBEN_EXPERIAN_202601.20260205093000-0500.txt", "SISBEN_EXPERIAN_202603.20260205093000-0500.txt", "SISBEN_EXPERIAN_202601.20251231093000-0500.txt", "SISBEN_EXPERIAN_202601.20260206093000-0500.txt", "SISBEN_EXPERIAN_202601.txt"))
      assert(RecordValidator.validarNombre(spec, "/ruta/sintetica/" + nombre, ahora).map(_.codigo) == Seq(spec.codigos.nombreArchivo), nombre)
  }

  test("la clave de duplicado nunca es vacía") {
    assert(RecordValidator.claveDuplicado(spec, Campos.separar(linea(RegistroBase), ";")).nonEmpty)
    assert(RecordValidator.claveDuplicado(spec, Campos.separar("", ";")).nonEmpty)
  }

  test("dos filas idénticas sin un campo de la clave no se agrupan como duplicadas (VAL-SP03, AUD-V-27)") {
    // El motor agrupa por keys = keysForDuplicate(...).mkString(":") (Validator.dupCheckForKey).
    def clave(fila: String): String = RecordValidator.claveDuplicado(spec, Campos.separar(fila, ";")).mkString(":")
    spec.duplicados.clave.flatMap(n => spec.campo(n).toSeq).foreach { c =>
      val sinCampo = conValor(c.posicion, "")
      assert(clave(sinCampo) != clave(sinCampo), "sin " + c.nombre + " dos filas idénticas comparten la clave")
    }
    if (spec.duplicados.clave.nonEmpty) assert(clave(linea(RegistroBase)) == clave(linea(RegistroBase)))
  }

  // Casos del fixture (filtro de filas, reglas entre campos, duplicados), declarados en el source-spec.
  test("fixture BASE: registro válido") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000101;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000101;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq())
  }

  test("fixture FECHA_ISO: fechas en yyyy-MM-dd") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000102;A;1;2025-11-15;20/12/2025;0;0;2026-01-10"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000102;A;1;2025-11-15;20/12/2025;0;0;2026-01-10") == Seq())
  }

  test("fixture MENOR_RC: registro civil: se filtra") {
    assert(RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;1;1000000103;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
  }

  test("fixture MENOR_TI: tarjeta de identidad: se filtra") {
    assert(RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;2;1000000104;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
  }

  test("fixture LINEA_VACIA: línea vacía: se filtra") {
    assert(RecordValidator.debeFiltrar(spec, ""))
  }

  test("fixture CE_ADULTO: cédula de extranjería es válida") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;4;1000000105;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;4;1000000105;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq())
  }

  test("fixture PASAPORTE_ALFA: pasaporte alfanumérico válido") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;6;PA1000106;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;6;PA1000106;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq())
  }

  test("fixture PPT: PPT válido") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;PPT;1000000107;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;PPT;1000000107;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq())
  }

  test("fixture SALVOCONDUCTO: tipo 7 sin homologación") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;7;1000000108;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;7;1000000108;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq("SISBEN_ERR_023"))
  }

  test("fixture TIPO_INEXISTENTE: tipo 9") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;9;1000000109;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;9;1000000109;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq("SISBEN_ERR_014"))
  }

  test("fixture DOC_CERO: número de documento en ceros") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;0000;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;0000;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq("SISBEN_ERR_029"))
  }

  test("fixture GRUPO_INVALIDO: grupo Z") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000110;Z;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000110;Z;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq("SISBEN_ERR_016"))
  }

  test("fixture NIVEL_FUERA: nivel 22") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000111;A;22;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000111;A;22;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq("SISBEN_ERR_017"))
  }

  test("fixture MPIO_OTRO_DPTO: municipio de otro departamento") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;11;BOGOTA D.C.;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000112;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;11;BOGOTA D.C.;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000112;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq("SISBEN_ERR_028"))
  }

  test("fixture NOM_DPTO_NO_CORRESPONDE: nombre de departamento no corresponde") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;BOLIVAR;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000113;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;BOLIVAR;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000113;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq("SISBEN_ERR_004"))
  }

  test("fixture CNS_VACIA: fecha de actualización CNS vacía") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000114;A;1;15/11/2025;20/12/2025;0;0;"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000114;A;1;15/11/2025;20/12/2025;0;0;") == Seq("SISBEN_ERR_022"))
  }

  test("fixture DIGITACION_VACIA: fecha de digitación vacía es válida") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000115;A;1;;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000115;A;1;;20/12/2025;0;0;10/01/2026") == Seq())
  }

  test("fixture FECHA_FUTURA: fecha de digitación futura") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000116;A;1;01/01/2099;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000116;A;1;01/01/2099;20/12/2025;0;0;10/01/2026") == Seq("SISBEN_INC_018"))
  }

  test("fixture ESTADO_TEXTO: estado textual") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000117;A;1;15/11/2025;20/12/2025;0;ACTIVO;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000117;A;1;15/11/2025;20/12/2025;0;ACTIVO;10/01/2026") == Seq("SISBEN_ERR_021"))
  }

  test("fixture MARCA_TEXTO: marca textual: inconsistencia") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000118;A;1;15/11/2025;20/12/2025;N;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000118;A;1;15/11/2025;20/12/2025;N;0;10/01/2026") == Seq("SISBEN_INC_020"))
  }

  test("fixture NOMBRE_TILDES: nombres con Ñ y tildes válidos") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;MUÑOZ;APELLIDOUNO;JOSÉ;SEGUNDOUNO;3;1000000119;A;1;15/11/2025;20/12/2025;0;0;10/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;MUÑOZ;APELLIDOUNO;JOSÉ;SEGUNDOUNO;3;1000000119;A;1;15/11/2025;20/12/2025;0;0;10/01/2026") == Seq())
  }

  test("fixture TRUNCADA: fila con 10 columnas") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO") == Seq("SISBEN_ERR_026"))
  }

  test("fixture DUP_ANTIGUO: duplicado más antiguo: se marca") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000199;A;1;15/11/2025;20/12/2025;0;0;01/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000199;A;1;15/11/2025;20/12/2025;0;0;01/01/2026") == Seq())
  }

  test("fixture DUP_RECIENTE: duplicado más reciente: se conserva") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000199;A;1;15/11/2025;20/12/2025;0;0;15/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000199;A;1;15/11/2025;20/12/2025;0;0;15/01/2026") == Seq())
  }

  test("fixture DUP_CEROS_ANTIGUO: duplicado con ceros a la izquierda, más antiguo: se marca") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000198;A;1;15/11/2025;20/12/2025;0;0;01/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000198;A;1;15/11/2025;20/12/2025;0;0;01/01/2026") == Seq())
  }

  test("fixture DUP_CEROS_RECIENTE: duplicado con ceros a la izquierda, más reciente: se conserva") {
    assert(!RecordValidator.debeFiltrar(spec, "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;0001000000198;A;1;15/11/2025;20/12/2025;0;0;15/01/2026"))
    assert(codigos("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;0001000000198;A;1;15/11/2025;20/12/2025;0;0;15/01/2026") == Seq())
  }

  test("fixture duplicados: misma clave y se conserva el más reciente (RESUELTO F-14 (spec-sisben.md §6 (mayor fec_actualizacion_cns; empate por mayor eventId)))") {
    val grupo = Seq("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000199;A;1;15/11/2025;20/12/2025;0;0;01/01/2026", "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000199;A;1;15/11/2025;20/12/2025;0;0;15/01/2026")
    val claves = grupo.map(l => RecordValidator.claveDuplicado(spec, Campos.separar(l, ";")).toSeq)
    assert(claves.distinct.size == 1)
    val fechas = grupo.map(l => RecordValidator.asOfDate(spec, l).getOrElse(0L))
    val candidatos = fechas.zipWithIndex.map { case (f, i) => (f, "%05d".format(i)) }
    assert(RecordValidator.indiceAConservar(candidatos) == 1)
  }

  test("fixture duplicados (grupo DUP_CEROS): misma clave y se conserva el más reciente (RESUELTO F-14 (spec-sisben.md §6 (mayor fec_actualizacion_cns; empate por mayor eventId)))") {
    val grupo = Seq("0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;1000000198;A;1;15/11/2025;20/12/2025;0;0;01/01/2026", "0900000101;1;05;ANTIOQUIA;05001;MEDELLIN;1;1;9300000101;PRUEBAUNO;APELLIDOUNO;NOMBREUNO;SEGUNDOUNO;3;0001000000198;A;1;15/11/2025;20/12/2025;0;0;15/01/2026")
    val claves = grupo.map(l => RecordValidator.claveDuplicado(spec, Campos.separar(l, ";")).toSeq)
    assert(claves.distinct.size == 1)
    val fechas = grupo.map(l => RecordValidator.asOfDate(spec, l).getOrElse(0L))
    val candidatos = fechas.zipWithIndex.map { case (f, i) => (f, "%05d".format(i)) }
    assert(RecordValidator.indiceAConservar(candidatos) == 1)
  }
}
