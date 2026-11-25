#!/usr/bin/env python3
"""Genera los artefactos de la etapa Validation (EDF/Oxygen) desde un source-spec.

Generador VIGENTE de la skill (desde la versión 1.4.0 del framework, 2026-11-18). No requiere compilar nada:
solo Python 3 con PyYAML y jsonschema. Es la traducción fiel del generador Java deprecado que se usó hasta la
1.3.x (scripts/java, conservado como referencia histórica, no se ejecuta): misma salida byte a byte, incluido
manifest.json. Desde la auditoría 2026-11-24 (AUD-V1..AUD-V8) la salida evoluciona solo aquí.
1.9.2 (corrida E2E SISBEN, AUD-V-01..AUD-V-21): filePattern en validate[]/metrics[], lookupHome en los lookups,
valor recibido en validatedRecordJson, reglas de fecha del nombre, regla solo_ceros, supuestos_resueltos, grupos de
duplicados y nombres inválidos en el fixture, códigos de error en docs/ y nombre de la skill en el manifest.

Uso:
    python render_templates.py --spec source-spec.yaml --out carpeta_salida
    python render_templates.py --spec source-spec.yaml --solo-verificar

Pasos: (1) carga y valida el source-spec contra el esquema (draft 2020-12); (2) verificaciones semánticas
(códigos de longitud fija y sin colisiones de subcadena, quality scores, métricas, referencias de catálogo,
guard-rails, contrato de archivo, fixture); (3) arma en memoria las plantillas rellenas, los CSV de catálogo
y manifest.json (archivos, sha256 y supuestos derivados de las marcas ASSUMPTION); (4) falla sin escribir
nada si queda algún hueco sin resolver, una marca ASSUMPTION sin ID o un patrón prohibido (credenciales);
(5) si no hay problemas, escribe todo.

No compila, no ejecuta nada en el clúster y no modifica repositorios: solo escribe en --out.
Códigos de salida: 0 bien; 2 spec inválida, error de generación o de uso; 1 error inesperado.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from typing import Any, Callable

import yaml

SKILL_NAME = "edf-validation"  # nombre de la skill (SKILL.md); manifest y cabeceras (AUD-V-10)
SKILL_DIR = Path(__file__).resolve().parent.parent
TEMPLATES_DIR = SKILL_DIR / "assets" / "templates"
SCHEMA_PATH = SKILL_DIR / "assets" / "schema" / "source-spec.schema.yaml"

PLACEHOLDER_RE = re.compile(r"\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}")
# ID de un supuesto de references/decisiones-y-abiertos.md (ADR-G1).
ID_SUPUESTO = r"F-\d{2}|VAL-P[AT]\d{2}|VAL-SP\d{2}"
# Marca válida: ASSUMPTION seguido del ID en la misma línea.
MARCA_RE = re.compile(r"\bASSUMPTION[ \t]+(" + ID_SUPUESTO + r")\b")
ASSUMPTION_RE = re.compile(r"\bASSUMPTION\b")
DUPLICATE_MARK = "OxygenDuplicate"
DSL_KEYWORDS = ("errorcodeIn", "errorcodeNotIn", "errorcodeInList", "errorcodeNotInList", "errorlevelIn",
                "errorlevelNotIn", "errorlevelInList", "errorlevelNotInList", "count(errors)", "and", "or",
                "not", "true", "false", DUPLICATE_MARK)
FORBIDDEN_PATTERNS = (
    re.compile(r"settings-artifactory", re.I),
    re.compile(r"<password>", re.I),
    re.compile(r"\bpassword\s*[=:]\s*\S", re.I),
    re.compile(r"\b(api[_-]?key|secret|token)\s*[=:]\s*['\"]?[A-Za-z0-9/+]{12,}", re.I),
    re.compile(r"AKIA[0-9A-Z]{16}"),
)
JAVA_DATE_TOKENS = {"dd": "%d", "MM": "%m", "yyyy": "%Y", "HH": "%H", "mm": "%M", "ss": "%S"}
# Caracteres con significado en DateTimeFormatter.ofPattern (sección opcional, reservados, literal): con ellos
# ofPattern lanza o cambia el sentido del patrón. Se rechazan al generar para que Fechas.parsear sea total (AUD-V2).
JAVA_DATE_RESERVADOS = set("[]{}#'")
# Sintaxis que acepta `re` de Python y que java.util.regex.Pattern rechaza (AUD-V4). Lista corta, no exhaustiva:
# los regex del contrato de archivo se compilan en Java (Capa A, processor y motor).
REGEX_SOLO_PYTHON = (
    ("(?P<", "grupo con nombre de Python; en Java es (?<nombre>...)"),
    ("(?P=", "referencia a grupo con nombre de Python; en Java es \\k<nombre>"),
    ("(?#", "comentario en línea"),
    ("(?(", "grupo condicional"),
)
QS_REF = re.compile(r"qs\.([A-Za-z0-9_]+)")

# Plantilla → ruta destino (relativa a --out). Los {{huecos}} de la ruta se rellenan con el contexto.
TEMPLATE_TARGETS: dict[str, str] = {
    "capa-a/FieldSpec.scala.tpl": "src/main/scala/{{ruta_comun}}/FieldSpec.scala",
    "capa-a/FieldValidators.scala.tpl": "src/main/scala/{{ruta_comun}}/FieldValidators.scala",
    "capa-a/FieldValidatorsSpec.scala.tpl": "src/test/scala/{{ruta_comun}}/FieldValidatorsSpec.scala",
    "SourceSpec.scala.tpl": "src/main/scala/{{ruta_fuente}}/config/{{Fuente}}Spec.scala",
    "ValidatedEvent.scala.tpl": "src/main/scala/{{ruta_fuente}}/{{Fuente}}ValidatedEvent.scala",
    "FileMetadata.scala.tpl": "src/main/scala/{{ruta_fuente}}/validator/{{Fuente}}FileMetadata.scala",
    "ValidationProcessor.scala.tpl": "src/main/scala/{{ruta_fuente}}/processor/{{Fuente}}ValidationProcessor.scala",
    "SourceProcessorSpec.scala.tpl": "src/test/scala/{{ruta_fuente}}/{{Fuente}}ValidationProcessorSpec.scala",
    "fixture.txt.tpl": "src/test/resources/{{fuente}}/{{fixture_nombre_archivo}}",
    "validation-block.conf.tpl": "env/application-conf/innersource/propuesta/validation-configs-{{fuente}}.conf",
    "lookups.conf.tpl": "env/application-conf/innersource/propuesta/lookups-{{fuente}}.conf",
    # Catálogo documental de códigos: fuera de la carpeta propuesta/ que se pega en el fork (AUD-V-19).
    "codigos-error.csv.tpl": "docs/codigos-error-{{fuente}}.csv",
}


class SpecError(Exception):
    """El source-spec no cumple el esquema o una regla semántica, o la generación encontró un problema."""


# --------------------------------------------------------------------------------------------- carga
def read_text(path: Path) -> str:
    """UTF-8 sin BOM, con \\r\\n y \\r normalizados a \\n."""
    return path.read_text(encoding="utf-8-sig")


def load_yaml(path: Path) -> Any:
    return yaml.safe_load(read_text(path))


def load_spec(path: Path) -> dict:
    data = load_yaml(path)
    if not isinstance(data, dict):
        raise SpecError(f"{path.name}: el source-spec debe ser un objeto YAML")
    return data


def validate_schema(spec: dict, schema_path: Path = SCHEMA_PATH) -> list[str]:
    """Errores de la spec contra el JSON Schema (draft 2020-12), ordenados por ruta."""
    try:
        import jsonschema  # type: ignore
    except ImportError as exc:  # sin jsonschema no se puede certificar la forma del spec
        raise SpecError("falta el paquete jsonschema: pip install jsonschema") from exc
    validator = jsonschema.Draft202012Validator(load_yaml(schema_path))
    found = []
    for error in validator.iter_errors(spec):
        path = "/".join(str(p) for p in error.absolute_path)
        found.append((path, f"{path or '(raíz)'}: {error.message}"))
    return [text for _, text in sorted(found, key=lambda f: f[0])]


# ------------------------------------------------------------------------------------ utilidades
def scala_str(value: str) -> str:
    """Contenido de un literal de cadena Scala (sin comillas)."""
    return value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "\\r")


def hocon_str(value: Any) -> str:
    """Contenido de una cadena HOCON entre comillas (mismas reglas que JSON)."""
    return json.dumps(value, ensure_ascii=False)[1:-1]


def scala_list(values: list) -> str:
    return ", ".join(f'"{scala_str(v)}"' for v in values)


def camel(name: str) -> str:
    """NOMBRE_EN_MAYUSCULAS -> nombreEnMayusculas."""
    parts = name.lower().split("_")
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def lower(value: Any) -> str:
    return str(value).lower()


def java_to_py_date(pattern: str) -> str:
    """Traduce un patrón de fecha Java (dd, MM, yyyy, HH, mm, ss) a strftime; falla con otra letra."""
    out, i = "", 0
    while i < len(pattern):
        for token, repl in JAVA_DATE_TOKENS.items():
            if pattern.startswith(token, i):
                out += repl
                i += len(token)
                break
        else:
            if pattern[i].isalpha() or pattern[i] in JAVA_DATE_RESERVADOS:
                raise SpecError(f"formato de fecha no soportado: {pattern!r}")
            out += pattern[i]
            i += 1
    return out


def all_codes(spec: dict) -> list[str]:
    """Códigos de campos, reglas y especiales, sin repetidos y en orden de aparición."""
    codes = [c["codigo"] for c in spec["campos"]]
    codes += [r["codigo"] for r in spec["reglas_entre_campos"]]
    codes += list(spec["codigos_especiales"].values())
    return list(dict.fromkeys(codes))


def field_by_name(spec: dict) -> dict[str, dict]:
    return {c["nombre"]: c for c in spec["campos"]}


def build_fixture_line(spec: dict, case: dict) -> str:
    """Línea cruda de un caso del fixture (registro base + cambios, o línea literal)."""
    if "linea" in case:
        return case["linea"]
    values = list(spec["fixture"]["registro_base"])
    fields = field_by_name(spec)
    for name, value in (case.get("cambios") or {}).items():
        values[fields[name]["posicion"] - 1] = value
    if "columnas" in case:
        values = values[: case["columnas"]]
    values += case.get("columnas_extra") or []
    return spec["archivo"]["delimitador"].join(values)


# ------------------------------------------------- nombre de archivo (réplica de RecordValidator, AUD-V-02/16)
# Reglas de fecha opcionales de archivo.nombre. Sin ellas (o en false) el nombre se valida como antes: regex,
# periodo AAAAMM legible, marca de tiempo legible y offset que acepta java.time.ZoneOffset.of.
REGLAS_NOMBRE = ("periodo_no_futuro", "recepcion_no_futura", "recepcion_desde_inicio_periodo")
REGLA_SCALA = {"periodo_no_futuro": "periodoNoFuturo", "recepcion_no_futura": "recepcionNoFutura",
               "recepcion_desde_inicio_periodo": "recepcionDesdeInicioPeriodo"}
# Formas que acepta java.time.ZoneOffset.of, por longitud del texto (sin contar la Z).
_OFFSET_FORMAS = {2: r"([+-])(\d)", 3: r"([+-])(\d\d)", 5: r"([+-])(\d\d)(\d\d)", 6: r"([+-])(\d\d):(\d\d)",
                  7: r"([+-])(\d\d)(\d\d)(\d\d)", 9: r"([+-])(\d\d):(\d\d):(\d\d)"}


def java_zone_offset(texto: str | None) -> timedelta | None:
    """Offset con las reglas de java.time.ZoneOffset.of: Z, ±h, ±hh, ±hhmm, ±hh:mm, ±hhmmss, ±hh:mm:ss; horas 0..18,
    minutos y segundos 0..59 y total dentro de ±18:00. None si Java lo rechazaría (p. ej. -2500, AUD-V-16)."""
    if texto == "Z":
        return timedelta(0)
    forma = _OFFSET_FORMAS.get(len(texto or ""))
    m = re.fullmatch(forma, texto) if forma else None
    if not m:
        return None
    partes = [int(x) for x in m.groups()[1:]] + [0, 0]
    horas, minutos, segundos = partes[:3]
    total = timedelta(hours=horas, minutes=minutos, seconds=segundos)
    if horas > 18 or minutos > 59 or segundos > 59 or total > timedelta(hours=18):
        return None
    return -total if m.group(1) == "-" else total


def info_nombre(spec: dict, ruta: str | None) -> dict | None:
    """Réplica de RecordValidator.parsearNombre: grupos del nombre (sin ruta) o None si no coincide, si el periodo
    o la marca de tiempo no son fechas válidas o si el offset no lo acepta Java."""
    nombre = re.split(r"[/\\]", ruta or "")[-1]
    rx = spec["archivo"]["nombre"]
    grupos = rx["grupos"]
    match = re.fullmatch(rx["regex"], nombre)
    if not match:
        return None
    periodo_txt, marca = match.group(grupos["periodo"]), match.group(grupos["marca_tiempo"])
    try:
        periodo = datetime.strptime(periodo_txt, "%Y%m")
        recibido = datetime.strptime(marca, "%Y%m%d%H%M%S")
    except (TypeError, ValueError):
        return None
    if periodo.strftime("%Y%m") != periodo_txt or recibido.strftime("%Y%m%d%H%M%S") != marca:
        return None
    offset = java_zone_offset(match.group(grupos["offset"]))
    if offset is None:
        return None
    return {"periodo": (periodo.year, periodo.month), "recibido": recibido, "offset": offset}


def ahora_de_referencia(spec: dict, hoy: date) -> datetime:
    """Reloj de un día (--hoy, fixture.fecha_referencia): fin del día (23:59:59) en la zona de la fuente. Es el mismo
    instante que usa la prueba Scala generada (`ahora`)."""
    from zoneinfo import ZoneInfo

    return datetime.combine(hoy, time(23, 59, 59), ZoneInfo(spec["fuente"]["zona_horaria"]))


def reglas_nombre_violadas(spec: dict, info: dict, ahora: datetime) -> list[str]:
    """Reglas de fecha de archivo.nombre activas que el nombre no cumple respecto de `ahora` (con zona)."""
    nombre = spec["archivo"]["nombre"]
    violadas = []
    if nombre.get("periodo_no_futuro"):
        try:
            from zoneinfo import ZoneInfo

            local = ahora.astimezone(ZoneInfo(spec["fuente"]["zona_horaria"]))
            futuro = info["periodo"] > (local.year, local.month)
        except Exception:  # noqa: BLE001 - como la Capa A: zona inválida → nombre inválido, sin excepción
            futuro = True
        if futuro:
            violadas.append("periodo_no_futuro")
    if nombre.get("recepcion_no_futura") and info["recibido"].replace(tzinfo=timezone(info["offset"])) > ahora:
        violadas.append("recepcion_no_futura")
    if nombre.get("recepcion_desde_inicio_periodo") and info["recibido"].date() < date(*info["periodo"], 1):
        violadas.append("recepcion_desde_inicio_periodo")
    return violadas


def nombre_valido(spec: dict, ruta: str, ahora: datetime) -> bool:
    """Réplica de RecordValidator.validarNombre(spec, ruta, ahora)."""
    info = info_nombre(spec, ruta)
    return info is not None and not reglas_nombre_violadas(spec, info, ahora)


def nombre_valido_el_dia(spec: dict, ruta: str, hoy: date) -> bool:
    """nombre_valido con el reloj de un día (ahora_de_referencia). Sin reglas de fecha activas no usa la zona ni el
    día, como la Capa A; con reglas y una zona inválida el nombre es inválido."""
    info = info_nombre(spec, ruta)
    if info is None:
        return False
    if not any(spec["archivo"]["nombre"].get(r) for r in REGLAS_NOMBRE):
        return True
    try:
        ahora = ahora_de_referencia(spec, hoy)
    except Exception:  # noqa: BLE001 - zona inexistente: nombre inválido, sin excepción (como la Capa A)
        return False
    return not reglas_nombre_violadas(spec, info, ahora)


def viola_solo(spec: dict, regla: str, ruta: str, hoy: date) -> bool:
    """AUD-V-28: con las TRES reglas de fecha activas, `ruta` viola exactamente `regla` con el reloj de `hoy`."""
    todas = {**spec, "archivo": {**spec["archivo"], "nombre": {**spec["archivo"]["nombre"],
                                                               **{r: True for r in REGLAS_NOMBRE}}}}
    info = info_nombre(todas, ruta)
    if info is None:
        return False
    try:
        ahora = ahora_de_referencia(spec, hoy)
    except Exception:  # noqa: BLE001 - zona inválida: no se puede aislar (check_name_rules lo informa aparte)
        return False
    return reglas_nombre_violadas(todas, info, ahora) == [regla]


def nombres_de_prueba(spec: dict) -> list[tuple[str, str, str, date]]:
    """(regla, descripción, nombre, día del reloj) SINTÉTICOS que violan cada regla de fecha activa, derivados del
    nombre del fixture cambiando solo los dígitos del periodo, de la marca de tiempo y, para RA-02, del offset.
    Vacío si no hay reglas activas.

    AUD-V-28: cada nombre viola SOLO su regla (con las tres activas). RA-02 (periodo no futuro) se aísla con un
    offset adelantado: periodo = mes siguiente al de referencia, recibido el 1.º de ese mes a las 00:00:00 con +1400
    y reloj = último día del mes de referencia (su instante cae antes del reloj y su fecha local no es anterior al
    periodo). Si el regex no admite ese nombre se usa el de antes (periodo un año después), que también viola RA-04."""
    nombre = spec["archivo"]["nombre"]
    activas = [r for r in REGLAS_NOMBRE if nombre.get(r)]
    fixture = spec["fixture"]["nombre_archivo"]
    match = re.fullmatch(nombre["regex"], fixture)
    if not activas or not match:
        return []
    grupos = nombre["grupos"]
    ref = date.fromisoformat(spec["fixture"]["fecha_referencia"])
    periodo = datetime.strptime(match.group(grupos["periodo"]), "%Y%m")

    def con(**valores: str) -> str:
        texto = fixture
        for grupo, valor in sorted(valores.items(), key=lambda kv: match.span(grupos[kv[0]])[0], reverse=True):
            i, j = match.span(grupos[grupo])
            texto = texto[:i] + valor + texto[j:]
        return texto

    siguiente = date(ref.year + ref.month // 12, ref.month % 12 + 1, 1)
    fin_de_mes = siguiente - timedelta(days=1)
    aislado = con(periodo=siguiente.strftime("%Y%m"), marca_tiempo=siguiente.strftime("%Y%m%d") + "000000",
                  offset="+1400")
    if viola_solo(spec, "periodo_no_futuro", "/ruta/sintetica/" + aislado, fin_de_mes):
        periodo_futuro = ("periodo posterior al mes de referencia (offset adelantado, solo esta regla)", aislado,
                          fin_de_mes)
    else:
        periodo_futuro = ("periodo posterior al mes de referencia",
                          con(periodo=f"{ref.year + 1:04d}{ref.month:02d}"), ref)
    candidatos = {
        "periodo_no_futuro": periodo_futuro,
        "recepcion_no_futura": ("recepción posterior a la fecha de referencia",
                                con(marca_tiempo=(ref + timedelta(days=3)).strftime("%Y%m%d") + "120000"), ref),
        "recepcion_desde_inicio_periodo": ("recepción anterior al primer día del periodo",
                                           con(marca_tiempo=(periodo - timedelta(days=1)).strftime("%Y%m%d") + "120000"),
                                           ref),
    }
    return [(regla, *candidatos[regla]) for regla in activas]


# ---------------------------------------------------------------------- verificaciones semánticas
def check_codes(spec: dict) -> list[str]:
    errors: list[str] = []
    codes = all_codes(spec)
    lengths = sorted({len(c) for c in codes})
    if len(lengths) != 1:
        errors.append(f"los códigos de error deben tener longitud fija; longitudes encontradas: {lengths}")
    errors += [f"código de 11 caracteres (lo reescribe decodeErrorObject): {c}" for c in codes if len(c) == 11]
    for a in codes:
        for b in codes:
            if a != b and a in b:
                errors.append(f"colisión de subcadena en el DSL: {a} está dentro de {b}")
        if a in DUPLICATE_MARK or DUPLICATE_MARK in a:
            errors.append(f"colisión de subcadena con {DUPLICATE_MARK}: {a}")
    field_codes = [c["codigo"] for c in spec["campos"]]
    if len(field_codes) != len(set(field_codes)):
        errors.append("cada campo debe tener un código propio (F-10)")
    return errors


def check_quality_scores(spec: dict) -> list[str]:
    errors = []
    diag = spec["diagnostico_archivo"]
    texts = [diag["fileName"], diag["header"], diag.get("footer") or "", spec["handoff_acceptance"]["record_acceptance"]]
    codes = all_codes(spec)
    for qs in spec["quality_scores"]:
        name = qs["nombre"]
        if any(name in kw for kw in DSL_KEYWORDS) or any(name in c for c in codes):
            errors.append(f"el nombre de quality score {name!r} aparece dentro de un operador o código del DSL")
        if any(name in t for t in texts):
            errors.append(f"el nombre de quality score {name!r} aparece en una expresión de registro/archivo")
    return errors


def check_metrics(spec: dict) -> list[str]:
    """AC-VAL-54a: cada referencia qs.<X> de metricas[].expr nombra un quality score declarado."""
    errors = []
    declarados = {q["nombre"] for q in spec["quality_scores"]}
    for metrica in spec["metricas"]:
        for usado in dict.fromkeys(QS_REF.findall(metrica["expr"])):
            if usado not in declarados:
                errors.append(f"métrica {metrica['id']} usa el quality score no declarado {usado} (AC-VAL-54a)")
    return errors


def check_fields(spec: dict) -> list[str]:
    errors = []
    positions = [c["posicion"] for c in spec["campos"]]
    if sorted(positions) != list(range(1, len(positions) + 1)):
        errors.append("las posiciones de los campos deben ser 1..N sin huecos ni repetidos")
    names = [c["nombre"] for c in spec["campos"]]
    if len(names) != len(set(names)):
        errors.append("nombres de campo repetidos")
    for c in spec["campos"]:
        if c["tipo"] == "fecha" and not c.get("formatos_fecha"):
            errors.append(f"{c['nombre']}: un campo fecha requiere formatos_fecha")
        for fmt in c.get("formatos_fecha") or []:
            try:
                java_to_py_date(fmt)
            except SpecError as exc:
                errors.append(f"{c['nombre']}: {exc}")
        if bool(c["obligatorio"]) != (c["severidad"] == "1"):
            errors.append(f"{c['nombre']}: obligatorio ⇒ severidad 1 y opcional ⇒ severidad 3")
    return errors


def check_references(spec: dict) -> list[str]:
    errors = []
    fields = field_by_name(spec)
    catalogs = {c["tabla"]: c for c in spec["catalogos"]}

    def need_field(name: str | None, where: str, tipo: str | None = None) -> None:
        if name is None:
            return
        if name not in fields:
            errors.append(f"{where}: campo inexistente {name}")
        elif tipo is not None and fields[name]["tipo"] != tipo:
            errors.append(f"{where}: {name} debe ser de tipo {tipo}")

    def need_column(tabla: str, columna: str, where: str, key: bool) -> None:
        cat = catalogs.get(tabla)
        if cat is None:
            errors.append(f"{where}: tabla {tabla} no declarada en catalogos")
        elif columna not in cat["columnas"]:
            errors.append(f"{where}: columna {columna} no existe en {tabla}")
        elif key and columna not in cat["keys"]:
            errors.append(f"{where}: columna {columna} debe estar en keys de {tabla} (consulta por lookup)")

    for c in spec["campos"]:
        if "catalogo" in c:
            need_column(c["catalogo"]["tabla"], c["catalogo"]["columna"], c["nombre"], key=True)
    for r in spec["reglas_entre_campos"]:
        if r["tipo"] == "correspondencia_catalogo":
            need_field(r["campo_codigo"], r["id"])
            need_field(r["campo_nombre"], r["id"])
            need_column(r["tabla"], r["columna_clave"], r["id"], key=True)
            need_column(r["tabla"], r["columna_nombre"], r["id"], key=False)
        else:
            need_field(r.get("campo"), r["id"])
    for cat in spec["catalogos"]:
        archivo = cat["archivo"]
        if archivo.startswith("/") or ".." in archivo.split("/") or re.search(r"[\\\"$]", archivo) or not archivo:
            errors.append(f"{cat['tabla']}: archivo debe ser una ruta relativa a lookupHome, sin '/' inicial, '..', '\\\\', "
                          f"comillas ni '$' (AUD-V-06): {archivo!r}")
        for key in cat["keys"]:
            if key not in cat["columnas"]:
                errors.append(f"{cat['tabla']}: key {key} no es una columna")
        for row in cat["filas_sinteticas"]:
            if len(row) != len(cat["columnas"]):
                errors.append(f"{cat['tabla']}: fila sintética con {len(row)} valores (esperado {len(cat['columnas'])})")
    gr = spec["guard_rails"]
    need_field(gr.get("subscriber_relation"), "guard_rails.subscriber_relation")
    need_field(gr.get("as_of_date"), "guard_rails.as_of_date", tipo="fecha")
    need_field(spec["filtro_filas"].get("campo"), "filtro_filas.campo")
    dup = spec["duplicados"]
    if dup["habilitado"]:
        if not dup.get("clave"):
            errors.append("duplicados.habilitado exige una clave no vacía (assert(false) del motor)")
        for name in dup.get("clave") or []:
            need_field(name, "duplicados.clave")
        if dup["modo"] == "EXTN":
            need_field(dup.get("campo_fecha"), "duplicados.campo_fecha", tipo="fecha")
    return errors


def regex_solo_python(regex: str, donde: str) -> list[str]:
    """AUD-V4: sintaxis de REGEX_SOLO_PYTHON en un regex que se compilará con java.util.regex.Pattern."""
    return [f"{donde}: sintaxis que Java no acepta ({descripcion}): {token}"
            for token, descripcion in REGEX_SOLO_PYTHON if token in regex]


def check_footer(spec: dict, footer_re: re.Pattern | None) -> list[str]:
    """AUD-V3: con filas_footer > 0, código, expresión validate[].footer y filas de ejemplo que cumplen el regex."""
    archivo, fixture = spec["archivo"], spec["fixture"]
    filas = archivo["filas_footer"]
    if filas == 0:
        return []
    errors = []
    if not archivo.get("footer_regex"):
        errors.append("filas_footer > 0 exige footer_regex")
    if not spec["codigos_especiales"].get("footer"):
        errors.append("filas_footer > 0 exige codigos_especiales.footer (código del footer)")
    if not spec["diagnostico_archivo"].get("footer"):
        errors.append("filas_footer > 0 exige diagnostico_archivo.footer (expresión validate[].footer, F-11)")
    ejemplo = fixture.get("footer") or []
    if not ejemplo:
        errors.append("filas_footer > 0 exige fixture.footer (filas de footer de ejemplo)")
    elif len(ejemplo) != filas:
        errors.append(f"fixture.footer tiene {len(ejemplo)} filas (filas_footer = {filas})")
    if footer_re is not None:
        errors += [f"fixture.footer: {fila!r} no cumple archivo.footer_regex"
                   for fila in ejemplo if not footer_re.fullmatch(fila.strip())]
        if footer_re.fullmatch(archivo["delimitador"].join(fixture["registro_base"]).strip()):
            errors.append("fixture.registro_base cumple archivo.footer_regex: el motor lo tomaría como footer")
    return errors


def check_file_contract(spec: dict) -> list[str]:
    errors = []
    archivo = spec["archivo"]
    footer_regex = archivo.get("footer_regex") if archivo["filas_footer"] > 0 else None
    regexes = {"archivo.nombre.regex": archivo["nombre"]["regex"], "archivo.file_pattern": archivo["file_pattern"],
               "archivo.footer_regex": footer_regex or ""}
    for donde, regex in regexes.items():
        errors += regex_solo_python(regex, donde)
    try:
        name_re = re.compile(archivo["nombre"]["regex"])
        for group, idx in archivo["nombre"]["grupos"].items():
            if idx > name_re.groups:
                errors.append(f"archivo.nombre.grupos.{group} = {idx} excede los grupos del regex")
        pattern_re = re.compile(archivo["file_pattern"])
        footer_re = re.compile(footer_regex) if footer_regex else None
    except re.error as exc:
        return errors + [f"regex inválido: {exc}"]
    errors += check_footer(spec, footer_re)
    fixture_name = spec["fixture"]["nombre_archivo"]
    if not name_re.fullmatch(fixture_name):
        errors.append("fixture.nombre_archivo no cumple archivo.nombre.regex")
    if not pattern_re.fullmatch("/ruta/sintetica/" + fixture_name):
        errors.append("fixture.nombre_archivo no cumple archivo.file_pattern (ruta completa)")
    if re.search(r"[\"\\/]", fixture_name):
        errors.append("fixture.nombre_archivo no puede contener comillas, barras ni rutas")
    return errors + check_name_rules(spec)


def check_name_rules(spec: dict) -> list[str]:
    """AUD-V-02/AUD-V-09: el nombre del fixture cumple las reglas de fecha activas a la fecha de referencia; cada
    nombre de prueba derivado y cada fixture.nombres_invalidos se rechaza con la réplica Python de la Capa A."""
    errors: list[str] = []
    fixture = spec["fixture"]
    try:
        ahora = ahora_de_referencia(spec, date.fromisoformat(fixture["fecha_referencia"]))
    except Exception as exc:  # noqa: BLE001 - zona u hoy inválidos: se informa como error de la spec
        activas = [r for r in REGLAS_NOMBRE if spec["archivo"]["nombre"].get(r)]
        return [f"fuente.zona_horaria o fixture.fecha_referencia inválidas para las reglas del nombre: {exc}"] \
            if activas or fixture.get("nombres_invalidos") else []
    nombre = "/ruta/sintetica/" + fixture["nombre_archivo"]
    info = info_nombre(spec, nombre)
    if info is None:
        errors.append("fixture.nombre_archivo: periodo, marca de tiempo u offset inválidos (Java lo rechazaría)")
    else:
        errors += [f"fixture.nombre_archivo no cumple archivo.nombre.{r} a la fecha de referencia"
                   for r in reglas_nombre_violadas(spec, info, ahora)]
    for regla, _, prueba, hoy in nombres_de_prueba(spec):
        if nombre_valido(spec, prueba, ahora_de_referencia(spec, hoy)):
            errors.append(f"archivo.nombre.{regla}: no se pudo derivar del fixture un nombre que la viole ({prueba})")
    for invalido in fixture.get("nombres_invalidos") or []:
        if nombre_valido(spec, invalido, ahora):
            errors.append(f"fixture.nombres_invalidos: {invalido} es válido con el regex y las reglas del nombre")
    return errors


MIEMBROS_FILE_METADATA = {"fileName", "headerRowCount", "dataSource", "subscriber", "receivedDate", "fileAsOfDate",
                          "fileTag", "fileType", "minFrequency", "subSubscriber", "additionalInformation"}


def check_metadata(spec: dict) -> list[str]:
    """archivo.metadata: miembros_adicionales sin repetir ni pisar los que ya genera la plantilla."""
    errors = []
    nombres = [m["miembro"] for m in (spec["archivo"].get("metadata") or {}).get("miembros_adicionales") or []]
    for nombre in sorted(set(nombres)):
        if nombres.count(nombre) > 1:
            errors.append(f"archivo.metadata.miembros_adicionales: {nombre} repetido")
        if nombre in MIEMBROS_FILE_METADATA:
            errors.append(f"archivo.metadata.miembros_adicionales: {nombre} ya existe en FileMetadata; "
                          "use la clave propia de archivo.metadata o no lo declare")
    return errors


def check_fixture(spec: dict) -> list[str]:
    errors = []
    fields = field_by_name(spec)
    if len(spec["fixture"]["registro_base"]) != len(spec["campos"]):
        errors.append("fixture.registro_base debe tener un valor por campo")
    codes = all_codes(spec)
    for case in spec["fixture"]["casos"]:
        for name in case.get("cambios") or {}:
            if name not in fields:
                errors.append(f"fixture {case['id']}: campo inexistente {name}")
        for code in case["esperado"].get("codigos") or []:
            if code not in codes:
                errors.append(f"fixture {case['id']}: código esperado no declarado {code}")
    ids = [c["id"] for c in spec["fixture"]["casos"]]
    if len(ids) != len(set(ids)):
        errors.append("ids de casos del fixture repetidos")
    return errors


def check_supuestos_resueltos(spec: dict) -> list[str]:
    """AUD-V-08: un ID de supuestos_resueltos aparece una sola vez y no está a la vez en la lista `supuestos` de
    un campo, regla o catálogo (sería resuelto y abierto a la vez)."""
    errors = []
    ids = [r["id"] for r in spec.get("supuestos_resueltos") or []]
    errors += [f"supuestos_resueltos: {i} repetido" for i in sorted({i for i in ids if ids.count(i) > 1})]
    abiertos = {s for grupo in ("campos", "reglas_entre_campos", "catalogos")
                for item in spec[grupo] for s in item.get("supuestos") or []}
    abiertos.update(spec["archivo"]["nombre"].get("supuestos") or [])  # HF-13
    errors += [f"supuestos_resueltos: {i} también figura como supuesto abierto de un campo, regla, catálogo o archivo.nombre"
               for i in sorted(set(ids) & abiertos)]
    return errors


def verify_spec(spec: dict, schema_path: Path = SCHEMA_PATH) -> list[str]:
    """Esquema + reglas semánticas. Devuelve todos los errores encontrados."""
    errors = validate_schema(spec, schema_path)
    if errors:
        return errors  # sin forma válida, las reglas semánticas no son confiables
    for check in (check_fields, check_codes, check_quality_scores, check_metrics, check_references,
                  check_file_contract, check_metadata, check_fixture, check_supuestos_resueltos):
        errors += check(spec)
    return errors


# ------------------------------------------------------------------------------------- contexto
SEVERIDAD_SCALA = {"1": "Severidad.Error", "3": "Severidad.Inconsistencia"}
TIPO_SCALA = {"alfanumerico": "TipoDato.Alfanumerico", "numerico": "TipoDato.Numerico", "fecha": "TipoDato.Fecha"}
NORMALIZACION_SCALA = {"quitar_espacios": "Normalizacion.QuitarEspacios",
                       "quitar_ceros_izquierda": "Normalizacion.QuitarCerosIzquierda"}
DEDUP_VERSION = {"EXTN": "EXTN", "RANDOM": "RANDOM", "AS-IS": ""}


def _opt(value: Any, render: Callable[[Any], str]) -> str:
    return "None" if value is None else f"Some({render(value)})"


def render_marcas_supuesto(item: dict, sangria: str) -> str:
    """Una línea // ASSUMPTION <ID>: ... por cada ID de la lista opcional `supuestos` del elemento (ADR-G1)."""
    return "".join(f"{sangria}// ASSUMPTION {i}: valor supuesto en el source-spec (references/decisiones-y-abiertos.md)\n"
                   for i in item.get("supuestos") or [])


