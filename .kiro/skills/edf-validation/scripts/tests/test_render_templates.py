"""Pruebas del generador vigente render_templates.py (sin Java ni compilación).

Son la suite vigente del generador. Reemplazan a la suite del generador Java deprecado (histórico, ver
scripts/DEPRECADOS.md); no dependen de él. Datos SINTÉTICOS (source-spec de ejemplo de SISBEN)."""
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
    assert ctx["is_footer_row_expr"] == "Option(row).exists(r => patronFooter.matcher(r.trim).matches())"
    assert "override def validateFooter" in ctx["bloque_validate_footer"]
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
    for elemento in list(block["validate"]) + list(block["metrics"]):  # AUD-V-01: el motor hace filePattern.get
        assert re.compile(elemento["filePattern"]).fullmatch("/home/app/validation/input/" + spec["fixture"]["nombre_archivo"])
    lookups = pyhocon.ConfigFactory.parse_string(
        "lookups = " + (tmp_path / PROPUESTA / "lookups-sisben.conf").read_text(encoding="utf-8")
        + '\noxygen.application.paths.lookupHome = "/lookupHome"\n')["lookups"]
    assert len(lookups) == len(spec["catalogos"])
    assert [l["file"] for l in lookups] == [f"/lookupHome/{c['archivo']}" for c in spec["catalogos"]]  # AUD-V-06


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


# ------------------------------------------------------------------ 1.8.0: catálogo y FileMetadata
def _archivo(out: Path, sufijo: str) -> str:
    return next(p for p in out.rglob(f"*{sufijo}")).read_text(encoding="utf-8")


def test_oxygen_catalogo_no_oculta_fallas_del_lookup(spec, tmp_path):
    """ADR-01/ADR-01b: el catálogo del processor ya no envuelve la consulta en Try(...).getOrElse(false).

    Esta prueba solo revisa el TEXTO generado (no compila Scala). El COMPORTAMIENTO (una consulta que lanza produce
    el código interno de nivel 1) lo cubren FieldValidatorsSpec ("CatalogoDeConsulta no oculta la falla del
    lookup") y, desde AUD-V5, las pruebas de la fuente, que usan CatalogoDeConsulta."""
    rt.render_all(spec, tmp_path)
    processor = _archivo(tmp_path, "ValidationProcessor.scala")
    clase = processor[processor.index("final class OxygenCatalogo"):processor.index("/** Processor de")]
    assert "Try(" not in clase and "getOrElse" not in clase
    assert "extends CatalogoDeConsulta(" in clase and "Util.getLookup(tabla, filtro, columna, lookup())" in clase
    comun = _archivo(tmp_path, "FieldSpec.scala")
    adaptador = comun[comun.index("class CatalogoDeConsulta"):]
    assert "Try" not in adaptador[:adaptador.index("\n}") ]
    pruebas = _archivo(tmp_path, "FieldValidatorsSpec.scala")
    assert "CatalogoDeConsulta no oculta la falla del lookup" in pruebas


