# Archivos deprecados

## Desde 2026-11-18 (versión 1.4.0 del framework): el generador vigente es Python

La skill **solo genera artefactos y no compila nada** (decisión del Desarrollador, 2026-11-18). El generador
vigente es `render_templates.py`: solo necesita Python 3 con PyYAML y jsonschema.

| Archivo | Estado |
|---|---|
| `render_templates.py` | **Vigente.** Generador y verificador del source-spec |
| `spec_utils.py` | Vigente. Reutiliza la verificación de `render_templates.py`; ya no llama a ningún `.jar` |
| `tests/test_render_templates.py` | Vigente, en la suite (`python3 -m pytest scripts/tests`) y en la cobertura |
| `java/` (generador `RenderTemplates`, `Contexto`, `Verificaciones`, `Py` y `RenderTemplatesTest`) | **Deprecado.** Solo referencia histórica. No se compila ni se ejecuta; la skill nunca usa `mvn` ni `java -jar` |

### Evidencia de equivalencia (2026-11-18)

`render_templates.py` se reescribió como traducción del generador Java vigente hasta la 1.3.x (marcas
`ASSUMPTION` con ID, `manifest["supuestos"]` derivado de las marcas, generación todo o nada, verificación
de métricas). Con el source-spec de ejemplo de SISBEN y con el de `expedientes/sisben/fuentes/spec-sisben.md`
(Anexo A), las dos versiones producen los mismos archivos byte a byte, incluido `manifest.json`. Solo
puede diferir la redacción de los mensajes de error del JSON Schema (los errores detectados son los mismos).

La suite `tests/test_render_templates.py` traduce los casos de `RenderTemplatesTest.java`, incluido
"el ejemplo persistido es igual a una regeneración". Ese caso fallaba en Java porque al ejemplo versionado
le faltaba `processor/SISBENValidationProcessor.scala` (el `manifest.json` sí lo listaba); se repuso con la
salida generada.

La comprobación con el parser real del motor (Typesafe Config), que antes hacían las pruebas Java, ocurre
ahora al compilar y probar en el fork, fuera de la skill (`references/runbook-ejecucion.md` §5). Antes,
opcionalmente, `verificar_hocon.py` con `pyhocon`.

## Historia: 2026-10-20 a 2026-11-17 (versión Java)

Entre esas fechas el generador vigente fue Java 17 (`java/`) y `render_templates.py` se conservó solo como
referencia (ADR-04, 2026-10-21: fuera de la suite y de la cobertura). En esa época se comprobó la
equivalencia inversa: con la spec SISBEN y las 24 specs de
`Discovery/13_casos_de_prueba/datos_mock/validation/06_specs_negativas`, la versión Java dio 26/26 corridas
iguales a la versión Python de entonces.

## Condición para retirar `java/`

Borrarla cuando el equipo lo apruebe. Mientras exista, no se actualiza: si cambia una plantilla o una regla,
manda `render_templates.py`.
