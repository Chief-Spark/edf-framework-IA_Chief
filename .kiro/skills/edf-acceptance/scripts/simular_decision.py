#!/usr/bin/env python3
"""Simulador en Python de la decisión de Acceptance (sin Spark ni JAR del framework).

Reproduce, para el SUBCONJUNTO de expresiones que genera este skill, la semántica verificada del
motor SparkAcceptor:
  - E4: rechazo automático si el nombre contiene errorInHeader/Footer/FileName/HistoricAttribute, o
        errorInEmptyFileBody y accept no contiene isEmptyBodyFile.
  - E8: reject en sintaxis nueva anyOf/allOf sobre fileConditions; una condición que falla vale false.
  - E9: accept en sintaxis antigua; si falla vale false (archivo pendiente).
  - E10: recordAcceptance con errorlevel(Not)In / errorcode(Not)In unidos por and/or; comparación
         por SUBCADENA sobre valores unidos por coma; si falla, el registro se ACEPTA.
  - average("qs.X"): promedio de X sobre los eventos que lo traen; sin ninguno -> error (null).
Contrato de entrada (AC-ACC-05, regla "QS por registro binario"): cada quality score declarado vale
0 o 1 en cada evento del fixture. Si no, el simulador no decide: lanza ContratoError y `main` sale
con código 1, igual que el generador (render_templates.py) rechaza esa spec.
No sustituye las pruebas ACC-TPL-05 contra los parsers reales; sirve para revisar la propuesta y
para el resumen esperado del fixture. Uso: python simular_decision.py --spec <spec.yaml>

Comparación con lo esperado (HF-24, AUD-A-08): cada resultado lleva `coincide` y `diferencias` frente a
`decision_esperada`, `reason_code_esperado` (los reasonCode registrados, unidos por coma; en un rechazo automático
solo se exige vacío si customReasonForAutomaticRejectionEnabled es false, como ACC-TPL-05) y el `esperado_registro`
de cada evento. Si la spec declara `fixture.umbrales_alternativos` (HF-23, AUD-A-01), evalúa además cada caso con
accept y las condiciones con el umbral sustituido, sobre registros sintéticos con QS binario. Imprime la lista JSON
en stdout y un resumen en stderr; sale con código 1 si algún resultado no coincide.
Diferencia con el log del motor: aquí `reason_codes` son solo los de las condiciones isLogged que dan true; el
motor registra todas las condiciones isLogged con su resultado (SparkAcceptor.scala:335).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import yaml

AUTO_MARKERS = ("errorInHeader", "errorInFooter", "errorInFileName", "errorInHistoricAttribute")
REC_ATOM = re.compile(r'(errorlevelNotIn|errorlevelIn|errorcodeNotIn|errorcodeIn)\("((?:[^"\\]|\\.)*)"\)')
AGG_RE = re.compile(r'^\s*average\("qs\.([A-Za-z0-9_]+)"\)\s*(>=|<=|>|<|==)\s*([0-9.]+)\s*$')
STMT_RE = re.compile(r"^\s*(anyOf|allOf)\(([A-Za-z0-9_, ]+)\)\s*$")


class EvalError(Exception):
    pass


class ContratoError(ValueError):
    """La spec rompe el contrato de entrada desde Validation (AC-ACC-05)."""


def _binario(value) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and value in (0, 1)


def qs_no_binarios(spec: dict, archivos: list[dict] | None = None) -> list[str]:
    """Regla "QS por registro binario" (AC-ACC-05, plan #2): un mensaje por score declarado que no es 0 ni 1."""
    declarados = set(spec.get("diagnostico_validation", {}).get("quality_scores", []))
    declarados.add(spec.get("calidad", {}).get("quality_score"))
    errores = []
    for archivo in spec["fixture"]["archivos"] if archivos is None else archivos:
        for event in archivo["eventos"]:
            for q in event.get("qualityScores", []):
                if q.get("name") in declarados and not _binario(q.get("score")):
                    errores.append(f"QS por registro binario: {q.get('name')} = {q.get('score')!r} en "
                                   f"{archivo['directorio']} ({event.get('eventId')}); debe ser 0 o 1 (AC-ACC-05)")
    return errores


def _exigir_qs_binarios(spec: dict, archivos: list[dict] | None = None) -> None:
    errores = qs_no_binarios(spec, archivos)
    if errores:
        raise ContratoError("la spec no cumple el contrato de entrada:\n  - " + "\n  - ".join(errores))


def eval_record(expr: str, event: dict) -> bool:
    """Evalúa recordAcceptance; ante cualquier error devuelve True (default del motor)."""
    try:
        return _eval_record(expr, event)
    except EvalError:
        return True


def _eval_record(expr: str, event: dict) -> bool:
    codes = ",".join(e["errorCodeValue"] for e in event["errorCodes"])
    levels = ",".join(e["errorLevel"] for e in event["errorCodes"])

    def atom(m: re.Match) -> str:
        op, arg = m.group(1), m.group(2).split(",")
        hay = levels if op.startswith("errorlevel") else codes
        res = all((v not in hay) if op.endswith("NotIn") else (v in hay) for v in arg)
        return " True " if res else " False "

    reduced = REC_ATOM.sub(atom, expr)
    if not re.fullmatch(r"(\s*(True|False|and|or|\(|\))\s*)+", reduced):
        raise EvalError(f"fuera del subconjunto simulado: {expr}")
    return bool(eval(reduced.replace("and", " and ").replace("or", " or "), {"__builtins__": {}}))  # noqa: S307


def eval_aggregate(expr: str, events: list[dict]) -> bool:
    m = AGG_RE.match(expr)
    if not m:
        raise EvalError(f"agregado fuera del subconjunto simulado: {expr}")
    name, op, num = m.group(1), m.group(2), float(m.group(3))
    scores = [q["score"] for e in events for q in e["qualityScores"] if q["name"] == name]
    if not scores:
        raise EvalError(f"ningún evento trae qs.{name}: average es null")
    avg = sum(scores) / len(scores)
    return {">=": avg >= num, "<=": avg <= num, ">": avg > num, "<": avg < num, "==": avg == num}[op]


def eval_reject(stmt: str, conditions: dict, events: list[dict]) -> tuple[bool, list[str]]:
    m = STMT_RE.match(stmt)
    if not m:
        raise EvalError(f"reject fuera de la sintaxis nueva simulada: {stmt}")
    results, logged = [], []
    for name in [n.strip() for n in m.group(2).split(",")]:
        cond = conditions.get(name)
        if cond is None:
            continue  # el motor ignora condiciones no definidas (y lo registra como error)
        try:
            res = eval_aggregate(cond["expresion"], events)
        except EvalError:
            res = False
        results.append(res)
        if res and cond["isLogged"]:
            logged.append(cond["reasonCode"])
    decided = any(results) if m.group(1) == "anyOf" else (bool(results) and all(results))
    return decided, logged


def decide_file(spec: dict, archivo: dict) -> dict:
    _exigir_qs_binarios(spec, [archivo])
    accept = spec["fileActions"]["accept"]
    name = archivo["directorio"]
    events = archivo["eventos"]
    if any(mk in name for mk in AUTO_MARKERS) or ("errorInEmptyFileBody" in name and "isEmptyBodyFile" not in accept):
        return {"decision": "rechazo_automatico", "reason_codes": [], "aceptados": 0, "rechazados": 0}
    conditions = {c["nombre"]: c for c in spec["condiciones"]}
    rejected, logged = eval_reject(spec["fileActions"]["reject"], conditions, events)
    if rejected:
        return {"decision": "rechazado", "reason_codes": logged, "aceptados": 0, "rechazados": 0}
    try:
        accepted = eval_aggregate(accept, events)
    except EvalError:
        accepted = False
    if not accepted:
        return {"decision": "pendiente", "reason_codes": [], "aceptados": 0, "rechazados": 0}
    flags = [eval_record(spec["registro"]["recordAcceptance"], e) for e in events]
    return {"decision": "aceptado", "reason_codes": [], "aceptados": sum(flags), "rechazados": len(flags) - sum(flags)}


def simulate(spec: dict) -> list[dict]:
    _exigir_qs_binarios(spec)  # informa todos los scores no binarios de una vez
    return [{"directorio": a["directorio"], **decide_file(spec, a)} for a in spec["fixture"]["archivos"]]


def diferencias_archivo(spec: dict, archivo: dict, resultado: dict) -> list[str]:
    """HF-24 (AUD-A-08): lo que difiere entre la simulación y lo esperado del fixture (vacío = coincide)."""
    out = []
    if resultado["decision"] != archivo["decision_esperada"]:
        out.append(f"decisión {resultado['decision']} (esperada {archivo['decision_esperada']})")
    registrados = ",".join(resultado["reason_codes"])
    if resultado["decision"] == "rechazo_automatico":
        # Como ACC-TPL-05: con customReason… el motor evalúa reject sobre el marcador y puede registrar un reasonCode.
        reason_ok = spec["rechazo_automatico"]["customReasonForAutomaticRejectionEnabled"] or not archivo["reason_code_esperado"]
    else:
        reason_ok = registrados == archivo["reason_code_esperado"]
    if not reason_ok:
        out.append(f"reasonCode {registrados!r} (esperado {archivo['reason_code_esperado']!r})")
    for event in archivo["eventos"]:
        obtenido = eval_record(spec["registro"]["recordAcceptance"], event)
        if obtenido != event["esperado_registro"]:
            out.append(f"registro {event['eventId']}: {obtenido} (esperado {event['esperado_registro']})")
    return out


def con_umbral(expr: str, umbral: str, alternativo: str) -> str:
    """HF-23: misma sustitución que render_templates.con_umbral y conUmbral de ACC-TPL-05."""
    return re.sub(r"(?<![\w.])" + re.escape(umbral) + r"(?![\w.])", lambda _: alternativo, expr)


def simular_umbrales_alternativos(spec: dict) -> list[dict]:
    """HF-23 (AUD-A-01): cada caso de fixture.umbrales_alternativos con accept y las condiciones evaluados con el
    umbral sustituido, sobre registros_totales eventos sintéticos (registros_validos con QS 1 y el resto con 0).
    Sin la clave, lista vacía."""
    umbral = spec["calidad"]["umbral"]
    qs = spec["calidad"]["quality_score"]
    out = []
    for alt in spec["fixture"].get("umbrales_alternativos") or []:
        valor = alt["umbral"]
        variante = {**spec,
                    "condiciones": [{**c, "expresion": con_umbral(c["expresion"], umbral, valor)} for c in spec["condiciones"]],
                    "fileActions": {**spec["fileActions"], "accept": con_umbral(spec["fileActions"]["accept"], umbral, valor)}}
        for caso in alt["casos"]:
            eventos = [{"eventId": f"SINT-UMBRAL-{i}", "qualityScores": [{"name": qs, "score": 1 if i <= caso["registros_validos"] else 0}],
                        "errorCodes": []} for i in range(1, caso["registros_totales"] + 1)]
            conditions = {c["nombre"]: c for c in variante["condiciones"]}
            rejected, logged = eval_reject(variante["fileActions"]["reject"], conditions, eventos)
            if rejected:
                decision = "rechazado"
            else:
                try:
                    decision = "aceptado" if eval_aggregate(variante["fileActions"]["accept"], eventos) else "pendiente"
                except EvalError:
                    decision = "pendiente"
            diferencias = []
            if decision != caso["esperado"]:
                diferencias.append(f"decisión {decision} (esperada {caso['esperado']})")
            esperado_rc = caso.get("reason_code_esperado", "")
            if ",".join(logged) != esperado_rc:
                diferencias.append(f"reasonCode {','.join(logged)!r} (esperado {esperado_rc!r})")
            out.append({"umbral_alternativo": valor, "caso": caso["id"],
                        "fqs": round(caso["registros_validos"] / caso["registros_totales"], 6),
                        "decision": decision, "reason_codes": logged,
                        "coincide": not diferencias, "diferencias": diferencias})
    return out


def simular_y_comparar(spec: dict) -> list[dict]:
    """simulate + comparación con lo esperado (HF-24) + umbrales alternativos (HF-23)."""
    resultados = []
    for archivo, resultado in zip(spec["fixture"]["archivos"], simulate(spec)):
        diferencias = diferencias_archivo(spec, archivo, resultado)
        resultados.append({**resultado, "coincide": not diferencias, "diferencias": diferencias})
    return resultados + simular_umbrales_alternativos(spec)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Simula la decisión de Acceptance sobre el fixture")
    parser.add_argument("--spec", required=True, type=Path)
    args = parser.parse_args(argv)
    spec = yaml.safe_load(args.spec.read_text(encoding="utf-8"))
    try:
        result = simular_y_comparar(spec)
    except ContratoError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    archivos = [r for r in result if "directorio" in r]
    umbrales = [r for r in result if "umbral_alternativo" in r]
    for r in result:
        if not r["coincide"]:
            quien = r.get("directorio") or f"umbral {r['umbral_alternativo']} caso {r['caso']}"
            print(f"NO COINCIDE  {quien}: " + "; ".join(r["diferencias"]), file=sys.stderr)
    resumen = f"{sum(r['coincide'] for r in archivos)}/{len(archivos)} archivos coinciden con lo esperado"
    if umbrales:
        resumen += f"; {sum(r['coincide'] for r in umbrales)}/{len(umbrales)} casos de umbral alternativo coinciden"
    print(resumen, file=sys.stderr)
    return 0 if all(r["coincide"] for r in result) else 1


if __name__ == "__main__":
    sys.exit(main())
