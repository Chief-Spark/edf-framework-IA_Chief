"""Pruebas del generador vigente render_templates.py (sin Java ni compilación).

Traducen la suite Java que lo acompañaba hasta la versión 1.3.x (scripts/java/src/test/.../RenderTemplatesTest.java).
Datos SINTÉTICOS (source-spec de ejemplo de SISBEN)."""
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

SKILL = SCRIPTS.parent
SPEC_PATH = SKILL / "assets" / "examples" / "sisben" / "source-spec.sisben.yaml"
GEN_PATH = SKILL / "assets" / "examples" / "sisben" / "generated"
PROPUESTA = "env/application-conf/innersource/propuesta/"
ID = re.compile(r"F-\d{2}|VAL-P[AT]\d{2}|VAL-SP\d{2}")
MARCA = re.compile(r"ASSUMPTION[ \t]+(" + ID.pattern + r")\b")


@pytest.fixture()
def spec() -> dict:
    return rt.load_spec(SPEC_PATH)


def contiene(errores: list[str], texto: str) -> bool:
    return any(texto in e for e in errores)


def arbol(root: Path) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): p.read_text(encoding="utf-8")
            for p in sorted(root.rglob("*")) if p.is_file()}


def salida(root: Path) -> dict[str, str]:
    out = arbol(root)
    out.pop("manifest.json", None)
    return out


def entradas(root: Path) -> list[str]:
    return sorted(str(p.relative_to(root)) for p in root.rglob("*")) if root.exists() else []


# ------------------------------------------------------------------ verificación del spec
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


def test_codigo_de_longitud_distinta_falla(spec):
    bad = copy.deepcopy(spec)
    bad["campos"][0]["codigo"] = "SISBEN_ERR_1"
    assert contiene(rt.verify_spec(bad), "longitud fija")


def test_colision_de_subcadena_falla(spec):
    bad = copy.deepcopy(spec)
    for f in bad["campos"]:
        f["codigo"] += "X"
    bad["campos"][1]["codigo"] = "SISBEN_ERR_00"
    bad["campos"][0]["codigo"] = "SISBEN_ERR_001"
    assert contiene(rt.check_codes(bad), "subcadena")


def test_codigo_de_11_caracteres_falla(spec):
    bad = copy.deepcopy(spec)
    for i, c in enumerate(bad["campos"]):
        c["codigo"] = f"SISBENE{i:04d}"
    for i, r in enumerate(bad["reglas_entre_campos"]):
        r["codigo"] = f"SISBENR{i:04d}"
    bad["codigos_especiales"] = {k: f"SISBENS{i:04d}" for i, k in enumerate(bad["codigos_especiales"])}
    for caso in bad["fixture"]["casos"]:
        caso["esperado"]["codigos"] = []
    assert contiene(rt.verify_spec(bad), "11 caracteres")


def test_codigo_igual_a_la_marca_de_duplicado_falla(spec):
    bad = copy.deepcopy(spec)
    bad["codigos_especiales"]["interno"] = "Oxygen"
    assert contiene(rt.check_codes(bad), "colisión de subcadena con OxygenDuplicate")


def test_codigo_repetido_entre_campos_falla(spec):
    bad = copy.deepcopy(spec)
    bad["campos"][1]["codigo"] = bad["campos"][0]["codigo"]
    assert contiene(rt.check_codes(bad), "código propio")


def test_file_pattern_sin_prefijo_falla(spec):
    bad = copy.deepcopy(spec)
    bad["archivo"]["file_pattern"] = "SISBEN_EXPERIAN_.*"
    assert rt.verify_spec(bad)


def test_duplicados_sin_clave_falla(spec):
    bad = copy.deepcopy(spec)
    bad["duplicados"]["clave"] = []
    assert contiene(rt.verify_spec(bad), "clave no vacía")


def test_columna_de_catalogo_fuera_de_keys_falla(spec):
    bad = copy.deepcopy(spec)
    bad["campos"][1]["catalogo"]["columna"] = "descripcion"
    assert contiene(rt.verify_spec(bad), "keys")


