"""Pruebas de tools/verificar-artefactos-etapa.py. Datos sintéticos y golden de edf-statecalc."""
import importlib.util
import json
import sys
from pathlib import Path

import pytest

RAIZ = Path(__file__).resolve().parent.parent
_spec = importlib.util.spec_from_file_location("verificar_artefactos_etapa", RAIZ / "tools" / "verificar-artefactos-etapa.py")
va = importlib.util.module_from_spec(_spec)
sys.modules["verificar_artefactos_etapa"] = va
_spec.loader.exec_module(va)

GDD_OK = {"domain": "sintetico", "version": 2,
          "fields": [{"name": "grupoSisben", "handler": "LAP"}, {"name": "nivelSisben"}]}
HOCON_OK = """oxygen.stage.consistencyCheck {
  eventTypeList = ["30"]
  paths {
    domainDictionaryFile = ["domaindictionary.json"]
    customRulesFile = ["custom-rules-empty.json"]
  }
}
"""


def test_gdd_correcto():
    assert va.verificar_gdd(GDD_OK) == []


@pytest.mark.parametrize("cambio,texto", [
    (lambda g: g.pop("version"), "sin `version`"),
    (lambda g: g["fields"].append({"name": "fileTag"}), "inyecta el motor"),
    (lambda g: g["fields"].append({"name": "firstEventDate", "handler": "EUC"}), "inyecta el motor"),
    (lambda g: g["fields"].append({"name": "x", "handler": "LFP"}), "solo EUC o LAP"),
    (lambda g: g["fields"].append({"name": "x", "type": "string", "nullable": True}), "ignora: nullable, type"),
    (lambda g: g["fields"].append({"name": "grupoSisben"}), "repetido"),
    (lambda g: g["fields"].append({"handler": "LAP"}), "sin `name`"),
])
def test_gdd_con_defectos(cambio, texto):
    gdd = json.loads(json.dumps(GDD_OK))
    cambio(gdd)
    assert any(texto in h for h in va.verificar_gdd(gdd)), va.verificar_gdd(gdd)


def test_hocon_synthesis():
    assert va.verificar_hocon_synthesis(HOCON_OK) == []
    assert any("null" in h for h in va.verificar_hocon_synthesis(HOCON_OK.replace(
        '["custom-rules-empty.json"]', "null")))
    sin = "\n".join(l for l in HOCON_OK.splitlines() if "customRulesFile" not in l)
    assert any("falta paths.customRulesFile" in h for h in va.verificar_hocon_synthesis(sin))
    malo = HOCON_OK.replace("consistencyCheck", "synthesis") + "x = {{HUECO}}\n"
    hall = va.verificar_hocon_synthesis(malo)
    assert len(hall) == 3
    # un comentario que menciona null o la clave vieja no cuenta
    assert va.verificar_hocon_synthesis("# customRulesFile = null\n# oxygen.stage.synthesis\n" + HOCON_OK) == []


def seccion(**kw):
    base = {"name": "s", "type": "AGGREGATES", "select": ["a"], "groupBy": ["a", "b"],
            "aggregates": [{"field": "c", "function": "SUM"}]}
    base.update(kw)
    return {"domain": "d", "calculations": [base]}


def test_calculation_aggregates():
    assert va.verificar_calculation(seccion()) == []
    assert any("OXY-SC-3" in h for h in va.verificar_calculation(seccion(select=["a", "z"])))
    assert any("OXY-SC-2" in h for h in va.verificar_calculation(seccion(groupBy=[])))
    assert any("OXY-SC-1" in h for h in va.verificar_calculation(seccion(aggregates=[])))
    assert any("MAYÚSCULAS: el validador" in h for h in va.verificar_calculation(seccion(type="aggregates")))
    assert any("function 'sum'" in h for h in va.verificar_calculation(seccion(aggregates=[{"field": "c", "function": "sum"}])))
    assert any("anidado" in h for h in va.verificar_calculation(seccion(grouping={"groupBy": ["a"]})))
    assert any("fuera de" in h for h in va.verificar_calculation(seccion(type="CUSTOM_ON_SECTION")))
    assert va.verificar_calculation({"x": 1}) == ["Calculation sin lista `calculations`"]


def test_calculation_contra_foundation():
    f = {"fields": [{"name": "a"}, {"name": "b"}]}
    hall = va.verificar_calculation(seccion(), f)
    assert hall == ["sección `s`: `c` no existe en fields[] de Foundation"]
    assert va.verificar_calculation(seccion(select=["pin"], groupBy=["pin"],
                                            aggregates=[{"field": "a", "function": "COUNT"}]), f) == []


def test_golden_de_statecalc_pasan():
    golden = RAIZ / ".kiro/skills/edf-statecalc/evals/fixtures/golden"
    calcs = sorted(golden.rglob("*calculation*.json"))
    assert calcs, "sin golden de calculation"
    for c in calcs:
        found = next(iter(sorted(c.parent.glob("*foundation*.json"))), None)
        args = ["statecalc", "--calculation", str(c)] + (["--foundation", str(found)] if found else [])
        assert va.main(args) == 0, c


def test_cli(tmp_path, capsys):
    gdd, hocon = tmp_path / "dd.json", tmp_path / "b.conf"
    gdd.write_text(json.dumps(GDD_OK), encoding="utf-8")
    hocon.write_text(HOCON_OK, encoding="utf-8")
    assert va.main(["synthesis", "--gdd", str(gdd), "--hocon", str(hocon)]) == 0
    (tmp_path / "malo.json").write_text("// comentario\n{}", encoding="utf-8")
    assert va.main(["synthesis", "--gdd", str(tmp_path / "malo.json")]) == 2
    assert va.main(["synthesis", "--hocon", str(tmp_path / "no.conf")]) == 2
    assert "comentarios" in capsys.readouterr().err
    with pytest.raises(SystemExit):
        va.main(["synthesis"])
