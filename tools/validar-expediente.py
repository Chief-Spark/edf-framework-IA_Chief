#!/usr/bin/env python3
"""Valida un expediente de ingesta EDF contra el esquema del Orquestador.

Uso:
    python3 tools/validar-expediente.py <ruta/estado-ingesta.json> [--cruzado <ruta/bitacora.md>]

Verifica:
  1. El JSON valida contra estado-ingesta.schema.json (requiere `jsonschema`).
  2. Invariante R3.2: ninguna etapa tiene transicion_habilitada=true sin ref_aprobacion
     valida y estado en {aprobada, completada_manualmente}.
  3. Invariante R3.5: ninguna aprobacion de riesgo alto o critico sin justificacion;
     riesgo critico (N4) aprobado solo por PO o Aprobador designado.
  4. Integridad referencial: ref_aprobacion -> aprobaciones[]; preguntas/riesgos por etapa
     existen en las listas globales.
  5. Condicion 4 (1.9.1, por etapa): una pregunta bloqueante abierta detiene solo su `etapa_afectada`
     (sin `etapa_afectada`, todas). Ninguna etapa aprobada con bloqueantes abiertas de esa etapa, y
     ninguna transicion N -> N+1 habilitada con bloqueantes abiertas de N o de N+1.
  2d. Etapa omitida: solo con nota_omision y aprobacion tipo 'omision' del PO o del Aprobador designado.
  6. Heuristica anti-PII sobre el JSON (y la bitacora con --cruzado).
  7. (1.9.0, references/03 del Orquestador) Reglas que antes no se exigian:
     7a. C2: la aprobacion que habilita la transicion es tipo 'transicion' y sale de la etapa.
     7b. C3: las dependencias de la etapa estan habilitadas y el insumo de la etapa siguiente esta aprobado.
     7c. C5: hay evidencia del pipeline o la etapa se aprobo con evidencia diferida (riesgo abierto
         "diferida" de la etapa), y en ese caso la aprobacion es de riesgo alto o critico.
     7d. §1/§3b: State Calculation es siempre critico (PO o Aprobador designado) y no admite evidencia diferida.
     7e. Una etapa aprobada tiene aprobados todos sus artefactos obligatorios.
     7f. §4: completada_manualmente exige aprobacion 'regla_de_corte' y una entrada en backlog_evolutivo.
     7g. (1.9.13, §3d-bis) Una re-aprobacion 'artefacto' de un insumo (`insumos/`) hecha con la transicion
         hacia su etapa habilitada (ninguna transicion re-aprobada despues: no se siguio §3c) debe declarar en
         `condiciones` el marcador «revisión no sustantiva (§3d-bis)», con justificacion que cite (a)-(d),
         riesgo no menor que la primera aprobacion y actor Desarrollador, PO o Aprobador designado. Un insumo
         critico (o de State Calculation) nunca lleva el marcador: siempre §3c.
  8. Con --cruzado: la bitacora no reusa IDs, sus IDs son crecientes, y eventos_totales / ultimo_evento
     coinciden con la bitacora.
Opciones:
  --permisivo   jsonschema ausente y posible PII pasan a AVISO (por defecto son HALLAZGO).
Salida: codigo 0 si todo pasa, 1 si hay algun hallazgo.
"""
import json
import pathlib
import re
import sys

_RAIZ = pathlib.Path(__file__).resolve().parent.parent
_REL = pathlib.Path("skills") / "edf-orquestador" / "templates" / "estado-ingesta.schema.json"
# El repo usa `kiro/` (visible); instalado en un workspace se llama `.kiro/`.
SCHEMA = next((_RAIZ / d / _REL for d in ("kiro", ".kiro") if (_RAIZ / d / _REL).is_file()),
              _RAIZ / "kiro" / _REL)

PII_PATTERNS = [
    (r"\b[\w.+-]+@[\w-]+\.[\w.]+\b", "correo electronico"),
    (r"\b(?:nombre|apellido)s?_?(?:completo|real)?\s*[:=]\s*\"?[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+", "valor de nombre"),
    (r"\bnum(?:ero)?_?doc(?:umento)?\s*[:=]\s*\"?\d{5,}", "numero de documento"),
    (r"\bcedula\s*[:=]\s*\"?\d{5,}", "numero de cedula"),
]


