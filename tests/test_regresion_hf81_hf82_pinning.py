"""Regresión de HF-81 (AUD-P-26) y HF-82 (AUD-P-27) de edf-pinning, re-auditoría de Pinning de la corrida E2E SISBEN.

HF-81: el bloque de modo Colombia copia del insumo, de forma determinista, el `estado` (con sus `[PM]` y RG-nnn) de
identifyingAttributesFile, identityMatcher y reusePin, el disparador de escalamiento de reusePin y las referencias de
la ingesta; `--verificar --insumo` exige esos marcadores (edf-orquestador references/03 §3e, regla 2).
HF-82: la fuente del matcher es la que declara el insumo, no «steering 11 §6 #7».

Se prueba con un insumo sintético derivado del fixture de evals (no con el de la corrida).
"""
import hashlib
import importlib.util
import re
from pathlib import Path

import pytest
import yaml

RAIZ = Path(__file__).resolve().parent.parent
PIN = RAIZ / ".kiro" / "skills" / "edf-pinning"
TPL_BLOQUE = PIN / "assets" / "pinning-application-block.conf.template"
TPL_INSUMO = RAIZ / ".kiro" / "skills" / "edf-analisis" / "assets" / "templates" / "yaml-skill-pinning.yaml.tpl"
FIXTURE_COL = PIN / "evals" / "fixtures" / "yaml-skill-pinning-colombia-sintetico.yaml"
R_IDENT = "oxygen.stage.pinning.paths.identifyingAttributesFile"
R_MATCHER = "oxygen.stage.pinning.identityMatcher"
R_REUSE = "oxygen.stage.pinning.reusePin"


@pytest.fixture(scope="module")
def rb():
    spec = importlib.util.spec_from_file_location("render_bloque_colombia_hf81", PIN / "scripts" / "render_bloque_colombia.py")
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


def entrada(insumo: dict, corta: str) -> dict:
    return next(e for e in insumo["claves_configuracion_pinning"]["bloque_oxygen_stage_pinning"]
                if e["clave"].rsplit(".", 1)[-1] == corta)


def insumo_sintetico() -> dict:
    """Fixture de evals con verificación diferida sintética: [PM] (RG-901), escalamiento RG-902, pendiente RG-903."""
    insumo = yaml.safe_load(FIXTURE_COL.read_text(encoding="utf-8"))
    entrada(insumo, "identifyingAttributesFile")["estado"] = "[P] no se redefine · contenido de la lista [PM] (RG-901)"
    matcher = entrada(insumo, "identityMatcher")
    matcher["estado"] = "[C] decisión (spec sintética §11) · [PM] FQCN en el fork (RG-901)"
    matcher["fuente"] = "spec sintética §11; HU sintética R-7"
    entrada(insumo, "reusePin")["estado"] = "decisión del PO, PA-SINT-1"
    pre = insumo["alcance"]["precondiciones_bloqueantes"][0]
    pre["estado"] = "RESUELTA por el PO, opción (a)"
    pre["hallazgo_N4_escalado"] = ("Escribir reusePin = false renumera los PIN de todas las fuentes: N4, se escala. "
                                   "Escalamiento si en el fork el valor vigente es false (RG-902)")
    insumo["mapeo_identidad"]["pendientes_del_mapeo"] = [
        {"id": "RG-901", "descripcion": "matcher fuera de la copia", "efecto": "verificación diferida",
         "estado": "[PM] (RG-901)"}]
    insumo["claves_configuracion_pinning"]["pendientes_de_configuracion"] = [
        {"clave": "oxygen.stage.pinning.reusePin (valor vigente)",
         "estado": "[PM] verificar en el fork (RG-901); si es false, escalar (N4, RG-902)"},
        {"clave": "oxygen.stage.pinning.paths.domainDictionaryFile", "estado": "[PM] verificar que existe (RG-903)"}]
    return insumo


@pytest.fixture()
def ruta_sintetica(tmp_path) -> Path:
    ruta = tmp_path / "yaml-skill-pinning.yaml"
    ruta.write_text(yaml.safe_dump(insumo_sintetico(), allow_unicode=True, sort_keys=False), encoding="utf-8")
    return ruta


