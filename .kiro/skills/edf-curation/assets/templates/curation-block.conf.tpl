# CUR-TPL-07 · parametrizada · generado por el skill edf-curation-stage v{{version_skill}}
# Ingesta: {{Fuente}} (carpeta y paquete: {{fuente}}).
# Destino: {{ruta_hocon}} (fork de Colombia de oxygen-curation).
# REGLA ADITIVA: copiar SOLO el objeto { filePattern, processor } como un elemento nuevo dentro de la
# lista oxygen.stage.curation.configs existente del archivo inner. No reemplazar la lista, no tocar
# curation-common-application.conf (base inmutable) ni bloques de otras ingestas.
#
# NO se generan aquí (claves de etapa, afectan a todas las fuentes; el skill solo las lee y avisa):
#   runMode, partitionSize, enableSSRWrite, includeQualityCodes, includeErrorCodes, excludes.
# Requisitos del ambiente que el skill comprueba (si faltan, Curation no arranca):
#   oxygen.features.enable.emitStockMetrics, oxygen.stage.consistencyCheck.paths.domainDictionaryFile.
#
# filePattern: empieza con .* y termina en (\.txt)? porque el motor compara la ruta del dataset sin
# extensión y cada evento por lineage.fileName. Un solo patrón cubre todos los tipos de archivo de la spec (RF-CUR-03).
# ASSUMPTION: F-07 / D8 — convención de nombre en propuesta de frontera.
oxygen {
  stage {
    curation {
      configs = [
        {
          filePattern = "{{file_pattern_hocon}}"
          processor = "{{processor_fqcn}}"
        }
      ]
    }
  }
}
