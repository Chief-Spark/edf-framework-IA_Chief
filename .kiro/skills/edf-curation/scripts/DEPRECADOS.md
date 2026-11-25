# Archivos deprecados

## Desde 2026-11-18 (versión 1.7.0 del framework): el generador vigente es Python

La skill **solo genera artefactos y no compila nada** (decisión del Desarrollador, 2026-11-18). El generador
vigente es `render_templates.py`: solo necesita Python 3 con PyYAML y jsonschema.

| Archivo | Estado |
|---|---|
| `render_templates.py` | **Vigente.** Generador de los 12 artefactos, salida esperada y `MANIFEST.json` |
| `curation_reference.py` | **Vigente.** Implementación de referencia (espejo de Capa A y del núcleo del processor) |
| `hocon_check.py` | Vigente. Parse-check mínimo de los fragmentos HOCON |
| `tests/test_render_templates.py`, `tests/test_curation_reference.py`, `tests/test_hocon_y_portabilidad.py` | Vigentes, en la suite (`python3 -m pytest scripts/tests -q`) y en la cobertura |
| `java/` (generador `RenderTemplates`, `Referencia`, `Py`, `RenderError` y sus pruebas) | **Deprecado.** Solo referencia histórica. No se compila ni se ejecuta; la skill nunca usa `mvn` ni `java -jar` |

Las pruebas Python anteriores (`tests/test_render.py`, `tests/test_reference.py`), que cubrían la versión
Python de 2026-10-20 y estaban fuera de la suite, se reemplazaron por las dos suites nuevas.

### Evidencia de equivalencia (2026-11-18)

`render_templates.py` y `curation_reference.py` se reescribieron como traducción de `RenderTemplates.java` y
`Referencia.java` (vigentes hasta la 1.6.x: subscriberRelation por extensión, F-FW-10/11/13/14, marca de
sintético de los lookups, `otros_archivos` con SHA-256, zona de fechas y advertencias). Con el source-spec de
ejemplo de SISBEN y las plantillas de entonces, la versión Python produjo los mismos 23 archivos que la salida
Java versionada en `assets/examples/sisben/generated/` y `expected/`, **byte a byte**, incluido
`MANIFEST.json`. Solo puede diferir la redacción de los mensajes de error del JSON Schema (los errores
detectados son los mismos).

Las suites Python traducen los casos de `RenderTemplatesTest.java` y `ReferenciaTest.java`, incluido "la
salida es igual a la versionada". La comprobación con Typesafe Config (el parser HOCON del motor), que antes
hacían las pruebas Java, ocurre ahora al compilar y probar en el fork, fuera de la skill
(`references/runbook-ejecucion.md` §1 a §5); antes, `hocon_check.py` y, si está instalado, `pyhocon`.

### Cambios de la 1.7.0 sobre la equivalencia

Después de comprobar la equivalencia se agregaron, solo en Python y en las plantillas:

- Transformación `concatenacion` (une valores curados de campos anteriores; RG-013 de SISBEN,
  `standardizedIdName`). Cambian `FieldMapping.scala`, `Transformaciones.scala`, el processor y
  `TransformacionesSpec.scala` del ejemplo; `expected/` no cambia porque el ejemplo no la usa.
- Política de severidad 3 documentada en el esquema: un nivel fuera de `niveles_no_almacenar` y
  `niveles_anular` se publica estandarizado (opción B de SISBEN, PA-003). No requirió cambio de código.
- Generación todo o nada: se valida todo antes de borrar `--out`.
- `info_archivo` devuelve fecha de corte nula con año 0000 (Java devolvía una fecha del año 0).
- La comparación de zonas para la advertencia C-CUR-04 trata como iguales los alias de UTC (`UTC`,
  `Etc/UTC`, `GMT`, `Z`, …) y compara por nombre las demás zonas.

## Historia: 2026-10-20 a 2026-11-17 (versión Java)

Entre esas fechas el generador vigente fue Java 17 (`java/`) y los Python se conservaron solo como referencia
(ADR-04, 2026-10-21: fuera de la suite y de la cobertura). En esa época se comprobó la equivalencia inversa con
la spec SISBEN y con la spec mock `G-07_zona_bogota.yaml`.

## Condición para retirar `java/`

Borrarla cuando el equipo lo apruebe. Mientras exista, no se actualiza: si cambia una plantilla o una regla,
manda `render_templates.py`.