def render_fieldspec(c: dict) -> str:
    alias = [c["nombre"]] + [a for a in c["alias"] if a != c["nombre"]]
    rango = _opt(c.get("rango"), lambda r: f"RangoEntero({r[0]}L, {r[1]}L)")
    dominio = _opt(c.get("dominio"), lambda d: f"Set({scala_list(d)})")
    catalogo = _opt(c.get("catalogo"),
                    lambda k: f'CatalogoRef("{scala_str(k["tabla"])}", "{scala_str(k["columna"])}")')
    normal = ", ".join(NORMALIZACION_SCALA[n] for n in c.get("normalizacion") or [])
    return (render_marcas_supuesto(c, "    ")
            + f"    FieldSpec(nombre = {c['nombre']}, posicion = {c['posicion']}, aliasHeader = Seq({scala_list(alias)}),\n"
            f"      obligatorio = {lower(c['obligatorio'])}, severidad = {SEVERIDAD_SCALA[c['severidad']]}, "
            f"tipo = {TIPO_SCALA[c['tipo']]},\n"
            f"      longitudMax = {_opt(c.get('longitud_max'), str)}, rango = {rango}, dominio = {dominio},\n"
            f"      catalogo = {catalogo}, formatosFecha = Seq({scala_list(c.get('formatos_fecha') or [])}), "
            f"noFutura = {lower(c.get('no_futura', False))},\n"
            f"      normalizacion = Seq({normal}), codigoError = \"{scala_str(c['codigo'])}\")")


