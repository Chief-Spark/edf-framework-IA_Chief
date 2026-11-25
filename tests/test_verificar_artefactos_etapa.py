"""Pruebas de tools/verificar-artefactos-etapa.py. Datos sintéticos y golden de edf-statecalc."""
import importlib.util
import json
import re
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


# ── HF-68 (AUD-S defecto 1): modo Colombia del bloque HOCON de Synthesis ──

TPL_BLOQUE = RAIZ / ".kiro/skills/edf-synthesis/assets/consistency-application-block.conf.template"


def bloque_colombia() -> str:
    """Cabecera + sección «Modo Colombia» de la plantilla, como la entregaría edf-synthesis."""
    texto = TPL_BLOQUE.read_text(encoding="utf-8")
    return texto[:texto.index("# ── Fuera de modo Colombia")].replace("{{INGESTA_NOMBRE}}", "sintetica")


def test_hf68_bloque_colombia_de_la_plantilla_pasa_en_modo_colombia():
    bloque = bloque_colombia()
    assert all(not l.strip() or l.lstrip().startswith("#") for l in bloque.splitlines())
    assert va.verificar_hocon_synthesis(bloque, "colombia") == []
    # El mismo bloque en modo propio da los 2 hallazgos de la corrida (raíz y customRulesFile), con la pista del modo.
    propio = va.verificar_hocon_synthesis(bloque)
    assert len(propio) == 2 and "--modo colombia" in propio[0] and "customRulesFile" in propio[1]


@pytest.mark.parametrize("clave", va.CLAVES_VERIFICAR_COLOMBIA)
def test_hf68_modo_colombia_exige_cada_clave_verificar(clave):
    lineas = [l for l in bloque_colombia().splitlines()
              if not ("VERIFICAR" in l and re.search(re.escape(clave) + r"(?![A-Za-z0-9_])", l))]
    hall = va.verificar_hocon_synthesis("\n".join(lineas), "colombia")
    assert hall == [f"falta `VERIFICAR {clave}` en la lista de verificación del modo Colombia"], hall


def test_hf68_modo_colombia_rechaza_claves_activas_y_huecos():
    activo = bloque_colombia() + 'oxygen.stage.consistencyCheck {\n  eventTypeList = ["sintetica"]\n}\n'
    hall = va.verificar_hocon_synthesis(activo, "colombia")
    assert len(hall) == 3 and all("activa en modo Colombia" in h for h in hall)
    assert any("quedan {{huecos}}" in h for h in va.verificar_hocon_synthesis(bloque_colombia() + "# {{X}}\n", "colombia"))
    # Una lista que solo nombra la clave corta (sin la ruta HOCON completa) no cuenta.
    corto = bloque_colombia().replace("oxygen.stage.consistencyCheck.dateType", "dateType")
    assert va.verificar_hocon_synthesis(corto, "colombia") == [
        "falta `VERIFICAR oxygen.stage.consistencyCheck.dateType` en la lista de verificación del modo Colombia"]


def test_hf68_modo_propio_sigue_exigiendo_raiz_y_custom_rules(tmp_path):
    assert va.verificar_hocon_synthesis(HOCON_OK, "propio") == []
    sin = "\n".join(l for l in HOCON_OK.splitlines() if "customRulesFile" not in l)
    assert any("falta paths.customRulesFile" in h for h in va.verificar_hocon_synthesis(sin, "propio"))
    # Un bloque propio no pasa como Colombia: tiene líneas activas.
    assert any("activa en modo Colombia" in h for h in va.verificar_hocon_synthesis(HOCON_OK, "colombia"))


def test_hf68_cli_modo(tmp_path, capsys):
    b = tmp_path / "bloque.conf"
    b.write_text(bloque_colombia(), encoding="utf-8")
    assert va.main(["synthesis", "--modo", "colombia", "--hocon", str(b)]) == 0
    assert "ARTEFACTOS OK" in capsys.readouterr().out
    assert va.main(["synthesis", "--hocon", str(b)]) == 1
    assert va.main(["synthesis", "--modo", "propio", "--hocon", str(b)]) == 1
    with pytest.raises(SystemExit):
        va.main(["synthesis", "--modo", "otro", "--hocon", str(b)])
    assert "--modo colombia" in va.__doc__ and "--modo propio" in va.__doc__


# ── HF-73 (AUD-SC defecto 1): select contra name o alias; DERIVED cuyo name no es clave curada ──

GOLDEN_SC = RAIZ / ".kiro/skills/edf-statecalc/evals/fixtures/golden/sisben-camino-feliz"
F_ALIAS = {"fields": [{"name": "fechaDigitacion", "dataType": "LONG", "aliases": ["fechaEncuesta"]},
                      {"name": "grupoSisben"}]}


