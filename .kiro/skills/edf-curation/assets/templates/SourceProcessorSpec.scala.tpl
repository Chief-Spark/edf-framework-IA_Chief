// CUR-TPL-11 · esqueleto · generado por el skill edf-curation-stage v{{version_skill}}
// Tests de la fuente {{Fuente}} sobre eventos aceptados SINTÉTICOS, sin Spark. Usa el núcleo puro
// {{processor_clase}}.curar; requiere el JAR del framework en el classpath para compilar
// (CuratedEvent, TaggedValidatedEvent): compilación NO CERTIFICADA (F-01).
// Los JSON esperados salen de la implementación de referencia (scripts/curation_reference.py).
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

  // ---- Propiedades fijas del contrato
  test("write/read: ida y vuelta conserva el JSON") {
    val ev = new {{Fuente}}CuratedEvent(curarJson({{evento_base_json_scala}}, "{{archivo_base}}"))
    assert(ev.read(ev.write).write == ev.write)
  }

  test("evento con todos los campos inválidos: no lanza y trae advertencias") {
    val record = {{processor_clase}}.curar("{no es json", "{{archivo_base}}", Nil, {{Fuente}}CurationSpec.Homologacion)
    assert(record.{{campo_advertencias}}.contains("registro:REGISTRO_ILEGIBLE"))
  }

  test("null y basura no lanzan") {
    assert({{processor_clase}}.curar(null, null, null, Map.empty).{{campo_advertencias}}.nonEmpty)
  }

  test("claves del evento = domain dictionary (CUR-RC05)") {
    val record = {{processor_clase}}.curar({{evento_base_json_scala}}, "{{archivo_base}}", Nil, {{Fuente}}CurationSpec.Homologacion)
    import scala.collection.JavaConverters._
    assert(record.toJavaMap.keySet.asScala.toSeq == {{Fuente}}CurationSpec.ClavesPublicadas)
  }

  // TODO(manual): casos de enriquecimiento (IN-06) y de la derivación del tipo de archivo cuando
  // se cierren CUR-PT06 y CUR-PA03. El payload se conserva: probarlo con un TaggedValidatedEvent
  // real del framework (enrichEventWithPayload devuelve event.payload).
}
