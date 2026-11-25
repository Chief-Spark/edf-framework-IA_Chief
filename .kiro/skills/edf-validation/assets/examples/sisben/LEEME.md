# Ejemplo SISBEN V4 (SINTÉTICO)

Todos los datos de esta carpeta son **sintéticos**: nombres, documentos y fichas son inventados; la
muestra DIVIPOLA es mínima y **no está verificada contra DANE**. No reemplazar por datos reales.

| Ruta | Contenido |
|---|---|
| `source-spec.sisben.yaml` | Ejemplo de FORMA de un source-spec (entrada solo de este fixture). **No es fuente:** sus valores no se copian a ninguna ingesta, tampoco a SISBEN; el insumo real sale de la HU y la spec vía `edf-analisis` |
| `input/SISBEN_EXPERIAN_202406.20240630120000-0500.txt` | Fixture sintético: header + 15 casos (válidos, filtrados, errores, duplicados) |
| `esperado/resumen-prevalidacion.json` | Salida esperada de `prevalidar_archivo.py --hoy 2024-06-30 --detalle` sobre el fixture |
| `generated/` | Salida de referencia del generador `scripts/render_templates.py` (regenerable; no editar) |

Resultado esperado del fixture: 3 filas filtradas (2 menores, 1 vacía), 12 registros validados,
6 con error de severidad 1 (`SISBEN_ERR_004`, `006`, `018`, `023` y dos `026`), 1 inconsistencia
(`SISBEN_INC_011`), 1 duplicado marcado (`OxygenDuplicate`), `RQS` promedio 0.5 → Acceptance
rechazaría el archivo con el umbral 0.90; 5 registros pasarían la regla de registro.

Regenerar:

```bash
python3 scripts/render_templates.py --spec assets/examples/sisben/source-spec.sisben.yaml --out assets/examples/sisben/generated
python3 scripts/prevalidar_archivo.py --spec assets/examples/sisben/source-spec.sisben.yaml \
  --archivo assets/examples/sisben/input/SISBEN_EXPERIAN_202406.20240630120000-0500.txt --hoy 2024-06-30 --detalle
```