def test_file_metadata_sin_bloque_no_cambia(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    fm = _archivo(tmp_path, "FileMetadata.scala")
    assert "archivo.metadata" not in fm and "Frequency" not in fm
    assert fm == _archivo(GEN_PATH, "FileMetadata.scala")


def test_file_metadata_con_miembros_declarados(spec, tmp_path):
    spec["archivo"]["metadata"] = {
        "min_frequency": "onceEveryMonth",
        "sub_subscriber": "SINTETICO",
        "additional_information": {"fileType": "SISBEN", "nota": 'con "comillas"'},
        "miembros_adicionales": [
            {"miembro": "fileLoadType", "tipo": "String", "valor": {"texto": "FULLLOAD"},
             "fuente": "implementación manual, rama de referencia (sintético)"},
            {"miembro": "cargaCompleta", "tipo": "FileLoadType", "valor": {"expresion": "FileLoadType.FULLLOAD"},
             "importar": ["com.ejemplo.sintetico.FileLoadType"], "fuente": "sintético"},
        ],
    }
    assert rt.verify_spec(spec) == []
    manifest = rt.render_all(spec, tmp_path)
    fm = _archivo(tmp_path, "FileMetadata.scala")
    assert "import com.experian.edf.oxygen.domain.internal.Frequency\n" in fm
    assert "import com.ejemplo.sintetico.FileLoadType\n" in fm
    assert "  override val minFrequency: Frequency = Frequency.onceEveryMonth\n" in fm
    assert '  override val subSubscriber: String = "SINTETICO"\n' in fm
    assert ('  override val additionalInformation: Map[String, String] = '
            'Map("fileType" -> "SISBEN", "nota" -> "con \\"comillas\\"")\n') in fm
    assert '  override val fileLoadType: String = "FULLLOAD"\n' in fm
    assert "  override val cargaCompleta: FileLoadType = FileLoadType.FULLLOAD\n" in fm
    assert "ASSUMPTION VAL-PT04: se sobrescriben sin poder confirmar" in fm
    assert fm.rstrip().endswith("}") and fm.count("{{") == 0
    assert "VAL-PT04" in manifest["supuestos"]


@pytest.mark.parametrize("cambio,mensaje", [
    ({"miembros_adicionales": [{"miembro": "subscriber", "tipo": "String", "valor": {"texto": "x"}, "fuente": "f"}]},
     "ya existe en FileMetadata"),
    ({"miembros_adicionales": [{"miembro": "fileLoadType", "tipo": "String", "valor": {"texto": "x"}, "fuente": "f"}] * 2},
     "repetido"),
    ({"miembros_adicionales": [{"miembro": "fileLoadType", "tipo": "String", "valor": {"texto": "x", "expresion": "y"},
                                "fuente": "f"}]}, "valor"),
    ({"miembros_adicionales": [{"miembro": "fileLoadType", "tipo": "String", "valor": {"expresion": "a\nb"},
                                "fuente": "f"}]}, "expresion"),
    ({"miembros_adicionales": [{"miembro": "fileLoadType", "tipo": "String", "valor": {"texto": "x"}}]}, "fuente"),
    ({"min_frequency": "once every month"}, "min_frequency"),
])
def test_file_metadata_invalida_se_rechaza(spec, cambio, mensaje):
    spec["archivo"]["metadata"] = cambio
    assert contiene(rt.verify_spec(spec), mensaje), rt.verify_spec(spec)


# ------------------------------------------------------------------ auditoría 2026-11-24 (AUD-V1..AUD-V8)
SILENCIO = re.compile(r"\bTry\s*[({].*\.(getOrElse\s*\(|toOption\b)|case\s+NonFatal\s*\(")
MARCA_SILENCIO = re.compile(r"SILENCIO-INTENCIONAL\s+[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*")


def spec_con_footer(spec: dict) -> dict:
    """Variante SINTÉTICA de SISBEN con una fila de footer (E8)."""
    alt = copy.deepcopy(spec)
    alt["archivo"]["filas_footer"] = 1
    alt["archivo"]["footer_regex"] = "^TOTAL;[0-9]+$"
    alt["codigos_especiales"]["footer"] = "SISBEN_ERR_028"
    alt["diagnostico_archivo"]["footer"] = 'errorlevelNotIn("1")'
    alt["fixture"]["footer"] = ["TOTAL;12"]
    return alt


def test_filtro_y_header_sin_try_en_el_processor(spec, tmp_path):
    """AUD-V1: debeFiltrar y esHeader son totales; el processor ya no los envuelve en Try(...).getOrElse(false)."""
    rt.render_all(spec, tmp_path)
    processor = _archivo(tmp_path, "ValidationProcessor.scala")
    assert "override def shouldFilterRow(row: String): Boolean = RecordValidator.debeFiltrar(spec, row)" in processor
    assert "override def isHeaderRow(row: String): Boolean = RecordValidator.esHeader(spec, row)" in processor
    assert "Try(RecordValidator" not in processor
    assert "debeFiltrar y esHeader son totales" in _archivo(tmp_path, "FieldValidatorsSpec.scala")
    assert "filtro y header con filas degeneradas no lanzan" in _archivo(tmp_path, "SISBENValidationProcessorSpec.scala")


def test_as_of_date_invalida_va_como_none(spec, tmp_path):
    """AUD-V2: sin asOfDate válida se pasa None; el motor usa receivedDate y marca didCreateAsOfDateFromReceivedDate."""
    rt.render_all(spec, tmp_path)
    processor = _archivo(tmp_path, "ValidationProcessor.scala")
    assert "val asOfDate: Option[Long] = RecordValidator.asOfDate(spec, linea)" in processor
    assert "DomainData(asOfDate, relacion)" in processor
    assert "getOrElse(0L)" not in processor and "fileMetadata.receivedDate" not in processor
    assert "didCreateAsOfDateFromReceivedDate = true" in processor


def test_formato_de_fecha_con_caracteres_reservados_de_java_falla(spec):
    """AUD-V2: DateTimeFormatter.ofPattern lanza con [ ] { } # '; se rechaza al generar para que parsear sea total."""
    for fmt in ("dd#MM#yyyy", "dd/MM/yyyy[ HH]", "dd'x'MM"):
        with pytest.raises(rt.SpecError, match="no soportado"):
            rt.java_to_py_date(fmt)


def test_sin_footer_no_se_emite_validacion_de_footer(spec, tmp_path):
    """AUD-V3: la salida de SISBEN (filas_footer = 0) no cambia."""
    rt.render_all(spec, tmp_path)
    assert "validateFooter" not in _archivo(tmp_path, "ValidationProcessor.scala")
    assert "footer =" not in (tmp_path / PROPUESTA / "validation-configs-sisben.conf").read_text(encoding="utf-8")


def test_footer_se_valida_cuando_hay_filas_footer(spec, tmp_path):
    """AUD-V3: con filas_footer > 0 se emiten validateFooter, validate[].footer, el código y la prueba."""
    alt = spec_con_footer(spec)
    assert rt.verify_spec(alt) == []
    rt.render_all(alt, tmp_path)
    processor = _archivo(tmp_path, "ValidationProcessor.scala")
    assert "override def validateFooter(validationContext: ValidationContext): ValidationResult =" in processor
    assert "Option(processorContext.footers).getOrElse(Array.empty[String])" in processor
    assert 'java.util.regex.Pattern.compile("^TOTAL;[0-9]+$")' in processor
    assert 'RecordValidator.validarFooter(footers.toSeq, 1, isFooterRow, "SISBEN_ERR_028")' in processor
    assert "override def isFooterRow(row: String): Boolean = Option(row).exists(r => patronFooter.matcher(r.trim).matches())" in processor
    assert "Try(" not in processor.split("override def isFooterRow", 1)[1].split("\n", 1)[0]
    conf = (tmp_path / PROPUESTA / "validation-configs-sisben.conf").read_text(encoding="utf-8")
    assert "footerRows = 1" in conf
    assert '      footer = "errorlevelNotIn(\\"1\\")"\n' in conf
    csv = (tmp_path / "docs" / "codigos-error-sisben.csv").read_text(encoding="utf-8")
    assert "SISBEN_ERR_028;1;FOOTER;Footer inválido" in csv
    pruebas = _archivo(tmp_path, "SISBENValidationProcessorSpec.scala")
    assert "footer: filas que cumplen footer_regex se aceptan" in pruebas
    assert 'Seq("TOTAL;12")' in pruebas
    fixture = _archivo(tmp_path, ".txt")
    assert fixture.endswith("\nTOTAL;12\n")
    assert "def validarFooter(" in _archivo(tmp_path, "FieldValidators.scala")


def test_footer_hocon_parsea(spec, tmp_path):
    pyhocon = pytest.importorskip("pyhocon")
    rt.render_all(spec_con_footer(spec), tmp_path)
    texto = (tmp_path / PROPUESTA / "validation-configs-sisben.conf").read_text(encoding="utf-8")
    block = pyhocon.ConfigFactory.parse_string("configs = [\n" + texto + "\n]")["configs"][0]
    assert block["validate"][0]["footer"] == 'errorlevelNotIn("1")'


@pytest.mark.parametrize("quitar,mensaje", [
    (("codigos_especiales", "footer"), "codigos_especiales.footer"),
    (("diagnostico_archivo", "footer"), "diagnostico_archivo.footer"),
    (("fixture", "footer"), "fixture.footer"),
])
def test_footer_exige_codigo_expresion_y_fixture(spec, quitar, mensaje):
    alt = spec_con_footer(spec)
    del alt[quitar[0]][quitar[1]]
    assert contiene(rt.verify_spec(alt), mensaje), rt.verify_spec(alt)


def test_fixture_de_footer_debe_cumplir_el_regex_y_el_numero_de_filas(spec):
    alt = spec_con_footer(spec)
    alt["fixture"]["footer"] = ["TOTAL;X", "TOTAL;1"]
    errores = rt.verify_spec(alt)
    assert contiene(errores, "fixture.footer tiene 2 filas"), errores
    assert contiene(errores, "no cumple archivo.footer_regex"), errores


def test_footer_regex_invalido_o_con_sintaxis_solo_de_python_falla(spec):
    """AUD-V4: el regex se compila en Java (Pattern); se rechaza lo que Python acepta y Java no."""
    alt = spec_con_footer(spec)
    alt["archivo"]["footer_regex"] = "("
    assert contiene(rt.check_file_contract(alt), "regex inválido")
    for python_solo in ("^(?P<t>TOTAL);[0-9]+$", "^(?P<t>T)(?P=t)$", "^TOTAL(?#comentario)$", "^(T)?(?(1)A|B)$"):
        alt["archivo"]["footer_regex"] = python_solo
        assert contiene(rt.check_file_contract(alt), "sintaxis que Java no acepta"), python_solo
    alt["archivo"]["footer_regex"] = "^TOTAL;[0-9]+$"
    alt["archivo"]["nombre"]["regex"] = "^(?P<p>RT_|EP_)?" + spec["archivo"]["nombre"]["regex"].lstrip("^")
    assert contiene(rt.check_file_contract(alt), "archivo.nombre.regex: sintaxis que Java no acepta")


def test_catalogo_de_pruebas_sobre_catalogo_de_consulta(spec, tmp_path):
    """AUD-V5: las pruebas de la fuente ejercitan CatalogoDeConsulta (List("") = sin coincidencia)."""
    rt.render_all(spec, tmp_path)
    pruebas = _archivo(tmp_path, "SISBENValidationProcessorSpec.scala")
    assert "new CatalogoDeConsulta(TablasSinteticas.consultar _)" in pruebas
    assert "extends Catalogo {" not in pruebas
    assert 'if (valores.isEmpty) List("") else valores' in pruebas
    assert "catálogo sintético sobre CatalogoDeConsulta" in pruebas


def test_silencios_intencionales_marcados():
    """Todo Try(...).getOrElse/toOption o NonFatal de las plantillas y del generador lleva SILENCIO-INTENCIONAL <ID>."""
    sin_marca = []
    for f in sorted(rt.TEMPLATES_DIR.rglob("*.tpl")) + [SCRIPTS / "render_templates.py"]:
        lineas = f.read_text(encoding="utf-8").splitlines()
        for i, linea in enumerate(lineas):
            if SILENCIO.search(linea):
                previa = lineas[i - 1] if i else ""
                if not MARCA_SILENCIO.search(linea + "\n" + previa):
                    sin_marca.append(f"{f.name}:{i + 1}")
    assert sin_marca == []


# ------------------------------------------------------------------ 1.9.2: corrida E2E SISBEN (AUD-V-nn)
RAIZ_REPO = SKILL.parent.parent.parent
INSUMO_E2E = RAIZ_REPO / "ejecuciones" / "sisben-e2e" / "expedientes" / "sisben" / "insumos" / "source-spec-validation.yaml"


def spec_con_reglas_nombre(spec: dict) -> dict:
    alt = copy.deepcopy(spec)
    alt["archivo"]["nombre"].update(periodo_no_futuro=True, recepcion_no_futura=True,
                                    recepcion_desde_inicio_periodo=True)
    return alt


def test_aud_v01_validate_y_metrics_llevan_file_pattern(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    conf = (tmp_path / PROPUESTA / "validation-configs-sisben.conf").read_text(encoding="utf-8")
    patron = f'filePattern = "{rt.hocon_str(spec["archivo"]["file_pattern"])}"'
    validate = conf[conf.index("validate = ["):conf.index("metrics = [")]
    metrics = conf[conf.index("metrics = ["):]
    assert conf.count(patron) == 3 and patron in validate and patron in metrics


def test_aud_v02_sin_claves_el_nombre_no_cambia(spec, tmp_path):
    ctx = rt.build_context(spec)
    assert ctx["bloque_pruebas_nombre"] == ""
    assert (ctx["nombre_periodo_no_futuro"], ctx["nombre_recepcion_no_futura"],
            ctx["nombre_recepcion_desde_inicio_periodo"]) == ("false", "false", "false")
    hoy = rt.date(2024, 6, 30)
    for nombre in ("SISBEN_EXPERIAN_209901.20240630120000-0500.txt", "SISBEN_EXPERIAN_202406.20240531120000-0500.txt",
                   "SISBEN_EXPERIAN_202406.20240703120000-0500.txt"):
        assert rt.nombre_valido_el_dia(spec, "/x/" + nombre, hoy), nombre


def test_aud_v02_reglas_de_fecha_del_nombre_se_generan_y_prueban(spec, tmp_path):
    alt = spec_con_reglas_nombre(spec)
    assert rt.verify_spec(alt) == []
    pruebas = rt.nombres_de_prueba(alt)
    # AUD-V-28 (1.9.5): RA-02 se aísla con un offset adelantado (antes 202506.20240630…, que violaba también RA-04).
    assert [(r, n) for r, _, n, _ in pruebas] == [
        ("periodo_no_futuro", "SISBEN_EXPERIAN_202407.20240701000000+1400.txt"),
        ("recepcion_no_futura", "SISBEN_EXPERIAN_202406.20240703120000-0500.txt"),
        ("recepcion_desde_inicio_periodo", "SISBEN_EXPERIAN_202406.20240531120000-0500.txt")]
    hoy = rt.date(2024, 6, 30)
    assert not any(rt.nombre_valido_el_dia(alt, n, d) for _, _, n, d in pruebas)
    assert rt.nombre_valido_el_dia(alt, alt["fixture"]["nombre_archivo"], hoy)
    rt.render_all(alt, tmp_path)
    fuente = _archivo(tmp_path, "SISBENSpec.scala")
    assert ("periodoNoFuturo = true,\n      recepcionNoFutura = true,\n      recepcionDesdeInicioPeriodo = true" in fuente)
    pruebas_scala = _archivo(tmp_path, "SISBENValidationProcessorSpec.scala")
    for regla, _, nombre, _ in pruebas:
        assert f'"/ruta/sintetica/{nombre}", ahora).map(_.codigo) == Seq(spec.codigos.nombreArchivo)' in pruebas_scala
        assert f"({regla})" in pruebas_scala
    processor = _archivo(tmp_path, "ValidationProcessor.scala")
    assert "RecordValidator.validarNombre(spec, nombreArchivo, Instant.now())" in processor


def test_aud_v02_fixture_que_viola_una_regla_falla(spec):
    alt = spec_con_reglas_nombre(spec)
    alt["fixture"]["fecha_referencia"] = "2024-06-29"  # la marca del fixture (30/06 12:00) queda en el futuro
    assert contiene(rt.verify_spec(alt), "fixture.nombre_archivo no cumple archivo.nombre.recepcion_no_futura")
    alt = spec_con_reglas_nombre(spec)
    alt["fuente"]["zona_horaria"] = "Zona/Inexistente"
    assert contiene(rt.verify_spec(alt), "inválidas para las reglas del nombre")


def test_aud_v16_offset_que_java_rechaza(spec):
    assert rt.java_zone_offset("-2500") is None and rt.java_zone_offset("+1801") is None
    assert rt.java_zone_offset("+1800") == rt.timedelta(hours=18)
    assert rt.java_zone_offset("-0500") == -rt.timedelta(hours=5)
    assert rt.java_zone_offset("-05:30") == -rt.timedelta(hours=5, minutes=30)
    assert rt.java_zone_offset("Z") == rt.timedelta(0)
    for malo in ("0500", "-0560", "+05:3", "", None, "+123"):
        assert rt.java_zone_offset(malo) is None, malo
    alt = copy.deepcopy(spec)
    alt["fixture"]["nombre_archivo"] = "SISBEN_EXPERIAN_202406.20240630120000-2500.txt"
    assert contiene(rt.verify_spec(alt), "offset inválidos (Java lo rechazaría)")


def test_aud_v03_regla_solo_ceros(spec, tmp_path):
    alt = copy.deepcopy(spec)
    alt["reglas_entre_campos"].append({"id": "RV-SIS-05", "tipo": "solo_ceros", "campo": "NUM_DOCUMENTO",
                                       "codigo": "SISBEN_ERR_029", "severidad": "1", "causa": "DOCUMENTO_CERO"})
    assert rt.verify_spec(alt) == []
    rt.render_all(alt, tmp_path)
    assert ('    SoloCeros(id = "RV-SIS-05", campo = NUM_DOCUMENTO, codigo = "SISBEN_ERR_029", '
            'severidad = Severidad.Error, causa = "DOCUMENTO_CERO")') in _archivo(tmp_path, "SISBENSpec.scala")
    assert "case r: ReglaEntreCampos.SoloCeros =>" in _archivo(tmp_path, "FieldValidators.scala")
    assert "SISBEN_ERR_029;1;NUM_DOCUMENTO;Regla RV-SIS-05" in (tmp_path / "docs" / "codigos-error-sisben.csv").read_text(
        encoding="utf-8")
    alt["reglas_entre_campos"][-1]["campo"] = "NO_EXISTE"
    assert contiene(rt.check_references(alt), "RV-SIS-05: campo inexistente NO_EXISTE")
    sin_causa = copy.deepcopy(alt)
    del sin_causa["reglas_entre_campos"][-1]["causa"]
    assert rt.verify_spec(sin_causa)


def test_aud_v05_validated_record_json_lleva_el_valor_recibido(spec, tmp_path):
    """Por inspección del texto: comoMapa (validatedRecordJson) usa Campos.crudo y no aplica trim."""
    rt.render_all(spec, tmp_path)
    evento = _archivo(tmp_path, "SISBENValidatedEvent.scala")
    mapa = evento[evento.index("def comoMapa"):evento.index("override def read")]
    assert ".trim" not in mapa and "Campos.valor" not in mapa
    assert 'm.put("NUM_DOCUMENTO", Campos.crudo(valores, 15))' in mapa
    assert mapa.count("Campos.crudo(valores, ") == len(spec["campos"])
    comun = _archivo(tmp_path, "FieldValidators.scala")
    crudo = comun[comun.index("def crudo("):comun.index("def valor(")]
    assert ".trim" not in crudo
    assert "def valor(valores: Array[String], posicion: Int): String = crudo(valores, posicion).trim" in comun


def test_aud_v06_lookups_con_lookup_home(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    conf = (tmp_path / PROPUESTA / "lookups-sisben.conf").read_text(encoding="utf-8")
    for cat in spec["catalogos"]:
        assert f'    file = ${{oxygen.application.paths.lookupHome}}"/{cat["archivo"]}"\n' in conf
    for malo in ("/abs/x.csv", "../x.csv", "a\\b.csv", 'a"b.csv', "${x}.csv", ""):
        alt = copy.deepcopy(spec)
        alt["catalogos"][0]["archivo"] = malo
        assert contiene(rt.check_references(alt), "ruta relativa a lookupHome"), malo
    alt = copy.deepcopy(spec)
    alt["catalogos"][0]["archivo"] = "sisben/SISBEN_HOME_ID_CLASIFICATION_NH.csv"
    assert rt.check_references(alt) == []


def test_aud_v08_supuestos_resueltos_por_la_spec(spec, tmp_path):
    alt = copy.deepcopy(spec)
    alt["supuestos_resueltos"] = [{"id": "F-11", "fuente": "spec §8"}, {"id": "VAL-SP01", "fuente": "spec §8"},
                                  {"id": "F-14", "fuente": "spec §6"}]
    assert rt.verify_spec(alt) == []
    manifest = rt.render_all(alt, tmp_path)
    assert not {"F-11", "VAL-SP01", "F-14"} & set(manifest["supuestos"])
    assert manifest["supuestos_resueltos"] == [{"id": "F-11", "fuente": "spec §8"}, {"id": "F-14", "fuente": "spec §6"},
                                               {"id": "VAL-SP01", "fuente": "spec §8"}]
    textos = salida(tmp_path)
    assert all("ASSUMPTION F-11" not in t and "ASSUMPTION F-14" not in t for t in textos.values())
    assert "// RESUELTO F-11 (spec §8): códigos del nombre de archivo" in _archivo(tmp_path, "SISBENSpec.scala")
    assert "RESUELTO F-14 (spec §6)" in _archivo(tmp_path, "SISBENValidationProcessorSpec.scala")
    en_codigo = sorted({m for t in textos.values() for m in MARCA.findall(t)})
    assert manifest["supuestos"] == en_codigo  # el manifest sigue derivándose de las marcas que quedan


def test_aud_v08_sin_clave_el_manifest_no_cambia(spec, tmp_path):
    manifest = rt.render_all(spec, tmp_path)
    assert "supuestos_resueltos" not in manifest and "F-11" in manifest["supuestos"]


def test_aud_v08_supuesto_resuelto_invalido(spec, tmp_path):
    alt = copy.deepcopy(spec)
    alt["supuestos_resueltos"] = [{"id": "F-08", "fuente": "spec"}, {"id": "F-08", "fuente": "otra"}]
    errores = rt.verify_spec(alt)
    assert contiene(errores, "F-08 repetido") and contiene(errores, "F-08 también figura como supuesto abierto")
    alt["supuestos_resueltos"] = [{"id": "F-99", "fuente": "spec"}]
    assert rt.verify_spec(alt) == []
    with pytest.raises(rt.SpecError, match=re.escape("supuestos_resueltos sin marca ASSUMPTION en la salida: ['F-99']")):
        rt.render_all(alt, tmp_path / "out")
    assert not (tmp_path / "out").exists()
    for fuente in ('con "comillas"', "fin */ de comentario", "a\\b"):
        alt["supuestos_resueltos"] = [{"id": "F-11", "fuente": fuente}]
        assert contiene(rt.verify_spec(alt), "supuestos_resueltos/0/fuente"), fuente


def test_aud_v09_grupos_de_duplicados_y_header_fuente(spec, tmp_path):
    alt = copy.deepcopy(spec)
    casos = alt["fixture"]["casos"]
    casos.append({"id": "DUP_CEROS_A", "descripcion": "duplicado sin ceros",
                  "cambios": {"NUM_DOCUMENTO": "1000000199", "FECHA_ACTUALIZACION_CNS": "01/01/2024"},
                  "esperado": {"codigos": [], "duplicado": True, "grupo_duplicado": "CEROS"}})
    casos.append({"id": "DUP_CEROS_B", "descripcion": "duplicado con ceros a la izquierda",
                  "cambios": {"NUM_DOCUMENTO": "0001000000199", "FECHA_ACTUALIZACION_CNS": "01/06/2024"},
                  "esperado": {"codigos": [], "duplicado": False, "grupo_duplicado": "CEROS"}})
    alt["fixture"]["nombres_invalidos"] = ["SISBEN_EXPERIAN_202406.txt", "XX_SISBEN_EXPERIAN_202406.20240630120000-0500.txt",
                                           "SISBEN_EXPERIAN_202413.20240630120000-0500.txt"]
    assert rt.verify_spec(alt) == []
    rt.render_all(alt, tmp_path)
    pruebas = _archivo(tmp_path, "SISBENValidationProcessorSpec.scala")
    assert 'test("fixture duplicados: misma clave' in pruebas  # el grupo sin clave conserva su título
    assert 'test("fixture duplicados (grupo CEROS): misma clave' in pruebas
    assert 'Seq("SISBEN_EXPERIAN_202406.txt", "XX_SISBEN_EXPERIAN_202406.20240630120000-0500.txt"' in pruebas
    assert 'test("header con los nombres fuente (alias) se acepta' in pruebas
    fuente_header = ";".join(next((a for a in c["alias"] if a != c["nombre"]), c["nombre"]) for c in spec["campos"])
    assert f'private val HeaderFuente: String = "{fuente_header}"' in pruebas
    alt["fixture"]["nombres_invalidos"].append(spec["fixture"]["nombre_archivo"])
    assert contiene(rt.verify_spec(alt), "es válido con el regex y las reglas del nombre")


def test_aud_v09_sin_grupos_la_prueba_de_duplicados_no_cambia(spec):
    assert rt.render_fixture_tests(spec).count('test("fixture duplicados: misma clave') == 1
    assert "(grupo " not in rt.render_fixture_tests(spec)


def test_aud_v10_nombre_de_la_skill(spec, tmp_path):
    manifest = rt.render_all(spec, tmp_path)
    assert manifest["skill"] == "edf-validation" == rt.SKILL_NAME
    assert not any("edf-validation-stage" in t for t in arbol(tmp_path).values())
    assert all("Generado por la skill edf-validation" in t
               for r, t in salida(tmp_path).items() if r.endswith((".scala", ".conf")))


def test_aud_v17_duplicado_conserva_file_tag(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    processor = _archivo(tmp_path, "ValidationProcessor.scala")
    assert "tve.headerErrors, tve.footerErrors, tve.keys, tve.inferredAsDeleted, tve.fileTag)" in processor


def test_aud_v19_codigos_de_error_fuera_de_la_carpeta_del_fork(spec, tmp_path):
    manifest = rt.render_all(spec, tmp_path)
    rutas = [a["ruta"] for a in manifest["archivos"]]
    assert "docs/codigos-error-sisben.csv" in rutas
    assert not any(r.startswith(PROPUESTA) and r.endswith(".csv") for r in rutas)
    assert sorted(p.name for p in (tmp_path / PROPUESTA).iterdir()) == ["lookups-sisben.conf",
                                                                        "validation-configs-sisben.conf"]
    csv = (tmp_path / "docs" / "codigos-error-sisben.csv").read_text(encoding="utf-8")
    assert csv.splitlines()[-1] == "OxygenDuplicate;;REGISTRO;Duplicado no conservado (marca OxygenDuplicate)"
    sin_dup = copy.deepcopy(spec)
    sin_dup["duplicados"]["habilitado"] = False
    assert "OxygenDuplicate" not in rt.render_error_codes(sin_dup)


@pytest.mark.skipif(not INSUMO_E2E.exists(), reason="sin el expediente de la corrida E2E SISBEN")
def test_insumo_e2e_sigue_verificando_y_acepta_las_claves_nuevas(tmp_path, capsys):
    """El insumo de la corrida (v1 sin claves nuevas o v2 con ellas) da OK; con las claves nuevas también genera (copia en memoria)."""
    assert rt.main(["--spec", str(INSUMO_E2E), "--solo-verificar"]) == 0
    assert capsys.readouterr().out.startswith("source-spec OK (22 campos, 29 códigos)")
    alt = rt.load_spec(INSUMO_E2E)
    alt["archivo"]["nombre"].update(periodo_no_futuro=True, recepcion_no_futura=True, recepcion_desde_inicio_periodo=True)
    rv05 = next(r for r in alt["reglas_entre_campos"] if r["id"] == "RV-SIS-05")
    # El insumo de la corrida puede estar en v1 (lista literal) o en v2 (ya solo_ceros): la prueba sirve para ambos
    # (corrida E2E SISBEN, HF-14: la suite no debe romperse porque el expediente avance).
    rv05.pop("valores", None)
    rv05["tipo"] = "solo_ceros"
    alt["supuestos_resueltos"] = [{"id": "F-11", "fuente": "spec-sisben.md §8"}]
    assert rt.verify_spec(alt) == []
    manifest = rt.render_all(alt, tmp_path)
    assert manifest["skill"] == "edf-validation" and "F-11" not in manifest["supuestos"]


def test_hf15_supuesto_resuelto_no_pide_confirmacion():
    """HF-15 (corrida E2E SISBEN): al marcar RESUELTO se quita el «; confirmar con ...» de la misma línea."""
    texto = "# ASSUMPTION F-12: archivo y columnas; confirmar con el dueño del catálogo.\n# ASSUMPTION F-04: x; confirmar en el ambiente."
    salida, usados = rt.resolver_supuestos(texto, {"F-12": "spec §9"})
    primera, segunda = salida.split("\n")
    assert primera == "# RESUELTO F-12 (spec §9): archivo y columnas."
    assert "confirmar en el ambiente" in segunda and "ASSUMPTION F-04" in segunda
    assert usados == {"F-12"}


# ------------------------------------------------------------------ 1.9.5: corrida E2E SISBEN (HF-16, -17, -18)
FV_TPL = SKILL / "assets" / "templates" / "capa-a" / "FieldValidators.scala.tpl"


def test_hf17_cada_nombre_de_prueba_viola_solo_su_regla(spec):
    """AUD-V-28: con las tres reglas activas, cada nombre derivado viola exactamente su regla con su reloj."""
    alt = spec_con_reglas_nombre(spec)
    for fecha in ("2024-06-30", "2024-06-05", "2024-12-31", "2026-02-05"):
        alt["fixture"]["fecha_referencia"] = fecha
        alt["fixture"]["nombre_archivo"] = "SISBEN_EXPERIAN_202406.20240605120000-0500.txt"
        assert rt.verify_spec(alt) == [], fecha
        pruebas = rt.nombres_de_prueba(alt)
        assert [r for r, *_ in pruebas] == list(rt.REGLAS_NOMBRE)
        for regla, _, nombre, hoy in pruebas:
            assert rt.viola_solo(alt, regla, "/x/" + nombre, hoy), (fecha, regla, nombre, hoy)
        ra02 = pruebas[0]
        assert ra02[2].endswith("000000+1400.txt") and ra02[3].month == rt.date.fromisoformat(fecha).month


def test_hf17_caso_de_la_auditoria_aisla_ra02(spec):
    """x7 de la auditoría: SISBEN_EXPERIAN_202612.20261201000000+1400.txt con reloj 2026-11-30 (America/Bogota)."""
    alt = spec_con_reglas_nombre(spec)
    nombre = "/x/SISBEN_EXPERIAN_202612.20261201000000+1400.txt"
    hoy = rt.date(2026, 11, 30)
    assert rt.viola_solo(alt, "periodo_no_futuro", nombre, hoy)
    assert not rt.nombre_valido_el_dia(alt, nombre, hoy)
    alt["archivo"]["nombre"]["periodo_no_futuro"] = False
    assert rt.nombre_valido_el_dia(alt, nombre, hoy)
    # El caso anterior del generador violaba también RA-04: no aislaba RA-02.
    assert not rt.viola_solo(spec_con_reglas_nombre(spec), "periodo_no_futuro",
                             "/x/SISBEN_EXPERIAN_202506.20240630120000-0500.txt", rt.date(2024, 6, 30))


def test_hf17_prueba_scala_generada_aisla_cada_regla(spec, tmp_path):
    alt = spec_con_reglas_nombre(spec)
    alt["fixture"]["fecha_referencia"] = "2024-06-29"
    alt["fixture"]["nombre_archivo"] = "SISBEN_EXPERIAN_202406.20240605120000-0500.txt"
    rt.render_all(alt, tmp_path)
    scala = _archivo(tmp_path, "SISBENValidationProcessorSpec.scala")
    assert "val reloj = java.time.LocalDate.of(2024, 6, 30).atTime(23, 59, 59)" in scala
    assert '"/ruta/sintetica/SISBEN_EXPERIAN_202407.20240701000000+1400.txt", reloj).map(_.codigo)' in scala
    for campo in ("periodoNoFuturo", "recepcionNoFutura", "recepcionDesdeInicioPeriodo"):
        assert f"spec.copy(nombreArchivo = spec.nombreArchivo.copy({campo} = false))" in scala, campo
    fv = (tmp_path / "src/test/scala/com/experian/edf/oxygen/validation/source/common/FieldValidatorsSpec.scala")
    texto = fv.read_text(encoding="utf-8")
    assert "cada regla de fecha del nombre se prueba aislada" in texto and '"X20240720240701000000+1400"' in texto


def test_hf17_regex_sin_offset_positivo_usa_el_caso_anterior(spec):
    alt = spec_con_reglas_nombre(spec)
    alt["archivo"]["nombre"]["regex"] = alt["archivo"]["nombre"]["regex"].replace("[+-]", "-")
    alt["archivo"]["file_pattern"] = alt["archivo"]["file_pattern"].replace("[+-]", "-")
    regla, _, nombre, hoy = rt.nombres_de_prueba(alt)[0]
    assert (regla, nombre, hoy) == ("periodo_no_futuro", "SISBEN_EXPERIAN_202506.20240630120000-0500.txt",
                                    rt.date(2024, 6, 30))


def test_hf16_clave_sin_documento_unica_por_registro_en_la_plantilla():
    tpl = FV_TPL.read_text(encoding="utf-8")
    assert 'Array[Any]("SIN_CLAVE", unico)' in tpl
    assert "java.util.UUID.randomUUID().toString" in tpl
    assert 'valores.mkString("\\u0001").hashCode' not in tpl
    proc = (SKILL / "assets" / "templates" / "SourceProcessorSpec.scala.tpl").read_text(encoding="utf-8")
    assert "dos filas idénticas sin un campo de la clave no se agrupan como duplicadas" in proc


def test_hf18_separar_quita_solo_el_cr_final_en_la_plantilla():
    tpl = FV_TPL.read_text(encoding="utf-8")
    assert 'if (original.endsWith("\\r")) original.dropRight(1) else original' in tpl


# ------------------------------------------------------------------ HF-13: supuestos en archivo.nombre
def test_hf13_supuestos_en_archivo_nombre_dejan_marca_y_entran_al_manifest(spec, tmp_path):
    alt = copy.deepcopy(spec)
    alt["archivo"]["nombre"]["supuestos"] = ["VAL-SP02"]
    assert rt.verify_spec(alt) == []
    manifest = rt.render_all(alt, tmp_path)
    texto = _archivo(tmp_path, "SISBENSpec.scala")
    bloque = texto[texto.index("nombreArchivo = NombreArchivoSpec("):texto.index("periodoNoFuturo")]
    assert "      // ASSUMPTION VAL-SP02: valor supuesto en el source-spec" in bloque
    assert "VAL-SP02" in manifest["supuestos"]
    alt["supuestos_resueltos"] = [{"id": "VAL-SP02", "fuente": "spec §8"}]
    assert contiene(rt.verify_spec(alt), "VAL-SP02 también figura como supuesto abierto")
    alt["archivo"]["nombre"]["supuestos"] = ["INT-012"]  # sin ID de decisiones-y-abiertos.md
    assert rt.verify_spec(alt) != []


def test_hf13_sin_supuestos_en_el_nombre_la_salida_no_cambia(spec, tmp_path):
    rt.render_all(spec, tmp_path)
    texto = _archivo(tmp_path, "SISBENSpec.scala")
    assert ("      // Reglas de fecha del nombre (archivo.nombre del source-spec; false = no se aplican, AUD-V-02).\n"
            "      periodoNoFuturo = ") in texto
