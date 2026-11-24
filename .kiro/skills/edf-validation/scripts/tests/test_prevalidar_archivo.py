"""Tests de prevalidar_archivo.py. Datos SINTÉTICOS (fixture del source-spec de ejemplo de SISBEN)."""
from __future__ import annotations

import copy
import json
import sys
from datetime import date
from pathlib import Path

import pytest
import yaml

SCRIPTS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS))

import prevalidar_archivo as pv  # noqa: E402
import spec_utils as su  # noqa: E402

EXAMPLE = SCRIPTS.parent / "assets" / "examples" / "sisben"
SPEC_PATH = EXAMPLE / "source-spec.sisben.yaml"
GEN = EXAMPLE / "generated"
HOY = date(2024, 6, 30)
CASOS = su.load_spec(SPEC_PATH)["fixture"]["casos"]
# render_templates.py escribe una línea por caso, en orden, después del header; la prueba
# test_ejemplo_persistido_igual_a_regeneracion (test_render_templates.py) garantiza que GEN es igual a una regeneración.
FIXTURE_LINES = (GEN / "src" / "test" / "resources" / "sisben" / su.load_spec(SPEC_PATH)["fixture"]["nombre_archivo"]
                 ).read_text(encoding="utf-8").split("\n")[1:1 + len(CASOS)]


@pytest.fixture()
def spec() -> dict:
    return su.load_spec(SPEC_PATH)


@pytest.fixture()
def catalog(spec) -> pv.Catalog:
    return pv.Catalog.from_spec(spec)


def write_file(tmp_path: Path, spec: dict, rows: list[str], name: str | None = None) -> Path:
    path = tmp_path / (name or spec["fixture"]["nombre_archivo"])
    header = spec["archivo"]["delimitador"].join(c["nombre"] for c in spec["campos"])
    path.write_text("\n".join([header, *rows]) + "\n", encoding="utf-8")
    return path


def base_row(spec: dict) -> list[str]:
    return list(spec["fixture"]["registro_base"])


@pytest.mark.parametrize("index", range(len(CASOS)), ids=[c["id"] for c in CASOS])
def test_cada_caso_del_fixture_produce_lo_esperado(spec, catalog, index):
    case = spec["fixture"]["casos"][index]
    row = FIXTURE_LINES[index]
    esperado = case["esperado"]
    if esperado.get("filtrado"):
        assert pv.should_filter(spec, row)
    else:
        assert not pv.should_filter(spec, row)
        assert [e["codigo"] for e in pv.validate_record(spec, row, catalog, HOY)] == esperado.get("codigos", [])


@pytest.mark.parametrize("position", range(1, 23))
def test_ejemplo_invalido_de_cada_campo_produce_su_codigo(spec, catalog, position):
    field = next(c for c in spec["campos"] if c["posicion"] == position)
    values = base_row(spec)
    values[position - 1] = field["ejemplo_invalido"]
    codes = [e["codigo"] for e in pv.validate_record(spec, ";".join(values), catalog, HOY)]
    assert field["codigo"] in codes


def test_registro_base_valido_con_rqs_1(spec, catalog):
    errors = pv.validate_record(spec, ";".join(base_row(spec)), catalog, HOY)
    assert errors == [] and pv.quality_scores(spec, errors) == {"RQS": 1.0}


def test_inconsistencia_no_anula_rqs(spec, catalog):
    values = base_row(spec)
    values[10] = "X" * 51
    errors = pv.validate_record(spec, ";".join(values), catalog, HOY)
    assert [e["nivel"] for e in errors] == ["3"] and pv.quality_scores(spec, errors)["RQS"] == 1.0


def test_fixture_completo_coincide_con_el_resumen_esperado(spec, catalog):
    archivo = EXAMPLE / "input" / spec["fixture"]["nombre_archivo"]
    summary = pv.prevalidate(spec, archivo, catalog, HOY, detail=True)
    expected = json.loads((EXAMPLE / "esperado" / "resumen-prevalidacion.json").read_text(encoding="utf-8"))
    assert summary == expected
    assert summary["filas_filtradas"] == 3 and summary["duplicados_marcados"] == 1


