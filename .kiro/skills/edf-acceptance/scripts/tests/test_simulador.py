"""Pruebas del simulador de decisiones y del verificador HOCON (scripts Python que siguen en Python).

El renderizador y sus pruebas están en Java: scripts/java (mvn -q -f scripts/java/pom.xml verify).
Ejecutar desde la raíz del skill:  python -m pytest scripts/tests -q
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

import pytest
import yaml

SCRIPTS = Path(__file__).resolve().parent.parent
SKILL = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

import hocon_check  # noqa: E402
import simular_decision as sim  # noqa: E402

SPEC_PATH = SKILL / "assets" / "examples" / "sisben" / "source-spec.sisben.yaml"
EXPECTED_PATH = SKILL / "assets" / "examples" / "sisben" / "expected-output.yaml"
GEN = SKILL / "assets" / "examples" / "sisben" / "generated"


@pytest.fixture(scope="module")
def spec() -> dict:
    return yaml.safe_load(SPEC_PATH.read_text(encoding="utf-8"))


# ------------------------------------------------------------------ hocon_check sobre el ejemplo persistido
# (la prueba Java ejemploPersistidoIgualARegeneracion garantiza que GEN es igual a una regeneración)

def test_hocon_check_parsea_el_hocon_del_ejemplo(spec):
    block = hocon_check.parse_hocon((GEN / "hocon/sisben-acc-tpl-01-acceptance-block.conf").read_text(encoding="utf-8"))
    cfg = block["oxygen"]["stage"]["acceptance"]["configs"][0]
    assert cfg["filePattern"] == spec["archivo"]["filePattern"]
    assert cfg["recordAcceptance"] == spec["registro"]["recordAcceptance"]
    conds = hocon_check.parse_hocon((GEN / "hocon/sisben-acc-tpl-02-file-conditions.conf").read_text(encoding="utf-8"))
    assert conds["oxygen"]["stage"]["acceptance"]["fileConditions"][0]["reasonCode"] == "SISBFA00001"


# ------------------------------------------------------------------ semántica (simulador)

def test_simulador_reproduce_resumen_esperado(spec):
    expected = {a["directorio"]: a for a in yaml.safe_load(EXPECTED_PATH.read_text(encoding="utf-8"))["archivos"]}
    for result in sim.simulate(spec):
        exp = expected[result["directorio"]]
        assert result["decision"] == exp["decision"]
        assert result["reason_codes"] == exp["reason_codes"]
        if "registros" in exp:
            assert (result["aceptados"], result["rechazados"]) == (exp["registros"]["aceptados"], exp["registros"]["rechazados"])


def test_simulador_coincide_con_esperados_del_fixture(spec):
    for archivo in spec["fixture"]["archivos"]:
        assert sim.decide_file(spec, archivo)["decision"] == archivo["decision_esperada"]
        for event in archivo["eventos"]:
            assert sim.eval_record(spec["registro"]["recordAcceptance"], event) == event["esperado_registro"], event["eventId"]


def test_expresion_rota_acepta_el_registro():
    event = {"errorCodes": [{"errorCodeValue": "SISBEN_ERR_003", "errorLevel": "1"}]}
    assert sim.eval_record('errorlevelNotIn("1") andd x', event) is True


def test_riesgo_subcadena_de_niveles():
    event = {"errorCodes": [{"errorCodeValue": "X", "errorLevel": "10"}]}
    assert sim.eval_record('errorlevelNotIn("1")', event) is False


# ------------------------------------------------------------------ contrato de entrada (AC-ACC-05, plan #2)

def _con_score(spec: dict, score) -> dict:
    """Copia de la spec con el RQS de SINT-A-03 (archivo 900001) cambiado."""
    copia = copy.deepcopy(spec)
    copia["fixture"]["archivos"][0]["eventos"][2]["qualityScores"][0]["score"] = score
    return copia


def test_simulador_rechaza_qs_no_binario(spec):
    """CP-ACC-B-12 (c): con score 0.5 el oráculo no decide (antes aceptaba con promedio 0.95)."""
    mala = _con_score(spec, 0.5)
    assert sim.qs_no_binarios(mala) == [
        "QS por registro binario: RQS = 0.5 en SISBEN_EXPERIAN_202401.20240131120000-0500.txt.900001 "
        "(SINT-A-03); debe ser 0 o 1 (AC-ACC-05)"]
    with pytest.raises(sim.ContratoError, match="QS por registro binario"):
        sim.simulate(mala)
    with pytest.raises(sim.ContratoError):
        sim.decide_file(mala, mala["fixture"]["archivos"][0])


@pytest.mark.parametrize("score", [0, 1, 0.0, 1.0])
def test_simulador_acepta_qs_binario_entero_o_decimal(spec, score):
    assert sim.qs_no_binarios(_con_score(spec, score)) == []


@pytest.mark.parametrize("score", [0.99, -1, 2, True, None, "1"])
def test_qs_no_binario_detectado(spec, score):
    assert len(sim.qs_no_binarios(_con_score(spec, score))) == 1


def test_main_sale_con_uno_si_el_qs_no_es_binario(spec, tmp_path, capsys):
    path = tmp_path / "qs.yaml"
    path.write_text(yaml.safe_dump(_con_score(spec, 0.5), allow_unicode=True), encoding="utf-8")
    assert sim.main(["--spec", str(path)]) == 1
    out = capsys.readouterr()
    assert out.out == ""
    assert out.err.startswith("ERROR: la spec no cumple el contrato de entrada:")
    assert "RQS = 0.5" in out.err


def test_main_imprime_la_simulacion(capsys):
    assert sim.main(["--spec", str(SPEC_PATH)]) == 0
    decisiones = [r["decision"] for r in json.loads(capsys.readouterr().out)]
    assert decisiones == ["aceptado", "rechazado", "rechazo_automatico", "rechazo_automatico", "pendiente"]
