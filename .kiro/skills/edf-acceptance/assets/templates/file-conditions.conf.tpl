# ACC-TPL-02 · Condiciones con nombre de {{Fuente}} · generado por edf-acceptance {{version_skill}}
# Destino: {{rutas_destino.hocon}}
# REGLA ADITIVA: copie CADA elemento { ... } dentro de la lista existente
# oxygen.stage.acceptance.fileConditions del archivo inner. No reemplace la lista (en HOCON una lista
# redefinida sustituye a la anterior). Las condiciones son GLOBALES: name y reasonCode deben ser únicos
# entre todas las ingestas; verifíquelo antes de pegar.
oxygen {
  stage {
    acceptance {
      fileConditions = [
{{#cada condiciones}}
        {
          name = "{{nombre}}"
          expression = "{{expresion|hocon}}"
          reasonCode = "{{reasonCode}}"
          isLogged = {{isLogged}}
        }
{{/cada}}
      ]
    }
  }
}
