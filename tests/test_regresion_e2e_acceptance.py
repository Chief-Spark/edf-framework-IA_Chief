"""Regresión documental de los defectos del framework hallados en la etapa Acceptance de la corrida E2E SISBEN (1.9.5).

Las correcciones de código (HF-20 a HF-24) tienen sus pruebas en
`.kiro/skills/edf-acceptance/scripts/tests/test_correcciones_195.py`. Aquí se comprueba que siguen escritas las de
texto: HF-19 / AUD-A-10 (la columna «SISBEN» del SKILL y `references/especificacion-sisben.md` describían otra
versión de SISBEN) y la documentación de las claves nuevas.
"""
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SK = RAIZ / ".kiro" / "skills" / "edf-acceptance"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


def test_hf19_especificacion_sisben_es_historica():
    texto = leer(SK / "references/especificacion-sisben.md")
    assert "EJEMPLO HISTÓRICO" in texto.splitlines()[0]
    cabecera = texto[:texto.index("## Identidad")]
    assert "no se cita como fuente" in cabecera
    assert "Precedencia: decisiones" not in texto  # ya no fija precedencias sobre la HU y la spec
    assert "En este ejemplo histórico, SISBEN usaría `inferDelete`" in texto


def test_hf19_skill_no_cita_la_reference_como_fuente_y_la_tabla_es_solo_forma():
    skill = leer(SK / "SKILL.md")
    assert "prellenado" not in skill
    fila = next(l for l in skill.splitlines() if l.startswith("| `references/especificacion-sisben.md`"))
    assert "EJEMPLO HISTÓRICO" in fila and "se cita como fuente" in fila
    paso1 = skill[skill.index("### Paso 1"):skill.index("### Paso 2")]
    assert "ejemplo histórico" in paso1 and "no se cita como fuente" in paso1
    tabla = skill[skill.index("| Etapa | Qué se configura |"):skill.index("El generador falla")]
    assert "| Etapa | Qué se configura | Ejemplo de forma (no es fuente) |" in tabla
    assert "(RT_" not in tabla and "| SISBEN |" not in tabla
    previo = skill[skill.index("Las expresiones siguen el orden"):skill.index("| Etapa | Qué se configura |")]
    assert "solo la **forma**" in previo and "salen\nde la HU y la spec de la ingesta" in previo


def test_hf23_hf24_claves_nuevas_documentadas_en_el_skill():
    skill = leer(SK / "SKILL.md")
    assert "umbrales_alternativos:" in skill and "reason_code_esperado" in skill
    assert "sale con código 1 si alguno no\ncoincide (HF-24)" in skill
