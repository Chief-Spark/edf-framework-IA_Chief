"""Regresión de la corrida E2E SISBEN: revisión de un insumo ya aprobado (HF-12)."""
import importlib.util
import json
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
REF03 = RAIZ / ".kiro/skills/edf-orquestador/references/03-gobierno-riesgo-aprobacion.md"


def _validador():
    spec = importlib.util.spec_from_file_location("validar_expediente", RAIZ / "tools/validar-expediente.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_ref03_documenta_revision_de_insumo_aprobado():
    txt = REF03.read_text(encoding="utf-8")
    assert "### 3c. Revisión de un insumo ya aprobado" in txt
    seccion = txt.split("### 3c.")[1].split("Además aplican")[0]
    for frase in ("vuelve a `propuesto`", "Se suspende la transición", "`transicion_habilitada = false`", "vuelve a `no_iniciada`",
                  "Nueva aprobación de la transición", "regla 7b"):
        assert frase in seccion, frase


def test_regla_7b_bloquea_transicion_con_insumo_en_revision():
    mod = _validador()
    estado = {
        "etapas": [
            {"id": "analisis", "estado": "aprobada", "transicion_habilitada": True, "depende_de": [],
             "artefactos": [], "ref_aprobacion": "APR-002"},
            {"id": "validation", "estado": "en_progreso", "transicion_habilitada": False, "depende_de": ["analisis"],
             "artefactos": [{"id": "ART-010", "ruta": "insumos/source-spec-validation.yaml", "obligatorio": True,
                             "estado_artefacto": "propuesto"}]},
        ],
        "aprobaciones": [{"id": "APR-002", "tipo": "transicion", "desde_etapa": "analisis", "hacia_etapa": "validation",
                          "decision": "aprobada", "riesgo": "medio", "actor": {"rol": "Desarrollador"}}],
    }
    fallos = mod.reglas_gobierno(estado)
    assert any(f.startswith("7b.") and "ART-010" in f for f in fallos), json.dumps(fallos, ensure_ascii=False)
    # Suspensión correcta (3c paso 2): transición apagada y N+1 de vuelta en no_iniciada.
    estado["etapas"][0]["transicion_habilitada"] = False
    assert any("sin transicion" in f for f in mod.reglas_gobierno(estado))  # en_progreso no se admite
    estado["etapas"][1]["estado"] = "no_iniciada"
    assert not any(f.startswith("7b.") for f in mod.reglas_gobierno(estado))


def test_ref03_documenta_revision_de_artefactos_de_etapa_aprobada():
    """HF-25: regenerar artefactos de una etapa aprobada sin desaprobarla (re-auditoría por diferencia)."""
    txt = REF03.read_text(encoding="utf-8")
    assert "### 3d. Revisión de artefactos de una etapa ya aprobada" in txt
    seccion = txt.split("### 3d.")[1].split("Además aplican")[0]
    for frase in ("no** se desaprueba", "Re-auditoría por diferencia", "Nueva aprobación `tipo: artefacto`",
                  "evento `correccion`", "aplica §3c"):
        assert frase in seccion, frase


def test_ref03_3d_conserva_el_estado_aprobado_durante_la_revision():
    """HF-43: durante la revisión §3d el artefacto sigue aprobado con su hash anterior (regla 7e)."""
    seccion = REF03.read_text(encoding="utf-8").split("### 3d.")[1].split("Además aplican")[0]
    assert "siguen `aprobado` con el hash anterior" in seccion and "regla 7e" in seccion


def test_09_bloqueo_por_afirmacion_se_mide_por_etapa_afectada():
    """HF-46: `09` §5.1 #2 se evalúa por la etapa_afectada de la pregunta que cita el [A:<ID>]."""
    import glob
    txt = open(glob.glob(str(RAIZ / ".kiro/steering/09-*.md"))[0], encoding="utf-8").read()
    regla = txt.split("### 5.1")[1].split("### 5.2")[0]
    assert "`etapa_afectada` igual a esa etapa" in regla
    assert "sin pregunta registrada en el expediente bloquea siempre" in regla


def test_ref03_documenta_verificacion_diferida():
    """HF-62: verificación diferida de hechos fuera del repositorio (corrida E2E SISBEN, PA-008)."""
    seccion = REF03.read_text(encoding="utf-8").split("### 3e.")[1].split("Además aplican")[0]
    for frase in ("Ningún artefacto generado dependa del hecho no verificado", "`[PM]`",
                  "bloquea la integración al fork y la certificación", "§3c o §3d"):
        assert frase in seccion, frase


def test_ref03_documenta_revision_no_sustantiva_de_insumo():
    """HF-80 (1.9.13): revisión no sustantiva de un insumo aprobado sin suspender transiciones (§3d-bis)."""
    txt = REF03.read_text(encoding="utf-8")
    seccion = txt.split("### 3d-bis.")[1].split("### 3e.")[0]
    for frase in ("**(a)**", "**(b)**", "**(c)**", "**(d)**", "desplegable", "quitando comentarios",
                  "sigue `aprobado` con el hash anterior", "Re-auditoría por diferencia".lower(),
                  "mismo riesgo que la primera vez", "evento `correccion`", "revisión no sustantiva (§3d-bis)",
                  "Decide el aprobador de la etapa", "`critico`", "aplica §3c", "regla 7g"):
        assert frase in seccion, frase
    assert "§3d-bis" in txt.split("### 3c.")[1].split("### 3d.")[0]
    assert "§3d-bis" in txt.split("### 3d.")[1].split("### 3d-bis.")[0]
    prompt = (RAIZ / ".kiro/skills/edf-orquestador/prompts/03-registrar-aprobacion.md").read_text(encoding="utf-8")
    assert "revisión no sustantiva (§3d-bis)" in prompt and "regla 7g" in prompt
    assert _validador().MARCADOR_3D_BIS == "revisión no sustantiva (§3d-bis)"
