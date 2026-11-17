"""DEPRECADO (2026-10-20): la versión vigente es scripts/java/.../curation/Referencia.java
(ver scripts/DEPRECADOS.md). Se conserva solo como referencia.

Implementación de referencia (Python) de la lógica de curado SISBEN.

Replica, función por función, lo que hacen las plantillas Scala de Capa A
(Transformations.scala.tpl) y el núcleo puro del processor (CurationProcessor.scala.tpl).
Sirve para:
  * calcular la salida esperada del fixture sintético sin Spark ni JAR del framework (F-01);
  * probar las reglas de transformación con pytest.

No reemplaza la compilación ni la ejecución en Oxygen: si la lógica Scala cambia, este módulo
debe cambiar igual (lo verifica el checklist de entrega).
Nunca lanza excepciones por valor de dato: devuelve (valor | None, motivo | None) (CUR-RC03).
"""

from __future__ import annotations

import calendar
import json
import re
import unicodedata
from datetime import datetime, timedelta, timezone
from typing import Any

# Motivos de advertencia (sin PII: solo nombre de campo + motivo).
FORMATO_INVALIDO = "FORMATO_INVALIDO"
NO_HOMOLOGABLE = "NO_HOMOLOGABLE"
HOMOLOGACION_PENDIENTE = "HOMOLOGACION_PENDIENTE"
NO_NORMALIZABLE = "NO_NORMALIZABLE"
REGISTRO_ILEGIBLE = "REGISTRO_ILEGIBLE"
ARCHIVO_NO_RECONOCIDO = "ARCHIVO_NO_RECONOCIDO"

INT_MIN, INT_MAX = -(2**31), 2**31 - 1
LONG_MIN, LONG_MAX = -(2**63), 2**63 - 1

