"""Pruebas del generador vigente render_templates.py (sin Java ni compilación).

Son la suite vigente del generador; incluyen los casos de la suite del generador Java deprecado (histórico,
ver scripts/DEPRECADOS.md), que ya no se ejecuta. Las del simulador están en test_simulador.py.
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
    # Ramas deshabilitadas en SISBEN; fileActions.manualReview es opcional y no se declara (AUD-A4).
    disabled = ("intervencion_manual.manual", "contextual_override.", "fileActions.manualReview")
    # fixture.umbrales_alternativos es opcional (HF-23) y el ejemplo no lo declara: sus huecos quedan ausentes.
    opcionales = {"fixture.umbrales_alternativos?", "fixture.umbrales_alternativos", "casos", "umbral", "id",
                  "registros_validos", "registros_totales", "esperado", "reason_code_esperado?", "reason_code_esperado"}
    rows = rt.audit(spec, schema)
    empty = [r for r in rows if r["en_spec"] != "lleno" and not r["hueco"].startswith(disabled)
             and r["hueco"] not in opcionales]
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



# ------------------------------------------------------------------ casos traducidos de la suite Java (1.6.0)
GEN_PATH = SKILL / "assets" / "examples" / "sisben" / "generated"
TEMPLATES = SKILL / "assets" / "templates"
DECISIONES = SKILL / "references" / "decisiones-y-abiertos.md"
LINEA_SUPUESTO = re.compile(r"^supuesto  (S-ACC-\d{2})  impacto=(.+)  donde=(.+)  texto=(.+)$")


def _arbol(root: Path) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): p.read_text(encoding="utf-8") for p in sorted(root.rglob("*")) if p.is_file()}


def _mutated(base: dict, path: str, value):
    copia = copy.deepcopy(base)
    node = copia
    parts = path.split(".")
    for part in parts[:-1]:
        node = node[int(part)] if isinstance(node, list) else node[part]
    node[parts[-1]] = value
    return copia


def _con_score(spec: dict, score):
    return _mutated(spec, "fixture.archivos.0.eventos.2.qualityScores.0.score", score)


def test_ejemplo_persistido_igual_a_regeneracion(spec, tmp_path):
    rt.generate(spec, tmp_path)
    assert _arbol(GEN_PATH) == _arbol(tmp_path)


@pytest.mark.parametrize("path,value,expected", [
    ("archivo.filePattern", "SISBEN_EXPERIAN.*", "empezar con .*"),
    ("archivo.filePattern", ".*(", "no compila"),
    ("fileActions.accept", 'average("qs.RQS") >= 90', "escala del umbral"),
    ("fileActions.reject", 'average("qs.RQS") < 0.90', "sintaxis nueva"),
    ("fileActions.reject", "anyOf(NO_DEFINIDA)", "no definidas"),
    ("fileActions.reject", "anyOf(RQS_BAJO_UMBRAL_SISBEN) hasErrorlevelIn(1)", "severidades"),
    ("registro.recordAcceptance", 'errorlevelNotIn("1") and RQS > 0.5', "ACC-R02"),
    ("registro.recordAcceptance", 'errorcodeNotIn("CODIGO_INVENTADO")', "no existe en IN-05"),
    ("registro.niveles_que_rechazan", ["10"], "ACC-R01"),
    ("registro.codigos_que_rechazan", ["SISBEN_ERR_999"], "IN-05"),
    ("condiciones.0.nombre", "RQS_BAJO_UMBRAL", "nombre de la fuente"),
    ("condiciones.0.expresion", 'average("qs.RQS") < 0.85', "escala del umbral"),
    ("fileActions.accept", 'average("qs.RQS") >= 0.90 or isEmptyBodyFile', "isEmptyBodyFile"),
    ("fileActions.accept", 'min("qs.RQS") >= 0.90', "quality score declarado"),
])
def test_verificaciones_detectan_errores_java(spec, path, value, expected):
    problems = rt.verify_spec(_mutated(spec, path, value))
    assert any(expected in p for p in problems), problems


def test_codigo_subcadena_lista_ordenada(spec):
    corto = spec["diagnostico_validation"]["codigos_sev1"][0][:-1]
    problems = rt.verify_spec(_mutated(spec, "registro.codigos_que_rechazan", [corto]))
    sub = [p for p in problems if "es subcadena de" in p]
    assert sub and sub[0].endswith("(ACC-R01)")
    lista = sub[0].split("es subcadena de ")[1].rsplit(" (", 1)[0]
    assert eval(lista) == sorted(eval(lista))  # noqa: S307 - lista literal generada por la prueba


def test_manual_sin_external_action_rechazado(spec):
    bad = _mutated(spec, "intervencion_manual.habilitada", True)
    bad = _mutated(bad, "intervencion_manual.manualReject", 'average("qs.RQS") < 0.5')
    problems = rt.verify_spec(bad)
    assert any(p.startswith("manualReject") and "externalAction" in p for p in problems)
    assert any(p.startswith("manualAccept") for p in problems)


def test_umbral_fuera_de_rango_rechazado(spec, schema):
    bad = _mutated(spec, "calidad.umbral", "1.50")
    bad = _mutated(bad, "fileActions.accept", 'average("qs.RQS") >= 1.50')
    bad = _mutated(bad, "condiciones.0.expresion", 'average("qs.RQS") < 1.50')
    problems = rt.verify_spec(bad)
    assert any(p.startswith("umbral en [0, 1]") and "'1.50'" in p for p in problems), problems
    assert not any("escala del umbral" in p for p in problems)
    assert any(p.startswith("calidad/umbral") for p in rt.validate_schema(bad, schema))


def test_umbral_en_rango_y_fuera_de_rango():
    for ok in ("0.0", "0.90", "1.0", "1.00", "0", "1"):
        assert rt.umbral_fuera_de_rango(ok) is None, ok
    for malo in ("1.50", "-0.10", "1.01", "90"):
        assert "fuera de [0, 1]" in rt.umbral_fuera_de_rango(malo), malo
    assert "no es un número" in rt.umbral_fuera_de_rango("abc")


def test_qs_por_registro_no_binario_rechazado(spec, schema):
    bad = _con_score(spec, 0.5)
    problems = rt.verify_spec(bad)
    assert len(problems) == 1, problems
    assert problems[0].startswith("QS por registro binario: RQS = 0.5 en fixture/archivos/0/eventos/2")
    assert any(p.startswith("fixture/archivos/0/eventos/2/qualityScores/0/score") for p in rt.validate_schema(bad, schema))


def test_qs_por_registro_binario_acepta_cero_y_uno(spec, schema):
    for ok in (0, 1, 0.0, 1.0):
        assert rt.verify_spec(_con_score(spec, ok)) == [], ok
        assert rt.validate_schema(_con_score(spec, ok), schema) == [], ok
    for malo in (-1, 2, 0.99, True):
        assert rt.verify_spec(_con_score(spec, malo)), malo


def test_qs_no_binarios_tolera_otra_forma():
    assert rt.qs_no_binarios({}) == []
    assert rt.qs_no_binarios({"fixture": {"archivos": ["x", {"eventos": ["y", {"qualityScores": ["z"]}]}]}}) == []


def test_sin_jsonschema_falla_en_vez_de_omitir(spec, schema, monkeypatch):
    monkeypatch.setitem(sys.modules, "jsonschema", None)
    with pytest.raises(rt.TemplateError, match="jsonschema"):
        rt.validate_schema(spec, schema)


def _cli(capsys, *args) -> tuple[int, str]:
    code = rt.main(list(args))
    return code, capsys.readouterr().out


def test_cli_rechaza_qs_no_binario(tmp_path, capsys):
    original = SPEC_PATH.read_text(encoding="utf-8")
    bad = re.sub(r"(SINT-A-03,[^\n]*score: )1\.0", r"\g<1>0.5", original, count=1)
    assert bad != original
    f = tmp_path / "qs.yaml"
    f.write_text(bad, encoding="utf-8")
    code, out = _cli(capsys, "--spec", str(f), "--out", str(tmp_path / "o"))
    assert code == 1 and "QS por registro binario: RQS = 0.5" in out
    assert not (tmp_path / "o").exists()


def test_cli_sale_con_uno_si_la_spec_no_pasa(tmp_path, capsys):
    bad = tmp_path / "mala.yaml"
    bad.write_text(SPEC_PATH.read_text(encoding="utf-8").replace("filePattern: '.*", "filePattern: '"), encoding="utf-8")
    code, out = _cli(capsys, "--spec", str(bad), "--out", str(tmp_path / "o"))
    assert code == 1 and out.startswith("ERROR: la spec no pasa las verificaciones:")
    assert not (tmp_path / "o").exists()


def test_cli_spec_con_forma_inesperada(tmp_path, capsys, monkeypatch):
    monkeypatch.setattr(rt, "validate_schema", lambda spec, schema: [])
    f = tmp_path / "rara.yaml"
    f.write_text("Fuente: X\n", encoding="utf-8")
    code, out = _cli(capsys, "--spec", str(f), "--out", str(tmp_path / "o"))
    assert code == 1 and "la spec no tiene la forma esperada" in out


def test_cli_archivo_inexistente_o_sin_jsonschema(tmp_path, capsys, monkeypatch):
    code, out = _cli(capsys, "--spec", str(tmp_path / "no-existe.yaml"), "--out", str(tmp_path / "o"))
    assert code == 1 and out.startswith("ERROR:")
    monkeypatch.setitem(sys.modules, "jsonschema", None)
    code, out = _cli(capsys, "--spec", str(SPEC_PATH), "--out", str(tmp_path / "o"))
    assert code == 1 and "jsonschema" in out


def test_cli_error_de_plantilla(tmp_path, capsys, monkeypatch):
    def falla(*a, **k):
        raise rt.TemplateError("hueco sin resolver en x: {{y}}")
    monkeypatch.setattr(rt, "generate", falla)
    code, out = _cli(capsys, "--spec", str(SPEC_PATH), "--out", str(tmp_path / "o"))
    assert code == 1 and out.startswith("ERROR: hueco sin resolver")


def test_cli_sale_con_dos_si_faltan_argumentos():
    for argv in (["--spec"], ["--out", "x"], ["--otro", "x"]):
        with pytest.raises(SystemExit) as exc:
            rt.main(argv)
        assert exc.value.code == 2


def _supuestos_del_documento() -> dict[str, list[str]]:
    doc = DECISIONES.read_text(encoding="utf-8")
    desde = doc.index("## Supuestos propios de este skill")
    hasta = doc.find("\n## ", desde + 1)
    filas = {}
    for line in doc[desde: hasta if hasta >= 0 else len(doc)].split("\n"):
        if line.startswith("| S-ACC-"):
            cells = line.split("|")
            assert len(cells) == 6, line
            filas[cells[1].strip()] = [cells[2].strip(), cells[3].strip(), cells[4].strip()]
    return filas


def _normalizado(s: str) -> str:
    return re.sub(r"\s+", " ", s.replace("`", "")).strip()


def test_audit_lista_los_supuestos_con_impacto(spec, tmp_path, capsys):
    code, out = _cli(capsys, "--spec", str(SPEC_PATH), "--out", str(tmp_path), "--audit")
    assert code == 0
    lines = out.splitlines()
    sup = [l for l in lines if l.startswith("supuesto  ")]
    assert [l.split("  ")[1] for l in sup] == ["S-ACC-01", "S-ACC-02", "S-ACC-03", "S-ACC-04", "S-ACC-05"]
    for s in rt.supuestos(spec):
        assert s["impacto"] and rt.linea_supuesto(s) in sup
    assert all(LINEA_SUPUESTO.match(l) for l in sup)
    ultimo_hueco = max(i for i, l in enumerate(lines) if " schema=" in l)
    primer_generado = min(i for i, l in enumerate(lines) if l.startswith("generado  "))
    assert ultimo_hueco < lines.index(sup[0]) and lines.index(sup[-1]) < primer_generado
    assert "schema=True  spec=lleno" in out
    assert "omitido   ACC-TPL-03  reason-severity.csv.tpl  (severidades.habilitado = false)" in out


def test_reporte_lista_los_supuestos(spec, tmp_path, capsys):
    code, out = _cli(capsys, "--spec", str(SPEC_PATH), "--out", str(tmp_path))
    assert code == 0
    lines = out.splitlines()
    esperadas = [rt.linea_supuesto(s) for s in rt.supuestos(spec)]
    assert len(esperadas) == 5 and lines[-5:] == esperadas
    assert all(l.startswith(("generado  ", "omitido   ")) for l in lines[:-5])


def test_spec_sin_supuestos_no_imprime_ni_falla(spec, schema, tmp_path, capsys):
    sin = {k: v for k, v in spec.items() if k != "supuestos"}
    assert rt.validate_schema(sin, schema) == [] and rt.supuestos(sin) == []
    f = tmp_path / "sin-supuestos.yaml"
    f.write_text(yaml.safe_dump(sin, allow_unicode=True, sort_keys=False), encoding="utf-8")
    for audit in (False, True):
        o = tmp_path / f"o-{audit}"
        args = ["--spec", str(f), "--out", str(o)] + (["--audit"] if audit else [])
        code, out = _cli(capsys, *args)
        assert code == 0 and "supuesto  " not in out and "generado  ACC-TPL-05" in out
        assert _arbol(GEN_PATH) == _arbol(o)


def test_supuestos_toleran_otra_forma():
    assert rt.supuestos(None) == [] and rt.supuestos({"supuestos": "x"}) == []
    assert rt.supuestos({"supuestos": ["x", {"id": "S-ACC-01", "supuesto": "a\n  b"}]}) == [
        {"id": "S-ACC-01", "supuesto": "a b", "impacto": "", "donde": ""}]


def test_schema_supuestos_exige_id_y_cuatro_campos(spec, schema):
    ok = {"id": "S-ACC-09", "supuesto": "s", "impacto": "i", "donde": "d"}
    assert rt.validate_schema(_mutated(spec, "supuestos", [ok]), schema) == []
    assert rt.validate_schema(_mutated(spec, "supuestos", []), schema) == []
    for bad in ("S-01", "S-ACC-1", "S-ACC-001", "s-acc-01", "VAL-SP01"):
        assert rt.validate_schema(_mutated(spec, "supuestos", [{**ok, "id": bad}]), schema), bad
    for campo in ok:
        assert rt.validate_schema(_mutated(spec, "supuestos", [{k: v for k, v in ok.items() if k != campo}]), schema)
    assert rt.validate_schema(_mutated(spec, "supuestos", [{**ok, "otro": "x"}]), schema)


def test_supuestos_de_la_spec_iguales_a_los_del_documento(spec):
    doc = _supuestos_del_documento()
    en_spec = rt.supuestos(spec)
    assert set(doc) == {s["id"] for s in en_spec} == {"S-ACC-01", "S-ACC-02", "S-ACC-03", "S-ACC-04", "S-ACC-05"}
    assert len(en_spec) == len(doc)
    for s in en_spec:
        fila = doc[s["id"]]
        assert _normalizado(fila[1]) and _normalizado(fila[1]) == _normalizado(s["impacto"])
        assert _normalizado(fila[2]) == _normalizado(s["donde"])


def test_marcas_assumption_citan_un_supuesto_del_documento():
    ids = set(_supuestos_del_documento())
    marca = re.compile(r"ASSUMPTION(?: (S-ACC-\d{2}))?")
    citados = set()
    for f in [SPEC_PATH, *sorted(TEMPLATES.glob("*.tpl"))]:
        for m in marca.finditer(f.read_text(encoding="utf-8")):
            assert m.group(1), f"ASSUMPTION sin ID en {f.name}"
            assert m.group(1) in ids
            citados.add(m.group(1))
    assert citados == {"S-ACC-02", "S-ACC-04"}


# ------------------------------------------------------------------ auditoría 2026-11-24 (AUD-A1..AUD-A6)
SILENCIO = re.compile(r"\bTry\s*[({].*\.(getOrElse\s*\(|toOption\b)|case\s+NonFatal\s*\(")
MARCA_SILENCIO = re.compile(r"SILENCIO-INTENCIONAL\s+[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*")


def _scala(out: Path) -> str:
    return next(out.rglob("*AcceptanceExpressionsSpec.scala")).read_text(encoding="utf-8")


def _bloque(out: Path) -> str:
    return (out / "hocon/sisben-acc-tpl-01-acceptance-block.conf").read_text(encoding="utf-8")


def test_mapa_de_severidades_como_el_motor(spec, tmp_path):
    """AUD-A1: el evaluador recibe el mismo mapa reasonCode → severidad que buildReasonCodeSeverityMap."""
    rt.generate(spec, tmp_path / "off")
    off = _scala(tmp_path / "off")
    assert off.count("evaluator.apply(conditions, ReasonSeverities)") == 2
    assert "evaluator.apply(conditions, Map.empty" not in off
    assert "private val ReasonSeverities: Map[String, Int] = Map[String, Int](\n  ).filter" in off
    rt.generate(mutated(spec, "severidades.habilitado", True), tmp_path / "on")
    on = _scala(tmp_path / "on")
    assert '    "SISBFA00001" -> 1,\n' in on


def test_marcadores_automaticos_se_comprueban_con_la_regla_del_motor(spec, tmp_path):
    """AUD-A2: el caso automático ya no es assert(expected == "rechazo_automatico")."""
    rt.generate(spec, tmp_path)
    texto = _scala(tmp_path)
    assert 'assert(expected == "rechazo_automatico", dir)' not in texto
    assert "private def automaticReject(dir: String): Boolean" in texto
    assert 'assert(automaticReject(dir) == (expected == "rechazo_automatico"), dir)' in texto
    assert "CustomReasonForAutomaticRejection" in texto


@pytest.mark.parametrize("path,value,expected", [
    ("fixture.archivos.2.decision_esperada", "aceptado", "rechazo automático"),
    ("fixture.archivos.0.decision_esperada", "rechazo_automatico", "rechazo automático"),
    ("fixture.archivos.2.directorio", "EP_SISBEN_EXPERIAN_202401.20240131140000-0500.txt.900003", "no aparece en el directorio"),
])
def test_fixture_de_marcadores_coherente_con_el_motor(spec, path, value, expected):
    problems = rt.verify_spec(mutated(spec, path, value))
    assert any(expected in p for p in problems), problems


def test_toda_expresion_declarada_parsea(spec, tmp_path):
    """AUD-A3: un accept que no parsea ya no se disfraza de "pendiente": prueba explícita de parseo; la decisión
    conserva el Try del motor (SparkAcceptor.isFileActionValid), marcado como silencio intencional."""
    rt.generate(spec, tmp_path)
    texto = _scala(tmp_path)
    assert "toda expresión de archivo declarada parsea" in texto
    assert 'assert(parsed.successful, name + " no parsea en " + dir' in texto
    linea = next(l for l in texto.splitlines() if "getOrElse(false)" in l)
    previa = texto.splitlines()[texto.splitlines().index(linea) - 1]
    assert "SILENCIO-INTENCIONAL AUD-A3" in previa


def test_manual_review_opcional(spec, tmp_path):
    """AUD-A4: fileActions.manualReview (SparkDatasetAcceptor) se puede declarar; sin él la salida no cambia."""
    rt.generate(spec, tmp_path / "sin")
    assert "manualReview" not in _bloque(tmp_path / "sin") and "ManualReview" not in _scala(tmp_path / "sin")
    con = mutated(spec, "fileActions.manualReview", "anyOf(RQS_BAJO_UMBRAL_SISBEN)")
    pytest.importorskip("jsonschema")
    assert rt.validate_schema(con, rt.load_yaml(SCHEMA_PATH)) == [] and rt.verify_spec(con) == []
    rt.generate(con, tmp_path / "con")
    cfg = hocon_check.parse_hocon(_bloque(tmp_path / "con"))["oxygen"]["stage"]["acceptance"]["configs"][0]
    assert cfg["fileActions"]["manualReview"] == "anyOf(RQS_BAJO_UMBRAL_SISBEN)"
    assert "assert(evaluator.compile(ManualReview).isDefined" in _scala(tmp_path / "con")


@pytest.mark.parametrize("value,expected", [
    ('average("qs.RQS") < 0.90', "manualReview no está en la sintaxis nueva"),
    ("anyOf(NO_DEFINIDA)", "manualReview cita condiciones no definidas"),
])
def test_manual_review_invalido_rechazado(spec, value, expected):
    problems = rt.verify_spec(mutated(spec, "fileActions.manualReview", value))
    assert any(expected in p for p in problems), problems


def test_bloque_si_opcional_con_interrogacion():
    """AUD-A4: {{#si ruta?}} trata la clave ausente como falsa; sin "?" sigue fallando."""
    assert rt.render_string("{{#si a.b?}}x{{/si}}", {"a": {}}) == ""
    assert rt.render_string("{{#si a.b?}}x{{/si}}", {"a": {"b": "v"}}) == "x"
    with pytest.raises(rt.TemplateError):
        rt.render_string("{{#si a.b}}x{{/si}}", {"a": {}})


def test_silencios_intencionales_marcados():
    """Todo Try(...).getOrElse/toOption o NonFatal de plantillas y scripts lleva SILENCIO-INTENCIONAL <ID>."""
    sin_marca = []
    for f in sorted(rt.DEFAULT_TEMPLATES.rglob("*.tpl")) + sorted(SCRIPTS.glob("*.py")):
        lineas = f.read_text(encoding="utf-8").splitlines()
        for i, linea in enumerate(lineas):
            if SILENCIO.search(linea) and not MARCA_SILENCIO.search(linea + "\n" + (lineas[i - 1] if i else "")):
                sin_marca.append(f"{f.name}:{i + 1}")
    assert sin_marca == []
