# Archivos deprecados

## Desde 2026-11-18 (versión 1.6.0 del framework): el generador vigente es Python

La skill **solo genera artefactos y no compila nada** (decisión del Desarrollador, 2026-11-18). El generador
vigente es `render_templates.py`: solo necesita Python 3 con PyYAML y jsonschema.

| Archivo | Estado |
|---|---|
| `render_templates.py` | **Vigente.** Generador, verificador de la spec y auditoría de huecos y supuestos |
| `tests/test_render_templates.py` | Vigente, en la suite (`python -m pytest scripts/tests`) y en la cobertura |
| `simular_decision.py`, `hocon_check.py` y sus pruebas | Vigentes, sin cambios de función |
| `java/` (`RenderTemplates`, `Plantillas`, `Verificaciones`, `Auditoria`, `Py` y `RenderTemplatesTest`) | **Deprecado.** Solo referencia histórica. No se compila ni se ejecuta; la skill nunca usa `mvn` ni `java -jar` |

### Qué se agregó a `render_templates.py` para igualar al generador Java

- Listado de los supuestos del bloque `supuestos` de la spec (AC-27, ADR-G2): con `--audit`, después de los
  huecos; sin `--audit`, después de `generado`/`omitido`.
- Regla "umbral en [0, 1]" (AC-04) y regla "QS por registro binario" en el fixture (AC-ACC-05).
- Una spec que no tiene la forma esperada se reporta como error de verificación, sin excepción.
- `jsonschema` pasa a ser obligatorio: sin él, el generador se detiene en vez de omitir la validación.
- Lectura de plantillas, manifiesto y spec en UTF-8 con o sin BOM; orden estable en el mensaje de subcadenas.

### Evidencia de equivalencia (2026-11-18)

- Con la spec de ejemplo de SISBEN, con y sin `--audit`: mismos archivos byte a byte y mismo reporte.
- Con 22 variantes de esa spec (una por regla de verificación, más errores de esquema): mismo código de
  salida y los mismos errores en las mismas rutas en todos los casos. Solo cambia la redacción de los
  mensajes que producen las librerías de JSON Schema y de expresiones regulares.
- La suite `tests/test_render_templates.py` incluye los casos de `RenderTemplatesTest.java`, entre ellos
  "el ejemplo persistido es igual a una regeneración". Ese caso fallaba porque al ejemplo versionado le
  faltaba `generated/src/` (la prueba Scala y los eventos del fixture); se repuso con la salida generada.

La comprobación con el parser real del motor (Typesafe Config), que antes hacían las pruebas Java, ocurre
ahora al compilar y probar en el fork, fuera de la skill. Antes, `hocon_check.py` verifica el subconjunto HOCON.

## Historia: 2026-10-20 a 2026-11-17 (versión Java)

Entre esas fechas el generador vigente fue Java 17 (`java/`) y `render_templates.py` se conservó solo como
referencia (ADR-04, 2026-10-21: fuera de la suite y de la cobertura).

## Condición para retirar `java/`

Borrarla cuando el equipo lo apruebe. Mientras exista, no se actualiza: si cambia una plantilla o una regla,
manda `render_templates.py`.
