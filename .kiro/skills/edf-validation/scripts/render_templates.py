#!/usr/bin/env python3
"""DEPRECADO (2026-10-20): use el generador Java de scripts/java (ver scripts/DEPRECADOS.md).

Se conserva solo como referencia: produce la misma salida byte a byte que
java -jar scripts/java/target/render-templates.jar, que es la versión vigente.

Genera los artefactos de la etapa Validation (EDF/Oxygen) desde un source-spec.

Uso:
    python render_templates.py --spec source-spec.yaml --out carpeta_salida [--solo-verificar]

Pasos: (1) carga y valida el source-spec contra el esquema; (2) verificaciones semánticas
(códigos de longitud fija y sin colisiones de subcadena, referencias de catálogo, guard-rails, fixture);
(3) rellena cada {{hueco}} de las plantillas; (4) falla si queda algún hueco sin resolver o si la salida
contiene patrones prohibidos (credenciales); (5) escribe manifest.json con archivos, sha256 y supuestos.

No ejecuta nada en el clúster ni modifica repositorios: solo escribe en --out. Requiere PyYAML;
jsonschema es opcional (si falta, se avisa y se omite la validación de esquema).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any, Callable

import yaml

SKILL_DIR = Path(__file__).resolve().parent.parent
TEMPLATES_DIR = SKILL_DIR / "assets" / "templates"
SCHEMA_PATH = SKILL_DIR / "assets" / "schema" / "source-spec.schema.yaml"

PLACEHOLDER_RE = re.compile(r"\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}")
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
    "codigos-error.csv.tpl": "env/application-conf/innersource/propuesta/codigos-error-{{fuente}}.csv",
}


class SpecError(Exception):
    """El source-spec no cumple el esquema o una regla semántica."""


# --------------------------------------------------------------------------------------------- carga
def load_yaml(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as fh:
        return yaml.safe_load(fh)


def load_spec(path: Path) -> dict:
    data = load_yaml(path)
    if not isinstance(data, dict):
        raise SpecError(f"{path.name}: el source-spec debe ser un objeto YAML")
    return data


def validate_schema(spec: dict, schema_path: Path = SCHEMA_PATH) -> list[str]:
    """Devuelve la lista de errores de esquema; vacía si es válido o si jsonschema no está disponible."""
    try:
        import jsonschema  # type: ignore
    except ImportError:
        print("AVISO: jsonschema no está instalado; se omite la validación de esquema.", file=sys.stderr)
        return []
    schema = load_yaml(schema_path)
    validator = jsonschema.Draft202012Validator(schema)
    return [f"{'/'.join(str(p) for p in e.absolute_path) or '(raíz)'}: {e.message}"
            for e in sorted(validator.iter_errors(spec), key=lambda e: list(e.absolute_path))]


# ------------------------------------------------------------------------------------ utilidades
def scala_str(value: str) -> str:
    """Contenido de un literal de cadena Scala (sin comillas)."""
    return value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "\\r")


def hocon_str(value: str) -> str:
    """Contenido de una cadena HOCON entre comillas (mismas reglas que JSON)."""
    return json.dumps(value, ensure_ascii=False)[1:-1]


def scala_list(values: list[str]) -> str:
    return ", ".join(f'"{scala_str(v)}"' for v in values)


def camel(name: str) -> str:
    parts = name.lower().split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def java_to_py_date(pattern: str) -> str:
    out, i = "", 0
    while i < len(pattern):
        for token, repl in JAVA_DATE_TOKENS.items():
            if pattern.startswith(token, i):
                out += repl
                i += len(token)
                break
        else:
            if pattern[i].isalpha():
                raise SpecError(f"formato de fecha no soportado: {pattern!r}")
            out += pattern[i]
            i += 1
    return out


def all_codes(spec: dict) -> list[str]:
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
    values += case.get("columnas_extra", [])
    return spec["archivo"]["delimitador"].join(values)


# ---------------------------------------------------------------------- verificaciones semánticas
def check_codes(spec: dict) -> list[str]:
    errors: list[str] = []
    codes = all_codes(spec)
    lengths = {len(c) for c in codes}
    if len(lengths) != 1:
        errors.append(f"los códigos de error deben tener longitud fija; longitudes encontradas: {sorted(lengths)}")
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
    texts = [spec["diagnostico_archivo"]["fileName"], spec["diagnostico_archivo"]["header"],
             spec["handoff_acceptance"]["record_acceptance"]]
    for qs in spec["quality_scores"]:
        name = qs["nombre"]
        if any(name in kw for kw in DSL_KEYWORDS) or any(name in c for c in all_codes(spec)):
            errors.append(f"el nombre de quality score {name!r} aparece dentro de un operador o código del DSL")
        if any(name in t for t in texts):
            errors.append(f"el nombre de quality score {name!r} aparece en una expresión de registro/archivo")
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
        for fmt in c.get("formatos_fecha", []):
            try:
                java_to_py_date(fmt)
            except SpecError as exc:
                errors.append(f"{c['nombre']}: {exc}")
        if c["obligatorio"] != (c["severidad"] == "1"):
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
        elif tipo and fields[name]["tipo"] != tipo:
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
            need_field(r["campo"], r["id"])
    for cat in spec["catalogos"]:
        for key in cat["keys"]:
            if key not in cat["columnas"]:
                errors.append(f"{cat['tabla']}: key {key} no es una columna")
        for row in cat["filas_sinteticas"]:
            if len(row) != len(cat["columnas"]):
                errors.append(f"{cat['tabla']}: fila sintética con {len(row)} valores (esperado {len(cat['columnas'])})")
    gr = spec["guard_rails"]
    need_field(gr["subscriber_relation"], "guard_rails.subscriber_relation")
    need_field(gr["as_of_date"], "guard_rails.as_of_date", tipo="fecha")
    need_field(spec["filtro_filas"].get("campo"), "filtro_filas.campo")
    dup = spec["duplicados"]
    if dup["habilitado"]:
        if not dup["clave"]:
            errors.append("duplicados.habilitado exige una clave no vacía (assert(false) del motor)")
        for name in dup["clave"]:
            need_field(name, "duplicados.clave")
        if dup["modo"] == "EXTN":
            need_field(dup.get("campo_fecha"), "duplicados.campo_fecha", tipo="fecha")
    return errors


def check_file_contract(spec: dict) -> list[str]:
    errors = []
    archivo = spec["archivo"]
    if archivo["filas_footer"] > 0 and not archivo.get("footer_regex"):
        errors.append("filas_footer > 0 exige footer_regex")
    try:
        name_re = re.compile(archivo["nombre"]["regex"])
        for group, idx in archivo["nombre"]["grupos"].items():
            if idx > name_re.groups:
                errors.append(f"archivo.nombre.grupos.{group} = {idx} excede los grupos del regex")
        pattern_re = re.compile(archivo["file_pattern"])
    except re.error as exc:
        return errors + [f"regex inválido: {exc}"]
    fixture_name = spec["fixture"]["nombre_archivo"]
    if not name_re.fullmatch(fixture_name):
        errors.append("fixture.nombre_archivo no cumple archivo.nombre.regex")
    if not pattern_re.fullmatch("/ruta/sintetica/" + fixture_name):
        errors.append("fixture.nombre_archivo no cumple archivo.file_pattern (ruta completa)")
    if re.search(r"[\"\\/]", fixture_name):
        errors.append("fixture.nombre_archivo no puede contener comillas, barras ni rutas")
    return errors


def check_fixture(spec: dict) -> list[str]:
    errors = []
    fields = field_by_name(spec)
    if len(spec["fixture"]["registro_base"]) != len(spec["campos"]):
        errors.append("fixture.registro_base debe tener un valor por campo")
    for case in spec["fixture"]["casos"]:
        for name in (case.get("cambios") or {}):
            if name not in fields:
                errors.append(f"fixture {case['id']}: campo inexistente {name}")
        for code in case["esperado"].get("codigos", []):
            if code not in all_codes(spec):
                errors.append(f"fixture {case['id']}: código esperado no declarado {code}")
    ids = [c["id"] for c in spec["fixture"]["casos"]]
    if len(ids) != len(set(ids)):
        errors.append("ids de casos del fixture repetidos")
    return errors


def verify_spec(spec: dict, schema_path: Path = SCHEMA_PATH) -> list[str]:
    """Esquema + reglas semánticas. Devuelve todos los errores encontrados."""
    errors = validate_schema(spec, schema_path)
    if errors:
        return errors  # sin forma válida, las reglas semánticas no son confiables
    for check in (check_fields, check_codes, check_quality_scores, check_references, check_file_contract,
                  check_fixture):
        errors += check(spec)
    return errors


# ------------------------------------------------------------------------------------- contexto
SEVERIDAD_SCALA = {"1": "Severidad.Error", "3": "Severidad.Inconsistencia"}
TIPO_SCALA = {"alfanumerico": "TipoDato.Alfanumerico", "numerico": "TipoDato.Numerico", "fecha": "TipoDato.Fecha"}
NORMALIZACION_SCALA = {"quitar_espacios": "Normalizacion.QuitarEspacios",
                       "quitar_ceros_izquierda": "Normalizacion.QuitarCerosIzquierda"}


def _opt(value: Any, render: Callable[[Any], str]) -> str:
    return "None" if value is None else f"Some({render(value)})"


def render_fieldspec(c: dict) -> str:
    alias = [c["nombre"]] + [a for a in c["alias"] if a != c["nombre"]]
    rango = _opt(c.get("rango"), lambda r: f"RangoEntero({r[0]}L, {r[1]}L)")
    dominio = _opt(c.get("dominio"), lambda d: f"Set({scala_list(d)})")
    catalogo = _opt(c.get("catalogo"),
                    lambda k: f'CatalogoRef("{scala_str(k["tabla"])}", "{scala_str(k["columna"])}")')
    normal = ", ".join(NORMALIZACION_SCALA[n] for n in c.get("normalizacion", []))
    return (f"    FieldSpec(nombre = {c['nombre']}, posicion = {c['posicion']}, aliasHeader = Seq({scala_list(alias)}),\n"
            f"      obligatorio = {str(c['obligatorio']).lower()}, severidad = {SEVERIDAD_SCALA[c['severidad']]}, "
            f"tipo = {TIPO_SCALA[c['tipo']]},\n"
            f"      longitudMax = {_opt(c.get('longitud_max'), str)}, rango = {rango}, dominio = {dominio},\n"
            f"      catalogo = {catalogo}, formatosFecha = Seq({scala_list(c.get('formatos_fecha', []))}), "
            f"noFutura = {str(c.get('no_futura', False)).lower()},\n"
            f"      normalizacion = Seq({normal}), codigoError = \"{scala_str(c['codigo'])}\")")


def render_regla(r: dict) -> str:
    sev = SEVERIDAD_SCALA[r["severidad"]]
    if r["tipo"] == "correspondencia_catalogo":
        return (f"    CorrespondenciaCatalogo(id = \"{scala_str(r['id'])}\", campoCodigo = {r['campo_codigo']}, "
                f"campoNombre = {r['campo_nombre']},\n"
                f"      tabla = \"{scala_str(r['tabla'])}\", columnaClave = \"{scala_str(r['columna_clave'])}\", "
                f"columnaNombre = \"{scala_str(r['columna_nombre'])}\",\n"
                f"      codigo = \"{scala_str(r['codigo'])}\", severidad = {sev})")
    return (f"    ValorConCodigoPropio(id = \"{scala_str(r['id'])}\", campo = {r['campo']}, "
            f"valores = Set({scala_list(r['valores'])}),\n"
            f"      codigo = \"{scala_str(r['codigo'])}\", severidad = {sev}, causa = \"{scala_str(r['causa'])}\")")


def render_lookup(cat: dict) -> str:
    keys = ",\n".join(f'      {{ index = "{hocon_str(k)}" }}' for k in cat["keys"])
    return ("  {\n"
            f"    tableName = \"{hocon_str(cat['tabla'])}\"\n"
            f"    file = \"{hocon_str(cat['archivo'])}\"\n"
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
    blocks = []
    for c in spec["campos"]:
        blocks.append(
            f"  test(\"{c['nombre']} inválido produce {c['codigo']}\") {{\n"
            f"    assert(codigos(conValor({c['posicion']}, \"{scala_str(c['ejemplo_invalido'])}\")).contains(\"{c['codigo']}\"))\n"
            "  }")
    return "\n\n".join(blocks)


def render_fixture_tests(spec: dict) -> str:
    blocks, dup_cases = [], []
    for case in spec["fixture"]["casos"]:
        line = scala_str(build_fixture_line(spec, case))
        title = scala_str(f"fixture {case['id']}: {case['descripcion']}")
        esperado = case["esperado"]
        if esperado.get("filtrado"):
            body = f"    assert(RecordValidator.debeFiltrar(spec, \"{line}\"))"
        else:
            body = (f"    assert(!RecordValidator.debeFiltrar(spec, \"{line}\"))\n"
                    f"    assert(codigos(\"{line}\") == Seq({scala_list(esperado.get('codigos', []))}))")
        blocks.append(f"  test(\"{title}\") {{\n{body}\n  }}")
        if "duplicado" in esperado:
            dup_cases.append((line, esperado["duplicado"]))
    if dup_cases:
        kept = [i for i, (_, dup) in enumerate(dup_cases) if not dup]
        lines = ", ".join(f'"{line}"' for line, _ in dup_cases)
        delim = scala_str(spec["archivo"]["delimitador"])
        blocks.append(
            "  test(\"fixture duplicados: misma clave y se conserva el más reciente (ASSUMPTION F-14)\") {\n"
            f"    val grupo = Seq({lines})\n"
            f"    val claves = grupo.map(l => RecordValidator.claveDuplicado(spec, Campos.separar(l, \"{delim}\")).toSeq)\n"
            "    assert(claves.distinct.size == 1)\n"
            "    val fechas = grupo.map(l => RecordValidator.asOfDate(spec, l).getOrElse(0L))\n"
            "    val candidatos = fechas.zipWithIndex.map { case (f, i) => (f, \"%05d\".format(i)) }\n"
            f"    assert(RecordValidator.indiceAConservar(candidatos) == {kept[0] if kept else -1})\n"
            "  }")
    return "\n\n".join(blocks)


def render_catalog_scala(spec: dict) -> str:
    tables = []
    for cat in spec["catalogos"]:
        rows = ",\n".join(
            "        Map(" + ", ".join(f'"{scala_str(col)}" -> "{scala_str(val)}"'
                                     for col, val in zip(cat["columnas"], row)) + ")"
            for row in cat["filas_sinteticas"])
        tables.append(f"      \"{scala_str(cat['tabla'])}\" -> Seq(\n{rows}\n      )")
    return ",\n".join(tables)


def render_error_codes(spec: dict) -> str:
    rows = [f"{c['codigo']};{c['severidad']};{c['nombre']};Campo {c['nombre']} inválido" for c in spec["campos"]]
    rows += [f"{r['codigo']};{r['severidad']};{r.get('campo') or r.get('campo_nombre')};Regla {r['id']}"
             for r in spec["reglas_entre_campos"]]
    especiales = spec["codigos_especiales"]
    rows += [f"{especiales['nombre_archivo']};1;FILENAME;Nombre de archivo inválido",
             f"{especiales['header']};1;HEADER;Header inválido",
             f"{especiales['estructura']};1;REGISTRO;Número de columnas distinto al layout",
             f"{especiales['interno']};1;REGISTRO;Error interno al validar el registro"]
    seen, unique = set(), []
    for row in rows:  # un código puede venir de un campo y de una regla (p. ej. correspondencia DANE)
        code = row.split(";", 1)[0]
        if code not in seen:
            seen.add(code)
            unique.append(row)
    return "\n".join(unique)


def build_context(spec: dict) -> dict[str, str]:
    f, archivo, dup = spec["fuente"], spec["archivo"], spec["duplicados"]
    fields = field_by_name(spec)
    paquete_fuente = f"{spec['paquetes']['base_fuente']}.{f['fuente']}"
    delim = archivo["delimitador"]
    filtro = spec["filtro_filas"]
    filtro_pos = fields[filtro["campo"]]["posicion"] if filtro.get("campo") else None
    header = delim.join(c["nombre"] for c in spec["campos"])
    ref = spec["fixture"]["fecha_referencia"].split("-")
    footer_expr = ("false" if archivo["filas_footer"] == 0 else
                   f'Try(Option(row).exists(_.trim.matches("{scala_str(archivo["footer_regex"])}"))).getOrElse(false)')
    as_of = archivo["nombre"]["file_as_of_date"]
    dedup = {"EXTN": "EXTN", "RANDOM": "RANDOM", "AS-IS": ""}[dup["modo"]]
    filtro_desc = []
    if filtro["lineas_vacias"]:
        filtro_desc.append("líneas vacías")
    if filtro_pos:
        filtro_desc.append(f"{filtro['campo']} en {{{', '.join(filtro.get('valores', []))}}}")
    return {
        "spec_version": spec["spec_version"],
        "Fuente": f["Fuente"],
        "fuente": f["fuente"],
        "event_type": str(f["event_type"]),
        "datasource": scala_str(f["datasource"]),
        "subscriber": scala_str(f["subscriber"]),
        "zona_horaria": scala_str(f["zona_horaria"]),
        "paquete_comun": spec["paquetes"]["comun"],
        "paquete_fuente": paquete_fuente,
        "ruta_comun": spec["paquetes"]["comun"].replace(".", "/"),
        "ruta_fuente": paquete_fuente.replace(".", "/"),
        "processor_fqcn": f"{paquete_fuente}.processor.{f['Fuente']}ValidationProcessor",
        "file_format": archivo["formato"],
        "delimitador_scala": scala_str(delim),
        "filas_header": str(archivo["filas_header"]),
        "filas_footer": str(archivo["filas_footer"]),
        "is_footer_row_expr": footer_expr,
        "linea_encoding": (f'  encoding = "{hocon_str(archivo["encoding"])}"' if archivo.get("encoding")
                           else "  # sin clave encoding: ruta textFile UTF-8 (D5, F-17)"),
        "file_pattern_hocon": hocon_str(archivo["file_pattern"]),
        "regex_nombre_scala": scala_str(archivo["nombre"]["regex"]),
        "grupo_prefijo": str(archivo["nombre"]["grupos"]["prefijo"]),
        "grupo_periodo": str(archivo["nombre"]["grupos"]["periodo"]),
        "grupo_marca_tiempo": str(archivo["nombre"]["grupos"]["marca_tiempo"]),
        "grupo_offset": str(archivo["nombre"]["grupos"]["offset"]),
        "bloque_file_tags": ", ".join(f'"{scala_str(k)}" -> "{scala_str(v)}"'
                                      for k, v in archivo["nombre"]["file_tag_por_prefijo"].items()),
        "expr_file_as_of_date": "i.periodo.atEndOfMonth" if as_of == "ultimo_dia_periodo" else "i.periodo.atDay(1)",
        "file_as_of_date_desc": "último día del periodo AAAAMM" if as_of == "ultimo_dia_periodo"
                                else "primer día del periodo AAAAMM",
        "received_date_desc": "marca de tiempo del nombre con su offset",
        "campo_subscriber_relation": spec["guard_rails"]["subscriber_relation"],
        "campo_as_of_date": spec["guard_rails"]["as_of_date"],
        "bloque_constantes_campos": "\n".join(f'  val {c["nombre"]}: String = "{c["nombre"]}"' for c in spec["campos"]),
        "bloque_fieldspecs": ",\n".join(render_fieldspec(c) for c in spec["campos"]),
        "bloque_reglas": ",\n".join(render_regla(r) for r in spec["reglas_entre_campos"]),
        "bloque_accesores": "\n".join(f'  val {camel(c["nombre"])}: String = Campos.valor(valores, {c["posicion"]})'
                                      for c in spec["campos"]),
        "bloque_mapa_json": "\n".join(f'    m.put("{c["nombre"]}", {camel(c["nombre"])})' for c in spec["campos"]),
        "filtro_lineas_vacias": str(filtro["lineas_vacias"]).lower(),
        "filtro_posicion_scala": f"Some({filtro_pos})" if filtro_pos else "None",
        "filtro_valores_scala": scala_list(filtro.get("valores", [])),
        "filtro_descripcion": "; ".join(filtro_desc) or "sin filtro",
        "bloque_quality_scores": ", ".join(f'QualityScoreSpec("{scala_str(q["nombre"])}", Set({scala_list(q["anulan_niveles"])}))'
                                           for q in spec["quality_scores"]),
        "dup_habilitado": str(dup["habilitado"]).lower(),
        "dup_clave_scala": ", ".join(dup["clave"]),
        "dup_campo_fecha_scala": f"Some({dup['campo_fecha']})" if dup.get("campo_fecha") else "None",
        "dup_descripcion": " + ".join(dup["clave"]) + ", normalizados" if dup["clave"] else "sin clave",
        "dup_campo_fecha_desc": dup.get("campo_fecha") or "sin fecha",
        "dedup_version": dedup,
        "linea_duplicate_threshold": (f"  duplicateThreshold = {dup['umbral']}" if dup.get("umbral")
                                      else "  # duplicateThreshold omitido: default Integer.MAX_VALUE (F-15)"),
        "codigo_nombre_archivo": spec["codigos_especiales"]["nombre_archivo"],
        "codigo_header": spec["codigos_especiales"]["header"],
        "codigo_estructura": spec["codigos_especiales"]["estructura"],
        "codigo_interno": spec["codigos_especiales"]["interno"],
        "validate_file_name_hocon": hocon_str(spec["diagnostico_archivo"]["fileName"]),
        "validate_header_hocon": hocon_str(spec["diagnostico_archivo"]["header"]),
        "bloque_metricas": ",\n".join(render_metric(m) for m in spec["metricas"]),
        "bloque_lookups": ",\n".join(render_lookup(c) for c in spec["catalogos"]),
        "bloque_codigos_error": render_error_codes(spec),
        "bloque_catalogo_sintetico": render_catalog_scala(spec),
        "registro_base_scala": scala_list(spec["fixture"]["registro_base"]),
        "header_linea": header,
        "header_linea_scala": scala_str(header),
        "numero_campos": str(len(spec["campos"])),
        "fecha_referencia_scala": f"LocalDate.of({int(ref[0])}, {int(ref[1])}, {int(ref[2])})",
        "fixture_nombre_archivo": spec["fixture"]["nombre_archivo"],
        "bloque_registros_fixture": "\n".join(build_fixture_line(spec, c) for c in spec["fixture"]["casos"]),
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
    result = {}
    for rel, target in TEMPLATE_TARGETS.items():
        body = (templates_dir / rel).read_text(encoding="utf-8-sig")
        result[rel] = set(PLACEHOLDER_RE.findall(body)) | set(PLACEHOLDER_RE.findall(target))
    return result


def schema_placeholders(schema_path: Path = SCHEMA_PATH) -> set[str]:
    found: set[str] = set()

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            found.update(node.get("x-placeholders", []))
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(load_yaml(schema_path))
    return found


def scan_forbidden(text: str) -> list[str]:
    return [p.pattern for p in FORBIDDEN_PATTERNS if p.search(text)]


def write_lookup_csvs(spec: dict, out: Path) -> list[Path]:
    """Catálogos SINTÉTICOS para tests/ejecución local (no son datos oficiales)."""
    written = []
    base = out / "src" / "test" / "resources" / spec["fuente"]["fuente"] / "lookup"
    base.mkdir(parents=True, exist_ok=True)
    for cat in spec["catalogos"]:
        path = base / cat["archivo"]
        lines = [cat["delimitador"].join(cat["columnas"])] + [cat["delimitador"].join(r) for r in cat["filas_sinteticas"]]
        path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        written.append(path)
    return written


def render_all(spec: dict, out: Path, templates_dir: Path = TEMPLATES_DIR) -> dict:
    context = build_context(spec)
    files, problems = [], []
    for rel, target in TEMPLATE_TARGETS.items():
        body = (templates_dir / rel).read_text(encoding="utf-8-sig")
        rendered, missing = fill(body, context)
        target_path, missing_path = fill(target, context)
        missing |= missing_path
        if missing:
            problems.append(f"{rel}: huecos sin valor {sorted(missing)}")
            continue
        leftover = PLACEHOLDER_RE.findall(rendered)
        if leftover:
            problems.append(f"{rel}: quedaron huecos sin resolver {sorted(set(leftover))}")
        forbidden = scan_forbidden(rendered)
        if forbidden:
            problems.append(f"{rel}: patrón prohibido en la salida {forbidden}")
        path = out / target_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(rendered, encoding="utf-8", newline="\n")
        files.append(path)
    if problems:
        raise SpecError("; ".join(problems))
    files += write_lookup_csvs(spec, out)
    manifest = {
        "skill": "edf-validation-stage",
        "spec_version": spec["spec_version"],
        "fuente": spec["fuente"]["Fuente"],
        "estado": "PROPUESTA - requiere aprobación del técnico antes de copiar al fork (Human-in-the-Loop)",
        "compilacion": "NO CERTIFICADA (F-01: sin acceso al JAR del framework)",
        "archivos": [{"ruta": p.relative_to(out).as_posix(),
                      "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in files],
        "requisitos_de_ambiente": {
            "oxygen.application.lambda": spec["ambiente"]["lambda"],
            "oxygen.application.historicAttributes": spec["ambiente"]["historic_attributes"],
            "oxygen.features.enable.defaultOutputStructure": spec["ambiente"]["default_output_structure"],
            "oxygen.features.enable.emitStockMetrics": "debe existir (se lee con .get)",
        },
        "supuestos": ["F-04", "F-07", "F-08", "F-09", "F-11", "F-12", "F-13", "F-14", "VAL-PT01", "VAL-PT03",
                      "VAL-PT04", "VAL-PA04"],
    }
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                                       encoding="utf-8", newline="\n")
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--spec", required=True, type=Path, help="source-spec YAML")
    parser.add_argument("--out", type=Path, help="carpeta de salida (se crea si no existe)")
    parser.add_argument("--solo-verificar", action="store_true", help="solo valida el spec; no genera")
    args = parser.parse_args(argv)
    print("AVISO: render_templates.py está DEPRECADO; use java -jar scripts/java/target/render-templates.jar "
          "(ver scripts/DEPRECADOS.md).", file=sys.stderr)

    try:
        spec = load_spec(args.spec)
        errors = verify_spec(spec)
        if errors:
            print("El source-spec NO pasa la verificación:", file=sys.stderr)
            for e in errors:
                print(f"  - {e}", file=sys.stderr)
            return 2
        print(f"source-spec OK ({len(spec['campos'])} campos, {len(all_codes(spec))} códigos).")
        if args.solo_verificar:
            return 0
        if args.out is None:
            parser.error("--out es obligatorio salvo con --solo-verificar")
        manifest = render_all(spec, args.out)
    except (SpecError, OSError, yaml.YAMLError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    print(f"Generados {len(manifest['archivos'])} archivos en {args.out} (ver manifest.json).")
    print("Estado: PROPUESTA. Revisar el diff y aprobar antes de copiar al fork.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