def hallazgos_pii(texto, origen):
    out = []
    for patron, etiqueta in PII_PATTERNS:
        for m in re.finditer(patron, texto, re.IGNORECASE):
            out.append(f"[PII?] {origen}: posible {etiqueta} -> {m.group(0)[:60]!r}")
    return out


ETAPA_N4 = "state_calculation"
ESTADOS_CERRADOS = ("aprobada", "completada_manualmente", "omitida")


def _rol(aprobacion):
    return (aprobacion.get("actor") or {}).get("rol")


def reglas_gobierno(estado):
    """Reglas 7a-7f de references/03 (1.9.0). Devuelve la lista de hallazgos."""
    fallos = []
    etapas = {e.get("id"): e for e in estado.get("etapas", [])}
    aprobaciones = {a["id"]: a for a in estado.get("aprobaciones", [])}
    riesgos = estado.get("riesgos", [])
    backlog = estado.get("backlog_evolutivo", [])
    art_etapa = {a.get("id"): eid for eid, e in etapas.items() for a in e.get("artefactos", [])}

    def diferida(eid):
        return any(r.get("etapa_afectada") == eid and "diferid" in (r.get("descripcion") or "").lower()
                   and r.get("estado") in ("abierto", "mitigado", "aceptado") for r in riesgos)

    for eid, et in etapas.items():
        estado_et = et.get("estado")
        apr = aprobaciones.get(et.get("ref_aprobacion") or "")
        sigue = [m for m, e2 in etapas.items() if eid in (e2.get("depende_de") or [])]
        if et.get("transicion_habilitada") and apr and estado_et == "aprobada":
            # 7a. C2
            if apr.get("tipo") != "transicion":
                fallos.append(f"7a. [C2] Etapa `{eid}`: la aprobacion {apr['id']} que habilita la transicion es "
                              f"tipo '{apr.get('tipo')}', no 'transicion'.")
            elif apr.get("desde_etapa") and apr.get("desde_etapa") != eid:
                fallos.append(f"7a. [C2] Etapa `{eid}`: {apr['id']} es la transicion desde "
                              f"'{apr.get('desde_etapa')}', no desde `{eid}`.")
            # 7c. C5
            ev = (et.get("evidencia_pipeline") or {}).get("observada")
            if et.get("en_pipeline_configurado") and not ev:
                if eid == ETAPA_N4:
                    fallos.append(f"7d. [03 §3b] Etapa `{eid}`: transicion sin evidencia del pipeline; "
                                  "State Calculation no admite evidencia diferida.")
                elif not diferida(eid):
                    fallos.append(f"7c. [C5] Etapa `{eid}`: transicion habilitada sin evidencia del pipeline y sin "
                                  "riesgo abierto de evidencia diferida de la etapa.")
                elif apr.get("riesgo") not in ("alto", "critico"):
                    fallos.append(f"7c. [03 §1] Etapa `{eid}`: transicion con evidencia diferida aprobada como "
                                  f"'{apr.get('riesgo')}'; debe ser alto o critico.")
        # 7b. C3: dependencias e insumo de la etapa siguiente
        if et.get("transicion_habilitada"):
            for m in sigue:
                for a in etapas[m].get("artefactos", []):
                    if (a.get("obligatorio") and (a.get("ruta") or "").startswith("insumos/")
                            and a.get("estado_artefacto") != "aprobado"):
                        fallos.append(f"7b. [C3] Transicion `{eid}` -> `{m}` habilitada con el insumo {a.get('id')} "
                                      f"en estado '{a.get('estado_artefacto')}'.")
        if estado_et not in ("no_iniciada", None):
            for dep in et.get("depende_de") or []:
                if dep in etapas and not etapas[dep].get("transicion_habilitada"):
                    fallos.append(f"7b. [C3] Etapa `{eid}` en '{estado_et}' con su dependencia `{dep}` sin transicion "
                                  "habilitada.")
        # 7e. Etapa aprobada con artefactos obligatorios aprobados
        if estado_et == "aprobada":
            pend = [a.get("id") for a in et.get("artefactos", [])
                    if a.get("obligatorio") and a.get("estado_artefacto") != "aprobado"]
            if pend:
                fallos.append(f"7e. Etapa `{eid}` aprobada con artefactos obligatorios sin aprobar: {', '.join(pend)}.")
        # 7f. Regla de corte
        if estado_et == "completada_manualmente":
            items = [b for b in backlog if b.get("etapa") == eid]
            if not items:
                fallos.append(f"7f. [03 §4] Etapa `{eid}`: completada_manualmente sin entrada en backlog_evolutivo.")
            for b in items:
                ac = aprobaciones.get(b.get("ref_aprobacion") or "")
                if not ac or ac.get("tipo") != "regla_de_corte" or ac.get("decision") != "aprobada":
                    fallos.append(f"7f. [03 §4] Etapa `{eid}`: {b.get('id')} no apunta a una aprobacion "
                                  "'regla_de_corte' aprobada.")
                elif eid == ETAPA_N4 and _rol(ac) not in ("PO", "Aprobador designado"):
                    fallos.append(f"7f. [03 §4] Etapa `{eid}`: la regla de corte la aprobo '{_rol(ac)}'; "
                                  "solo PO o Aprobador designado.")

    # 7d. Toda aprobacion de State Calculation es critica (03 §1, clasificacion fija de 06 §2.2)
    n4 = etapas.get(ETAPA_N4, {})
    for aid, a in aprobaciones.items():
        toca_n4 = (a.get("desde_etapa") == ETAPA_N4 or aid == n4.get("ref_aprobacion")
                   or any(art_etapa.get(x) == ETAPA_N4 for x in a.get("artefactos") or []))
        if toca_n4 and a.get("decision") == "aprobada" and a.get("riesgo") != "critico":
            fallos.append(f"7d. [03 §1] {aid}: aprobacion de State Calculation con riesgo '{a.get('riesgo')}'; "
                          "es siempre critico (PO o Aprobador designado).")

    fallos += regla_revision_no_sustantiva(estado)
    return fallos


