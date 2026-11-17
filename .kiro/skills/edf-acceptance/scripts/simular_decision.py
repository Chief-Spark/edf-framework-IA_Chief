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
con código 1, igual que el generador (render-templates.jar) rechaza esa spec.
No sustituye las pruebas ACC-TPL-05 contra los parsers reales; sirve para revisar la propuesta y
para el resumen esperado del fixture. Uso: python simular_decision.py --spec <spec.yaml>
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


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Simula la decisión de Acceptance sobre el fixture")
    parser.add_argument("--spec", required=True, type=Path)
    args = parser.parse_args(argv)
    spec = yaml.safe_load(args.spec.read_text(encoding="utf-8"))
    try:
        result = simulate(spec)
    except ContratoError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
