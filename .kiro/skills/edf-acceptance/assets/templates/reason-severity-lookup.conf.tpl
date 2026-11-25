# ACC-TPL-03 · Registro del lookup de severidades de {{Fuente}} · edf-acceptance {{version_skill}}
# REGLA ADITIVA: copie el elemento dentro de la lista existente oxygen.lookups del archivo inner.
# La severidad debe ser entera: el motor la convierte con toInt.
oxygen {
  lookups = [
    {
      tableName = "{{severidades.tableName}}"
      file = "{{severidades.archivo}}"
      fileFormat = "{{severidades.fileFormat}}"
      delimiter = "{{severidades.delimiter|hocon}}"
      keys = [ { index = "{{severidades.mapFromColumn}}" } ]
    }
  ]
}
