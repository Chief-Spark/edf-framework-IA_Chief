// VAL-TPL-03 · Capa B · plantilla parametrizada.
// Generado por el skill edf-validation-stage desde source-spec (sisben-iv-1.0.0). No editar a mano.
// Especificación de SISBEN como datos: es la fuente de verdad del evento, del header y de los tests.
package com.experian.edf.oxygen.validation.source.sisben.config

import com.experian.edf.oxygen.validation.source.common._
import com.experian.edf.oxygen.validation.source.common.ReglaEntreCampos._

object SISBENSpec {

  // Constantes de nombre de campo (en el orden del layout)
  val ID_FICHA_ORIGEN: String = "ID_FICHA_ORIGEN"
  val ID_HOGAR: String = "ID_HOGAR"
  val COD_DPTO: String = "COD_DPTO"
  val NOM_DPTO: String = "NOM_DPTO"
  val COD_MPIO: String = "COD_MPIO"
  val NOM_MPIO: String = "NOM_MPIO"
  val ID_CLASE: String = "ID_CLASE"
  val ID_PERSONA_ORDEN: String = "ID_PERSONA_ORDEN"
  val ID_NACIONAL: String = "ID_NACIONAL"
  val PRI_APELLIDO: String = "PRI_APELLIDO"
  val SEG_APELLIDO: String = "SEG_APELLIDO"
  val PRI_NOMBRE: String = "PRI_NOMBRE"
  val SEG_NOMBRE: String = "SEG_NOMBRE"
  val TIP_DOCUMENTO: String = "TIP_DOCUMENTO"
  val NUM_DOCUMENTO: String = "NUM_DOCUMENTO"
  val ID_GRUPO: String = "ID_GRUPO"
  val ID_NIVEL: String = "ID_NIVEL"
  val FECHA_DIGITACION: String = "FECHA_DIGITACION"
  val FECHA_ACTUALIZACION_PERSONA: String = "FECHA_ACTUALIZACION_PERSONA"
  val ID_MARCA: String = "ID_MARCA"
  val ID_ESTADO: String = "ID_ESTADO"
  val FECHA_ACTUALIZACION_CNS: String = "FECHA_ACTUALIZACION_CNS"

