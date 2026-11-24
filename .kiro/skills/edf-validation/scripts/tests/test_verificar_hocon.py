"""Tests de verificar_hocon.py. Datos SINTÉTICOS.

Las pruebas del parseo real se omiten si pyhocon no está disponible; las de las reglas (evaluar_reglas) y
del CLI con un parser inyectado corren sin pyhocon (plan de corrección #5, P-1 a).

Trabajan sobre una copia del ejemplo persistido (assets/examples/sisben/generated), que la prueba
test_ejemplo_persistido_igual_a_regeneracion (test_render_templates.py) mantiene igual a una regeneración. El
parse del HOCON generado con pyhocon está en test_hocon_generado_parsea. El parse con el parser real del motor
(Typesafe Config) era de la suite del generador Java deprecado: es histórico y hoy no se ejecuta.
"""
from __future__ import annotations

import copy
import shutil
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS))

import spec_utils as su  # noqa: E402
import verificar_hocon as vh  # noqa: E402

EXAMPLE = SCRIPTS.parent / "assets" / "examples" / "sisben"
SPEC_PATH = EXAMPLE / "source-spec.sisben.yaml"


@pytest.fixture()
def generated(tmp_path) -> Path:
    shutil.copytree(EXAMPLE / "generated", tmp_path, dirs_exist_ok=True)
    return tmp_path


def test_bloque_generado_es_hocon_valido(generated):
    pytest.importorskip("pyhocon")
    spec = su.load_spec(SPEC_PATH)
    assert vh.check(generated, "sisben", spec["fixture"]["nombre_archivo"]) == []


def test_cli_codigos_de_salida(generated, monkeypatch):
    monkeypatch.setitem(sys.modules, "pyhocon", None)  # simula pyhocon ausente
    assert vh.main(["--generado", str(generated), "--fuente", "sisben"]) == 3


def test_cli_ok_y_error(generated):
    pytest.importorskip("pyhocon")
    spec = su.load_spec(SPEC_PATH)
    assert vh.main(["--generado", str(generated), "--fuente", "sisben",
                    "--archivo-ejemplo", spec["fixture"]["nombre_archivo"]]) == 0
    assert vh.main(["--generado", str(generated), "--fuente", "otra"]) == 2
    conf = generated / "env/application-conf/innersource/propuesta/validation-configs-sisben.conf"
    conf.write_text(conf.read_text(encoding="utf-8").replace('filePattern = ".*', 'filePattern = "'),
                    encoding="utf-8")
    assert vh.main(["--generado", str(generated), "--fuente", "sisben",
                    "--archivo-ejemplo", spec["fixture"]["nombre_archivo"]]) == 2


def test_detecta_clave_ignorada(generated):
    pytest.importorskip("pyhocon")
    conf = generated / "env/application-conf/innersource/propuesta/validation-configs-sisben.conf"
    conf.write_text(conf.read_text(encoding="utf-8").replace("headerRows = 1", "numberOfHeaderRows = 1"),
                    encoding="utf-8")
    problems = vh.check(generated, "sisben", None)
    assert any("numberOfHeaderRows" in p for p in problems) and any("headerRows" in p for p in problems)


# ------------------------------------------------------------------------------------------------------
# Reglas sin pyhocon (plan de corrección #5, P-1 a, 2026-10-21). evaluar_reglas() es pura: recibe el bloque y
# los lookups ya parseados como dict/list. Los valores imitan el bloque SISBEN generado (datos SINTÉTICOS).
# ------------------------------------------------------------------------------------------------------
MUESTRA = "SISBEN_EXPERIAN_202406.20240630120000-0500.txt"
PATRON = r".*(RT_|EP_)?SISBEN_EXPERIAN_\d{6}\.\d{14}[+-]\d{4}\.txt"
BLOQUE_OK = {
    "filePattern": PATRON,
    "processor": "com.experian.edf.oxygen.validation.source.sisben.processor.SISBENValidationProcessor",
    "fileFormat": "text",
    "headerRows": 1,
    "footerRows": 0,
    "searchAllHeaderFooter": False,
    "computeMetrics": True,
    "dupKeyCheckEnabled": True,
    "deDupVersion": "EXTN",
    "validate": [{"filePattern": PATRON, "fileName": 'errorlevelNotIn("1")', "header": 'errorlevelNotIn("1")',
                  "contexual": {"enabled": False}}],
    "metrics": [{"filePattern": PATRON, "file": [{"metricId": "TotalRegistros", "metricExpr": "count(*)"}]}],
}
LH = vh.LOOKUP_HOME_SINTETICO
LOOKUPS_OK = [{"tableName": "TABLA_SINTETICA_A", "file": LH + "/a.csv"},
              {"tableName": "TABLA_SINTETICA_B", "file": LH + "/sub/b.csv"}]


def _bloque(**cambios):
    bloque = copy.deepcopy(BLOQUE_OK)
    for clave, valor in cambios.items():
        if valor is None:
            bloque.pop(clave, None)
        else:
            bloque[clave] = valor
    return bloque