def test_resumen_no_contiene_valores_de_campos(spec, catalog):
    archivo = EXAMPLE / "input" / spec["fixture"]["nombre_archivo"]
    text = json.dumps(pv.prevalidate(spec, archivo, catalog, HOY, detail=True))
    for value in ("PRUEBA", "ANA", "1000000001", "FICHA-SINT", "MEDELLIN"):
        assert value not in text


def test_nombre_invalido_emite_marcador(spec, catalog, tmp_path):
    path = write_file(tmp_path, spec, [";".join(base_row(spec))], name="OTRO_ARCHIVO.txt")
    summary = pv.prevalidate(spec, path, catalog, HOY)
    assert summary["marcador"] == "errorInFileName" and summary["eventType"] == -1002


def test_marca_de_tiempo_imposible_es_nombre_invalido(spec):
    assert pv.validate_name(spec, "/x/SISBEN_EXPERIAN_202406.20241399120000-0500.txt")
    assert pv.validate_name(spec, "/x/RT_SISBEN_EXPERIAN_202406.20240630120000+0000.txt") == []


def test_header_invalido_emite_marcador(spec, catalog, tmp_path):
    path = tmp_path / spec["fixture"]["nombre_archivo"]
    path.write_text("COLUMNA_A;COLUMNA_B\n" + ";".join(base_row(spec)) + "\n", encoding="utf-8")
    summary = pv.prevalidate(spec, path, catalog, HOY)
    assert summary["marcador"] == "errorInHeader" and summary["codigos"] == ["SISBEN_ERR_025"]


def test_header_con_nombres_fuente_y_bom_es_valido(spec):
    names = [c["alias"][0].upper() for c in spec["campos"]]
    assert pv.validate_header(spec, ["\ufeff" + ";".join(names)]) == []


def test_solo_menores_emite_cuerpo_vacio(spec, catalog, tmp_path):
    values = base_row(spec)
    values[13] = "1"
    path = write_file(tmp_path, spec, [";".join(values), ""])
    summary = pv.prevalidate(spec, path, catalog, HOY)
    assert summary["marcador"] == "errorInEmptyFileBody" and summary["filas_filtradas"] == 2


def test_archivo_vacio_falla_el_job(spec, catalog, tmp_path):
    path = tmp_path / spec["fixture"]["nombre_archivo"]
    path.write_text("", encoding="utf-8")
    assert pv.prevalidate(spec, path, catalog, HOY)["resultado"] == "FALLA_JOB"


def test_spec_con_footer_se_rechaza(spec, catalog, tmp_path):
    # CP-VAL-O-01 / AC-24 (plan #6, P-2): el oráculo no modela E8; con footer declarado no simula en silencio.
    archivo = EXAMPLE / "input" / spec["fixture"]["nombre_archivo"]
    con_footer = copy.deepcopy(spec)
    con_footer["archivo"].update(filas_footer=1, footer_regex="^TOTAL;.*")
    with pytest.raises(su.SpecError, match=r"no modela E8 \(footer\)"):
        pv.prevalidate(con_footer, archivo, catalog, HOY)
    assert spec["archivo"]["filas_footer"] == 0  # SISBEN: sin footer, la salida no cambia
    assert pv.prevalidate(spec, archivo, catalog, HOY)["resultado"] == "VALIDADO"


def test_duplicados_sin_documento_no_se_agrupan(spec, catalog, tmp_path):
    values = base_row(spec)
    values[14] = ""
    path = write_file(tmp_path, spec, [";".join(values), ";".join(values[:-1] + ["01/01/2024"])])
    assert pv.prevalidate(spec, path, catalog, HOY)["duplicados_marcados"] == 0