def render_regla(r: dict) -> str:
    sev = SEVERIDAD_SCALA[r["severidad"]]
    marcas = render_marcas_supuesto(r, "    ")
    if r["tipo"] == "correspondencia_catalogo":
        return (marcas
                + f"    CorrespondenciaCatalogo(id = \"{scala_str(r['id'])}\", campoCodigo = {r['campo_codigo']}, "
                f"campoNombre = {r['campo_nombre']},\n"
                f"      tabla = \"{scala_str(r['tabla'])}\", columnaClave = \"{scala_str(r['columna_clave'])}\", "
                f"columnaNombre = \"{scala_str(r['columna_nombre'])}\",\n"
                f"      codigo = \"{scala_str(r['codigo'])}\", severidad = {sev})")
    if r["tipo"] == "solo_ceros":
        return (marcas
                + f"    SoloCeros(id = \"{scala_str(r['id'])}\", campo = {r['campo']}, "
                f"codigo = \"{scala_str(r['codigo'])}\", severidad = {sev}, causa = \"{scala_str(r['causa'])}\")")
    return (marcas
            + f"    ValorConCodigoPropio(id = \"{scala_str(r['id'])}\", campo = {r['campo']}, "
            f"valores = Set({scala_list(r['valores'])}),\n"
            f"      codigo = \"{scala_str(r['codigo'])}\", severidad = {sev}, causa = \"{scala_str(r['causa'])}\")")


