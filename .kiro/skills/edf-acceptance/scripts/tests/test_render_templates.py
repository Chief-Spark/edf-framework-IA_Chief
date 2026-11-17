"""DEPRECADO (2026-10-20): prueban render_templates.py, la versión Python deprecada.

Las pruebas vigentes están en scripts/java/src/test (RenderTemplatesTest); las del simulador, en
scripts/tests/test_simulador.py. Ver scripts/DEPRECADOS.md.
Pruebas del renderizador, de las verificaciones de la spec y del fixture sintético SISBEN.

Ejecutar desde la raíz del skill:  python -m pytest scripts/tests -q
"""
from __future__ import annotations

import copy
import json
import re
import sys
from pathlib import Path

import pytest
import yaml

SCRIPTS = Path(__file__).resolve().parent.parent
SKILL = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

import hocon_check  # noqa: E402
import render_templates as rt  # noqa: E402
import simular_decision as sim  # noqa: E402

SPEC_PATH = SKILL / "assets" / "examples" / "sisben" / "source-spec.sisben.yaml"
SCHEMA_PATH = SKILL / "assets" / "schema" / "source-spec.schema.yaml"
EXPECTED_PATH = SKILL / "assets" / "examples" / "sisben" / "expected-output.yaml"


@pytest.fixture(scope="module")
def spec() -> dict:
    return rt.load_yaml(SPEC_PATH)


@pytest.fixture(scope="module")
def schema() -> dict:
    return rt.load_yaml(SCHEMA_PATH)


@pytest.fixture(scope="module")
def generated(spec, tmp_path_factory) -> tuple[Path, dict]:
    out = tmp_path_factory.mktemp("generated")
    return out, rt.generate(spec, out)


def mutated(spec: dict, path: str, value) -> dict:
    """Copia profunda de la spec con un valor cambiado (la original no se toca)."""
    copy_ = copy.deepcopy(spec)
    *parents, last = path.split(".")
    node = copy_
    for part in parents:
        node = node[int(part)] if isinstance(node, list) else node[part]
    node[last] = value
    return copy_


# ------------------------------------------------------------------ motor de plantillas

def test_variable_y_filtros():
    spec = {"a": {"b": 'x"y\\z'}, "n": None, "l": [1, 2]}
    assert rt.render_string("{{a.b}}", spec) == 'x"y\\z'
    assert rt.render_string("{{a.b|hocon}}", spec) == 'x\\"y\\\\z'
    assert rt.render_string("{{l|json}}", spec) == "[1, 2]"
    assert rt.render_string("{{n|json}}", spec) == "null"


def test_hueco_inexistente_o_nulo_falla():
    with pytest.raises(rt.TemplateError, match="sin resolver"):
        rt.render_string("{{no.existe}}", {})
    with pytest.raises(rt.TemplateError, match="nulo"):
        rt.render_string("{{n}}", {"n": None})


def test_bloques_cada_y_si_sin_lineas_vacias():
    spec = {"xs": [{"v": 1}, {"v": 2}], "on": False, "k": "K"}
    tpl = "inicio\n{{#cada xs}}\n- {{v}}{{k}}\n{{/cada}}\n{{#si on}}\noculto\n{{/si}}\nfin\n"
    assert rt.render_string(tpl, spec) == "inicio\n- 1K\n- 2K\nfin\n"


def test_bloque_mal_cerrado_y_filtro_desconocido():
    with pytest.raises(rt.TemplateError):
        rt.parse("{{#cada xs}}sin cierre")
    with pytest.raises(rt.TemplateError):
        rt.parse("{{x|yaml}}")


def test_literal_con_salto_de_linea_rechazado():
    with pytest.raises(rt.TemplateError, match="salto de línea"):
        rt.render_string("{{x|hocon}}", {"x": "a\nb"})


def test_salida_fuera_del_directorio_rechazada(tmp_path):
    with pytest.raises(rt.TemplateError, match="fuera"):
        rt._safe_output(tmp_path, "../escape.conf")


