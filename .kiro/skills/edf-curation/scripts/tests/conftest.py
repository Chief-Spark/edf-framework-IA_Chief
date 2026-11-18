"""Fixtures comunes. Todos los datos de prueba son sintéticos."""

import copy
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
SCALA_FUENTE = "src/main/scala/com/experian/edf/oxygen/curation/source/sisben/"


@pytest.fixture(scope="session")
def spec_base():
    return yaml.safe_load(SPEC.read_text(encoding="utf-8"))


@pytest.fixture
def spec(spec_base):
    """Copia profunda por prueba: una prueba puede modificarla sin afectar a las demás."""
    return copy.deepcopy(spec_base)


@pytest.fixture(scope="session")
def schema():
    return yaml.safe_load(SCHEMA.read_text(encoding="utf-8"))


@pytest.fixture(scope="session")
def rendered(tmp_path_factory):
    """Generación del ejemplo SISBEN en una carpeta temporal: (out, manifest)."""
    import render_templates

    out = tmp_path_factory.mktemp("generated") / "out"
    manifest = render_templates.render(SPEC, out, TEMPLATES, SCHEMA)
    return out, manifest