def render_lookup(cat: dict) -> str:
    keys = ",\n".join(f'      {{ index = "{hocon_str(k)}" }}' for k in cat["keys"])
    return ("  {\n"
            f"    tableName = \"{hocon_str(cat['tabla'])}\"\n"
            f"    file = ${{oxygen.application.paths.lookupHome}}\"/{hocon_str(cat['archivo'])}\"\n"
            "    fileFormat = \"csv\"\n"
            f"    delimiter = \"{hocon_str(cat['delimitador'])}\"\n"
            f"    keys = [\n{keys}\n    ]\n"
            "  }")


def render_metric(m: dict) -> str:
    return ("        {\n"
            f"          metricId = \"{hocon_str(m['id'])}\"\n"
            f"          metricExpr = \"{hocon_str(m['expr'])}\"\n"
            "        }")


def render_field_tests(spec: dict) -> str:
    return "\n\n".join(
        f"  test(\"{c['nombre']} inválido produce {c['codigo']}\") {{\n"
        f"    assert(codigos(conValor({c['posicion']}, \"{scala_str(c['ejemplo_invalido'])}\")).contains(\"{c['codigo']}\"))\n"
        "  }"
        for c in spec["campos"])


def render_dup_test(spec: dict, grupo: str | None, dup_lines: list[str], dup_flags: list[bool]) -> str:
    """Prueba de un grupo de duplicados del fixture. Sin `grupo_duplicado` el texto es el de siempre."""
    kept = dup_flags.index(False) if False in dup_flags else -1
    lines = ", ".join(f'"{line}"' for line in dup_lines)
    delim = scala_str(spec["archivo"]["delimitador"])
    titulo = "fixture duplicados" + (f" (grupo {scala_str(grupo)})" if grupo else "")
    return (
        f"  test(\"{titulo}: misma clave y se conserva el más reciente (ASSUMPTION F-14)\") {{\n"
        f"    val grupo = Seq({lines})\n"
        f"    val claves = grupo.map(l => RecordValidator.claveDuplicado(spec, Campos.separar(l, \"{delim}\")).toSeq)\n"
        "    assert(claves.distinct.size == 1)\n"
        "    val fechas = grupo.map(l => RecordValidator.asOfDate(spec, l).getOrElse(0L))\n"
        "    val candidatos = fechas.zipWithIndex.map { case (f, i) => (f, \"%05d\".format(i)) }\n"
        f"    assert(RecordValidator.indiceAConservar(candidatos) == {kept})\n"
        "  }")


