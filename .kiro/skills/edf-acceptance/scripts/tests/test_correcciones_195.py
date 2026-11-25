"""Regresión 1.9.5: defectos del framework que encontró la corrida E2E SISBEN en la etapa Acceptance
(ejecuciones/sisben-e2e/auditorias/acceptance.md §2-§3): HF-20 (firma), HF-21 (clave genérica), HF-22 (severidades
deshabilitadas), HF-23 / AUD-A-01 (umbral alternativo) y HF-24 / AUD-A-08, -09 (simulador y comentario del Spec).
HF-19 (textos) está en tests/test_regresion_e2e_acceptance.py, en la raíz del framework.

Ejecutar desde la raíz del skill:  python -m pytest scripts/tests -q
"""
from __future__ import annotations

import copy
import json
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
SPEC_SCALA = "src/test/scala/com/experian/edf/oxygen/acceptance/source/sisben/SISBENAcceptanceExpressionsSpec.scala"

# HU SISBEN §5.2 «Umbral parametrizable»: con umbral 0.95, 19/20 se acepta y 47/50 = 0.94 se rechaza.
UMBRAL_095 = [{"umbral": "0.95", "casos": [
    {"id": "FQS_095", "registros_validos": 19, "registros_totales": 20, "esperado": "aceptado"},
    {"id": "FQS_094", "registros_validos": 47, "registros_totales": 50, "esperado": "rechazado",
     "reason_code_esperado": "SISBFA00001"},
]}]


@pytest.fixture()
def spec() -> dict:
    return rt.load_yaml(SPEC_PATH)


@pytest.fixture(scope="module")
def schema() -> dict:
    return rt.load_yaml(SCHEMA_PATH)


def con_umbrales(spec: dict, umbrales=None) -> dict:
    alt = copy.deepcopy(spec)
    alt["fixture"]["umbrales_alternativos"] = copy.deepcopy(UMBRAL_095 if umbrales is None else umbrales)
    return alt


def _escribir(tmp_path: Path, spec: dict, nombre: str = "spec.yaml") -> Path:
    path = tmp_path / nombre
    path.write_text(yaml.safe_dump(spec, allow_unicode=True, sort_keys=False), encoding="utf-8")
    return path


# ------------------------------------------------------------------ HF-20: firma edf-acceptance

def test_hf20_plantillas_manifiesto_schema_y_script_firman_edf_acceptance():
    rutas = [*(SKILL / "assets" / "templates").glob("*"), SCHEMA_PATH, SCRIPTS / "render_templates.py",
             SPEC_PATH, *[p for p in GEN_PATH.rglob("*") if p.is_file()]]
    for ruta in rutas:
        assert "edf-acceptance-stage" not in ruta.read_text(encoding="utf-8"), ruta
    gen = GEN_PATH / "hocon" / "sisben-acc-tpl-01-acceptance-block.conf"
    assert "generado por edf-acceptance 0.1.0" in gen.read_text(encoding="utf-8").splitlines()[0]
    assert "// Generado por edf-acceptance 0.1.0." in (GEN_PATH / SPEC_SCALA).read_text(encoding="utf-8")


# ------------------------------------------------------------------ HF-21: expresiones_propuestas

def test_hf21_clave_generica_y_alias_deprecado(spec, schema):
    assert "expresiones_propuestas" in spec and "expresiones_propuestas_sisben" not in spec
    assert rt.validate_schema(spec, schema) == []
    assert rt.avisos(spec) == []
    viejo = copy.deepcopy(spec)
    viejo["expresiones_propuestas_sisben"] = viejo.pop("expresiones_propuestas")
    assert rt.validate_schema(viejo, schema) == []
    assert rt.avisos(viejo) and "deprecada" in rt.avisos(viejo)[0]
    ambas = copy.deepcopy(spec)
    ambas["expresiones_propuestas_sisben"] = ambas["expresiones_propuestas"]
    assert rt.validate_schema(ambas, schema)
    ninguna = copy.deepcopy(spec)
    del ninguna["expresiones_propuestas"]
    assert rt.validate_schema(ninguna, schema)


def test_hf21_cli_con_la_clave_vieja_genera_y_avisa(spec, tmp_path, capsys):
    viejo = copy.deepcopy(spec)
    viejo["expresiones_propuestas_sisben"] = viejo.pop("expresiones_propuestas")
    assert rt.main(["--spec", str(_escribir(tmp_path, viejo)), "--out", str(tmp_path / "out")]) == 0
    assert "aviso  expresiones_propuestas_sisben está deprecada" in capsys.readouterr().out


# ------------------------------------------------------------------ HF-22: severidades deshabilitadas

def test_hf22_severidades_deshabilitadas_solo_exigen_habilitado_y_estado(spec, schema, tmp_path):
    minima = copy.deepcopy(spec)
    minima["severidades"] = {"habilitado": False, "estado": "abierto"}
    assert rt.validate_schema(minima, schema) == []
    assert rt.verify_spec(minima) == []
    report = rt.generate(minima, tmp_path)
    assert [o["id"] for o in report["omitidos"]] == ["ACC-TPL-03", "ACC-TPL-03"]
    assert rt.main(["--spec", str(_escribir(tmp_path, minima)), "--out", str(tmp_path / "o"), "--audit"]) == 0


