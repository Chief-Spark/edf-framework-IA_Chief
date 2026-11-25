#!/usr/bin/env python3
"""Verifica los artefactos de Synthesis y State Calculation contra reglas del motor (1.9.0, auditoría).

Complementa los verificadores PowerShell de cada skill (`evals/verificar-artefacto.ps1`) con las reglas que
encontró la auditoría y que el motor no reporta como error claro:

  synthesis   GDD (domaindictionary.json) y bloque HOCON oxygen.stage.consistencyCheck
              - GDD: JSON sin comentarios, `version` presente, nombres únicos, sin campos que inyecta el motor
                (inferredAsDeleted, shouldDelete, fileTag y los EUC subscriberRelationStartDate,
                subscriberRelationEndDate, firstEventDate), `handler` solo EUC o LAP (LFP es la ausencia de la
                clave), sin claves que la versión 2 ignora (p. ej. `fieldType`, `type`, `nullable`).
              - HOCON, modo propio (`--modo propio`, por defecto): raíz oxygen.stage.consistencyCheck, nunca
                oxygen.stage.synthesis, `customRulesFile` presente y no null (el motor hace `.head`, AUD-S1), sin
                {{huecos}}.
              - HOCON, modo Colombia (`--modo colombia`, HF-68): la configuración de Synthesis es compartida (una
                corrida lee un solo GDD, un solo diccionario y un solo customRulesFile y procesa todo el HC de
                Colombia, SparkConsistencyChecker.scala:241-245), así que el bloque de la ingesta es SOLO
                comentarios: ninguna línea activa, sin {{huecos}} y una línea `VERIFICAR <ruta HOCON completa>` por
                cada clave de CLAVES_VERIFICAR_COLOMBIA. No se exigen la raíz ni customRulesFile.
  statecalc   statecalc-calculation-local.json (y opcionalmente statecalc-foundation-local.json)
              - `type` en {AGGREGATES, MOST_RECENT_VALUE, DISTINCT, ALL}; AGGREGATES en mayúsculas exactas
                (el validador del motor compara literal, AUD-SC2); sin `grouping` anidado.
              - AGGREGATES: `aggregates[]` no vacío, `function` en MAYÚSCULAS de {SUM, MIN, MAX, COUNT, AVERAGE},
                y `select` solo con `groupBy` y contenido en él (OXY-SC-2 y OXY-SC-3 del motor).
              - Con --foundation: cada campo de select/groupBy/aggregates[].field existe en fields[] de Foundation
                o lo inyecta el motor.

Uso:
    python tools/verificar-artefactos-etapa.py synthesis --gdd domaindictionary.json --hocon bloque.conf
    python tools/verificar-artefactos-etapa.py synthesis --modo colombia --gdd domaindictionary.json --hocon bloque.conf
    python tools/verificar-artefactos-etapa.py statecalc --calculation calc.json [--foundation foundation.json]
Salida: 0 sin hallazgos, 1 con hallazgos, 2 error de uso o archivo ilegible.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

INYECTADOS_GDD = {"inferredAsDeleted", "shouldDelete", "fileTag",
                  "subscriberRelationStartDate", "subscriberRelationEndDate", "firstEventDate"}
CLAVES_V2 = {"name", "handler", "periodicity", "complexTypeDefinition", "dataType", "synthesizer", "description",
             "section", "enabled"}
TIPOS_SECCION = {"AGGREGATES", "MOST_RECENT_VALUE", "DISTINCT", "ALL"}
FUNCIONES = {"SUM", "MIN", "MAX", "COUNT", "AVERAGE"}
# HF-68 (AUD-S-03): claves compartidas que el motor lee y que en modo Colombia la ingesta solo verifica.
# Las de `.get` sin valor por defecto (dateType, fieldHandlerHashFunction, orchestration, datasetFormat y, con
# checkFileTagEnabled = true, historicAttributeArgs) hacen fallar la etapa si faltan en el conf compartido.
CLAVES_VERIFICAR_COLOMBIA = (
    "oxygen.stage.consistencyCheck.paths.dictionaryFile",          # dictionaryFile(0), :242
    "oxygen.stage.consistencyCheck.paths.domainDictionaryFile",    # domainDictionaryFile.head, :241
    "oxygen.stage.consistencyCheck.paths.customRulesFile",         # customRulesFile.head, :245 (nunca null)
    "oxygen.stage.consistencyCheck.dateType",                      # dateType.get, :47
    "oxygen.stage.consistencyCheck.checkFileTagEnabled",           # :48
    "oxygen.stage.consistencyCheck.eventTypeList",                 # listFileTypes, :46 (tipos de archivo)
    "oxygen.stage.consistencyCheck.historicAttributeArgs",         # .get con checkFileTagEnabled = true, :122
    "oxygen.stage.consistencyCheck.fieldHandlerHashFunction",      # .get, :202
    "oxygen.stage.consistencyCheck.orchestration",                 # orchestration.get, :68-69
    "oxygen.stage.consistencyCheck.datasetFormat",                 # readWriteFormat.get, SynthesisIOUtils.scala:129
    "oxygen.stage.consistencyCheck.relationshipMigrationProcessorClassName",  # :197, :250-253
)
BASE_STATECALC = {"dataSource", "subscriber", "subscriberRelation", "asOfPeriod", "pin", "parentEventIds"}


class ErrorArchivo(Exception):
    pass


def leer_json(ruta: Path):
    try:
        return json.loads(ruta.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise ErrorArchivo(f"no existe {ruta}") from None
    except json.JSONDecodeError as e:
        raise ErrorArchivo(f"{ruta.name} no es JSON válido (¿quedaron comentarios //?): {e.msg} en la línea {e.lineno}") from None


def verificar_gdd(gdd: dict) -> list[str]:
    out = []
    if not isinstance(gdd, dict) or "version" not in gdd:
        return ["GDD sin `version`: el motor lanza OxygenException"]
    if gdd.get("version") not in (1, 2):
        out.append(f"GDD con version {gdd.get('version')!r}: el motor solo conoce 1 y 2")
    campos = gdd.get("fields")
    if not isinstance(campos, list):
        return out + ["GDD sin lista `fields`"]
    vistos = set()
    for i, c in enumerate(campos):
        nombre = c.get("name") if isinstance(c, dict) else None
        if not nombre:
            out.append(f"fields[{i}] sin `name`")
            continue
        if nombre in vistos:
            out.append(f"`{nombre}` repetido en fields[]")
        vistos.add(nombre)
        if nombre in INYECTADOS_GDD:
            out.append(f"`{nombre}` lo inyecta el motor: no se declara (AUD-S3)")
        if "handler" in c and c["handler"] not in ("EUC", "LAP"):
            out.append(f"`{nombre}`: handler {c['handler']!r}; solo EUC o LAP (LFP es la ausencia de la clave)")
        if gdd.get("version") == 2:
            sobran = sorted(set(c) - CLAVES_V2)
            if sobran:
                out.append(f"`{nombre}`: claves que la versión 2 ignora: {', '.join(sobran)}")
    return out


def es_comentario(linea: str) -> bool:
    return linea.strip().startswith(("#", "//"))


def verificar_hocon_synthesis(texto: str, modo: str = "propio") -> list[str]:
    if modo == "colombia":
        return verificar_hocon_synthesis_colombia(texto)
    sin_coment = "\n".join(l for l in texto.splitlines() if not es_comentario(l))
    out = []
    if not re.search(r"oxygen\.stage\.consistencyCheck\s*\{", sin_coment):
        pista = (" (el bloque es solo comentarios: si la ingesta está en modo Colombia, verifícalo con --modo colombia)"
                 if not sin_coment.strip() else "")
        out.append("falta la raíz oxygen.stage.consistencyCheck" + pista)
    if re.search(r"oxygen\.stage\.synthesis\b", sin_coment):
        out.append("usa oxygen.stage.synthesis: el motor no la lee (la clave real es consistencyCheck)")
    m = re.search(r"customRulesFile\s*[=:]\s*(\S+)", sin_coment)
    if not m:
        out.append("falta paths.customRulesFile: el motor hace customRulesFile.head (AUD-S1)")
    elif m.group(1).startswith("null"):
        out.append("customRulesFile = null: el motor hace customRulesFile.head y la etapa falla (AUD-S1)")
    if "{{" in sin_coment:
        out.append("quedan {{huecos}} sin resolver")
    return out


def verificar_hocon_synthesis_colombia(texto: str) -> list[str]:
    """HF-68: en modo Colombia el bloque de la ingesta no escribe claves compartidas; solo la lista VERIFICAR."""
    out = []
    lineas = texto.splitlines()
    for n, linea in enumerate(lineas, 1):
        if linea.strip() and not es_comentario(linea):
            out.append(f"línea {n} activa en modo Colombia ({linea.strip()[:60]!r}): el bloque de la ingesta no "
                       "escribe claves de oxygen.stage.consistencyCheck; valen para todas las fuentes de Colombia")
    verificar = "\n".join(l for l in lineas if es_comentario(l) and "VERIFICAR" in l)
    for clave in CLAVES_VERIFICAR_COLOMBIA:
        if not re.search(re.escape(clave) + r"(?![A-Za-z0-9_])", verificar):
            out.append(f"falta `VERIFICAR {clave}` en la lista de verificación del modo Colombia")
    if "{{" in texto:
        out.append("quedan {{huecos}} sin resolver")
    return out


def verificar_calculation(calc: dict, foundation: dict | None = None) -> list[str]:
    out = []
    secciones = calc.get("calculations") if isinstance(calc, dict) else None
    if not isinstance(secciones, list):
        return ["Calculation sin lista `calculations`"]
    nombres_f = None
    if foundation is not None:
        nombres_f = {f.get("name") for f in foundation.get("fields") or [] if isinstance(f, dict)}
    for s in secciones:
        sn = s.get("name") or "?"
        tipo = str(s.get("type") or "")
        if "grouping" in s:
            out.append(f"sección `{sn}`: `grouping` anidado; el DTO solo tiene `groupBy` plano")
        if tipo.upper() not in TIPOS_SECCION:
            out.append(f"sección `{sn}`: type {tipo!r} fuera de {sorted(TIPOS_SECCION)}")
        if tipo.upper() == "AGGREGATES" and tipo != "AGGREGATES":
            out.append(f"sección `{sn}`: type {tipo!r} debe ir en MAYÚSCULAS: el validador del motor compara "
                       "'AGGREGATES' literal y se salta la sección (AUD-SC2)")
        select, group_by = list(s.get("select") or []), list(s.get("groupBy") or [])
        aggs = list(s.get("aggregates") or [])
        if tipo.upper() == "AGGREGATES":
            if not aggs:
                out.append(f"sección `{sn}`: AGGREGATES sin aggregates[] (OXY-SC-1)")
            if select and not group_by:
                out.append(f"sección `{sn}`: AGGREGATES con select y sin groupBy (OXY-SC-2)")
            for x in select:
                if group_by and x not in group_by:
                    out.append(f"sección `{sn}`: `{x}` está en select pero no en groupBy (OXY-SC-3)")
            for a in aggs:
                fn = a.get("function")
                if fn not in FUNCIONES:
                    out.append(f"sección `{sn}`: function {fn!r}; debe ser una de {sorted(FUNCIONES)} en MAYÚSCULAS")
        if nombres_f is not None:
            refs = select + group_by + [a.get("field") for a in aggs if a.get("field")]
            for r in refs:
                if r not in nombres_f and r not in BASE_STATECALC:
                    out.append(f"sección `{sn}`: `{r}` no existe en fields[] de Foundation")
    return out


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Verifica artefactos de Synthesis y State Calculation (1.9.0).")
    sub = ap.add_subparsers(dest="etapa", required=True)
    syn = sub.add_parser("synthesis")
    syn.add_argument("--gdd", type=Path)
    syn.add_argument("--hocon", type=Path)
    syn.add_argument("--modo", choices=("propio", "colombia"), default="propio",
                     help="propio (por defecto): bloque con raíz y customRulesFile; colombia: bloque solo de "
                          "comentarios con la lista VERIFICAR de las claves compartidas (HF-68)")
    sc = sub.add_parser("statecalc")
    sc.add_argument("--calculation", type=Path, required=True)
    sc.add_argument("--foundation", type=Path)
    args = ap.parse_args(argv)
    hallazgos: list[str] = []
    try:
        if args.etapa == "synthesis":
            if not (args.gdd or args.hocon):
                ap.error("synthesis requiere --gdd y/o --hocon")
            if args.gdd:
                hallazgos += [f"GDD: {h}" for h in verificar_gdd(leer_json(args.gdd))]
            if args.hocon:
                if not args.hocon.is_file():
                    raise ErrorArchivo(f"no existe {args.hocon}")
                hallazgos += [f"HOCON: {h}" for h in verificar_hocon_synthesis(args.hocon.read_text(encoding="utf-8"), args.modo)]
        else:
            foundation = leer_json(args.foundation) if args.foundation else None
            hallazgos += verificar_calculation(leer_json(args.calculation), foundation)
    except ErrorArchivo as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 2
    for h in hallazgos:
        print(f"  X {h}")
    print("ARTEFACTOS OK" if not hallazgos else f"ARTEFACTOS CON HALLAZGOS ({len(hallazgos)})")
    return 1 if hallazgos else 0


if __name__ == "__main__":
    sys.exit(main())
