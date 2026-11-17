# Tasks — puesta en marcha del Skill Orquestador

Notación de estado AI-DLC: `[ ]` no iniciado · `[-]` en progreso · `[?]` esperando aprobación · `[R]` revisando · `[x]` completado · `[S]` omitido.

## Fase A — Configuración del agente (hecho por KIDAR)

- [x] A1. Steering del principio rector, roles y fuentes de verdad → `references/00-principio-rector.md`
- [x] A2. Conocimiento del flujo de etapas (etapa 0 + 6 desde la 1.1.0), dependencias, DAGs y evidencia S3 → `references/01-flujo-etapas.md`
- [x] A3. Steering de guardrails y perímetro EDF (GR-1..GR-9) → `references/02-guardrails-perimetro.md`
- [x] A4. Steering de gobierno: riesgo, fricción, checklist de bloqueo, regla de corte, bitácora → `references/03-gobierno-riesgo-aprobacion.md`
- [x] A5. Steering de contratos de entrada/salida por skill → `references/04-contratos-por-skill.md`
- [x] A6. Steering de referencia del piloto SISBEN → `steering/10-piloto-sisben.md`
- [x] A7. Definición del agente → `skills/edf-orquestador/SKILL.md`
- [x] A8. Esquema JSON del estado (cierra OQ-T-1) → `templates/estado-ingesta.schema.json`
- [x] A9. Instancia semilla validada del piloto → `templates/estado-ingesta.seed.json`
- [x] A10. Plantillas de salida → `templates/estado-ingesta.template.md`, `templates/bitacora.template.md`
- [x] A11. Los 8 prompts reproducibles (componente ②) → `prompts/00..07`
- [x] A12. Diseño funcional y cierre de OQ-T-1/T-3/T-4 → `specs/orquestador/design.md`
- [x] A13. Prueba funcional de certificación (cierra OQ-T-4) → `specs/orquestador/certificacion.md`
- [x] A14. Validador del expediente → `tools/validar-expediente.py`

## Fase B — Validación con el equipo (pendiente)

- [ ] B1. Revisar el esquema JSON con el PO y el Experto de dominio EDF. Confirmar `etapas[]`, `estadoEtapa` y los campos de `evidencia_pipeline`.
- [ ] B2. Confirmar **OQ-D-1**: ¿el identificador corporativo del actor cuenta como PII bajo la política de Experian?
- [ ] B3. Confirmar **OQ-D-2**: política de retención de la bitácora (reemplaza los 24 meses por default si existe).
- [ ] B4. Confirmar **OQ-D-3**: dónde se versiona el expediente (repo propio; GR-3 impide escribir en `bureau.colombia_innersource`).
- [ ] B5. Decidir **OQ-D-4**: la validación del JSON la corre el Desarrollador o un hook de Kiro.
- [x] B6. ~~Validar que las 4 preguntas abiertas del piloto (PA-001..PA-004) siguen vigentes.~~ Ya no se siembran preguntas predefinidas (v1.3.0).

## Fase C — Certificación (pendiente)

- [ ] C1. Preparar el fixture sintético de SISBEN (columnas, patrón de nombre y delimitador según la spec de la ingesta).
- [ ] C2. Correr C-1..C-7 de `certificacion.md` sobre `expedientes/sisben-cert/`.
- [ ] C3. Correr la verificación de reproducibilidad V-6 (dos corridas, diff ignorando timestamps).
- [ ] C4. Archivar el veredicto en `specs/orquestador/resultados-certificacion-<fecha>.md`.
- [ ] C5. Gate de aprobación del PO sobre el veredicto.

## Fase D — Integración con el resto del framework

- [R] D1. Skill de Análisis (etapa 0 + análisis técnico por etapa) → `skills/edf-analisis/`. Construido en `edf-framework-IA` 1.0.0; pendiente de certificar.
- [R] D2. Skills de etapa (Validation → State Calculation) → `skills/edf-validation` … `skills/edf-statecalc`, con Paso 0 de habilitación contra el expediente. Pendiente prueba de punta a punta.
- [R] D5. Unificación del steering y modelo 6 etapas + etapa 0 (schema 1.1.0, sin `reception`).
- [R] D6. Hook `validar-expediente` (cierra OQ-D-4 con la opción "hook de Kiro", pendiente de confirmar).
- [ ] D3. Agente de Guía y Onboarding (se deriva del steering, ADR-6).
- [ ] D4. Harness de pre-validación (compilación + linting) que hoy el Orquestador **exige como evidencia reportada**, no ejecuta.

> Nota de alcance: el Orquestador coordina a los skills de etapa, pero **no depende de que existan** para certificar. Su prueba funcional (R7.3) se satisface con el expediente y el bloqueo.