def test_reglas_bloque_valido_sin_problemas_y_sin_pyhocon(monkeypatch):
    monkeypatch.setitem(sys.modules, "pyhocon", None)  # la evaluación no necesita pyhocon
    assert vh.evaluar_reglas(BLOQUE_OK, LOOKUPS_OK, MUESTRA) == []
    assert vh.evaluar_reglas(BLOQUE_OK, LOOKUPS_OK, "RT_" + MUESTRA) == []


@pytest.mark.parametrize("clave", vh.REQUIRED_KEYS)
def test_ac29a_clave_obligatoria_ausente(clave):
    problemas = vh.evaluar_reglas(_bloque(**{clave: None}), LOOKUPS_OK, None)
    assert f"falta la clave {clave}" in problemas


def test_ac29a_footer_rows_ausente_es_problema_aunque_sea_0_en_el_motor():
    # El default del motor es footerRows = 1: la clave se exige siempre, también con 0.
    assert vh.evaluar_reglas(_bloque(footerRows=None), LOOKUPS_OK, MUESTRA) == ["falta la clave footerRows"]
    assert vh.evaluar_reglas(_bloque(footerRows=0), LOOKUPS_OK, MUESTRA) == []


@pytest.mark.parametrize("clave", vh.IGNORED_KEYS)
def test_ac29b_clave_ignorada_presente(clave):
    problemas = vh.evaluar_reglas(_bloque(**{clave: 1}), LOOKUPS_OK, MUESTRA)
    assert problemas == [f"clave ignorada por el parser: {clave}"]


@pytest.mark.parametrize("dup, metricas, falla", [(True, False, True), (True, True, False),
                                                   (False, False, False), (None, False, False)])
def test_ac30_dup_key_check_exige_compute_metrics(dup, metricas, falla):
    problemas = vh.evaluar_reglas(_bloque(dupKeyCheckEnabled=dup, computeMetrics=metricas), LOOKUPS_OK, MUESTRA)
    assert ("dupKeyCheckEnabled exige computeMetrics = true" in problemas) is falla


@pytest.mark.parametrize("validate", [[{"filePattern": PATRON, "fileName": "x"}],
                                      [{"filePattern": PATRON, "header": "x"}], [{"filePattern": PATRON}], []])
def test_ac31_validate_sin_file_name_o_header(validate):
    problemas = vh.evaluar_reglas(_bloque(validate=validate), LOOKUPS_OK, MUESTRA)
    assert any("validate[] sin expresión fileName/header" in p for p in problemas)


def test_ac32_lookup_sin_table_name():
    lookups = LOOKUPS_OK + [{"file": LH + "/sin_tabla.csv"}]
    assert vh.evaluar_reglas(BLOQUE_OK, lookups, MUESTRA) == ["lookup sin tableName (clave obligatoria)"]
    assert vh.evaluar_reglas(BLOQUE_OK, [], MUESTRA) == []


def test_file_pattern_sin_comodin_y_que_no_acepta_la_muestra():
    sin_comodin = _bloque(filePattern=BLOQUE_OK["filePattern"][2:])
    assert vh.evaluar_reglas(sin_comodin, LOOKUPS_OK, None) == ["filePattern debe empezar con .*"]
    problemas = vh.evaluar_reglas(sin_comodin, LOOKUPS_OK, MUESTRA)
    assert "filePattern no acepta la ruta completa del archivo de ejemplo" in problemas
    assert vh.evaluar_reglas(BLOQUE_OK, LOOKUPS_OK, "OTRA_FUENTE_202406.txt") == [
        "filePattern no acepta la ruta completa del archivo de ejemplo",
        "validate[0].filePattern no acepta la ruta completa del archivo de ejemplo",
        "metrics[0].filePattern no acepta la ruta completa del archivo de ejemplo"]


def _parser_falso(bloque, lookups):
    """Sustituye a pyhocon: devuelve los dict dados según el prefijo que arma leer_fragmentos."""
    leidos = []

    def parse(texto):
        leidos.append(texto)
        if texto.startswith("configs = ["):
            return {"configs": [bloque]}
        if texto.startswith("lookups = "):
            return {"lookups": lookups}
        raise AssertionError("prefijo inesperado")

    return parse, leidos


def test_leer_fragmentos_arma_el_texto_para_el_parser(generated):
    parse, leidos = _parser_falso(BLOQUE_OK, LOOKUPS_OK)
    bloque, lookups = vh.leer_fragmentos(generated, "sisben", parse)
    assert bloque == BLOQUE_OK and lookups == LOOKUPS_OK
    assert "SISBENValidationProcessor" in leidos[0] and leidos[0].rstrip().endswith("]")
    assert "tableName" in leidos[1]