def test_modo_as_is_marca_todo_el_grupo(spec, catalog, tmp_path):
    other = copy.deepcopy(spec)
    other["duplicados"]["modo"] = "AS-IS"
    row = ";".join(base_row(spec))
    path = write_file(tmp_path, other, [row, row])
    assert pv.prevalidate(other, path, catalog, HOY)["duplicados_marcados"] == 2


def test_catalogo_desde_carpeta(spec):
    folder = GEN / "src" / "test" / "resources" / "sisben" / "lookup"
    cat = pv.Catalog.from_dir(spec, folder)
    assert cat.exists("201_SISBEN_ZONES_NH", "cod_clase", "2")
    assert cat.find("102_DANE_DIVIPOLA_STATES_CODES_NH", "cod_dpto", "05", "nom_dpto") == "ANTIOQUIA"


@pytest.mark.parametrize("expr,errors,expected", [
    ('errorlevelNotIn("1")', [{"codigo": "A", "nivel": "1"}], False),
    ('errorlevelNotIn("1")', [{"codigo": "A", "nivel": "3"}], True),
    ('errorcodeIn("SISBEN_ERR_02")', [{"codigo": "SISBEN_ERR_025", "nivel": "1"}], True),  # subcadena
    ('(errorcodeNotIn("X") and not errorlevelIn("3")) or false', [], True),
    ('errorcodeNotInList("A","B")', [{"codigo": "C", "nivel": "1"}], True),
])
def test_dsl_minimo(expr, errors, expected):
    assert pv.eval_dsl(expr, errors) is expected


def test_dsl_no_soportado_falla():
    with pytest.raises(su.SpecError):
        pv.eval_dsl("count(errors) > 2", [])


def test_fechas_estrictas():
    assert pv.parse_date("01/02/2024", ["dd/MM/yyyy"])
    assert pv.parse_date("1/2/2024", ["dd/MM/yyyy"]) is None
    assert pv.parse_date("31/02/2024", ["dd/MM/yyyy"]) is None


def test_java_to_py_date():
    assert su.java_to_py_date("yyyy-MM-dd HH:mm:ss") == "%Y-%m-%d %H:%M:%S"
    with pytest.raises(su.SpecError):
        su.java_to_py_date("dd/MMM/yyyy")


def test_verificacion_del_spec_con_el_generador_python(tmp_path):
    assert su.verify_spec(SPEC_PATH) == []
    bad = tmp_path / "malo.yaml"
    bad.write_text(SPEC_PATH.read_text(encoding="utf-8-sig").replace("fuente: sisben", "fuente: SISBEN"),
                   encoding="utf-8")
    assert su.verify_spec(bad)


def test_cli(tmp_path, capsys):
    out = tmp_path / "r.json"
    archivo = EXAMPLE / "input" / "SISBEN_EXPERIAN_202406.20240630120000-0500.txt"
    assert pv.main(["--spec", str(SPEC_PATH), "--archivo", str(archivo), "--hoy", "2024-06-30",
                    "--salida", str(out)]) == 0
    assert json.loads(out.read_text(encoding="utf-8"))["resultado"] == "VALIDADO"
    assert pv.main(["--spec", str(SPEC_PATH), "--archivo", str(tmp_path / "no-existe.txt")]) == 2


def test_cli_spec_con_footer_sale_con_2(tmp_path, capsys):
    # La guarda E8 también se ve desde el CLI: código 2 y mensaje que nombra E8/footer (plan #6, P-2).
    con_footer = tmp_path / "con-footer.yaml"
    texto = SPEC_PATH.read_text(encoding="utf-8-sig")
    assert "filas_footer: 0" in texto and "footer_regex: null" in texto
    datos = su.load_spec(SPEC_PATH)  # spec con footer completa y válida (AUD-V3): la guarda E8 es la que la detiene
    datos["archivo"].update(filas_footer=1, footer_regex="^TOTAL;.*")
    datos["codigos_especiales"]["footer"] = "SISBEN_ERR_028"
    datos["diagnostico_archivo"]["footer"] = 'errorlevelNotIn("1")'
    datos["fixture"]["footer"] = ["TOTAL;12"]
    con_footer.write_text(yaml.safe_dump(datos, allow_unicode=True, sort_keys=False), encoding="utf-8")
    assert su.verify_spec(con_footer) == []
    archivo = EXAMPLE / "input" / "SISBEN_EXPERIAN_202406.20240630120000-0500.txt"
    assert pv.main(["--spec", str(con_footer), "--archivo", str(archivo), "--hoy", "2024-06-30"]) == 2
    assert "no modela E8 (footer)" in capsys.readouterr().err


