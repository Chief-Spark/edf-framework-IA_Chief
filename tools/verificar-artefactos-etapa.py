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
              - Sección con `where`: su `name` es parte del identificador JavaScript `aggregated_where_<name>`
                (QueryFunctions.scala:75-82): sin `#`, espacios ni otros caracteres inválidos (HF-77). Igual
                para el `name` de un CALCULATED (`foundation_calculated_<name>`, FoundationFunctions.scala:52-56).
              - Con --foundation (HF-73): cada campo de select/groupBy/aggregates[].field es un campo del registro
                Foundation: el nombre final de un campo (un `name` con punto `a.b` queda como `b`,
                FoundationFunctions.scala:68-71) o un campo que inyecta el motor. Un `aliases[]` vale con aviso:
                solo existe en el registro con aliasEnabled = true en el conf (FoundationStateCalculator.scala:103-105).
                Cada `field.X` de un CALCULATED es un campo Foundation declarado (DERIVED, o no DERIVED declarado
                antes) o inyectado (pin, dataSource…): `field` es el registro Foundation, no el evento curado
                (FoundationStateCalculator.scala:52-63); sin él el valor sale null en silencio (HF-74, HF-75).
              - Con --source-spec <source-spec-curation.yaml> (HF-73): el `name` de cada DERIVED es una clave curada
                publicada (campos_curados[].destino o parametros.campo_advertencias). El motor lee el DERIVED por
                `name` (FoundationFunctions.scala:24-35); con otro nombre vale null siempre. Un `value` en un
                DERIVED no se usa: aviso.
              - Modo Colombia (`--modo colombia`, HF-77): la configuración de State Calculation de Colombia es
                compartida (un foundation y un calculation para todas las fuentes). Toda sección de la ingesta
                tiene `where` con filtro por fuente (field.dataSource, field.subscriber o un campo de --campo-fuente):
                sin él MOST_RECENT_VALUE toma el registro más reciente de cualquier fuente (MostRecentValue.scala:20-24).
                Con --campos-existentes / --secciones-existentes (el foundation / calculation compartido en JSON, o
                un nombre por línea): ningún campo ni sección de la ingesta repite un nombre existente; un nombre
                duplicado se procesa dos veces y el último gana para todas las fuentes (ContextUtils.scala:15-19).

Uso:
    python tools/verificar-artefactos-etapa.py synthesis --gdd domaindictionary.json --hocon bloque.conf
    python tools/verificar-artefactos-etapa.py synthesis --modo colombia --gdd domaindictionary.json --hocon bloque.conf
    python tools/verificar-artefactos-etapa.py statecalc --calculation calc.json [--foundation foundation.json]
        [--source-spec source-spec-curation.yaml]
    python tools/verificar-artefactos-etapa.py statecalc --modo colombia --calculation calc.json
        --foundation foundation.json [--source-spec source-spec-curation.yaml]
        [--campos-existentes foundation-colombia.json] [--secciones-existentes calculation-colombia.json]
        [--campo-fuente isSisben ...]
Salida: 0 sin hallazgos (los avisos `!` no cambian la salida), 1 con hallazgos, 2 error de uso o archivo ilegible.
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
# HF-77: el nombre de una sección con `where` y el de un CALCULATED son el sufijo de una función JavaScript.
SUFIJO_JS = re.compile(r"^[A-Za-z0-9_$]+$")
REF_FIELD = re.compile(r"\bfield\.([A-Za-z_$][A-Za-z0-9_$]*)|\bfield\[\s*[\"']([^\"']+)[\"']\s*\]")
CAMPOS_FUENTE = ("dataSource", "subscriber")   # campos base que identifican la fuente (FoundationStateCalculator.scala:47-48)


class ErrorArchivo(Exception):
    pass


