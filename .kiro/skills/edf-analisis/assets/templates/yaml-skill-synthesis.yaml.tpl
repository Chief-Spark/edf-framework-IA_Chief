# yaml-skill-synthesis.yaml — insumo de edf-synthesis para la ingesta {{ingesta-id}}
# Estructura derivada de assets/examples/sisben/yaml-skill-synthesis.yaml (ejemplo completo de referencia).
# Secciones que edf-synthesis lee: modo_colombia, decision_nomenclatura_handlers, mapeo_campos_handler,
# instruccion_generacion_gdd, claves_configuracion_synthesis.

# ── Cabecera: copiar _cabecera-yaml-skill.yaml.tpl con etapa_pipeline: "Synthesis/Consistency" ──

modo_colombia:                                      # edf-synthesis SKILL «Modo Colombia» (HF-68)
  aplica: null                                      # true si la ingesta usa la configuración compartida de Synthesis de
                                                    # Colombia (un GDD, un diccionario y un customRulesFile para todo el HC);
                                                    # el modo lo fija el expediente (orquestador, prompt 00, paso 2)
  regla: ""                                         # en modo Colombia: ninguna clave de oxygen.stage.consistencyCheck se
                                                    # escribe; ART-062 es la lista VERIFICAR y ART-061 las entradas del GDD
                                                    # que se integran en el GDD compartido (sin duplicar ni modificar names)
  estado: ""

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
    campos_a_generar:                               # forma de cada entrada = la del GDD v2 (HF-71); [] si no hay ninguna
      - name: ""                                    # literal de campos_curados[].destino (nombre_en_gdd está deprecado)
        description: ""                             # texto de la entrada del GDD (inerte para el motor)
        handler: null                               # null = LFP (sin la clave en el GDD) | "EUC" | "LAP"
    razon_de_lista_vacia: ""                        # obligatorio si campos_a_generar queda vacío
    estado: ""

claves_configuracion_synthesis:
  claves:
    - clave: "oxygen.stage.consistencyCheck.eventTypeList"
      valor_a_escribir: ""                          # por completar (HF-69). Guarda TIPOS DE ARCHIVO (fileTag), no el event
                                                    # type: el motor la lee como listFileTypes (SparkConsistencyChecker.scala:46)
                                                    # y NO filtra eventos; solo se usa con checkFileTagEnabled = true para el
                                                    # voidIndicator (:87, :119-131). Solo los fileTag reales confirmados de una
                                                    # aplicación propia; en modo Colombia no se escribe (la lista es de Colombia).
                                                    # Nunca el nombre de la ingesta ni el ejemplo ["sisben","jurad","judicial"]
      estado: ""
      fuente: ""
    - clave: "oxygen.stage.consistencyCheck.dateType"
      valor_a_escribir: ""                          # campo de fecha del voidIndicator (HCUtil.scala:76-87); NO agrupa por
                                                    # periodo (el periodo sale de getPeriod, HCUtil.scala:109-124). Obligatoria
                                                    # (dateType.get, :47). En modo Colombia no se escribe: se verifica
      estado: ""
      fuente: ""
  pendientes_de_configuracion: []

resumen_de_cierre:
  claves_si_generables: ""
  bloqueo_principal: ""
  # Sin estado de aprobación (HF-32): vive solo en estado-ingesta.json.