def plano(rb, texto: str) -> str:
    return rb._normal(texto)


def mutar_parrafo(rb, bloque: str, ruta: str) -> str:
    parrafo = rb.parrafo_verificar(bloque, ruta)
    return bloque.replace(parrafo, re.sub(r"\[PM\] ?|\(?RG-\d+\)?", "", parrafo))


# ── HF-81: el bloque conserva los marcadores del insumo ──

def test_hf81_bloque_copia_estado_marcadores_y_escalamiento(rb, ruta_sintetica):
    bloque = rb.generar(ruta_sintetica)
    assert bloque == rb.generar(ruta_sintetica)  # sigue siendo reproducible byte a byte
    insumo = yaml.safe_load(ruta_sintetica.read_text(encoding="utf-8"))
    assert rb.verificar_bloque(bloque, insumo=insumo,
                               sha256=hashlib.sha256(ruta_sintetica.read_bytes()).hexdigest()) == []
    ident = plano(rb, rb.parrafo_verificar(bloque, R_IDENT))
    assert "Estado en el insumo: [P] no se redefine · contenido de la lista [PM] (RG-901)" in ident
    matcher = plano(rb, rb.parrafo_verificar(bloque, R_MATCHER))
    assert "Estado en el insumo: [C] decisión (spec sintética §11) · [PM] FQCN en el fork (RG-901)" in matcher
    reuse = plano(rb, rb.parrafo_verificar(bloque, R_REUSE))
    assert "Estado en el insumo: decisión del PO, PA-SINT-1" in reuse
    assert "si es false, escalar (N4, RG-902)" in reuse
    assert "Escalamiento N4 en el insumo (PA-SINT-1):" in reuse and "valor vigente es false (RG-902)" in reuse
    refs = plano(rb, rb.seccion_referencias(bloque))
    assert "PA-SINT-1 (precondición): RESUELTA por el PO, opción (a)" in refs
    assert "RG-901 (pendiente del mapeo): [PM] (RG-901)" in refs
    assert "(oxygen.stage.pinning.paths.domainDictionaryFile): [PM] verificar que existe (RG-903)" in refs
    assert "reusePin (valor vigente)" not in refs  # el pendiente de reusePin va a su VERIFICAR
    # sin líneas activas y sin huecos, como antes
    assert not [l for l in bloque.splitlines() if l.strip() and not l.lstrip().startswith("#")]
    assert "{{" not in bloque


@pytest.mark.parametrize("ruta", [R_IDENT, R_MATCHER, R_REUSE])
def test_hf81_verificar_con_insumo_marca_la_mutacion_que_borra_marcadores(rb, ruta_sintetica, tmp_path, capsys, ruta):
    bloque = rb.generar(ruta_sintetica)
    mutado = mutar_parrafo(rb, bloque, ruta)
    assert mutado != bloque
    insumo = yaml.safe_load(ruta_sintetica.read_text(encoding="utf-8"))
    hallazgos = rb.verificar_bloque(mutado, insumo=insumo)
    assert any(f"`VERIFICAR {ruta}` no lleva" in h and "[PM]" in h and "RG-901" in h for h in hallazgos), hallazgos
    assert rb.verificar_bloque(mutado) == []  # sin insumo no se sabe qué marcadores tocaban
    archivo = tmp_path / "mutado.conf"
    archivo.write_text(mutado, encoding="utf-8")
    assert rb.main(["--verificar", str(archivo), "--insumo", str(ruta_sintetica)]) == 1
    assert "BLOQUE con" in capsys.readouterr().out


