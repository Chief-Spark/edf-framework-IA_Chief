"""DEPRECADO (2026-10-20): prueban render_templates.py, la versión Python deprecada.

Las pruebas vigentes están en scripts/java/src/test (RenderTemplatesTest). Ver scripts/DEPRECADOS.md.
Tests de render_templates.py. Datos SINTÉTICOS (source-spec de ejemplo de SISBEN)."""
from __future__ import annotations

import copy
import json
import re
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS))

import render_templates as rt  # noqa: E402

SPEC_PATH = SCRIPTS.parent / "assets" / "examples" / "sisben" / "source-spec.sisben.yaml"


@pytest.fixture()
def spec() -> dict:
    return rt.load_spec(SPEC_PATH)


def test_spec_sisben_pasa_la_verificacion(spec):
    assert rt.verify_spec(spec) == []


def test_todo_hueco_de_plantilla_esta_en_el_esquema_y_en_el_contexto(spec):
    declared = rt.schema_placeholders()
    context = rt.build_context(spec)
    for template, names in rt.template_placeholders().items():
        assert names <= declared, f"{template}: huecos sin declarar en el esquema {names - declared}"
        assert names <= context.keys(), f"{template}: huecos sin valor {names - context.keys()}"


def test_el_esquema_no_declara_huecos_huerfanos():
    used = set().union(*rt.template_placeholders().values())
    assert rt.schema_placeholders() - used == set()


def test_render_no_deja_huecos_y_escribe_manifiesto(spec, tmp_path):
    manifest = rt.render_all(spec, tmp_path)
    assert manifest["compilacion"].startswith("NO CERTIFICADA")
    files = [tmp_path / f["ruta"] for f in manifest["archivos"]]
    assert len(files) == len(rt.TEMPLATE_TARGETS) + len(spec["catalogos"])
    for path in files:
        text = path.read_text(encoding="utf-8")
        assert not rt.PLACEHOLDER_RE.search(text), path
        assert rt.scan_forbidden(text) == [], path
    assert json.loads((tmp_path / "manifest.json").read_text(encoding="utf-8"))["fuente"] == "SISBEN"