def test_cli_sin_pyhocon_con_parser_inyectado(generated, monkeypatch, capsys):
    # Recorre check() y main() con un parser falso: los códigos de salida no cambian (0 OK, 2 problemas o error).
    parse, _ = _parser_falso(BLOQUE_OK, LOOKUPS_OK)
    monkeypatch.setattr(vh, "cargar_parser", lambda: parse)
    assert vh.main(["--generado", str(generated), "--fuente", "sisben", "--archivo-ejemplo", MUESTRA]) == 0
    salida = capsys.readouterr().out
    assert "HOCON OK" in salida and "lookups: 2 tablas" in salida and "footerRows=0" in salida
    assert vh.main(["--generado", str(generated), "--fuente", "otra"]) == 2  # archivo inexistente
    parse_malo, _ = _parser_falso(_bloque(footerRows=None, numberOfFooterRows=0), LOOKUPS_OK)
    monkeypatch.setattr(vh, "cargar_parser", lambda: parse_malo)
    assert vh.main(["--generado", str(generated), "--fuente", "sisben"]) == 2
    error = capsys.readouterr().err
    assert "falta la clave footerRows" in error and "numberOfFooterRows" in error


def test_cli_sin_pyhocon_sale_con_3_aunque_falten_archivos(tmp_path, monkeypatch):
    monkeypatch.setitem(sys.modules, "pyhocon", None)
    assert vh.main(["--generado", str(tmp_path / "no_existe"), "--fuente", "sisben"]) == 3


# ------------------------------------------------------------------------------------------------------
# 1.9.2 (corrida E2E SISBEN): filePattern en validate[]/metrics[] (AUD-V-01) y lookupHome en los lookups (AUD-V-06)
# ------------------------------------------------------------------------------------------------------
@pytest.mark.parametrize("lista", ["validate", "metrics"])
def test_aud_v01_elemento_sin_file_pattern_falla(lista):
    bloque = copy.deepcopy(BLOQUE_OK)
    del bloque[lista][0]["filePattern"]
    problemas = vh.evaluar_reglas(bloque, LOOKUPS_OK, MUESTRA)
    assert problemas == [f"{lista}[0] sin filePattern: el motor hace data.filePattern.get "
                         "(NoSuchElementException en cada archivo)"]


def test_aud_v01_file_pattern_del_elemento_debe_aceptar_la_muestra():
    bloque = copy.deepcopy(BLOQUE_OK)
    bloque["metrics"][0]["filePattern"] = ".*OTRA_FUENTE.*"
    assert vh.evaluar_reglas(bloque, LOOKUPS_OK, MUESTRA) == [
        "metrics[0].filePattern no acepta la ruta completa del archivo de ejemplo"]
    assert vh.evaluar_reglas(bloque, LOOKUPS_OK, None) == []


def test_aud_v01_el_ejemplo_generado_lleva_file_pattern_en_validate_y_metrics(generated):
    """Sin pyhocon: el texto generado declara filePattern dentro de validate[0] y de metrics[0]."""
    texto = (generated / vh.CARPETA_PROPUESTA / "validation-configs-sisben.conf").read_text(encoding="utf-8")
    validate = texto[texto.index("validate = ["):texto.index("metrics = [")]
    metrics = texto[texto.index("metrics = ["):]
    assert validate.count("filePattern = ") == 1 and metrics.count("filePattern = ") == 1


def test_aud_v06_lookup_sin_lookup_home_falla():
    lookups = [{"tableName": "TABLA_SINTETICA_A", "file": "a.csv"}]
    assert vh.evaluar_reglas(BLOQUE_OK, lookups, MUESTRA) == [
        "lookup TABLA_SINTETICA_A: file sin ${oxygen.application.paths.lookupHome}"]


def test_aud_v06_lookups_generados_resuelven_lookup_home(generated):
    pytest.importorskip("pyhocon")
    _, lookups = vh.leer_fragmentos(generated, "sisben", vh.cargar_parser())
    assert lookups and all(str(l["file"]).startswith(vh.LOOKUP_HOME_SINTETICO + "/") for l in lookups)


def test_aud_v01_con_pyhocon_detecta_validate_y_metrics_sin_file_pattern(generated):
    """Reproduce la propuesta rechazada (sin filePattern en validate[0]/metrics[0], file sin lookupHome)."""
    pytest.importorskip("pyhocon")
    conf = generated / vh.CARPETA_PROPUESTA / "validation-configs-sisben.conf"
    lineas = conf.read_text(encoding="utf-8").split("\n")
    principal = next(l for l in lineas if l.startswith("  filePattern = "))
    conf.write_text("\n".join(l for l in lineas if not l.startswith("      filePattern = ") or l == principal),
                    encoding="utf-8")
    lookups = generated / vh.CARPETA_PROPUESTA / "lookups-sisben.conf"
    lookups.write_text(lookups.read_text(encoding="utf-8").replace('${oxygen.application.paths.lookupHome}"/', '"'),
                       encoding="utf-8")
    problemas = vh.check(generated, "sisben", MUESTRA)
    assert "validate[0] sin filePattern: el motor hace data.filePattern.get (NoSuchElementException en cada archivo)" in problemas
    assert "metrics[0] sin filePattern: el motor hace data.filePattern.get (NoSuchElementException en cada archivo)" in problemas
    assert sum("file sin ${oxygen.application.paths.lookupHome}" in p for p in problemas) == 7