def render_fixture_tests(spec: dict) -> str:
    blocks: list[str] = []
    # grupo_duplicado (opcional, AUD-V-09) → (líneas, marcas); sin la clave todos van al grupo None, como antes.
    dup_groups: dict[str | None, tuple[list[str], list[bool]]] = {}
    for case in spec["fixture"]["casos"]:
        line = scala_str(build_fixture_line(spec, case))
        title = scala_str(f"fixture {case['id']}: {case['descripcion']}")
        esperado = case["esperado"]
        if esperado.get("filtrado"):
            body = f"    assert(RecordValidator.debeFiltrar(spec, \"{line}\"))"
        else:
            body = (f"    assert(!RecordValidator.debeFiltrar(spec, \"{line}\"))\n"
                    f"    assert(codigos(\"{line}\") == Seq({scala_list(esperado.get('codigos') or [])}))")
        blocks.append(f"  test(\"{title}\") {{\n{body}\n  }}")
        if "duplicado" in esperado:
            lineas, marcas = dup_groups.setdefault(esperado.get("grupo_duplicado"), ([], []))
            lineas.append(line)
            marcas.append(bool(esperado["duplicado"]))
    for grupo, (lineas, marcas) in dup_groups.items():
        blocks.append(render_dup_test(spec, grupo, lineas, marcas))
    return "\n\n".join(blocks) + render_footer_test(spec)


