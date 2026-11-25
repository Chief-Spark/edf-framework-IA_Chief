// CUR-TPL-11 · esqueleto · generado por el skill edf-curation v0.1.0
// Tests de la fuente SISBEN sobre eventos aceptados SINTÉTICOS, sin Spark. Usa el núcleo puro
// SISBENCurationProcessor.curar; requiere el JAR del framework en el classpath para compilar
// (CuratedEvent, TaggedValidatedEvent): compilación NO CERTIFICADA (F-01).
// Los JSON esperados salen de la implementación de referencia (scripts/curation_reference.py).
// AUD-C9: estas pruebas llaman al núcleo (curar, relacion), no a curateEvent ni al gancho subscriberRelation,
// que necesitan un TaggedValidatedEvent del JAR. Esa prueba se escribe en el fork (runbook-ejecucion.md §2,
// «Prueba del lado del fork (AUD-C9)»; HF-50).
package com.experian.edf.oxygen.curation.source.sisben

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner
import com.experian.edf.oxygen.curation.source.sisben.config.SISBENCurationSpec
import com.experian.edf.oxygen.curation.source.sisben.processor.SISBENCurationProcessor

@RunWith(classOf[JUnitRunner])
class SISBENCurationProcessorSpec extends AnyFunSuite {

  private def curarJson(json: String, archivo: String, errores: Seq[(String, String)] = Nil): String =
    SISBENCuratedEvent.toJson(SISBENCurationProcessor.curar(json, archivo, errores, SISBENCurationSpec.Homologacion))

  // ---- Casos del fixture (generados desde CUR-TPL-12 y la salida esperada)
  test("evt-0001: Regular, todo válido; CC (3 -> 1), ceros y espacios del documento, padding DANE, tildes y ñ") {
    val esperado = """{"idFichaOrigen":"0999000001","idHogar":1,"codDepartamentoSisben":"05","nomDepartamento":"ANTIOQUIA","codMunicipioSisben":"05001","nomMunicipio":"MEDELLIN","idClase":1,"idPersonaOrden":1,"idNacional":999000001,"primerApellido":"PRUEBANEZ","segundoApellido":"FICTICIO","primerNombre":"SINTETICO","segundoNombre":"JOSE MARIA","tipoDocumento":1,"tipoDocumentoSisben":"3","numeroDocumento":"9990001","grupoSisben":"A","nivelSisben":1,"fechaDigitacion":1705276800000,"fechaActualizacionPersona":1705276800000,"idMarca":0,"idEstado":10,"fechaActualizacionCns":1705314600000,"fechaCorte":1706659200000,"tipoArchivo":"REGULAR","currentPartyType":1,"currentPartyIdNumber":"9990001","subscriberBlockStatus":"NO_BLOQUEADO","subscriberBlockReason":null,"advertenciasCuracion":[]}"""
    assert(curarJson("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"1","ID_NACIONAL":"999000001","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"Ficticio","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"3","NUM_DOCUMENTO":" 0009990001 ","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"15/01/2024","FECHA_ACTUALIZACION_PERSONA":"15/01/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", Seq()) == esperado)
  }

  test("evt-0002: PPT -> 11; el número de documento se conserva tal cual") {
    val esperado = """{"idFichaOrigen":"0999000001","idHogar":1,"codDepartamentoSisben":"05","nomDepartamento":"ANTIOQUIA","codMunicipioSisben":"05001","nomMunicipio":"MEDELLIN","idClase":1,"idPersonaOrden":2,"idNacional":999000002,"primerApellido":"PRUEBANEZ","segundoApellido":"FICTICIO","primerNombre":"SINTETICO","segundoNombre":"JOSE MARIA","tipoDocumento":11,"tipoDocumentoSisben":"PPT","numeroDocumento":"0099900002","grupoSisben":"A","nivelSisben":1,"fechaDigitacion":1705276800000,"fechaActualizacionPersona":1705276800000,"idMarca":0,"idEstado":10,"fechaActualizacionCns":1705314600000,"fechaCorte":1706659200000,"tipoArchivo":"REGULAR","currentPartyType":11,"currentPartyIdNumber":"0099900002","subscriberBlockStatus":"NO_BLOQUEADO","subscriberBlockReason":null,"advertenciasCuracion":[]}"""
    assert(curarJson("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"2","ID_NACIONAL":"999000002","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"Ficticio","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"PPT","NUM_DOCUMENTO":"0099900002","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"15/01/2024","FECHA_ACTUALIZACION_PERSONA":"15/01/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", Seq()) == esperado)
  }

