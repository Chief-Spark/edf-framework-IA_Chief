// ACC-TPL-05 · Casos de prueba de las expresiones de Acceptance de {{Fuente}}.
// Generado por edf-acceptance {{version_skill}}. Tipo: ESQUELETO.
// Compilación NO certificada (F-01): sin acceso al JAR del framework no se pudo compilar.
// Destino: {{rutas_destino.raiz_tests}}  ·  Fixture: {{rutas_destino.recursos_tests}}validated/
// Motivo: un error de sintaxis no falla en ejecución; acepta todos los registros o deja el archivo
// pendiente. Estos casos evalúan cada expresión con los mismos parsers del motor.
package {{rutas_destino.paquete_tests}}

import com.experian.edf.oxygen.acceptance.engines.dsl.{AcceptanceEvaluator, AcceptanceLexer}
import com.experian.edf.oxygen.acceptance.util.{AggregatesParserDF, RecordValidationParser}
import com.experian.edf.oxygen.configuration.configs.AcceptanceCondition
import com.experian.edf.oxygen.domain.internal.TaggedValidatedEvent
// ASSUMPTION S-ACC-04: JsonUtils está en com.experian.edf.oxygen.utils (el motor lo importa con comodín).
import com.experian.edf.oxygen.utils.JsonUtils
import org.apache.commons.io.FilenameUtils
import org.apache.spark.sql.{Dataset, SparkSession}
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