# ------------------------------------------------------------------ schema y huecos

def test_spec_valida_contra_schema(spec, schema):
    pytest.importorskip("jsonschema")
    assert rt.validate_schema(spec, schema) == []


def test_todo_hueco_definido_en_schema(spec, schema):
    rows = rt.audit(spec, schema)
    assert rows, "la auditoría no recorrió plantillas"
    assert [r for r in rows if not r["en_schema"]] == []


def test_huecos_de_ramas_activas_llenos_en_spec(spec, schema):
    disabled = ("intervencion_manual.manual", "contextual_override.")
    rows = rt.audit(spec, schema)
    empty = [r for r in rows if r["en_spec"] != "lleno" and not r["hueco"].startswith(disabled)]
    assert empty == []


def test_manifest_sigue_orden_del_inventario():
    pasos = [e["paso"] for e in rt.load_manifest(rt.DEFAULT_TEMPLATES)]
    assert pasos == sorted(pasos)
    ids = {e["id"] for e in rt.load_manifest(rt.DEFAULT_TEMPLATES)}
    assert ids == {"ACC-TPL-01", "ACC-TPL-02", "ACC-TPL-03", "ACC-TPL-04", "ACC-TPL-05", "ACC-TPL-06"}


# ------------------------------------------------------------------ verificaciones del inventario

def test_spec_sisben_pasa_verificaciones(spec):
    assert rt.verify_spec(spec) == []


@pytest.mark.parametrize("path,value,expected", [
    ("archivo.filePattern", "SISBEN_EXPERIAN.*", "empezar con .*"),
    ("fileActions.accept", 'average("qs.RQS") >= 90', "escala del umbral"),
    ("fileActions.reject", 'average("qs.RQS") < 0.90', "sintaxis nueva"),
    ("fileActions.reject", "anyOf(NO_DEFINIDA)", "no definidas"),
    ("fileActions.reject", "anyOf(RQS_BAJO_UMBRAL_SISBEN) hasErrorlevelIn(1)", "severidades"),
    ("registro.recordAcceptance", 'errorlevelNotIn("1") and RQS > 0.5', "ACC-R02"),
    ("registro.recordAcceptance", 'errorcodeNotIn("CODIGO_INVENTADO")', "no existe en IN-05"),
    ("registro.niveles_que_rechazan", ["10"], "ACC-R01"),
    ("condiciones.0.nombre", "RQS_BAJO_UMBRAL", "nombre de la fuente"),
])
def test_verificaciones_detectan_errores(spec, path, value, expected):
    problems = rt.verify_spec(mutated(spec, path, value))
    assert any(expected in p for p in problems), problems


def test_contextual_override_exige_decision_humana(spec):
    bad = mutated(spec, "contextual_override.decision_humana", True)
    assert any("decisión humana" in p for p in rt.verify_spec(bad))


def test_clave_de_archivo_del_motor():
    assert rt.engine_file_key("/v/X.txt.900001") == "/v/X.txt"
    assert rt.engine_file_key("/v/X.txt.errorInHeader.900003") == "/v/X.txt"


# ------------------------------------------------------------------ generación

def test_genera_y_omite_lo_esperado(generated):
    _, report = generated
    ids = [g["id"] for g in report["generados"]]
    assert ids.count("ACC-TPL-06") == 5
    assert {"ACC-TPL-01", "ACC-TPL-02", "ACC-TPL-05"} <= set(ids)
    assert {o["id"] for o in report["omitidos"]} == {"ACC-TPL-03"}


def test_sin_huecos_sin_resolver(generated):
    out, _ = generated
    for f in out.rglob("*"):
        if f.is_file():
            assert not re.search(r"\{\{|\}\}", f.read_text(encoding="utf-8")), f