def leer_json(ruta: Path):
    try:
        return json.loads(ruta.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise ErrorArchivo(f"no existe {ruta}") from None
    except json.JSONDecodeError as e:
        raise ErrorArchivo(f"{ruta.name} no es JSON válido (¿quedaron comentarios //?): {e.msg} en la línea {e.lineno}") from None


def leer_yaml(ruta: Path) -> dict:
    try:
        import yaml
    except ImportError:
        raise ErrorArchivo("--source-spec necesita PyYAML: pip install pyyaml") from None
    if not ruta.is_file():
        raise ErrorArchivo(f"no existe {ruta}")
    try:
        return yaml.safe_load(ruta.read_text(encoding="utf-8")) or {}
    except yaml.YAMLError as e:
        raise ErrorArchivo(f"{ruta.name} no es YAML válido: {e}") from None


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


def refs_field(script) -> list[str]:
    """Nombres leídos como field.X o field["X"] en un script Nashorn."""
    return [a or b for a, b in REF_FIELD.findall(str(script or ""))]


def nombre_registro(nombre: str) -> str:
    """Nombre del campo en el registro Foundation: `a.b` lee el anidado y se guarda como `b` (FoundationFunctions.scala:63-71)."""
    partes = str(nombre).split(".")
    return partes[1] if len(partes) == 2 else str(nombre)


def es_derived(campo: dict) -> bool:
    return str(campo.get("type") or "DERIVED").upper() == "DERIVED"


def claves_publicadas(spec: dict) -> set[str]:
    """Claves de curatedRecordJson que publica Curation: campos_curados[].destino y parametros.campo_advertencias."""
    out = {c.get("destino") for c in (spec.get("campos_curados") or []) if isinstance(c, dict) and c.get("destino")}
    adv = (spec.get("parametros") or {}).get("campo_advertencias")
    if isinstance(adv, str) and adv.strip():
        out.add(adv.strip())
    return out


def modelo_foundation(foundation: dict):
    """(campos del registro, alias, nombres no DERIVED en orden) de un statecalc-foundation."""
    campos = [f for f in (foundation.get("fields") or []) if isinstance(f, dict) and f.get("name")]
    registro = set(BASE_STATECALC)
    alias: dict[str, str] = {}
    for f in campos:
        registro.add(nombre_registro(f["name"]) if es_derived(f) else f["name"])
        for a in f.get("aliases") or []:
            alias[a] = f["name"]
    return campos, registro, alias


def verificar_foundation(foundation: dict, publicadas: set[str] | None = None) -> tuple[list[str], list[str]]:
    """HF-73/HF-74/HF-75: DERIVED por `name`, `field.X` de un CALCULATED contra el registro Foundation."""
    out, avisos = [], []
    campos, _, alias = modelo_foundation(foundation)
    derivados = {nombre_registro(f["name"]) for f in campos if es_derived(f)}
    vistos, previos = set(), []
    for f in campos:
        n = f["name"]
        final = nombre_registro(n) if es_derived(f) else n
        if final in vistos:
            out.append(f"Foundation: `{final}` repetido en fields[]: se procesa dos veces y el último valor gana")
        vistos.add(final)
        if es_derived(f):
            if f.get("value") not in (None, ""):
                avisos.append(f"Foundation: el DERIVED `{n}` trae `value` ({f['value']!r}), que el motor no usa: "
                              "lee la clave curada `name` (FoundationFunctions.scala:24-35)")
            if publicadas is not None:
                raiz = n.split(".")[0] if len(n.split(".")) == 2 else n
                if n not in publicadas and raiz not in publicadas and n not in INYECTADOS_GDD:
                    out.append(f"Foundation: el DERIVED `{n}` lee la clave curada `{n}` (FoundationFunctions.scala:24-35), "
                               "que Curation no publica: vale null (o su defaultValue) siempre. El `name` de un DERIVED "
                               "es la clave curada; para otro nombre en el registro usa aliases[] o un CALCULATED")
        elif str(f.get("type") or "").upper() == "CALCULATED":
            if not SUFIJO_JS.match(n):
                out.append(f"Foundation: el CALCULATED `{n}` no sirve como sufijo de la función JavaScript "
                           f"foundation_calculated_<name> (FoundationFunctions.scala:52-56): la etapa falla al arrancar")
            disponibles = set(BASE_STATECALC) | derivados | set(previos)
            for x in refs_field(f.get("value")):
                if x in disponibles:
                    continue
                if x in alias:
                    avisos.append(f"Foundation: el CALCULATED `{n}` lee field.{x}, alias de `{alias[x]}`: solo existe con "
                                  "aliasEnabled = true en el conf (FoundationStateCalculator.scala:103-105) [PM]")
                    continue
                despues = any(g["name"] == x for g in campos if not es_derived(g))
                out.append(f"Foundation: el CALCULATED `{n}` lee field.{x}, que no es un campo del registro Foundation "
                           + ("en ese punto (se calcula después: los no DERIVED van en orden)" if despues else
                              "(`field` es el registro Foundation, no el evento curado, FoundationStateCalculator.scala:52-63): "
                              f"declara el DERIVED `{x}` o usa un campo inyectado") + "; sin él el valor sale null en silencio")
        if not es_derived(f):
            previos.append(n)
    return out, avisos


def nombres_de(ruta: Path, clave: str) -> set[str]:
    """Nombres de un foundation (`fields`) o calculation (`calculations`) compartido en JSON, o uno por línea."""
    if not ruta.is_file():
        raise ErrorArchivo(f"no existe {ruta}")
    texto = ruta.read_text(encoding="utf-8")
    if ruta.suffix.lower() == ".json":
        datos = leer_json(ruta)
        lista = datos.get(clave) if isinstance(datos, dict) else datos
        out = set()
        for e in lista or []:
            if isinstance(e, dict) and e.get("name"):
                out.add(nombre_registro(e["name"]) if clave == "fields" and es_derived(e) else e["name"])
                if clave == "fields":
                    out.update(e.get("aliases") or [])
            elif isinstance(e, str):
                out.add(e)
        return out
    return {l.strip() for l in texto.splitlines() if l.strip() and not l.lstrip().startswith("#")}


def verificar_statecalc(calc: dict, foundation: dict | None = None, *, publicadas: set[str] | None = None,
                        modo: str = "propio", campos_existentes: set[str] | None = None,
                        secciones_existentes: set[str] | None = None,
                        campos_fuente: tuple[str, ...] = ()) -> tuple[list[str], list[str]]:
    out, avisos = [], []
    secciones = calc.get("calculations") if isinstance(calc, dict) else None
    if not isinstance(secciones, list):
        return ["Calculation sin lista `calculations`"], []
    registro = alias = None
    if foundation is not None:
        _, registro, alias = modelo_foundation(foundation)
        h, a = verificar_foundation(foundation, publicadas)
        out += h
        avisos += a
    fuente = set(CAMPOS_FUENTE) | set(campos_fuente)
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
        where = s.get("where")
        if where is not None and not SUFIJO_JS.match(str(sn)):
            out.append(f"sección `{sn}`: con `where`, el name forma la función JavaScript aggregated_where_<name> "
                       "(QueryFunctions.scala:75-82); `#`, espacios u otros caracteres inválidos hacen fallar la etapa "
                       "al arrancar. Usa un identificador válido (p. ej. `sisben`) (HF-77)")
        if registro is not None:
            refs = select + group_by + [a.get("field") for a in aggs if a.get("field")]
            for r in refs:
                if r in registro:
                    continue
                if r in alias:
                    avisos.append(f"sección `{sn}`: `{r}` es alias de `{alias[r]}`: solo existe en el registro con "
                                  "aliasEnabled = true en el conf (FoundationStateCalculator.scala:103-105) [PM]")
                else:
                    out.append(f"sección `{sn}`: `{r}` no existe en fields[] de Foundation")
            for x in refs_field(where):
                if x not in registro and x not in alias:
                    avisos.append(f"sección `{sn}`: el where lee field.{x}, que no está en Foundation: solo vale si lo "
                                  "agrega el enricher del fork (FoundationStateCalculator.scala:75)")
        if modo == "colombia" and s.get("enabled", True) is not False:
            if where is None or not str(where).strip():
                out.append(f"sección `{sn}`: sin `where` en modo Colombia; el foundation compartido mezcla todas las "
                           "fuentes del PIN y sin filtro por fuente MOST_RECENT_VALUE toma el registro más reciente de "
                           "cualquier fuente (MostRecentValue.scala:20-24) (HF-77)")
            elif not set(refs_field(where)) & fuente:
                out.append(f"sección `{sn}`: el where no filtra por fuente (lee {sorted(set(refs_field(where))) or 'nada'}; "
                           f"se espera alguno de {sorted(fuente)}; otro campo de fuente se declara con --campo-fuente) (HF-77)")
            if secciones_existentes is not None and sn in secciones_existentes:
                out.append(f"sección `{sn}`: ese nombre ya existe en el calculation compartido de Colombia; dos secciones "
                           "con el mismo nombre comparten función where y clave de salida (ContextUtils.scala:25-30)")
    if modo == "colombia":
        if foundation is None:
            avisos.append("modo Colombia sin --foundation: no se comprobaron los nombres de los campos de la ingesta")
        elif campos_existentes is None:
            avisos.append("modo Colombia sin --campos-existentes: no se comprobó el choque de nombres con el foundation "
                          "compartido de Colombia (queda en la lista de verificación del fork)")
        else:
            for f in modelo_foundation(foundation)[0]:
                final = nombre_registro(f["name"]) if es_derived(f) else f["name"]
                for n in [final] + list(f.get("aliases") or []):
                    if n in campos_existentes:
                        out.append(f"Foundation: `{n}` ya existe en el foundation compartido de Colombia; un nombre "
                                   "duplicado se procesa dos veces y el último gana para todas las fuentes "
                                   "(ContextUtils.scala:15-19; FoundationStateCalculator.scala:52-59). Reutiliza el "
                                   "existente o escala (N4)")
        if secciones_existentes is None:
            avisos.append("modo Colombia sin --secciones-existentes: no se comprobó el choque de nombres de sección")
    return out, avisos


def verificar_calculation(calc: dict, foundation: dict | None = None, **kw) -> list[str]:
    """Hallazgos de State Calculation (sin avisos); ver verificar_statecalc."""
    return verificar_statecalc(calc, foundation, **kw)[0]


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
    sc.add_argument("--source-spec", type=Path,
                    help="source-spec-curation.yaml: el name de cada DERIVED debe ser una clave curada publicada (HF-73)")
    sc.add_argument("--modo", choices=("propio", "colombia"), default="propio",
                    help="propio (por defecto); colombia: configuración compartida, cada sección filtra por fuente y "
                         "ningún nombre choca con los existentes (HF-77)")
    sc.add_argument("--campos-existentes", type=Path,
                    help="modo colombia: foundation compartido (JSON con fields[]) o un nombre por línea")
    sc.add_argument("--secciones-existentes", type=Path,
                    help="modo colombia: calculation compartido (JSON con calculations[]) o un nombre por línea")
    sc.add_argument("--campo-fuente", action="append", default=[],
                    help="modo colombia: campo que identifica la fuente en el where, además de dataSource y subscriber")
    args = ap.parse_args(argv)
    hallazgos: list[str] = []
    avisos: list[str] = []
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
            publicadas = None
            if args.source_spec:
                publicadas = claves_publicadas(leer_yaml(args.source_spec))
            existentes = nombres_de(args.campos_existentes, "fields") if args.campos_existentes else None
            secc = nombres_de(args.secciones_existentes, "calculations") if args.secciones_existentes else None
            h, a = verificar_statecalc(leer_json(args.calculation), foundation, publicadas=publicadas, modo=args.modo,
                                       campos_existentes=existentes, secciones_existentes=secc,
                                       campos_fuente=tuple(args.campo_fuente))
            hallazgos += h
            avisos += a
    except ErrorArchivo as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 2
    for a in avisos:
        print(f"  ! AVISO {a}")
    for h in hallazgos:
        print(f"  X {h}")
    print("ARTEFACTOS OK" if not hallazgos else f"ARTEFACTOS CON HALLAZGOS ({len(hallazgos)})")
    return 1 if hallazgos else 0


if __name__ == "__main__":
    sys.exit(main())