# --- 7g. Revision no sustantiva de un insumo (references/03 §3d-bis, 1.9.13; corrida E2E SISBEN, HF-80) ---
MARCADOR_3D_BIS = "revisión no sustantiva (§3d-bis)"
ORDEN_RIESGO = {"bajo": 0, "medio": 1, "alto": 2, "critico": 3}
ROLES_APROBADOR_ETAPA = ("Desarrollador", "PO", "Aprobador designado")


def _normal(texto):
    import unicodedata
    t = unicodedata.normalize("NFKD", texto or "")
    return "".join(c for c in t if not unicodedata.combining(c)).lower()


def declara_3d_bis(aprobacion):
    """True si alguna condicion de la aprobacion trae el marcador (sin distinguir tildes ni mayusculas)."""
    m = _normal(MARCADOR_3D_BIS)
    return any(m in _normal(c) for c in aprobacion.get("condiciones") or [])


def _orden(aprobacion):
    """Clave cronologica: timestamp y, a igual timestamp, el numero de APR (append-only)."""
    from datetime import datetime
    try:
        ts = datetime.fromisoformat((aprobacion.get("timestamp") or "").replace("Z", "+00:00")).timestamp()
    except ValueError:
        ts = float("-inf")
    num = int(re.sub(r"\D", "", aprobacion.get("id") or "") or 0)
    return (ts, num)