  val CamposFuente: Seq[FieldSpec] = Seq(
    FieldSpec(nombre = ID_FICHA_ORIGEN, posicion = 1, aliasHeader = Seq("ID_FICHA_ORIGEN", "ide_ficha_origen"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Numerico,
      longitudMax = Some(20), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_INC_001"),
    FieldSpec(nombre = ID_HOGAR, posicion = 2, aliasHeader = Seq("ID_HOGAR", "ide_hogar"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Numerico,
      longitudMax = Some(20), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_INC_002"),
    FieldSpec(nombre = COD_DPTO, posicion = 3, aliasHeader = Seq("COD_DPTO", "cod_dpto"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Numerico,
      longitudMax = Some(2), rango = None, dominio = None,
      catalogo = Some(CatalogoRef("102_DANE_DIVIPOLA_STATES_CODES_NH", "cod_dpto")), formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_003"),
    FieldSpec(nombre = NOM_DPTO, posicion = 4, aliasHeader = Seq("NOM_DPTO", "nom_dpto"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(60), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_004"),
    FieldSpec(nombre = COD_MPIO, posicion = 5, aliasHeader = Seq("COD_MPIO", "cod_mpio"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Numerico,
      longitudMax = Some(5), rango = None, dominio = None,
      catalogo = Some(CatalogoRef("2_DANE_DIVIPOLA_CITIES_CODES_NH", "cod_mpio")), formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_005"),
    FieldSpec(nombre = NOM_MPIO, posicion = 6, aliasHeader = Seq("NOM_MPIO", "nom_mpio"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(60), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_006"),
    FieldSpec(nombre = ID_CLASE, posicion = 7, aliasHeader = Seq("ID_CLASE", "cod_clase"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Numerico,
      longitudMax = Some(1), rango = None, dominio = Some(Set("1", "2", "3")),
      catalogo = Some(CatalogoRef("201_SISBEN_ZONES_NH", "cod_clase")), formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_007"),
    FieldSpec(nombre = ID_PERSONA_ORDEN, posicion = 8, aliasHeader = Seq("ID_PERSONA_ORDEN", "ide_persona"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Numerico,
      longitudMax = Some(20), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_INC_008"),
    FieldSpec(nombre = ID_NACIONAL, posicion = 9, aliasHeader = Seq("ID_NACIONAL", "ide_nacional"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Numerico,
      longitudMax = Some(20), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_INC_009"),
    FieldSpec(nombre = PRI_APELLIDO, posicion = 10, aliasHeader = Seq("PRI_APELLIDO", "pri_apellido"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(30), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_010"),
    FieldSpec(nombre = SEG_APELLIDO, posicion = 11, aliasHeader = Seq("SEG_APELLIDO", "seg_apellido"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(30), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_INC_011"),
    FieldSpec(nombre = PRI_NOMBRE, posicion = 12, aliasHeader = Seq("PRI_NOMBRE", "pri_nombre"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(30), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_012"),
    FieldSpec(nombre = SEG_NOMBRE, posicion = 13, aliasHeader = Seq("SEG_NOMBRE", "seg_nombre"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(30), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_INC_013"),
    FieldSpec(nombre = TIP_DOCUMENTO, posicion = 14, aliasHeader = Seq("TIP_DOCUMENTO", "tip_documento"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(3), rango = None, dominio = Some(Set("1", "2", "3", "4", "5", "6", "7", "8", "PPT")),
      catalogo = Some(CatalogoRef("202_SISBEN_PROCESS_ID_TYPE_HO", "cod_sisben")), formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_014"),
    FieldSpec(nombre = NUM_DOCUMENTO, posicion = 15, aliasHeader = Seq("NUM_DOCUMENTO", "num_documento"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(15), rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(Normalizacion.QuitarEspacios, Normalizacion.QuitarCerosIzquierda), codigoError = "SISBEN_ERR_015"),
    FieldSpec(nombre = ID_GRUPO, posicion = 16, aliasHeader = Seq("ID_GRUPO", "grupo"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Alfanumerico,
      longitudMax = Some(1), rango = None, dominio = Some(Set("A", "B", "C", "D")),
      catalogo = Some(CatalogoRef("234_SISBEN_GROUP_SUBGROUP_NH", "grupo")), formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_016"),
    FieldSpec(nombre = ID_NIVEL, posicion = 17, aliasHeader = Seq("ID_NIVEL", "nivel"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Numerico,
      longitudMax = Some(2), rango = Some(RangoEntero(1L, 21L)), dominio = None,
      catalogo = None, formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_017"),
    FieldSpec(nombre = FECHA_DIGITACION, posicion = 18, aliasHeader = Seq("FECHA_DIGITACION", "fec_digitacion"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Fecha,
      longitudMax = None, rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq("yyyy-MM-dd", "dd/MM/yyyy"), noFutura = true,
      normalizacion = Seq(), codigoError = "SISBEN_INC_018"),
    FieldSpec(nombre = FECHA_ACTUALIZACION_PERSONA, posicion = 19, aliasHeader = Seq("FECHA_ACTUALIZACION_PERSONA", "fec_actualizacion_persona"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Fecha,
      longitudMax = None, rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq("yyyy-MM-dd", "dd/MM/yyyy"), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_INC_019"),
    FieldSpec(nombre = ID_MARCA, posicion = 20, aliasHeader = Seq("ID_MARCA", "marca"),
      obligatorio = false, severidad = Severidad.Inconsistencia, tipo = TipoDato.Numerico,
      longitudMax = None, rango = None, dominio = Some(Set("0", "1", "10", "20")),
      catalogo = Some(CatalogoRef("203_SISBEN_RECORD_STATUS_AND_MARK_NH", "codigo")), formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_INC_020"),
    FieldSpec(nombre = ID_ESTADO, posicion = 21, aliasHeader = Seq("ID_ESTADO", "estado"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Numerico,
      longitudMax = None, rango = None, dominio = Some(Set("0", "10", "40", "41", "42", "43", "60", "61", "69", "70", "90", "91")),
      catalogo = Some(CatalogoRef("203_SISBEN_RECORD_STATUS_AND_MARK_NH", "codigo")), formatosFecha = Seq(), noFutura = false,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_021"),
    FieldSpec(nombre = FECHA_ACTUALIZACION_CNS, posicion = 22, aliasHeader = Seq("FECHA_ACTUALIZACION_CNS", "fec_actualizacion_cns"),
      obligatorio = true, severidad = Severidad.Error, tipo = TipoDato.Fecha,
      longitudMax = None, rango = None, dominio = None,
      catalogo = None, formatosFecha = Seq("yyyy-MM-dd", "dd/MM/yyyy"), noFutura = true,
      normalizacion = Seq(), codigoError = "SISBEN_ERR_022")
  )

  val Reglas: Seq[ReglaEntreCampos] = Seq(
    CorrespondenciaCatalogo(id = "RV-SIS-01", campoCodigo = COD_DPTO, campoNombre = NOM_DPTO,
      tabla = "102_DANE_DIVIPOLA_STATES_CODES_NH", columnaClave = "cod_dpto", columnaNombre = "nom_dpto",
      codigo = "SISBEN_ERR_004", severidad = Severidad.Error),
    CorrespondenciaCatalogo(id = "RV-SIS-02", campoCodigo = COD_MPIO, campoNombre = NOM_MPIO,
      tabla = "2_DANE_DIVIPOLA_CITIES_CODES_NH", columnaClave = "cod_mpio", columnaNombre = "nom_mpio",
      codigo = "SISBEN_ERR_006", severidad = Severidad.Error),
    CorrespondenciaCatalogo(id = "RV-SIS-03", campoCodigo = COD_MPIO, campoNombre = COD_DPTO,
      tabla = "2_DANE_DIVIPOLA_CITIES_CODES_NH", columnaClave = "cod_mpio", columnaNombre = "cod_dpto",
      codigo = "SISBEN_ERR_028", severidad = Severidad.Error),
    ValorConCodigoPropio(id = "RV-SIS-04", campo = TIP_DOCUMENTO, valores = Set("7"),
      codigo = "SISBEN_ERR_023", severidad = Severidad.Error, causa = "TIPO_PENDIENTE_CREACION"),
    ValorConCodigoPropio(id = "RV-SIS-05", campo = NUM_DOCUMENTO, valores = Set("0", "00", "000", "0000", "00000", "000000", "0000000", "00000000", "000000000", "0000000000", "00000000000", "000000000000", "0000000000000", "00000000000000", "000000000000000"),
      codigo = "SISBEN_ERR_029", severidad = Severidad.Error, causa = "DOCUMENTO_CERO")
  )

  val Spec: SourceSpec = SourceSpec(
    nombre = "SISBEN",
    campos = CamposFuente,
    delimitador = ";",
    filasHeader = 1,
    nombreArchivo = NombreArchivoSpec(
      regex = "()SISBEN_EXPERIAN_(\\d{6})\\.(\\d{14})([+-]\\d{4})\\.(txt|TXT)",
      grupoPrefijo = 1,
      grupoPeriodo = 2,
      grupoMarcaTiempo = 3,
      grupoOffset = 4,
      fileTagPorPrefijo = Map("" -> "REGULAR")
    ),
    subscriber = "999722",
    dataSource = "DNP",
    eventType = 30,
    zonaHoraria = "America/Bogota",
    reglas = Reglas,
    filtro = FiltroFilas(lineasVacias = true, posicionCampo = Some(14), valores = Set("1", "2")),
    qualityScores = Seq(QualityScoreSpec("RQS", Set("1"))),
    duplicados = DuplicadosSpec(habilitado = true, clave = Seq(TIP_DOCUMENTO, NUM_DOCUMENTO), campoFecha = Some(FECHA_ACTUALIZACION_CNS)),
    campoSubscriberRelation = NUM_DOCUMENTO,
    campoAsOfDate = FECHA_ACTUALIZACION_CNS,
    codigos = CodigosEspeciales(
      // ASSUMPTION F-11: códigos provisionales del nombre de archivo y del header.
      nombreArchivo = "SISBEN_ERR_024",
      header = "SISBEN_ERR_025",
      // ASSUMPTION F-09: fila con un número de columnas distinto al layout → error de nivel 1, sin excepción.
      estructura = "SISBEN_ERR_026",
      // ASSUMPTION VAL-SP01: código para un error interno inesperado al validar un registro (red de seguridad).
      interno = "SISBEN_ERR_027"
    )
  )
}