def test_hf22_severidades_habilitadas_siguen_exigiendo_todo(spec, schema):
    incompleta = copy.deepcopy(spec)
    incompleta["severidades"] = {"habilitado": True, "estado": "cerrado"}
    errores = rt.validate_schema(incompleta, schema)
    for campo in ("tableName", "archivo", "fileFormat", "delimiter", "mapFromColumn", "mapToColumn", "filas"):
        assert any(f"'{campo}' is a required property" in e for e in errores), campo


# ------------------------------------------------------------------ HF-23: umbral alternativo (AUD-A-01)

def test_hf23_sin_la_clave_nada_cambia(spec, tmp_path):
    assert "umbrales_alternativos" not in spec["fixture"]
    rt.generate(spec, tmp_path)
    scala = (tmp_path / SPEC_SCALA).read_text(encoding="utf-8")
    assert "umbral alternativo" not in scala and "conUmbral" not in scala
    assert scala == (GEN_PATH / SPEC_SCALA).read_text(encoding="utf-8")
    assert sim.simular_umbrales_alternativos(spec) == []
    assert all("directorio" in r for r in sim.simular_y_comparar(spec))


def test_hf23_con_la_clave_genera_la_prueba_sin_otra_configuracion(spec, schema, tmp_path):
    alt = con_umbrales(spec)
    assert rt.validate_schema(alt, schema) == [] and rt.verify_spec(alt) == []
    rt.generate(alt, tmp_path / "alt")
    rt.generate(spec, tmp_path / "base")
    scala = (tmp_path / "alt" / SPEC_SCALA).read_text(encoding="utf-8")
    assert 'private val UmbralDesplegado = "0.90"' in scala
    assert '("0.95", "FQS_095", 19, 20, "aceptado", ""),' in scala
    assert '("0.95", "FQS_094", 47, 50, "rechazado", "SISBFA00001"),' in scala
    assert 'test("umbral alternativo: la misma configuración con otro umbral acepta y rechaza lo esperado (HF-23)")' in scala
    # La configuración desplegable (HOCON) y el fixture no cambian: solo cambia el Spec.
    for rel in ("hocon/sisben-acc-tpl-01-acceptance-block.conf", "hocon/sisben-acc-tpl-02-file-conditions.conf"):
        assert (tmp_path / "alt" / rel).read_text(encoding="utf-8") == (tmp_path / "base" / rel).read_text(encoding="utf-8")
    rows = rt.audit(alt, schema)
    assert all(r["en_schema"] for r in rows)
    assert any(r["hueco"] == "fixture.umbrales_alternativos?" for r in rows)


def test_hf23_simulador_evalua_los_casos_con_el_umbral_alternativo(spec):
    resultados = sim.simular_umbrales_alternativos(con_umbrales(spec))
    assert [(r["caso"], r["fqs"], r["decision"], r["reason_codes"], r["coincide"]) for r in resultados] == [
        ("FQS_095", 0.95, "aceptado", [], True),
        ("FQS_094", 0.94, "rechazado", ["SISBFA00001"], True)]
    # Con el umbral desplegado (0.90) los dos casos se aceptarían: la prueba sí depende del umbral sustituido.
    mismo = con_umbrales(spec, [{"umbral": "0.90", "casos": UMBRAL_095[0]["casos"]}])
    assert [r["decision"] for r in sim.simular_umbrales_alternativos(mismo)] == ["aceptado", "aceptado"]


def test_hf23_simulador_informa_el_caso_que_no_coincide(spec, tmp_path, capsys):
    malo = con_umbrales(spec)
    malo["fixture"]["umbrales_alternativos"][0]["casos"][0]["registros_validos"] = 18  # 0.90 < 0.95
    path = _escribir(tmp_path, malo)
    assert sim.main(["--spec", str(path)]) == 1
    err = capsys.readouterr().err
    assert "NO COINCIDE  umbral 0.95 caso FQS_095: decisión rechazado (esperada aceptado)" in err
    assert "1/2 casos de umbral alternativo coinciden" in err


@pytest.mark.parametrize("cambio,esperado", [
    ({"umbral": "0.90"}, "debe ser otro"),
    ({"umbral": "1.50"}, "fuera de [0, 1]"),
])
def test_hf23_verificaciones_del_umbral(spec, cambio, esperado):
    alt = con_umbrales(spec)
    alt["fixture"]["umbrales_alternativos"][0].update(cambio)
    assert any(esperado in e for e in rt.verify_spec(alt)), rt.verify_spec(alt)


