# Cabecera común de todo yaml-skill-<etapa>.yaml (insumo de las skills edf-pinning, edf-synthesis, edf-statecalc).
# Derivada de los tres YAML reales de SISBEN (assets/examples/sisben/). No borrar secciones: si no aplican, dejar
# la sección con una nota y su marcador de estado.
# Marcadores (steering 05 §3): [C] confirmado · [P] propuesto · [A:<ID>] supuesto abierto · [PM] pendiente manual · [S] superado.

documento:
  tipo: "intermediario-skill"
  etapa_pipeline: "{{Etapa}}"                       # Pinning | Synthesis/Consistency | State Calculation
  ingesta: "{{ingesta-id}}"
  version: "1.0.0"
  fecha: "{{AAAA-MM-DD}}"
  estado: "PROPUESTO"                               # pasa a APROBADO con aprobación registrada en el expediente (edf-orquestador)
  aprobador: "PENDIENTE-MANUAL"

procedencia:
  fuente_analisis:
    ruta: ".kiro/skills/edf-analisis/references/logica-{{etapa}}.md"
    version_fuente: "{{version}}"
  fuentes_ingesta:
    mapeo_campos: "expedientes/{{ingesta-id}}/analisis/mapeo-campos.md"
    source_spec_curation: "expedientes/{{ingesta-id}}/insumos/source-spec-curation.yaml"
    contrato_curation_pinning: "expedientes/{{ingesta-id}}/insumos/contrato-curation-pinning.md"
  repositorio_base:
    nombre: "{{oxygen-pinning | oxygen-synthesis | oxygen-statecalculation}}"
    naturaleza: "Z2 — solo lectura"
  skill_destino: "{{edf-pinning | edf-synthesis | edf-statecalc}}"

alcance:
  genera: []                                        # artefactos que la skill de etapa SÍ debe generar con este YAML
  no_genera: []                                     # lo que queda fuera, con la razón
  precondiciones_bloqueantes: []                    # - {id, descripcion, efecto}

marcado_heredado:
  nota: "Todo valor con marcador distinto de [C] en logica-{{etapa}}.md se transcribe con su marcador original. Ningún [A:*] se resuelve en este YAML."

trazabilidad:
  citas_formato: "archivo §sección | ruta/archivo::símbolo"
  ultima_verificacion_referencias: "{{AAAA-MM-DD}}"
