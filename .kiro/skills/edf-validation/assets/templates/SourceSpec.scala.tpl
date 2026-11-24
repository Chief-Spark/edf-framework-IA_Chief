// VAL-TPL-03 · Capa B · plantilla parametrizada.
// Generado por la skill edf-validation desde source-spec ({{spec_version}}). No editar a mano.
// Especificación de {{Fuente}} como datos: es la fuente de verdad del evento, del header y de los tests.
package {{paquete_fuente}}.config

import {{paquete_comun}}._
import {{paquete_comun}}.ReglaEntreCampos._

object {{Fuente}}Spec {

  // Constantes de nombre de campo (en el orden del layout)
{{bloque_constantes_campos}}

  val CamposFuente: Seq[FieldSpec] = Seq(
{{bloque_fieldspecs}}
  )

  val Reglas: Seq[ReglaEntreCampos] = Seq(
{{bloque_reglas}}
  )

  val Spec: SourceSpec = SourceSpec(
    nombre = "{{Fuente}}",
    campos = CamposFuente,
    delimitador = "{{delimitador_scala}}",
    filasHeader = {{filas_header}},
    nombreArchivo = NombreArchivoSpec(
      regex = "{{regex_nombre_scala}}",
      grupoPrefijo = {{grupo_prefijo}},
      grupoPeriodo = {{grupo_periodo}},
      grupoMarcaTiempo = {{grupo_marca_tiempo}},
      grupoOffset = {{grupo_offset}},
      fileTagPorPrefijo = Map({{bloque_file_tags}}),
      // Reglas de fecha del nombre (archivo.nombre del source-spec; false = no se aplican, AUD-V-02).
      periodoNoFuturo = {{nombre_periodo_no_futuro}},
      recepcionNoFutura = {{nombre_recepcion_no_futura}},
      recepcionDesdeInicioPeriodo = {{nombre_recepcion_desde_inicio_periodo}}
    ),
    subscriber = "{{subscriber}}",
    dataSource = "{{datasource}}",
    eventType = {{event_type}},
    zonaHoraria = "{{zona_horaria}}",
    reglas = Reglas,
    filtro = FiltroFilas(lineasVacias = {{filtro_lineas_vacias}}, posicionCampo = {{filtro_posicion_scala}}, valores = Set({{filtro_valores_scala}})),
    qualityScores = Seq({{bloque_quality_scores}}),
    duplicados = DuplicadosSpec(habilitado = {{dup_habilitado}}, clave = Seq({{dup_clave_scala}}), campoFecha = {{dup_campo_fecha_scala}}),
    campoSubscriberRelation = {{campo_subscriber_relation}},
    campoAsOfDate = {{campo_as_of_date}},
    codigos = CodigosEspeciales(
      // ASSUMPTION F-11: códigos del nombre de archivo y del header (codigos_especiales del source-spec).
      nombreArchivo = "{{codigo_nombre_archivo}}",
      header = "{{codigo_header}}",
      // ASSUMPTION F-09: fila con un número de columnas distinto al layout → error de nivel 1, sin excepción.
      estructura = "{{codigo_estructura}}",
      // ASSUMPTION VAL-SP01: código para un error interno inesperado al validar un registro (red de seguridad).
      interno = "{{codigo_interno}}"
    )
  )
}