def mrv(**kw):
    base = {"name": "sisben", "type": "MOST_RECENT_VALUE", "select": ["grupoSisben", "fechaEncuesta"]}
    base.update(kw)
    return {"domain": "d", "calculations": [base]}


def test_hf73_select_por_alias_pasa_con_aviso():
    hall, avisos = va.verificar_statecalc(mrv(), F_ALIAS)
    assert hall == [], hall          # antes: «`fechaEncuesta` no existe en fields[] de Foundation» (error por exceso)
    assert len(avisos) == 1 and "alias de `fechaDigitacion`" in avisos[0] and "aliasEnabled" in avisos[0]


def test_hf73_nombre_con_punto_usa_el_nombre_final():
    f = {"fields": [{"name": "domicilio.codMunicipio"}]}
    assert va.verificar_calculation(mrv(select=["codMunicipio"]), f) == []
    assert va.verificar_calculation(mrv(select=["domicilio.codMunicipio"]), f) == [
        "sección `sisben`: `domicilio.codMunicipio` no existe en fields[] de Foundation"]


def test_hf73_derived_con_nombre_de_alias_falla_con_source_spec():
    """La «salida natural» del falso positivo: un DERIVED `fechaEncuesta` vale null siempre."""
    f = {"fields": [{"name": "fechaEncuesta", "dataType": "LONG"}, {"name": "grupoSisben"},
                    {"name": "x", "value": "grupoSisben"}]}
    publicadas = {"grupoSisben", "fechaDigitacion", "x"}
    hall, avisos = va.verificar_statecalc(mrv(), f, publicadas=publicadas)
    assert hall == [next(h for h in hall if "`fechaEncuesta`" in h)] and "que Curation no publica" in hall[0]
    assert any("trae `value`" in a for a in avisos)
    assert va.verificar_statecalc(mrv(), f)[0] == []        # sin --source-spec no se cruza


def test_hf73_cli_source_spec(tmp_path, capsys):
    import yaml
    (tmp_path / "f.json").write_text(json.dumps({"fields": [{"name": "fechaEncuesta"}, {"name": "grupoSisben"}]}), encoding="utf-8")
    (tmp_path / "c.json").write_text(json.dumps(mrv()), encoding="utf-8")
    (tmp_path / "ss.yaml").write_text(yaml.safe_dump({"campos_curados": [{"destino": "grupoSisben"}],
                                                      "parametros": {"campo_advertencias": "advertenciasCuracion"}}),
                                      encoding="utf-8")
    args = ["statecalc", "--calculation", str(tmp_path / "c.json"), "--foundation", str(tmp_path / "f.json")]
    assert va.main(args) == 0
    assert va.main(args + ["--source-spec", str(tmp_path / "ss.yaml")]) == 1
    assert "`fechaEncuesta` lee la clave curada" in capsys.readouterr().out
    assert va.main(args + ["--source-spec", str(tmp_path / "no.yaml")]) == 2


# ── HF-74/HF-75: field.X de un CALCULATED es un campo Foundation ──

def test_hf75_golden_pasa_y_una_copia_sin_el_derived_falla():
    f = json.loads((GOLDEN_SC / "statecalc-foundation-local.json").read_text(encoding="utf-8"))
    c = json.loads((GOLDEN_SC / "statecalc-calculation-local.json").read_text(encoding="utf-8"))
    publicadas = {"standardizedIdType", "standardizedIdNumber"}
    assert va.verificar_statecalc(c, f, publicadas=publicadas) == ([], [])
    sin = {"fields": [x for x in f["fields"] if x.get("type") == "CALCULATED"]}
    hall = va.verificar_calculation(c, sin, publicadas=publicadas)
    assert len(hall) == 2 and all("no es un campo del registro Foundation" in h for h in hall), hall
    assert "field.standardizedIdType" in hall[0] and "field.standardizedIdNumber" in hall[1]


def test_hf74_calculated_con_campos_inyectados_y_orden():
    f = {"fields": [{"name": "a", "type": "CALCULATED", "value": "return field.pin + field['dataSource']"},
                    {"name": "b", "type": "CALCULATED", "value": "return field.a + field.c"},
                    {"name": "c", "type": "CALCULATED", "value": "return 1"}]}
    hall = va.verificar_calculation({"calculations": []}, f)
    assert hall == [next(h for h in hall if "field.c" in h)] and "se calcula después" in hall[0]
    hall = va.verificar_calculation({"calculations": []}, {"fields": [{"name": "#x", "type": "CALCULATED", "value": "return 1"}]})
    assert any("foundation_calculated_<name>" in h for h in hall)


