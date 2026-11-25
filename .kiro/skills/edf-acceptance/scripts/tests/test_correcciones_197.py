"""Regresión del lote de cierre de la corrida E2E SISBEN (etapa Acceptance): HF-26 (marcadores con
`validatedRecordJson: null`), HF-29 (supuestos genéricos y criterio por spec) y HF-33 (AUD-A-16, -17: quinto
argumento de `AcceptanceCondition` y prueba Scala del `filePattern`). Las de solo texto (HF-30, HF-31) están en
tests/test_regresion_e2e_acceptance.py, en la raíz del framework.

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

import render_templates as rt  # noqa: E402
import simular_decision as sim  # noqa: E402

SPEC_PATH = SKILL / "assets" / "examples" / "sisben" / "source-spec.sisben.yaml"
SCHEMA_PATH = SKILL / "assets" / "schema" / "source-spec.schema.yaml"
GEN_PATH = SKILL / "assets" / "examples" / "sisben" / "generated"
DECISIONES = SKILL / "references" / "decisiones-y-abiertos.md"
CHECKLIST = SKILL / "assets" / "checklist-entrega.md"
SPEC_SCALA = "src/test/scala/com/experian/edf/oxygen/acceptance/source/sisben/SISBENAcceptanceExpressionsSpec.scala"
VALIDATED = "src/test/resources/sisben/validated"


@pytest.fixture()
def spec() -> dict:
    return rt.load_yaml(SPEC_PATH)


@pytest.fixture(scope="module")
def schema() -> dict:
    return rt.load_yaml(SCHEMA_PATH)


def _escribir(tmp_path: Path, spec: dict, nombre: str = "spec.yaml") -> Path:
    path = tmp_path / nombre
    path.write_text(yaml.safe_dump(spec, allow_unicode=True, sort_keys=False), encoding="utf-8")
    return path


def _marcadores(spec: dict) -> list[dict]:
    return [e for a in spec["fixture"]["archivos"] for e in a["eventos"] if e["eventType"] < 0]


# ------------------------------------------------------------------ HF-26: validatedRecordJson null

def test_hf26_schema_admite_null_y_conserva_los_casos_existentes(spec, schema):
    assert rt.validate_schema(spec, schema) == []
    assert all(isinstance(e["validatedRecordJson"], str) for a in spec["fixture"]["archivos"] for e in a["eventos"])
    con_null = copy.deepcopy(spec)
    for e in _marcadores(con_null):
        e["validatedRecordJson"] = None
    assert _marcadores(con_null)
    assert rt.validate_schema(con_null, schema) == []
    malo = copy.deepcopy(spec)
    malo["fixture"]["archivos"][0]["eventos"][0]["validatedRecordJson"] = 5
    assert rt.validate_schema(malo, schema)
    sin = copy.deepcopy(spec)
    del sin["fixture"]["archivos"][0]["eventos"][0]["validatedRecordJson"]
    assert rt.validate_schema(sin, schema)  # sigue siendo obligatorio


def test_hf26_la_plantilla_escribe_null_y_nada_mas_cambia(spec, tmp_path):
    con_null = copy.deepcopy(spec)
    for e in _marcadores(con_null):
        e["validatedRecordJson"] = None
    rt.generate(con_null, tmp_path)
    cambiados = 0
    for archivo in con_null["fixture"]["archivos"]:
        rel = f"{VALIDATED}/{archivo['directorio']}/part-00000.json"
        texto = (tmp_path / rel).read_text(encoding="utf-8")
        if any(e["eventType"] < 0 for e in archivo["eventos"]):
            cambiados += 1
            for line in texto.splitlines():
                assert '"validatedRecordJson":null,' in line
                assert json.loads(line)["validatedRecordJson"] is None
        else:
            assert texto == (GEN_PATH / rel).read_text(encoding="utf-8")
    assert cambiados >= 1
    assert (tmp_path / SPEC_SCALA).read_text(encoding="utf-8") == (GEN_PATH / SPEC_SCALA).read_text(encoding="utf-8")
    assert all(r["coincide"] for r in sim.simular_y_comparar(con_null) if "directorio" in r)


def test_hf26_cli_acepta_la_spec_con_null(spec, tmp_path, capsys):
    con_null = copy.deepcopy(spec)
    for e in _marcadores(con_null):
        e["validatedRecordJson"] = None
    code = rt.main(["--spec", str(_escribir(tmp_path, con_null)), "--out", str(tmp_path / "o"), "--audit"])
    assert code == 0, capsys.readouterr().out


# ------------------------------------------------------------------ HF-29: supuestos genéricos

def _catalogo() -> dict[str, list[str]]:
    doc = DECISIONES.read_text(encoding="utf-8")
    desde = doc.index("## Supuestos propios de este skill")
    hasta = doc.find("\n## ", desde + 1)
    filas = {}
    for line in doc[desde:hasta].split("\n"):
        if line.startswith("| S-ACC-"):
            cells = line.split("|")
            filas[cells[1].strip()] = [c.strip() for c in cells[2:5]]
    return filas


def test_hf29_catalogo_sin_datos_de_un_fixture():
    filas = _catalogo()
    assert set(filas) == {"S-ACC-01", "S-ACC-02", "S-ACC-03", "S-ACC-04", "S-ACC-05"}
    for sid, fila in filas.items():
        texto = " ".join(fila)
        assert not re.search(r"\b9\d{5}\b", texto), sid  # sufijos de directorio del fixture
        assert not re.search(r"\b\d+ directorios\b", texto), sid
        for dato in ("SISBEN", "SISBFA", "RQS"):
            assert dato not in texto, (sid, dato)
    assert "archivo del fixture sin QS" in filas["S-ACC-03"][1] and "archivo del fixture sin QS" in filas["S-ACC-03"][2]
    assert "los directorios del fixture" in filas["S-ACC-05"][1]
    doc = DECISIONES.read_text(encoding="utf-8")
    assert "**catálogo**" in doc and "para esa spec" in doc


def test_hf29_spec_sin_s_acc_02_y_texto_propio_imprime_exactamente_los_suyos(spec, schema, tmp_path, capsys):
    propia = copy.deepcopy(spec)
    propia["supuestos"] = [s for s in propia["supuestos"] if s["id"] != "S-ACC-02"]
    s03 = next(s for s in propia["supuestos"] if s["id"] == "S-ACC-03")
    s03["impacto"] = "El fixture 900005 y el simulador esperan pendiente"
    s03["donde"] = "simulador, fixture 900005"
    assert rt.validate_schema(propia, schema) == []
    ids = [s["id"] for s in rt.supuestos(propia)]
    assert "S-ACC-02" not in ids and set(ids) <= set(_catalogo())
    code = rt.main(["--spec", str(_escribir(tmp_path, propia)), "--out", str(tmp_path / "o"), "--audit"])
    out = capsys.readouterr().out
    assert code == 0, out
    impresos = [l for l in out.splitlines() if l.startswith("supuesto  ")]
    assert impresos == [rt.linea_supuesto(s) for s in rt.supuestos(propia)]
    assert "fixture 900005" in "\n".join(impresos)


def test_hf29_checklist_pide_los_supuestos_que_imprime_el_generador():
    texto = CHECKLIST.read_text(encoding="utf-8")
    assert "son los mismos de `decisiones-y-abiertos.md`" not in texto
    item = texto[texto.index("- [ ] `--audit` lista una línea `supuesto"):]
    item = item[:item.index("\n- [ ]")]
    plano = re.sub(r"\s+", " ", item)
    assert "declara los supuestos que imprime el generador para esa spec" in plano
    assert "catálogo" in plano and "HF-29" in plano


# ------------------------------------------------------------------ HF-33: Spec (AUD-A-16, -17)

def _argumentos(texto: str, desde: int) -> list[str]:
    """Argumentos de primer nivel de la llamada que abre en texto[desde] == '(' (respeta comillas y comentarios)."""
    args, actual, nivel, i, en_cadena = [], [], 0, desde + 1, False
    while True:
        c = texto[i]
        if en_cadena:
            actual.append(c)
            if c == "\\":
                actual.append(texto[i + 1])
                i += 1
            elif c == '"':
                en_cadena = False
        elif c == '"':
            en_cadena = True
            actual.append(c)
        elif texto.startswith("/*", i):
            i = texto.index("*/", i) + 1
        elif c in "([{":
            nivel += 1
            actual.append(c)
        elif c in ")]}" and nivel:
            nivel -= 1
            actual.append(c)
        elif c == ")":
            args.append("".join(actual).strip())
            return args
        elif c == "," and nivel == 0:
            args.append("".join(actual).strip())
            actual = []
        else:
            actual.append(c)
        i += 1


def _llamadas_acceptance_condition(scala: str) -> list[list[str]]:
    return [_argumentos(scala, m.end() - 1) for m in re.finditer(r"(?<![\w.])AcceptanceCondition\(", scala)]


def test_hf33_acceptance_condition_con_cinco_argumentos_y_result_false(spec, tmp_path):
    alt = copy.deepcopy(spec)
    alt["fixture"]["umbrales_alternativos"] = [{"umbral": "0.95", "casos": [
        {"id": "FQS_095", "registros_validos": 19, "registros_totales": 20, "esperado": "aceptado"}]}]
    rt.generate(alt, tmp_path)
    for scala in ((GEN_PATH / SPEC_SCALA).read_text(encoding="utf-8"), (tmp_path / SPEC_SCALA).read_text(encoding="utf-8")):
        llamadas = _llamadas_acceptance_condition(scala)
        assert llamadas
        for args in llamadas:
            assert len(args) == 5 and args[-1] == "false", args
    assert len(_llamadas_acceptance_condition((tmp_path / SPEC_SCALA).read_text(encoding="utf-8"))) == len(spec["condiciones"]) + 1


def _literal_scala(scala: str, nombre: str) -> str:
    m = re.search(rf'private val {nombre} = "((?:[^"\\]|\\.)*)"', scala)
    assert m, nombre
    return re.sub(r"\\(.)", r"\1", m.group(1))


def _casos_de_clave(scala: str) -> list[tuple[str, str, bool]]:
    bloque = scala[scala.index("private val fileKeyCases"):]
    bloque = bloque[:bloque.index("\n  )\n")]
    casos = re.findall(r'\("((?:[^"\\]|\\.)*)", "((?:[^"\\]|\\.)*)", (true|false)\)', bloque)
    return [(re.sub(r"\\(.)", r"\1", c), re.sub(r"\\(.)", r"\1", v), d == "true") for c, v, d in casos]


@pytest.mark.parametrize("variante", ["ejemplo", "patron_con_alternativa"])
def test_hf33_spec_prueba_el_file_pattern_con_las_claves_del_insumo(spec, tmp_path, variante):
    if variante == "patron_con_alternativa":  # forma del insumo SISBEN 1.0.2: (txt|TXT) y una clave de mayúsculas
        spec = copy.deepcopy(spec)
        spec["archivo"]["filePattern"] = spec["archivo"]["filePattern"][:-len(r"\.txt")] + r"\.(txt|TXT)"
        spec["archivo"]["claves_de_prueba"].append(
            {"clave": "lineage_fileName_mayusculas", "valor": "SISBEN_EXPERIAN_202401.20240131120000-0500.TXT",
             "debe_coincidir": True})
        assert rt.verify_spec(spec) == []
    rt.generate(spec, tmp_path)
    scala = (tmp_path / SPEC_SCALA).read_text(encoding="utf-8")
    patron = _literal_scala(scala, "FilePattern")
    assert patron == spec["archivo"]["filePattern"]
    casos = _casos_de_clave(scala)
    assert casos == [(c["clave"], c["valor"], c["debe_coincidir"]) for c in spec["archivo"]["claves_de_prueba"]]
    assert any(not d for _, _, d in casos) and any("errorIn" in v for _, v, _ in casos)
    # La prueba Scala aplica la regla del motor: ruta_* con removeFileExtension (dos si errorIn) y String.matches.
    assert 'test("filePattern: cada clave de prueba del insumo coincide o no como se declaró (E3, ACC-R03, HF-33)")' in scala
    assert 'if (clave.startsWith("ruta_")) engineFileKey(valor) else valor' in scala
    assert "key.matches(FilePattern) == expected" in scala
    assert 'if (path.contains("errorIn")) FilenameUtils.removeExtension(stripped) else stripped' in scala
    assert "import org.apache.commons.io.FilenameUtils" in scala
    # Réplica en Python de lo que evaluará el Spec: con los literales generados da lo declarado.
    regex = re.compile(patron)
    for clave, valor, esperado in casos:
        key = rt.engine_file_key(valor) if clave.startswith("ruta_") else valor
        assert bool(regex.fullmatch(key)) == esperado, clave


def test_hf33_ejemplo_regenerado_igual_a_la_plantilla(spec, tmp_path):
    rt.generate(spec, tmp_path)
    assert (tmp_path / SPEC_SCALA).read_text(encoding="utf-8") == (GEN_PATH / SPEC_SCALA).read_text(encoding="utf-8")