def render_name_tests(spec: dict) -> str:
    """Pruebas del nombre de archivo que dependen de la spec (AUD-V-02, AUD-V-09): una por regla de fecha activa
    (nombre derivado del fixture) y una por fixture.nombres_invalidos. Vacío si no hay ninguna."""
    blocks = []
    ref = date.fromisoformat(spec["fixture"]["fecha_referencia"])
    for regla, descripcion, nombre, hoy in nombres_de_prueba(spec):
        ruta = f"\"/ruta/sintetica/{scala_str(nombre)}\""
        reloj = "ahora"
        cuerpo = ""
        if hoy != ref:
            reloj = "reloj"
            cuerpo = (f"    val reloj = java.time.LocalDate.of({hoy.year}, {hoy.month}, {hoy.day}).atTime(23, 59, 59)"
                      ".atZone(java.time.ZoneId.of(spec.zonaHoraria)).toInstant\n")
        cuerpo += (f"    assert(RecordValidator.validarNombre(spec, {ruta}, {reloj}).map(_.codigo) == "
                   "Seq(spec.codigos.nombreArchivo))\n")
        if viola_solo(spec, regla, "/ruta/sintetica/" + nombre, hoy):
            # AUD-V-28: sin esa regla el nombre es válido, así que la prueba aísla la regla.
            sin = f"spec.nombreArchivo.copy({REGLA_SCALA[regla]} = false)"
            cuerpo += (f"    assert(RecordValidator.validarNombre(spec.copy(nombreArchivo = {sin}), {ruta}, {reloj}).isEmpty, "
                       "\"sin la regla el nombre es válido: la prueba la aísla (AUD-V-28)\")\n")
        blocks.append(
            f"  test(\"nombre de archivo con {scala_str(descripcion)} produce el código de nombre ({regla})\") {{\n"
            f"{cuerpo}"
            "  }")
    invalidos = spec["fixture"].get("nombres_invalidos") or []
    if invalidos:
        lista = ", ".join(f'"{scala_str(n)}"' for n in invalidos)
        blocks.append(
            "  test(\"nombres de archivo inválidos declarados en el fixture producen el código de nombre\") {\n"
            f"    for (nombre <- Seq({lista}))\n"
            "      assert(RecordValidator.validarNombre(spec, \"/ruta/sintetica/\" + nombre, ahora).map(_.codigo) == "
            "Seq(spec.codigos.nombreArchivo), nombre)\n"
            "  }")
    return "".join("\n\n" + b for b in blocks)


def render_footer(spec: dict) -> dict[str, str]:
    """AUD-V3/AUD-V4: piezas del footer (E8). Con filas_footer = 0 no se emite nada (la salida no cambia).

    El patrón se compila una vez al crear el processor (java.util.regex.Pattern, validado al generar con
    REGEX_SOLO_PYTHON): isFooterRow no necesita Try y un patrón inválido falla en voz alta, no fila por fila."""
    archivo = spec["archivo"]
    filas = archivo["filas_footer"]
    if filas == 0:
        return {"is_footer_row_expr": "false", "bloque_validate_footer": "", "linea_validate_footer": ""}
    regex = scala_str(archivo.get("footer_regex") or "")
    codigo = scala_str(spec["codigos_especiales"].get("footer") or "")
    expresion = hocon_str(spec["diagnostico_archivo"].get("footer") or "")
    bloque = (
        "\n\n"
        f"  // Footer (E8): {filas} fila(s); cada una cumple archivo.footer_regex. El patrón se compila al crear el\n"
        "  // processor: si fuera inválido, falla al instanciarlo y no fila por fila (AUD-V3, AUD-V4).\n"
        "  private val footers: Array[String] = Option(processorContext.footers).getOrElse(Array.empty[String])\n"
        f"  private val patronFooter: java.util.regex.Pattern = java.util.regex.Pattern.compile(\"{regex}\")\n"
        "\n"
        "  override def validateFooter(validationContext: ValidationContext): ValidationResult =\n"
        f"    resultado(RecordValidator.validarFooter(footers.toSeq, {filas}, isFooterRow, \"{codigo}\"))")
    return {"is_footer_row_expr": "Option(row).exists(r => patronFooter.matcher(r.trim).matches())",
            "bloque_validate_footer": bloque,
            "linea_validate_footer": f'\n      footer = "{expresion}"'}


def render_footer_test(spec: dict) -> str:
    """Prueba Capa A del footer (VAL-SP10: sin clases del framework). Vacía si filas_footer = 0."""
    archivo = spec["archivo"]
    filas = archivo["filas_footer"]
    if filas == 0:
        return ""
    regex = scala_str(archivo.get("footer_regex") or "")
    codigo = scala_str(spec["codigos_especiales"].get("footer") or "")
    ejemplo = scala_list(spec["fixture"].get("footer") or [])
    return (
        "\n\n  test(\"footer: filas que cumplen footer_regex se aceptan; otra cosa produce el código de footer (AUD-V3)\") {\n"
        f"    val patron = java.util.regex.Pattern.compile(\"{regex}\")\n"
        "    val esFooter = (r: String) => Option(r).exists(f => patron.matcher(f.trim).matches())\n"
        f"    assert(RecordValidator.validarFooter(Seq({ejemplo}), {filas}, esFooter, \"{codigo}\").isEmpty)\n"
        f"    assert(RecordValidator.validarFooter(Seq(linea(RegistroBase)), {filas}, esFooter, \"{codigo}\").map(_.codigo) == Seq(\"{codigo}\"))\n"
        f"    assert(RecordValidator.validarFooter(Seq.empty, {filas}, esFooter, \"{codigo}\").map(_.codigo) == Seq(\"{codigo}\"))\n"
        f"    assert(!esFooter(linea(RegistroBase)))\n"
        "  }")


def render_metadata(spec: dict) -> tuple[str, str]:
    """(imports, bloque) de los miembros adicionales de FileMetadata. Sin archivo.metadata: ("", "")."""
    meta = spec["archivo"].get("metadata") or {}
    imports: list[str] = []
    lineas: list[str] = []
    if meta.get("min_frequency"):
        imports.append("com.experian.edf.oxygen.domain.internal.Frequency")
        lineas.append(f"  override val minFrequency: Frequency = Frequency.{meta['min_frequency']}")
    if "sub_subscriber" in meta:
        lineas.append(f'  override val subSubscriber: String = "{scala_str(meta["sub_subscriber"])}"')
    if meta.get("additional_information"):
        pares = ", ".join(f'"{scala_str(k)}" -> "{scala_str(v)}"' for k, v in meta["additional_information"].items())
        lineas.append(f"  override val additionalInformation: Map[String, String] = Map({pares})")
    for m in meta.get("miembros_adicionales") or []:
        valor = m["valor"]
        expr = f'"{scala_str(valor["texto"])}"' if "texto" in valor else valor["expresion"]
        imports += m.get("importar") or []
        lineas.append(f"  /** Fuente: {m['fuente'].replace('*/', '* /')} */")
        lineas.append(f"  override val {m['miembro']}: {m['tipo']} = {expr}")
    if not lineas:
        return "", ""
    unicos = sorted(set(imports) - {"com.experian.edf.oxygen.domain.internal.FileMetadata"})
    cabecera = ["", "", "  // Miembros declarados en el source-spec (archivo.metadata).",
                "  // ASSUMPTION VAL-PT04: se sobrescriben sin poder confirmar la clase base contra el JAR del framework."]
    return "".join(f"\nimport {i}" for i in unicos), "\n".join(cabecera + lineas)


def render_catalog_scala(spec: dict) -> str:
    tables = []
    for cat in spec["catalogos"]:
        rows = ",\n".join(
            "        Map(" + ", ".join(f'"{scala_str(col)}" -> "{scala_str(val)}"'
                                     for col, val in zip(cat["columnas"], row)) + ")"
            for row in cat["filas_sinteticas"])
        tables.append(render_marcas_supuesto(cat, "      ")
                      + f"      \"{scala_str(cat['tabla'])}\" -> Seq(\n{rows}\n      )")
    return ",\n".join(tables)


def render_error_codes(spec: dict) -> str:
    rows = [f"{c['codigo']};{c['severidad']};{c['nombre']};Campo {c['nombre']} inválido" for c in spec["campos"]]
    rows += [f"{r['codigo']};{r['severidad']};{r.get('campo') or r.get('campo_nombre')};Regla {r['id']}"
             for r in spec["reglas_entre_campos"]]
    especiales = spec["codigos_especiales"]
    rows += [f"{especiales['nombre_archivo']};1;FILENAME;Nombre de archivo inválido",
             f"{especiales['header']};1;HEADER;Header inválido",
             *([f"{especiales['footer']};1;FOOTER;Footer inválido"] if especiales.get("footer") else []),
             f"{especiales['estructura']};1;REGISTRO;Número de columnas distinto al layout",
             f"{especiales['interno']};1;REGISTRO;Error interno al validar el registro"]
    if spec["duplicados"]["habilitado"]:  # marca del motor/processor, sin nivel (F-15, AUD-V-19)
        rows.append(f"{DUPLICATE_MARK};;REGISTRO;Duplicado no conservado (marca OxygenDuplicate)")
    seen, unique = set(), []
    for row in rows:  # un código puede venir de un campo y de una regla (p. ej. correspondencia DANE)
        code = row.split(";", 1)[0]
        if code not in seen:
            seen.add(code)
            unique.append(row)
    return "\n".join(unique)


