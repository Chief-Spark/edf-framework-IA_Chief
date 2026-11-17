# Archivos deprecados

Desde el 2026-10-20 el generador de Validation está en Java 17 (`scripts/java/`). Los archivos Python de
abajo **se conservan solo como referencia**: no los use para generar ni para verificar el spec.

| Archivo deprecado | Reemplazo vigente |
|---|---|
| `render_templates.py` | `java/src/main/java/com/experian/edf/skills/validation/RenderTemplates.java` (`java -jar java/target/render-templates.jar`), con `Contexto.java` y `Verificaciones.java` |
| `tests/test_render_templates.py` | `java/src/test/java/com/experian/edf/skills/validation/RenderTemplatesTest.java` |

Siguen vigentes en Python: `prevalidar_archivo.py`, `spec_utils.py` (verifica el spec con el jar),
`verificar_hocon.py`, `tests/test_prevalidar_archivo.py` y `tests/test_verificar_hocon.py`. Ninguno
importa `render_templates.py`.

## Fuera de la suite y de la cobertura vigentes (ADR-04, 2026-10-21)

| Qué | Cómo | Efecto |
|---|---|---|
| `tests/test_render_templates.py` | `collect_ignore = ["test_render_templates.py"]` en `tests/conftest.py` | `python -m pytest scripts/tests` ya no lo recoge (antes recogía sus 21 pruebas). Se puede ejecutar a mano dando su ruta: `python -m pytest scripts/tests/test_render_templates.py` |
| `render_templates.py` | `omit` en `../.coveragerc` (raíz del skill) | `coverage` solo mide los scripts vigentes. Comando por script (P-5): `coverage report --include=scripts/<script>.py --fail-under=80` |

No se borró ni se movió ningún archivo.

## Evidencia de equivalencia (2026-10-20)

Con la spec SISBEN (generación y `--solo-verificar`) y las 24 specs de
`Discovery/13_casos_de_prueba/datos_mock/validation/06_specs_negativas`, la versión Java dio 26/26
corridas iguales a la versión Python: mismo código de salida y mismos archivos byte a byte, incluido
`manifest.json`. Solo difiere la redacción de los mensajes de error del JSON Schema (los errores
detectados son los mismos).

## Condición para retirarlos

Borrarlos cuando el equipo lo apruebe. Mientras existan, si cambia una plantilla o una regla, la versión
Java es la que manda; los archivos deprecados no se actualizan.