def test_hf81_verificar_marca_referencias_y_escalamiento_borrados(rb, ruta_sintetica):
    bloque = rb.generar(ruta_sintetica)
    insumo = yaml.safe_load(ruta_sintetica.read_text(encoding="utf-8"))
    refs = rb.seccion_referencias(bloque)
    sin_refs = bloque.replace(refs, re.sub(r"\[PM\] ?|\(?RG-90[13]\)?", "", refs))
    hallazgos = rb.verificar_bloque(sin_refs, insumo=insumo)
    assert any("Referencias de la ingesta" in h and "RG-901 (pendiente del mapeo)" in h for h in hallazgos)
    assert any("domainDictionaryFile" in h and "RG-903" in h for h in hallazgos)
    reuse = rb.parrafo_verificar(bloque, R_REUSE)
    sin_esc = bloque.replace(reuse, reuse[:reuse.index("Escalamiento N4 en el insumo")].rstrip(" #\n"))
    assert any("escalamiento N4 de PA-SINT-1" in h for h in rb.verificar_bloque(sin_esc, insumo=insumo))


def test_hf81_sin_estado_del_insumo_falla_aun_sin_insumo_y_cabecera_de_otro_insumo(rb, ruta_sintetica):
    bloque = rb.generar(ruta_sintetica)
    viejo = re.sub(r"^#\s+Estado en el insumo:.*\n(#\s{15}.*\n)*", "", bloque, flags=re.M)
    hallazgos = rb.verificar_bloque(viejo)
    assert sum("sin «Estado en el insumo:»" in h for h in hallazgos) == 3
    insumo = yaml.safe_load(ruta_sintetica.read_text(encoding="utf-8"))
    otro = rb.verificar_bloque(bloque, insumo=insumo, sha256="0" * 64)
    assert any("sha256 del insumo" in h for h in otro)


def test_hf81_insumo_sin_entrada_identity_matcher_se_rechaza(rb, tmp_path):
    insumo = insumo_sintetico()
    bloque = insumo["claves_configuracion_pinning"]["bloque_oxygen_stage_pinning"]
    bloque[:] = [e for e in bloque if not e["clave"].endswith("identityMatcher")]
    ruta = tmp_path / "yaml-skill-pinning.yaml"
    ruta.write_text(yaml.safe_dump(insumo, allow_unicode=True, sort_keys=False), encoding="utf-8")
    with pytest.raises(ValueError, match="identityMatcher"):
        rb.generar(ruta)


def test_hf81_plantilla_del_insumo_trae_identity_matcher_y_explica_lo_que_se_copia():
    texto = TPL_INSUMO.read_text(encoding="utf-8")
    insumo = yaml.safe_load(texto.replace("{{ingesta}}", "x"))
    claves = [e["clave"] for e in insumo["claves_configuracion_pinning"]["bloque_oxygen_stage_pinning"]]
    assert R_MATCHER in claves
    assert "HF-81" in texto and "hallazgo_N4_escalado" in texto and "[PM] (RG-nnn)" in texto


# ── HF-82: la fuente del matcher es la del insumo ──

def test_hf82_plantilla_no_cita_steering_11_como_fuente_del_matcher(rb):
    texto = TPL_BLOQUE.read_text(encoding="utf-8")
    colombia = texto[texto.index("── Modo Colombia"):texto.index("── Fuera de modo Colombia")]
    assert "steering 11 §6 #7" not in colombia
    matcher = colombia[colombia.index(f"VERIFICAR {R_MATCHER}"):colombia.index(f"VERIFICAR {R_REUSE}")]
    assert "{{ESTADO_IDENTITY_MATCHER}}" in matcher and "HF-82" in matcher


def test_hf82_bloque_lleva_la_fuente_del_insumo_y_verificar_marca_la_cita_de_steering(rb, ruta_sintetica):
    bloque = rb.generar(ruta_sintetica)
    matcher = rb.parrafo_verificar(bloque, R_MATCHER)
    assert "Fuente en el insumo: spec sintética §11; HU sintética R-7" in plano(rb, matcher)
    insumo = yaml.safe_load(ruta_sintetica.read_text(encoding="utf-8"))
    con_steering = bloque.replace(matcher, matcher.replace("(modo_colombia.identity_matcher;",
                                                           "(steering 11 §6 #7;"))
    assert any("HF-82" in h for h in rb.verificar_bloque(con_steering, insumo=insumo))
