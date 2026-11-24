"""Regresión de los defectos del framework hallados en la etapa 0 de la corrida E2E SISBEN (1.9.1).

AUD-E0-10 (bloqueo por etapa), AUD-E0-11 (ejemplo SISBEN solo de forma), AUD-E0-13 (formato de contrato del
mapeo), AUD-E0-14 (efectos de la violación) y AUD-E0-15 (casilla de insumos). Las pruebas leen los archivos
del framework y comprueban que la corrección sigue escrita. La regla 5 del validador se prueba en
`test_validar_expediente.py`.
"""
import re
from pathlib import Path

import pytest

RAIZ = Path(__file__).resolve().parent.parent
SK = RAIZ / ".kiro" / "skills"
ST = RAIZ / ".kiro" / "steering"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


# --- AUD-E0-10: el bloqueo se mide por etapa_afectada ---

def test_deteccion_ambiguedades_mide_el_bloqueo_por_etapa_afectada():
    t = leer(SK / "edf-analisis/references/deteccion-ambiguedades.md")
    assert "ningún insumo de etapa" not in t  # la letra que bloqueaba la etapa 0 por cualquier insumo
    assert "El bloqueo se mide por `etapa_afectada`" in t
    assert "`etapa_afectada` ∈ {`analisis`, `validation`}" in t


@pytest.mark.parametrize("rel", ["edf-orquestador/references/04-contratos-por-skill.md",
                                 "edf-orquestador/references/01-flujo-etapas.md"])
def test_gate_de_la_etapa_0_distingue_etapa(rel):
    t = leer(SK / rel)
    assert "`etapa_afectada` ∈ {`analisis`, `validation`}" in t


def test_gate_de_cierre_etapa_0_no_es_global():
    t = leer(SK / "edf-orquestador/references/04-contratos-por-skill.md")
    gate = next(l for l in t.splitlines() if l.startswith("**Gate de cierre:**") and "validation" in l)
    assert "etapa_afectada" in gate


def test_condicion_4_por_etapa_afectada():
    t = leer(SK / "edf-orquestador/references/03-gobierno-riesgo-aprobacion.md")
    assert re.search(r"4\. No quedan preguntas abiertas con `bloqueante: true` cuya `etapa_afectada` sea N o N\+1", t)


# --- AUD-E0-11: el ejemplo SISBEN es solo referencia de forma ---

ORDEN_EJEMPLO = re.compile(r"(?i)(partir del? |parte del? |parte de )(el )?(`assets/examples|ejemplo)")


@pytest.mark.parametrize("rel", ["edf-validation/SKILL.md", "edf-analisis/SKILL.md",
                                 "edf-acceptance/SKILL.md", "edf-curation/SKILL.md"])
def test_skill_no_ordena_partir_del_ejemplo_sin_salvedad(rel):
    t = leer(SK / rel)
    assert "pre-llenado" not in t and "(prellenado)" not in t
    for linea in t.splitlines():
        if ORDEN_EJEMPLO.search(linea):
            assert re.search(r"(?i)forma", linea), f"{rel}: {linea.strip()}"


@pytest.mark.parametrize("rel", ["edf-validation/SKILL.md", "edf-analisis/SKILL.md"])
def test_skill_declara_el_ejemplo_solo_de_forma(rel):
    t = leer(SK / rel)
    assert re.search(r"(?i)solo referencia de \**forma", t)
    assert "00-framework-edf.md` §6" in t
    assert re.search(r"(?i)nunca\** (se )?copi", t)


@pytest.mark.parametrize("rel", ["edf-validation/assets/examples/sisben/source-spec.sisben.yaml",
                                 "edf-acceptance/assets/examples/sisben/source-spec.sisben.yaml",
                                 "edf-curation/assets/examples/sisben/source-spec.sisben.yaml",
                                 "edf-analisis/assets/examples/sisben/yaml-skill-pinning.yaml",
                                 "edf-analisis/assets/examples/sisben/yaml-skill-synthesis.yaml",
                                 "edf-analisis/assets/examples/sisben/yaml-skill-statecalc.yaml"])
def test_ejemplo_lleva_marcador_de_forma(rel):
    assert leer(SK / rel).startswith("# EJEMPLO DE FORMA, NO ES FUENTE")


# --- AUD-E0-13: tabla de mapeo en formato de contrato ---

def test_convenciones_exime_la_tabla_de_mapeo():
    t = leer(ST / "05-convenciones.md")
    assert "Excepción explícita: tablas en formato de contrato" in t
    assert "mapeo-campos.md.tpl" in t


def test_plantilla_de_mapeo_explica_el_marcador_por_dimension():
    t = leer(SK / "edf-analisis/assets/templates/mapeo-campos.md.tpl")
    assert "05-convenciones.md` §8" in t
    assert "Marcador por dimensión" in t
    assert "[A:PA-" in t and "[PM]" in t


# --- AUD-E0-14: efectos de la violación ---

EFECTOS = ["Rechazo del registro", "Inconsistencia registrada", "Vaciado del campo con preservación",
           "Nulo con advertencia", "Descarte silencioso", "Marca de duplicado no aceptado"]


def _tabla_efectos():
    t = leer(ST / "07-contratos-frontera.md")
    sec = t.split("### 5.1", 1)[1].split("### 5.2", 1)[0]
    return [l for l in sec.splitlines() if l.startswith("| **")]


def test_tabla_de_efectos_tiene_los_seis_con_etapa():
    filas = _tabla_efectos()
    nombres = [re.match(r"\| \*\*(.+?)\*\*", f).group(1) for f in filas]
    assert nombres == EFECTOS
    t = leer(ST / "07-contratos-frontera.md")
    assert "| Efecto | Descripción | Etapa que lo produce | Suma al puntaje de calidad |" in t
    for f in filas:
        assert len(f.strip("|").split("|")) == 4, f


def test_plantilla_de_mapeo_usa_el_vocabulario_de_efectos():
    t = leer(SK / "edf-analisis/assets/templates/mapeo-campos.md.tpl").lower()
    for e in EFECTOS:
        assert e.lower() in t, e


# --- AUD-E0-15: casilla de insumos en el checklist ---

def test_checklist_de_contexto_empieza_por_insumos():
    t = leer(SK / "edf-analisis/assets/templates/contexto-ingesta.md.tpl")
    checklist = t.split("## Checklist de cierre", 1)[1]
    casillas = [l for l in checklist.splitlines() if l.startswith("- [ ]")]
    assert casillas[0].startswith("- [ ] Insumos obligatorios presentes y leídos")
    pasos_04 = [l for l in leer(ST / "04-reglas-analisis.md").split("### 4.3", 1)[1].split("### 4.4", 1)[0]
                .splitlines() if l.startswith("- [ ]")]
    assert len(casillas) == len(pasos_04)