def regla_revision_no_sustantiva(estado):
    """Regla 7g. Con la transicion N -> N+1 habilitada, un insumo de N+1 solo se re-aprueba por dos vias:

    - §3c: la transicion se suspende y se re-aprueba DESPUES de la re-aprobacion del insumo (hay una aprobacion
      'transicion' hacia N+1 posterior), o sigue suspendida;
    - §3d-bis: la re-aprobacion declara el marcador, con la evidencia (a)-(d), sin ser critica.
    Una aprobacion anterior a la transicion vigente (p. ej. ART-010 APR-005 en SISBEN, re-aprobado por §3c antes
    de APR-006) no es hallazgo.
    """
    fallos = []
    etapas = {e.get("id"): e for e in estado.get("etapas", [])}
    aprs = [a for a in estado.get("aprobaciones", []) if a.get("id")]
    por_id = {a["id"]: a for a in aprs}
    transiciones = [a for a in aprs if a.get("tipo") == "transicion" and a.get("decision") == "aprobada"]

    for mid, et in etapas.items():
        for art in et.get("artefactos", []):
            if not (art.get("ruta") or "").startswith("insumos/"):
                continue
            xid = art.get("id")
            propias = sorted((a for a in aprs if a.get("tipo") == "artefacto" and a.get("decision") == "aprobada"
                              and xid in (a.get("artefactos") or [])), key=_orden)
            critico = (mid == ETAPA_N4 or art.get("riesgo") == "critico"
                       or any(a.get("riesgo") == "critico" for a in propias))
            for i, r in enumerate(propias):
                marca = declara_3d_bis(r)
                if i == 0:
                    if marca:
                        fallos.append(f"7g. [03 §3d-bis] {r['id']}: declara revision no sustantiva de {xid} sin una "
                                      "aprobacion anterior del insumo que revisar.")
                    continue
                # ¿Alguna transicion hacia la etapa del insumo sigue habilitada desde antes de esta re-aprobacion?
                sin_suspender = []
                for dep in et.get("depende_de") or []:
                    n = etapas.get(dep) or {}
                    if not n.get("transicion_habilitada"):
                        continue
                    cands = [t for t in transiciones if t.get("desde_etapa") == dep
                             and t.get("hacia_etapa") in (mid, None, "")]
                    ref = por_id.get(n.get("ref_aprobacion") or "")
                    if ref and ref.get("tipo") == "transicion" and ref.get("hacia_etapa") in (mid, None, ""):
                        cands.append(ref)
                    if cands and max(_orden(t) for t in cands) < _orden(r):
                        sin_suspender.append(dep)
                if critico and marca:
                    fallos.append(f"7g. [03 §3d-bis] {r['id']}: {xid} es un insumo critico; la revision no "
                                  "sustantiva no aplica (siempre §3c).")
                    continue
                if not sin_suspender:
                    continue  # §3c: la transicion se suspendio y se re-aprobo despues (o sigue suspendida)
                if critico:
                    fallos.append(f"7g. [03 §3c] {r['id']}: re-aprobacion del insumo critico {xid} con la transicion "
                                  f"{', '.join(sin_suspender)} -> {mid} habilitada; debe suspenderse (§3c).")
                    continue
                if not marca:
                    fallos.append(f"7g. [03 §3c/§3d-bis] {r['id']}: re-aprobacion de {xid} con la transicion "
                                  f"{', '.join(sin_suspender)} -> {mid} habilitada y sin el marcador "
                                  f"«{MARCADOR_3D_BIS}» en condiciones; aplica §3c (suspender la transicion).")
                    continue
                just = _normal(r.get("justificacion"))
                faltan = [x for x in ("(a)", "(b)", "(c)", "(d)") if x not in just]
                if len(just.strip()) < 10 or faltan:
                    fallos.append(f"7g. [03 §3d-bis] {r['id']}: la justificacion de la revision no sustantiva de {xid} "
                                  f"no da la evidencia de {', '.join(faltan) or '(a)-(d)'}.")
                if ORDEN_RIESGO.get(r.get("riesgo"), -1) < ORDEN_RIESGO.get(propias[0].get("riesgo"), -1):
                    fallos.append(f"7g. [03 §3d-bis] {r['id']}: riesgo '{r.get('riesgo')}' menor que el de la primera "
                                  f"aprobacion de {xid} ({propias[0]['id']}, '{propias[0].get('riesgo')}').")
                if _rol(r) not in ROLES_APROBADOR_ETAPA:
                    fallos.append(f"7g. [03 §3d-bis] {r['id']}: la revision no sustantiva la decide el aprobador de la "
                                  f"etapa (Desarrollador, PO o Aprobador designado), no '{_rol(r)}'.")
    return fallos


def reglas_preguntas(estado):
    """Regla 5, condicion 4 de references/03 (1.9.1): el bloqueo se mide por `etapa_afectada`.

    Una pregunta bloqueante abierta detiene la aprobacion de su etapa y la transicion hacia ella. Antes se
    contaban las preguntas listadas en la etapa, y una pregunta de una etapa posterior registrada en
    `analisis` frenaba la etapa 0 (corrida E2E SISBEN, AUD-E0-10). Sin `etapa_afectada`, cuenta para todas.
    """
    fallos = []
    etapas = {e.get("id"): e for e in estado.get("etapas", [])}
    abiertas = [p for p in estado.get("preguntas_abiertas", [])
                if p.get("bloqueante") and p.get("estado") in ("abierta", "en_consulta")]

    def de(eid):
        return sorted(p["id"] for p in abiertas if p.get("etapa_afectada") in (eid, None, ""))

    for eid, et in etapas.items():
        if et.get("estado") in ("aprobada", "completada_manualmente"):
            pend = de(eid)
            if pend:
                fallos.append(f"5. [cond. 4] Etapa `{eid}` aprobada con preguntas bloqueantes abiertas de la etapa: "
                              f"{', '.join(pend)}.")
        if et.get("transicion_habilitada"):
            for m, e2 in etapas.items():
                if eid in (e2.get("depende_de") or []):
                    # Las de N ya se reportan arriba si N esta aprobada; aqui las de N+1 (y las de N si se omitio).
                    ya = set(de(eid)) if et.get("estado") in ("aprobada", "completada_manualmente") else set()
                    pend = sorted((set(de(m)) | set(de(eid))) - ya)
                    if pend:
                        fallos.append(f"5. [cond. 4] Transicion `{eid}` -> `{m}` habilitada con preguntas bloqueantes "
                                      f"abiertas de `{eid}` o `{m}`: {', '.join(pend)}.")
    return fallos


