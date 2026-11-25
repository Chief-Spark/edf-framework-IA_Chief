"""Pruebas de tools/validar-expediente.py (reglas de gobierno de references/03 del Orquestador).

Cada caso parte de la plantilla del expediente (estado-ingesta.seed.json), arma un estado válido y rompe una
sola regla. Datos sintéticos.
"""
import copy
import importlib.util
import json
import sys
from pathlib import Path

import pytest

RAIZ = Path(__file__).resolve().parent.parent
SEED = RAIZ / ".kiro/skills/edf-orquestador/templates/estado-ingesta.seed.json"
_spec = importlib.util.spec_from_file_location("validar_expediente", RAIZ / "tools" / "validar-expediente.py")
ve = importlib.util.module_from_spec(_spec)
sys.modules["validar_expediente"] = ve
_spec.loader.exec_module(ve)


def apr(aid, tipo, riesgo="bajo", desde=None, hacia=None, artefactos=None, rol="Desarrollador", bit="BIT-0002",
        justificacion="Revisado y aprobado por el ingeniero de ingesta."):
    return {"id": aid, "tipo": tipo, "desde_etapa": desde, "hacia_etapa": hacia, "artefactos": artefactos or [],
            "decision": "aprobada", "actor": {"rol": rol, "identificador": "C000001"},
            "timestamp": "2026-11-24T10:00:00-05:00", "riesgo": riesgo, "nivel_friccion": "ligera",
            "justificacion": justificacion, "condiciones": [], "resultado": "aplicado", "ref_bitacora": bit}


def etapa(est, eid):
    return next(e for e in est["etapas"] if e["id"] == eid)


@pytest.fixture
def est():
    """Etapa 0 aprobada y transición a Validation habilitada; Validation en progreso con su insumo aprobado."""
    e = json.loads(SEED.read_text(encoding="utf-8"))
    an, va = etapa(e, "analisis"), etapa(e, "validation")
    for a in an["artefactos"]:
        a["estado_artefacto"] = "aprobado"
    an.update(estado="aprobada", transicion_habilitada=True, ref_aprobacion="APR-001")
    va["estado"] = "en_progreso"
    va["artefactos"][0]["estado_artefacto"] = "aprobado"  # ART-010, insumo
    e["aprobaciones"] = [apr("APR-001", "transicion", desde="analisis", hacia="validation")]
    e["bitacora"].update(ultimo_evento="BIT-0002", eventos_totales=2)
    return e


def codigos(e):
    return sorted({f.split(" ")[0] for f in ve.reglas_gobierno(e)})


def habilitar_validation(e, evidencia=None, riesgo_apr="medio"):
    va = etapa(e, "validation")
    for a in va["artefactos"]:
        a["estado_artefacto"] = "aprobado"
    va.update(estado="aprobada", transicion_habilitada=True, ref_aprobacion="APR-002")
    va["evidencia_pipeline"]["observada"] = evidencia
    etapa(e, "acceptance")["artefactos"][0]["estado_artefacto"] = "aprobado"
    e["aprobaciones"].append(apr("APR-002", "transicion", riesgo=riesgo_apr, desde="validation", hacia="acceptance"))


def test_estado_base_y_plantilla_pasan(est):
    assert ve.reglas_gobierno(est) == []
    assert ve.reglas_gobierno(json.loads(SEED.read_text(encoding="utf-8"))) == []


def test_c2_aprobacion_de_otro_tipo_o_de_otra_etapa(est):
    malo = copy.deepcopy(est)
    malo["aprobaciones"][0]["tipo"] = "artefacto"
    assert codigos(malo) == ["7a."]
    malo = copy.deepcopy(est)
    malo["aprobaciones"][0]["desde_etapa"] = "validation"
    assert codigos(malo) == ["7a."]


def test_c3_insumo_de_la_etapa_siguiente_sin_aprobar(est):
    etapa(est, "validation")["artefactos"][0]["estado_artefacto"] = "prevalidado"
    assert codigos(est) == ["7b."]


def test_c3_etapa_iniciada_con_dependencia_sin_habilitar(est):
    etapa(est, "acceptance")["estado"] = "en_progreso"
    assert codigos(est) == ["7b."]


def test_c5_sin_evidencia_ni_diferida(est):
    habilitar_validation(est)
    assert codigos(est) == ["7c."]


def test_c5_diferida_exige_riesgo_alto(est):
    habilitar_validation(est, riesgo_apr="medio")
    est["riesgos"].append({"id": "RG-010", "descripcion": "Evidencia diferida de validation", "nivel": "alto",
                           "etapa_afectada": "validation", "mitigacion": None, "estado": "abierto"})
    assert codigos(est) == ["7c."]
    est["aprobaciones"][-1]["riesgo"] = "alto"
    assert codigos(est) == []


