# yaml-skill-pinning.yaml — insumo de edf-pinning para la ingesta {{ingesta-id}}
# Estructura derivada de assets/examples/sisben/yaml-skill-pinning.yaml (ejemplo completo de referencia).
# Secciones que edf-pinning lee: mapeo_identidad.atributos, reglas_matching_por_campo, instruccion_generacion,
# claves_configuracion_pinning. Las demás son contexto obligatorio para el revisor humano.

# ── Cabecera: copiar _cabecera-yaml-skill.yaml.tpl con etapa_pipeline: "Pinning" ──

# Ingestas del bureau de Colombia (DA-12): se REUTILIZAN la lista de atributos y el matcher compartidos.
# En ese caso, llenar esta sección y dejar reglas_matching_por_campo e instruccion_generacion como "no aplica".
modo_colombia:
  aplica: true                                      # false solo si la ingesta no es del bureau de Colombia
  identifying_attributes_compartido: "identifying-attributes-new.json"   # steering 11 §2.1; no se regenera
  identity_matcher: "[A:matcher-colombia]"          # ColombiaIdentityMatcher o IdentityImpl (steering 11 §6 #7)
  claves_obligatorias_en_evento_curado:             # deben existir literal en source-spec-curation.yaml (regla 0 del matcher)
    - "standardizedIdType"
    - "standardizedIdNumber"
  clave_opcional: "standardizedIdName"
  bandera_fuente_regla_1:
    opcion: "[A:OQ-TR-6]"                           # isOfficialSource | bandera nueva | ninguna (regla 2)
    efecto: "sin bandera, la ingesta cae en la regla 2 (tipo + número + nombre)"
  subscriber_relation_formato: "[A:OQ-IL-6]"        # steering 11 §6 #8

mapeo_identidad:
  decision_origen:
    descripcion: ""                                 # qué layout de campos curados se usa y por qué
    fuente: ""
    estado: ""
  atributos:
    - atributo_motor: ""                            # slot del motor: nationalId | firstName | firstLastName | middleName | cellPhone | ...
      campo_curado_{{ingesta}}: ""                  # clave EXACTA publicada por Curation en curatedRecordJson (steering 11 §4)
      rol_en_matching: []                           # doesMatch (+peso) / nearNeighborKeys / ninguno
      obligatorio: false
      pertenece_llave_identidad: false
      pii: true
      estado: ""                                    # solo [C] entra al array activo de identifying-attributes.json
      fuente: ""
  pendientes_del_mapeo: []                          # - {id, descripcion, efecto, estado}

reglas_matching_por_campo:
  motor_referencia: "com.experian.edf.oxygen.pinning.extension.ISIdentityMatcher"
  umbral_global:
    similarityThreshold: 0.6
    regla: "match si la suma de los pesos de los campos coincidentes es >= similarityThreshold"
    estado: ""
    fuente: "logica-pinning.md §7.3"
  reglas:
    - campo: ""                                     # atributo_motor
      campo_curado_{{ingesta}}: ""
      condicion_comparacion: "igualdad exacta de string"   # Pinning no normaliza (steering 11 §3)
      peso_doesMatch: 0
      participa_nearNeighborKeys: false
      detalle_blocking: ""
      participa_doesMatchKA: false
      estado: ""
      fuente: ""

instruccion_generacion:
  archivo: "identifying-attributes.json"
  ubicacion: "recurso de classpath (logica-pinning.md §5)"
  formato: "array JSON plano de strings con los nombres del slot del motor"
  contenido_a_escribir:
    regla: "solo atributos [C]"
    valor_resultante: []
    excluidos_hasta_resolver: []                    # - {atributo, razon}
    advertencia_consecuencia: ""                    # verificación aritmética: ¿se alcanza el umbral sin el identificador principal?
    estado: ""

claves_configuracion_pinning:
  bloque_oxygen_stage_pinning:
    - clave: "oxygen.stage.pinning.paths.identifyingAttributesFile"
      valor_a_escribir: '["identifying-attributes.json"]'
      estado: ""
      fuente: "logica-pinning.md §4.1, §5"
    - clave: "oxygen.stage.pinning.reusePin"
      valor_a_escribir: false                       # false en ingesta nueva; cambiarlo eleva el riesgo
      estado: ""
      fuente: ""
  pendientes_de_configuracion: []

keep_together_keep_apart:
  instruccion_generacion:
    accion: "NO GENERAR salvo caso de negocio confirmado"
    razon: ""

artefactos_salida_hacia_synthesis:
  nivel_contrato:
    ruta_pinned: "pinning/pinned/"
    marcador_finalizacion: "_SUCCESS"
    estado: ""
    fuente: "07-contratos-frontera.md §4"

comportamiento_del_motor_a_tener_en_cuenta:
  comportamientos_fail_fast: []
  comportamientos_fail_silent: []
