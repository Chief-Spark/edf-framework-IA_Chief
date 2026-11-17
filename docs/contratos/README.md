# Contratos entre etapas

Los contratos que el agente debe aplicar viven en el **steering** para que Kiro los cargue. Esta carpeta solo indica dónde está cada uno.

| Frontera | Dónde está el contrato | Estado |
|---|---|---|
| Archivo de entrada → Validation | `kiro/steering/07-contratos-frontera.md` §2 | `[C]` para SISBEN |
| Contrato de campo (nueve dimensiones) | `kiro/steering/07-contratos-frontera.md` §3 | `[C]` |
| Etapa a etapa (rutas S3, marcadores) | `kiro/steering/07-contratos-frontera.md` §4 | Mixto |
| Validation → Acceptance | `kiro/skills/edf-validation/references/interpretar-salida.md` + `source-spec-acceptance.yaml` (`diagnostico_validation`) | `[C]` |
| Acceptance → Curation | `kiro/skills/edf-acceptance/references/interpretar-salida.md` | `[C]` |
| **Curation → Pinning** | `kiro/steering/11-contrato-curation-pinning.md` (general) + `expedientes/<id>/insumos/contrato-curation-pinning.md` (por ingesta) | Parcial: ver §6 del steering `11` |
| Pinning → Synthesis | `yaml-skill-pinning.yaml` → `artefactos_salida_hacia_synthesis` | `[A:sin-clasificar]` en el consumo |
| Qué exige el Orquestador por etapa | `kiro/skills/edf-orquestador/references/04-contratos-por-skill.md` | PROPUESTO |