def test_nombre_de_quality_score_en_expresion_falla(spec):
    bad = copy.deepcopy(spec)
    bad["quality_scores"][0]["nombre"] = "error"
    assert contiene(rt.verify_spec(bad), "quality score")


def test_footer_sin_regex_falla(spec):
    bad = copy.deepcopy(spec)
    bad["archivo"]["filas_footer"] = 1
    assert contiene(rt.verify_spec(bad), "footer_regex")


def test_historic_attributes_true_falla_por_esquema(spec):
    bad = copy.deepcopy(spec)
    bad["ambiente"]["historic_attributes"] = True
    assert rt.verify_spec(bad)


def test_errores_de_esquema_ordenados_y_con_ruta(spec):
    bad = copy.deepcopy(spec)
    bad["campos"][3]["severidad"] = "2"
    del bad["ambiente"]
    errores = rt.verify_spec(bad)
    assert errores == sorted(errores, key=lambda e: e.split(":")[0])
    assert contiene(errores, "campos/3/severidad:") and contiene(errores, "(raíz):")


def test_sin_jsonschema_falla_en_vez_de_omitir(spec, monkeypatch):
    monkeypatch.setitem(sys.modules, "jsonschema", None)
    with pytest.raises(rt.SpecError, match="jsonschema"):
        rt.verify_spec(spec)


def test_reglas_semanticas_detectan_referencias_y_fixture(spec):
    bad = copy.deepcopy(spec)
    bad["campos"][0]["posicion"] = 99
    bad["campos"][1]["nombre"] = bad["campos"][2]["nombre"]
    bad["guard_rails"]["subscriber_relation"] = "NO_EXISTE"
    cat = bad["catalogos"][0]
    cat["keys"] = ["no_es_columna"]
    cat["filas_sinteticas"] = [["solo-un-valor"]]
    bad["fixture"]["registro_base"] = ["x"]
    casos = bad["fixture"]["casos"]
    casos.append(copy.deepcopy(casos[0]))
    casos[0]["cambios"] = {"NO_EXISTE": "1"}
    casos[0]["esperado"]["codigos"] = ["SISBEN_ERR_999"]
    errores = rt.check_fields(bad) + rt.check_references(bad) + rt.check_fixture(bad)
    for esperado in ("posiciones", "nombres de campo repetidos", "campo inexistente NO_EXISTE", "no es una columna",
                     "fila sintética", "un valor por campo", "código esperado no declarado",
                     "ids de casos del fixture repetidos"):
        assert contiene(errores, esperado), f"{esperado} en {errores}"


def test_referencias_de_reglas_tablas_y_tipos(spec):
    bad = copy.deepcopy(spec)
    bad["reglas_entre_campos"][0]["tabla"] = "NO_DECLARADA"
    bad["reglas_entre_campos"][1]["columna_nombre"] = "no_existe"
    bad["guard_rails"]["as_of_date"] = "NUM_DOCUMENTO"
    bad["duplicados"]["campo_fecha"] = "NUM_DOCUMENTO"
    errores = rt.check_references(bad)
    for esperado in ("tabla NO_DECLARADA no declarada", "columna no_existe no existe",
                     "guard_rails.as_of_date: NUM_DOCUMENTO debe ser de tipo fecha",
                     "duplicados.campo_fecha: NUM_DOCUMENTO debe ser de tipo fecha"):
        assert contiene(errores, esperado), f"{esperado} en {errores}"


def test_campo_fecha_y_obligatoriedad(spec):
    bad = copy.deepcopy(spec)
    fecha = next(c for c in bad["campos"] if c["tipo"] == "fecha")
    fecha["formatos_fecha"] = ["dd/MMM/yyyy"]
    otra = next(c for c in bad["campos"] if c["tipo"] == "fecha" and c is not fecha)
    otra["formatos_fecha"] = []
    bad["campos"][0]["severidad"] = "3"
    errores = rt.check_fields(bad)
    for esperado in ("formato de fecha no soportado", "requiere formatos_fecha", "obligatorio ⇒ severidad 1"):
        assert contiene(errores, esperado), f"{esperado} en {errores}"


