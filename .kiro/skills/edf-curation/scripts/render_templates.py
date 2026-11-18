#!/usr/bin/env python3
"""Genera los artefactos de Curation (CUR-TPL-01..12) desde un source-spec. Solo genera: no compila nada.

Vigente desde la 1.7.0 del framework (2026-11-18). Es la traducción del generador Java de ``scripts/java``
(deprecado, ver ``scripts/DEPRECADOS.md``): con el mismo source-spec produce los mismos archivos byte a byte.
Agrega la transformación ``concatenacion``.

Uso (desde la raíz del skill):
    python scripts/render_templates.py \
        --spec assets/examples/sisben/source-spec.sisben.yaml \
        --out  assets/examples/sisben/generated

Pasos: valida el source-spec contra assets/schema/source-spec.schema.yaml, calcula los huecos declarados en
x-placeholders, rellena cada plantilla de assets/templates/, verifica que no quede ningún {{hueco}} sin
resolver y escribe:
  <out>/<ruta del repo destino>      código Scala y recursos de test (mismas rutas del fork)
  <out>/fragmentos/                  HOCON y domain dictionary a FUSIONAR (regla aditiva)
  <out>/expected/                    salida esperada del fixture (implementación de referencia)
  <out>/MANIFEST.json                qué plantilla generó qué archivo y adónde va en el fork
Con --write-expected DIR copia además la salida esperada a DIR (golden versionado). Sin red.
Con --solo-verificar hace todo el cálculo de la generación en memoria y no escribe nada (lo usa edf-analisis):
si pasa, la generación con --out también pasa.
ATENCIÓN: --out se borra y se vuelve a crear.

Requiere Python 3.9+ con PyYAML y jsonschema. Salida: 0 bien; 1 spec inválida o error de generación;
2 error de uso.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import curation_reference as ref  # noqa: E402

SKILL_ROOT = Path(__file__).resolve().parent.parent
# Marca de sintético de la carpeta de lookups (AC-30): se copia del fixture a los lookups generados.
MARCA_LOOKUPS = "LEEME.md"
# Zona de asOfDate de la etapa anterior si la spec no la declara (D3; ADR-G4).
ZONA_ASOFDATE_POR_DEFECTO = "America/Bogota"
# Origen de zona_horaria_fechas si la spec no lo declara (ADR-G4).
ORIGEN_ZONA_POR_DEFECTO = "supuesto"
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
# Transformaciones que no leen un campo de entrada: no llevan origen.
SIN_ORIGEN = ("desde_archivo", "copia_de", "constante", "concatenacion")
# Huecos que pueden quedar vacíos: representan una lista vacía legítima dentro de un Set(...) de Scala (F-FW-13).
PUEDEN_QUEDAR_VACIOS = {"pendientes_scala", "niveles_no_almacenar_scala", "niveles_anular_scala"}


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


def leer_texto(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")


def cargar_yaml(ruta: Path) -> Any:
    return yaml.safe_load(leer_texto(ruta))


def escribir(ruta: Path, texto: str) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(texto, encoding="utf-8", newline="\n")


def nombre_archivo(ruta: str) -> str:
    """Último componente de la ruta (separadores / y \\)."""
    return re.split(r"[\\/]", ruta)[-1]


def stem(ruta: str) -> str:
    nombre = nombre_archivo(ruta)
    punto = nombre.rfind(".")
    return nombre[:punto] if punto > 0 else nombre


# ---------------------------------------------------------------- validación
def validar_spec(spec: Any, schema: dict[str, Any]) -> None:
    import jsonschema

    esquema = {k: v for k, v in schema.items() if k != "x-placeholders"}
    errores = []
    for e in jsonschema.Draft202012Validator(esquema).iter_errors(spec):
        ruta = "/".join(str(p) for p in e.absolute_path)
        errores.append((ruta, f"{ruta or '(raíz)'}: {e.message}"))
    if errores:
        errores.sort(key=lambda x: x[0])
        raise RenderError("source-spec inválido: " + "; ".join(m for _, m in errores[:10]))
    validar_coherencia(spec)


def validar_coherencia(spec: dict[str, Any]) -> None:
    """Reglas que el JSON Schema no expresa (CUR-RC05, CUR-TPL-03)."""
    cc = spec["campos_curados"]
    destinos = [m["destino"] for m in cc]
    duplicados = {d for d in destinos if destinos.count(d) > 1}
    if duplicados:
        raise RenderError(f"nombres publicados repetidos: {sorted(duplicados)}")
    if spec["parametros"]["campo_advertencias"] in destinos:
        raise RenderError("el campo de advertencias no puede ser también un campo curado")
    entradas = set(spec["campos_entrada"])
    vistos: set[str] = set()
    for m in cc:
        t = m["transformacion"]
        destino, origen, tipo = m["destino"], m.get("origen"), t["tipo"]
        if origen and origen not in entradas:
            raise RenderError(f"{destino}: origen {origen} no está en campos_entrada")
        if tipo in SIN_ORIGEN and origen:
            raise RenderError(f"{destino}: {tipo} no lleva origen")
        if tipo not in SIN_ORIGEN and not origen:
            raise RenderError(f"{destino}: falta origen")
        if tipo == "padding_izquierda" and "longitud" not in t:
            raise RenderError(f"{destino}: padding_izquierda requiere longitud")
        if tipo == "desde_archivo" and "atributo" not in t:
            raise RenderError(f"{destino}: desde_archivo requiere atributo")
        if tipo == "copia_de" and t.get("campo") not in vistos:
            raise RenderError(f"{destino}: copia_de debe referirse a un campo anterior")
        if tipo == "concatenacion":
            campos = t.get("campos")
            if not campos:
                raise RenderError(f"{destino}: concatenacion requiere campos")
            fuera = [c for c in campos if c not in vistos]
            if fuera:
                raise RenderError(f"{destino}: concatenacion debe referirse a campos anteriores: {fuera}")
        vistos.add(destino)
    abiertos = sorted({a["destino"] for a in spec.get("campos_abiertos") or []} & set(destinos))
    if abiertos:
        raise RenderError(f"campos abiertos no pueden generarse: {abiertos}")
    # F-FW-11: los campos de extension_subscriber_relation deben ser campos curados publicados.
    ext = spec.get("extension_subscriber_relation")
    if ext is not None:
        sin_publicar = sorted({c for c in ext.get("campos") or [] if c not in destinos})
        if sin_publicar:
            raise RenderError(f"extension_subscriber_relation: campos que no se publican: {sin_publicar}")
    for codigo, campo in spec["codigos_error"].items():
        if campo not in entradas:
            raise RenderError(f"código {codigo}: campo {campo} no está en campos_entrada")
    zona_asof = zona_asofdate(spec)
    try:
        ref.zona(zona_asof)
    except ValueError:
        raise RenderError(f"parametros.zona_asofdate_etapa_anterior: zona inválida {zona_asof}") from None
    archivo = spec["archivo"]
    pat = re.compile(archivo["file_pattern"])
    for ej in archivo.get("ejemplos_sinteticos") or []:
        if not pat.fullmatch(ej):
            raise RenderError(f"filePattern no cubre el ejemplo {ej}")
    for ej in archivo.get("contraejemplos_sinteticos") or []:
        if pat.fullmatch(ej):
            raise RenderError(f"filePattern cubre el contraejemplo {ej}")


# ---------------------------------------------------------------- zona (ADR-G4, AC-CUR-10)
def zona_asofdate(spec: dict[str, Any]) -> str:
    """parametros.zona_asofdate_etapa_anterior, o America/Bogota (D3) si la spec no la declara."""
    return str(spec["parametros"].get("zona_asofdate_etapa_anterior", ZONA_ASOFDATE_POR_DEFECTO))


def origen_zona_fechas(spec: dict[str, Any]) -> str:
    """parametros.zona_horaria_fechas_origen (requisito | supuesto), o supuesto si la spec no lo declara."""
    return str(spec["parametros"].get("zona_horaria_fechas_origen", ORIGEN_ZONA_POR_DEFECTO))


def advertencias_zona(spec: dict[str, Any]) -> list[str]:
    """Advertencias para stderr y MANIFEST.json: hoy, la zona de fechas distinta de la de asOfDate (C-CUR-04)."""
    zona_fechas = str(spec["parametros"]["zona_horaria_fechas"])
    zona_asof = zona_asofdate(spec)
    if ref.zona_normalizada(zona_fechas) == ref.zona_normalizada(zona_asof):
        return []
    return [f"ADVERTENCIA: zona_horaria_fechas ({zona_fechas}) difiere de la zona de asOfDate de la etapa "
            f"anterior ({zona_asof}) (C-CUR-04)"]


# ---------------------------------------------------------------- fixture
def epoch_ms(texto: str, zona: str) -> int:
    """Fecha del fixture ("%Y-%m-%d %H:%M:%S", "%d/%m/%Y" o "%Y-%m-%d") a epoch ms en la zona dada."""
    for fmt in ("%Y-%m-%d %H:%M:%S", "%d/%m/%Y", "%Y-%m-%d"):
        try:
            dt = datetime.strptime(texto, fmt)
        except (ValueError, TypeError):
            continue
        return ref._epoch_ms(dt.replace(tzinfo=ref.zona(zona)))
    raise RenderError(f"fecha de fixture inválida: {texto}")


def fecha_recepcion(archivo: str) -> int:
    """receivedDate: marca AAAAMMDDhhmmss±hhmm del nombre del archivo, en epoch ms."""
    m = re.search(r"\.(\d{14})([+-]\d{4})", archivo)
    if not m:
        raise RenderError(f"nombre de fixture sin marca de tiempo: {archivo}")
    marca, off = m.group(1), m.group(2)
    try:
        signo = -1 if off[0] == "-" else 1
        tz = timezone(signo * timedelta(hours=int(off[1:3]), minutes=int(off[3:5])))
        dt = datetime(int(marca[0:4]), int(marca[4:6]), int(marca[6:8]), int(marca[8:10]), int(marca[10:12]),
                      int(marca[12:14]), tzinfo=tz)
    except ValueError:
        raise RenderError(f"marca de tiempo no reconocida: {marca}{off}") from None
    return ref._epoch_ms(dt)


def construir_eventos(spec: dict[str, Any], fixture: dict[str, Any]) -> list[dict[str, Any]]:
    """Eventos TaggedValidatedEvent sintéticos (forma de 2404, campos que usa Curation)."""
    fuente = spec["fuente"]
    eventos = []
    for e in fixture["eventos"]:
        registro = {**fixture["base_registro"], **(e.get("registro") or {})}
        cns = registro["FECHA_ACTUALIZACION_CNS"]
        recibido = fecha_recepcion(e["archivo"])
        eventos.append({
            "lineage": {
                "dataSource": fuente["datasource"],
                "receivedDate": recibido,
                "fileName": e["archivo"],
                "subscriber": fuente["subscriber"],
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
            "eventType": fuente["output_event_type"],
            # Guard-rails de Validation (D3): As-Of-Date = FECHA_ACTUALIZACION_CNS en la zona de la etapa
            # anterior (parametros.zona_asofdate_etapa_anterior, por defecto America/Bogota; ADR-G4).
            "asOfDate": epoch_ms(cns, zona_asofdate(spec)),
            "subscriberRelation": registro["NUM_DOCUMENTO"].strip(),
            "subscriber": fuente["subscriber"],
            "headerErrors": [],
            "footerErrors": [],
            "keys": "",
            "fileTag": "",
            "_caso": e.get("caso"),
        })
    return eventos


def sin_meta(evento: dict[str, Any]) -> dict[str, Any]:
    return {k: v for k, v in evento.items() if not k.startswith("_")}


def salida_esperada(spec: dict[str, Any], eventos: list[dict[str, Any]]) -> tuple[list[dict], dict]:
    """Eventos curados y resumen de la salida esperada del fixture."""
    curados, descartados = [], []
    for ev in eventos:
        r = ref.curar_evento(sin_meta(ev), spec)
        if r is None:
            descartados.append({"eventId": ev["eventId"], "fileName": ev["lineage"]["fileName"],
                                "motivo": "sin processor para lineage.fileName (CUR-AP02, descarte sin log)"})
        else:
            curados.append(r)
    campo_adv = spec["parametros"]["campo_advertencias"]
    advertencias, omitidas, homologacion = {}, {}, {}
    con_advertencias = 0
    for c in curados:
        rec = c["curatedRecordJson"]
        ident = str(c["eventId"])
        if rec.get(campo_adv):
            con_advertencias += 1
            advertencias[ident] = rec[campo_adv]
        faltan = [m["destino"] for m in spec["campos_curados"] if m["destino"] not in rec]
        if faltan:
            omitidas[ident] = faltan
        entrada = next(e for e in eventos if e["eventId"] == c["eventId"])
        tip = json.loads(entrada["validatedRecordJson"]).get("TIP_DOCUMENTO")
        homologacion[ident] = [tip, rec.get("tipoDocumento")]
    resumen = {
        "sintetico": True,
        "eventos_entrada": len(eventos),
        "eventos_curados": len(curados),
        "eventos_descartados_por_motor": descartados,
        "eventos_con_advertencias": con_advertencias,
        "advertencias": advertencias,
        "claves_omitidas_por_severidad": omitidas,
        "homologacion_tipo_documento": homologacion,
    }
    return curados, resumen


# ---------------------------------------------------------------- huecos
def lista_scala(xs: list[Any]) -> str:
    return ", ".join(q(x) for x in xs)


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
    elif tipo == "concatenacion":
        tr = f"Transformacion.Concatenacion(Seq({lista_scala(t['campos'])}), {q(t.get('separador', ' '))})"
    else:
        tr = "Transformacion.Constante(None)" if t.get("valor") is None else f"Transformacion.Constante(Some({q(t['valor'])}))"
    salida = "TipoSalida." + TIPO_SALIDA_SCALA[m["tipo_salida"]]
    cat = m.get("catalogo")
    cat_s = f"Some(Catalogo({q(cat['tabla'])}, {q(cat['columna'])}))" if cat else "None"
    estado = m.get("estado")
    # AC-35 (2026-10-21): toda marca ASSUMPTION lleva un ID de decisiones-y-abiertos.md; el supuesto de un
    # FieldMapping es el nombre publicado (C-CUR-02).
    if estado == "cerrado":
        marca = ""
    else:
        marca = "  // " + ("ASSUMPTION: C-CUR-02 (supuesto)" if estado == "supuesto" else str(estado))
    coma = "" if ultimo else ","
    return f"    FieldMapping({q(m['destino'])}, {q(m.get('origen'))}, {tr}, {salida}, {cat_s}){coma}{marca}"


def regla_scala(r: dict[str, Any]) -> str:
    pad = f"Some({int(r['padding'])})" if r.get("padding") else "None"
    return (f"ReglaDocumento(quitarCerosIzquierda = {str(r.get('quitar_ceros_izquierda')).lower()}, "
            f"soloDigitos = {str(r.get('solo_digitos')).lower()}, "
            f"conservarOriginal = {str(r.get('conservar_original')).lower()}, padding = {pad})")


def reglas_documento_scala(rd: dict[str, Any]) -> str:
    """Entradas de ReglasDocumento. Sin reglas por tipo (la misma regla para todos), un comentario: Map() vacío."""
    if not rd.get("por_tipo"):
        return "    // Sin reglas por tipo: todos los tipos usan ReglaDocumentoPorDefecto."
    return ",\n".join(f"    {q(k)} -> {regla_scala(v)}" for k, v in rd["por_tipo"].items())


def mapa_scala(d: dict[Any, Any], sangria: str = "", por_linea: bool = False) -> str:
    pares = [f"{q(k)} -> {q(v)}" for k, v in d.items()]
    return ",\n".join(sangria + p for p in pares) if por_linea else ", ".join(pares)


def supuestos(spec: dict[str, Any]) -> str:
    lineas = [f"//   - {m['destino']}: {m.get('estado')}" for m in spec["campos_curados"] if m.get("estado") != "cerrado"]
    lineas += [
        f"//   - paquete compartido: {spec['paquetes'].get('compartido_estado', 'cerrado')} (CUR-PT01)",
        f"//   - reglas de documento: {spec['reglas_documento'].get('estado', 'cerrado')} (RF-CUR-08)",
        f"//   - outputEventType: {spec['fuente'].get('output_event_type_estado', 'cerrado')} (CUR-PA04)",
        f"//   - convención de nombre de archivo: {spec['archivo'].get('estado', 'cerrado')} (D8)",
    ]
    return "\n".join(lineas)


def abiertos(spec: dict[str, Any]) -> str:
    items = spec.get("campos_abiertos") or []
    if not items:
        return "  // Sin campos abiertos."
    cab = "  // Campos pedidos por la frontera que NO se generan hasta cerrar la decisión:"
    return "\n".join([cab] + [f"  //   - {a['destino']}: {a['motivo']}" for a in items])


ENRIQUECIMIENTO_SIN_VALORES = {"businessLineCode", "typeOfData"}  # IN-06 / CUR-PT06


def parte_manual_comentario(spec: dict[str, Any]) -> str:
    """Comentario final del processor: lo que queda manual según la spec (no un texto fijo)."""
    abiertos = {a["destino"] for a in spec.get("campos_abiertos") or []}
    lineas = []
    if abiertos & ENRIQUECIMIENTO_SIN_VALORES:
        lineas += ["  // TODO(manual) IN-06 / CUR-PT06: Business Line Code (catálogo de terceras fuentes) y Type of Data",
                   "  //   (catálogo 170) sin valores. Agregarlos en source-spec cuando se cierren; no inventarlos aquí."]
    for m in spec["campos_curados"]:
        t = m["transformacion"]
        if t["tipo"] == "concatenacion":
            lineas.append(f"  // {m['destino']}: concatenacion de {', '.join(t['campos'])} con separador "
                          f"{q(t.get('separador', ' '))}; se genera desde el source-spec (no es parte manual).")
    if "nombreCompleto" in abiertos:
        lineas.append("  // TODO(manual) CUR-PA01 / P2: nombre completo bloqueado hasta confirmar el orden de concatenación.")
    return "\n".join(lineas) if lineas else "  // Sin parte manual: todos los campos de la spec se generan."


def pruebas_pendientes_comentario(spec: dict[str, Any]) -> str:
    """Pruebas que el generador no puede escribir, según la spec."""
    abiertos = {a["destino"] for a in spec.get("campos_abiertos") or []}
    if abiertos & ENRIQUECIMIENTO_SIN_VALORES:
        return "\n".join([
            "  // TODO(manual): casos de enriquecimiento (IN-06) y de la derivación del tipo de archivo cuando",
            "  // se cierren CUR-PT06 y CUR-PA03. El payload se conserva: probarlo con un TaggedValidatedEvent",
            "  // real del framework (enrichEventWithPayload devuelve event.payload)."])
    return "\n".join([
        "  // En el fork: probar que el payload se conserva con un TaggedValidatedEvent real del framework",
        "  // (enrichEventWithPayload devuelve event.payload); el núcleo ya está cubierto por los casos de arriba."])


def lookups_hocon(spec: dict[str, Any]) -> str:
    bloques = []
    for c in spec["catalogos"]:
        if not c.get("registrar_en_curation"):
            continue
        bloques.append("\n".join([
            "    {",
            f"      tableName = {q(c['tabla'])}",
            f"      file = ${{oxygen.application.paths.lookupHome}}{q('/' + str(c['archivo']))}",
            f"      delimiter = {q(c['delimitador'])}",
            "      keys = [",
            f"        {{ index = {q('+'.join(str(x) for x in c['claves']))} }}",
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


# ---------------------------------------------------------------- subscriberRelation (F-FW-11, D14)
def relacion_override_scala(spec: dict[str, Any]) -> str:
    """Miembro override del processor; sin la extensión en la spec, solo un comentario."""
    ext = spec.get("extension_subscriber_relation")
    if ext is None:
        return "  // subscriberRelation no se sobrescribe: se copia del evento aceptado (D3)."
    clase = spec["processor"]["nombre_clase"]
    componentes = str(ext["separador"]).join(str(c) for c in ext["campos"])
    return "\n".join([
        "  /**",
        f"   * subscriberRelation = {componentes} (extension_subscriber_relation). Usa el mismo núcleo que",
        "   * curateEvent; si falta algún componente conserva el del evento aceptado. Nunca lanza.",
        "   * ASSUMPTION: D14 — firma del gancho CurationProcessor.subscriberRelation sin verificar contra el JAR (F-01).",
        "   */",
        "  override def subscriberRelation(event: TaggedValidatedEvent): String = {",
        "    val errores = Option(event.errorCodes).map(_.toSeq).getOrElse(Seq.empty)",
        "      .map(e => (e.errorCodeValue, e.errorLevel))",
        "    val fileName = Option(event.lineage).map(_.fileName).orNull",
        f"    {clase}.relacion(event.validatedRecordJson, fileName, errores, homologacion, event.subscriberRelation)",
        "  }",
    ])


def relacion_helper_scala(spec: dict[str, Any]) -> str:
    """Núcleo puro en el objeto del processor: se prueba sin Spark."""
    ext = spec.get("extension_subscriber_relation")
    if ext is None:
        return "  // subscriberRelation: sin extension_subscriber_relation en la spec (D3)."
    return "\n".join([
        "  /** Campos curados que forman subscriberRelation y su separador (extension_subscriber_relation, D14). */",
        f"  private val CamposRelacion: Seq[String] = Seq({lista_scala(ext['campos'])})",
        f"  private val SeparadorRelacion: String = {q(ext['separador'])}",
        "",
        "  /** subscriberRelation = campos curados unidos; si falta alguno, el del evento aceptado (`copiado`). Nunca lanza. */",
        "  def relacion(json: String, fileName: String, errores: Seq[(String, String)], homologacion: Map[String, String],",
        "               copiado: String): String =",
        "    try {",
        "      val m = curar(json, fileName, errores, homologacion).toJavaMap",
        "      val partes = CamposRelacion.map(c => Option(m.get(c)).map(_.toString.trim).filter(_.nonEmpty))",
        "      if (partes.forall(_.isDefined)) partes.flatten.mkString(SeparadorRelacion) else copiado",
        "    } catch { case NonFatal(_) => copiado }",
    ])


def _errores_scala(ev: dict[str, Any]) -> str:
    return ", ".join(f"({q(e['errorCodeValue'])}, {q(e['errorLevel'])})" for e in ev.get("errorCodes") or [])


def casos_relacion_scala(spec: dict[str, Any], eventos: list[dict], curados: list[dict]) -> str:
    """Pruebas de subscriberRelation sobre el fixture; el esperado sale de ref.subscriber_relation."""
    if spec.get("extension_subscriber_relation") is None:
        return "  // Sin extension_subscriber_relation: subscriberRelation se copia del evento aceptado."
    clase = spec["processor"]["nombre_clase"]
    fuente = spec["fuente"]["Fuente"]
    por_id = {c["eventId"]: c for c in curados}
    lineas = ['  test("subscriberRelation: campos curados unidos; si falta alguno, el del evento aceptado") {']
    for ev in eventos:
        c = por_id.get(ev["eventId"])
        if c is None:
            continue
        esperado = ref.subscriber_relation(c["curatedRecordJson"], spec, "COPIADO")
        lineas.append(
            f"    assert({clase}.relacion({tq(ev['validatedRecordJson'])}, {q(ev['lineage']['fileName'])}, "
            f"Seq({_errores_scala(ev)}), {fuente}CurationSpec.Homologacion, \"COPIADO\") == {q(esperado)})"
            f"  // {ev['eventId']}")
    lineas.append("  }")
    return "\n".join(lineas)


def casos_scala(eventos: list[dict[str, Any]], curados: list[dict[str, Any]]) -> str:
    por_id = {c["eventId"]: c for c in curados}
    bloques = []
    for ev in eventos:
        c = por_id.get(ev["eventId"])
        if c is None:
            continue  # descartado por el motor: se documenta en el resumen, no aplica al núcleo
        bloques.append("\n".join([
            f"  test({q(str(ev['eventId']) + ': ' + str(ev.get('_caso')))}) {{",
            f"    val esperado = {tq(compacto(c['curatedRecordJson']))}",
            f"    assert(curarJson({tq(ev['validatedRecordJson'])}, {q(ev['lineage']['fileName'])}, "
            f"Seq({_errores_scala(ev)})) == esperado)",
            "  }",
        ]))
    return "\n\n".join(bloques)


def huecos(spec: dict[str, Any], fixture: dict[str, Any], eventos: list[dict],
           curados: list[dict]) -> dict[str, Callable[[], Any]]:
    """Valor (perezoso) de cada hueco declarado en x-placeholders."""
    f, p, a = spec["fuente"], spec["parametros"], spec["archivo"]
    hom, rd, cc = spec["homologacion_tipo_documento"], spec["reglas_documento"], spec["campos_curados"]
    paquetes, processor, rutas = spec["paquetes"], spec["processor"], spec["rutas_destino"]
    adv = p["campo_advertencias"]
    return {
        "version_skill": lambda: spec["meta"]["version_skill"],
        "Fuente": lambda: f["Fuente"],
        "fuente": lambda: f["fuente"],
        "output_event_type": lambda: str(f["output_event_type"]),
        "paquete_fuente": lambda: paquetes["fuente"],
        "paquete_compartido": lambda: paquetes["compartido"],
        "processor_clase": lambda: processor["nombre_clase"],
        "processor_fqcn": lambda: f"{paquetes['fuente']}.processor.{processor['nombre_clase']}",
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
        "reglas_documento_scala": lambda: reglas_documento_scala(rd),
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
        "casos_spec_scala": lambda: casos_scala(eventos, curados),
        "evento_base_json_scala": lambda: tq(compacto(fixture["base_registro"])),
        "subscriber_relation_override_scala": lambda: relacion_override_scala(spec),
        "subscriber_relation_helper_scala": lambda: relacion_helper_scala(spec),
        "casos_relacion_scala": lambda: casos_relacion_scala(spec, eventos, curados),
        "parte_manual_comentario": lambda: parte_manual_comentario(spec),
        "pruebas_pendientes_comentario": lambda: pruebas_pendientes_comentario(spec),
        "ruta_hocon": lambda: rutas["hocon"],
        "ruta_lookups_conf": lambda: rutas["lookups_conf"],
        "ruta_domain_dictionary": lambda: rutas["domain_dictionary"],
    }


def plan(spec: dict[str, Any]) -> list[tuple[str, str, str]]:
    """(id, plantilla, ruta de salida relativa a --out). Orden de curation_template.config."""
    r, F, fuente = spec["rutas_destino"], spec["fuente"]["Fuente"], spec["fuente"]["fuente"]
    clase = spec["processor"]["nombre_clase"]
    return [
        ("CUR-TPL-01", "FieldMapping.scala.tpl", f"{r['raiz_compartido']}FieldMapping.scala"),
        ("CUR-TPL-02", "Transformations.scala.tpl", f"{r['raiz_compartido']}Transformaciones.scala"),
        ("CUR-TPL-10", "TransformationsSpec.scala.tpl", f"{r['raiz_tests_compartido']}TransformacionesSpec.scala"),
        ("CUR-TPL-03", "CurationSpec.scala.tpl", f"{r['raiz_fuente']}config/{F}CurationSpec.scala"),
        ("CUR-TPL-04", "CuratedEvent.scala.tpl", f"{r['raiz_fuente']}{F}CuratedEvent.scala"),
        ("CUR-TPL-05", "AcceptedEventReader.scala.tpl", f"{r['raiz_fuente']}{F}AcceptedRecord.scala"),
        ("CUR-TPL-08", "domain-dictionary.json.tpl", f"fragmentos/{stem(r['domain_dictionary'])}.{fuente}.json"),
        ("CUR-TPL-12", "accepted-events.json.tpl", f"{r['test_resources']}accepted-events.json"),
        ("CUR-TPL-06", "CurationProcessor.scala.tpl", f"{r['raiz_fuente']}processor/{clase}.scala"),
        ("CUR-TPL-11", "SourceProcessorSpec.scala.tpl", f"{r['raiz_tests']}{clase}Spec.scala"),
        ("CUR-TPL-07", "curation-block.conf.tpl", f"fragmentos/{stem(r['hocon'])}.{fuente}.conf"),
        ("CUR-TPL-09", "lookups.conf.tpl", f"fragmentos/{stem(r['lookups_conf'])}.{fuente}.conf"),
    ]


def rellenar(texto: str, valores: dict[str, Callable[[], Any]], declarados: set[str], nombre: str) -> str:
    usados = sorted(set(HUECO.findall(texto)))
    sin_declarar = sorted(set(usados) - declarados)
    if sin_declarar:
        raise RenderError(f"{nombre}: huecos no declarados en x-placeholders: {sin_declarar}")
    sin_valor = sorted(set(usados) - set(valores))
    if sin_valor:
        raise RenderError(f"{nombre}: huecos sin cálculo en el renderer: {sin_valor}")
    cache = {k: str(valores[k]()) for k in usados}
    vacios = [k for k, v in cache.items() if v.strip() == "" and k not in PUEDEN_QUEDAR_VACIOS]
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
    desc = hom.get("descripciones") or {}
    filas = [sep.join(str(c) for c in cat["columnas"])]
    filas += [sep.join([str(k), str(v), str(desc[k]) if k in desc else ""]) for k, v in hom["tabla"].items()]
    return "\n".join(filas) + "\n"


# ---------------------------------------------------------------- principal
def sha256(archivo: Path) -> str:
    return hashlib.sha256(archivo.read_bytes()).hexdigest()


def preparar(spec_path: Path, templates: Path, schema_path: Path) -> dict[str, Any]:
    """Valida y calcula todo en memoria (plantillas rellenas, salida esperada, lookups); no escribe nada."""
    spec = cargar_yaml(spec_path)
    schema = cargar_yaml(schema_path)
    validar_spec(spec, schema)
    declarados = set(schema.get("x-placeholders") or {})
    fixture_spec = spec["fixture"]
    fixture_path = (spec_path.parent / fixture_spec["entrada"]).resolve()
    if not fixture_path.exists():
        raise RenderError(f"no existe el fixture {fixture_spec['entrada']} (relativo al source-spec)")
    fixture = cargar_yaml(fixture_path)
    eventos = construir_eventos(spec, fixture)
    curados, resumen = salida_esperada(spec, eventos)
    valores = huecos(spec, fixture, eventos, curados)
    faltan = sorted(declarados - set(valores))
    if faltan:
        raise RenderError(f"x-placeholders sin cálculo en el renderer: {faltan}")
    contenidos = [(tpl_id, destino, rellenar(leer_texto(templates / tpl), valores, declarados, tpl))
                  for tpl_id, tpl, destino in plan(spec)]
    lk_in = fixture_path.parent / fixture_spec["lookups_dir"]
    tabla_hom = spec["homologacion_tipo_documento"]["catalogo"]["tabla"]
    for c in spec["catalogos"]:
        nombre = nombre_archivo(c["archivo"])
        if c["tabla"] != tabla_hom and not (lk_in / nombre).exists():
            raise RenderError(f"falta el lookup sintético {nombre} en {lk_in} (lookups_dir es relativo a la carpeta de fixture.entrada)")
    # AC-30 (plan #9, 2026-10-21): marca de sintético de los lookups entregados al fork. Va en un archivo
    # aparte: un comentario dentro del CSV podría romper la carga de OxygenLookup (lee con cabecera).
    if not (lk_in / MARCA_LOOKUPS).exists():
        raise RenderError(f"falta la marca de sintético de los lookups: {lk_in / MARCA_LOOKUPS}")
    return {"spec": spec, "curados": curados, "resumen": resumen, "contenidos": contenidos,
            "lk_in": lk_in, "tabla_hom": tabla_hom}


def verificar(spec_path: Path, schema_path: Path, templates: Path) -> str:
    """Verificación completa sin escribir: esquema, coherencia, fixture, lookups y las 12 plantillas rellenas.

    Si pasa, la generación con --out también pasa (mismo cálculo).
    """
    ctx = preparar(spec_path, templates, schema_path)
    cc = ctx["spec"]["campos_curados"]
    concat = sum(1 for m in cc if m["transformacion"]["tipo"] == "concatenacion")
    return (f"source-spec OK ({len(cc)} campos curados, {concat} con concatenacion; "
            f"{len(ctx['contenidos'])} plantillas y {len(ctx['curados'])} eventos esperados calculados)")


def render(spec_path: Path, out: Path, templates: Path, schema_path: Path,
           write_expected: Path | None = None) -> dict[str, Any]:
    # Todo o nada: se calcula todo antes de tocar --out.
    ctx = preparar(spec_path, templates, schema_path)
    spec, curados, resumen = ctx["spec"], ctx["curados"], ctx["resumen"]
    contenidos, lk_in, tabla_hom = ctx["contenidos"], ctx["lk_in"], ctx["tabla_hom"]
    rutas = spec["rutas_destino"]

    if out.exists():
        shutil.rmtree(out)
    meta = spec["meta"]
    manifest: dict[str, Any] = {"skill": "edf-curation-stage", "version": meta["version_skill"],
                                "compilacion": meta.get("compilacion"), "sintetico": True, "archivos": []}
    for tpl_id, destino, contenido in contenidos:
        escribir(out / destino, contenido)
        manifest["archivos"].append({"plantilla": tpl_id, "archivo": destino,
                                     "fusionar": destino.startswith("fragmentos/")})

    # Lookups sintéticos de prueba (CUR-TPL-12 complementa): el de homologación desde el spec, el resto copiado.
    lk_out = out / rutas["test_resources"] / "lookups"
    lk_out.mkdir(parents=True, exist_ok=True)
    otras = []
    for c in spec["catalogos"]:
        nombre = nombre_archivo(c["archivo"])
        otras.append(f"{rutas['test_resources']}lookups/{nombre}")
        if c["tabla"] == tabla_hom:
            escribir(lk_out / nombre, csv_homologacion(spec))
        else:
            shutil.copyfile(lk_in / nombre, lk_out / nombre)
    shutil.copyfile(lk_in / MARCA_LOOKUPS, lk_out / MARCA_LOOKUPS)
    manifest["marcas_sintetico"] = [f"{rutas['test_resources']}lookups/{MARCA_LOOKUPS}"]

    exp = out / "expected"
    escribir(exp / "curated-events.json", json.dumps(curados, ensure_ascii=False, indent=2) + "\n")
    escribir(exp / "resumen.json", json.dumps(resumen, ensure_ascii=False, indent=2) + "\n")
    # AC-25 (ADR-G3, 2026-10-21): el manifest cubre todo lo generado. Los CSV de lookups y expected/ van con su
    # hash. MANIFEST.json no se lista a sí mismo.
    otras += ["expected/curated-events.json", "expected/resumen.json"]
    manifest["otros_archivos"] = [{"ruta": r, "sha256": sha256(out / r)} for r in otras]
    # AC-CUR-10 (ADR-G4): valor y origen de la zona; advertencia si difiere de la de asOfDate (main la escribe
    # en stderr).
    manifest["zona_horaria_fechas"] = {"valor": spec["parametros"]["zona_horaria_fechas"],
                                       "origen": origen_zona_fechas(spec)}
    manifest["advertencias"] = advertencias_zona(spec)
    if write_expected is not None:
        write_expected.mkdir(parents=True, exist_ok=True)
        for nombre in ("curated-events.json", "resumen.json"):
            shutil.copyfile(exp / nombre, write_expected / nombre)
    escribir(out / "MANIFEST.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    return manifest


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--spec", type=Path, default=SKILL_ROOT / "assets/examples/sisben/source-spec.sisben.yaml")
    ap.add_argument("--out", type=Path, default=SKILL_ROOT / "assets/examples/sisben/generated")
    ap.add_argument("--templates", type=Path, default=SKILL_ROOT / "assets/templates")
    ap.add_argument("--schema", type=Path, default=SKILL_ROOT / "assets/schema/source-spec.schema.yaml")
    ap.add_argument("--write-expected", type=Path, default=None)
    ap.add_argument("--solo-verificar", action="store_true", help="valida el source-spec y no genera nada")
    args = ap.parse_args(argv)
    try:
        if args.solo_verificar:
            print(verificar(args.spec, args.schema, args.templates))
            return 0
        manifest = render(args.spec, args.out, args.templates, args.schema, args.write_expected)
    except (RenderError, OSError, yaml.YAMLError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    except ImportError as exc:
        print(f"ERROR: falta una dependencia ({exc.name}); instale PyYAML y jsonschema", file=sys.stderr)
        return 1
    except Exception as exc:  # noqa: BLE001 - spec con forma inesperada
        print(f"ERROR inesperado: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1
    for a in manifest["advertencias"]:
        print(a, file=sys.stderr)  # stderr: stdout sigue terminando en OK (ADR-G4)
    print(f"OK: {len(manifest['archivos'])} artefactos en {args.out} (compilación {manifest['compilacion']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
