"""Configuración de pytest para la suite vigente del skill (scripts/tests).

ADR-04 (2026-10-21, `Discovery/13_casos_de_prueba/decisiones/ADR_2026-10-21_fallas-priorizados.md`):
las pruebas de los scripts Python deprecados salen de la suite vigente, sin borrarlas. Se conservan
como referencia (ver `scripts/DEPRECADOS.md`). `python -m pytest scripts/tests -q` recoge solo las
pruebas vigentes: `test_simulador.py` y `test_hocon_check.py`.
"""

# Pruebas deprecadas el 2026-10-20 (generador Python reemplazado por scripts/java). No se recogen.
collect_ignore = ["test_render_templates.py"]