def test_contrato_de_archivo_detecta_regex_y_nombre(spec):
    bad = copy.deepcopy(spec)
    bad["archivo"]["nombre"]["grupos"]["prefijo"] = 99
    bad["fixture"]["nombre_archivo"] = "otro/nombre.txt"
    errores = rt.check_file_contract(bad)
    assert contiene(errores, "excede los grupos"), errores
    assert contiene(errores, "no cumple archivo.nombre.regex"), errores
    assert contiene(errores, "no cumple archivo.file_pattern"), errores
    assert contiene(errores, "comillas, barras"), errores
    bad["archivo"]["file_pattern"] = "("
    assert contiene(rt.check_file_contract(bad), "regex inválido")


# ------------------------------------------------------------------ utilidades
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
    assert rt.scan_forbidden("token=SintTok3nLarg0Xyz12")
    assert rt.scan_forbidden("texto normal sin secretos") == []


def test_fill_reporta_huecos_faltantes():
    assert rt.fill("{{a}} y {{b}}", {"a": "1"}) == ("1 y {{b}}", {"b"})


def test_fixture_line_builder(spec):
    caso = {"id": "X", "descripcion": "", "cambios": {"TIP_DOCUMENTO": "PPT"}, "columnas": 14, "esperado": {}}
    parts = rt.build_fixture_line(spec, caso).split(";")
    assert parts[-1] == "PPT" and len(parts) == 14
    assert re.fullmatch(spec["archivo"]["nombre"]["regex"], spec["fixture"]["nombre_archivo"])
    assert rt.build_fixture_line(spec, {"linea": "a;b"}) == "a;b"


def test_load_spec_rechaza_lo_que_no_es_objeto(tmp_path):
    lista = tmp_path / "lista.yaml"
    lista.write_text("- a\n- b\n", encoding="utf-8")
    with pytest.raises(rt.SpecError, match="objeto YAML"):
        rt.load_spec(lista)


def test_modo_de_duplicados_desconocido(spec):
    bad = copy.deepcopy(spec)
    bad["duplicados"]["modo"] = "OTRO"
    with pytest.raises(rt.SpecError, match="duplicados.modo desconocido"):
        rt.build_context(bad)


def test_contexto_con_valores_opcionales(spec):
    alt = copy.deepcopy(spec)
    alt["archivo"]["encoding"] = "ISO-8859-1"
    alt["archivo"]["filas_footer"] = 1
    alt["archivo"]["footer_regex"] = "^TOTAL;.*"
    alt["archivo"]["nombre"]["file_as_of_date"] = "primer_dia_periodo"
    alt["duplicados"]["umbral"] = 5
    alt["duplicados"]["campo_fecha"] = None
    alt["filtro_filas"] = {"lineas_vacias": False}
    ctx = rt.build_context(alt)
    assert ctx["linea_encoding"] == '  encoding = "ISO-8859-1"'
    assert ctx["is_footer_row_expr"].startswith("Try(Option(row).exists(")
    assert ctx["expr_file_as_of_date"] == "i.periodo.atDay(1)"
    assert ctx["linea_duplicate_threshold"] == "  duplicateThreshold = 5"
    assert ctx["dup_campo_fecha_scala"] == "None" and ctx["dup_campo_fecha_desc"] == "sin fecha"
    assert ctx["filtro_posicion_scala"] == "None" and ctx["filtro_descripcion"] == "sin filtro"


def test_fixture_sin_conservado_y_sin_duplicados(spec):
    alt = copy.deepcopy(spec)
    for caso in alt["fixture"]["casos"]:
        if "duplicado" in caso["esperado"]:
            caso["esperado"]["duplicado"] = True
    assert "indiceAConservar(candidatos) == -1" in rt.render_fixture_tests(alt)
    for caso in alt["fixture"]["casos"]:
        caso["esperado"].pop("duplicado", None)
    assert "fixture duplicados" not in rt.render_fixture_tests(alt)