  test("evt-0003: PEP (8 -> 9); se eliminan letras y ceros a la izquierda") {
    val esperado = """{"idFichaOrigen":"0999000001","idHogar":1,"codDepartamentoSisben":"05","nomDepartamento":"ANTIOQUIA","codMunicipioSisben":"05001","nomMunicipio":"MEDELLIN","idClase":1,"idPersonaOrden":1,"idNacional":999000003,"primerApellido":"PRUEBANEZ","segundoApellido":"FICTICIO","primerNombre":"SINTETICO","segundoNombre":"JOSE MARIA","tipoDocumento":9,"tipoDocumentoSisben":"8","numeroDocumento":"9990003","grupoSisben":"A","nivelSisben":1,"fechaDigitacion":1705276800000,"fechaActualizacionPersona":1705276800000,"idMarca":0,"idEstado":10,"fechaActualizacionCns":1705314600000,"fechaCorte":1706659200000,"tipoArchivo":"REGULAR","currentPartyType":9,"currentPartyIdNumber":"9990003","subscriberBlockStatus":"NO_BLOQUEADO","subscriberBlockReason":null,"advertenciasCuracion":[]}"""
    assert(curarJson("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"1","ID_NACIONAL":"999000003","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"Ficticio","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"8","NUM_DOCUMENTO":"PE0999A0003","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"15/01/2024","FECHA_ACTUALIZACION_PERSONA":"15/01/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", Seq()) == esperado)
  }

