# yaml-skill-synthesis.yaml — insumo de edf-synthesis para la ingesta {{ingesta-id}}
# Estructura derivada de assets/examples/sisben/yaml-skill-synthesis.yaml (ejemplo completo de referencia).
# Secciones que edf-synthesis lee: decision_nomenclatura_handlers, mapeo_campos_handler, instruccion_generacion_gdd,
# claves_configuracion_synthesis.

# ── Cabecera: copiar _cabecera-yaml-skill.yaml.tpl con etapa_pipeline: "Synthesis/Consistency" ──

decision_nomenclatura_handlers:
  version_adoptada: "A"                             # logica-synthesis.md §5.1.1 — no cambiar sin decisión registrada
  definiciones:
    LFP: "Latest For Period"
    LAP: "Latest Across Period"
    EUC: "Earliest Until Corrected"
  estado: "[C]"
  fuente: "logica-synthesis.md §5.1.1"

mapeo_campos_handler:
  nota_general: ""
  campos_con_mapeo_confirmado:
    - campo_curado_{{ingesta}}: ""                  # clave publicada por Curation
      nombre_en_gdd: ""
      handler_propuesto: ""                         # LFP | LAP | EUC
      justificacion: ""
      nivel_confianza: ""                           # cita textual de fuente | propuesta razonada (NO genera)
      estado: ""                                    # solo [C] se genera
      fuente: ""
  campos_propuestos_en_bloque_sin_desglose:
    descripcion: ""
    estado: ""
  pendientes_del_mapeo: []

instruccion_generacion_gdd:
  archivo: "domaindictionary.json"
  seccion: "fields[]"
  contenido_a_escribir:
    regla: "solo campos con handler [C]"
    campos_a_generar: []
    razon_de_lista_vacia: ""                        # obligatorio si campos_a_generar queda vacío
    estado: ""

claves_configuracion_synthesis:
  claves:
    - clave: "oxygen.stage.consistencyCheck.eventTypeList"
      valor_a_escribir: '["{{ingesta}}"]'
      estado: ""
      fuente: ""
    - clave: "oxygen.stage.consistencyCheck.dateType"
      valor_a_escribir: ""
      estado: ""
      fuente: ""
  pendientes_de_configuracion: []

resumen_de_cierre:
  claves_si_generables: ""
  bloqueo_principal: ""
  estado: "PROPUESTO"
