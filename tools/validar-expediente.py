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
  5. Condicion 4: ninguna etapa aprobada con preguntas bloqueantes abiertas.
  2d. Etapa omitida: solo con nota_omision y aprobacion tipo 'omision' del PO o del Aprobador designado.
  6. Heuristica anti-PII sobre el JSON (y la bitacora con --cruzado).
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


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    ruta = pathlib.Path(sys.argv[1])
    raw = ruta.read_text(encoding="utf-8")
    estado = json.loads(raw)
    fallos, avisos = [], []

    # 1. Esquema
    try:
        import jsonschema
        jsonschema.validate(estado, json.loads(SCHEMA.read_text(encoding="utf-8")))
        print("[OK] 1. Valida contra el esquema", estado.get("schema_version"))
    except ImportError:
        avisos.append("1. `jsonschema` no instalado: `pip install jsonschema`. Validacion de esquema OMITIDA.")
    except Exception as e:
        fallos.append(f"1. No valida contra el esquema: {str(e).splitlines()[0]}")

    aprobaciones = {a["id"]: a for a in estado.get("aprobaciones", [])}
    preguntas = {p["id"]: p for p in estado.get("preguntas_abiertas", [])}
    riesgos = {r["id"] for r in estado.get("riesgos", [])}
    bloqueantes_abiertas = {
        pid for pid, p in preguntas.items()
        if p.get("bloqueante") and p.get("estado") in ("abierta", "en_consulta")
    }

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
        # 5. Condicion 4
        if et.get("estado") in ("aprobada", "completada_manualmente"):
            pend = sorted(set(et.get("preguntas_abiertas", [])) & bloqueantes_abiertas)
            if pend:
                fallos.append(f"5. [cond. 4] Etapa `{eid}` aprobada con preguntas bloqueantes abiertas: {', '.join(pend)}.")
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

    # 6. Anti-PII
    pii = hallazgos_pii(raw, ruta.name)
    if "--cruzado" in sys.argv:
        i = sys.argv.index("--cruzado")
        if i + 1 < len(sys.argv):
            bit = pathlib.Path(sys.argv[i + 1])
            btxt = bit.read_text(encoding="utf-8")
            pii += hallazgos_pii(btxt, bit.name)
            ids = re.findall(r"^##\s+(BIT-\d{4,})", btxt, re.M)
            if len(ids) != len(set(ids)):
                fallos.append("V-2. La bitacora reusa identificadores BIT-nnnn.")
            refs = {a.get("ref_bitacora") for a in aprobaciones.values() if a.get("ref_bitacora")}
            faltan = sorted(refs - set(ids))
            if faltan:
                fallos.append(f"V-3. Aprobaciones referencian eventos ausentes de la bitacora: {', '.join(faltan)}.")
            print(f"[OK] bitacora: {len(ids)} eventos, sin reusos" if len(ids) == len(set(ids)) else "")

    for p in pii:
        avisos.append(f"6. {p}")

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