def build_context(spec: dict) -> dict[str, str]:
    """Valores de los {{huecos}} de las plantillas, calculados desde el source-spec."""
    f, archivo, dup = spec["fuente"], spec["archivo"], spec["duplicados"]
    fields = field_by_name(spec)
    paquetes, nombre = spec["paquetes"], archivo["nombre"]
    grupos, fixture = nombre["grupos"], spec["fixture"]
    especiales, diag = spec["codigos_especiales"], spec["diagnostico_archivo"]
    campos = spec["campos"]
    paquete_fuente = f"{paquetes['base_fuente']}.{f['fuente']}"
    delim = archivo["delimitador"]
    filtro = spec["filtro_filas"]
    filtro_pos = fields[filtro["campo"]]["posicion"] if filtro.get("campo") else None
    header = delim.join(c["nombre"] for c in campos)
    # Header con los nombres fuente: primer alias distinto del nombre de dominio (o el nombre si no hay) (AUD-V-09).
    header_fuente = delim.join(next((a for a in c["alias"] if a != c["nombre"]), c["nombre"]) for c in campos)
    ref = fixture["fecha_referencia"].split("-")
    footer = render_footer(spec)
    ultimo_dia = nombre["file_as_of_date"] == "ultimo_dia_periodo"
    if dup["modo"] not in DEDUP_VERSION:
        raise SpecError(f"duplicados.modo desconocido: {dup['modo']}")
    filtro_desc = []
    if filtro.get("lineas_vacias"):
        filtro_desc.append("líneas vacías")
    if filtro_pos:
        filtro_desc.append(f"{filtro['campo']} en {{{', '.join(filtro.get('valores') or [])}}}")
    clave = list(dup.get("clave") or [])
    imports_metadata, bloque_metadata = render_metadata(spec)
    return {
        "spec_version": str(spec["spec_version"]),
        "Fuente": f["Fuente"],
        "fuente": f["fuente"],
        "event_type": str(f["event_type"]),
        "datasource": scala_str(f["datasource"]),
        "subscriber": scala_str(f["subscriber"]),
        "zona_horaria": scala_str(f["zona_horaria"]),
        "paquete_comun": paquetes["comun"],
        "paquete_fuente": paquete_fuente,
        "ruta_comun": paquetes["comun"].replace(".", "/"),
        "ruta_fuente": paquete_fuente.replace(".", "/"),
        "processor_fqcn": f"{paquete_fuente}.processor.{f['Fuente']}ValidationProcessor",
        "file_format": archivo["formato"],
        "delimitador_scala": scala_str(delim),
        "filas_header": str(archivo["filas_header"]),
        "filas_footer": str(archivo["filas_footer"]),
        "is_footer_row_expr": footer["is_footer_row_expr"],
        "bloque_validate_footer": footer["bloque_validate_footer"],
        "linea_validate_footer": footer["linea_validate_footer"],
        "linea_encoding": (f'  encoding = "{hocon_str(archivo["encoding"])}"' if archivo.get("encoding")
                           else "  # sin clave encoding: ruta textFile UTF-8 (D5, F-17)"),
        "file_pattern_hocon": hocon_str(archivo["file_pattern"]),
        "regex_nombre_scala": scala_str(nombre["regex"]),
        "grupo_prefijo": str(grupos["prefijo"]),
        "grupo_periodo": str(grupos["periodo"]),
        "grupo_marca_tiempo": str(grupos["marca_tiempo"]),
        "grupo_offset": str(grupos["offset"]),
        "nombre_periodo_no_futuro": lower(bool(nombre.get("periodo_no_futuro"))),
        "nombre_recepcion_no_futura": lower(bool(nombre.get("recepcion_no_futura"))),
        "nombre_recepcion_desde_inicio_periodo": lower(bool(nombre.get("recepcion_desde_inicio_periodo"))),
        # HF-13: supuestos sobre las reglas del nombre; vacío sin la clave (la salida no cambia).
        "marcas_supuesto_nombre": render_marcas_supuesto(nombre, "      "),
        "bloque_pruebas_nombre": render_name_tests(spec),
        "bloque_file_tags": ", ".join(f'"{scala_str(k)}" -> "{scala_str(v)}"'
                                      for k, v in nombre["file_tag_por_prefijo"].items()),
        "expr_file_as_of_date": "i.periodo.atEndOfMonth" if ultimo_dia else "i.periodo.atDay(1)",
        "file_as_of_date_desc": "último día del periodo AAAAMM" if ultimo_dia else "primer día del periodo AAAAMM",
        "received_date_desc": "marca de tiempo del nombre con su offset",
        "imports_metadata": imports_metadata,
        "bloque_metadata_adicional": bloque_metadata,
        "campo_subscriber_relation": str(spec["guard_rails"].get("subscriber_relation")),
        "campo_as_of_date": str(spec["guard_rails"].get("as_of_date")),
        "bloque_constantes_campos": "\n".join(f'  val {c["nombre"]}: String = "{c["nombre"]}"' for c in campos),
        "bloque_fieldspecs": ",\n".join(render_fieldspec(c) for c in campos),
        "bloque_reglas": ",\n".join(render_regla(r) for r in spec["reglas_entre_campos"]),
        "bloque_accesores": "\n".join(f'  val {camel(c["nombre"])}: String = Campos.valor(valores, {c["posicion"]})'
                                      for c in campos),
        # validatedRecordJson lleva el valor RECIBIDO, sin trim (Curation lo necesita; AUD-V-05).
        "bloque_mapa_json": "\n".join(f'    m.put("{c["nombre"]}", Campos.crudo(valores, {c["posicion"]}))' for c in campos),
        "filtro_lineas_vacias": lower(filtro.get("lineas_vacias")),
        "filtro_posicion_scala": f"Some({filtro_pos})" if filtro_pos else "None",
        "filtro_valores_scala": scala_list(filtro.get("valores") or []),
        "filtro_descripcion": "; ".join(filtro_desc) or "sin filtro",
        "bloque_quality_scores": ", ".join(f'QualityScoreSpec("{scala_str(q["nombre"])}", Set({scala_list(q["anulan_niveles"])}))'
                                           for q in spec["quality_scores"]),
        "dup_habilitado": lower(dup["habilitado"]),
        "dup_clave_scala": ", ".join(clave),
        "dup_campo_fecha_scala": f"Some({dup['campo_fecha']})" if dup.get("campo_fecha") else "None",
        "dup_descripcion": " + ".join(clave) + ", normalizados" if clave else "sin clave",
        "dup_campo_fecha_desc": str(dup["campo_fecha"]) if dup.get("campo_fecha") else "sin fecha",
        "dedup_version": DEDUP_VERSION[dup["modo"]],
        "linea_duplicate_threshold": (f"  duplicateThreshold = {dup['umbral']}" if dup.get("umbral")
                                      else "  # duplicateThreshold omitido: default Integer.MAX_VALUE (F-15)"),
        "default_output_structure": lower(spec["ambiente"].get("default_output_structure")),
        "codigo_nombre_archivo": especiales["nombre_archivo"],
        "codigo_header": especiales["header"],
        "codigo_estructura": especiales["estructura"],
        "codigo_interno": especiales["interno"],
        "validate_file_name_hocon": hocon_str(diag["fileName"]),
        "validate_header_hocon": hocon_str(diag["header"]),
        "bloque_metricas": ",\n".join(render_metric(m) for m in spec["metricas"]),
        "bloque_lookups": ",\n".join(render_lookup(c) for c in spec["catalogos"]),
        "bloque_codigos_error": render_error_codes(spec),
        "bloque_catalogo_sintetico": render_catalog_scala(spec),
        "registro_base_scala": scala_list(fixture["registro_base"]),
        "header_linea": header,
        "header_linea_scala": scala_str(header),
        "header_fuente_linea_scala": scala_str(header_fuente),
        "numero_campos": str(len(campos)),
        "fecha_referencia_scala": f"LocalDate.of({int(ref[0])}, {int(ref[1])}, {int(ref[2])})",
        "fixture_nombre_archivo": fixture["nombre_archivo"],
        "bloque_registros_fixture": "\n".join([build_fixture_line(spec, c) for c in fixture["casos"]]
                                              + list(fixture.get("footer") or [])),
        "bloque_casos_campo": render_field_tests(spec),
        "bloque_casos_fixture": render_fixture_tests(spec),
    }


# ------------------------------------------------------------------------------------- render
def fill(text: str, context: dict[str, str]) -> tuple[str, set[str]]:
    """Rellena los huecos en una sola pasada; devuelve el texto y los huecos sin valor."""
    missing: set[str] = set()

    def repl(match: re.Match) -> str:
        name = match.group(1)
        if name not in context:
            missing.add(name)
            return match.group(0)
        return context[name]

    return PLACEHOLDER_RE.sub(repl, text), missing


