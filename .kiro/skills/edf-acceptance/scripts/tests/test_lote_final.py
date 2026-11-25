"""Regresión del lote final de la corrida E2E SISBEN (etapa Acceptance): HF-44 (convención `ruta_` de
`archivo.claves_de_prueba` declarada en el esquema y validada).

Ejecutar desde la raíz del skill:  python3 -m pytest scripts/tests -q
"""
from __future__ import annotations

import copy
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parent.parent
SKILL = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

import render_templates as rt  # noqa: E402

SPEC_PATH = SKILL / "assets" / "examples" / "sisben" / "source-spec.sisben.yaml"
SCHEMA_PATH = SKILL / "assets" / "schema" / "source-spec.schema.yaml"


@pytest.fixture()
def spec() -> dict:
    return rt.load_yaml(SPEC_PATH)


def test_hf44_el_esquema_declara_la_convencion_del_prefijo_ruta():
    schema = rt.load_yaml(SCHEMA_PATH)
    clave = schema["properties"]["archivo"]["properties"]["claves_de_prueba"]["items"]["properties"]["clave"]
    assert "ruta_" in clave["description"] and "HF-44" in clave["description"]


def test_hf44_el_ejemplo_nombra_ruta_toda_clave_que_es_una_ruta(spec):
    for caso in spec["archivo"]["claves_de_prueba"]:
        assert rt.es_ruta(caso["valor"]) == rt.es_clave_de_ruta(caso["clave"]), caso["clave"]
    assert rt.verify_spec(spec) == [] and rt.avisos(spec) == []


def test_hf44_ruta_sin_prefijo_da_aviso_y_no_bloquea(spec):
    alt = copy.deepcopy(spec)
    caso = next(c for c in alt["archivo"]["claves_de_prueba"] if c["clave"] == "ruta_otra_fuente")
    caso["clave"] = "otra_fuente"  # forma del insumo aprobado de la corrida (AUD-A-19)
    assert rt.verify_spec(alt) == []
    avisos = rt.avisos(alt)
    assert len(avisos) == 1 and "otra_fuente" in avisos[0] and "ruta_otra_fuente" in avisos[0]


def test_hf44_clave_ruta_sin_separador_es_error(spec):
    alt = copy.deepcopy(spec)
    alt["archivo"]["claves_de_prueba"].append(
        {"clave": "ruta_mal_nombrada", "valor": "SISBEN_EXPERIAN_202401.20240131120000-0500.txt", "debe_coincidir": True})
    errores = rt.verify_spec(alt)
    assert any("ruta_mal_nombrada" in e and "HF-44" in e for e in errores)