# ------------------------------------------------------------------ generación
def test_render_no_deja_huecos_y_escribe_manifiesto(spec, tmp_path):
    manifest = rt.render_all(spec, tmp_path)
    assert manifest["compilacion"].startswith("NO CERTIFICADA")
    assert len(manifest["archivos"]) == len(rt.TEMPLATE_TARGETS) + len(spec["catalogos"])
    for archivo in manifest["archivos"]:
        text = (tmp_path / archivo["ruta"]).read_text(encoding="utf-8")
        assert not rt.PLACEHOLDER_RE.search(text), archivo["ruta"]
        assert rt.scan_forbidden(text) == []
    assert '"fuente": "SISBEN"' in (tmp_path / "manifest.json").read_text(encoding="utf-8")


def test_ejemplo_persistido_igual_a_regeneracion(spec, tmp_path):
    """El ejemplo persistido (assets/examples/sisben/generated) es igual byte a byte a una regeneración."""
    rt.render_all(spec, tmp_path)
    assert arbol(GEN_PATH) == arbol(tmp_path)


def test_bloque_hocon_usa_claves_verificadas(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    conf = (tmp_path / PROPUESTA / "validation-configs-sisben.conf").read_text(encoding="utf-8")
    assert "headerRows = 1" in conf and "footerRows = 0" in conf
    assert "numberOfHeaderRows" not in conf and "numberOfFooterRows" not in conf
    assert 'deDupVersion = "EXTN"' in conf and "computeMetrics = true" in conf
    assert "\nencoding" not in conf and "  encoding =" not in conf
    assert 'filePattern = ".*' in conf
    assert "contexual" in conf


def test_hocon_generado_parsea(spec, tmp_path):
    pyhocon = pytest.importorskip("pyhocon")
    rt.render_all(spec, tmp_path)
    texto = (tmp_path / PROPUESTA / "validation-configs-sisben.conf").read_text(encoding="utf-8")
    block = pyhocon.ConfigFactory.parse_string("configs = [\n" + texto + "\n]")["configs"][0]
    for clave in ("filePattern", "processor", "headerRows", "footerRows", "computeMetrics", "validate"):
        assert clave in block, f"falta la clave {clave}"
    pattern = re.compile(block["filePattern"])
    assert pattern.fullmatch("/home/app/validation/input/" + spec["fixture"]["nombre_archivo"])
    lookups = pyhocon.ConfigFactory.parse_string(
        "lookups = " + (tmp_path / PROPUESTA / "lookups-sisben.conf").read_text(encoding="utf-8"))["lookups"]
    assert len(lookups) == len(spec["catalogos"])


def test_processor_no_usa_apis_prohibidas(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    text = next(tmp_path.rglob("SISBENValidationProcessor.scala")).read_text(encoding="utf-8")
    assert "class SISBENValidationProcessor(processorContext: ProcessorContext) extends ValidationProcessor" in text
    for prohibido in ("println", "decodeErrorObject", "HistoricAttributes", "throw "):
        assert prohibido not in text, prohibido
    assert "@transient private lazy val logger" in text


@pytest.mark.parametrize("credencial", ["password=" + "X" * 14, "token=" + "Y" * 20, "AKIA" + "Z" * 16,
                                        "settings-artifactory.xml"])
def test_credencial_en_el_spec_detiene_la_generacion(spec, tmp_path, credencial):
    """AC-22: la generación falla completa y el directorio de salida queda vacío."""
    bad = copy.deepcopy(spec)
    bad["spec_version"] = "sisben-v4-0.1.0 " + credencial
    with pytest.raises(rt.SpecError, match="patrón prohibido"):
        rt.render_all(bad, tmp_path)
    assert entradas(tmp_path) == []


def test_credencial_en_un_catalogo_sintetico_detiene_la_generacion(spec, tmp_path):
    bad = copy.deepcopy(spec)
    bad["catalogos"][0]["filas_sinteticas"].append(["16", "token=" + "W" * 20])
    with pytest.raises(rt.SpecError) as exc:
        rt.render_all(bad, tmp_path)
    assert "lookup/SISBEN_HOME_ID_CLASIFICATION_NH.csv: patrón prohibido" in str(exc.value)
    assert entradas(tmp_path) == []


def test_credencial_solo_en_el_manifest_detiene_la_generacion(spec, tmp_path, monkeypatch):
    original = rt.scan_forbidden
    monkeypatch.setattr(rt, "scan_forbidden", lambda text: ["x"] if '"skill"' in text else original(text))
    with pytest.raises(rt.SpecError, match="manifest.json: patrón prohibido"):
        rt.render_all(spec, tmp_path)
    assert entradas(tmp_path) == []


# ------------------------------------------------------------------ supuestos (AC-18a, AC-18b, ADR-G1)
def test_toda_marca_assumption_tiene_id(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    marcas, sin_id = 0, []
    for ruta, text in salida(tmp_path).items():
        for numero, linea in enumerate(text.split("\n"), start=1):
            for pos in [m.start() for m in re.finditer("ASSUMPTION", linea)]:
                if MARCA.match(linea, pos):
                    marcas += 1
                else:
                    sin_id.append(f"{ruta}:{numero}")
    assert sin_id == []
    assert marcas > 0


def test_supuestos_del_manifest_iguales_a_las_marcas(spec, tmp_path):
    manifest = rt.render_all(spec, tmp_path)
    en_codigo = sorted({m for text in salida(tmp_path).values() for m in MARCA.findall(text)})
    assert manifest["supuestos"] == en_codigo
    assert '"VAL-SP01"' in (tmp_path / "manifest.json").read_text(encoding="utf-8")


def supuestos_activos_del_documento(doc: Path) -> set[str]:
    """Filas "sí" de la columna "En el código generado" de decisiones-y-abiertos.md."""
    activos, vistos, col = set(), set(), -1
    for line in doc.read_text(encoding="utf-8").split("\n"):
        if not line.startswith("|"):
            col = -1
            continue
        cells = [c.strip() for c in line.split("|")]
        if "En el código generado" in cells:
            col = cells.index("En el código generado")
        if col > 0 and len(cells) > col and ID.fullmatch(cells[1]):
            assert cells[1] not in vistos, f"ID repetido en el documento: {cells[1]}"
            vistos.add(cells[1])
            if cells[col].startswith("sí"):
                activos.add(cells[1])
    return activos


def test_supuestos_del_manifest_en_decisiones(spec, tmp_path):
    manifest = rt.render_all(spec, tmp_path)
    activos = supuestos_activos_del_documento(SKILL / "references" / "decisiones-y-abiertos.md")
    en_manifest = set(manifest["supuestos"])
    assert en_manifest - activos == set(), "en el manifest y no activos en el documento"
    assert activos - en_manifest == set(), "activos en el documento y no en el manifest"


def _plantillas_minimas(root: Path, especial: str, contenido: str) -> Path:
    templates = root / "tpl"
    for rel in rt.TEMPLATE_TARGETS:
        dst = templates / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(contenido if rel == especial else "x", encoding="utf-8")
    return templates


def test_marca_sin_id_detiene_la_generacion(spec, tmp_path):
    templates = _plantillas_minimas(tmp_path, "lookups.conf.tpl", "# ASSUMPTION F-12: ok\n# ASSUMPTION: sin id\n")
    out = tmp_path / "out"
    with pytest.raises(rt.SpecError) as exc:
        rt.render_all(spec, out, templates)
    assert "lookups.conf.tpl: marca ASSUMPTION sin ID en las líneas [2]" in str(exc.value)
    assert not out.exists()
    assert rt.marcas_supuesto("ASSUMPTION F-12: a\n(ASSUMPTION F-12)") == ["F-12", "F-12"]
    assert rt.marcas_sin_id("ASSUMPTION (F-07)\nASSUMPTION\nASSUMPTION VAL-SP01: b") == [1, 2]


def test_hueco_sin_valor_se_reporta(spec, tmp_path):
    templates = _plantillas_minimas(tmp_path, "fixture.txt.tpl", "{{no_existe}}")
    with pytest.raises(rt.SpecError, match=re.escape("fixture.txt.tpl: huecos sin valor ['no_existe']")):
        rt.render_all(spec, tmp_path / "out", templates)


def test_hueco_que_aparece_tras_rellenar_se_reporta(spec, tmp_path, monkeypatch):
    templates = _plantillas_minimas(tmp_path, "fixture.txt.tpl", "{{header_linea}}")
    original = rt.build_context
    monkeypatch.setattr(rt, "build_context", lambda s: {**original(s), "header_linea": "{{otro}}"})
    with pytest.raises(rt.SpecError, match=re.escape("quedaron huecos sin resolver ['otro']")):
        rt.render_all(spec, tmp_path / "out", templates)


def test_patron_prohibido_en_una_plantilla_se_reporta(spec, tmp_path):
    templates = _plantillas_minimas(tmp_path, "fixture.txt.tpl", "password = algo")
    with pytest.raises(rt.SpecError, match="fixture.txt.tpl: patrón prohibido"):
        rt.render_all(spec, tmp_path / "out", templates)


# ------------------------------------------------------------------ métricas (AC-VAL-54a)
def test_metrica_con_quality_score_no_declarado_falla(spec):
    bad = copy.deepcopy(spec)
    bad["metricas"].append({"id": "AverageFQS", "expr": 'average("qs.FQS")'})
    assert rt.verify_spec(bad) == ["métrica AverageFQS usa el quality score no declarado FQS (AC-VAL-54a)"]


def test_metrica_sin_quality_score_pasa(spec):
    ok = copy.deepcopy(spec)
    ok["metricas"] = [{"id": "TotalRegistros", "expr": "count(*)"}]
    assert rt.check_metrics(ok) == []
    assert rt.verify_spec(ok) == []


# ------------------------------------------------------------------ línea de comandos
def test_cli_solo_verificar_y_error(tmp_path, capsys):
    assert rt.main(["--spec", str(SPEC_PATH), "--solo-verificar"]) == 0
    assert capsys.readouterr().out.startswith("source-spec OK (22 campos")
    bad = tmp_path / "malo.yaml"
    bad.write_text(SPEC_PATH.read_text(encoding="utf-8-sig").replace("fuente: sisben", "fuente: SISBEN"),
                   encoding="utf-8")
    assert rt.main(["--spec", str(bad), "--out", str(tmp_path / "out")]) == 2
    assert capsys.readouterr().err.startswith("El source-spec NO pasa la verificación:")
    assert not (tmp_path / "out").exists()


def test_cli_render(tmp_path, capsys):
    assert rt.main(["--spec", str(SPEC_PATH), "--out", str(tmp_path)]) == 0
    assert (tmp_path / "manifest.json").exists()
    # 12 plantillas + 7 CSV de catálogo (manifest.json no se cuenta a sí mismo)
    assert "Generados 19 archivos" in capsys.readouterr().out
    assert json.loads((tmp_path / "manifest.json").read_text(encoding="utf-8"))["fuente"] == "SISBEN"


def test_cli_errores_de_uso(tmp_path, capsys):
    for argv in (["--out", "x"], ["--otro"]):
        with pytest.raises(SystemExit) as exc:
            rt.main(argv)
        assert exc.value.code == 2
    assert rt.main(["--spec", str(SPEC_PATH)]) == 2
    assert rt.main(["--spec", str(tmp_path / "no-existe.yaml")]) == 2


def test_cli_error_inesperado(tmp_path, capsys, monkeypatch):
    monkeypatch.setattr(rt, "render_all", lambda *a, **k: 1 / 0)
    assert rt.main(["--spec", str(SPEC_PATH), "--out", str(tmp_path)]) == 1
    assert "ERROR inesperado" in capsys.readouterr().err
