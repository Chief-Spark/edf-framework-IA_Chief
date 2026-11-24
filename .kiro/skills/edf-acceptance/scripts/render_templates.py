#!/usr/bin/env python3
"""Renderiza las plantillas del skill edf-acceptance-stage a partir de una source-spec.

Generador VIGENTE de la skill (desde la versión 1.6.0 del framework, 2026-11-18). No requiere compilar nada:
solo Python 3 con PyYAML y jsonschema. Equivale al generador Java deprecado que se usó hasta la 1.5.x
(scripts/java, conservado como referencia histórica, no se ejecuta): mismos archivos byte a byte y mismo reporte.
Desde la auditoría 2026-11-24 (AUD-A1..AUD-A6) la salida evoluciona solo aquí.

Uso:
    python render_templates.py --spec <source-spec.yaml> --out <dir> [--schema <schema.yaml>]
                               [--templates <dir>] [--audit]

Sintaxis de plantilla (subconjunto propio, sin dependencias):
    {{ruta.al.valor}}            valor escalar; falla si no existe o es null
    {{ruta|hocon}} / {{ruta|scala}}  escapa \\ y " para un literal entre comillas
    {{ruta|json}}                serializa el valor como JSON
    {{#cada ruta}}...{{/cada}}   repite el bloque por cada elemento (objeto) de la lista
    {{#si ruta}}...{{/si}}       incluye el bloque si el valor es verdadero
    {{#si ruta?}}...{{/si}}      igual, pero una clave ausente cuenta como falsa (claves opcionales, AUD-A4)
    {{> archivo.tpl}}            incluye otra plantilla (parcial)
Las etiquetas de bloque solas en su línea no dejan línea en blanco. Las rutas se buscan primero en
el elemento actual del bucle y después en los ámbitos exteriores, hasta la raíz de la spec.

Sale con código 1 si la spec no valida, si una verificación del inventario falla o si queda algún
hueco sin resolver; con código 2 ante un error de uso. Requiere PyYAML y jsonschema.

Si la spec declara el bloque opcional `supuestos` (AC-27, ADR-G2), imprime una línea
`supuesto  S-ACC-xx  impacto=…  donde=…  texto=…` por supuesto: con --audit, después de los huecos;
sin --audit, después de generado/omitido.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

import yaml

SKILL_DIR = Path(__file__).resolve().parent.parent
DEFAULT_TEMPLATES = SKILL_DIR / "assets" / "templates"
DEFAULT_SCHEMA = SKILL_DIR / "assets" / "schema" / "source-spec.schema.yaml"

TAG_RE = re.compile(r"\{\{(.*?)\}\}", re.S)
STANDALONE_RE = re.compile(r"^[ \t]*(\{\{[#/>][^}]*\}\})[ \t]*\r?\n", re.M)
FILTERS = ("hocon", "scala", "json")
MAX_PARTIAL_DEPTH = 5


class TemplateError(Exception):
    """Error de plantilla o de valor: hueco sin resolver, bloque mal cerrado, valor nulo."""


# --------------------------------------------------------------------------- parseo

@dataclass(frozen=True)
class Text:
    value: str


@dataclass(frozen=True)
class Var:
    path: str
    filter: str | None


@dataclass(frozen=True)
class Block:
    kind: str  # "cada" | "si"
    path: str
    children: tuple = field(default_factory=tuple)


def expand_partials(source: str, templates_dir: Path, depth: int = 0) -> str:
    if depth > MAX_PARTIAL_DEPTH:
        raise TemplateError("parciales anidados demasiado profundos")
    source = STANDALONE_RE.sub(lambda m: m.group(1), source)

    def repl(match: re.Match) -> str:
        name = match.group(1).strip()
        partial = (templates_dir / name).resolve()
        if partial.parent != templates_dir.resolve():
            raise TemplateError(f"parcial fuera del directorio de plantillas: {name}")
        if not partial.is_file():
            raise TemplateError(f"parcial inexistente: {name}")
        return expand_partials(partial.read_text(encoding="utf-8-sig"), templates_dir, depth + 1)

    return re.sub(r"\{\{>\s*([^}]+?)\s*\}\}", repl, source)


def parse(source: str) -> tuple:
    """Convierte el texto de la plantilla en un árbol inmutable de nodos."""
    root: list = []
    stack: list[tuple[str, str, list]] = []
    current = root
    pos = 0
    for match in TAG_RE.finditer(source):
        if match.start() > pos:
            current.append(Text(source[pos:match.start()]))
        pos = match.end()
        tag = match.group(1).strip()
        if tag.startswith("#"):
            kind, _, path = tag[1:].partition(" ")
            if kind not in ("cada", "si") or not path.strip():
                raise TemplateError(f"bloque desconocido: {{{{{tag}}}}}")
            stack.append((kind, path.strip(), current))
            current = []
        elif tag.startswith("/"):
            if not stack or stack[-1][0] != tag[1:].strip():
                raise TemplateError(f"cierre inesperado: {{{{{tag}}}}}")
            kind, path, parent = stack.pop()
            parent.append(Block(kind, path, tuple(current)))
            current = parent
        else:
            path, _, filt = tag.partition("|")
            filt = filt.strip() or None
            if filt is not None and filt not in FILTERS:
                raise TemplateError(f"filtro desconocido: {filt}")
            if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*", path.strip()):
                raise TemplateError(f"hueco inválido: {{{{{tag}}}}}")
            current.append(Var(path.strip(), filt))
    if stack:
        raise TemplateError(f"bloque sin cerrar: {stack[-1][0]} {stack[-1][1]}")
    if pos < len(source):
        current.append(Text(source[pos:]))
    return tuple(root)


def load_template(name: str, templates_dir: Path) -> tuple:
    text = (templates_dir / name).read_text(encoding="utf-8-sig")
    return parse(expand_partials(text, templates_dir))


# --------------------------------------------------------------------------- render

_MISSING = object()


def _resolve_in(scope: Any, parts: list[str]) -> Any:
    value = scope
    for part in parts:
        if not isinstance(value, dict) or part not in value:
            return _MISSING
        value = value[part]
    return value


def lookup(path: str, scopes: tuple) -> Any:
    parts = path.split(".")
    for scope in reversed(scopes):
        if isinstance(scope, dict) and parts[0] in scope:
            value = _resolve_in(scope, parts)
            if value is _MISSING:
                raise TemplateError(f"hueco sin resolver: {path}")
            return value
    raise TemplateError(f"hueco sin resolver: {path}")


def format_value(value: Any, filt: str | None, path: str) -> str:
    if filt == "json":
        return json.dumps(value, ensure_ascii=False)
    if value is None:
        raise TemplateError(f"hueco con valor nulo: {path}")
    if isinstance(value, bool):
        text = "true" if value else "false"
    elif isinstance(value, (int, float, str)):
        text = str(value)
    else:
        raise TemplateError(f"hueco no escalar sin filtro json: {path}")
    if filt in ("hocon", "scala"):
        if "\n" in text or "\r" in text:
            raise TemplateError(f"salto de línea no permitido en literal: {path}")
        text = text.replace("\\", "\\\\").replace('"', '\\"')
    return text


def condition_value(path: str, scopes: tuple) -> Any:
    """Valor de un {{#si}}. Con sufijo "?" la clave es opcional: ausente = falso (AUD-A4). Sin él, falla."""
    if not path.endswith("?"):
        return lookup(path, scopes)
    try:
        return lookup(path[:-1], scopes)
    except TemplateError:
        return None


def render_nodes(nodes: tuple, scopes: tuple) -> str:
    out: list[str] = []
    for node in nodes:
        if isinstance(node, Text):
            out.append(node.value)
        elif isinstance(node, Var):
            out.append(format_value(lookup(node.path, scopes), node.filter, node.path))
        elif node.kind == "si":
            if condition_value(node.path, scopes):
                out.append(render_nodes(node.children, scopes))
        else:
            items = lookup(node.path, scopes)
            if not isinstance(items, list):
                raise TemplateError(f"#cada requiere una lista: {node.path}")
            for item in items:
                if not isinstance(item, dict):
                    raise TemplateError(f"#cada requiere objetos: {node.path}")
                out.append(render_nodes(node.children, scopes + (item,)))
    return "".join(out)


def render_string(source: str, spec: dict, templates_dir: Path = DEFAULT_TEMPLATES,
                  extra_scope: dict | None = None) -> str:
    scopes = (spec,) if extra_scope is None else (spec, extra_scope)
    return render_nodes(parse(expand_partials(source, templates_dir)), scopes)


# --------------------------------------------------------------------------- auditoría

def _schema_child(node: dict, part: str) -> dict | None:
    props = node.get("properties", {}) if isinstance(node, dict) else {}
    return props.get(part)


def _schema_lookup(path: str, scopes: tuple) -> dict | None:
    parts = path.rstrip("?").split(".")
    for scope in reversed(scopes):
        if _schema_child(scope, parts[0]) is None:
            continue
        node = scope
        for part in parts:
            node = _schema_child(node, part)
            if node is None:
                return None
        return node
    return None


def audit_nodes(nodes: tuple, schema_scopes: tuple, data_scopes: tuple, template: str, rows: list) -> None:
    """Recorre TODAS las ramas (incluidas las deshabilitadas) contra el schema y contra la spec."""
    for node in nodes:
        if isinstance(node, Text):
            continue
        schema_node = _schema_lookup(node.path, schema_scopes)
        try:
            value = lookup(node.path.rstrip("?"), data_scopes) if data_scopes else _MISSING
        except TemplateError:
            value = _MISSING
        if value is _MISSING:
            status = "ausente"
        elif value is None:
            status = "nulo"
        else:
            status = "lleno"
        rows.append({"plantilla": template, "hueco": node.path,
                     "en_schema": schema_node is not None, "en_spec": status})
        if isinstance(node, Block) and node.kind == "cada":
            items_schema = (schema_node or {}).get("items", {})
            first = value[0] if isinstance(value, list) and value and isinstance(value[0], dict) else None
            child_data = data_scopes + (first,) if first is not None else ()
            audit_nodes(node.children, schema_scopes + (items_schema,), child_data, template, rows)
        elif isinstance(node, Block):
            audit_nodes(node.children, schema_scopes, data_scopes, template, rows)


def inline_refs(node: Any, root: dict) -> Any:
    """Devuelve una copia del schema con los $ref locales (#/$defs/...) sustituidos."""
    if isinstance(node, dict):
        ref = node.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/$defs/"):
            target = root["$defs"][ref.split("/")[-1]]
            merged = {**inline_refs(target, root), **{k: v for k, v in node.items() if k != "$ref"}}
            return {k: inline_refs(v, root) for k, v in merged.items()}
        return {k: inline_refs(v, root) for k, v in node.items()}
    if isinstance(node, list):
        return [inline_refs(v, root) for v in node]
    return node


def audit(spec: dict, schema: dict, templates_dir: Path = DEFAULT_TEMPLATES) -> list[dict]:
    schema = inline_refs(schema, schema)
    manifest = load_manifest(templates_dir)
    rows: list[dict] = []
    for entry in manifest:
        nodes = load_template(entry["plantilla"], templates_dir)
        schema_scopes: tuple = (schema,)
        data_scopes: tuple = (spec,)
        if entry.get("por_cada"):
            items_schema = (_schema_lookup(entry["por_cada"], schema_scopes) or {}).get("items", {})
            items = lookup(entry["por_cada"], data_scopes)
            schema_scopes += (items_schema,)
            data_scopes += (items[0],)
        if entry.get("salida"):
            audit_nodes(parse(entry["salida"]), schema_scopes, data_scopes, entry["plantilla"] + "#salida", rows)
        audit_nodes(nodes, schema_scopes, data_scopes, entry["plantilla"], rows)
    return rows


def _una_linea(value: Any) -> str:
    return "" if value is None else re.sub(r"\s+", " ", str(value)).strip()


def supuestos(spec: Any) -> list[dict]:
    """Supuestos declarados en la spec (AC-27, ADR-G2), en su orden. Bloque opcional: sin él, lista vacía."""
    out = []
    if isinstance(spec, dict) and isinstance(spec.get("supuestos"), list):
        for item in spec["supuestos"]:
            if isinstance(item, dict):
                out.append({k: _una_linea(item.get(k)) for k in ("id", "supuesto", "impacto", "donde")})
    return out


def linea_supuesto(s: dict) -> str:
    return f"supuesto  {s['id']}  impacto={s['impacto']}  donde={s['donde']}  texto={s['supuesto']}"


# --------------------------------------------------------------------------- verificaciones

NUM_RE = re.compile(r"(?<![\w.])\d+\.\d+|(?<![\w.])\d+(?![\w.])")
LITERAL_RE = re.compile(r'"([^"]*)"')
DSL_WORDS = {"anyOf", "allOf", "hasErrorlevelIn", "hasErrorlevelNotIn"}
NEW_SYNTAX_RE = re.compile(r"(anyOf|allOf)\([A-Za-z0-9_, ]+\)( hasErrorlevel(Not)?In\([0-9, ]+\))?")
# Marcadores que el motor rechaza siempre (SparkAcceptor.shouldAutomaticReject); errorInEmptyFileBody solo si
# accept no contiene isEmptyBodyFile.
AUTOMATIC_MARKERS = ("errorInHeader", "errorInFooter", "errorInFileName", "errorInHistoricAttribute")


def remove_extension(path: str) -> str:
    """Equivalente a FilenameUtils.removeExtension: quita desde el último punto del nombre."""
    sep = max(path.rfind("/"), path.rfind("\\"))
    dot = path.rfind(".")
    return path[:dot] if dot > sep else path


def engine_file_key(path: str) -> str:
    """Clave de archivo de E3: una extensión menos; dos si la ruta contiene un marcador errorIn*."""
    stripped = remove_extension(path)
    return remove_extension(stripped) if "errorIn" in path else stripped


def verify_spec(spec: dict) -> list[str]:
    """Verificaciones del inventario (ACC-TPL-01/02/04) y de los riesgos ACC-R01..R03."""
    errors: list[str] = []
    fuente = spec["Fuente"]
    pattern = spec["archivo"]["filePattern"]
    if not pattern.startswith(".*"):
        errors.append("filePattern debe empezar con .* (RF-ACC-01)")
    try:
        regex = re.compile(pattern)
    except re.error as exc:
        return errors + [f"filePattern no compila: {exc}"]
    for case in spec["archivo"].get("claves_de_prueba", []):
        # Las claves "ruta_*" son rutas del directorio de validados: se les aplica el mismo recorte
        # de extensión que hace el motor en E3 antes de comparar (String.matches = coincidencia total).
        value = engine_file_key(case["valor"]) if case["clave"].startswith("ruta_") else case["valor"]
        if bool(regex.fullmatch(value)) != case["debe_coincidir"]:
            errors.append(f"filePattern vs {case['clave']}: se esperaba {case['debe_coincidir']} (ACC-R03)")

    umbral = spec["calidad"]["umbral"]
    fuera_de_rango = umbral_fuera_de_rango(umbral)
    if fuera_de_rango:
        errors.append(fuera_de_rango)
    exprs = [spec["fileActions"]["accept"]] + [c["expresion"] for c in spec["condiciones"]]
    for expr in exprs:
        nums = NUM_RE.findall(expr)
        if nums != [umbral]:
            errors.append(f"escala del umbral distinta de {umbral} en: {expr}")
    qs = spec["calidad"]["quality_score"]
    if f'average("qs.{qs}")' not in spec["fileActions"]["accept"]:
        errors.append("accept no agrega el quality score declarado")
    if "isEmptyBodyFile" in spec["fileActions"]["accept"] and not spec["rechazo_automatico"]["aceptar_cuerpo_vacio"]:
        errors.append("accept contiene isEmptyBodyFile pero aceptar_cuerpo_vacio es false")

    names = [c["nombre"] for c in spec["condiciones"]]
    codes = [c["reasonCode"] for c in spec["condiciones"]]
    if len(set(names)) != len(names) or len(set(codes)) != len(codes):
        errors.append("nombres o reasonCode de condiciones repetidos")
    for name in names:
        if fuente not in name:
            errors.append(f"la condición {name} no lleva el nombre de la fuente (unicidad global)")
    # manualReview es opcional (AUD-A4): SparkDatasetAcceptor lo compila con la sintaxis nueva, como reject.
    nuevas = {"reject": spec["fileActions"]["reject"]}
    if spec["fileActions"].get("manualReview"):
        nuevas["manualReview"] = spec["fileActions"]["manualReview"]
    for key, expr in nuevas.items():
        cited = set(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", expr)) - DSL_WORDS
        if not cited or not cited <= set(names):
            errors.append(f"{key} cita condiciones no definidas: {sorted(cited - set(names))}")
        if not NEW_SYNTAX_RE.fullmatch(expr):
            errors.append(f"{key} no está en la sintaxis nueva: caería en silencio a la antigua (ACC-P09)")
        if "hasErrorlevel" in expr and not spec["severidades"]["habilitado"]:
            errors.append(f"{key} usa hasErrorlevel* sin tabla de severidades (ACC-TPL-03)")

    record = spec["registro"]["recordAcceptance"]
    diag = spec["diagnostico_validation"]
    known_codes = sorted(set(diag["codigos_sev1"] + diag["codigos_sev3"] + diag["codigos_motor"]))
    for level in spec["registro"]["niveles_que_rechazan"]:
        if level not in diag["niveles"] or len(level) != 1:
            errors.append(f"nivel {level!r} no emitido por Validation o no es de un carácter (ACC-R01)")
    for code in spec["registro"]["codigos_que_rechazan"]:
        if code not in known_codes:
            errors.append(f"código {code} no emitido por Validation (IN-05)")
        others = [c for c in known_codes if c != code and code in c]
        if others:
            errors.append(f"código {code} es subcadena de {others} (ACC-R01)")
    for literal in LITERAL_RE.findall(record):
        if literal not in known_codes and literal not in diag["niveles"]:
            errors.append(f"literal {literal!r} de recordAcceptance no existe en IN-05")
    for name in diag["quality_scores"]:
        # El motor hace replaceAll(nombreQS, score) sobre TODO el texto: basta una subcadena.
        if name in record:
            errors.append(f"el quality score {name} aparece dentro de recordAcceptance (ACC-R02)")

    co = spec["contextual_override"]
    if co["decision_humana"] and not co.get("decision_ref"):
        errors.append("contextualOverride habilitado sin referencia a la decisión humana (ACC-TPL-04)")
    if spec["intervencion_manual"]["habilitada"]:
        for key in ("manualReject", "manualAccept"):
            expr = spec["intervencion_manual"].get(key)
            if not isinstance(expr, str) or "externalAction(" not in expr:
                errors.append(f"{key} solo admite expresiones con externalAction(...) (IN-07)")
    errors += qs_no_binarios(spec)
    errors += marcadores_coherentes(spec)
    return errors


def marcadores_coherentes(spec: dict) -> list[str]:
    """AUD-A2: en el fixture, el marcador está en el directorio y "rechazo_automatico" coincide con la regla del
    motor (SparkAcceptor.shouldAutomaticReject, que mira el nombre del directorio de validados)."""
    errors: list[str] = []
    accept = spec["fileActions"]["accept"]
    for i, archivo in enumerate(spec["fixture"]["archivos"]):
        directorio, marcador = archivo["directorio"], archivo["marcador"]
        if marcador and marcador not in directorio:
            errors.append(f"fixture/archivos/{i}: el marcador {marcador} no aparece en el directorio {directorio}")
        automatico = (any(m in directorio for m in AUTOMATIC_MARKERS)
                      or ("errorInEmptyFileBody" in directorio and "isEmptyBodyFile" not in accept))
        if automatico != (archivo["decision_esperada"] == "rechazo_automatico"):
            errors.append(f"fixture/archivos/{i}: decision_esperada {archivo['decision_esperada']!r} no coincide con "
                          f"la regla de rechazo automático del motor (automático = {automatico})")
    return errors


def umbral_fuera_de_rango(umbral: Any) -> str | None:
    """Regla "umbral en [0, 1]" (AC-04, P-3). Devuelve el mensaje de error o None si está en rango."""
    try:
        valor = Decimal(str(umbral).strip())
    except InvalidOperation:
        return f"umbral en [0, 1]: calidad.umbral {umbral!r} no es un número (AC-04)"
    if valor < 0 or valor > 1:
        return f"umbral en [0, 1]: calidad.umbral {umbral!r} está fuera de [0, 1] (AC-04)"
    return None


def qs_no_binarios(spec: dict) -> list[str]:
    """Regla "QS por registro binario" (AC-ACC-05): en el fixture, cada quality score declarado vale 0 o 1."""
    errors: list[str] = []
    declarados = set()
    diag = spec.get("diagnostico_validation")
    if isinstance(diag, dict) and isinstance(diag.get("quality_scores"), list):
        declarados |= {q for q in diag["quality_scores"] if isinstance(q, str)}
    calidad = spec.get("calidad")
    if isinstance(calidad, dict) and isinstance(calidad.get("quality_score"), str):
        declarados.add(calidad["quality_score"])
    fixture = spec.get("fixture")
    if not isinstance(fixture, dict) or not isinstance(fixture.get("archivos"), list):
        return errors
    for i, archivo in enumerate(fixture["archivos"]):
        if not isinstance(archivo, dict) or not isinstance(archivo.get("eventos"), list):
            continue
        for j, evento in enumerate(archivo["eventos"]):
            if not isinstance(evento, dict) or not isinstance(evento.get("qualityScores"), list):
                continue
            for score in evento["qualityScores"]:
                if not isinstance(score, dict) or score.get("name") not in declarados:
                    continue
                v = score.get("score")
                binario = isinstance(v, (int, float)) and not isinstance(v, bool) and float(v) in (0.0, 1.0)
                if not binario:
                    errors.append(f"QS por registro binario: {score.get('name')} = {v} en fixture/archivos/{i}"
                                  f"/eventos/{j} ({evento.get('eventId')}); debe ser 0 o 1 (AC-ACC-05)")
    return errors


def validate_schema(spec: dict, schema: dict) -> list[str]:
    try:
        import jsonschema  # type: ignore
    except ImportError as exc:  # sin jsonschema no se puede certificar la forma de la spec
        raise TemplateError("falta el paquete jsonschema: pip install jsonschema") from exc
    validator = jsonschema.Draft202012Validator(schema)
    return [f"{'/'.join(map(str, e.path)) or '(raíz)'}: {e.message}" for e in validator.iter_errors(spec)]


# --------------------------------------------------------------------------- generación

def load_manifest(templates_dir: Path) -> list[dict]:
    data = yaml.safe_load((templates_dir / "manifest.yaml").read_text(encoding="utf-8-sig"))
    return data["plantillas"]


def _safe_output(out_dir: Path, relative: str) -> Path:
    target = (out_dir / relative).resolve()
    if out_dir.resolve() not in target.parents:
        raise TemplateError(f"ruta de salida fuera del directorio: {relative}")
    return target


def generate(spec: dict, out_dir: Path, templates_dir: Path = DEFAULT_TEMPLATES) -> dict:
    """Genera los artefactos en el orden del manifiesto. Devuelve el reporte (generados/omitidos)."""
    report = {"generados": [], "omitidos": []}
    for entry in load_manifest(templates_dir):
        if entry.get("parcial"):
            continue
        if entry.get("condicion") and not lookup(entry["condicion"], (spec,)):
            report["omitidos"].append({"id": entry["id"], "plantilla": entry["plantilla"],
                                       "motivo": f"{entry['condicion']} = false"})
            continue
        nodes = load_template(entry["plantilla"], templates_dir)
        items = lookup(entry["por_cada"], (spec,)) if entry.get("por_cada") else [None]
        for item in items:
            scopes = (spec,) if item is None else (spec, item)
            relative = render_nodes(parse(entry["salida"]), scopes)
            content = render_nodes(nodes, scopes)
            leftover = TAG_RE.search(content)
            if leftover:
                raise TemplateError(f"hueco sin resolver en {relative}: {leftover.group(0)}")
            target = _safe_output(out_dir, relative)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8", newline="\n")
            report["generados"].append({"id": entry["id"], "archivo": relative})
    return report


def load_yaml(path: Path) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8-sig"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--spec", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--schema", type=Path, default=DEFAULT_SCHEMA)
    parser.add_argument("--templates", type=Path, default=DEFAULT_TEMPLATES)
    parser.add_argument("--audit", action="store_true", help="lista cada hueco: en schema / en spec")
    args = parser.parse_args(argv)
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, OSError):
            pass
    try:
        spec, schema = load_yaml(args.spec), load_yaml(args.schema)
        problems = validate_schema(spec, schema)
    except (OSError, yaml.YAMLError, TemplateError) as exc:
        print(f"ERROR: {exc}")
        return 1
    try:
        problems += verify_spec(spec)
    except Exception as exc:  # noqa: BLE001 - una spec que no cumple el schema puede no tener la forma esperada
        if not problems:
            problems.append(f"la spec no tiene la forma esperada: {exc!r}")
    if problems:
        print("ERROR: la spec no pasa las verificaciones:", *problems, sep="\n  - ")
        return 1
    lista_supuestos = supuestos(spec)
    if args.audit:
        rows = audit(spec, schema, args.templates)
        undefined = [r for r in rows if not r["en_schema"]]
        for row in rows:
            print(f"{row['plantilla']:<40} {row['hueco']:<55} schema={row['en_schema']!s:<5} spec={row['en_spec']}")
        for s in lista_supuestos:
            print(linea_supuesto(s))
        if undefined:
            print(f"ERROR: {len(undefined)} huecos no definidos en el schema")
            return 1
    try:
        report = generate(spec, args.out, args.templates)
    except TemplateError as exc:
        print(f"ERROR: {exc}")
        return 1
    for item in report["generados"]:
        print(f"generado  {item['id']}  {item['archivo']}")
    for item in report["omitidos"]:
        print(f"omitido   {item['id']}  {item['plantilla']}  ({item['motivo']})")
    if not args.audit:
        for s in lista_supuestos:
            print(linea_supuesto(s))
    return 0


if __name__ == "__main__":
    sys.exit(main())
