"""Regresión documental de los defectos del framework hallados en la auditoría de Curation de la corrida E2E SISBEN (1.9.7).

Las correcciones de código (HF-34 a HF-40) tienen sus pruebas en
`.kiro/skills/edf-curation/scripts/tests/test_correcciones_197.py` y HF-37 en `tests/test_verificar_claves.py`.
Aquí se comprueba el texto: HF-40 / AUD-C-07 (referencias del SISBEN histórico rotuladas y no citadas como fuente),
HF-41 / AUD-C-13 («Vaciado del campo con preservación» sin soporte en `edf-curation`) y las claves nuevas. Al
final, si la corrida E2E está en el repo, el escenario de HU §5.3 cur-0004 con su insumo de Curation.
"""
import importlib.util
import json
import sys
from pathlib import Path

import pytest

RAIZ = Path(__file__).resolve().parent.parent
SK = RAIZ / ".kiro" / "skills" / "edf-curation"
INSUMO = RAIZ / "ejecuciones/sisben-e2e/expedientes/sisben/insumos/source-spec-curation.yaml"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


def test_hf40_especificacion_sisben_es_historica_y_no_fuente():
    texto = leer(SK / "references/especificacion-sisben.md")
    cabecera = texto[:texto.index("## Identidad")]
    assert "**Histórico (HF-40, AUD-C-07).**" in cabecera and "**No es fuente**" in cabecera
    skill = leer(SK / "SKILL.md")
    fila = next(l for l in skill.splitlines() if l.startswith("| `references/especificacion-sisben.md`"))
    assert "**histórico**" in fila and "no fuente" in fila
    assert "PEP sin letras, PPT\noriginal" not in skill and "1→10, 2→7" not in skill
    assert "son referencia de forma, no fuente (HF-40, AUD-C-07)" in skill
    dsl = leer(SK / "references/dsl-config-reference.md")
    assert "SISBEN **histórico** del ejemplo de la skill (HF-40" in dsl and "Ejemplo histórico (delimitador" in dsl
    bloque = leer(SK / "assets/templates/curation-block.conf.tpl")
    assert "(\\.txt)?" not in bloque


def test_hf41_vaciado_con_preservacion_sin_soporte():
    skill = leer(SK / "SKILL.md")
    assert "**Sin soporte (HF-41, AUD-C-13):** el efecto «Vaciado del campo con preservación»" in skill
    assert "**no lo implementa esta skill**" in skill
    alcance = leer(SK / "references/alcance-y-glosario.md")
    assert "Efecto de frontera **sin soporte** (HF-41, AUD-C-13): «Vaciado del campo con preservación»" in alcance
    fila = next(l for l in leer(RAIZ / ".kiro/steering/07-contratos-frontera.md").splitlines()
                if l.startswith("| **Vaciado del campo con preservación**"))
    assert "**Sin soporte en `edf-curation`** (HF-41)" in fila


def test_claves_nuevas_documentadas_en_el_skill():
    skill = leer(SK / "SKILL.md")
    assert 'supuestos_resueltos: [{id: CUR-PT05, fuente: "spec-sisben.md §10.1"}]' in skill
    assert '`"7": null` con `pendientes: ["7"]`, HF-38' in skill
    assert "`original` (HF-34) publica el valor recibido **tal cual**" in skill
    esquema = leer(SK / "assets/schema/source-spec.schema.yaml")
    assert "supuestos_resueltos:" in esquema and "HF-38 (AUD-C-09)" in esquema


@pytest.mark.skipif(not INSUMO.is_file(), reason="la corrida E2E SISBEN no está en el repo")
def test_hu_5_3_cur_0004_numero_documento_original_con_espacio():
    sys.path.insert(0, str(SK / "scripts"))
    spec_rt = importlib.util.spec_from_file_location("render_templates_e2e", SK / "scripts/render_templates.py")
    rt = importlib.util.module_from_spec(spec_rt)
    spec_rt.loader.exec_module(rt)
    ctx = rt.preparar(INSUMO, SK / "assets/templates", SK / "assets/schema/source-spec.schema.yaml")
    cur = next(c for c in ctx["curados"] if c["eventId"] == "cur-0004")["curatedRecordJson"]
    assert cur["numeroDocumentoOriginal"] == " 0001000000101"
    assert cur["standardizedIdNumber"] == "1000000101"
    prueba = next(t for _, d, t in ctx["contenidos"] if d.endswith("CurationProcessorSpec.scala"))
    linea = next(l for l in prueba.split("\n") if "val esperado" in l and '"numeroDocumentoOriginal":" 0001000000101"' in l)
    assert json.loads(linea.split('"""')[1])["numeroDocumentoOriginal"] == " 0001000000101"
