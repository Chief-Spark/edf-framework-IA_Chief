#!/usr/bin/env python3
"""Pre-validación local (sin Spark ni JAR) de un archivo contra un source-spec de Validation.

Replica en Python las reglas de la Capa A generada (FieldValidators / RecordValidator) y el flujo del
motor SparkValidator (archivo vacío → nombre → header → cuerpo filtrado → duplicados → métricas), para
que el ingeniero de datos vea ANTES de ejecutar en el clúster qué diagnóstico produciría el archivo.

Límite declarado (AC-24): el oráculo NO modela E8 (footer). No lee filas_footer ni footer_regex y nunca
produce errorInFooter (-1004). Por eso una spec con filas_footer > 0 se rechaza con SpecError en vez de
validar el footer como un registro más (guarda P-2). SISBEN no tiene footer (filas_footer = 0).

Uso:
    python prevalidar_archivo.py --spec source-spec.yaml --archivo RUTA [--lookups DIR] [--hoy AAAA-MM-DD]
                                 [--salida resumen.json] [--detalle]

Privacidad: el resumen NUNCA contiene valores de campos; solo números de línea, códigos y conteos.
Catálogos: sin --lookups se usan las filas SINTÉTICAS del source-spec (no son datos oficiales).
Es una simulación: no certifica el comportamiento del framework (F-01).
Verifica el spec con el generador Python (render_templates.py, vía spec_utils.py). No requiere Java.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import unicodedata
from datetime import date, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from spec_utils import (SpecError, field_by_name, java_to_py_date, load_spec,  # noqa: E402
                        nombre_valido_el_dia, verify_spec)

DUPLICATE_MARK = "OxygenDuplicate"


# --------------------------------------------------------------------------------- utilidades
def comparable(value: str | None) -> str:
    text = unicodedata.normalize("NFD", (value or "").strip())
    return "".join(ch for ch in text if unicodedata.category(ch) != "Mn").upper()


def split_row(row: str | None, delimiter: str) -> list[str]:
    return (row or "").split(delimiter)  # equivale a split(Pattern.quote(d), -1)


def value_at(values: list[str], position: int) -> str:
    return values[position - 1].strip() if 1 <= position <= len(values) else ""


def normalize(value: str, steps: list[str]) -> str:
    out = value.strip()
    for step in steps:
        if step == "quitar_espacios":
            out = re.sub(r"\s+", "", out)
        elif step == "quitar_ceros_izquierda":
            out = re.sub(r"^0+(?=.)", "", out)
    return out


def parse_date(value: str, patterns: list[str]) -> datetime | None:
    for pattern in patterns:
        fmt = java_to_py_date(pattern)
        try:
            parsed = datetime.strptime(value, fmt)
        except ValueError:
            continue
        if parsed.strftime(fmt) == value:  # estricto: rechaza 1/2/2024 frente a dd/MM/yyyy
            return parsed
    return None


class Catalog:
    """Catálogo en memoria: tabla → lista de filas {columna: valor}."""

    def __init__(self, tables: dict[str, list[dict[str, str]]]):
        self.tables = tables

    @classmethod
    def from_spec(cls, spec: dict) -> "Catalog":
        return cls({c["tabla"]: [dict(zip(c["columnas"], row)) for row in c["filas_sinteticas"]]
                    for c in spec["catalogos"]})

    @classmethod
    def from_dir(cls, spec: dict, folder: Path) -> "Catalog":
        tables = {}
        for cat in spec["catalogos"]:
            path = folder / cat["archivo"]
            with path.open("r", encoding="utf-8-sig", newline="") as fh:
                tables[cat["tabla"]] = list(csv.DictReader(fh, delimiter=cat["delimitador"]))
        return cls(tables)

    def exists(self, table: str, column: str, value: str) -> bool:
        return any((row.get(column) or "") == value for row in self.tables.get(table, []))

    def find(self, table: str, key_column: str, key: str, out_column: str) -> str | None:
        for row in self.tables.get(table, []):
            if (row.get(key_column) or "") == key:
                return row.get(out_column)
        return None


# -------------------------------------------------------------------------------- validación
def validate_field(field: dict, raw: str, catalog: Catalog, today: date) -> str | None:
    """Devuelve la causa del error (o None). Mismo orden que FieldValidators.validar."""
    value = normalize(raw or "", field.get("normalizacion", []))
    if not value:
        return "OBLIGATORIO" if field["obligatorio"] else None
    if field["tipo"] == "numerico" and not re.fullmatch(r"[0-9]+", value):
        return "TIPO"
    if field["tipo"] == "fecha" and parse_date(value, field.get("formatos_fecha", [])) is None:
        return "FORMATO_FECHA"
    if field.get("longitud_max") and len(value) > field["longitud_max"]:
        return "LONGITUD"
    if field.get("rango"):
        try:
            number = int(value)
        except ValueError:
            return "TIPO"
        if not field["rango"][0] <= number <= field["rango"][1]:
            return "RANGO"
    if field.get("dominio") and comparable(value) not in {comparable(d) for d in field["dominio"]}:
        return "DOMINIO"
    if field.get("no_futura"):
        parsed = parse_date(value, field.get("formatos_fecha", []))
        if parsed and parsed.date() > today:
            return "FECHA_FUTURA"
    if field.get("catalogo") and not catalog.exists(field["catalogo"]["tabla"], field["catalogo"]["columna"], value):
        return "CATALOGO"
    return None


def validate_record(spec: dict, row: str, catalog: Catalog, today: date) -> list[dict]:
    """Errores del registro: [{codigo, nivel, causa, campo}] sin valores (sin PII)."""
    values = split_row(row, spec["archivo"]["delimitador"])
    especiales = spec["codigos_especiales"]
    if len(values) != len(spec["campos"]):
        return [{"campo": "REGISTRO", "codigo": especiales["estructura"], "nivel": "1", "causa": "COLUMNAS"}]
    errors = []
    for field in spec["campos"]:
        cause = validate_field(field, value_at(values, field["posicion"]), catalog, today)
        if cause:
            errors.append({"campo": field["nombre"], "codigo": field["codigo"], "nivel": field["severidad"],
                           "causa": cause})
    with_error = {e["campo"] for e in errors}
    fields = field_by_name(spec)

    def v(name: str) -> str:
        return value_at(values, fields[name]["posicion"])

    for rule in spec["reglas_entre_campos"]:
        if rule["tipo"] == "correspondencia_catalogo":
            if rule["campo_codigo"] in with_error or rule["campo_nombre"] in with_error or not v(rule["campo_codigo"]):
                continue
            expected = catalog.find(rule["tabla"], rule["columna_clave"], v(rule["campo_codigo"]), rule["columna_nombre"])
            if expected is not None and comparable(expected) != comparable(v(rule["campo_nombre"])):
                errors.append({"campo": rule["campo_nombre"], "codigo": rule["codigo"], "nivel": rule["severidad"],
                               "causa": "CORRESPONDENCIA"})
        elif rule["tipo"] == "solo_ceros":  # AUD-V-03: sobre el valor sin espacios, no sobre una lista
            sin_espacios = normalize(v(rule["campo"]), ["quitar_espacios"])
            if sin_espacios and set(sin_espacios) == {"0"}:
                errors.append({"campo": rule["campo"], "codigo": rule["codigo"], "nivel": rule["severidad"],
                               "causa": rule["causa"]})
        elif comparable(v(rule["campo"])) in {comparable(x) for x in rule["valores"]}:
            errors.append({"campo": rule["campo"], "codigo": rule["codigo"], "nivel": rule["severidad"],
                           "causa": rule["causa"]})
    seen, unique = set(), []
    for error in errors:
        if error["codigo"] not in seen:
            seen.add(error["codigo"])
            unique.append(error)
    return unique


def quality_scores(spec: dict, errors: list[dict]) -> dict[str, float]:
    return {q["nombre"]: 0.0 if any(e["nivel"] in q["anulan_niveles"] for e in errors) else 1.0
            for q in spec["quality_scores"]}


def is_header_row(spec: dict, row: str) -> bool:
    first = comparable(value_at(split_row(row.replace("\ufeff", ""), spec["archivo"]["delimitador"]), 1))
    field = next(c for c in spec["campos"] if c["posicion"] == 1)
    return first in {comparable(a) for a in [field["nombre"], *field["alias"]]}


def should_filter(spec: dict, row: str) -> bool:
    rule = spec["filtro_filas"]
    if not row.strip():
        return rule["lineas_vacias"]
    if not rule.get("campo"):
        return False
    values = split_row(row, spec["archivo"]["delimitador"])
    position = field_by_name(spec)[rule["campo"]]["posicion"]
    return position <= len(values) and value_at(values, position) in rule.get("valores", [])


def validate_header(spec: dict, headers: list[str]) -> list[dict]:
    error = {"campo": "HEADER", "codigo": spec["codigos_especiales"]["header"], "nivel": "1", "causa": "HEADER"}
    if not headers:
        return [error]
    cols = split_row(headers[0].replace("\ufeff", ""), spec["archivo"]["delimitador"])
    if len(cols) != len(spec["campos"]):
        return [dict(error, causa="COLUMNAS")]
    ok = all(comparable(value_at(cols, c["posicion"])) in {comparable(a) for a in [c["nombre"], *c["alias"]]}
             for c in spec["campos"])
    return [] if ok else [error]


def validate_name(spec: dict, path: str, today: date | None = None) -> list[dict]:
    """Réplica de RecordValidator.validarNombre: regex, periodo, marca de tiempo y offset que acepta Java
    (AUD-V-16) y, si la spec las activa, las reglas de fecha (AUD-V-02) con el reloj `today` (fin del día en la
    zona de la fuente; sin `today`, la fecha de hoy)."""
    if nombre_valido_el_dia(spec, path, today or date.today()):
        return []
    return [{"campo": "FILENAME", "codigo": spec["codigos_especiales"]["nombre_archivo"], "nivel": "1",
             "causa": "NOMBRE_ARCHIVO"}]


def duplicate_key(spec: dict, row: str) -> tuple:
    values = split_row(row, spec["archivo"]["delimitador"])
    fields = field_by_name(spec)
    parts = [normalize(value_at(values, fields[n]["posicion"]), fields[n].get("normalizacion", []))
             for n in spec["duplicados"]["clave"]]
    if not parts or any(not p for p in parts):
        return ("SIN_CLAVE", row)  # nunca agrupa registros sin documento
    return tuple(parts)


def as_of_date(spec: dict, row: str) -> date:
    field = field_by_name(spec)[spec["guard_rails"]["as_of_date"]]
    parsed = parse_date(value_at(split_row(row, spec["archivo"]["delimitador"]), field["posicion"]),
                        field.get("formatos_fecha", []))
    return parsed.date() if parsed else date.min


# ------------------------------------------------------------------------------- DSL mínimo
_TOKEN_RE = re.compile(r'\s*(\(|\)|"[^"]*"|,|[A-Za-z_]+|\S)')
_FUNCS = {
    "errorcodeIn": ("codigo", True), "errorcodeNotIn": ("codigo", False),
    "errorcodeInList": ("codigo", True), "errorcodeNotInList": ("codigo", False),
    "errorlevelIn": ("nivel", True), "errorlevelNotIn": ("nivel", False),
    "errorlevelInList": ("nivel", True), "errorlevelNotInList": ("nivel", False),
}


def eval_dsl(expression: str, errors: list[dict]) -> bool:
    """Evalúa errorcode*/errorlevel* con and/or/not y paréntesis, con la semántica de SUBCADENA del motor."""
    tokens = [t for t in _TOKEN_RE.findall(expression) if t.strip()]
    pos = 0

    def peek() -> str | None:
        return tokens[pos] if pos < len(tokens) else None

    def take(expected: str | None = None) -> str:
        nonlocal pos
        token = peek()
        if token is None or (expected is not None and token != expected):
            raise SpecError(f"expresión DSL no soportada localmente: {expression!r}")
        pos += 1
        return token

    def atom() -> bool:
        token = take()
        if token == "(":
            result = disjunction()
            take(")")
            return result
        if token == "not":
            return not atom()
        if token in ("true", "false"):
            return token == "true"
        if token in _FUNCS:
            take("(")
            args = [take().strip('"')]
            while peek() == ",":
                take(",")
                args.append(take().strip('"'))
            take(")")
            attr, inside = _FUNCS[token]
            joined = ",".join(e[attr] for e in errors)
            return all((a in joined) == inside for a in args)
        raise SpecError(f"expresión DSL no soportada localmente: {expression!r}")

    def conjunction() -> bool:
        result = atom()
        while peek() == "and":
            take("and")
            right = atom()
            result = result and right
        return result

    def disjunction() -> bool:
        result = conjunction()
        while peek() == "or":
            take("or")
            right = conjunction()
            result = result or right
        return result

    value = disjunction()
    if peek() is not None:
        raise SpecError(f"expresión DSL no soportada localmente: {expression!r}")
    return value


# ------------------------------------------------------------------------------------ flujo
def marker(name: str, errors: list[dict], event_type: int) -> dict:
    return {"marcador": name, "eventType": event_type, "codigos": [e["codigo"] for e in errors]}


def check_supported(spec: dict) -> None:
    """Guarda (plan #6, P-2): el oráculo no modela E8; con footer declarado no simula en silencio."""
    footer_rows = spec["archivo"].get("filas_footer") or 0
    if footer_rows > 0:
        raise SpecError(f"la simulación local no modela E8 (footer): el source-spec declara filas_footer = "
                        f"{footer_rows} y el footer se validaría como un registro; no se simula (AC-24)")


def prevalidate(spec: dict, path: Path, catalog: Catalog, today: date, detail: bool = False) -> dict:
    check_supported(spec)
    lines = path.read_text(encoding="utf-8").splitlines()
    summary: dict = {"archivo": path.name, "simulacion": True, "compilacion": "no certificada (F-01)"}
    if not lines:
        summary.update(resultado="FALLA_JOB", motivo="archivo totalmente vacío: OxygenException('File Empty Exception')")
        return summary
    name_errors = validate_name(spec, str(path), today)
    summary["nombre_valido"] = not name_errors
    if name_errors and not eval_dsl(spec["diagnostico_archivo"]["fileName"], name_errors):
        summary.update(resultado="MARCADOR", **marker("errorInFileName", name_errors, -1002))
        return summary
    header_rows = spec["archivo"]["filas_header"]
    header_errors = validate_header(spec, lines[:header_rows])
    summary["header_valido"] = not header_errors
    if header_errors and not eval_dsl(spec["diagnostico_archivo"]["header"], header_errors):
        summary.update(resultado="MARCADOR", **marker("errorInHeader", header_errors, -1003))
        return summary

    records, filtered = [], 0
    for number, row in enumerate(lines, start=1):
        if is_header_row(spec, row):
            continue
        if should_filter(spec, row):
            filtered += 1
            continue
        errors = validate_record(spec, row, catalog, today)
        records.append({"linea": number, "row": row, "errores": errors, "qs": quality_scores(spec, errors),
                        "duplicado": False})
    summary["filas_filtradas"] = filtered
    if not records:
        summary.update(resultado="MARCADOR", **marker("errorInEmptyFileBody", [], -1005))
        return summary

    dup = spec["duplicados"]
    if dup["habilitado"]:
        groups: dict[tuple, list[dict]] = {}
        for rec in records:
            groups.setdefault(duplicate_key(spec, rec["row"]), []).append(rec)
        for group in groups.values():
            if len(group) < 2:
                continue
            if dup["modo"] == "EXTN":
                # ASSUMPTION F-14: más reciente por fecha; el motor desempata por eventId, aquí por número de línea.
                keep = max(group, key=lambda r: (as_of_date(spec, r["row"]), r["linea"]))
                to_mark = [r for r in group if r is not keep]
            elif dup["modo"] == "RANDOM":
                to_mark = group[1:]
            else:
                to_mark = group
            for rec in to_mark:
                rec["duplicado"] = True

    counts: dict[str, int] = {}
    for rec in records:
        for code in [e["codigo"] for e in rec["errores"]] + ([DUPLICATE_MARK] if rec["duplicado"] else []):
            counts[code] = counts.get(code, 0) + 1
    qs_names = [q["nombre"] for q in spec["quality_scores"]]
    averages = {n: round(sum(r["qs"][n] for r in records) / len(records), 6) for n in qs_names}
    accepted = [r for r in records if not r["duplicado"] and all(e["nivel"] != "1" for e in r["errores"])]
    threshold = spec["handoff_acceptance"]["umbral_rqs"]
    first_qs = qs_names[0]
    summary.update(
        resultado="VALIDADO",
        registros_validados=len(records),
        registros_con_error_sev1=sum(1 for r in records if any(e["nivel"] == "1" for e in r["errores"])),
        duplicados_marcados=sum(1 for r in records if r["duplicado"]),
        conteo_por_codigo=dict(sorted(counts.items())),
        promedio_quality_scores=averages,
        vista_previa_acceptance={
            "umbral": threshold,
            "archivo": "ACEPTARIA" if averages[first_qs] >= threshold else "RECHAZARIA",
            "registros_aceptados": len(accepted),
            "nota": "simulación de la regla de handoff; la decisión real la toma Acceptance",
        },
    )
    if detail:
        summary["detalle"] = [{"linea": r["linea"], "codigos": [e["codigo"] for e in r["errores"]],
                               "duplicado": r["duplicado"]} for r in records]
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--spec", required=True, type=Path)
    parser.add_argument("--archivo", required=True, type=Path)
    parser.add_argument("--lookups", type=Path, help="carpeta con los CSV de catálogo (default: filas sintéticas)")
    parser.add_argument("--hoy", help="fecha de referencia AAAA-MM-DD (default: hoy); es también el reloj de las "
                                      "reglas de fecha del nombre: fin de ese día en la zona de la fuente")
    parser.add_argument("--salida", type=Path, help="escribe el resumen JSON en este archivo")
    parser.add_argument("--detalle", action="store_true", help="incluye línea → códigos (sin valores)")
    args = parser.parse_args(argv)
    try:
        spec = load_spec(args.spec)
        errors = verify_spec(args.spec)
        if errors:
            print("El source-spec NO pasa la verificación:", file=sys.stderr)
            for error in errors:
                print(f"  - {error}", file=sys.stderr)
            return 2
        today = date.fromisoformat(args.hoy) if args.hoy else date.today()
        catalog = Catalog.from_dir(spec, args.lookups) if args.lookups else Catalog.from_spec(spec)
        summary = prevalidate(spec, args.archivo, catalog, today, args.detalle)
    except (SpecError, OSError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    text = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.salida:
        args.salida.parent.mkdir(parents=True, exist_ok=True)
        args.salida.write_text(text + "\n", encoding="utf-8", newline="\n")
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
