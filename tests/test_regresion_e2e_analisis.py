"""Regresión documental de HF-32 (corrida E2E SISBEN, lote de cierre): la cabecera de los insumos de `edf-analisis`
(Modo 2) no lleva estado ni aprobador. El estado vive solo en `estado-ingesta.json`; un «Estado: PROPUESTO» en la
cabecera queda desactualizado al aprobar y corregirlo cambia el hash registrado.
"""
import re
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SK = RAIZ / ".kiro" / "skills" / "edf-analisis"
PLANTILLAS = SK / "assets" / "templates"
EJEMPLOS = SK / "assets" / "examples" / "sisben"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


def test_hf32_skill_modo_2_dice_que_la_cabecera_no_lleva_estado():
    skill = leer(SK / "SKILL.md")
    modo2 = skill[skill.index("## Modo 2"):skill.index("## Referencias")]
    regla = " ".join(modo2[modo2.index("**Cabecera del insumo: sin estado (HF-32).**"):modo2.index("Flujo:")].split())
    assert "**no lleva estado** (ni `PROPUESTO` ni `APROBADO`) **ni aprobador**" in regla
    assert "viven solo en `estado-ingesta.json`" in regla and "hash" in regla
    for permitido in ("versión y fecha", "fuentes citadas", "desviaciones"):
        assert permitido in regla, permitido
    for insumo in ("`source-spec-*.yaml`", "`yaml-skill-*.yaml`", "`contrato-curation-pinning.md`"):
        assert insumo in regla, insumo
    assert "estado PROPUESTO" not in skill


def test_hf32_plantillas_de_insumos_sin_estado_ni_aprobador():
    cabecera = leer(PLANTILLAS / "_cabecera-yaml-skill.yaml.tpl")
    documento = cabecera[cabecera.index("documento:"):cabecera.index("procedencia:")]
    assert not re.search(r"^\s*estado:", documento, re.M) and not re.search(r"^\s*aprobador:", documento, re.M)
    assert "HF-32" in documento and "estado-ingesta.json" in documento
    for tpl in ("yaml-skill-pinning.yaml.tpl", "yaml-skill-synthesis.yaml.tpl", "yaml-skill-statecalc.yaml.tpl",
                "contrato-curation-pinning.md.tpl"):
        texto = leer(PLANTILLAS / tpl)
        assert "PROPUESTO" not in texto, tpl
        assert not re.search(r"^\| Estado \|", texto, re.M), tpl


def test_hf32_ejemplos_rotulan_el_estado_de_cabecera_como_historico():
    leeme = leer(EJEMPLOS / "LEEME.md")
    fila = next(l for l in leeme.splitlines() if "HF-32" in l)
    assert "no lleva estado ni aprobador" in fila and "estado-ingesta.json" in fila
    for ejemplo in ("yaml-skill-pinning.yaml", "yaml-skill-synthesis.yaml", "yaml-skill-statecalc.yaml"):
        texto = leer(EJEMPLOS / ejemplo)
        documento = texto[texto.index("documento:"):texto.index("procedencia:")]
        linea = documento.splitlines()[documento.splitlines().index('  estado: "PROPUESTO"') - 1]
        assert linea.strip().startswith("# HISTÓRICO (HF-32): un insumo nuevo NO lleva estado ni aprobador"), ejemplo
