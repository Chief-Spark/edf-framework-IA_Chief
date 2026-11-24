# VAL-TPL-08 · registro de lookups de {{Fuente}} (datos).
# Generado por la skill edf-validation desde source-spec ({{spec_version}}). No editar a mano.
# ASSUMPTION VAL-SP08: DESTINO: agregar cada objeto como elemento NUEVO de oxygen.lookups = [ ... ] del archivo inner (aditivo).
# Forma verificada (LookupElements): tableName (obligatoria), file, fileFormat, delimiter, keys[].index.
# file = ${oxygen.application.paths.lookupHome}"/<archivo>", como todos los lookups del motor (AUD-V-06): el CSV
# real va en lookupHome con la ruta relativa de catalogos[].archivo.
# ASSUMPTION F-12: archivo, columnas y delimitador de cada tabla; confirmar con el dueño del catálogo.
# Las columnas usadas por el processor deben estar en keys[].index.
[
{{bloque_lookups}}
]
