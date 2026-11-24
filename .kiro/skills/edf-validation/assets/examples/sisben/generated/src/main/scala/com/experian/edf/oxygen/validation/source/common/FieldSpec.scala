// VAL-TPL-01 · Capa A · plantilla fija (solo se rellena el paquete).
// Generado por el skill edf-validation-stage. No editar a mano: regenerar desde el source-spec.
// Tipos con los que se declara una fuente. Sin Spark ni framework Oxygen: compila solo con Scala 2.12.
// ASSUMPTION VAL-PT01: el código compartido (Capa A) vive en el paquete com.experian.edf.oxygen.validation.source.common.
package com.experian.edf.oxygen.validation.source.common

/** Severidad de un diagnóstico. El framework la trata como texto libre (ObjectError.errorLevel);
  * aquí es un tipo sellado (VAL-AP06, VAL-T05). Niveles de un solo carácter (VAL-P09). */
sealed trait Severidad extends Serializable { def nivel: String }

object Severidad {
  case object Error extends Severidad { val nivel: String = "1" }
  case object Inconsistencia extends Severidad { val nivel: String = "3" }

  def desdeNivel(nivel: String): Option[Severidad] = nivel match {
    case "1" => Some(Error)
    case "3" => Some(Inconsistencia)
    case _   => None
  }
}

/** Tipo de dato declarado de un campo. */
sealed trait TipoDato extends Serializable

object TipoDato {
  case object Alfanumerico extends TipoDato
  case object Numerico extends TipoDato
  case object Fecha extends TipoDato
}

/** Normalizaciones que se aplican SOLO en memoria para validar (RV-GEN-07). El valor persistido no cambia. */
sealed trait Normalizacion extends Serializable

object Normalizacion {
  case object QuitarEspacios extends Normalizacion
  case object QuitarCerosIzquierda extends Normalizacion
}

/** Referencia a una tabla de lookup (oxygen.lookups[]) y a la columna donde se busca el valor. */
final case class CatalogoRef(tabla: String, columna: String)

/** Rango entero inclusivo. */
final case class RangoEntero(min: Long, max: Long)

/** Declaración de un campo del layout. Un único código de error por campo (F-10). */
final case class FieldSpec(
  nombre: String,
  posicion: Int,
  aliasHeader: Seq[String],
  obligatorio: Boolean,
  severidad: Severidad,
  tipo: TipoDato,
  longitudMax: Option[Int],
  rango: Option[RangoEntero],
  dominio: Option[Set[String]],
  catalogo: Option[CatalogoRef],
  formatosFecha: Seq[String],
  noFutura: Boolean,
  normalizacion: Seq[Normalizacion],
  codigoError: String
)

/** Reglas entre campos expresadas como datos (parte "manual" de VAL-TPL-06 llevada a la especificación). */
sealed trait ReglaEntreCampos extends Serializable { def id: String; def codigo: String; def severidad: Severidad }

object ReglaEntreCampos {
  /** El nombre debe corresponder con el código según el catálogo (p. ej. DIVIPOLA código ↔ nombre). */
  final case class CorrespondenciaCatalogo(
    id: String,
    campoCodigo: String,
    campoNombre: String,
    tabla: String,
    columnaClave: String,
    columnaNombre: String,
    codigo: String,
    severidad: Severidad
  ) extends ReglaEntreCampos

  /** Valores aceptados por el catálogo pero que requieren un código propio (p. ej. salvoconducto). */
  final case class ValorConCodigoPropio(
    id: String,
    campo: String,
    valores: Set[String],
    codigo: String,
    severidad: Severidad,
    causa: String
  ) extends ReglaEntreCampos
}

/** Filtro de filas crudas (shouldFilterRow): silencioso, fuera de todo quality score. */
final case class FiltroFilas(lineasVacias: Boolean, posicionCampo: Option[Int], valores: Set[String])

/** Quality score de registro: vale 0.0 si el registro tiene algún error con un nivel de `anulanNiveles`. */
final case class QualityScoreSpec(nombre: String, anulanNiveles: Set[String])

/** Contrato del nombre de archivo: regex anclado sobre el nombre (sin ruta) y sus grupos. */
final case class NombreArchivoSpec(
  regex: String,
  grupoPrefijo: Int,
  grupoPeriodo: Int,
  grupoMarcaTiempo: Int,
  grupoOffset: Int,
  fileTagPorPrefijo: Map[String, String]
)

/** Duplicados dentro del archivo (E12). */
final case class DuplicadosSpec(
  habilitado: Boolean,
  clave: Seq[String],
  campoFecha: Option[String]
)

/** Códigos fijos de la fuente que no pertenecen a un campo. */
final case class CodigosEspeciales(nombreArchivo: String, header: String, estructura: String, interno: String)

/** Especificación completa de la fuente (Capa B la instancia). */
final case class SourceSpec(
  nombre: String,
  campos: Seq[FieldSpec],
  delimitador: String,
  filasHeader: Int,
  nombreArchivo: NombreArchivoSpec,
  subscriber: String,
  dataSource: String,
  eventType: Int,
  zonaHoraria: String,
  reglas: Seq[ReglaEntreCampos],
  filtro: FiltroFilas,
  qualityScores: Seq[QualityScoreSpec],
  duplicados: DuplicadosSpec,
  campoSubscriberRelation: String,
  campoAsOfDate: String,
  codigos: CodigosEspeciales
) {
  lazy val numeroCampos: Int = campos.size
  lazy val porNombre: Map[String, FieldSpec] = campos.map(c => c.nombre -> c).toMap
  def campo(nombre: String): Option[FieldSpec] = porNombre.get(nombre)
}

/** Diagnóstico propio de Capa A. Capa C lo traduce a ObjectError del framework. */
final case class FieldError(campo: String, codigo: String, nivel: String, causa: String)

/** Catálogo consultable. Capa C lo implementa sobre el lookup del framework; los tests, en memoria. */
trait Catalogo extends Serializable {
  def existe(tabla: String, columna: String, valor: String): Boolean
  def buscar(tabla: String, columnaClave: String, clave: String, columnaSalida: String): Option[String]
}

/** Catálogo sobre una consulta de lookup: (tabla, filtro columna -> valor, columna a leer) => valores
  * ("" = sin coincidencia, como Util.getLookup del motor). Capa C lo usa con el lookup real.
  * ADR-01: NO captura excepciones de la consulta. Una falla del lookup se propaga para que
  * RecordValidator.validarRegistro la convierta en el código interno (nivel 1), en vez de tratarla como
  * "valor fuera de catálogo". */
class CatalogoDeConsulta(consultar: (String, Map[String, String], String) => Seq[String]) extends Catalogo {
  def existe(tabla: String, columna: String, valor: String): Boolean =
    consultar(tabla, Map(columna -> valor), columna).exists(_.nonEmpty)
  def buscar(tabla: String, columnaClave: String, clave: String, columnaSalida: String): Option[String] =
    consultar(tabla, Map(columnaClave -> clave), columnaSalida).find(_.nonEmpty)
}