def test_c5_con_evidencia_observada(est):
    habilitar_validation(est, evidencia="s3://sintetico/validated/")
    assert codigos(est) == []


def test_etapa_aprobada_con_artefacto_pendiente(est):
    habilitar_validation(est, evidencia="s3://sintetico/validated/")
    etapa(est, "validation")["artefactos"][2]["estado_artefacto"] = "prevalidado"
    assert codigos(est) == ["7e."]


def test_state_calculation_siempre_critica(est):
    est["aprobaciones"].append(apr("APR-070", "artefacto", riesgo="alto", artefactos=["ART-070"]))
    assert codigos(est) == ["7d."]
    est["aprobaciones"][-1].update(riesgo="critico", actor={"rol": "PO", "identificador": "PO0001"})
    assert codigos(est) == []


def test_state_calculation_no_admite_evidencia_diferida(est):
    sc = etapa(est, "state_calculation")
    for a in sc["artefactos"]:
        a["estado_artefacto"] = "aprobado"
    sc.update(estado="aprobada", transicion_habilitada=True, ref_aprobacion="APR-080")
    est["aprobaciones"].append(apr("APR-080", "transicion", riesgo="critico", desde="state_calculation", rol="PO"))
    est["riesgos"].append({"id": "RG-080", "descripcion": "Evidencia diferida de state_calculation", "nivel": "alto",
                           "etapa_afectada": "state_calculation", "mitigacion": None, "estado": "abierto"})
    assert "7d." in codigos(est)


def test_regla_de_corte_exige_backlog_y_aprobacion(est):
    cs = etapa(est, "curation_stock")
    cs.update(estado="completada_manualmente", nota_regla_de_corte="DAG fuera de plazo (sintético)")
    assert "7f." in codigos(est)
    est["aprobaciones"].append(apr("APR-090", "regla_de_corte"))
    est["backlog_evolutivo"].append({"id": "BL-001", "etapa": "curation_stock", "pendiente": "Automatizar",
                                     "motivo": None, "creado_en": "2026-11-24", "ref_aprobacion": "APR-090"})
    assert "7f." not in codigos(est)


def test_bitacora_cruzada(est):
    texto = "## BIT-0001\nx\n## BIT-0002\ny\n"
    assert ve.bitacora_cruzada(est, texto)[0] == []
    falla = [f.split(" ")[0] for f in ve.bitacora_cruzada(est, "## BIT-0002\n## BIT-0001\n## BIT-0003\n")[0]]
    assert {"V-4.", "V-5.", "V-6."} <= set(falla)
    solo_uno = [f.split(" ")[0] for f in ve.bitacora_cruzada(est, "## BIT-0001\n")[0]]
    assert "V-3." in solo_uno  # APR-001 apunta a BIT-0002, que no está


def test_main_pii_es_hallazgo_salvo_permisivo(est, tmp_path, capsys):
    est["ingesta"]["descripcion"] = "contacto persona@ejemplo.com"
    ruta = tmp_path / "estado-ingesta.json"
    ruta.write_text(json.dumps(est, ensure_ascii=False), encoding="utf-8")
    bit = tmp_path / "bitacora.md"
    bit.write_text("## BIT-0001\n## BIT-0002\n", encoding="utf-8")
    assert ve.main([str(ruta), "--cruzado", str(bit)]) == 1
    assert ve.main([str(ruta), "--cruzado", str(bit), "--permisivo"]) == 0
    assert "EXPEDIENTE VALIDO" in capsys.readouterr().out


def test_main_sin_argumentos(capsys):
    assert ve.main([]) == 2


# --- Regla 5, condición 4 por etapa afectada (1.9.1; corrida E2E SISBEN, AUD-E0-10) ---

def pregunta(pid, etapa_afectada, bloqueante=True, estado="abierta"):
    return {"id": pid, "pregunta": "Pregunta sintética de prueba", "origen": None, "etapa_afectada": etapa_afectada,
            "bloqueante": bloqueante, "estado": estado, "abierta_en": "2026-11-24T10:00:00-05:00",
            "resuelta_en": None, "respuesta": None, "ref_bitacora": None}


def codigos_preguntas(e):
    return [f.split(" ")[0] for f in ve.reglas_preguntas(e)]


def test_estado_base_sin_preguntas_pasa_regla_5(est):
    assert ve.reglas_preguntas(est) == []