def test_hf23_verificaciones_de_los_casos(spec, schema):
    alt = con_umbrales(spec)
    casos = alt["fixture"]["umbrales_alternativos"][0]["casos"]
    casos[0]["registros_validos"] = 21
    casos[1]["id"] = casos[0]["id"]
    errores = rt.verify_spec(alt)
    assert any("registros_validos > registros_totales" in e for e in errores)
    assert any("caso FQS_095 repetido" in e for e in errores)
    alt = con_umbrales(spec, UMBRAL_095 * 2)
    assert any("umbral 0.95 repetido" in e for e in rt.verify_spec(alt))
    sin_rc = con_umbrales(spec)
    del sin_rc["fixture"]["umbrales_alternativos"][0]["casos"][1]["reason_code_esperado"]
    assert any("reason_code_esperado" in e for e in rt.validate_schema(sin_rc, schema))
    otro = con_umbrales(spec)
    otro["fixture"]["umbrales_alternativos"][0]["casos"][0]["esperado"] = "pendiente"
    assert rt.validate_schema(otro, schema)


def test_hf23_umbral_que_no_se_puede_sustituir(spec):
    alt = con_umbrales(spec)
    assert rt.con_umbral('average("qs.RQS") >= 0.90', "0.90", "0.95") == 'average("qs.RQS") >= 0.95'
    assert rt.con_umbral('x >= 10.90', "0.90", "0.95") == 'x >= 10.90'  # solo el número completo
    assert sim.con_umbral('average("qs.RQS") < 0.90', "0.90", "0.95") == 'average("qs.RQS") < 0.95'
    alt["calidad"]["umbral"] = "0.9"  # ya no es el literal de las expresiones: la escala única también lo detecta
    assert any("no se pudo sustituir" in e for e in rt.umbrales_alternativos_coherentes(alt))


# ------------------------------------------------------------------ HF-24: simulador comparado y comentario

def test_hf24_simulador_compara_con_lo_esperado(spec, capsys):
    resultados = sim.simular_y_comparar(spec)
    assert all(r["coincide"] and r["diferencias"] == [] for r in resultados)
    assert sim.main(["--spec", str(SPEC_PATH)]) == 0
    captura = capsys.readouterr()
    assert all(r["coincide"] for r in json.loads(captura.out))
    assert "5/5 archivos coinciden con lo esperado" in captura.err


@pytest.mark.parametrize("ruta,valor,texto", [
    ("fixture.archivos.0.decision_esperada", "rechazado", "decisión aceptado (esperada rechazado)"),
    ("fixture.archivos.1.reason_code_esperado", "OTRO", "reasonCode 'SISBFA00001' (esperado 'OTRO')"),
    ("fixture.archivos.0.eventos.0.esperado_registro", False, "registro SINT-A-01: True (esperado False)"),
])
def test_hf24_simulador_sale_con_uno_si_algo_no_coincide(spec, tmp_path, capsys, ruta, valor, texto):
    malo = copy.deepcopy(spec)
    *padres, ultimo = ruta.split(".")
    nodo = malo
    for parte in padres:
        nodo = nodo[int(parte)] if isinstance(nodo, list) else nodo[parte]
    nodo[ultimo] = valor
    assert sim.main(["--spec", str(_escribir(tmp_path, malo))]) == 1
    captura = capsys.readouterr()
    assert texto in captura.err and "4/5 archivos coinciden" in captura.err
    assert [r["coincide"] for r in json.loads(captura.out)].count(False) == 1


def test_hf24_rechazo_automatico_y_reason_code(spec):
    archivo = next(a for a in spec["fixture"]["archivos"] if a["decision_esperada"] == "rechazo_automatico")
    resultado = sim.decide_file(spec, archivo)
    con_rc = {**archivo, "reason_code_esperado": "SISBFA00001"}
    assert sim.diferencias_archivo(spec, con_rc, resultado) == []  # customReason = true: el motor puede registrarlo
    sin_custom = copy.deepcopy(spec)
    sin_custom["rechazo_automatico"]["customReasonForAutomaticRejectionEnabled"] = False
    assert sim.diferencias_archivo(sin_custom, con_rc, resultado)


def test_hf24_comentario_del_spec_sobre_reason_code_matizado():
    tpl = (SKILL / "assets" / "templates" / "AcceptanceExpressionsSpec.scala.tpl").read_text(encoding="utf-8")
    assert "el motor además registra el reasonCode (E4)" not in tpl
    assert "registra el reasonCode solo si alguna condición de reject es verdadera sobre ellos" in tpl


def test_hf28_comentario_e4_no_engana():
    """HF-28 (corrida E2E SISBEN, AUD-A-14): el comentario sobre customReason describe true y false."""
    tpl = (Path(__file__).resolve().parents[2] / "assets/templates/acceptance-block.conf.tpl").read_text(encoding="utf-8")
    bloque = tpl.split("customReasonForAutomaticRejectionEnabled")[0].rsplit("recordAcceptance", 1)[1]
    assert "true:" in bloque and "false (default del motor)" in bloque
    assert "registra códigos de razón también en el rechazo automático por marcadores." not in tpl
