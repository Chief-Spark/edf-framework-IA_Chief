// ACC-TPL-05 · Casos de prueba de las expresiones de Acceptance de SISBEN.
// Generado por edf-acceptance-stage 0.1.0. Tipo: ESQUELETO.
// Compilación NO certificada (F-01): sin acceso al JAR del framework no se pudo compilar.
// Destino: src/test/scala/com/experian/edf/oxygen/acceptance/source/sisben/  ·  Fixture: src/test/resources/sisben/validated/
// Motivo: un error de sintaxis no falla en ejecución; acepta todos los registros o deja el archivo
// pendiente. Estos casos evalúan cada expresión con los mismos parsers del motor.
package com.experian.edf.oxygen.acceptance.source.sisben

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
class SISBENAcceptanceExpressionsSpec extends AnyFunSuite with BeforeAndAfterAll {

  private val RecordAcceptance = "errorlevelNotIn(\"1\") and errorcodeNotIn(\"OxygenDuplicate\")"
  private val FileReject = "anyOf(RQS_BAJO_UMBRAL_SISBEN)"
  private val FileAccept = "average(\"qs.RQS\") >= 0.90"
  private val QualityScoreNames = Seq("RQS")
  private val AutomaticMarkers = Seq("errorInHeader", "errorInFooter", "errorInFileName", "errorInHistoricAttribute")

  private val conditions: Map[String, AcceptanceCondition] = Map(
    "RQS_BAJO_UMBRAL_SISBEN" -> AcceptanceCondition("RQS_BAJO_UMBRAL_SISBEN", "average(\"qs.RQS\") < 0.90", "SISBFA00001", true),
  )

  // (directorio, eventId, resultado esperado de recordAcceptance)
  private val expectedRecords: Seq[(String, String, Boolean)] = Seq(
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-01", true),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-02", true),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-03", true),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-04", true),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-05", true),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-06", true),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-07", true),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-08", true),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-09", false),
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "SINT-A-10", false),
    ("RT_SISBEN_EXPERIAN_202401.20240131130000-0500.txt.900002", "SINT-B-01", true),
    ("RT_SISBEN_EXPERIAN_202401.20240131130000-0500.txt.900002", "SINT-B-02", true),
    ("RT_SISBEN_EXPERIAN_202401.20240131130000-0500.txt.900002", "SINT-B-03", true),
    ("RT_SISBEN_EXPERIAN_202401.20240131130000-0500.txt.900002", "SINT-B-04", false),
    ("RT_SISBEN_EXPERIAN_202401.20240131130000-0500.txt.900002", "SINT-B-05", false),
    ("EP_SISBEN_EXPERIAN_202401.20240131140000-0500.txt.errorInHeader.900003", "SINT-C-01", true),
    ("SISBEN_EXPERIAN_202402.20240229120000-0500.txt.errorInEmptyFileBody.900004", "SINT-D-01", true),
    ("SISBEN_EXPERIAN_202403.20240331120000-0500.txt.900005", "SINT-E-01", true),
    ("SISBEN_EXPERIAN_202403.20240331120000-0500.txt.900005", "SINT-E-02", true),
  )

  // (directorio, marcador, decisión esperada, reasonCode esperado)
  private val expectedFiles: Seq[(String, String, String, String)] = Seq(
    ("SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001", "", "aceptado", ""),
    ("RT_SISBEN_EXPERIAN_202401.20240131130000-0500.txt.900002", "", "rechazado", "SISBFA00001"),
    ("EP_SISBEN_EXPERIAN_202401.20240131140000-0500.txt.errorInHeader.900003", "errorInHeader", "rechazo_automatico", ""),
    ("SISBEN_EXPERIAN_202402.20240229120000-0500.txt.errorInEmptyFileBody.900004", "errorInEmptyFileBody", "rechazo_automatico", ""),
    ("SISBEN_EXPERIAN_202403.20240331120000-0500.txt.900005", "", "pendiente", ""),
  )

  private lazy val spark: SparkSession =
    SparkSession.builder().master("local[1]").appName("sisben-acceptance-expressions").getOrCreate()

  override def afterAll(): Unit = {
    spark.stop()
    super.afterAll()
  }

  private def loadEvents(dir: String): Seq[TaggedValidatedEvent] = {
    val src = Source.fromResource("sisben/validated/" + dir + "/part-00000.json")
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
