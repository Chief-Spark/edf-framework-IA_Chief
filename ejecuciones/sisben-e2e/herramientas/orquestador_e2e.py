#!/usr/bin/env python3
"""Emulación del Orquestador (edf-orquestador) para la corrida autónoma de SISBEN sin Kiro.

Hace sobre el expediente lo que el Orquestador hace en Kiro, siempre de la misma forma, y deja rastro:
- abrir         prompt 00: expediente desde estado-ingesta.seed.json, BIT-0001, carpetas.
- evento        un evento append-only en bitacora.md; contadores del JSON al día.
- artefacto     prompt 08: registra o cambia el estado de un artefacto (con sha256 al aprobar).
- aprobacion    prompt 03: agrega APR-nnn y su evento de bitácora.
- etapa         cambia estado, ref_aprobacion, transicion_habilitada, evidencia o etapa_actual.
- checklist     prompt 02: checklist_bloqueo de una etapa.
- riesgo        agrega o cambia un RG-nnn.
- vista         regenera estado-ingesta.md desde el JSON (+ evento regeneracion_vista).
- validar       emula el hook validar-expediente: tools/validar-expediente.py --cruzado.
- interaccion   agrega INT-nnn a registro-interacciones.md (cada mensaje a un agente).

Todas las aprobaciones de esta corrida son SIMULADAS (aprobador simulado SIM-DEV-01 / SIM-PO-01).
Uso: python orquestador_e2e.py <subcomando> [opciones]   (ver --help de cada subcomando)
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

CORRIDA = Path(__file__).resolve().parent.parent          # ejecuciones/sisben-e2e
EXP = CORRIDA / "expedientes" / "sisben"
JSON = EXP / "estado-ingesta.json"
BIT = EXP / "bitacora.md"
REG = CORRIDA / "registro-interacciones.md"
RAIZ = CORRIDA.parent.parent                              # raíz del repo
SEED = RAIZ / ".kiro/skills/edf-orquestador/templates/estado-ingesta.seed.json"
VALIDADOR = RAIZ / "tools" / "validar-expediente.py"


def ahora() -> str:
    return datetime.now(ZoneInfo("America/Bogota")).isoformat(timespec="seconds")


def cargar() -> dict:
    return json.loads(JSON.read_text(encoding="utf-8"))


def guardar(e: dict) -> None:
    e["generado_por"]["actualizado_en"] = ahora()
    JSON.write_text(json.dumps(e, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def etapa(e: dict, eid: str) -> dict:
    return next(x for x in e["etapas"] if x["id"] == eid)


def sha256(ruta: Path) -> str:
    if ruta.is_dir():
        h = hashlib.sha256()
        for f in sorted(p for p in ruta.rglob("*") if p.is_file()):
            h.update(f.relative_to(ruta).as_posix().encode())
            h.update(f.read_bytes())
        return h.hexdigest()
    return hashlib.sha256(ruta.read_bytes()).hexdigest()


def nuevo_evento(e: dict, tipo: str, que: str, quien: str, porque: str, resultado: str, etapa_id: str = "—",
                 riesgo: str = "—", artefactos: str = "—", traza: str = "—", corrige: str = "—") -> str:
    n = e["bitacora"]["eventos_totales"] + 1
    bid = f"BIT-{n:04d}"
    filas = [("Qué", que), ("Quién", quien), ("Cuándo", ahora()), ("Por qué", porque), ("Resultado", f"`{resultado}`"),
             ("Etapa", f"`{etapa_id}`" if etapa_id != "—" else "—"), ("Riesgo", riesgo), ("Artefactos", artefactos),
             ("Trazabilidad", traza), ("Corrige", corrige)]
    bloque = [f"## {bid} · `{tipo}`", "", "| Campo | Valor |", "|-------|-------|"]
    bloque += [f"| **{k}** | {str(v).replace('|', '/').replace(chr(10), ' ')} |" for k, v in filas]
    with BIT.open("a", encoding="utf-8") as f:
        f.write("\n" + "\n".join(bloque) + "\n\n---\n")
    e["bitacora"]["eventos_totales"] = n
    e["bitacora"]["ultimo_evento"] = bid
    return bid


# ------------------------------------------------------------------ subcomandos
def cmd_abrir(a) -> None:
    if JSON.exists():
        sys.exit("El expediente ya existe: no se reabre (prompt 00).")
    e = json.loads(SEED.read_text(encoding="utf-8"))
    t = ahora()
    e["generado_por"].update(actualizado_en=t, vista_md_regenerada_en=t)
    e["ingesta"] = {
        "id": "sisben", "nombre": "SISBEN IV",
        "descripcion": "Ingesta mensual de SISBEN IV (DNP) en EDF, de Validation a State Calculation. Corrida autónoma "
                       "con aprobador simulado. Fuentes: hu-sisben.md y spec-sisben.md 1.0.2 (sintéticas).",
        "creada_en": t, "periodo": "202601", "es_ingesta_nueva": True, "fuente_oficial": "mixto",
        "jerarquia_fuente": "Oficial para clasificación socioeconómica; para identidad, por debajo de Registraduría, "
                            "Cámaras de comercio y Migración (HU §3).",
        "business_light_code": "batch", "reuse_pin": False, "epoca_epoc": None, "epoca_activada": False,
        "contrato_archivo": {"patron_nombre": "SISBEN_EXPERIAN_AAAAMM.yyyyMMddHHmmss±ZZZZ.txt", "delimitador": ";",
                             "encoding": "UTF-8", "header": True, "footer": False, "event_type": 30,
                             "num_columnas": 22, "formato": "texto delimitado"},
        "rutas_pipeline": {"entrada": "s3://colombia-data/sisben/preparation/prepared/",
                           "validated": "s3://colombia-data/sisben/validation/validated/",
                           "accepted": "s3://colombia-data/sisben/acceptance/accepted/",
                           "rejects_files": "s3://colombia-data/sisben/rejected/files/",
                           "rejects_records": "s3://colombia-data/sisben/rejected/records/",
                           "curated": "s3://colombia-data/sisben/curation/curated/",
                           "generacion_epoc": "s3://colombia-data/sisben/statecalc/calculated/",
                           "joblogs": "s3://colombia-data/sisben/reports/logs/"},
    }
    for x in e["etapas"]:
        for art in x.get("artefactos", []):
            art["nombre"] = art["nombre"].replace("<Fuente>", "SISBEN")
    for d in ("analisis", "insumos", "propuestas"):
        (EXP / d).mkdir(parents=True, exist_ok=True)
    plantilla = (RAIZ / ".kiro/skills/edf-orquestador/templates/bitacora.template.md").read_text(encoding="utf-8")
    cabecera = plantilla.split("## BIT-0001")[0].replace("{{ingesta.nombre}}", "SISBEN IV") \
        .replace("{{ingesta.id}}", "sisben").replace("{{version}}", "1.9.0")
    BIT.write_text(cabecera.rstrip() + "\n", encoding="utf-8")
    e["bitacora"].update(eventos_totales=0, ultimo_evento=None)
    nuevo_evento(e, "inicio_expediente",
                 "Creación del expediente de SISBEN IV desde la semilla: 8 etapas, 0 preguntas abiertas, 2 riesgos.",
                 "Orquestador (emulado) · `ORQ-E2E`", "Apertura de la ingesta. Compuerta de la etapa 0 superada "
                 "(HU y spec presentes). Requisitos R2.1 a R2.3.", "aplicado", "analisis",
                 traza="prompt 00 · R2.1, R2.3")
    guardar(e)
    print("Expediente abierto:", JSON)


def cmd_evento(a) -> None:
    e = cargar()
    print(nuevo_evento(e, a.tipo, a.que, a.quien, a.porque, a.resultado, a.etapa or "—", a.riesgo or "—",
                       a.artefactos or "—", a.traza or "—", a.corrige or "—"))
    guardar(e)


def cmd_artefacto(a) -> None:
    e = cargar()
    et = etapa(e, a.etapa)
    art = next((x for x in et["artefactos"] if x["id"] == a.id), None)
    if art is None:
        art = {"id": a.id, "tipo": a.tipo or "documento", "nombre": a.nombre or a.id, "ruta": a.ruta,
               "obligatorio": True, "estado_artefacto": "ausente"}
        et["artefactos"].append(art)
    if a.ruta:
        art["ruta"] = a.ruta
    if a.nombre:
        art["nombre"] = a.nombre
    if a.riesgo:
        art["riesgo"] = a.riesgo
    art["estado_artefacto"] = a.estado
    if a.prevalidacion:
        art["prevalidacion"] = json.loads(a.prevalidacion)
    if a.contexto:
        art["contexto_precargado"] = json.loads(a.contexto)
    if a.hash_de:
        art["hash"] = "sha256:" + sha256(Path(a.hash_de))
    guardar(e)
    print(a.id, art["estado_artefacto"], art.get("hash", ""))


def cmd_aprobacion(a) -> None:
    e = cargar()
    aid = f"APR-{len(e['aprobaciones']) + 1:03d}"
    rol, ident = ("PO", "SIM-PO-01") if a.po else ("Desarrollador", "SIM-DEV-01")
    just = f"[SIMULADO · aprobador {ident}] {a.justificacion}"
    bid = nuevo_evento(e, "aprobacion" if a.decision == "aprobada" else ("rechazo" if a.decision == "rechazada"
                                                                          else "aprobacion_condicional"),
                       f"{aid} ({a.tipo}): {a.que}", f"{rol} (simulado) · `{ident}`", just, "aplicado",
                       a.desde or a.etapa_ref or "—", a.riesgo, ", ".join(a.artefactos) if a.artefactos else "—",
                       f"prompt 03 · {a.traza or 'R3.2'}")
    e["aprobaciones"].append({
        "id": aid, "tipo": a.tipo, "desde_etapa": a.desde, "hacia_etapa": a.hacia, "artefactos": a.artefactos or [],
        "decision": a.decision, "actor": {"rol": rol, "identificador": ident}, "timestamp": ahora(),
        "riesgo": a.riesgo, "nivel_friccion": a.friccion, "justificacion": just, "condiciones": a.condiciones or [],
        "resultado": "aplicado", "ref_bitacora": bid})
    guardar(e)
    print(aid, bid)


def cmd_etapa(a) -> None:
    e = cargar()
    et = etapa(e, a.id)
    if a.estado:
        et["estado"] = a.estado
        if a.estado == "en_progreso" and not et.get("iniciada_en"):
            et["iniciada_en"] = ahora()
        if a.estado in ("aprobada", "completada_manualmente"):
            et["cerrada_en"] = ahora()
    if a.ref_aprobacion:
        et["ref_aprobacion"] = a.ref_aprobacion
    if a.transicion is not None:
        et["transicion_habilitada"] = a.transicion == "true"
    if a.riesgo_evaluado:
        et["riesgo_evaluado"] = a.riesgo_evaluado
    if a.friccion:
        et["nivel_friccion"] = a.friccion
    if a.agregar_riesgo and a.agregar_riesgo not in et.setdefault("riesgos", []):  # HC-04: sin duplicados
        et["riesgos"].append(a.agregar_riesgo)
    if a.etapa_actual:
        e["etapa_actual"] = a.id
    bit = "—"
    if a.porque:  # HC-03: un cambio de estado con motivo deja evento en la bitácora
        bit = nuevo_evento(e, a.evento_tipo, f"Etapa `{a.id}` → estado `{et['estado']}`"
                           f" · transicion_habilitada={et['transicion_habilitada']}", a.quien, a.porque, "aplicado",
                           a.id, traza="R3.2 · prompt 02", corrige=a.corrige or "—")
    guardar(e)
    print(a.id, et["estado"], et["transicion_habilitada"], bit)


def cmd_checklist(a) -> None:
    e = cargar()
    c = json.loads(a.json)
    c["evaluado_en"] = ahora()
    etapa(e, a.etapa)["checklist_bloqueo"] = c
    guardar(e)
    print(a.etapa, c["resultado"])


def cmd_riesgo(a) -> None:
    e = cargar()
    r = next((x for x in e["riesgos"] if x["id"] == a.id), None) if a.id else None
    if r is None:
        rid = a.id or f"RG-{len(e['riesgos']) + 1:03d}"
        r = {"id": rid, "descripcion": a.descripcion, "nivel": a.nivel, "etapa_afectada": a.etapa,
             "mitigacion": a.mitigacion, "estado": a.estado or "abierto"}
        e["riesgos"].append(r)
        if a.etapa:
            etapa(e, a.etapa).setdefault("riesgos", []).append(rid)
    else:
        for k in ("descripcion", "nivel", "mitigacion", "estado"):
            if getattr(a, k):
                r[k] = getattr(a, k)
        if a.etapa:
            r["etapa_afectada"] = a.etapa
    bit = nuevo_evento(e, "riesgo", f"{r['id']} ({r['nivel']}, {r.get('etapa_afectada') or '—'}): {r['descripcion']}",
                       a.quien or "Orquestador (emulado) · `ORQ-E2E`", a.porque or "Registro o cambio de riesgo",
                       "aplicado", r.get("etapa_afectada") or "—", riesgo=r["nivel"], traza="R3.4 · riesgos")
    guardar(e)
    print(r["id"], r["estado"], bit)


def cmd_pregunta(a) -> None:
    e = cargar()
    q = next((x for x in e["preguntas_abiertas"] if x["id"] == a.id), None) if a.id else None
    if q is None:
        qid = a.id or f"PA-{len(e['preguntas_abiertas']) + 1:03d}"
        q = {"id": qid, "pregunta": a.pregunta, "origen": a.origen, "etapa_afectada": a.etapa,
             "bloqueante": a.bloqueante == "true", "estado": "abierta", "abierta_en": ahora(),
             "resuelta_en": None, "respuesta": None, "ref_bitacora": None}
        e["preguntas_abiertas"].append(q)
        for eid in a.etapas_bloqueadas or []:
            etapa(e, eid).setdefault("preguntas_abiertas", []).append(qid)
        q["ref_bitacora"] = nuevo_evento(e, "pregunta_abierta", f"{qid}: {a.pregunta}", a.quien, a.origen or "—",
                                         "aplicado", a.etapa or "—", traza="R3.1 · condición 4")
    else:
        q.update(estado=a.estado or "resuelta", respuesta=a.respuesta, resuelta_en=ahora())
        q["ref_bitacora"] = nuevo_evento(e, "cierre_pregunta", f"{q['id']} {q['estado']}: {a.respuesta}", a.quien,
                                         a.origen or "Respuesta del aprobador simulado con cita a las fuentes.",
                                         "aplicado", q.get("etapa_afectada") or "—", traza="R3.1 · condición 4")
    guardar(e)
    print(q["id"], q["estado"], q["ref_bitacora"])


def cmd_vista(a) -> None:
    e = cargar()
    t = ahora()
    # El evento se registra ANTES de escribir la vista, para que sus contadores coincidan con el JSON.
    nuevo_evento(e, "regeneracion_vista", "Regeneración de estado-ingesta.md desde el JSON.",
                 "Orquestador (emulado) · `ORQ-E2E`", "La vista se regenera tras cada cambio de estado (R2.2).",
                 "aplicado", e["etapa_actual"], traza="R2.2")
    i = e["ingesta"]
    L = [f"# Estado de la ingesta — {i['nombre']} (`{i['id']}`)", "",
         f"> Vista regenerada desde `estado-ingesta.json` el {t}. No se edita a mano: la fuente de verdad es el JSON.",
         "> **Corrida autónoma: todas las aprobaciones son SIMULADAS** (SIM-DEV-01, SIM-PO-01).", "",
         "| Campo | Valor |", "|---|---|",
         f"| Etapa actual | `{e['etapa_actual']}` |", f"| Fuente oficial | {i['fuente_oficial']} |",
         f"| reusePIN | `{str(i['reuse_pin']).lower()}` |", f"| Business Light Code | {i['business_light_code']} |",
         f"| Bitácora | {e['bitacora']['eventos_totales']} eventos, último {e['bitacora']['ultimo_evento']} |", "",
         "## Etapas", "", "| # | Etapa | Estado | Riesgo | Artefactos aprobados | Aprobación | Transición |",
         "|---|---|---|---|---|---|---|"]
    for x in e["etapas"]:
        arts = x.get("artefactos", [])
        ok = sum(1 for z in arts if z.get("estado_artefacto") == "aprobado")
        L.append(f"| {x['orden']} | {x['nombre']} | `{x['estado']}` | {x.get('riesgo_evaluado') or x['riesgo_tipico']} "
                 f"| {ok}/{len(arts)} | {x.get('ref_aprobacion') or '—'} | {'✅' if x['transicion_habilitada'] else '🔒'} |")
    L += ["", "## Artefactos", "", "| Etapa | ID | Nombre | Estado | Hash |", "|---|---|---|---|---|"]
    for x in e["etapas"]:
        for z in x.get("artefactos", []):
            L.append(f"| {x['id']} | {z['id']} | {z['nombre']} | `{z['estado_artefacto']}` | {(z.get('hash') or '—')[:19]} |")
    L += ["", "## Aprobaciones", "", "| ID | Tipo | Desde → hacia | Decisión | Actor | Riesgo | Bitácora |",
          "|---|---|---|---|---|---|---|"]
    for p in e["aprobaciones"]:
        L.append(f"| {p['id']} | {p['tipo']} | {p.get('desde_etapa') or '—'} → {p.get('hacia_etapa') or '—'} | "
                 f"{p['decision']} | {p['actor']['rol']} `{p['actor']['identificador']}` | {p['riesgo']} | {p['ref_bitacora']} |")
    L += ["", "## Preguntas abiertas", ""]
    L += [f"- {q['id']} ({q['estado']}, {'bloqueante' if q['bloqueante'] else 'no bloqueante'}): {q['pregunta']}"
          for q in e["preguntas_abiertas"]] or ["Ninguna."]
    L += ["", "## Riesgos", "", "| ID | Nivel | Etapa | Estado | Descripción |", "|---|---|---|---|---|"]
    L += [f"| {r['id']} | {r['nivel']} | {r.get('etapa_afectada') or '—'} | {r['estado']} | {r['descripcion']} |"
          for r in e["riesgos"]]
    (EXP / "estado-ingesta.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    e["generado_por"]["vista_md_regenerada_en"] = t
    guardar(e)
    print("vista regenerada")


def cmd_validar(a) -> int:
    r = subprocess.run([sys.executable, str(VALIDADOR), str(JSON), "--cruzado", str(BIT)], capture_output=True, text=True)
    print(r.stdout.strip().splitlines()[-1] if r.stdout.strip() else r.stderr)
    if r.returncode:
        print(r.stdout)
    return r.returncode


def cmd_interaccion(a) -> None:
    if not REG.exists():
        REG.write_text("# Registro de interacciones de la corrida autónoma\n\nCada mensaje enviado a un agente "
                       "(analista, generador, auditor, aprobador simulado), con lo que se le pidió, lo que respondió y "
                       "la decisión que se tomó. `INT-nnn` se enlaza con el evento `BIT-nnnn` del expediente.\n",
                       encoding="utf-8")
    n = REG.read_text(encoding="utf-8").count("\n## INT-") + 1
    bloque = [f"## INT-{n:03d} · {a.rol} · `{a.etapa}`", "", f"- **Cuándo:** {ahora()}", f"- **Pedido:** {a.pedido}",
              f"- **Respuesta:** {a.respuesta}", f"- **Decisión:** {a.decision}", f"- **Bitácora:** {a.bit or '—'}", ""]
    with REG.open("a", encoding="utf-8") as f:
        f.write("\n" + "\n".join(bloque))
    print(f"INT-{n:03d}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("abrir")
    p = sub.add_parser("evento")
    for k in ("tipo", "que", "quien", "porque", "resultado"):
        p.add_argument(f"--{k}", required=True)
    for k in ("etapa", "riesgo", "artefactos", "traza", "corrige"):
        p.add_argument(f"--{k}")
    p = sub.add_parser("artefacto")
    p.add_argument("--etapa", required=True); p.add_argument("--id", required=True); p.add_argument("--estado", required=True)
    for k in ("ruta", "nombre", "tipo", "riesgo", "prevalidacion", "contexto"):
        p.add_argument(f"--{k}")
    p.add_argument("--hash-de")
    p = sub.add_parser("aprobacion")
    p.add_argument("--tipo", required=True); p.add_argument("--decision", default="aprobada")
    p.add_argument("--riesgo", required=True); p.add_argument("--friccion", default="ligera")
    p.add_argument("--justificacion", required=True); p.add_argument("--que", required=True)
    p.add_argument("--desde"); p.add_argument("--hacia"); p.add_argument("--etapa-ref"); p.add_argument("--traza")
    p.add_argument("--artefactos", nargs="*"); p.add_argument("--condiciones", nargs="*")
    p.add_argument("--po", action="store_true", help="aprueba el PO simulado (State Calculation, N4)")
    p = sub.add_parser("etapa")
    p.add_argument("--id", required=True); p.add_argument("--estado"); p.add_argument("--ref-aprobacion")
    p.add_argument("--transicion", choices=["true", "false"]); p.add_argument("--riesgo-evaluado")
    p.add_argument("--friccion"); p.add_argument("--agregar-riesgo"); p.add_argument("--etapa-actual", action="store_true")
    p.add_argument("--porque"); p.add_argument("--corrige")
    p.add_argument("--evento-tipo", default="propuesta")
    p.add_argument("--quien", default="Orquestador (emulado) · `ORQ-E2E`")
    p = sub.add_parser("checklist")
    p.add_argument("--etapa", required=True); p.add_argument("--json", required=True)
    p = sub.add_parser("riesgo")
    for k in ("id", "descripcion", "nivel", "etapa", "mitigacion", "estado", "quien", "porque"):
        p.add_argument(f"--{k}")
    p = sub.add_parser("pregunta")
    for k in ("id", "pregunta", "origen", "etapa", "estado", "respuesta"):
        p.add_argument(f"--{k}")
    p.add_argument("--bloqueante", choices=["true", "false"], default="true")
    p.add_argument("--etapas-bloqueadas", nargs="*")
    p.add_argument("--quien", default="Orquestador (emulado) · `ORQ-E2E`")
    sub.add_parser("vista")
    sub.add_parser("validar")
    p = sub.add_parser("interaccion")
    for k in ("rol", "etapa", "pedido", "respuesta", "decision"):
        p.add_argument(f"--{k}", required=True)
    p.add_argument("--bit")
    a = ap.parse_args()
    r = globals()[f"cmd_{a.cmd}"](a)
    return r if isinstance(r, int) else 0


if __name__ == "__main__":
    sys.exit(main())
