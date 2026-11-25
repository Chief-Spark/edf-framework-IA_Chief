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


# --- Lote de cierre (HF-30, HF-31). HF-26, HF-29 y HF-33 están en scripts/tests/test_correcciones_197.py ---

def test_hf30_restos_del_sisben_historico_rotulados():
    inventario = leer(SK / "references/inventario-plantillas.md")
    entradas = inventario[inventario.index("## Entradas del skill"):inventario.index("## Plantillas")]
    assert "| Id | Entrada | Clave en la spec | Ejemplo histórico (no es fuente) | Estado en el ejemplo |" in entradas
    assert "| SISBEN |" not in entradas and "HF-30" in entradas and "salen de la HU y la spec" in entradas

    dsl = leer(SK / "references/dsl-config-reference.md")
    seccion = dsl[dsl.index("## 4."):]
    assert seccion.startswith("## 4. Ejemplo histórico: expresiones SISBEN (D7; no es fuente)")
    previo = seccion[:seccion.index("```hocon")]
    assert "HF-30" in previo and "salen de la HU y la spec de la ingesta" in previo and "`false`" in previo

    decisiones = leer(SK / "references/decisiones-y-abiertos.md")
    for fid in ("F-07", "F-08", "F-09"):
        fila = next(l for l in decisiones.splitlines() if l.startswith(f"| {fid} |"))
        assert "spec de la ingesta" in fila and "Ejemplo histórico (no es fuente, HF-30)" in fila, fid
        actual = fila[:fila.index("Ejemplo histórico")]
        assert "RT_" not in actual and "SISBEN_ERR" not in actual and "Provisional" not in actual, fid

    patrones = leer(SK / "references/patrones.md")
    assert "| Campo | Oblig. | Etapas | Ejemplo histórico (no es fuente) |" in patrones


def test_hf31_checklist_acepta_documentos_sinteticos_declarados_por_la_spec():
    checklist = leer(SK / "assets/checklist-entrega.md")
    assert "documentos `SINT*`" not in checklist
    plano = " ".join(checklist.split())
    assert ("`eventId` `SINT-*`; documentos sintéticos, declarados como tales por la spec de la ingesta o con prefijo "
            "`SINT`, nunca acompañados de nombres reales (HF-31)") in plano