def template_placeholders(templates_dir: Path = TEMPLATES_DIR) -> dict[str, set[str]]:
    """Huecos usados por cada plantilla (cuerpo + ruta destino)."""
    return {rel: set(PLACEHOLDER_RE.findall(read_text(templates_dir / rel))) | set(PLACEHOLDER_RE.findall(target))
            for rel, target in TEMPLATE_TARGETS.items()}


def schema_placeholders(schema_path: Path = SCHEMA_PATH) -> set[str]:
    """Huecos declarados en el esquema (claves x-placeholders)."""
    found: set[str] = set()

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            found.update(node.get("x-placeholders") or [])
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(load_yaml(schema_path))
    return found


def scan_forbidden(text: str) -> list[str]:
    return [p.pattern for p in FORBIDDEN_PATTERNS if p.search(text)]


def marcas_supuesto(text: str) -> list[str]:
    """IDs de las marcas ASSUMPTION <ID> del texto, en orden de aparición (ADR-G1)."""
    return MARCA_RE.findall(text)


def marcas_sin_id(text: str) -> list[int]:
    """Números de línea (desde 1) con una marca ASSUMPTION que no lleva un ID reconocible en la misma línea."""
    out = []
    for number, line in enumerate(text.split("\n"), start=1):
        for any_mark in ASSUMPTION_RE.finditer(line):
            ok = MARCA_RE.search(line, any_mark.start())
            if not ok or ok.start() != any_mark.start():
                out.append(number)
    return out


def resolver_supuestos(text: str, resueltos: dict[str, str]) -> tuple[str, set[str]]:
    """AUD-V-08: cambia `ASSUMPTION <ID>` por `RESUELTO <ID> (<fuente>)` para los IDs que la spec de la ingesta ya
    fija (supuestos_resueltos). Esas marcas dejan de ser supuestos abiertos y no entran a manifest["supuestos"]."""
    usados: set[str] = set()

    def repl(match: re.Match) -> str:
        ident = match.group(1)
        if ident not in resueltos:
            return match.group(0)
        usados.add(ident)
        return f"RESUELTO {ident} ({resueltos[ident]})"

    lineas = []
    for linea in text.split("\n"):
        nueva = MARCA_RE.sub(repl, linea)
        if nueva != linea:
            # HF-15: un supuesto resuelto no puede seguir pidiendo confirmación en la misma marca.
            nueva = PEDIDO_CONFIRMAR_RE.sub("", nueva)
        lineas.append(nueva)
    return "\n".join(lineas), usados


PEDIDO_CONFIRMAR_RE = re.compile(r";\s*(?:confirmar|verificar)\b[^\n]*?(?=\.?\s*$)", re.IGNORECASE)


def lookup_csvs(spec: dict, out: Path) -> dict[Path, str]:
    """Catálogos SINTÉTICOS para tests/ejecución local (no son datos oficiales): ruta → contenido."""
    base = out / "src" / "test" / "resources" / spec["fuente"]["fuente"] / "lookup"
    csvs = {}
    for cat in spec["catalogos"]:
        delim = cat["delimitador"]
        lines = [delim.join(cat["columnas"])] + [delim.join(r) for r in cat["filas_sinteticas"]]
        csvs[base / cat["archivo"]] = "\n".join(lines) + "\n"
    return csvs


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def _rel(out: Path, path: Path) -> str:
    return path.relative_to(out).as_posix()


def render_all(spec: dict, out: Path, templates_dir: Path = TEMPLATES_DIR) -> dict:
    """Genera todo o nada (AC-22).

    (a) Arma en memoria las plantillas rellenas, los CSV de catálogo y manifest.json, y revisa todo: huecos
    sin valor o sin resolver, marcas ASSUMPTION sin ID (AC-18a) y patrones prohibidos. (b) Si hubo algún
    problema lanza SpecError sin escribir nada; si no, escribe cada archivo. manifest["supuestos"] es el
    conjunto ordenado de IDs de las marcas (ADR-G1).
    """
    context = build_context(spec)
    files: dict[Path, str] = {}
    supuestos: set[str] = set()
    problems: list[str] = []
    resueltos = {r["id"]: r["fuente"] for r in spec.get("supuestos_resueltos") or []}
    resueltos_usados: set[str] = set()
    for rel, target in TEMPLATE_TARGETS.items():
        body, missing = fill(read_text(templates_dir / rel), context)
        target_path, missing_path = fill(target, context)
        missing = missing | missing_path
        if missing:
            problems.append(f"{rel}: huecos sin valor {sorted(missing)}")
            continue
        body, usados = resolver_supuestos(body, resueltos)
        resueltos_usados |= usados
        leftover = sorted(set(PLACEHOLDER_RE.findall(body)))
        if leftover:
            problems.append(f"{rel}: quedaron huecos sin resolver {leftover}")
        forbidden = scan_forbidden(body)
        if forbidden:
            problems.append(f"{rel}: patrón prohibido en la salida {forbidden}")
        sin_id = marcas_sin_id(body)
        if sin_id:
            problems.append(f"{rel}: marca ASSUMPTION sin ID en las líneas {sin_id} (AC-18a)")
        supuestos.update(marcas_supuesto(body))
        files[out / target_path] = body
    sin_marca = sorted(set(resueltos) - resueltos_usados)
    if sin_marca:
        problems.append(f"supuestos_resueltos sin marca ASSUMPTION en la salida: {sin_marca}")
    for path, text in lookup_csvs(spec, out).items():
        forbidden = scan_forbidden(text)
        if forbidden:
            problems.append(f"{_rel(out, path)}: patrón prohibido en la salida {forbidden}")
        files[path] = text

    ambiente = spec["ambiente"]
    manifest = {
        "skill": SKILL_NAME,
        "spec_version": spec["spec_version"],
        "fuente": spec["fuente"]["Fuente"],
        "estado": "PROPUESTA - requiere aprobación del técnico antes de copiar al fork (Human-in-the-Loop)",
        "compilacion": "NO CERTIFICADA (F-01: sin acceso al JAR del framework)",
        "archivos": [{"ruta": _rel(out, path), "sha256": hashlib.sha256(text.encode("utf-8")).hexdigest()}
                     for path, text in files.items()],
        "requisitos_de_ambiente": {
            "oxygen.application.lambda": ambiente.get("lambda"),
            "oxygen.application.historicAttributes": ambiente.get("historic_attributes"),
            "oxygen.features.enable.defaultOutputStructure": ambiente.get("default_output_structure"),
            "oxygen.features.enable.emitStockMetrics": "debe existir (se lee con .get)",
        },
        "supuestos": sorted(supuestos),
    }
    if resueltos:
        manifest["supuestos_resueltos"] = [{"id": i, "fuente": resueltos[i]} for i in sorted(resueltos)]
    manifest_text = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    forbidden = scan_forbidden(manifest_text)
    if forbidden:
        problems.append(f"manifest.json: patrón prohibido en la salida {forbidden}")
    if problems:
        raise SpecError("; ".join(problems))  # fase (b) sin escribir nada
    for path, text in files.items():
        _write(path, text)
    _write(out / "manifest.json", manifest_text)
    return manifest


# ----------------------------------------------------------------------------------------- CLI
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Genera los artefactos de Validation desde un source-spec.")
    parser.add_argument("--spec", required=True, type=Path, help="source-spec YAML")
    parser.add_argument("--out", type=Path, help="carpeta de salida (se crea si no existe)")
    parser.add_argument("--solo-verificar", action="store_true", help="solo valida el spec; no genera")
    args = parser.parse_args(argv)
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, OSError):
            pass
    try:
        spec = load_spec(args.spec)
        errors = verify_spec(spec)
        if errors:
            print("El source-spec NO pasa la verificación:", file=sys.stderr)
            for error in errors:
                print(f"  - {error}", file=sys.stderr)
            return 2
        print(f"source-spec OK ({len(spec['campos'])} campos, {len(all_codes(spec))} códigos).")
        if args.solo_verificar:
            return 0
        if args.out is None:
            print("ERROR: --out es obligatorio salvo con --solo-verificar", file=sys.stderr)
            return 2
        manifest = render_all(spec, args.out)
    except (SpecError, OSError, yaml.YAMLError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    except Exception as exc:  # noqa: BLE001 - red de seguridad: error inesperado con código propio
        print(f"ERROR inesperado: {exc!r}", file=sys.stderr)
        return 1
    print(f"Generados {len(manifest['archivos'])} archivos en {args.out} (ver manifest.json).")
    print("Estado: PROPUESTA. Revisar el diff y aprobar antes de copiar al fork.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
