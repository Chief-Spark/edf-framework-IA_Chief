# Lookups sintéticos de Curation · ingesta `sisben`

**SINTÉTICO.** Carpeta de lookups del fixture de `insumos/source-spec-curation.yaml` (`fixture.lookups_dir`).
No contiene catálogos oficiales ni datos personales.

- Curation solo usa el catálogo `202_SISBEN_PROCESS_ID_TYPE_HO` (homologación, `spec-sisben.md` §9.4). El generador de
  `edf-curation` escribe su CSV desde `homologacion_tipo_documento` del source-spec, para que la tabla embebida y el
  lookup no diverjan; por eso esta carpeta no trae CSV.
- Los otros cinco catálogos de la spec §9 los consulta Validation (`insumos/source-spec-validation.yaml`).
- Este archivo es la marca de sintético que exige el generador (AC-30).
