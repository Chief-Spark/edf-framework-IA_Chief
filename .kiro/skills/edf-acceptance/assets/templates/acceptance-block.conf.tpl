# ACC-TPL-01 · Bloque de ingesta de {{Fuente}} · generado por edf-acceptance {{version_skill}}
# Destino: {{rutas_destino.hocon}}
# REGLA ADITIVA: copie el elemento { ... } dentro de la lista existente oxygen.stage.acceptance.configs
# del archivo inner. No toque el *-common-application.conf base ni otros elementos de la lista.
# Convención de nombre: {{archivo.convencion}} (estado: {{archivo.estado}})
oxygen {
  stage {
    acceptance {
      configs = [
        {
          # Debe coincidir con la ruta sin extensión (E3) y con lineage.fileName (E10).
          filePattern = "{{archivo.filePattern|hocon}}"
          fileActions {
            # E8 · sintaxis nueva: registra el reasonCode de las condiciones que se cumplen.
            reject = "{{fileActions.reject|hocon}}"
            # E9 · sintaxis antigua: misma escala que el rechazo ({{calidad.umbral}}).
            accept = "{{fileActions.accept|hocon}}"
{{#si fileActions.manualReview?}}
            # Opcional (AUD-A4): lo lee SparkDatasetAcceptor (sintaxis nueva); en SparkAcceptor está comentado.
            manualReview = "{{fileActions.manualReview|hocon}}"
{{/si}}
{{#si intervencion_manual.habilitada}}
            manualReject = "{{intervencion_manual.manualReject|hocon}}"
            manualAccept = "{{intervencion_manual.manualAccept|hocon}}"
{{/si}}
          }
          # E10 · un error de evaluación ACEPTA el registro: ver los casos de ACC-TPL-05.
          recordAcceptance = "{{registro.recordAcceptance|hocon}}"
          # E4 · registra códigos de razón también en el rechazo automático por marcadores.
          customReasonForAutomaticRejectionEnabled = {{rechazo_automatico.customReasonForAutomaticRejectionEnabled}}
{{#si severidades.habilitado}}
          lookupMapping {
            tableName = "{{severidades.tableName}}"
            mapFromColumn = "{{severidades.mapFromColumn}}"
            mapToColumn = "{{severidades.mapToColumn}}"
          }
{{/si}}
{{#si contextual_override.decision_humana}}
{{> contextual-override.conf.tpl}}
{{/si}}
        }
      ]
    }
  }
}
