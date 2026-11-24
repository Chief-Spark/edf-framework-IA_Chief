# VAL-TPL-07 · bloque HOCON de la ingesta {{Fuente}} (parametrizada).
# Generado por la skill edf-validation desde source-spec ({{spec_version}}). No editar a mano.
# ASSUMPTION VAL-SP08: DESTINO: agregar este objeto como un elemento NUEVO de la lista oxygen.stage.validation.configs = [ ... ]
# del archivo validation-common-application-inner.conf (regla aditiva). Nunca tocar el
# *-common-application.conf base ni otros elementos de la lista.
# Claves verificadas contra ValidationConfigs (oxygen-config): headerRows/footerRows (NO numberOf*),
# deDupVersion (vacío = AS-IS), duplicateThreshold (default Integer.MAX_VALUE). validate[] y metrics[] llevan su
# propio filePattern: el motor elige el elemento con data.filePattern.get (SparkValidator.getValidateInfo y
# getMetricsInfo); sin la clave lanza NoSuchElementException en cada archivo (AUD-V-01).
# Requisitos de AMBIENTE (no se escriben aquí; verificar): oxygen.application.lambda = batch,
# oxygen.application.historicAttributes = false, oxygen.features.enable.emitStockMetrics presente.
# ASSUMPTION F-04: oxygen.features.enable.defaultOutputStructure = {{default_output_structure}} en QA/PRO (se verifica, no se escribe).
{
  filePattern = "{{file_pattern_hocon}}"
  processor = "{{processor_fqcn}}"
  fileFormat = "{{file_format}}"
  headerRows = {{filas_header}}
  footerRows = {{filas_footer}}
  searchAllHeaderFooter = false
  computeMetrics = true
  dupKeyCheckEnabled = {{dup_habilitado}}
  deDupVersion = "{{dedup_version}}"
{{linea_duplicate_threshold}}
{{linea_encoding}}
  # ASSUMPTION F-11: sin expresión el motor da el archivo por válido y no emite marcador errorInFileName/errorInHeader;
  # se emiten las expresiones del source-spec (diagnostico_archivo).
  validate = [
    {
      filePattern = "{{file_pattern_hocon}}"
      fileName = "{{validate_file_name_hocon}}"
      header = "{{validate_header_hocon}}"{{linea_validate_footer}}
      contexual {
        enabled = false
      }
    }
  ]
  metrics = [
    {
      filePattern = "{{file_pattern_hocon}}"
      file = [
{{bloque_metricas}}
      ]
    }
  ]
}
