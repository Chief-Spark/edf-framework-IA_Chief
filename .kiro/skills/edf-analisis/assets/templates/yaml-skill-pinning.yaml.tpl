# yaml-skill-pinning.yaml — insumo de edf-pinning para la ingesta {{ingesta-id}}
# Estructura derivada de assets/examples/sisben/yaml-skill-pinning.yaml (ejemplo completo de referencia).
# Secciones que edf-pinning lee: mapeo_identidad.atributos, reglas_matching_por_campo, instruccion_generacion,
# claves_configuracion_pinning. Las demás son contexto obligatorio para el revisor humano.

# ── Cabecera: copiar _cabecera-yaml-skill.yaml.tpl con etapa_pipeline: "Pinning" ──

# Ingestas del bureau de Colombia (DA-12): se REUTILIZAN la lista de atributos y el matcher compartidos.
# En ese caso, llenar esta sección y dejar reglas_matching_por_campo con aplica: false y reglas: [], e
# instruccion_generacion con valor_resultante: [] ("no aplica"). La aplicación de Pinning es compartida: ninguna clave de
# claves_configuracion_pinning se escribe (edf-pinning SKILL.md «Modo Colombia»; HF-57, HF-59).
# Nunca se hereda ISIdentityMatcher ni la lista de ejemplo (nationalId, firstName, cellPhone) como arranque: con eventos
# de Colombia todos los slots valen "" y todas las identidades colapsan en un PIN, sin error (HF-58).
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
  umbral_y_matcher_determinista:                    # HF-60: el matcher de Colombia es determinista (match / no match, sin similarityThreshold)
    texto_spec: ""                                  # lo que dice la spec del umbral o de la similitud (p. ej. «NearNeighborKeys con el umbral vigente»), con su cita
    lectura_del_matcher: ""                         # lo que dice la descripción del matcher (pinning-muric.md §4.1.2, §4.2): reglas 0-4, sin umbral; nearNeighborKeys = standardizedIdNumber
    diferencia: ""                                  # en qué difieren y si cambia el resultado; si lo cambia, pregunta PA-nnn (deteccion-ambiguedades.md)
    estado: ""                                      # [A:<ID>] mientras no se confirme en el código del fork

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
  aplica: true                                      # modo Colombia: false (matcher determinista; la diferencia con la spec va en modo_colombia.umbral_y_matcher_determinista)
  motor_referencia: "com.experian.edf.oxygen.pinning.extension.ISIdentityMatcher"   # solo fuera de modo Colombia (HF-58)
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
    # Modo Colombia (HF-59): ninguna clave compartida se redefine; valor_a_escribir es "no se redefine (<qué se verifica>)".
    - clave: "oxygen.stage.pinning.paths.identifyingAttributesFile"
      valor_a_escribir: '["identifying-attributes.json"]'   # modo Colombia: "no se redefine (lista compartida identifying-attributes-new.json)"
      estado: ""
      fuente: "logica-pinning.md §4.1, §5"
    # HF-57: reusePin es GLOBAL de la aplicación de Pinning (SparkPinner.scala:223, :409); false renumera los PIN de todas
    # las fuentes (:979-985). «Ingesta nueva → reusePIN = false» en una spec = la ingesta no tiene generación previa propia,
    # no un valor de la clave global. En modo Colombia valor_a_escribir es siempre el texto de abajo, nunca un booleano;
    # cambiar la clave de la aplicación compartida es N4 y se escala (steering 06 §3).
    - clave: "oxygen.stage.pinning.reusePin"
      valor_en_fuente: ""                           # dato, no se genera: lo que dicen la spec / HU (p. ej. "reusePIN = false (spec §11)")
      valor_a_escribir: "no se escribe en modo Colombia (clave global de la aplicación compartida)"   # fuera de modo Colombia: "true" | "false" confirmado [C] y la aplicación es propia
      estado: ""
      fuente: "logica-pinning.md §4.1, §8; edf-pinning SKILL.md «Modo Colombia»"
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