_JAVA_A_REGEX = {
    "dd/MM/uuuu": (r"^\d{2}/\d{2}/\d{4}$", "%d/%m/%Y"),
    "uuuu-MM-dd HH:mm:ss": (r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$", "%Y-%m-%d %H:%M:%S"),
}


def zona(nombre: str) -> timezone:
    """Zona horaria; America/Bogota cae a UTC-5 fijo si no hay base tz (sin DST desde 1993)."""
    if nombre.upper() == "UTC":
        return timezone.utc
    try:
        from zoneinfo import ZoneInfo

        return ZoneInfo(nombre)  # type: ignore[return-value]
    except Exception:  # noqa: BLE001 - sin tzdata en el equipo
        if nombre == "America/Bogota":
            return timezone(timedelta(hours=-5))
        raise


def _limpio(valor: str | None) -> str | None:
    if valor is None:
        return None
    s = valor.strip()
    return s or None


# ---------------------------------------------------------------- Capa A: acciones puras
def texto(valor: str | None):
    return _limpio(valor), None


def nombre(valor: str | None, conservar_enie: bool = False):
    s = _limpio(valor)
    if s is None:
        return None, None
    s = s.upper()
    marca = "\u0001"
    if conservar_enie:
        s = s.replace("Ñ", marca)
    s = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")
    if conservar_enie:
        s = s.replace(marca, "Ñ")
    s = re.sub(r"[^A-ZÑ ]" if conservar_enie else r"[^A-Z ]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return (s, None) if s else (None, NO_NORMALIZABLE)


def padding_izquierda(valor: str | None, longitud: int):
    s = _limpio(valor)
    if s is None:
        return None, None
    if not re.fullmatch(r"\d+", s) or len(s) > longitud:
        return None, FORMATO_INVALIDO
    return s.rjust(longitud, "0"), None


def fecha_epoch(valor: str | None, formatos: list[str], nombre_zona: str):
    s = _limpio(valor)
    if s is None:
        return None, None
    for fmt in formatos:
        patron, py = _JAVA_A_REGEX[fmt]
        if re.fullmatch(patron, s):
            try:
                dt = datetime.strptime(s, py).replace(tzinfo=zona(nombre_zona))
            except ValueError:
                return None, FORMATO_INVALIDO
            return int(dt.timestamp() * 1000), None
    return None, FORMATO_INVALIDO


def homologar(valor: str | None, tabla: dict[str, str], pendientes: set[str]):
    s = _limpio(valor)
    if s is None:
        return None, None
    s = s.upper()
    if s in pendientes:
        return None, HOMOLOGACION_PENDIENTE
    if s in tabla:
        return tabla[s], None
    return None, NO_HOMOLOGABLE


def documento(valor: str | None, regla: dict[str, Any]):
    s = _limpio(valor)
    if s is None:
        return None, None
    if regla.get("conservar_original"):
        return s, None
    s = re.sub(r"\s+", "", s)
    if regla.get("solo_digitos"):
        s = re.sub(r"\D", "", s)
    if regla.get("quitar_ceros_izquierda"):
        s = s.lstrip("0")
    if regla.get("padding"):
        s = s.rjust(int(regla["padding"]), "0")
    return (s, None) if s else (None, NO_NORMALIZABLE)


def convertir(valor: Any, tipo: str):
    """Convierte el resultado de la transformación al tipo de salida (RF-CUR-05)."""
    if valor is None:
        return None, None
    if tipo == "texto":
        return str(valor), None
    if isinstance(valor, int) and tipo in ("entero", "largo"):
        return valor, None
    s = str(valor).strip()
    if tipo in ("entero", "largo"):
        if not re.fullmatch(r"[+-]?\d+", s):
            return None, FORMATO_INVALIDO
        n = int(s)
        lo, hi = (INT_MIN, INT_MAX) if tipo == "entero" else (LONG_MIN, LONG_MAX)
        return (n, None) if lo <= n <= hi else (None, FORMATO_INVALIDO)
    if tipo == "decimal":
        if not re.fullmatch(r"[+-]?\d+(\.\d+)?", s):  # separador decimal: punto
            return None, FORMATO_INVALIDO
        return float(s), None
    if tipo == "booleano":
        if s.lower() in ("true", "false"):
            return s.lower() == "true", None
        return None, FORMATO_INVALIDO
    return None, FORMATO_INVALIDO


# ---------------------------------------------------------------- archivo
def info_archivo(file_name: str, spec: dict[str, Any]):
    """Tipo de archivo y fecha de corte (último día de AAAAMM, 00:00, epoch ms)."""
    base = re.split(r"[\\/]", file_name or "")[-1]
    m = re.search(spec["archivo"]["regex_derivacion"], base)
    if not m:
        return None, None
    prefijo = m.group(1) or ""
    tipo = spec["archivo"]["tipo_por_prefijo"].get(prefijo)
    aaaamm = m.group(2)
    anio, mes = int(aaaamm[:4]), int(aaaamm[4:])
    if not 1 <= mes <= 12:
        return tipo, None
    dia = calendar.monthrange(anio, mes)[1]
    dt = datetime(anio, mes, dia, tzinfo=zona(spec["parametros"]["zona_horaria_fechas"]))
    return tipo, int(dt.timestamp() * 1000)


def coincide_file_pattern(file_name: str, spec: dict[str, Any]) -> bool:
    """El motor resuelve el processor con una coincidencia completa (String.matches)."""
    return re.fullmatch(spec["archivo"]["file_pattern"], file_name or "") is not None


# ---------------------------------------------------------------- núcleo del processor
def _parse_registro(validated_record_json: str):
    try:
        datos = json.loads(validated_record_json)
    except (TypeError, ValueError):
        return None
    if not isinstance(datos, dict):
        return None
    out = {}
    for k, v in datos.items():
        if v is None:
            continue
        if isinstance(v, float) and v.is_integer():
            v = int(v)
        out[str(k).strip().upper()] = str(v)
    return out


def _regla_documento(tipo_raw: str | None, spec: dict[str, Any]) -> dict[str, Any]:
    reglas = spec["reglas_documento"]
    clave = (tipo_raw or "").strip().upper()
    return reglas["por_tipo"].get(clave, reglas["por_defecto"])


def curar(validated_record_json: str, file_name: str, errores: list[dict[str, str]],
          spec: dict[str, Any], homologacion: dict[str, str] | None = None) -> dict[str, Any]:
    """Devuelve el mapa curado (claves = nombres publicados) más el campo de advertencias."""
    params = spec["parametros"]
    hom = spec["homologacion_tipo_documento"]
    tabla_hom = homologacion if homologacion is not None else {str(k): str(v) for k, v in hom["tabla"].items()}
    pendientes = {str(p).upper() for p in hom["pendientes"]}
    advertencias: list[str] = []

    registro = _parse_registro(validated_record_json)
    if registro is None:
        advertencias.append(f"registro:{REGISTRO_ILEGIBLE}")
        registro = {}

    codigos = spec["codigos_error"]
    omitir_entrada: set[str] = set()
    anular_entrada: set[str] = set()
    for e in errores or []:
        campo = codigos.get(e.get("codigo") or e.get("errorCodeValue"))
        nivel = str(e.get("nivel", e.get("errorLevel", "")))
        if campo is None:
            continue
        if nivel in params["niveles_no_almacenar"]:
            omitir_entrada.add(campo)
        elif nivel in params["niveles_anular"]:
            anular_entrada.add(campo)

    tipo_archivo, fecha_corte = info_archivo(file_name, spec)
    salida: dict[str, Any] = {}
    for m in spec["campos_curados"]:
        destino, origen, t = m["destino"], m.get("origen") or "", m["transformacion"]
        if origen and origen in omitir_entrada:
            continue  # severidad 3: no se almacena (RF-CUR-10)
        crudo = None if (origen and origen in anular_entrada) else registro.get(origen) if origen else None
        tipo = t["tipo"]
        if tipo == "texto" or tipo == "original":
            valor, motivo = texto(crudo)
        elif tipo == "nombre":
            valor, motivo = nombre(crudo, params["conservar_enie"])
        elif tipo == "padding_izquierda":
            valor, motivo = padding_izquierda(crudo, int(t["longitud"]))
        elif tipo == "fecha_epoch":
            valor, motivo = fecha_epoch(crudo, params["formatos_fecha"], params["zona_horaria_fechas"])
        elif tipo == "homologacion":
            valor, motivo = homologar(crudo, tabla_hom, pendientes)
        elif tipo == "documento":
            valor, motivo = documento(crudo, _regla_documento(registro.get(spec["reglas_documento"]["campo_tipo"]), spec))
        elif tipo == "desde_archivo":
            valor = tipo_archivo if t["atributo"] == "tipo_archivo" else fecha_corte
            motivo = ARCHIVO_NO_RECONOCIDO if valor is None else None
        elif tipo == "copia_de":
            valor, motivo = salida.get(t["campo"]), None
        elif tipo == "constante":
            valor, motivo = t.get("valor"), None
        else:
            valor, motivo = None, FORMATO_INVALIDO
        if motivo is None:
            valor, motivo = convertir(valor, m["tipo_salida"])
        if motivo is not None:
            advertencias.append(f"{destino}:{motivo}")
        salida[destino] = valor
    salida[params["campo_advertencias"]] = advertencias
    return salida


def curar_evento(evento: dict[str, Any], spec: dict[str, Any]) -> dict[str, Any] | None:
    """Simula el motor + processor sobre un TaggedValidatedEvent (dict). None = no lo procesa."""
    file_name = evento["lineage"]["fileName"]
    if not coincide_file_pattern(file_name, spec):
        return None  # CUR-AP02: sin processor, el motor lo descarta sin log
    errores = [{"codigo": e["errorCodeValue"], "nivel": e["errorLevel"]} for e in evento.get("errorCodes") or []]
    return {
        "eventId": evento["eventId"],
        "lineage": evento["lineage"],
        "eventType": spec["fuente"]["output_event_type"],
        "payload": evento.get("payload", {}),
        "asOfDate": evento["asOfDate"],
        "subscriber": evento["subscriber"],
        "subscriberRelation": evento["subscriberRelation"],
        "curatedRecordJson": curar(evento["validatedRecordJson"], file_name, errores, spec),
    }
