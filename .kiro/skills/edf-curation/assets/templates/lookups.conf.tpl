# CUR-TPL-09 · datos · generado por el skill edf-curation v{{version_skill}}
# Destino: {{ruta_lookups_conf}} (archivo de lookups que recibe CurationApp como 3er argumento).
# REGLA ADITIVA: agregar cada objeto como elemento nuevo de la lista oxygen.lookups existente;
# no reemplazar la lista ni modificar tablas de otras ingestas.
# Curation solo registra las tablas que PERSISTE (homologación). Las otras tablas SISBEN las
# consulta Validation y se registran en su propio repositorio.
# ASSUMPTION F-12: archivo, delimitador y columnas de cada tabla; confirmar con el catálogo real (supuestos).
oxygen {
  lookups = [
{{lookups_hocon_entradas}}
  ]
}
