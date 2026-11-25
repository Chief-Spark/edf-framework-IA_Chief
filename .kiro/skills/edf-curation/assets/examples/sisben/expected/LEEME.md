# Salida esperada del fixture sintético (SISBEN)

Generada con `python3 scripts/render_templates.py --write-expected <dir>` y la implementación de
referencia `scripts/curation_reference.py` (idéntica, byte a byte, a la que generaba el Java deprecado). Entrada: `../fixture/accepted-events.input.yaml` (8 eventos
aceptados sintéticos).

- `curated-events.json`: los 7 eventos que cura el processor, con guard-rails copiados y
  `curatedRecordJson`.
- `resumen.json`: conteos, advertencias por evento, claves omitidas por severidad y homologación
  aplicada.

| Métrica | Valor |
|---|---|
| Eventos de entrada | 8 |
| Curados | 7 |
| Descartados por el motor (sin processor) | 1 (`evt-0008`) |
| Con advertencias | 3 (`evt-0005`, `evt-0006`, `evt-0007`) |
| Con clave omitida por severidad 3 | 1 (`evt-0004`, `segundoApellido`) |

Datos sintéticos: ningún registro corresponde a una persona real. Detalle caso por caso en
`references/interpretar-salida.md`.
