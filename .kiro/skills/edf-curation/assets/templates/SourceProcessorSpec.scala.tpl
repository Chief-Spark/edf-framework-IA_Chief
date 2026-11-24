// CUR-TPL-11 · esqueleto · generado por el skill edf-curation-stage v{{version_skill}}
// Tests de la fuente {{Fuente}} sobre eventos aceptados SINTÉTICOS, sin Spark. Usa el núcleo puro
// {{processor_clase}}.curar; requiere el JAR del framework en el classpath para compilar
// (CuratedEvent, TaggedValidatedEvent): compilación NO CERTIFICADA (F-01).
// Los JSON esperados salen de la implementación de referencia (scripts/curation_reference.py).
// AUD-C9: estas pruebas llaman al núcleo (curar, relacion), no a curateEvent ni al gancho subscriberRelation,
// que necesitan un TaggedValidatedEvent del JAR. Esa prueba se escribe en el fork (runbook-ejecucion.md §fork).
package {{paquete_fuente}}

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner
import {{paquete_fuente}}.config.{{Fuente}}CurationSpec
import {{paquete_fuente}}.processor.{{processor_clase}}

@RunWith(classOf[JUnitRunner])
class {{processor_clase}}Spec extends AnyFunSuite {

  private def curarJson(json: String, archivo: String, errores: Seq[(String, String)] = Nil): String =
    {{Fuente}}CuratedEvent.toJson({{processor_clase}}.curar(json, archivo, errores, {{Fuente}}CurationSpec.Homologacion))

  // ---- Casos del fixture (generados desde CUR-TPL-12 y la salida esperada)
{{casos_spec_scala}}

  // ---- subscriberRelation (extension_subscriber_relation, D14)
{{casos_relacion_scala}}

  // ---- Lector de validatedRecordJson (mismo texto que la referencia, AUD-C5)
{{casos_lector_scala}}

  // ---- Propiedades fijas del contrato
  test("write/read: ida y vuelta conserva el JSON") {
    val ev = new {{Fuente}}CuratedEvent(curarJson({{evento_base_json_scala}}, "{{archivo_base}}"))
    assert(ev.read(ev.write).write == ev.write)
  }

  test("evento con todos los campos inválidos: no lanza y trae advertencias") {
    val record = {{processor_clase}}.curar("{no es json", "{{archivo_base}}", Nil, {{Fuente}}CurationSpec.Homologacion)
    assert(record.{{campo_advertencias}}.contains("registro:REGISTRO_ILEGIBLE"))
  }

  test("null y basura no lanzan; errores nulo = sin errores (AUD-C5)") {
    val record = {{processor_clase}}.curar(null, null, null, Map.empty)
    assert(record.{{campo_advertencias}}.contains("registro:REGISTRO_ILEGIBLE"))
    assert(!record.{{campo_advertencias}}.contains("registro:ERROR_INTERNO"))
  }

  test("claves del evento = domain dictionary (CUR-RC05); solo faltan claves de identidad nulas (AUD-C4)") {
    val record = {{processor_clase}}.curar({{evento_base_json_scala}}, "{{archivo_base}}", Nil, {{Fuente}}CurationSpec.Homologacion)
    import scala.collection.JavaConverters._
    val claves = record.toJavaMap.keySet.asScala.toSeq
    assert(claves == {{Fuente}}CurationSpec.ClavesPublicadas.filter(claves.contains))
    assert({{Fuente}}CurationSpec.ClavesPublicadas.filterNot(claves.contains).forall({{Fuente}}CurationSpec.OmitirSiNulas))
  }

{{pruebas_pendientes_comentario}}
}