# ------------------------------------------------------------------ 1.9.2: corrida E2E SISBEN (AUD-V-nn)
HOY_E2E = date(2026, 11, 24)
NOMBRES_FECHA = {  # los tres casos de la auditoría (prefijo del ejemplo de forma), con --hoy 2026-11-24
    "periodo futuro": "SISBEN_EXPERIAN_209901.20260205093000-0500.txt",
    "recepción anterior al periodo": "SISBEN_EXPERIAN_202601.20251205093000-0500.txt",
    "recepción futura": "SISBEN_EXPERIAN_202611.20261230093000-0500.txt",
}
VALIDO_E2E = "SISBEN_EXPERIAN_202601.20260205093000-0500.txt"


def con_reglas(spec: dict) -> dict:
    alt = copy.deepcopy(spec)
    alt["archivo"]["nombre"].update(periodo_no_futuro=True, recepcion_no_futura=True,
                                    recepcion_desde_inicio_periodo=True)
    return alt


@pytest.mark.parametrize("nombre", NOMBRES_FECHA.values(), ids=NOMBRES_FECHA.keys())
def test_aud_v02_sin_claves_el_nombre_se_valida_como_antes(spec, nombre):
    assert pv.validate_name(spec, "/x/" + nombre, HOY_E2E) == []


@pytest.mark.parametrize("nombre", NOMBRES_FECHA.values(), ids=NOMBRES_FECHA.keys())
def test_aud_v02_con_claves_el_nombre_se_rechaza_con_el_codigo_configurado(spec, nombre):
    errores = pv.validate_name(con_reglas(spec), "/x/" + nombre, HOY_E2E)
    assert [e["codigo"] for e in errores] == [spec["codigos_especiales"]["nombre_archivo"]]
    assert pv.validate_name(con_reglas(spec), "/x/" + VALIDO_E2E, HOY_E2E) == []


@pytest.mark.parametrize("regla,nombre", [
    ("periodo_no_futuro", NOMBRES_FECHA["periodo futuro"]),
    ("recepcion_no_futura", NOMBRES_FECHA["recepción futura"]),
    ("recepcion_desde_inicio_periodo", NOMBRES_FECHA["recepción anterior al periodo"]),
])
def test_aud_v02_cada_regla_por_separado(spec, regla, nombre):
    alt = copy.deepcopy(spec)
    alt["archivo"]["nombre"][regla] = True
    assert pv.validate_name(alt, "/x/" + nombre, HOY_E2E)
    alt["archivo"]["nombre"][regla] = False
    assert pv.validate_name(alt, "/x/" + nombre, HOY_E2E) == []


def test_aud_v02_el_reloj_es_hoy_hasta_el_fin_del_dia(spec):
    alt = copy.deepcopy(spec)
    alt["archivo"]["nombre"]["recepcion_no_futura"] = True
    mismo_dia = "SISBEN_EXPERIAN_202611.20261124235959-0500.txt"  # 23:59:59 en Bogotá, --hoy 2026-11-24
    assert pv.validate_name(alt, mismo_dia, HOY_E2E) == []
    assert pv.validate_name(alt, mismo_dia, date(2026, 11, 23))
    alt["archivo"]["nombre"]["recepcion_no_futura"] = False
    alt["archivo"]["nombre"]["periodo_no_futuro"] = True
    assert pv.validate_name(alt, "SISBEN_EXPERIAN_202611.20261124120000-0500.txt", HOY_E2E) == []
    assert pv.validate_name(alt, "SISBEN_EXPERIAN_202612.20261124120000-0500.txt", HOY_E2E)


