"""Implementación de referencia (Python) de la lógica de curado.

Vigente desde la 1.7.0 del framework (2026-11-18): traduce, función por función, la clase Java
``scripts/java/.../curation/Referencia.java`` (deprecada, ver ``scripts/DEPRECADOS.md``) y agrega la
transformación ``concatenacion``.

Replica lo que hacen las plantillas Scala de Capa A (``Transformations.scala.tpl``) y el núcleo puro del
processor (``CurationProcessor.scala.tpl``). Sirve para:
  * calcular la salida esperada del fixture sintético sin Spark ni JAR del framework (F-01);
  * probar las reglas de transformación con pytest.

No reemplaza la compilación ni la ejecución en Oxygen, que hace el ingeniero en el fork: si la lógica Scala
cambia, este módulo debe cambiar igual (lo verifica el checklist de entrega).
Nunca lanza excepciones por valor de dato: devuelve (valor | None, motivo | None) (CUR-RC03).
"""

from __future__ import annotations

import calendar
import json
import re
import unicodedata
from datetime import datetime, timedelta, timezone, tzinfo
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

# Formatos de fecha (patrón Java del source-spec → regex). \d reconoce dígitos Unicode, como en Java.
_FORMATOS = {
    "dd/MM/uuuu": re.compile(r"^(\d{2})/(\d{2})/(\d{4})$"),
    "uuuu-MM-dd HH:mm:ss": re.compile(r"^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$"),
    # F-FW-10: ISO solo fecha (00:00 en la zona).
    "uuuu-MM-dd": re.compile(r"^(\d{4})-(\d{2})-(\d{2})$"),
}
_UTC_ALIAS = {"UTC", "Z", "GMT", "UT", "UCT", "ETC/UTC", "ETC/GMT", "ETC/UCT", "ETC/UNIVERSAL", "ETC/ZULU",
              "UNIVERSAL", "ZULU", "GMT0", "ETC/GMT0", "ETC/GMT+0", "ETC/GMT-0", "GREENWICH", "ETC/GREENWICH"}


def zona(nombre: str) -> tzinfo:
    """Zona horaria; "UTC" sin distinguir mayúsculas. America/Bogota cae a UTC-5 fijo sin base tz."""
    if nombre.upper() == "UTC":
        return timezone.utc
    try:
        from zoneinfo import ZoneInfo

        return ZoneInfo(nombre)
    except Exception:  # noqa: BLE001 - sin tzdata en el equipo, o nombre inválido
        if nombre == "America/Bogota":
            return timezone(timedelta(hours=-5))
        raise ValueError(f"zona inválida: {nombre}") from None


def zona_normalizada(nombre: str) -> str:
    """Equivalente práctico de ZoneId.normalized(): los alias de UTC se comparan como una sola zona."""
    return "UTC" if nombre.upper() in _UTC_ALIAS else nombre


def _limpio(valor: Any) -> str | None:
    if valor is None:
        return None
    s = str(valor).strip()
    return s or None


def _epoch_ms(dt: datetime) -> int:
    """Epoch en ms sin pasar por float (exacto para cualquier año)."""
    delta = dt - datetime(1970, 1, 1, tzinfo=timezone.utc)
    return (delta.days * 86_400 + delta.seconds) * 1000 + delta.microseconds // 1000


