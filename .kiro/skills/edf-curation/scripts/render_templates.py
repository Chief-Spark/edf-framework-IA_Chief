#!/usr/bin/env python3
"""DEPRECADO (2026-10-20): use el generador Java de scripts/java (ver scripts/DEPRECADOS.md).

Se conserva solo como referencia: produce la misma salida byte a byte que
java -jar scripts/java/target/render-templates.jar, que es la versión vigente.

Genera los artefactos de Curation (CUR-TPL-01..12) desde un source-spec.

Uso (desde la raíz del skill):
    python scripts/render_templates.py \
        --spec assets/examples/sisben/source-spec.sisben.yaml \
        --out  assets/examples/sisben/generated

Pasos: valida el source-spec contra assets/schema/source-spec.schema.yaml, calcula los huecos
declarados en x-placeholders, rellena cada plantilla de assets/templates/, verifica que no quede
ningún {{hueco}} sin resolver y escribe:
  <out>/<ruta del repo destino>      código Scala y recursos de test (mismas rutas del fork)
  <out>/fragmentos/                  HOCON y domain dictionary a FUSIONAR (regla aditiva)
  <out>/expected/                    salida esperada del fixture (implementación de referencia)
  <out>/MANIFEST.json                qué plantilla generó qué archivo y adónde va en el fork
Con --write-expected DIR copia además la salida esperada a DIR (golden versionado).

Solo usa la biblioteca estándar + PyYAML (jsonschema es opcional). Sin red.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from datetime import datetime
from pathlib import Path
from typing import Any, Callable

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import curation_reference as ref  # noqa: E402

SKILL_ROOT = Path(__file__).resolve().parent.parent
HUECO = re.compile(r"\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}")
SCALA_TIPO = {"texto": "String", "entero": "Int", "largo": "Long", "decimal": "Double", "booleano": "Boolean"}
TIPO_SALIDA_SCALA = {"texto": "Texto", "entero": "Entero", "largo": "Largo", "decimal": "Decimal", "booleano": "Booleano"}
DD_TIPO = {"texto": "string", "entero": "integer", "largo": "long", "decimal": "double", "booleano": "boolean"}
TRANSF_SCALA = {
    "texto": "Transformacion.Texto",
    "original": "Transformacion.Original",
    "nombre": "Transformacion.Nombre",
    "fecha_epoch": "Transformacion.FechaEpoch",
    "homologacion": "Transformacion.Homologacion",
    "documento": "Transformacion.Documento",
}


class RenderError(Exception):
    pass


# ---------------------------------------------------------------- utilidades
def q(s: Any) -> str:
    """Literal de cadena Scala/JSON entre comillas dobles."""
    return json.dumps("" if s is None else str(s), ensure_ascii=False)


def tq(s: str) -> str:
    """Literal Scala triple-comillas (para JSON y regex)."""
    if '"""' in s:
        raise RenderError("el texto contiene comillas triples")
    return f'"""{s}"""'


