# Corrida autónoma de SISBEN (framework 1.9.0)

Ejecución de punta a punta, etapa por etapa, con las fuentes `hu-sisben.md` y `spec-sisben.md` 1.0.2 (sintéticas),
sin Kiro. El Orquestador se emula con `herramientas/orquestador_e2e.py`; cada skill la corre un agente generador,
cada etapa la revisa un agente auditor independiente y las aprobaciones las da un **aprobador simulado**
(`SIM-DEV-01`, y `SIM-PO-01` para State Calculation).

**Las aprobaciones de esta corrida son SIMULADAS. No reemplazan la aprobación humana real.**

| Archivo | Contenido |
|---|---|
| `expedientes/sisben/` | Expediente: `estado-ingesta.json` (fuente de verdad), vista `.md`, `bitacora.md` (append-only), análisis, insumos y propuestas |
| `registro-interacciones.md` | Cada mensaje a un agente y su respuesta (`INT-nnn` ↔ `BIT-nnnn`) |
| `auditorias/<etapa>.md` | Dictamen completo del auditor de cada etapa |
| `hallazgos-framework.md` | Defectos del framework encontrados y su corrección |
| `informe-ejecucion.md` | Resumen final |