  test("evt-0004: SEG_APELLIDO con SISBEN_INC_011 (severidad 3) -> la clave segundoApellido no se almacena (RF-CUR-10); CE (4 -> 4)") {
    val esperado = """{"idFichaOrigen":"0999000001","idHogar":1,"codDepartamentoSisben":"05","nomDepartamento":"ANTIOQUIA","codMunicipioSisben":"05001","nomMunicipio":"MEDELLIN","idClase":1,"idPersonaOrden":1,"idNacional":999000004,"primerApellido":"PRUEBANEZ","primerNombre":"SINTETICO","segundoNombre":"JOSE MARIA","tipoDocumento":4,"tipoDocumentoSisben":"4","numeroDocumento":"9990004","grupoSisben":"A","nivelSisben":1,"fechaDigitacion":1705276800000,"fechaActualizacionPersona":1705276800000,"idMarca":0,"idEstado":10,"fechaActualizacionCns":1705314600000,"fechaCorte":1706659200000,"tipoArchivo":"REGULAR","currentPartyType":4,"currentPartyIdNumber":"9990004","subscriberBlockStatus":"NO_BLOQUEADO","subscriberBlockReason":null,"advertenciasCuracion":[]}"""
    assert(curarJson("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"1","ID_NACIONAL":"999000004","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"4","NUM_DOCUMENTO":"9990004","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"15/01/2024","FECHA_ACTUALIZACION_PERSONA":"15/01/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", Seq(("SISBEN_INC_011", "3"))) == esperado)
  }

  test("evt-0005: RT; fecha con formato largo; FECHA_ACTUALIZACION_PERSONA imposible -> nula + advertencia") {
    val esperado = """{"idFichaOrigen":"0999000001","idHogar":1,"codDepartamentoSisben":"05","nomDepartamento":"ANTIOQUIA","codMunicipioSisben":"05001","nomMunicipio":"MEDELLIN","idClase":1,"idPersonaOrden":1,"idNacional":999000005,"primerApellido":"PRUEBANEZ","segundoApellido":"FICTICIO","primerNombre":"SINTETICO","segundoNombre":"JOSE MARIA","tipoDocumento":8,"tipoDocumentoSisben":"5","numeroDocumento":"9990005","grupoSisben":"A","nivelSisben":1,"fechaDigitacion":1705738500000,"fechaActualizacionPersona":null,"idMarca":0,"idEstado":10,"fechaActualizacionCns":1705314600000,"fechaCorte":1706659200000,"tipoArchivo":"RT","currentPartyType":8,"currentPartyIdNumber":"9990005","subscriberBlockStatus":"NO_BLOQUEADO","subscriberBlockReason":null,"advertenciasCuracion":["fechaActualizacionPersona:FORMATO_INVALIDO"]}"""
    assert(curarJson("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"1","ID_NACIONAL":"999000005","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"Ficticio","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"5","NUM_DOCUMENTO":"9990005","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"2024-01-20 08:15:00","FECHA_ACTUALIZACION_PERSONA":"31/02/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "RT_SISBEN_EXPERIAN_202401.20240206080000-0500.txt", Seq()) == esperado)
  }

  test("evt-0006: EP (sin extensión en lineage.fileName); ID_HOGAR no numérico -> nulo + advertencia") {
    val esperado = """{"idFichaOrigen":"0999000001","idHogar":null,"codDepartamentoSisben":"05","nomDepartamento":"ANTIOQUIA","codMunicipioSisben":"05001","nomMunicipio":"MEDELLIN","idClase":1,"idPersonaOrden":1,"idNacional":999000006,"primerApellido":"PRUEBANEZ","segundoApellido":"FICTICIO","primerNombre":"SINTETICO","segundoNombre":"JOSE MARIA","tipoDocumento":5,"tipoDocumentoSisben":"6","numeroDocumento":"AB0999006","grupoSisben":"A","nivelSisben":1,"fechaDigitacion":1705276800000,"fechaActualizacionPersona":1705276800000,"idMarca":0,"idEstado":10,"fechaActualizacionCns":1705314600000,"fechaCorte":1709164800000,"tipoArchivo":"EP","currentPartyType":5,"currentPartyIdNumber":"AB0999006","subscriberBlockStatus":"NO_BLOQUEADO","subscriberBlockReason":null,"advertenciasCuracion":["idHogar:FORMATO_INVALIDO"]}"""
    assert(curarJson("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"X1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"1","ID_NACIONAL":"999000006","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"Ficticio","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"6","NUM_DOCUMENTO":"AB0999006","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"15/01/2024","FECHA_ACTUALIZACION_PERSONA":"15/01/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "EP_SISBEN_EXPERIAN_202402.20240305091500-0500", Seq()) == esperado)
  }

  test("evt-0007: Defensivo; salvoconducto (7) no debería llegar aceptado (SISBEN_ERR_023 sev 1); si llega -> nulo + advertencia, sin código arbitrario") {
    val esperado = """{"idFichaOrigen":"0999000001","idHogar":1,"codDepartamentoSisben":"05","nomDepartamento":"ANTIOQUIA","codMunicipioSisben":"05001","nomMunicipio":"MEDELLIN","idClase":1,"idPersonaOrden":1,"idNacional":999000007,"primerApellido":"PRUEBANEZ","segundoApellido":"FICTICIO","primerNombre":"SINTETICO","segundoNombre":"JOSE MARIA","tipoDocumento":null,"tipoDocumentoSisben":"7","numeroDocumento":"9990007","grupoSisben":"A","nivelSisben":1,"fechaDigitacion":1705276800000,"fechaActualizacionPersona":1705276800000,"idMarca":0,"idEstado":10,"fechaActualizacionCns":1705314600000,"fechaCorte":1706659200000,"tipoArchivo":"REGULAR","currentPartyType":null,"currentPartyIdNumber":"9990007","subscriberBlockStatus":"NO_BLOQUEADO","subscriberBlockReason":null,"advertenciasCuracion":["tipoDocumento:HOMOLOGACION_PENDIENTE"]}"""
    assert(curarJson("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"1","ID_NACIONAL":"999000007","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"Ficticio","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"7","NUM_DOCUMENTO":"9990007","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"15/01/2024","FECHA_ACTUALIZACION_PERSONA":"15/01/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", Seq()) == esperado)
  }

  // ---- subscriberRelation (extension_subscriber_relation, D14)
  // Sin extension_subscriber_relation: subscriberRelation se copia del evento aceptado.

  // ---- Lector de validatedRecordJson (mismo texto que la referencia, AUD-C5)
  test("lector: booleanos, números y JSON anidado como Gson (AUD-C5)") {
    val r = SISBENAcceptedRecord.parse("""{" a ": true, "b": [1, "x", 1.50, null], "c": {"k": 1.5e3, "t": "q\"z"}, "d": null, "e": 2.0, "f": 0.10000000000000001, "g": 100, "h": false, "i": -0.0}""").toOption.get
    assert(r.get("A") == Some("true"))
    assert(r.get("B") == Some("[1,\"x\",1.50,null]"))
    assert(r.get("C") == Some("{\"k\":1.5e3,\"t\":\"q\\\"z\"}"))
    assert(r.get("D") == None)
    assert(r.get("E") == Some("2"))
    assert(r.get("F") == Some("0.10000000000000001"))
    assert(r.get("G") == Some("100"))
    assert(r.get("H") == Some("false"))
    assert(r.get("I") == Some("0"))
  }

  test("lector: crudo conserva el valor sin recortar; get lo recorta (HF-34)") {
    val r = SISBENAcceptedRecord.parse("""{"a": " 0001000000101", "b": "   ", "c": null}""").toOption.get
    assert(r.crudo("A") == Some(" 0001000000101"))
    assert(r.get("A") == Some("0001000000101"))
    assert(r.crudo("B") == Some("   "))
    assert(r.get("B") == None)
    assert(r.crudo("C") == None)
    assert(r.get("C") == None)
  }

  // ---- Propiedades fijas del contrato
  test("write/read: ida y vuelta conserva el JSON") {
    val ev = new SISBENCuratedEvent(curarJson("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"1","ID_NACIONAL":"999000001","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"Ficticio","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"3","NUM_DOCUMENTO":" 0009990001 ","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"15/01/2024","FECHA_ACTUALIZACION_PERSONA":"15/01/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt"))
    assert(ev.read(ev.write).write == ev.write)
  }

  test("evento con todos los campos inválidos: no lanza y trae advertencias") {
    val record = SISBENCurationProcessor.curar("{no es json", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", Nil, SISBENCurationSpec.Homologacion)
    assert(record.advertenciasCuracion.contains("registro:REGISTRO_ILEGIBLE"))
  }

  test("null y basura no lanzan; errores nulo = sin errores (AUD-C5)") {
    val record = SISBENCurationProcessor.curar(null, null, null, Map.empty)
    assert(record.advertenciasCuracion.contains("registro:REGISTRO_ILEGIBLE"))
    assert(!record.advertenciasCuracion.contains("registro:ERROR_INTERNO"))
  }

  test("claves del evento = domain dictionary (CUR-RC05); solo faltan claves de identidad nulas (AUD-C4)") {
    val record = SISBENCurationProcessor.curar("""{"ID_FICHA_ORIGEN":"0999000001","ID_HOGAR":"1","COD_DPTO":"5","NOM_DPTO":"Antioquia","COD_MPIO":"5001","NOM_MPIO":"Medellín","ID_CLASE":"1","ID_PERSONA_ORDEN":"1","ID_NACIONAL":"999000001","PRI_APELLIDO":"Pruebañez","SEG_APELLIDO":"Ficticio","PRI_NOMBRE":"Sintético","SEG_NOMBRE":"José-María","TIP_DOCUMENTO":"3","NUM_DOCUMENTO":" 0009990001 ","ID_GRUPO":"A","ID_NIVEL":"1","FECHA_DIGITACION":"15/01/2024","FECHA_ACTUALIZACION_PERSONA":"15/01/2024","ID_MARCA":"0","ID_ESTADO":"10","FECHA_ACTUALIZACION_CNS":"2024-01-15 10:30:00"}""", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", Nil, SISBENCurationSpec.Homologacion)
    import scala.collection.JavaConverters._
    val claves = record.toJavaMap.keySet.asScala.toSeq
    assert(claves == SISBENCurationSpec.ClavesPublicadas.filter(claves.contains))
    assert(SISBENCurationSpec.ClavesPublicadas.filterNot(claves.contains).forall(SISBENCurationSpec.OmitirSiNulas))
  }

  // TODO(manual): casos de enriquecimiento (IN-06) y de la derivación del tipo de archivo cuando
  // se cierren CUR-PT06 y CUR-PA03. El payload se conserva: probarlo con un TaggedValidatedEvent
  // real del framework (enrichEventWithPayload devuelve event.payload).
}