def test_hocon_generado_parsea_y_conserva_valores(generated, spec):
    out, _ = generated
    block = hocon_check.parse_hocon((out / "hocon/sisben-acc-tpl-01-acceptance-block.conf").read_text(encoding="utf-8"))
    cfg = block["oxygen"]["stage"]["acceptance"]["configs"][0]
    assert cfg["filePattern"] == spec["archivo"]["filePattern"]
    assert cfg["fileActions"] == {"reject": spec["fileActions"]["reject"], "accept": spec["fileActions"]["accept"]}
    assert cfg["recordAcceptance"] == spec["registro"]["recordAcceptance"]
    assert cfg["customReasonForAutomaticRejectionEnabled"] is True
    assert "contextualOverride" not in cfg and "lookupMapping" not in cfg and "processor" not in cfg
    conds = hocon_check.parse_hocon((out / "hocon/sisben-acc-tpl-02-file-conditions.conf").read_text(encoding="utf-8"))
    cond = conds["oxygen"]["stage"]["acceptance"]["fileConditions"][0]
    assert cond == {"name": "RQS_BAJO_UMBRAL_SISBEN", "expression": 'average("qs.RQS") < 0.90',
                    "reasonCode": "SISBFA00001", "isLogged": True}


def test_pyhocon_si_esta_disponible(generated):
    pyhocon = pytest.importorskip("pyhocon")
    out, _ = generated
    for f in (out / "hocon").glob("*.conf"):
        pyhocon.ConfigFactory.parse_string(f.read_text(encoding="utf-8"))


def test_ramas_opcionales_cuando_se_habilitan(spec, tmp_path):
    on = mutated(spec, "severidades.habilitado", True)
    on = mutated(on, "contextual_override.decision_humana", True)
    on = mutated(on, "contextual_override.decision_ref", "ACTA-SINTETICA-001")
    on = mutated(on, "contextual_override.inferDelete", {"enabled": True, "lookBackPeriod": 6, "deletePredicates": [["subscriberRelation"]]})
    on = mutated(on, "contextual_override.explicitDelete", {"enabled": False, "lookBackPeriod": 1, "deletePredicates": []})
    report = rt.generate(on, tmp_path)
    assert {"ACC-TPL-03"} <= {g["id"] for g in report["generados"]}
    cfg = hocon_check.parse_hocon((tmp_path / "hocon/sisben-acc-tpl-01-acceptance-block.conf").read_text(encoding="utf-8"))
    cfg = cfg["oxygen"]["stage"]["acceptance"]["configs"][0]
    assert cfg["lookupMapping"] == {"tableName": "SISBEN_ACC_REASON_SEVERITY_NH", "mapFromColumn": "reasonCode", "mapToColumn": "severity"}
    assert cfg["contextualOverride"]["inferDelete"]["deletePredicates"] == [["subscriberRelation"]]
    csv = (tmp_path / "lookups/SISBEN_ACC_REASON_SEVERITY_NH.csv").read_text(encoding="utf-8")
    assert csv == "reasonCode,severity\nSISBFA00001,1\n"
    hocon_check.parse_hocon((tmp_path / "hocon/sisben-acc-tpl-03-lookup.conf").read_text(encoding="utf-8"))


def test_fixture_json_valido_y_sintetico(generated, spec):
    out, _ = generated
    files = list((out / "src/test/resources/sisben/validated").glob("*/part-00000.json"))
    assert len(files) == len(spec["fixture"]["archivos"])
    for f in files:
        for line in f.read_text(encoding="utf-8").splitlines():
            event = json.loads(line)
            assert event["eventId"].startswith("SINT-")
            assert event["lineage"]["fileName"] in f.parent.name
            rec = json.loads(event["validatedRecordJson"])
            assert all(str(v).startswith("SINT") for k, v in rec.items() if k == "NUM_DOCUMENTO")


def test_lineage_filename_coincide_con_filepattern(spec):
    pattern = re.compile(spec["archivo"]["filePattern"])
    for archivo in spec["fixture"]["archivos"]:
        assert pattern.fullmatch(archivo["nombre_lineage"]), archivo["nombre_lineage"]
        assert pattern.fullmatch(rt.engine_file_key("/h/validated/" + archivo["directorio"]))


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
