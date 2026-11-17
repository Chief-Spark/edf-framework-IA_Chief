# Archivos deprecados

Desde el 2026-10-20 el generador de Curation y su implementación de referencia están en Java 17
(`scripts/java/`). Los archivos Python de abajo **se conservan solo como referencia**: no los use para
generar ni para calcular la salida esperada.

| Archivo deprecado | Reemplazo vigente |
|---|---|
| `render_templates.py` | `java/src/main/java/com/experian/edf/skills/curation/RenderTemplates.java` (`java -jar java/target/render-templates.jar`) |
| `curation_reference.py` | `java/src/main/java/com/experian/edf/skills/curation/Referencia.java` |
| `tests/test_render.py` | `java/src/test/java/com/experian/edf/skills/curation/RenderTemplatesTest.java` |
| `tests/test_reference.py` | `java/src/test/java/com/experian/edf/skills/curation/ReferenciaTest.java` |

Siguen vigentes en Python: `hocon_check.py` y `tests/test_hocon_y_portabilidad.py`.

## Fuera de la suite y de la cobertura vigentes (ADR-04, 2026-10-21)

- **Suite.** `tests/conftest.py` declara `collect_ignore = ["test_render.py", "test_reference.py"]`:
  `python -m pytest scripts/tests -q` ya no las recoge. No se borraron ni se movieron; se pueden correr a
  mano (`python -m pytest scripts/tests/test_render.py scripts/tests/test_reference.py`). La fixture
  `rendered` de `conftest.py` solo la usan esas pruebas.
- **Cobertura.** `.coveragerc` (raíz del skill) omite `scripts/render_templates.py` y
  `scripts/curation_reference.py`. El 80 % de AC-39 se mide por script vigente (P-5):
  `coverage report --include=scripts/hocon_check.py --fail-under=80`.
- **Deriva esperada.** Como los deprecados no se actualizan, desde el 2026-10-21 (plantilla
  `TransformationsSpec.scala.tpl` con la prueba de ADR-02 y `LEEME.md` en los lookups generados, plan #9)
  `test_render.py::test_salida_igual_a_la_versionada` falla si se corre a mano: `render_templates.py` ya no
  produce el ejemplo versionado. La versión Java es la que manda.

## Evidencia de equivalencia (2026-10-20)

Con la spec SISBEN (con y sin `--write-expected`) y con la spec mock `G-07_zona_bogota.yaml` (zona
`America/Bogota`), la versión Java produjo los mismos 22 archivos, byte a byte, que la versión Python,
incluidos `expected/curated-events.json` y `expected/resumen.json`, y el mismo código de salida. Solo
difiere la redacción de los mensajes de error del JSON Schema (los errores detectados son los mismos).

## Condición para retirarlos

Borrarlos cuando el equipo lo apruebe. Mientras existan, si cambia una plantilla o una regla, la versión
Java es la que manda; los archivos deprecados no se actualizan.