def texto_scala(valor: Any) -> str:
    """toString de Scala para los valores curados (booleanos en minúsculas)."""
    if isinstance(valor, bool):
        return "true" if valor else "false"
    return str(valor)


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
        patron = _FORMATOS.get(fmt)
        if patron is None:
            raise ValueError(f"formato de fecha no soportado: {fmt}")
        m = patron.match(s)
        if m:
            n = [int(g) for g in m.groups()] + [0, 0, 0]
            dia_primero = fmt == "dd/MM/uuuu"
            anio, mes, dia = (n[2], n[1], n[0]) if dia_primero else (n[0], n[1], n[2])
            try:
                dt = datetime(anio, mes, dia, n[3], n[4], n[5], tzinfo=zona(nombre_zona))
            except ValueError:
                return None, FORMATO_INVALIDO
            return _epoch_ms(dt), None
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
    # F-FW-14: Pinning compara texto exacto (steering 11 §3); las letras del número van en mayúsculas.
    s = re.sub(r"\s+", "", s).upper()
    if regla.get("solo_digitos"):
        s = re.sub(r"\D", "", s)
    if regla.get("quitar_ceros_izquierda"):
        s = s.lstrip("0")
    if regla.get("padding"):
        s = s.rjust(int(regla["padding"]), "0")
    return (s, None) if s else (None, NO_NORMALIZABLE)


def concatenar(valores: list[Any], separador: str):
    """Une los valores curados no vacíos con el separador; si todos están vacíos, nulo sin advertencia.

    Nuevo en la 1.7.0 (RG-013, standardizedIdName de SISBEN). Los componentes son campos curados anteriores,
    ya estandarizados; un componente omitido o nulo simplemente no aparece.
    """
    partes = [p for p in (_limpio(texto_scala(v)) if v is not None else None for v in valores) if p]
    return (separador.join(partes), None) if partes else (None, None)


def subscriber_relation(curado: dict[str, Any], spec: dict[str, Any], copiado: str | None) -> str | None:
    """F-FW-11 (D14): subscriberRelation según extension_subscriber_relation.

    Los campos curados indicados, unidos por el separador. Sin el bloque en la spec, o si falta algún
    componente (nulo o vacío), devuelve ``copiado``, el subscriberRelation del evento aceptado. Nunca lanza.
    """
    ext = spec.get("extension_subscriber_relation")
    if ext is None:
        return copiado
    partes = []
    for campo in ext.get("campos") or []:
        valor = curado.get(campo)
        s = None if valor is None else _limpio(str(valor))
        if s is None:
            return copiado
        partes.append(s)
    return str(ext["separador"]).join(partes)


def convertir(valor: Any, tipo: str):
    """Convierte el resultado de la transformación al tipo de salida (RF-CUR-05)."""
    if valor is None:
        return None, None
    if tipo == "texto":
        return str(valor), None
    entero = tipo in ("entero", "largo")
    if isinstance(valor, int) and entero:  # bool es un int: pasa tal cual
        return valor, None
    s = str(valor).strip()
    if entero:
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
def info_archivo(file_name: str | None, spec: dict[str, Any]):
    """Tipo de archivo y fecha de corte (último día de AAAAMM, 00:00, epoch ms); None si no aplica."""
    base = re.split(r"[\\/]", file_name or "")[-1]
    archivo = spec["archivo"]
    m = re.search(archivo["regex_derivacion"], base)
    if not m:
        return None, None
    prefijo = m.group(1) or ""
    tipo = archivo["tipo_por_prefijo"].get(prefijo)
    aaaamm = m.group(2)
    anio, mes = int(aaaamm[:4]), int(aaaamm[4:])
    if not 1 <= mes <= 12 or anio < 1:
        return tipo, None
    dia = calendar.monthrange(anio, mes)[1]
    dt = datetime(anio, mes, dia, tzinfo=zona(spec["parametros"]["zona_horaria_fechas"]))
    return tipo, _epoch_ms(dt)


def coincide_file_pattern(file_name: str | None, spec: dict[str, Any]) -> bool:
    """El motor resuelve el processor con una coincidencia completa (String.matches)."""
    return re.fullmatch(spec["archivo"]["file_pattern"], file_name or "") is not None


# ---------------------------------------------------------------- núcleo del processor
def _parse_registro(validated_record_json: str | None) -> dict[str, str] | None:
    """JSON del registro validado como mapa CLAVE -> texto; None si no es un objeto JSON legible."""
    if validated_record_json is None:
        return None
    try:
        datos = json.loads(validated_record_json)
    except (TypeError, ValueError, RecursionError):
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


