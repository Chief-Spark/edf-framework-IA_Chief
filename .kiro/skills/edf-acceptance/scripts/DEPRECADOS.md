# Archivos deprecados

Desde el 2026-10-20 el generador de Acceptance está en Java 17 (`scripts/java/`). Los archivos Python de
abajo **se conservan solo como referencia**: no los use para generar ni para auditar huecos.

| Archivo deprecado | Reemplazo vigente |
|---|---|
| `render_templates.py` | `java/src/main/java/com/experian/edf/skills/acceptance/RenderTemplates.java` (`java -jar java/target/render-templates.jar`), con `Plantillas.java`, `Verificaciones.java` y `Auditoria.java` |
| `tests/test_render_templates.py` | `java/src/test/java/com/experian/edf/skills/acceptance/RenderTemplatesTest.java` |

Siguen vigentes en Python: `simular_decision.py`, `hocon_check.py`, `tests/test_simulador.py` y
`tests/test_hocon_check.py` (desde 2026-10-21).

## Fuera de la suite y de la cobertura (ADR-04, 2026-10-21)

Decisión: `Discovery/13_casos_de_prueba/decisiones/ADR_2026-10-21_fallas-priorizados.md`, ADR-04. Los
archivos deprecados no se borran, pero ya no forman parte de lo que se prueba ni se mide:

| Archivo deprecado | Cómo se excluye | Ejecutarlo a mano |
|---|---|---|
| `tests/test_render_templates.py` | `collect_ignore` en `tests/conftest.py`: `python -m pytest scripts/tests -q` no lo recoge | `python -m pytest scripts/tests/test_render_templates.py -q` (nombrado explícitamente sí se ejecuta; necesita `pyhocon` para su única prueba opcional) |
| `render_templates.py` | `omit` en `.coveragerc` (raíz del skill), en `[run]` y en `[report]`: no aparece en `coverage report` aunque se pase `--omit="scripts/tests/*"` | — |

Efecto medido el 2026-10-21: la suite vigente pasó de `37 passed, 1 skipped` a solo pruebas vigentes,
sin `skip`; `hocon_check.py` bajó de 79,65 % a 76,99 % sin las pruebas deprecadas y subió a 100 % con
`tests/test_hocon_check.py` (plan #4). Detalle en
`Discovery/13_casos_de_prueba/resultados/acceptance/RECORRIDA_correcciones_2026-10-21.md`.

## Evidencia de equivalencia (2026-10-20)

Con la spec SISBEN (con y sin `--audit`) y las 20 specs de
`Discovery/13_casos_de_prueba/datos_mock/acceptance/03_specs`, la versión Java dio 22/22 corridas
iguales a la versión Python: mismo código de salida, mismos archivos byte a byte y misma salida de
`--audit`. Solo difiere la redacción de los mensajes de error del JSON Schema (los errores detectados
son los mismos).

## Condición para retirarlos

Borrarlos cuando el equipo lo apruebe. Mientras existan, si cambia una plantilla o una regla, la versión
Java es la que manda; los archivos deprecados no se actualizan.
