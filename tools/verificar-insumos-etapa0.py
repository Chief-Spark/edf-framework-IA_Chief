#!/usr/bin/env python3
"""Compuerta de entrada de la etapa 0: la HU y la spec de Confluence son obligatorias.

La HU y la spec de Confluence son obligatorias. Si falta alguna, el analisis no
arranca: se dice cual falta y se detiene.

Busca los dos insumos de una ingesta en `expedientes/<id>/fuentes/`:
  - un archivo cuyo nombre empiece por `HU`
  - un archivo cuyo nombre empiece por `spec`
(sin distinguir mayusculas, cualquier extension, no vacios).

Si no estan ahi, acepta las rutas que el usuario haya indicado y que el agente
registro en `expedientes/<id>/fuentes/rutas-fuentes.json`:
    {"hu": "<ruta de la HU>", "spec": "<ruta de la spec de Confluence>"}

Uso:
    python tools/verificar-insumos-etapa0.py --id sisben        # verificacion manual
    python tools/verificar-insumos-etapa0.py --evento prompt    # hook PromptSubmit (lee USER_PROMPT)
    python tools/verificar-insumos-etapa0.py --evento pretool   # hook PreToolUse (lee JSON por STDIN)

Salida: 0 si se puede arrancar o continuar; 2 si falta algun insumo (bloquea el
prompt o la herramienta; el mensaje por stderr indica cuales faltan).
En Linux/macOS usa `python3`.
"""
import argparse
import json
import os
import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
BLOQUEO = 2

INSUMOS = (("HU", "hu"), ("spec", "spec"))  # (prefijo del archivo, clave del manifiesto)
MANIFIESTO = "rutas-fuentes.json"

# Claves donde las herramientas de archivos suelen poner la ruta destino.
CLAVES_RUTA = {"path", "file_path", "filepath", "filename", "file", "target", "targetfile",
               "target_file", "destination", "uri"}

PALABRAS_NO_ID = {
    "la", "el", "los", "las", "de", "del", "un", "una", "nueva", "nuevo", "que", "en", "con",
    "y", "o", "a", "para", "por", "actual", "anterior", "sin", "mi", "esa", "ese", "esta",
    "este", "siguiente", "llamada", "llamado", "id", "nombre", "al", "se", "lo", "su",
}

RE_VERBO = re.compile(
    r"\b(inici\w*|contin[uú]\w*|analiz\w*|retom\w*|reanud\w*|arranc\w*|empez\w*|comenz\w*)\b|etapa\s*0",
    re.IGNORECASE,
)
RE_OBJETO = re.compile(r"\b(ingesta|expediente)s?\b", re.IGNORECASE)
RE_INGESTA_ID = re.compile(r"\bingesta\s+([^\s,;:()\[\]]+)", re.IGNORECASE)
RE_RUTA_EXPEDIENTE = re.compile(r"(?:^|[\\/])expedientes[\\/]+([^\\/]+)(?:[\\/]+(.*))?$", re.IGNORECASE)


def _valido(ruta):
    """Archivo existente y no vacio."""
    try:
        return ruta.is_file() and ruta.stat().st_size > 0
    except OSError:
        return False


def _resolver(raiz, texto):
    p = pathlib.Path(str(texto))
    return p if p.is_absolute() else raiz / p


def faltantes(raiz, ingesta, rutas_extra=()):
    """Devuelve (lista de insumos que faltan, lista de detalles del manifiesto)."""
    fuentes = raiz / "expedientes" / ingesta / "fuentes"
    detalles = []

    manifiesto = {}
    archivo_manifiesto = fuentes / MANIFIESTO
    if archivo_manifiesto.is_file():
        try:
            # utf-8-sig: PowerShell 5 escribe JSON con BOM
            datos = json.loads(archivo_manifiesto.read_text(encoding="utf-8-sig"))
            if isinstance(datos, dict):
                manifiesto = datos
            else:
                detalles.append(f"{MANIFIESTO} no es un objeto JSON.")
        except (OSError, ValueError):
            detalles.append(f"{MANIFIESTO} no es un JSON legible.")

    faltan = []
    for prefijo, clave in INSUMOS:
        encontrado = False
        if fuentes.is_dir():
            for f in fuentes.iterdir():
                if f.name.lower().startswith(prefijo.lower()) and _valido(f):
                    encontrado = True
                    break
        if not encontrado and manifiesto.get(clave):
            if _valido(_resolver(raiz, manifiesto[clave])):
                encontrado = True
            else:
                detalles.append(
                    f"{MANIFIESTO}: la ruta de '{clave}' no existe o esta vacia: {manifiesto[clave]}"
                )
        if not encontrado:
            for r in rutas_extra:
                if r.name.lower().startswith(prefijo.lower()) and _valido(r):
                    encontrado = True
                    break
        if not encontrado:
            faltan.append(prefijo)
    return faltan, detalles


def mensaje(raiz, ingesta, faltan, detalles):
    existe = (raiz / "expedientes" / ingesta / "estado-ingesta.json").is_file()
    accion = "continua" if existe else "arranca"
    lineas = [
        f"La ingesta `{ingesta}` no {accion}.",
        "La HU y la spec de Confluence son obligatorias. Si falta alguna, el analisis no arranca: "
        "se dice cual falta y se detiene.",
        f"Falta: {', '.join(faltan)}.",
        f"Colocalas en `expedientes/{ingesta}/fuentes/` (un archivo que empiece por `HU` y otro por `spec`), "
        f"o indica la ruta donde estan para registrarla en `expedientes/{ingesta}/fuentes/{MANIFIESTO}`.",
    ]
    lineas.extend(detalles)
    return "\n".join(lineas)


