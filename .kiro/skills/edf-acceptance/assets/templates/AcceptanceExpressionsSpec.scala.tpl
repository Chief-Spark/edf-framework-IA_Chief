// ACC-TPL-05 · Casos de prueba de las expresiones de Acceptance de {{Fuente}}.
// Generado por edf-acceptance-stage {{version_skill}}. Tipo: ESQUELETO.
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
  private val QualityScoreNames = Seq("{{calidad.quality_score}}")
  private val AutomaticMarkers = Seq("errorInHeader", "errorInFooter", "errorInFileName", "errorInHistoricAttribute")

  private val conditions: Map[String, AcceptanceCondition] = Map(
{{#cada condiciones}}
    "{{nombre}}" -> AcceptanceCondition("{{nombre}}", "{{expresion|scala}}", "{{reasonCode}}", {{isLogged}}),
{{/cada}}
  )

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

  private def recordAccepted(event: TaggedValidatedEvent): Boolean = {
    val parser = new RecordValidationParser(Left(event))
    parser.parseAll(parser.boolExpr, RecordAcceptance).get
  }

  test("recordAcceptance no contiene nombres de quality score (ACC-R02)") {
    QualityScoreNames.foreach(qs => assert(!RecordAcceptance.contains(qs), "QS dentro de la expresión: " + qs))
  }

  test("reject compila en la sintaxis nueva y solo cita condiciones definidas (ACC-P09)") {
    assert(AcceptanceLexer(FileReject).isDefined, "reject cae a la sintaxis antigua y no registra reasonCode")
    val evaluator = new AcceptanceEvaluator
    evaluator.apply(conditions, Map.empty[String, Int])
    assert(evaluator.compile(FileReject).isDefined)
    val cited = "[A-Za-z_][A-Za-z0-9_]*".r.findAllIn(FileReject).toSet -- Set("anyOf", "allOf", "hasErrorlevelIn", "hasErrorlevelNotIn")
    assert(cited.subsetOf(conditions.keySet), "condiciones no definidas: " + (cited -- conditions.keySet))
  }

  test("accept usa la sintaxis antigua y no acepta cuerpos vacíos (RF-ACC-02)") {
    assert(AcceptanceLexer(FileAccept).isEmpty)
    assert(!FileAccept.contains("isEmptyBodyFile"))
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
    import spark.implicits._
    val evaluator = new AcceptanceEvaluator
    evaluator.apply(conditions, Map.empty[String, Int])
    val rejectStatement = evaluator.compile(FileReject).get
    expectedFiles.foreach { case (dir, marker, expected, reasonCode) =>
      if (AutomaticMarkers.contains(marker) || marker == "errorInEmptyFileBody") {
        assert(expected == "rechazo_automatico", dir)
      } else {
        val ds: Dataset[TaggedValidatedEvent] = spark.createDataset(loadEvents(dir))
        val parser = new AggregatesParserDF(spark.sqlContext, AggregatesParserDF.createQSDataFrame(spark, ds))
        val (rejected, partials) = evaluator.execute(rejectStatement, conditions.values.toList, parser)
        val accepted = !rejected && Try(parser.parseAll(parser.boolExpr, FileAccept).get).getOrElse(false)
        val decision = if (rejected) "rechazado" else if (accepted) "aceptado" else "pendiente"
        assert(decision == expected, dir)
        val logged = partials.filter(p => p.isLogged && p.result).map(_.reasonCode).mkString(",")
        assert(logged == reasonCode, dir)
      }
    }
  }

  // PARTE MANUAL (ACC-TPL-05): si se activa ACC-TPL-04 (contextualOverride), agregue aquí un caso que
  // falle cuando la inferencia de borrados devuelve vacío en silencio (ACC-AP03).
}
