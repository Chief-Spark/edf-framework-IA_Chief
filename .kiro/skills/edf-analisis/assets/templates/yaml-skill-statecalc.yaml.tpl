# yaml-skill-statecalc.yaml — insumo de edf-statecalc para la ingesta {{ingesta-id}}
# Estructura derivada de assets/examples/sisben/yaml-skill-statecalc.yaml (ejemplo completo de referencia).
# Secciones que edf-statecalc lee: decision_formato, mapeo_campos (filtra nivel_confianza "A"),
# instruccion_generacion_foundation, instruccion_generacion_calculation.
# State Calculation es riesgo N4 (steering 06 §2.2): el YAML lo aprueba el PO o el Aprobador designado.

# ── Cabecera: copiar _cabecera-yaml-skill.yaml.tpl con etapa_pipeline: "State Calculation" ──

decision_formato:
  descripcion: ""                                   # passthrough MOST_RECENT_VALUE u otro tipo, y por qué
  estado: ""
  fuente: "logica-statecalc.md §5.2-5.3"

mapeo_campos:
  nota_general: ""
  campos:
    - campo_xpm_destino: ""
      campo_curado_{{ingesta}}_origen: ""           # clave publicada por Curation / sintetizada
      nombre_en_foundation: ""                      # [PM] si no está confirmado: NO se infiere del nombre de negocio
      tipo_foundation: ""                           # DERIVED | CALCULATED
      valor_calculated: ""                          # solo si CALCULATED, script confirmado por fuente
      dataType: ""
      nivel_confianza: ""                           # A = cadena completa confirmada (se genera) | B = solo nombre XPM (no se genera)
      estado: ""
      fuente: ""
  pendientes_del_mapeo: []

instruccion_generacion_foundation:
  archivo: "statecalc-foundation-local.json"
  seccion: "fields[]"
  contenido_a_escribir:
    regla: "solo campos nivel_confianza A"
    campos_a_generar: []                            # - {name, type, value, dataType}
    campos_excluidos_hasta_resolver: []
    advertencia: ""
    estado: ""

instruccion_generacion_calculation:
  archivo: "statecalc-calculation-local.json"
  seccion: "calculations[]"
  contenido_a_escribir:
    regla: "una CalculationSection con groupBy PLANO (nunca grouping.groupBy anidado)"
    valor_resultante:
      name: ""
      description: ""
      type: "MOST_RECENT_VALUE"
      select: []
      groupBy: []
      enabled: true
      transient: false
    campos_select_pendientes_de_agregar: []
    estado: ""

fuera_de_alcance_frontera_xpm:
  temas_excluidos: []