def compacto(obj: Any) -> str:
    """JSON compacto, mismo formato que Gson (serializeNulls, disableHtmlEscaping)."""
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def cargar_yaml(ruta: Path) -> Any:
    with ruta.open(encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def validar_spec(spec: dict[str, Any], schema: dict[str, Any]) -> None:
    esquema = {k: v for k, v in schema.items() if k != "x-placeholders"}
    try:
        import jsonschema
    except ImportError:  # validación mínima si no está jsonschema
        faltan = [k for k in esquema.get("required", []) if k not in spec]
        if faltan:
            raise RenderError(f"faltan secciones en el source-spec: {faltan}")
        validar_coherencia(spec)
        return
    errores = sorted(jsonschema.Draft202012Validator(esquema).iter_errors(spec), key=lambda e: list(e.path))
    if errores:
        detalle = "; ".join(f"{'/'.join(map(str, e.path)) or '(raíz)'}: {e.message}" for e in errores[:10])
        raise RenderError(f"source-spec inválido: {detalle}")
    validar_coherencia(spec)


def validar_coherencia(spec: dict[str, Any]) -> None:
    """Reglas que el JSON Schema no expresa (CUR-RC05, CUR-TPL-03)."""
    destinos = [m["destino"] for m in spec["campos_curados"]]
    duplicados = {d for d in destinos if destinos.count(d) > 1}
    if duplicados:
        raise RenderError(f"nombres publicados repetidos: {sorted(duplicados)}")
    if spec["parametros"]["campo_advertencias"] in destinos:
        raise RenderError("el campo de advertencias no puede ser también un campo curado")
    entradas = set(spec["campos_entrada"])
    vistos: set[str] = set()
    for m in spec["campos_curados"]:
        t = m["transformacion"]
        if m["origen"] and m["origen"] not in entradas:
            raise RenderError(f"{m['destino']}: origen {m['origen']} no está en campos_entrada")
        if t["tipo"] in ("desde_archivo", "copia_de", "constante") and m["origen"]:
            raise RenderError(f"{m['destino']}: {t['tipo']} no lleva origen")
        if t["tipo"] not in ("desde_archivo", "copia_de", "constante") and not m["origen"]:
            raise RenderError(f"{m['destino']}: falta origen")
        if t["tipo"] == "padding_izquierda" and "longitud" not in t:
            raise RenderError(f"{m['destino']}: padding_izquierda requiere longitud")
        if t["tipo"] == "desde_archivo" and "atributo" not in t:
            raise RenderError(f"{m['destino']}: desde_archivo requiere atributo")
        if t["tipo"] == "copia_de" and t.get("campo") not in vistos:
            raise RenderError(f"{m['destino']}: copia_de debe referirse a un campo anterior")
        vistos.add(m["destino"])
    abiertos = {a["destino"] for a in spec.get("campos_abiertos", [])}
    if abiertos & set(destinos):
        raise RenderError(f"campos abiertos no pueden generarse: {sorted(abiertos & set(destinos))}")
    for codigo, campo in spec["codigos_error"].items():
        if campo not in entradas:
            raise RenderError(f"código {codigo}: campo {campo} no está en campos_entrada")
    pat = re.compile(spec["archivo"]["file_pattern"])
    for ej in spec["archivo"]["ejemplos_sinteticos"]:
        if not pat.fullmatch(ej):
            raise RenderError(f"filePattern no cubre el ejemplo {ej}")
    for ej in spec["archivo"].get("contraejemplos_sinteticos", []):
        if pat.fullmatch(ej):
            raise RenderError(f"filePattern cubre el contraejemplo {ej}")


# ---------------------------------------------------------------- fixture
def epoch_ms(texto: str, zona: str) -> int:
    for fmt in ("%Y-%m-%d %H:%M:%S", "%d/%m/%Y"):
        try:
            return int(datetime.strptime(texto, fmt).replace(tzinfo=ref.zona(zona)).timestamp() * 1000)
        except ValueError:
            continue
    raise RenderError(f"fecha de fixture inválida: {texto}")


def fecha_recepcion(archivo: str) -> int:
    m = re.search(r"\.(\d{14})([+-]\d{4})", archivo)
    if not m:
        raise RenderError(f"nombre de fixture sin marca de tiempo: {archivo}")
    return int(datetime.strptime(m.group(1) + m.group(2), "%Y%m%d%H%M%S%z").timestamp() * 1000)


def construir_eventos(spec: dict[str, Any], fixture: dict[str, Any]) -> list[dict[str, Any]]:
    """Eventos TaggedValidatedEvent sintéticos (forma de 2404, campos que usa Curation)."""
    eventos = []
    for e in fixture["eventos"]:
        registro = {**fixture["base_registro"], **(e.get("registro") or {})}
        cns = registro["FECHA_ACTUALIZACION_CNS"]
        recibido = fecha_recepcion(e["archivo"])
        eventos.append({
            "lineage": {
                "dataSource": spec["fuente"]["datasource"],
                "receivedDate": recibido,
                "fileName": e["archivo"],
                "subscriber": spec["fuente"]["subscriber"],
                "fileAsOfDate": recibido,
                "datasetId": "ds-sintetico-0001",
                "recordHash": f"hash-{e['id']}",
            },
            "eventId": e["id"],
            "validatedRecordJson": compacto(registro),
            "payload": {"fixture": "SINTETICO"},
            "qualityScores": [{"name": "RQS", "score": 1.0}],
            "errorCodes": [
                {"errorCodeName": err["codigo"], "errorCodeValue": err["codigo"], "errorLevel": err["nivel"],
                 "messageKey": "", "errorType": "event"}
                for err in e.get("errores") or []
            ],
            "eventType": spec["fuente"]["output_event_type"],
            # Guard-rails de Validation (D3): As-Of-Date = FECHA_ACTUALIZACION_CNS en America/Bogota.
            "asOfDate": epoch_ms(cns, "America/Bogota"),
            "subscriberRelation": registro["NUM_DOCUMENTO"].strip(),
            "subscriber": spec["fuente"]["subscriber"],
            "headerErrors": [],
            "footerErrors": [],
            "keys": "",
            "fileTag": "",
            "_caso": e["caso"],
        })
    return eventos


def sin_meta(evento: dict[str, Any]) -> dict[str, Any]:
    return {k: v for k, v in evento.items() if not k.startswith("_")}


def salida_esperada(spec: dict[str, Any], eventos: list[dict[str, Any]]) -> tuple[list[dict], dict]:
    curados, descartados = [], []
    for ev in eventos:
        r = ref.curar_evento(sin_meta(ev), spec)
        if r is None:
            descartados.append({"eventId": ev["eventId"], "fileName": ev["lineage"]["fileName"],
                                "motivo": "sin processor para lineage.fileName (CUR-AP02, descarte sin log)"})
        else:
            curados.append(r)
    campo_adv = spec["parametros"]["campo_advertencias"]
    resumen = {
        "sintetico": True,
        "eventos_entrada": len(eventos),
        "eventos_curados": len(curados),
        "eventos_descartados_por_motor": descartados,
        "eventos_con_advertencias": sum(1 for c in curados if c["curatedRecordJson"][campo_adv]),
        "advertencias": {c["eventId"]: c["curatedRecordJson"][campo_adv] for c in curados if c["curatedRecordJson"][campo_adv]},
        "claves_omitidas_por_severidad": {
            c["eventId"]: [m["destino"] for m in spec["campos_curados"] if m["destino"] not in c["curatedRecordJson"]]
            for c in curados
            if any(m["destino"] not in c["curatedRecordJson"] for m in spec["campos_curados"])
        },
        "homologacion_tipo_documento": {
            c["eventId"]: [json.loads(next(e for e in eventos if e["eventId"] == c["eventId"])["validatedRecordJson"])["TIP_DOCUMENTO"],
                           c["curatedRecordJson"].get("tipoDocumento")]
            for c in curados
        },
    }
    return curados, resumen


# ---------------------------------------------------------------- huecos
def mapeo_scala(m: dict[str, Any], ultimo: bool) -> str:
    t = m["transformacion"]
    tipo = t["tipo"]
    if tipo in TRANSF_SCALA:
        tr = TRANSF_SCALA[tipo]
    elif tipo == "padding_izquierda":
        tr = f"Transformacion.PaddingIzquierda({int(t['longitud'])})"
    elif tipo == "desde_archivo":
        tr = f"Transformacion.DesdeArchivo({q(t['atributo'])})"
    elif tipo == "copia_de":
        tr = f"Transformacion.CopiaDe({q(t['campo'])})"
    else:
        tr = "Transformacion.Constante(None)" if t.get("valor") is None else f"Transformacion.Constante(Some({q(t['valor'])}))"
    salida = "TipoSalida." + TIPO_SALIDA_SCALA[m["tipo_salida"]]
    cat = m.get("catalogo")
    cat_s = f"Some(Catalogo({q(cat['tabla'])}, {q(cat['columna'])}))" if cat else "None"
    marca = "" if m["estado"] == "cerrado" else f"  // {'ASSUMPTION: ' if m['estado'] == 'supuesto' else ''}{m['estado']}"
    coma = "" if ultimo else ","
    return f"    FieldMapping({q(m['destino'])}, {q(m['origen'])}, {tr}, {salida}, {cat_s}){coma}{marca}"


def regla_scala(r: dict[str, Any]) -> str:
    pad = f"Some({int(r['padding'])})" if r.get("padding") else "None"
    return (f"ReglaDocumento(quitarCerosIzquierda = {str(r['quitar_ceros_izquierda']).lower()}, "
            f"soloDigitos = {str(r['solo_digitos']).lower()}, conservarOriginal = {str(r['conservar_original']).lower()}, "
            f"padding = {pad})")


def mapa_scala(d: dict[str, Any], sangria: str = "", por_linea: bool = False) -> str:
    pares = [f"{q(k)} -> {q(v)}" for k, v in d.items()]
    return (",\n".join(sangria + p for p in pares)) if por_linea else ", ".join(pares)


def lista_scala(xs: list[Any]) -> str:
    return ", ".join(q(x) for x in xs)


def supuestos(spec: dict[str, Any]) -> str:
    lineas = [f"//   - {m['destino']}: {m['estado']}" for m in spec["campos_curados"] if m["estado"] != "cerrado"]
    lineas += [
        f"//   - paquete compartido: {spec['paquetes'].get('compartido_estado', 'cerrado')} (CUR-PT01)",
        f"//   - reglas de documento: {spec['reglas_documento'].get('estado', 'cerrado')} (RF-CUR-08)",
        f"//   - outputEventType: {spec['fuente'].get('output_event_type_estado', 'cerrado')} (CUR-PA04)",
        f"//   - convención de nombre de archivo: {spec['archivo'].get('estado', 'cerrado')} (D8)",
    ]
    return "\n".join(lineas)


def abiertos(spec: dict[str, Any]) -> str:
    items = spec.get("campos_abiertos", [])
    if not items:
        return "  // Sin campos abiertos."
    cab = "  // Campos pedidos por la frontera que NO se generan hasta cerrar la decisión:"
    return "\n".join([cab] + [f"  //   - {a['destino']}: {a['motivo']}" for a in items])


def lookups_hocon(spec: dict[str, Any]) -> str:
    bloques = []
    for c in spec["catalogos"]:
        if not c["registrar_en_curation"]:
            continue
        bloques.append("\n".join([
            "    {",
            f"      tableName = {q(c['tabla'])}",
            f"      file = ${{oxygen.application.paths.lookupHome}}{q('/' + c['archivo'])}",
            f"      delimiter = {q(c['delimitador'])}",
            "      keys = [",
            f"        {{ index = {q('+'.join(c['claves']))} }}",
            "      ]",
            "    }",
        ]))
    if not bloques:
        raise RenderError("ningún catálogo con registrar_en_curation = true")
    return ",\n".join(bloques)


def domain_dictionary(spec: dict[str, Any]) -> str:
    filas = [{"name": m["destino"], "type": DD_TIPO[m["tipo_salida"]], "nullable": True} for m in spec["campos_curados"]]
    filas.append({"name": spec["parametros"]["campo_advertencias"], "type": "array<string>", "nullable": False})
    return ",\n".join("    " + json.dumps(f, ensure_ascii=False) for f in filas)


def casos_scala(spec: dict[str, Any], eventos: list[dict[str, Any]], curados: list[dict[str, Any]]) -> str:
    por_id = {c["eventId"]: c for c in curados}
    bloques = []
    for ev in eventos:
        c = por_id.get(ev["eventId"])
        if c is None:
            continue  # descartado por el motor: se documenta en el resumen, no aplica al núcleo
        errores = ", ".join(f"({q(e['errorCodeValue'])}, {q(e['errorLevel'])})" for e in ev["errorCodes"])
        bloques.append("\n".join([
            f"  test({q(ev['eventId'] + ': ' + ev['_caso'])}) {{",
            f"    val esperado = {tq(compacto(c['curatedRecordJson']))}",
            f"    assert(curarJson({tq(ev['validatedRecordJson'])}, {q(ev['lineage']['fileName'])}, Seq({errores})) == esperado)",
            "  }",
        ]))
    return "\n\n".join(bloques)


def huecos(spec: dict[str, Any], fixture: dict[str, Any], eventos: list[dict], curados: list[dict]) -> dict[str, Callable[[], Any]]:
    f, p, a = spec["fuente"], spec["parametros"], spec["archivo"]
    hom, rd, cc = spec["homologacion_tipo_documento"], spec["reglas_documento"], spec["campos_curados"]
    adv = p["campo_advertencias"]
    return {
        "version_skill": lambda: spec["meta"]["version_skill"],
        "Fuente": lambda: f["Fuente"],
        "fuente": lambda: f["fuente"],
        "output_event_type": lambda: str(f["output_event_type"]),
        "paquete_fuente": lambda: spec["paquetes"]["fuente"],
        "paquete_compartido": lambda: spec["paquetes"]["compartido"],
        "processor_clase": lambda: spec["processor"]["nombre_clase"],
        "processor_fqcn": lambda: f"{spec['paquetes']['fuente']}.processor.{spec['processor']['nombre_clase']}",
        "file_pattern_hocon": lambda: a["file_pattern"].replace("\\", "\\\\").replace('"', '\\"'),
        "regex_derivacion_scala": lambda: a["regex_derivacion"],
        "tipo_por_prefijo_scala": lambda: mapa_scala(a["tipo_por_prefijo"]),
        "archivo_base": lambda: a["ejemplos_sinteticos"][0],
        "zona_fechas": lambda: p["zona_horaria_fechas"],
        "formatos_fecha_scala": lambda: lista_scala(p["formatos_fecha"]),
        "conservar_enie": lambda: str(p["conservar_enie"]).lower(),
        "niveles_no_almacenar_scala": lambda: lista_scala(p["niveles_no_almacenar"]),
        "niveles_anular_scala": lambda: lista_scala(p["niveles_anular"]),
        "campo_advertencias": lambda: adv,
        "campos_entrada_scala": lambda: lista_scala(spec["campos_entrada"]),
        "codigos_error_scala": lambda: mapa_scala(spec["codigos_error"], "    ", por_linea=True),
        "tabla_homologacion": lambda: hom["catalogo"]["tabla"],
        "columna_clave_homologacion": lambda: hom["catalogo"]["columna_clave"],
        "columna_valor_homologacion": lambda: hom["catalogo"]["columna_valor"],
        "homologacion_scala": lambda: mapa_scala(hom["tabla"]),
        "claves_homologacion_scala": lambda: lista_scala(list(hom["tabla"].keys())),
        "pendientes_scala": lambda: lista_scala(hom["pendientes"]),
        "campo_tipo_documento": lambda: rd["campo_tipo"],
        "regla_documento_defecto_scala": lambda: regla_scala(rd["por_defecto"]),
        "reglas_documento_scala": lambda: ",\n".join(f"    {q(k)} -> {regla_scala(v)}" for k, v in rd["por_tipo"].items()),
        "mapeos_scala": lambda: "\n".join(mapeo_scala(m, i == len(cc) - 1) for i, m in enumerate(cc)),
        "claves_publicadas_scala": lambda: lista_scala([m["destino"] for m in cc] + [adv]),
        "campos_record_scala": lambda: ",\n".join(f"  {m['destino']}: Option[{SCALA_TIPO[m['tipo_salida']]}]" for m in cc),
        "campos_to_map_scala": lambda: "\n".join(
            f"    if (!omitidos({q(m['destino'])})) m.put({q(m['destino'])}, {m['destino']}.map(_.asInstanceOf[AnyRef]).orNull)"
            for m in cc),
        "campos_from_map_scala": lambda: ",\n".join(
            f"      {m['destino']} = valores.get({q(m['destino'])}).flatten.collect {{ case v: {SCALA_TIPO[m['tipo_salida']]} => v }}"
            for m in cc),
        "campos_abiertos_comentario": lambda: abiertos(spec),
        "supuestos_comentario": lambda: supuestos(spec),
        "lookups_hocon_entradas": lambda: lookups_hocon(spec),
        "domain_dictionary_entradas": lambda: domain_dictionary(spec),
        "eventos_jsonl": lambda: "\n".join(compacto(sin_meta(e)) for e in eventos),
        "casos_spec_scala": lambda: casos_scala(spec, eventos, curados),
        "evento_base_json_scala": lambda: tq(compacto(fixture["base_registro"])),
        "ruta_hocon": lambda: spec["rutas_destino"]["hocon"],
        "ruta_lookups_conf": lambda: spec["rutas_destino"]["lookups_conf"],
        "ruta_domain_dictionary": lambda: spec["rutas_destino"]["domain_dictionary"],
    }


def plan(spec: dict[str, Any]) -> list[tuple[str, str, str]]:
    """(id, plantilla, ruta de salida relativa a --out). Orden de curation_template.config."""
    r, F = spec["rutas_destino"], spec["fuente"]["Fuente"]
    return [
        ("CUR-TPL-01", "FieldMapping.scala.tpl", f"{r['raiz_compartido']}FieldMapping.scala"),
        ("CUR-TPL-02", "Transformations.scala.tpl", f"{r['raiz_compartido']}Transformaciones.scala"),
        ("CUR-TPL-10", "TransformationsSpec.scala.tpl", f"{r['raiz_tests_compartido']}TransformacionesSpec.scala"),
        ("CUR-TPL-03", "CurationSpec.scala.tpl", f"{r['raiz_fuente']}config/{F}CurationSpec.scala"),
        ("CUR-TPL-04", "CuratedEvent.scala.tpl", f"{r['raiz_fuente']}{F}CuratedEvent.scala"),
        ("CUR-TPL-05", "AcceptedEventReader.scala.tpl", f"{r['raiz_fuente']}{F}AcceptedRecord.scala"),
        ("CUR-TPL-08", "domain-dictionary.json.tpl", f"fragmentos/{Path(r['domain_dictionary']).stem}.{spec['fuente']['fuente']}.json"),
        ("CUR-TPL-12", "accepted-events.json.tpl", f"{r['test_resources']}accepted-events.json"),
        ("CUR-TPL-06", "CurationProcessor.scala.tpl", f"{r['raiz_fuente']}processor/{spec['processor']['nombre_clase']}.scala"),
        ("CUR-TPL-11", "SourceProcessorSpec.scala.tpl", f"{r['raiz_tests']}{spec['processor']['nombre_clase']}Spec.scala"),
        ("CUR-TPL-07", "curation-block.conf.tpl", f"fragmentos/{Path(r['hocon']).stem}.{spec['fuente']['fuente']}.conf"),
        ("CUR-TPL-09", "lookups.conf.tpl", f"fragmentos/{Path(r['lookups_conf']).stem}.{spec['fuente']['fuente']}.conf"),
    ]


def rellenar(texto: str, valores: dict[str, Callable[[], Any]], declarados: set[str], nombre: str) -> str:
    usados = set(HUECO.findall(texto))
    sin_declarar = usados - declarados
    if sin_declarar:
        raise RenderError(f"{nombre}: huecos no declarados en x-placeholders: {sorted(sin_declarar)}")
    sin_valor = usados - set(valores)
    if sin_valor:
        raise RenderError(f"{nombre}: huecos sin cálculo en el renderer: {sorted(sin_valor)}")
    cache = {k: str(valores[k]()) for k in usados}
    vacios = [k for k, v in cache.items() if v.strip() == ""]
    if vacios:
        raise RenderError(f"{nombre}: huecos con valor vacío: {vacios}")
    salida = HUECO.sub(lambda m: cache[m.group(1)], texto)
    if "{{" in salida and HUECO.search(salida):
        raise RenderError(f"{nombre}: quedaron huecos sin resolver")
    return salida


def csv_homologacion(spec: dict[str, Any]) -> str:
    hom = spec["homologacion_tipo_documento"]
    cat = next(c for c in spec["catalogos"] if c["tabla"] == hom["catalogo"]["tabla"])
    sep = cat["delimitador"]
    desc = hom.get("descripciones", {})
    filas = [sep.join(cat["columnas"])]
    filas += [sep.join([k, v, desc.get(k, "")]) for k, v in hom["tabla"].items()]
    return "\n".join(filas) + "\n"


# ---------------------------------------------------------------- principal
def render(spec_path: Path, out: Path, templates: Path, schema_path: Path, write_expected: Path | None = None) -> dict[str, Any]:
    spec = cargar_yaml(spec_path)
    schema = cargar_yaml(schema_path)
    validar_spec(spec, schema)
    declarados = set(schema.get("x-placeholders", {}))
    fixture_path = (spec_path.parent / spec["fixture"]["entrada"]).resolve()
    fixture = cargar_yaml(fixture_path)
    eventos = construir_eventos(spec, fixture)
    curados, resumen = salida_esperada(spec, eventos)
    valores = huecos(spec, fixture, eventos, curados)
    faltan = declarados - set(valores)
    if faltan:
        raise RenderError(f"x-placeholders sin cálculo en el renderer: {sorted(faltan)}")

    if out.exists():
        shutil.rmtree(out)
    manifest = {"skill": "edf-curation-stage", "version": spec["meta"]["version_skill"],
                "compilacion": spec["meta"]["compilacion"], "sintetico": True, "archivos": []}
    for tpl_id, tpl, destino in plan(spec):
        texto = (templates / tpl).read_text(encoding="utf-8")
        contenido = rellenar(texto, valores, declarados, tpl)
        ruta = out / destino
        ruta.parent.mkdir(parents=True, exist_ok=True)
        ruta.write_text(contenido, encoding="utf-8", newline="\n")
        manifest["archivos"].append({"plantilla": tpl_id, "archivo": destino,
                                     "fusionar": destino.startswith("fragmentos/")})

    # Lookups sintéticos de prueba (CUR-TPL-12 complementa): 202 desde el spec, el resto copiado.
    lk_out = out / spec["rutas_destino"]["test_resources"] / "lookups"
    lk_out.mkdir(parents=True, exist_ok=True)
    lk_in = spec_path.parent / spec["fixture"]["entrada"]
    lk_in = lk_in.parent / spec["fixture"]["lookups_dir"]
    for c in spec["catalogos"]:
        nombre = Path(c["archivo"]).name
        if c["tabla"] == spec["homologacion_tipo_documento"]["catalogo"]["tabla"]:
            (lk_out / nombre).write_text(csv_homologacion(spec), encoding="utf-8", newline="\n")
        elif (lk_in / nombre).exists():
            shutil.copyfile(lk_in / nombre, lk_out / nombre)
        else:
            raise RenderError(f"falta el lookup sintético {nombre}")

    exp = out / "expected"
    exp.mkdir(parents=True, exist_ok=True)
    (exp / "curated-events.json").write_text(json.dumps(curados, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    (exp / "resumen.json").write_text(json.dumps(resumen, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    if write_expected:
        write_expected.mkdir(parents=True, exist_ok=True)
        for nombre in ("curated-events.json", "resumen.json"):
            shutil.copyfile(exp / nombre, write_expected / nombre)

    (out / "MANIFEST.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    return manifest


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--spec", type=Path, default=SKILL_ROOT / "assets/examples/sisben/source-spec.sisben.yaml")
    ap.add_argument("--out", type=Path, default=SKILL_ROOT / "assets/examples/sisben/generated")
    ap.add_argument("--templates", type=Path, default=SKILL_ROOT / "assets/templates")
    ap.add_argument("--schema", type=Path, default=SKILL_ROOT / "assets/schema/source-spec.schema.yaml")
    ap.add_argument("--write-expected", type=Path, default=None)
    args = ap.parse_args(argv)
    print("AVISO: render_templates.py está DEPRECADO; use java -jar scripts/java/target/render-templates.jar "
          "(ver scripts/DEPRECADOS.md).", file=sys.stderr)
    try:
        manifest = render(args.spec, args.out, args.templates, args.schema, args.write_expected)
    except (RenderError, OSError, yaml.YAMLError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(f"OK: {len(manifest['archivos'])} artefactos en {args.out} (compilación {manifest['compilacion']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