import scala.io.Source
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class {{Fuente}}AcceptanceExpressionsSpec extends AnyFunSuite with BeforeAndAfterAll {

  private val RecordAcceptance = "{{registro.recordAcceptance|scala}}"
  private val FileReject = "{{fileActions.reject|scala}}"
  private val FileAccept = "{{fileActions.accept|scala}}"
{{#si fileActions.manualReview?}}
  private val ManualReview = "{{fileActions.manualReview|scala}}"
{{/si}}
  private val QualityScoreNames = Seq("{{calidad.quality_score}}")
  private val AutomaticMarkers = Seq("errorInHeader", "errorInFooter", "errorInFileName", "errorInHistoricAttribute")
  private val CustomReasonForAutomaticRejection = {{rechazo_automatico.customReasonForAutomaticRejectionEnabled}}

  // HF-33 (AUD-A-16): el motor construye AcceptanceCondition con 5 argumentos (AcceptanceAST.scala:75,
  // SparkDatasetAcceptor.scala:284); el quinto (result) va explícito en false, sin suponer un valor por defecto.
  private val conditions: Map[String, AcceptanceCondition] = Map(
{{#cada condiciones}}
    "{{nombre}}" -> AcceptanceCondition("{{nombre}}", "{{expresion|scala}}", "{{reasonCode}}", {{isLogged}}, /* result = */ false),
{{/cada}}
  )

  // AUD-A1: el motor pasa buildReasonCodeSeverityMap(config.lookupMapping, conditions) (SparkAcceptor.scala:208 y
  // :224): reasonCode → severidad, solo de los reasonCode de las condiciones y solo si hay lookupMapping. Aquí sale
  // de severidades.filas cuando severidades.habilitado; sin lookupMapping el motor pasa un mapa vacío.
  private val ReasonSeverities: Map[String, Int] = Map[String, Int](
{{#si severidades.habilitado}}
{{#cada severidades.filas}}
    "{{reasonCode}}" -> {{severidad}},
{{/cada}}
{{/si}}
  ).filter { case (code, _) => conditions.values.exists(_.reasonCode == code) }

  // (directorio, eventId, resultado esperado de recordAcceptance)
  private val expectedRecords: Seq[(String, String, Boolean)] = Seq(
{{#cada fixture.archivos}}
{{#cada eventos}}
    ("{{directorio}}", "{{eventId}}", {{esperado_registro}}),
{{/cada}}
{{/cada}}
  )

  // (directorio, marcador, decisión esperada, reasonCode esperado)
  private val expectedFiles: Seq[(String, String, String, String)] = Seq(
{{#cada fixture.archivos}}
    ("{{directorio}}", "{{marcador}}", "{{decision_esperada}}", "{{reason_code_esperado}}"),
{{/cada}}
  )

  private val FilePattern = "{{archivo.filePattern|scala}}"

  // (clave, valor, debe coincidir): archivo.claves_de_prueba del insumo
  private val fileKeyCases: Seq[(String, String, Boolean)] = Seq(
{{#cada archivo.claves_de_prueba}}
    ("{{clave|scala}}", "{{valor|scala}}", {{debe_coincidir}}),
{{/cada}}
  )

  private lazy val spark: SparkSession =
    SparkSession.builder().master("local[1]").appName("{{fuente}}-acceptance-expressions").getOrCreate()

  override def afterAll(): Unit = {
    spark.stop()
    super.afterAll()
  }

  private def loadEvents(dir: String): Seq[TaggedValidatedEvent] = {
    val src = Source.fromResource("{{fuente}}/validated/" + dir + "/part-00000.json")
    try src.getLines().filter(_.trim.nonEmpty).map(l => JsonUtils.fromJson(l, classOf[TaggedValidatedEvent])).toList
    finally src.close()
  }

  /** AUD-A2: misma regla que SparkAcceptor.shouldAutomaticReject (SparkAcceptor.scala:190-198) sobre el nombre del
    * directorio de validados: marcador de header, footer, nombre o atributos históricos; cuerpo vacío si accept no
    * contiene isEmptyBodyFile. */
  private def automaticReject(dir: String): Boolean =
    AutomaticMarkers.exists(m => dir.contains(m)) ||
      (dir.contains("errorInEmptyFileBody") && !FileAccept.contains("isEmptyBodyFile"))

  /** HF-33 (AUD-A-17): misma regla que SparkAcceptor.removeFileExtension (SparkAcceptor.scala:152-157): una
    * extensión menos; dos si la ruta contiene errorIn. Con esa clave el motor elige la configuración
    * (`removeFileExtension(ruta) matches filePattern`, :131); si no coincide, el archivo queda sin procesar. */
  private def engineFileKey(path: String): String = {
    val stripped = FilenameUtils.removeExtension(path)
    if (path.contains("errorIn")) FilenameUtils.removeExtension(stripped) else stripped
  }

  private def aggregatesParser(dir: String): AggregatesParserDF = {
    import spark.implicits._
    val ds: Dataset[TaggedValidatedEvent] = spark.createDataset(loadEvents(dir))
    new AggregatesParserDF(spark.sqlContext, AggregatesParserDF.createQSDataFrame(spark, ds))
  }

  private def recordAccepted(event: TaggedValidatedEvent): Boolean = {
    val parser = new RecordValidationParser(Left(event))
    parser.parseAll(parser.boolExpr, RecordAcceptance).get
  }

  test("filePattern: cada clave de prueba del insumo coincide o no como se declaró (E3, ACC-R03, HF-33)") {
    assert(fileKeyCases.nonEmpty, "el insumo no declara archivo.claves_de_prueba")
    fileKeyCases.foreach { case (clave, valor, expected) =>
      // Las claves ruta_* son rutas de validados y se recortan como en el motor; las demás se comparan tal cual
      // (convención declarada en el esquema, archivo.claves_de_prueba[].clave, HF-44).
      // (misma regla que render_templates.verify_spec). String.matches exige coincidencia total, como el motor.
      val key = if (clave.startsWith("ruta_")) engineFileKey(valor) else valor
      assert(key.matches(FilePattern) == expected, clave + ": " + key + " debería " + (if (expected) "" else "no ") + "coincidir")
    }
  }

  test("recordAcceptance no contiene nombres de quality score (ACC-R02)") {
    QualityScoreNames.foreach(qs => assert(!RecordAcceptance.contains(qs), "QS dentro de la expresión: " + qs))
  }

  test("reject compila en la sintaxis nueva y solo cita condiciones definidas (ACC-P09)") {
    assert(AcceptanceLexer(FileReject).isDefined, "reject cae a la sintaxis antigua y no registra reasonCode")
    val evaluator = new AcceptanceEvaluator
    evaluator.apply(conditions, ReasonSeverities)
    assert(evaluator.compile(FileReject).isDefined)
    val cited = "[A-Za-z_][A-Za-z0-9_]*".r.findAllIn(FileReject).toSet -- Set("anyOf", "allOf", "hasErrorlevelIn", "hasErrorlevelNotIn")
    assert(cited.subsetOf(conditions.keySet), "condiciones no definidas: " + (cited -- conditions.keySet))
  }

  test("accept usa la sintaxis antigua y no acepta cuerpos vacíos (RF-ACC-02)") {
    assert(AcceptanceLexer(FileAccept).isEmpty)
    assert(!FileAccept.contains("isEmptyBodyFile"))
  }

{{#si fileActions.manualReview?}}
  test("manualReview compila en la sintaxis nueva (AUD-A4; lo lee SparkDatasetAcceptor)") {
    val evaluator = new AcceptanceEvaluator
    evaluator.apply(conditions, ReasonSeverities)
    assert(evaluator.compile(ManualReview).isDefined, "manualReview no compila: el motor lo evalúa como false")
  }

{{/si}}
  test("toda expresión de archivo declarada parsea: accept y cada condición (AUD-A3)") {
    // En el motor nada de esto falla en ejecución: un accept que lanza vale false y deja el archivo pendiente
    // (SparkAcceptor.isFileActionValid) y una condición que lanza vale false (AcceptanceAST.evaluateNamedInternal).
    // Aquí se exige que parseen sobre los archivos que cumplen el contrato con Validation (traen el QS declarado):
    // si una lanza o no parsea, la prueba falla. Los archivos sin QS (S-ACC-03) se excluyen a propósito.
    val conContrato = expectedFiles.map(_._1).distinct.filterNot(automaticReject).filter { dir =>
      val events = loadEvents(dir)
      events.nonEmpty && events.forall(e => QualityScoreNames.forall(qs => Option(e.qualityScores).exists(_.exists(_.name == qs))))
    }
    assert(conContrato.nonEmpty, "el fixture no tiene archivos con el QS declarado que lleguen a evaluar accept")
    conContrato.foreach { dir =>
      val parser = aggregatesParser(dir)
      (("accept" -> FileAccept) +: conditions.values.toSeq.map(c => c.name -> c.expression)).foreach { case (name, expr) =>
        val parsed = parser.parseAll(parser.boolExpr, expr)
        assert(parsed.successful, name + " no parsea en " + dir + ": " + parsed)
      }
    }
  }

  test("regla de registro: un caso por fila de la tabla de IN-04") {
    expectedRecords.groupBy(_._1).foreach { case (dir, cases) =>
      val byId = loadEvents(dir).map(e => e.eventId -> e).toMap
      cases.foreach { case (_, eventId, expected) =>
        val result = Try(recordAccepted(byId(eventId)))
        assert(result.isSuccess, "la expresión falla y el motor ACEPTARÍA el registro " + eventId)
        assert(result.get == expected, "registro " + eventId)
      }
    }
  }

  test("decisión de archivo: se acepta, se rechaza con reasonCode, automático y pendiente") {
    val evaluator = new AcceptanceEvaluator
    evaluator.apply(conditions, ReasonSeverities)
    val rejectStatement = evaluator.compile(FileReject).get
    expectedFiles.foreach { case (dir, marker, expected, reasonCode) =>
      // AUD-A2: el rechazo automático se decide con la regla del motor, no se da por hecho.
      assert(marker.isEmpty || dir.contains(marker), "el marcador " + marker + " no está en el directorio " + dir)
      assert(automaticReject(dir) == (expected == "rechazo_automatico"), dir)
      if (automaticReject(dir)) {
        // Con customReasonForAutomaticRejectionEnabled el motor evalúa además reject sobre los eventos del marcador
        // y registra el reasonCode solo si alguna condición de reject es verdadera sobre ellos (E4). Si los eventos
        // del marcador no traen el QS de la condición, la condición vale false y no registra ninguno (AUD-A-09).
        assert(CustomReasonForAutomaticRejection || reasonCode.isEmpty, dir)
      } else {
        val parser = aggregatesParser(dir)
        val (rejected, partials) = evaluator.execute(rejectStatement, conditions.values.toList, parser)
        // SILENCIO-INTENCIONAL AUD-A3: igual que el motor (SparkAcceptor.isFileActionValid), un accept que lanza vale false.
        val accepted = !rejected && Try(parser.parseAll(parser.boolExpr, FileAccept).get).getOrElse(false)
        val decision = if (rejected) "rechazado" else if (accepted) "aceptado" else "pendiente"
        assert(decision == expected, dir)
        val logged = partials.filter(p => p.isLogged && p.result).map(_.reasonCode).mkString(",")
        assert(logged == reasonCode, dir)
      }
    }
  }

{{#si fixture.umbrales_alternativos?}}
  // HF-23 (AUD-A-01): fixture.umbrales_alternativos. Prueba la MISMA configuración con otro umbral sin generar otra
  // configuración desplegable: accept y cada condición se evalúan con el literal del umbral sustituido (la regla
  // "escala única" garantiza que es su único número) sobre registros sintéticos con QS binario: registros_validos
  // con 1 y el resto con 0.
  private val UmbralDesplegado = "{{calidad.umbral}}"

  // (umbral, caso, registros válidos, registros totales, decisión esperada, reasonCode esperado)
  private val umbralesAlternativos: Seq[(String, String, Int, Int, String, String)] = Seq(
{{#cada fixture.umbrales_alternativos}}
{{#cada casos}}
    ("{{umbral}}", "{{id}}", {{registros_validos}}, {{registros_totales}}, "{{esperado}}", "{{#si reason_code_esperado?}}{{reason_code_esperado|scala}}{{/si}}"),
{{/cada}}
{{/cada}}
  )

  /** Misma sustitución que render_templates.con_umbral y simular_decision.con_umbral. */
  private def conUmbral(expr: String, umbral: String): String =
    ("(?<![\\w.])" + java.util.regex.Pattern.quote(UmbralDesplegado) + "(?![\\w.])").r
      .replaceAllIn(expr, scala.util.matching.Regex.quoteReplacement(umbral))

  test("umbral alternativo: la misma configuración con otro umbral acepta y rechaza lo esperado (HF-23)") {
    import spark.implicits._
    umbralesAlternativos.foreach { case (umbral, caso, validos, totales, expected, reasonCode) =>
      val accept = conUmbral(FileAccept, umbral)
      assert(accept != FileAccept, "accept no contiene el umbral " + UmbralDesplegado)
      val alt = conditions.map { case (n, c) => n -> AcceptanceCondition(c.name, conUmbral(c.expression, umbral), c.reasonCode, c.isLogged, /* result = */ false) }
      val qs = (1 to totales).map(i => Tuple1(QualityScoreNames.map(_ -> (if (i <= validos) 1.0 else 0.0)).toMap)).toDF("qs")
      val parser = new AggregatesParserDF(spark.sqlContext, qs)
      val evaluator = new AcceptanceEvaluator
      evaluator.apply(alt, ReasonSeverities)
      val (rejected, partials) = evaluator.execute(evaluator.compile(FileReject).get, alt.values.toList, parser)
      // SILENCIO-INTENCIONAL AUD-A3: igual que el motor (SparkAcceptor.isFileActionValid), un accept que lanza vale false.
      val accepted = !rejected && Try(parser.parseAll(parser.boolExpr, accept).get).getOrElse(false)
      val decision = if (rejected) "rechazado" else if (accepted) "aceptado" else "pendiente"
      assert(decision == expected, "umbral " + umbral + ", caso " + caso)
      val logged = partials.filter(p => p.isLogged && p.result).map(_.reasonCode).mkString(",")
      assert(logged == reasonCode, "umbral " + umbral + ", caso " + caso)
    }
  }

{{/si}}
  // PARTE MANUAL (ACC-TPL-05): si se activa ACC-TPL-04 (contextualOverride), agregue aquí un caso que
  // falle cuando la inferencia de borrados devuelve vacío en silencio (ACC-AP03).
}