# ── HF-77: name de sección con where y modo Colombia ──

F_COL = {"fields": [{"name": "grupoSisben"}, {"name": "nivelSisben", "dataType": "INTEGER"}]}


def col(**kw):
    base = {"name": "sisben", "type": "MOST_RECENT_VALUE", "select": ["grupoSisben", "nivelSisben"],
            "where": "return field.dataSource == 'DNP' && field.subscriber == '999722'"}
    base.update(kw)
    return {"domain": "d", "calculations": [base]}


def test_hf77_seccion_con_where_y_nombre_invalido_falla_en_los_dos_modos():
    for modo in ("propio", "colombia"):
        hall = va.verificar_calculation(col(name="#sisben"), F_COL, modo=modo)
        assert any("aggregated_where_<name>" in h and "`#sisben`" in h for h in hall), (modo, hall)
    # sin where, el name no forma una función: no se exige identificador (modo propio)
    assert va.verificar_calculation(col(name="#sisben", where=None), F_COL) == []


def test_hf77_modo_colombia_exige_filtro_por_fuente():
    assert va.verificar_statecalc(col(), F_COL, modo="colombia", campos_existentes=set(),
                                  secciones_existentes=set()) == ([], [])
    sin_where = va.verificar_calculation(col(where=None), F_COL, modo="colombia")
    assert len(sin_where) == 1 and "sin `where` en modo Colombia" in sin_where[0] and "MostRecentValue.scala:20-24" in sin_where[0]
    otro = va.verificar_calculation(col(where="return field.grupoSisben != null"), F_COL, modo="colombia")
    assert len(otro) == 1 and "no filtra por fuente" in otro[0]
    bandera = col(where="return field.isSisben == true")
    assert va.verificar_calculation(bandera, F_COL, modo="colombia")          # sin --campo-fuente: hallazgo
    assert va.verificar_calculation(bandera, F_COL, modo="colombia", campos_fuente=("isSisben",)) == []
    assert va.verificar_calculation(col(where=None), F_COL) == []           # modo propio: no se exige
    assert va.verificar_calculation(col(where=None, enabled=False), F_COL, modo="colombia") == []


def test_hf77_modo_colombia_nombres_que_chocan(tmp_path):
    compartido = {"domain": "colombia", "fields": [{"name": "nivelSisben", "dataType": "STRING"},
                                                   {"name": "otra.grupoSisben"}]}
    (tmp_path / "fc.json").write_text(json.dumps(compartido), encoding="utf-8")
    (tmp_path / "secc.txt").write_text("# secciones de Colombia\nsisben\nregistraduria\n", encoding="utf-8")
    existentes = va.nombres_de(tmp_path / "fc.json", "fields")
    assert existentes == {"nivelSisben", "grupoSisben"}
    hall, _ = va.verificar_statecalc(col(), F_COL, modo="colombia", campos_existentes=existentes,
                                     secciones_existentes=va.nombres_de(tmp_path / "secc.txt", "calculations"))
    assert sum("ya existe en el foundation compartido" in h for h in hall) == 2
    assert sum("ya existe en el calculation compartido" in h for h in hall) == 1
    _, avisos = va.verificar_statecalc(col(), F_COL, modo="colombia")
    assert any("--campos-existentes" in a for a in avisos) and any("--secciones-existentes" in a for a in avisos)


def test_hf77_cli_modo_colombia(tmp_path, capsys):
    (tmp_path / "f.json").write_text(json.dumps(F_COL), encoding="utf-8")
    (tmp_path / "c.json").write_text(json.dumps(col(where=None)), encoding="utf-8")
    (tmp_path / "fc.txt").write_text("nivelSisben\n", encoding="utf-8")
    base = ["statecalc", "--calculation", str(tmp_path / "c.json"), "--foundation", str(tmp_path / "f.json")]
    assert va.main(base) == 0
    capsys.readouterr()
    assert va.main(base + ["--modo", "colombia", "--campos-existentes", str(tmp_path / "fc.txt")]) == 1
    out = capsys.readouterr().out
    assert "sin `where` en modo Colombia" in out and "`nivelSisben` ya existe" in out and "! AVISO" in out
    assert va.main(base + ["--modo", "colombia", "--campos-existentes", str(tmp_path / "no.txt")]) == 2
    assert "--modo colombia" in va.__doc__ and "--campos-existentes" in va.__doc__ and "--source-spec" in va.__doc__
