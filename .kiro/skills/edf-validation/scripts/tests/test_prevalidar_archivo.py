"""Tests de prevalidar_archivo.py. Datos SINTÉTICOS (fixture del source-spec de ejemplo de SISBEN)."""
from __future__ import annotations

import copy
import json
import sys
from datetime import date
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS))

import prevalidar_archivo as pv  # noqa: E402
import spec_utils as su  # noqa: E402

EXAMPLE = SCRIPTS.parent / "assets" / "examples" / "sisben"
SPEC_PATH = EXAMPLE / "source-spec.sisben.yaml"
GEN = EXAMPLE / "generated"
HOY = date(2024, 6, 30)
CASOS = su.load_spec(SPEC_PATH)["fixture"]["casos"]
# El generador Java escribe una línea por caso, en orden, después del header; la prueba Java
# ejemploPersistidoIgualARegeneracion garantiza que GEN es igual a una regeneración.
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


requiere_jar = pytest.mark.skipif(not su.JAR_PATH.is_file(),
                                  reason="falta el jar: mvn -q -f scripts/java/pom.xml package")


@requiere_jar
def test_verificacion_del_spec_con_el_generador_java(tmp_path):
    assert su.verify_spec(SPEC_PATH) == []
    bad = tmp_path / "malo.yaml"
    bad.write_text(SPEC_PATH.read_text(encoding="utf-8-sig").replace("fuente: sisben", "fuente: SISBEN"),
                   encoding="utf-8")
    assert su.verify_spec(bad)


@requiere_jar
def test_cli(tmp_path, capsys):
    out = tmp_path / "r.json"
    archivo = EXAMPLE / "input" / "SISBEN_EXPERIAN_202406.20240630120000-0500.txt"
    assert pv.main(["--spec", str(SPEC_PATH), "--archivo", str(archivo), "--hoy", "2024-06-30",
                    "--salida", str(out)]) == 0
    assert json.loads(out.read_text(encoding="utf-8"))["resultado"] == "VALIDADO"
    assert pv.main(["--spec", str(SPEC_PATH), "--archivo", str(tmp_path / "no-existe.txt")]) == 2


@requiere_jar
def test_cli_spec_con_footer_sale_con_2(tmp_path, capsys):
    # La guarda E8 también se ve desde el CLI: código 2 y mensaje que nombra E8/footer (plan #6, P-2).
    con_footer = tmp_path / "con-footer.yaml"
    texto = SPEC_PATH.read_text(encoding="utf-8-sig")
    assert "filas_footer: 0" in texto and "footer_regex: null" in texto
    con_footer.write_text(texto.replace("filas_footer: 0", "filas_footer: 1")
                          .replace("footer_regex: null", 'footer_regex: "^TOTAL;.*"'), encoding="utf-8")
    archivo = EXAMPLE / "input" / "SISBEN_EXPERIAN_202406.20240630120000-0500.txt"
    assert pv.main(["--spec", str(con_footer), "--archivo", str(archivo), "--hoy", "2024-06-30"]) == 2
    assert "no modela E8 (footer)" in capsys.readouterr().err
