"""Configuración de pytest para la suite vigente del skill.

ADR-04 (2026-10-21): las pruebas del generador Python deprecado salen de la suite vigente sin borrarse.
test_render_templates.py se conserva solo como referencia (ver scripts/DEPRECADOS.md); su reemplazo es
scripts/java/src/test/java/.../RenderTemplatesTest.java. Para ejecutarla a mano (la ruta explícita
no se ignora):
    python -m pytest scripts/tests/test_render_templates.py -p no:cacheprovider
"""

collect_ignore = ["test_render_templates.py"]
