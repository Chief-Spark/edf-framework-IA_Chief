// CUR-TPL-10 · Capa A · fija · generado por el skill edf-curation-stage v0.1.0
// Tests de las acciones genéricas (ScalaTest 3.2, runner JUnit 4). No requieren Spark ni el JAR
// del framework: compilan con Scala 2.12 + ScalaTest (check opcional de F-01).
// Datos sintéticos.
package com.experian.edf.oxygen.curation.source.common

import java.time.ZoneOffset
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TransformacionesSpec extends AnyFunSuite {

  private val utc = ZoneOffset.UTC
  private val fmts = Transformaciones.formateadores(Seq("dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss"))
  private val fmtsIso = Transformaciones.formateadores(Seq("uuuu-MM-dd"))

  test("nombre: mayúsculas, sin tildes, sin especiales, Ñ transliterada") {
    assert(Transformaciones.nombre(Some(" José-María "), conservarEnie = false) == Resultado.ok("JOSE MARIA"))
    assert(Transformaciones.nombre(Some("Pruebañez"), conservarEnie = false) == Resultado.ok("PRUEBANEZ"))
    assert(Transformaciones.nombre(Some("Pruebañez"), conservarEnie = true) == Resultado.ok("PRUEBAÑEZ"))
  }

  test("nombre: vacío es nulo sin advertencia; solo símbolos advierte") {
    assert(Transformaciones.nombre(Some("   "), conservarEnie = false) == Resultado.nulo)
    assert(Transformaciones.nombre(Some("123"), conservarEnie = false) == Resultado.advertir(Motivos.NoNormalizable))
  }

  test("padding DANE") {
    assert(Transformaciones.paddingIzquierda(Some("5"), 2) == Resultado.ok("05"))
    assert(Transformaciones.paddingIzquierda(Some("5001"), 5) == Resultado.ok("05001"))
    assert(Transformaciones.paddingIzquierda(Some("A1"), 2) == Resultado.advertir(Motivos.FormatoInvalido))
    assert(Transformaciones.paddingIzquierda(Some("123"), 2) == Resultado.advertir(Motivos.FormatoInvalido))
  }

  test("fecha a epoch ms: dos formatos, estricto") {
    assert(Transformaciones.fechaEpoch(Some("15/01/2024"), fmts, utc) == Resultado.ok(1705276800000L))
    assert(Transformaciones.fechaEpoch(Some("2024-01-15 10:30:00"), fmts, utc) == Resultado.ok(1705314600000L))
    assert(Transformaciones.fechaEpoch(Some("31/02/2024"), fmts, utc) == Resultado.advertir(Motivos.FormatoInvalido))
    assert(Transformaciones.fechaEpoch(Some("1/1/2024"), fmts, utc) == Resultado.advertir(Motivos.FormatoInvalido))
    assert(Transformaciones.fechaEpoch(None, fmts, utc) == Resultado.nulo)
  }

  test("fecha a epoch ms: ISO solo fecha (uuuu-MM-dd), estricto, 00:00 en la zona") {
    assert(Transformaciones.fechaEpoch(Some("2024-01-15"), fmtsIso, utc) == Resultado.ok(1705276800000L))
    assert(Transformaciones.fechaEpoch(Some("2024-02-30"), fmtsIso, utc) == Resultado.advertir(Motivos.FormatoInvalido))
    assert(Transformaciones.fechaEpoch(Some("15/01/2024"), fmtsIso, utc) == Resultado.advertir(Motivos.FormatoInvalido))
    assert(Transformaciones.fechaEpoch(Some("2024-01-15 10:30:00"), fmtsIso, utc) == Resultado.advertir(Motivos.FormatoInvalido))
    // Con varios formatos, el ISO convive con los otros dos.
    val todos = Transformaciones.formateadores(Seq("dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss", "uuuu-MM-dd"))
    assert(Transformaciones.fechaEpoch(Some("2024-01-15"), todos, utc) == Resultado.ok(1705276800000L))
    assert(Transformaciones.fechaEpoch(Some("2024-01-15 10:30:00"), todos, utc) == Resultado.ok(1705314600000L))
  }

  test("homologación: pendiente y desconocido nunca reciben un código") {
    val tabla = Map("3" -> "1", "PPT" -> "11")
    assert(Transformaciones.homologar(Some("ppt"), tabla, Set("7")) == Resultado.ok("11"))
    assert(Transformaciones.homologar(Some("7"), tabla, Set("7")) == Resultado.advertir(Motivos.HomologacionPendiente))
    assert(Transformaciones.homologar(Some("99"), tabla, Set("7")) == Resultado.advertir(Motivos.NoHomologable))
  }

  test("documento: espacios, ceros, solo dígitos, conservar original") {
    val defecto = ReglaDocumento(quitarCerosIzquierda = true, soloDigitos = false, conservarOriginal = false, padding = None)
    val pep = defecto.copy(soloDigitos = true)
    val ppt = defecto.copy(quitarCerosIzquierda = false, conservarOriginal = true)
    assert(Transformaciones.documento(Some(" 000 9990001 "), defecto) == Resultado.ok("9990001"))
    assert(Transformaciones.documento(Some("PE0999A0003"), pep) == Resultado.ok("9990003"))
    assert(Transformaciones.documento(Some("0099900002"), ppt) == Resultado.ok("0099900002"))
    assert(Transformaciones.documento(Some("000"), defecto) == Resultado.advertir(Motivos.NoNormalizable))
    // En blanco = ausente, sin advertencia; un valor no vacío que la regla deja vacío sí advierte (AC-CUR-06).
    assert(Transformaciones.documento(Some("   "), defecto) == Resultado.nulo)
    assert(Transformaciones.documento(Some("AB"), pep) == Resultado.advertir(Motivos.NoNormalizable))
  }

  test("documento: las letras salen en mayúsculas (F-FW-14); conservar original no las toca") {
    val defecto = ReglaDocumento(quitarCerosIzquierda = true, soloDigitos = false, conservarOriginal = false, padding = None)
    val original = defecto.copy(quitarCerosIzquierda = false, conservarOriginal = true)
    assert(Transformaciones.documento(Some(" ab 0012c "), defecto) == Resultado.ok("AB0012C"))
    assert(Transformaciones.documento(Some("0ab12"), defecto) == Resultado.ok("AB12"))
    assert(Transformaciones.documento(Some("pe0999a0003"), defecto.copy(soloDigitos = true)) == Resultado.ok("9990003"))
    assert(Transformaciones.documento(Some("0ab12"), original) == Resultado.ok("0ab12"))
  }

  test("convertir: tipos de salida") {
    assert(Transformaciones.convertir(Some("12"), TipoSalida.Entero) == Resultado.ok(12))
    assert(Transformaciones.convertir(Some("X1"), TipoSalida.Entero) == Resultado.advertir(Motivos.FormatoInvalido))
    assert(Transformaciones.convertir(Some(5L), TipoSalida.Largo) == Resultado.ok(5L))
    assert(Transformaciones.convertir(Some("1.5"), TipoSalida.Decimal) == Resultado.ok(1.5))
    assert(Transformaciones.convertir(None, TipoSalida.Texto) == Resultado.nulo)
  }

  test("concatenar: omite vacíos y nulos; todo vacío es nulo sin advertencia") {
    assert(Transformaciones.concatenar(Seq(Some("ANA"), None, Some("PRUEBA"), Some(" ")), " ") == Resultado.ok("ANA PRUEBA"))
    assert(Transformaciones.concatenar(Seq(Some(1), Some("X")), "_") == Resultado.ok("1_X"))
    assert(Transformaciones.concatenar(Seq(None, Some("")), " ") == Resultado.nulo)
  }

  test("infoArchivo: tipo por prefijo y último día del mes") {
    val rx = """(RT_|EP_)?FUENTE_(\d{6})""".r
    val pref = Map("RT_" -> "RT", "EP_" -> "EP", "" -> "REGULAR")
    assert(Transformaciones.infoArchivo("ruta/RT_FUENTE_202402.txt", rx, pref, utc) == (Some("RT"), Some(1709164800000L)))
    assert(Transformaciones.infoArchivo("FUENTE_202413", rx, pref, utc) == (Some("REGULAR"), None))
    assert(Transformaciones.infoArchivo(null, rx, pref, utc) == (None, None))
  }
}