def bitacora_cruzada(estado, btxt):
    """Regla 8: IDs unicos y crecientes; contadores del JSON iguales a la bitacora."""
    fallos = []
    ids = re.findall(r"^##\s+(BIT-\d{4,})", btxt, re.M)
    if len(ids) != len(set(ids)):
        fallos.append("V-2. La bitacora reusa identificadores BIT-nnnn.")
    nums = [int(i.split("-")[1]) for i in ids]
    if nums != sorted(nums):
        fallos.append("V-4. Los eventos de la bitacora no estan en orden creciente (append-only).")
    bit = estado.get("bitacora") or {}
    if ids and bit.get("eventos_totales") != len(ids):
        fallos.append(f"V-5. bitacora.eventos_totales = {bit.get('eventos_totales')}, pero la bitacora tiene "
                      f"{len(ids)} eventos.")
    if ids and bit.get("ultimo_evento") != ids[-1]:
        fallos.append(f"V-6. bitacora.ultimo_evento = {bit.get('ultimo_evento')}, pero el ultimo es {ids[-1]}.")
    refs = {a.get("ref_bitacora") for a in estado.get("aprobaciones", []) if a.get("ref_bitacora")}
    faltan = sorted(refs - set(ids))
    if faltan:
        fallos.append(f"V-3. Aprobaciones referencian eventos ausentes de la bitacora: {', '.join(faltan)}.")
    return fallos, ids


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if not argv:
        print(__doc__)
        return 2
    permisivo = "--permisivo" in argv
    ruta = pathlib.Path(argv[0])
    raw = ruta.read_text(encoding="utf-8")
    estado = json.loads(raw)
    fallos, avisos = [], []
    grave = avisos if permisivo else fallos

    # 1. Esquema
    try:
        import jsonschema
        jsonschema.validate(estado, json.loads(SCHEMA.read_text(encoding="utf-8")))
        print("[OK] 1. Valida contra el esquema", estado.get("schema_version"))
    except ImportError:
        grave.append("1. `jsonschema` no instalado: `pip install jsonschema`. Sin esquema no se puede validar "
                     "(use --permisivo para continuar sin el).")
    except Exception as e:
        fallos.append(f"1. No valida contra el esquema: {str(e).splitlines()[0]}")

    aprobaciones = {a["id"]: a for a in estado.get("aprobaciones", [])}
    preguntas = {p["id"]: p for p in estado.get("preguntas_abiertas", [])}
    riesgos = {r["id"] for r in estado.get("riesgos", [])}

    for et in estado.get("etapas", []):
        eid = et.get("id")
        # 2. R3.2
        if et.get("transicion_habilitada"):
            ref = et.get("ref_aprobacion")
            if not ref:
                fallos.append(f"2. [R3.2] Etapa `{eid}`: transicion_habilitada=true SIN ref_aprobacion.")
            elif ref not in aprobaciones:
                fallos.append(f"2. [R3.2] Etapa `{eid}`: ref_aprobacion {ref} no existe en aprobaciones[].")
            elif aprobaciones[ref].get("decision") != "aprobada":
                fallos.append(f"2. [R3.2] Etapa `{eid}`: {ref} tiene decision='{aprobaciones[ref].get('decision')}', no 'aprobada'.")
            if et.get("estado") not in ("aprobada", "completada_manualmente", "omitida"):
                fallos.append(f"2. [R3.2] Etapa `{eid}`: transicion habilitada con estado='{et.get('estado')}'.")
        # 2d. Omision: solo con aprobacion tipo omision del PO o del Aprobador designado
        if et.get("estado") == "omitida":
            ref = et.get("ref_aprobacion")
            apr = aprobaciones.get(ref) if ref else None
            if not (et.get("nota_omision") or "").strip():
                fallos.append(f"2d. Etapa `{eid}`: omitida sin nota_omision.")
            if not apr or apr.get("tipo") != "omision" or apr.get("decision") != "aprobada":
                fallos.append(f"2d. Etapa `{eid}`: omitida sin aprobacion tipo 'omision' con decision 'aprobada'. 'omitida' no cuenta como aprobada por defecto.")
            elif (apr.get("actor") or {}).get("rol") not in ("PO", "Aprobador designado"):
                fallos.append(f"2d. Etapa `{eid}`: la omision la aprobo '{(apr.get('actor') or {}).get('rol')}'; solo PO o Aprobador designado.")
        # Regla de corte
        if et.get("estado") == "completada_manualmente":
            if et.get("clasificacion_compromiso") != "exito":
                fallos.append(f"2b. [G3-ORQ-RF-04] Etapa `{eid}` marcada completada_manualmente pero su clasificacion es '{et.get('clasificacion_compromiso')}' (solo 'exito').")
            if not (et.get("nota_regla_de_corte") or "").strip():
                fallos.append(f"2c. Etapa `{eid}`: completada_manualmente sin nota_regla_de_corte.")
        # 4. Integridad referencial
        for pid in et.get("preguntas_abiertas", []):
            if pid not in preguntas:
                fallos.append(f"4. Etapa `{eid}` referencia {pid}, ausente de preguntas_abiertas[].")
        for rid in et.get("riesgos", []):
            if rid not in riesgos:
                fallos.append(f"4. Etapa `{eid}` referencia {rid}, ausente de riesgos[].")

    # 3. R3.5
    for aid, a in aprobaciones.items():
        just = (a.get("justificacion") or "").strip()
        if a.get("riesgo") in ("alto", "critico") and len(just) < 10:
            fallos.append(f"3. [R3.5] {aid}: riesgo {a.get('riesgo')} sin justificacion suficiente.")
        if (a.get("riesgo") == "critico" and a.get("decision") == "aprobada"
                and (a.get("actor") or {}).get("rol") not in ("PO", "Aprobador designado")):
            fallos.append(f"3b. [06 §3 N4] {aid}: riesgo critico aprobado por '{(a.get('actor') or {}).get('rol')}'; solo PO o Aprobador designado.")
        if a.get("decision") in ("rechazada", "condicional") and len(just) < 10:
            fallos.append(f"3. [R3.5] {aid}: decision '{a.get('decision')}' sin justificacion.")
        if a.get("tipo") == "regla_de_corte" and len(just) < 10:
            fallos.append(f"3. [R3.5] {aid}: regla_de_corte sin justificacion.")
        if a.get("decision") == "condicional" and not a.get("condiciones"):
            fallos.append(f"3. {aid}: aprobacion condicional sin condiciones[].")

    # 5. Condicion 4, por etapa afectada (1.9.1)
    fallos += reglas_preguntas(estado)

    # 7. Reglas de gobierno de references/03 (1.9.0)
    fallos += reglas_gobierno(estado)

    # 6. Anti-PII y 8. bitacora
    pii = hallazgos_pii(raw, ruta.name)
    if "--cruzado" in argv:
        i = argv.index("--cruzado")
        if i + 1 < len(argv):
            bit = pathlib.Path(argv[i + 1])
            btxt = bit.read_text(encoding="utf-8")
            pii += hallazgos_pii(btxt, bit.name)
            f_bit, ids = bitacora_cruzada(estado, btxt)
            fallos += f_bit
            if not f_bit:
                print(f"[OK] bitacora: {len(ids)} eventos, crecientes, sin reusos y con contadores al dia")

    for p in pii:
        grave.append(f"6. {p}")

    print()
    if fallos:
        print(f"HALLAZGOS ({len(fallos)}):")
        for f in fallos:
            print("  X", f)
    if avisos:
        print(f"AVISOS ({len(avisos)}):")
        for a in avisos:
            print("  !", a)
    if not fallos:
        print("EXPEDIENTE VALIDO: ninguna transicion habilitada sin aprobacion registrada.")
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
