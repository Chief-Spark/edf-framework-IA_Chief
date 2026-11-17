"""Fixtures comunes. Todos los datos de prueba son sintéticos."""

import sys
from pathlib import Path

import pytest
import yaml

SCRIPTS = Path(__file__).resolve().parent.parent
SKILL = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

SPEC = SKILL / "assets/examples/sisben/source-spec.sisben.yaml"
SCHEMA = SKILL / "assets/schema/source-spec.schema.yaml"
TEMPLATES = SKILL / "assets/templates"
EXPECTED = SKILL / "assets/examples/sisben/expected"
GENERATED = SKILL / "assets/examples/sisben/generated"

# ADR-04 (2026-10-21): las pruebas de los scripts Python deprecados (ver ../DEPRECADOS.md) salen de la
# suite vigente sin borrarse. Se pueden correr a mano: python -m pytest scripts/tests/test_render.py
collect_ignore = ["test_render.py", "test_reference.py"]


@pytest.fixture(scope="session")
def spec():
    return yaml.safe_load(SPEC.read_text(encoding="utf-8"))


@pytest.fixture(scope="session")
def schema():
    return yaml.safe_load(SCHEMA.read_text(encoding="utf-8"))


@pytest.fixture(scope="session")
def rendered(tmp_path_factory):
    # Solo la usan las pruebas deprecadas (test_render.py); DEPRECADO junto con render_templates.py.
    import render_templates

    out = tmp_path_factory.mktemp("generated")
    manifest = render_templates.render(SPEC, out, TEMPLATES, SCHEMA)
    return out, manifest