def test_bloqueante_de_state_calculation_no_frena_la_etapa_0(est):
    """La pregunta se registró en la lista de `analisis` (como en la corrida E2E), pero afecta a State Calculation."""
    est["preguntas_abiertas"].append(pregunta("PA-002", "state_calculation"))
    etapa(est, "analisis")["preguntas_abiertas"].append("PA-002")
    # analisis aprobada y transición analisis -> validation habilitada (fixture): no hay hallazgo.
    assert ve.reglas_preguntas(est) == []
    # validation aprobada y transición a acceptance tampoco se frenan.
    habilitar_validation(est, evidencia="s3://sintetico/validated/")
    assert ve.reglas_preguntas(est) == []


def test_bloqueante_de_state_calculation_frena_la_transicion_hacia_ella(est):
    est["preguntas_abiertas"].append(pregunta("PA-002", "state_calculation"))
    sy = etapa(est, "synthesis")
    sy.update(estado="aprobada", transicion_habilitada=True, ref_aprobacion="APR-060")
    fallos = ve.reglas_preguntas(est)
    assert len(fallos) == 1 and "`synthesis` -> `state_calculation`" in fallos[0] and "PA-002" in fallos[0]
    # Y frena la aprobación de la propia etapa.
    sy.update(transicion_habilitada=False)
    etapa(est, "state_calculation")["estado"] = "aprobada"
    assert codigos_preguntas(est) == ["5."]
    assert "`state_calculation` aprobada" in ve.reglas_preguntas(est)[0]


def test_bloqueante_de_analisis_o_validation_frena_la_etapa_0(est):
    for afectada in ("analisis", "validation"):
        e = copy.deepcopy(est)
        e["preguntas_abiertas"].append(pregunta("PA-001", afectada))
        fallos = ve.reglas_preguntas(e)
        assert len(fallos) == 1 and "PA-001" in fallos[0], afectada


def test_sin_etapa_afectada_cuenta_para_todas(est):
    est["preguntas_abiertas"].append(pregunta("PA-003", None))
    assert codigos_preguntas(est) == ["5."]


def test_no_bloqueante_o_resuelta_no_frena(est):
    est["preguntas_abiertas"] += [pregunta("PA-004", "analisis", bloqueante=False),
                                  pregunta("PA-005", "validation", estado="resuelta")]
    assert ve.reglas_preguntas(est) == []


def test_main_reporta_la_regla_5(est, tmp_path, capsys):
    pytest.importorskip("jsonschema")
    est["preguntas_abiertas"].append(pregunta("PA-001", "validation"))
    ruta = tmp_path / "estado-ingesta.json"
    ruta.write_text(json.dumps(est, ensure_ascii=False), encoding="utf-8")
    assert ve.main([str(ruta)]) == 1
    assert "[cond. 4] Transicion `analisis` -> `validation`" in capsys.readouterr().out
    est["preguntas_abiertas"][-1]["etapa_afectada"] = "state_calculation"
    ruta.write_text(json.dumps(est, ensure_ascii=False), encoding="utf-8")
    assert ve.main([str(ruta)]) == 0


# --- Regla 7g: revisión no sustantiva de un insumo (references/03 §3d-bis, 1.9.13; corrida E2E SISBEN, HF-80) ---

EVIDENCIA_3D_BIS = ("Re-auditoría por diferencia: (a) desplegables regenerados idénticos sin comentarios; (b) solo cambia "
                    "una clave de prueba; (c) sin cambios de decisión, alcance, riesgo ni marcadores; (d) mismas "
                    "preguntas y riesgos.")


def con_hora(a, hora):
    a["timestamp"] = f"2026-11-24T{hora}:00-05:00"
    return a


@pytest.fixture
def cascada(est):
    """ART-010 aprobado (APR-001) antes de la transición analisis -> validation (APR-002); validation aprobada y su
    sucesora acceptance en progreso: un §3c obligaría a desaprobar validation."""
    est["aprobaciones"] = [con_hora(apr("APR-001", "artefacto", riesgo="medio", artefactos=["ART-010"]), "09:00"),
                           con_hora(apr("APR-002", "transicion", desde="analisis", hacia="validation"), "10:00")]
    etapa(est, "analisis")["ref_aprobacion"] = "APR-002"
    va = etapa(est, "validation")
    for a in va["artefactos"]:
        a["estado_artefacto"] = "aprobado"
    va.update(estado="aprobada", transicion_habilitada=True, ref_aprobacion="APR-003")
    va["evidencia_pipeline"]["observada"] = "s3://sintetico/validated/"
    etapa(est, "acceptance")["artefactos"][0]["estado_artefacto"] = "aprobado"
    etapa(est, "acceptance")["estado"] = "en_progreso"
    est["aprobaciones"].append(con_hora(apr("APR-003", "transicion", riesgo="medio", desde="validation",
                                            hacia="acceptance"), "11:00"))
    assert codigos(est) == []  # durante la revisión el insumo sigue aprobado con el hash anterior
    return est


