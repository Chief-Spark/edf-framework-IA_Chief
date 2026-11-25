"""Regresión documental de los defectos del framework hallados en la etapa Validation de la corrida E2E SISBEN (1.9.2).

Las correcciones de código (AUD-V-01, -02, -03, -05, -06, -08, -09, -10, -16, -17, -19) tienen sus pruebas en
`.kiro/skills/edf-validation/scripts/tests/`. Aquí se comprueba que siguen escritas las correcciones de texto:
AUD-V-11 (discrepancias fuera de `.kiro/`), AUD-V-12 (checklist y ADR-04), AUD-V-13 (referencias históricas),
AUD-V-15 (steering 07 y léxico) y AUD-V-21 (nombre del inner).
"""
import re
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SK = RAIZ / ".kiro" / "skills" / "edf-validation"
ST = RAIZ / ".kiro" / "steering"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


# --- AUD-V-11: las discrepancias de una ingesta van al expediente, nunca a .kiro/ ---

def test_regla_dura_7_y_checklist_no_mandan_escribir_en_la_skill():
    skill = leer(SK / "SKILL.md")
    regla7 = skill[skill.index("7. **Discrepancias:**"):skill.index("## Regla de ejecución")]
    assert "entrega al Orquestador" in regla7 and "nunca" in regla7 and "`.kiro/`" in regla7
    assert not re.search(r"se registran en `references/decisiones-y-abiertos.md`", skill)
    checklist = leer(SK / "assets/checklist-entrega.md")
    linea = next(l for l in checklist.splitlines() if "Discrepancias documentales" in l)
    assert "entrega al Orquestador" in linea and "registradas en `references/decisiones" not in linea
    assert "no se escriben aquí" in leer(SK / "references/decisiones-y-abiertos.md")


# --- AUD-V-12: el generador vigente es Python; ADR-04 superado ---

def test_checklist_declara_vigente_el_generador_python():
    checklist = leer(SK / "assets/checklist-entrega.md")
    assert "## Nota vigente · generador Python" in checklist
    assert checklist.index("## Nota vigente") < checklist.index("generador en Java 17 (2026-10-20) · HISTÓRICA")
    assert "## Nota de verificación · correcciones de la corrida priorizada (2026-10-21) · HISTÓRICA" in checklist


def test_adr04_marcado_como_superado():
    fila = next(l for l in leer(SK / "references/decisiones-y-abiertos.md").splitlines() if l.startswith("| ADR-04 |"))
    assert "Superado el 2026-11-18" in fila and "DEPRECADOS.md" in fila


# --- AUD-V-13: referencias desactualizadas rotuladas; el Paso 1 no las cita como fuente ---

def test_especificacion_sisben_es_historica_y_el_paso_1_no_la_cita_como_fuente():
    assert "EJEMPLO HISTÓRICO" in leer(SK / "references/especificacion-sisben.md").splitlines()[0]
    skill = leer(SK / "SKILL.md")
    paso1 = skill[skill.index("### Paso 1"):skill.index("### Paso 2")]
    assert "Referencias: `references/especificacion-sisben.md`" not in paso1
    assert "ejemplo histórico" in paso1 and "**manda**" in paso1


def test_interpretar_salida_y_runbook_no_fijan_valores_del_ejemplo():
    salida = leer(SK / "references/interpretar-salida.md")
    assert "son los del ejemplo de forma" in salida and "SISBEN_INC_018" in salida
    assert "Las 7 tablas en `lookupHome`" not in leer(SK / "references/runbook-ejecucion.md")
    fila = next(l for l in leer(SK / "references/decisiones-y-abiertos.md").splitlines() if l.startswith("| F-08 |"))
    assert "RV-SIS-04" in fila and "ejemplo de forma" in fila


# --- AUD-V-15: RQS por registro; menores = tipos 1 y 2 del archivo ---

def test_steering_07_y_lexico_alineados_con_la_spec():
    t = leer(ST / "07-contratos-frontera.md")
    assert "Quality Score por campo" not in t and "quality score por registro" in t
    assert "tipos documentales 7 y 10 se rechazan" not in t
    assert "tipos de documento `1` y `2` **del archivo**" in t
    lexico = leer(ST / "02-lexico-edf.md")
    assert "tipos documentales 7 y 10 se rechazan" not in lexico and "la spec de la ingesta" in lexico


# --- AUD-V-21: un solo nombre para el inner de Validation ---

def test_nombre_del_inner_unificado():
    assert "validation-common-application.inner.conf" not in leer(ST / "10-piloto-sisben.md")
    assert "validation-common-application-inner.conf" in leer(ST / "10-piloto-sisben.md")
    for rel in ("assets/templates/validation-block.conf.tpl", "references/runbook-ejecucion.md",
                "references/inventario-plantillas.md"):
        assert "validation-common-application-inner.conf" in leer(SK / rel), rel


# --- HF-27 (AUD-V-31): comparar corridas sin eventId ni keys, por lineage.recordHash ---

def test_hf27_interpretar_salida_documenta_como_comparar_corridas():
    salida = leer(SK / "references/interpretar-salida.md")
    seccion = salida[salida.index("## Comparar dos corridas"):salida.index("## Privacidad al revisar")]
    plano = " ".join(seccion.split())
    assert "HF-27" in plano and "no se reproduce byte a byte" in plano
    assert "**se excluyen `eventId` y `keys`**" in plano and "**`lineage.recordHash`**" in plano
    assert "IdGenerator.nextEventId" in plano and "UUID" in plano and "SIN_CLAVE" in plano
    assert "multiconjunto" in plano  # filas idénticas comparten recordHash
    fila_keys = next(l for l in salida.splitlines() if l.startswith("| `keys` |"))
    assert "Comparar dos corridas" in fila_keys