def curar(validated_record_json: str | None, file_name: str | None, errores: list[dict[str, Any]] | None,
          spec: dict[str, Any]) -> dict[str, Any]:
    """Mapa curado (claves = nombres publicados) más el campo de advertencias."""
    params = spec["parametros"]
    hom = spec["homologacion_tipo_documento"]
    tabla_hom = {str(k): str(v) for k, v in hom["tabla"].items()}
    pendientes = {str(p).upper() for p in hom.get("pendientes") or []}
    advertencias: list[str] = []

    registro = _parse_registro(validated_record_json)
    if registro is None:
        advertencias.append(f"registro:{REGISTRO_ILEGIBLE}")
        registro = {}

    codigos = spec["codigos_error"]
    omitir_entrada: set[str] = set()
    anular_entrada: set[str] = set()
    for e in errores or []:
        codigo = e.get("codigo") or e.get("errorCodeValue")
        campo = codigos.get(codigo)
        nivel = str(e["nivel"] if "nivel" in e else e["errorLevel"] if "errorLevel" in e else "")
        if campo is None:
            continue
        if nivel in (params.get("niveles_no_almacenar") or []):
            omitir_entrada.add(campo)
        elif nivel in (params.get("niveles_anular") or []):
            anular_entrada.add(campo)

    tipo_archivo, fecha_corte = info_archivo(file_name, spec)
    salida: dict[str, Any] = {}
    for m in spec["campos_curados"]:
        destino, origen, t = m["destino"], m.get("origen") or "", m["transformacion"]
        if origen and origen in omitir_entrada:
            continue  # nivel en niveles_no_almacenar: la clave no se publica (RF-CUR-10)
        crudo = None if (not origen or origen in anular_entrada) else registro.get(origen)
        tipo = t["tipo"]
        if tipo in ("texto", "original"):
            valor, motivo = texto(crudo)
        elif tipo == "nombre":
            valor, motivo = nombre(crudo, bool(params.get("conservar_enie")))
        elif tipo == "padding_izquierda":
            valor, motivo = padding_izquierda(crudo, int(t["longitud"]))
        elif tipo == "fecha_epoch":
            valor, motivo = fecha_epoch(crudo, params["formatos_fecha"], params["zona_horaria_fechas"])
        elif tipo == "homologacion":
            valor, motivo = homologar(crudo, tabla_hom, pendientes)
        elif tipo == "documento":
            regla = _regla_documento(registro.get(spec["reglas_documento"]["campo_tipo"]), spec)
            valor, motivo = documento(crudo, regla)
        elif tipo == "desde_archivo":
            valor = tipo_archivo if t["atributo"] == "tipo_archivo" else fecha_corte
            motivo = ARCHIVO_NO_RECONOCIDO if valor is None else None
        elif tipo == "copia_de":
            valor, motivo = salida.get(t["campo"]), None
        elif tipo == "concatenacion":
            valor, motivo = concatenar([salida.get(c) for c in t["campos"]], t.get("separador", " "))
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
    """Simula el motor + processor sobre un TaggedValidatedEvent. None = el motor no lo procesa."""
    lineage = evento["lineage"]
    file_name = lineage.get("fileName")
    if not coincide_file_pattern(file_name, spec):
        return None  # CUR-AP02: sin processor, el motor lo descarta sin log
    errores = [{"codigo": e.get("errorCodeValue"), "nivel": e.get("errorLevel")} for e in evento.get("errorCodes") or []]
    curado = curar(evento.get("validatedRecordJson"), file_name, errores, spec)
    return {
        "eventId": evento.get("eventId"),
        "lineage": lineage,
        "eventType": spec["fuente"]["output_event_type"],
        "payload": evento.get("payload", {}),
        "asOfDate": evento.get("asOfDate"),
        "subscriber": evento.get("subscriber"),
        "subscriberRelation": subscriber_relation(curado, spec, evento.get("subscriberRelation")),
        "curatedRecordJson": curado,
    }