def reaprobar(e, aid="APR-004", hora="12:00", riesgo="medio", marcador=True, justificacion=EVIDENCIA_3D_BIS, **kw):
    r = con_hora(apr(aid, "artefacto", riesgo=riesgo, artefactos=["ART-010"], justificacion=justificacion, **kw), hora)
    if marcador:
        r["condiciones"] = ["Revisión no sustantiva (§3d-bis)"]
    e["aprobaciones"].append(r)
    return r


def test_7g_revision_no_sustantiva_aceptada(cascada):
    reaprobar(cascada)
    assert codigos(cascada) == []


def test_7g_sin_marcador_se_rechaza(cascada):
    """Cambio sustantivo (o no declarado): re-aprobar el insumo sin suspender la transición es hallazgo."""
    reaprobar(cascada, marcador=False)
    fallos = ve.reglas_gobierno(cascada)
    assert codigos(cascada) == ["7g."] and "aplica §3c" in fallos[0] and "ART-010" in fallos[0]


def test_7g_insumo_critico_se_rechaza(cascada):
    etapa(cascada, "validation")["artefactos"][0]["riesgo"] = "critico"
    reaprobar(cascada, riesgo="critico", rol="PO")
    fallos = ve.reglas_gobierno(cascada)
    assert codigos(cascada) == ["7g."] and "critico" in fallos[0]
    # Sin el marcador tampoco: con la transición habilitada, un insumo crítico solo se revisa por §3c.
    cascada["aprobaciones"][-1]["condiciones"] = []
    assert codigos(cascada) == ["7g."]


def test_7g_marcador_exige_evidencia_riesgo_y_aprobador(cascada):
    r = reaprobar(cascada, justificacion="Cambio menor de una clave de prueba (a) y (b).")
    assert codigos(cascada) == ["7g."] and "(c), (d)" in ve.reglas_gobierno(cascada)[0]
    r["justificacion"] = EVIDENCIA_3D_BIS
    r["riesgo"] = "bajo"  # la primera fue medio
    assert codigos(cascada) == ["7g."]
    r["riesgo"] = "medio"
    r["actor"]["rol"] = "Experto de dominio EDF"
    assert codigos(cascada) == ["7g."]


def test_7g_via_3c_sigue_valida(cascada):
    """Historial de la corrida SISBEN (ART-010, APR-005 y APR-006): la transición se re-aprueba después del insumo
    (mismo timestamp, APR posterior). No lleva marcador y no es hallazgo."""
    reaprobar(cascada, marcador=False, hora="12:00")
    cascada["aprobaciones"].append(con_hora(apr("APR-005", "transicion", desde="analisis", hacia="validation"),
                                            "12:00"))
    etapa(cascada, "analisis")["ref_aprobacion"] = "APR-005"
    assert codigos(cascada) == []


def test_7g_transicion_suspendida_no_es_hallazgo(est):
    """§3c en curso: insumo re-aprobado con la transición todavía suspendida."""
    est["aprobaciones"] = [con_hora(apr("APR-001", "artefacto", riesgo="medio", artefactos=["ART-010"]), "09:00"),
                           con_hora(apr("APR-002", "transicion", desde="analisis", hacia="validation"), "10:00")]
    etapa(est, "analisis").update(ref_aprobacion="APR-002", transicion_habilitada=False)
    etapa(est, "validation")["estado"] = "no_iniciada"
    reaprobar(est, marcador=False)
    assert codigos(est) == []


def test_7g_marcador_sin_aprobacion_anterior(est):
    reaprobar(est, aid="APR-009", hora="08:00")
    assert codigos(est) == ["7g."]


def test_7g_marcador_sin_tildes_ni_mayusculas(cascada):
    r = reaprobar(cascada)
    r["condiciones"] = ["revision no sustantiva (§3d-bis): solo la clave de prueba"]
    assert codigos(cascada) == []


def test_main_reporta_7g(cascada, tmp_path, capsys):
    pytest.importorskip("jsonschema")
    reaprobar(cascada, marcador=False)
    ruta = tmp_path / "estado-ingesta.json"
    ruta.write_text(json.dumps(cascada, ensure_ascii=False), encoding="utf-8")
    assert ve.main([str(ruta)]) == 1
    assert "7g. [03 §3c/§3d-bis] APR-004" in capsys.readouterr().out
    cascada["aprobaciones"][-1]["condiciones"] = [ve.MARCADOR_3D_BIS]
    ruta.write_text(json.dumps(cascada, ensure_ascii=False), encoding="utf-8")
    assert ve.main([str(ruta)]) == 0
