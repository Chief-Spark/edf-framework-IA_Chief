          # ACC-TPL-04 · contextualOverride · SOLO con decisión humana: {{contextual_override.decision_ref}}
          # Si la inferencia falla, el motor omite los borrados en silencio (ACC-AP03): exija un caso de prueba.
          # inferDelete lee todo el histórico curado (oxygen.stage.curation.paths.curatedDir).
          contextualOverride {
            inferDelete {
              enabled = {{contextual_override.inferDelete.enabled}}
              lookBackPeriod = {{contextual_override.inferDelete.lookBackPeriod}}
              deletePredicates = {{contextual_override.inferDelete.deletePredicates|json}}
            }
            explicitDelete {
              enabled = {{contextual_override.explicitDelete.enabled}}
              lookBackPeriod = {{contextual_override.explicitDelete.lookBackPeriod}}
              deletePredicates = {{contextual_override.explicitDelete.deletePredicates|json}}
            }
          }