def test_aud_v02_prevalidacion_emite_marcador_de_nombre(spec, catalog, tmp_path):
    path = write_file(tmp_path, spec, [";".join(base_row(spec))], name=NOMBRES_FECHA["periodo futuro"])
    assert pv.prevalidate(spec, path, catalog, HOY_E2E)["resultado"] == "VALIDADO"
    summary = pv.prevalidate(con_reglas(spec), path, catalog, HOY_E2E)
    assert summary["marcador"] == "errorInFileName" and summary["codigos"] == ["SISBEN_ERR_024"]


def test_aud_v02_cli_usa_hoy_como_reloj(spec, tmp_path, capsys):
    spec_path = tmp_path / "con-reglas.yaml"
    spec_path.write_text(yaml.safe_dump(con_reglas(spec), allow_unicode=True, sort_keys=False), encoding="utf-8")
    archivo = write_file(tmp_path, spec, [";".join(base_row(spec))], name=NOMBRES_FECHA["recepción futura"])
    assert pv.main(["--spec", str(spec_path), "--archivo", str(archivo), "--hoy", "2026-11-24"]) == 0
    assert json.loads(capsys.readouterr().out)["marcador"] == "errorInFileName"
    assert pv.main(["--spec", str(spec_path), "--archivo", str(archivo), "--hoy", "2026-12-31"]) == 0
    assert json.loads(capsys.readouterr().out)["resultado"] == "VALIDADO"


@pytest.mark.parametrize("offset,valido", [("-2500", False), ("+1801", False), ("-0560", False),
                                           ("+1800", True), ("-0500", True), ("+0000", True)])
def test_aud_v16_offset_como_java(spec, offset, valido):
    errores = pv.validate_name(spec, f"/x/SISBEN_EXPERIAN_202601.20260205093000{offset}.txt", HOY_E2E)
    assert (errores == []) is valido


def spec_solo_ceros(spec: dict) -> dict:
    alt = copy.deepcopy(spec)
    alt["reglas_entre_campos"].append({"id": "RV-SIS-05", "tipo": "solo_ceros", "campo": "NUM_DOCUMENTO",
                                       "codigo": "SISBEN_ERR_029", "severidad": "1", "causa": "DOCUMENTO_CERO"})
    return alt


@pytest.mark.parametrize("documento,emite", [("0000000000000000", True), ("00 00", True), (" 0000", True), ("0", True),
                                             ("0001000000101", False), (" 0001000000101", False), ("", False)])
def test_aud_v03_solo_ceros_sobre_el_valor_sin_espacios(spec, catalog, documento, emite):
    alt = spec_solo_ceros(spec)
    values = base_row(alt)
    values[14] = documento
    codigos = [e["codigo"] for e in pv.validate_record(alt, ";".join(values), catalog, HOY)]
    assert ("SISBEN_ERR_029" in codigos) is emite
    if emite:
        assert "SISBEN_ERR_015" not in codigos  # 16 ceros normalizan a "0": la longitud no falla, solo la regla


def test_aud_v03_reglas_por_lista_no_cambian(spec, catalog):
    alt = copy.deepcopy(spec)
    alt["reglas_entre_campos"].append({"id": "RV-LISTA", "tipo": "valor_con_codigo_propio", "campo": "NUM_DOCUMENTO",
                                       "valores": ["0000"], "codigo": "SISBEN_ERR_029", "severidad": "1",
                                       "causa": "DOCUMENTO_CERO"})
    values = base_row(alt)
    for documento, emite in ((" 0000", True), ("0000000000000000", False), ("00 00", False)):
        values[14] = documento
        assert ("SISBEN_ERR_029" in [e["codigo"] for e in pv.validate_record(alt, ";".join(values), catalog, HOY)]) is emite