def test_bloque_hocon_usa_claves_verificadas(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    conf = (tmp_path / "env/application-conf/innersource/propuesta/validation-configs-sisben.conf").read_text(encoding="utf-8")
    assert "headerRows = 1" in conf and "footerRows = 0" in conf
    assert "numberOfHeaderRows" not in conf and "numberOfFooterRows" not in conf
    assert 'deDupVersion = "EXTN"' in conf and "computeMetrics = true" in conf
    assert "\nencoding" not in conf and "  encoding =" not in conf
    assert 'filePattern = ".*' in conf
    assert "contexual" in conf


def test_processor_no_usa_apis_prohibidas(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    proc = next(tmp_path.rglob("SISBENValidationProcessor.scala")).read_text(encoding="utf-8")
    assert "class SISBENValidationProcessor(processorContext: ProcessorContext) extends ValidationProcessor" in proc
    for forbidden in ("println", "decodeErrorObject", "HistoricAttributes", "throw "):
        assert forbidden not in proc
    assert "@transient private lazy val logger" in proc


def test_codigo_de_longitud_distinta_falla(spec):
    bad = copy.deepcopy(spec)
    bad["campos"][0]["codigo"] = "SISBEN_ERR_1"
    assert any("longitud fija" in e for e in rt.verify_spec(bad))


def test_colision_de_subcadena_falla(spec):
    bad = copy.deepcopy(spec)
    for field in bad["campos"]:
        field["codigo"] = field["codigo"] + "X"
    bad["campos"][1]["codigo"] = "SISBEN_ERR_00"
    bad["campos"][0]["codigo"] = "SISBEN_ERR_001"
    errors = rt.check_codes(bad)
    assert any("subcadena" in e for e in errors)


def test_codigo_de_11_caracteres_falla(spec):
    bad = copy.deepcopy(spec)
    for i, field in enumerate(bad["campos"]):
        field["codigo"] = f"SISBENE{i:04d}"
    for i, rule in enumerate(bad["reglas_entre_campos"]):
        rule["codigo"] = f"SISBENR{i:04d}"
    bad["codigos_especiales"] = {k: f"SISBENS{i:04d}" for i, k in enumerate(bad["codigos_especiales"])}
    for case in bad["fixture"]["casos"]:
        case["esperado"]["codigos"] = []
    assert any("11 caracteres" in e for e in rt.verify_spec(bad))


def test_file_pattern_sin_prefijo_falla(spec):
    bad = copy.deepcopy(spec)
    bad["archivo"]["file_pattern"] = "SISBEN_EXPERIAN_.*"
    assert rt.verify_spec(bad)


def test_duplicados_sin_clave_falla(spec):
    bad = copy.deepcopy(spec)
    bad["duplicados"]["clave"] = []
    assert any("clave no vacía" in e for e in rt.verify_spec(bad))


def test_columna_de_catalogo_fuera_de_keys_falla(spec):
    bad = copy.deepcopy(spec)
    bad["campos"][1]["catalogo"]["columna"] = "descripcion"
    assert any("keys" in e for e in rt.verify_spec(bad))


def test_nombre_de_quality_score_en_expresion_falla(spec):
    bad = copy.deepcopy(spec)
    bad["quality_scores"][0]["nombre"] = "error"
    assert any("quality score" in e for e in rt.verify_spec(bad))


def test_footer_sin_regex_falla(spec):
    bad = copy.deepcopy(spec)
    bad["archivo"]["filas_footer"] = 1
    assert any("footer_regex" in e for e in rt.verify_spec(bad))


def test_historic_attributes_true_falla_por_esquema(spec):
    bad = copy.deepcopy(spec)
    bad["ambiente"]["historic_attributes"] = True
    assert rt.verify_spec(bad)


def test_escapes():
    assert rt.scala_str('a"b\\c') == 'a\\"b\\\\c'
    assert rt.hocon_str('average("qs.RQS")') == 'average(\\"qs.RQS\\")'
    assert rt.camel("FECHA_ACTUALIZACION_CNS") == "fechaActualizacionCns"
    assert rt.java_to_py_date("yyyy-MM-dd HH:mm:ss") == "%Y-%m-%d %H:%M:%S"
    with pytest.raises(rt.SpecError):
        rt.java_to_py_date("dd/MMM/yyyy")


def test_scan_forbidden_detecta_credenciales():
    assert rt.scan_forbidden("password = algo")
    assert rt.scan_forbidden("ver settings-artifactory.xml")
    assert rt.scan_forbidden("clave AKIAABCDEFGHIJKLMNOP")
    assert rt.scan_forbidden("texto normal sin secretos") == []


def test_fill_reporta_huecos_faltantes():
    text, missing = rt.fill("{{a}} y {{b}}", {"a": "1"})
    assert text == "1 y {{b}}" and missing == {"b"}


def test_cli_solo_verificar_y_error(spec, tmp_path, capsys):
    assert rt.main(["--spec", str(SPEC_PATH), "--solo-verificar"]) == 0
    bad = copy.deepcopy(spec)
    bad["fuente"]["fuente"] = "SISBEN"  # debe ser minúscula
    bad_path = tmp_path / "malo.yaml"
    bad_path.write_text(json.dumps(bad), encoding="utf-8")
    assert rt.main(["--spec", str(bad_path), "--out", str(tmp_path / "out")]) == 2
    assert not (tmp_path / "out").exists()


def test_cli_render(tmp_path):
    assert rt.main(["--spec", str(SPEC_PATH), "--out", str(tmp_path)]) == 0
    assert (tmp_path / "manifest.json").exists()


def test_fixture_line_builder(spec):
    case = {"id": "X", "descripcion": "", "cambios": {"TIP_DOCUMENTO": "PPT"}, "columnas": 14,
            "esperado": {}}
    line = rt.build_fixture_line(spec, case)
    assert line.split(";")[-1] == "PPT" and len(line.split(";")) == 14
    assert re.fullmatch(spec["archivo"]["nombre"]["regex"], spec["fixture"]["nombre_archivo"])