def verificar(raiz, ingesta, rutas_extra=()):
    """Devuelve (codigo, mensaje). Codigo 0 si estan los dos insumos."""
    faltan, detalles = faltantes(raiz, ingesta, rutas_extra)
    if not faltan:
        return 0, ""
    return BLOQUEO, mensaje(raiz, ingesta, faltan, detalles)


# --- Evento PromptSubmit -------------------------------------------------

def _ids_existentes(raiz):
    base = raiz / "expedientes"
    if not base.is_dir():
        return []
    return sorted((d.name for d in base.iterdir() if d.is_dir()), key=len, reverse=True)


def resolver_id(raiz, prompt):
    """Id de la ingesta mencionada en el prompt, o None."""
    for nombre in _ids_existentes(raiz):
        if re.search(r"(?<![\w\-])" + re.escape(nombre) + r"(?![\w\-])", prompt, re.IGNORECASE):
            return nombre
    for m in RE_INGESTA_ID.finditer(prompt):
        candidato = m.group(1).strip("`'\".¿?¡!")
        if candidato and candidato.lower() not in PALABRAS_NO_ID:
            for nombre in _ids_existentes(raiz):
                if nombre.lower() == candidato.lower():
                    return nombre
            return candidato.lower()
    return None


def rutas_en_prompt(raiz, prompt):
    """Archivos existentes citados en el prompt (el usuario indica donde estan HU y spec)."""
    rutas = []
    for token in re.findall(r'"([^"]+)"|\'([^\']+)\'|`([^`]+)`|(\S+)', prompt):
        texto = next(t for t in token if t)
        texto = texto.strip(",;)(\u00bf?\u00a1!").rstrip(".")
        if not texto or len(texto) > 400:
            continue
        p = _resolver(raiz, texto)
        try:
            if p.is_file():
                rutas.append(p)
        except OSError:
            continue
    return rutas


def evento_prompt(raiz, prompt):
    if not (RE_VERBO.search(prompt) and (RE_OBJETO.search(prompt) or resolver_id(raiz, prompt))):
        return 0, ""
    ingesta = resolver_id(raiz, prompt)
    if not ingesta:
        return 0, (
            "Compuerta etapa 0: se pidio iniciar o continuar una ingesta sin indicar su id. "
            "Antes de arrancar, pregunta el id y verifica que `expedientes/<id>/fuentes/` tenga "
            "la HU y la spec de Confluence; si falta alguna, dilo y detente."
        )
    return verificar(raiz, ingesta, rutas_en_prompt(raiz, prompt))


# --- Evento PreToolUse ---------------------------------------------------

def _cadenas(valor, clave=None):
    """Recorre el JSON y entrega (clave, cadena)."""
    if isinstance(valor, str):
        yield clave, valor
    elif isinstance(valor, dict):
        for k, v in valor.items():
            yield from _cadenas(v, str(k))
    elif isinstance(valor, list):
        for v in valor:
            yield from _cadenas(v, clave)


def rutas_destino(evento):
    """Rutas destino de la herramienta. Primero las claves conocidas; si no hay, cadenas cortas de una linea."""
    todas = list(_cadenas(evento))
    conocidas = [s for k, s in todas if k and k.lower() in CLAVES_RUTA]
    if conocidas:
        return conocidas
    return [s for _, s in todas if "\n" not in s and len(s) < 400]


def evento_pretool(raiz, evento):
    ids = []
    for ruta in rutas_destino(evento):
        m = RE_RUTA_EXPEDIENTE.search(ruta.strip())
        if not m:
            continue
        ingesta, resto = m.group(1), (m.group(2) or "")
        primer = re.split(r"[\\/]", resto)[0].lower() if resto else ""
        if primer == "fuentes":
            continue  # exento: ahi se colocan las fuentes y se registra el manifiesto
        if ingesta not in ids:
            ids.append(ingesta)
    for ingesta in ids:
        codigo, texto = verificar(raiz, ingesta)
        if codigo:
            return codigo, texto
    return 0, ""


# --- CLI -----------------------------------------------------------------

def _leer_stdin():
    if sys.stdin is None or sys.stdin.isatty():
        return ""
    try:
        return sys.stdin.read()
    except OSError:
        return ""


def main(argv=None):
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8")
        except (AttributeError, OSError):
            pass

    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--id", help="id de la ingesta (expedientes/<id>/)")
    ap.add_argument("--evento", choices=("prompt", "pretool"), help="modo hook")
    ap.add_argument("--raiz", default=str(RAIZ), help="raiz del workspace (por defecto, la del repositorio)")
    args = ap.parse_args(argv)
    raiz = pathlib.Path(args.raiz)

    if args.evento == "prompt":
        prompt = os.environ.get("USER_PROMPT", "")
        if not prompt:
            crudo = _leer_stdin()
            try:
                datos = json.loads(crudo) if crudo.strip() else {}
                prompt = datos.get("prompt") or datos.get("user_prompt") or ""
            except (ValueError, AttributeError):
                prompt = ""
        codigo, texto = evento_prompt(raiz, prompt)
    elif args.evento == "pretool":
        crudo = _leer_stdin()
        try:
            evento = json.loads(crudo) if crudo.strip() else {}
        except ValueError:
            print("Compuerta etapa 0: el JSON del evento no es legible; no se pudo verificar.", file=sys.stderr)
            return 0
        codigo, texto = evento_pretool(raiz, evento)
    elif args.id:
        codigo, texto = verificar(raiz, args.id)
        if not codigo:
            print(f"OK: la ingesta `{args.id}` tiene la HU y la spec de Confluence.")
    else:
        ap.error("indica --id <ingesta> o --evento prompt|pretool")

    if codigo:
        print(texto, file=sys.stderr)
    elif texto:
        print(texto)
    return codigo


if __name__ == "__main__":
    sys.exit(main())
