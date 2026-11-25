#!/usr/bin/env python3
"""Bloque de Pinning en modo Colombia: generador mínimo y verificador mecánico (HF-66, HF-67, HF-81, HF-82).

En modo Colombia (bureau.colombia_innersource) el bloque HOCON de la ingesta NO tiene claves: es la lista VERIFICAR
de la sección «Modo Colombia» de assets/pinning-application-block.conf.template contra el conf compartido. Este script
lo hace reproducible: dos generaciones con el mismo insumo dan el mismo archivo, byte a byte.

Uso (desde la raíz del framework):
    python3 .kiro/skills/edf-pinning/scripts/render_bloque_colombia.py --insumo <yaml-skill-pinning.yaml> \
        [--contrato <contrato-curation-pinning.md>] [--out <bloque.conf>]
    python3 .kiro/skills/edf-pinning/scripts/render_bloque_colombia.py --verificar <bloque.conf> [--dir-propuesta <dir>] \
        [--insumo <yaml-skill-pinning.yaml>]

Generar:
  1. valida el insumo con tools/verificar-insumo-pinning.py (sin errores; con --contrato cruza las claves) y exige
     modo_colombia.aplica = true;
  2. toma la sección «Modo Colombia» de la plantilla tal cual y rellena sus huecos con datos del insumo:
     INGESTA_NOMBRE (documento.ingesta, en mayúsculas), IDENTIFYING_ATTRIBUTES_COMPARTIDO,
     IDENTITY_MATCHER_COLOMBIA, CLAVES_EVENTO_CURADO (obligatorias y opcional) y REUSE_PIN_VALOR_FUENTE
     (valor_en_fuente de la entrada reusePin: un dato, nunca un booleano para escribir);
     copia además, literal y en el orden del insumo (HF-81, references/03 §3e regla 2 de edf-orquestador), el
     `estado` y la `fuente` de las entradas identifyingAttributesFile, identityMatcher y reusePin de
     claves_configuracion_pinning.bloque_oxygen_stage_pinning (con sus [PM] y RG-nnn), el hallazgo_N4_escalado de
     alcance.precondiciones_bloqueantes y el estado de las pendientes_de_configuracion que nombran reusePin
     (disparador de escalamiento de reusePin), y las «Referencias de la ingesta»: id y estado de
     precondiciones_bloqueantes y pendientes_del_mapeo, y clave y estado del resto de pendientes_de_configuracion. La fuente del matcher es la del insumo, no una fila de steering (HF-82);
  3. antepone una cabecera de procedencia (insumo, versión y sha256) y verifica el resultado antes de escribirlo.
  Sin --out escribe en la salida estándar.

Verificar (equivalente en Python de evals/verificar-artefacto.ps1 para modo Colombia, EVAL-PIN-12..14):
  - 0 líneas activas (todo comentario o en blanco): ninguna clave compartida, tampoco `reusePin = …` (EVAL-PIN-12, -14);
  - una línea `VERIFICAR` por cada entrada de VERIFICAR_COLOMBIA (rutas HOCON que lee el motor y los nombres [PM]);
  - ISIdentityMatcher, RIIdentityMatcher e identifying-attributes.json solo en comentarios (EVAL-PIN-13): sin líneas
    activas no pueden ser un valor;
  - ningún {{hueco}};
  - con --dir-propuesta: no hay *IdentityMatcher.scala ni identifying-attributes.json (EVAL-PIN-14);
  - los VERIFICAR de identifyingAttributesFile, identityMatcher y reusePin llevan su «Estado en el insumo» (HF-81);
  - con --insumo (HF-81, HF-82): el sha256 de la cabecera es el del insumo; cada VERIFICAR de esas tres claves lleva
    los [PM], [A:*] y RG-nnn que el insumo trae en su estado (reusePin, también los de sus pendientes de configuración
    y del hallazgo_N4_escalado); las
    «Referencias de la ingesta» llevan cada id y los marcadores de su estado; el VERIFICAR del matcher no cita
    «steering 11 §6 #7» si el insumo no la cita.
  groupSSR se evalúa fuera de comentarios (HF-67): el comentario obligatorio del colapso («groupSSR = true (:676)»)
  no es un valor.

Salida: 0 OK; 1 con hallazgos (insumo o bloque); 2 si falta PyYAML, un archivo o la plantilla.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import re
import sys
import textwrap
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
RAIZ = SKILL.parent.parent.parent
PLANTILLA = SKILL / "assets" / "pinning-application-block.conf.template"
VERIFICADOR_INSUMO = RAIZ / "tools" / "verificar-insumo-pinning.py"
INICIO = "# ── Modo Colombia"
FIN = "# Fin de la sección «Modo Colombia»."
HUECO = re.compile(r"\{\{([A-Z_0-9]+)\}\}")

# Lo que debe nombrar la lista VERIFICAR en modo Colombia (HF-65): la ruta HOCON que lee el motor o, si la copia solo
# trae la constante de oxygen-global, el nombre [PM].
VERIFICAR_COLOMBIA = (
    "oxygen.stage.pinning.paths.identifyingAttributesFile",
    "oxygen.stage.pinning.identityMatcher",
    "oxygen.stage.pinning.reusePin",
    "oxygen.stage.pinning.paths.domainDictionaryFile",
    "oxygen.stage.pinning.nearNeighborBlockSize",
    "oxygen.stage.pinning.pinningVleRange",
    "oxygen.stage.pinning.groupSSR",
    "oxygen.stage.pinning.paths.pinnedReferenceDir",
    "oxygen.stage.curation.paths.curatedDir",
    "integración estática con HBase",
    "clusterSplitter",
    "INPUT_REF_DIR",
    "*-common-application.conf",
)
CLAVES_COMPARTIDAS = ("identifyingAttributesFile", "identityMatcher", "reusePin", "domainDictionaryFile",
                      "clusterSplitter", "nearNeighborBlockSize", "pinningVleRange", "groupSSR")
REFERENCIA = re.compile(r"\b(ISIdentityMatcher|RIIdentityMatcher)\b|(?<![-\w])identifying-attributes\.json")

# HF-81: entradas del insumo cuyo estado (con sus [PM] y RG-nnn) se copia en su VERIFICAR, y hueco de párrafo.
ENTRADAS_CON_ESTADO = (
    ("identifyingattributesfile", "oxygen.stage.pinning.paths.identifyingAttributesFile", "ESTADO_IDENTIFYING_ATTRIBUTES"),
    ("identitymatcher", "oxygen.stage.pinning.identityMatcher", "ESTADO_IDENTITY_MATCHER"),
    ("reusepin", "oxygen.stage.pinning.reusePin", "ESTADO_REUSE_PIN"),
)
HUECOS_PARRAFO = tuple(h for _, _, h in ENTRADAS_CON_ESTADO) + ("REFERENCIAS_INGESTA",)
ANCHO = 78
# Marcadores que §3e (regla 2) exige llevar del insumo al artefacto: [PM], [A:<ID>] y el ID del riesgo.
MARCADOR = re.compile(r"\[PM\]|\[A:[^\]]+\]|\bRG-\d+\b")
ESTADO_EN_INSUMO = "Estado en el insumo:"
TITULO_REFERENCIAS = "Referencias de la ingesta"
CITA_STEERING_MATCHER = "steering 11 §6 #7"


class ErrorUso(Exception):
    """Falta un archivo, la plantilla o PyYAML (salida 2)."""


def es_comentario(linea: str) -> bool:
    return linea.strip().startswith(("#", "//"))


def activas(texto: str) -> list[tuple[int, str]]:
    return [(n, l) for n, l in enumerate(texto.splitlines(), 1) if l.strip() and not es_comentario(l)]


def seccion_colombia(plantilla: str) -> str:
    if INICIO not in plantilla or FIN not in plantilla:
        raise ErrorUso(f"la plantilla no trae la sección «Modo Colombia» completa: {PLANTILLA}")
    return plantilla[plantilla.index(INICIO):plantilla.index(FIN) + len(FIN)] + "\n"


def _modulo_insumo():
    if not VERIFICADOR_INSUMO.is_file():
        raise ErrorUso(f"falta {VERIFICADOR_INSUMO}")
    spec = importlib.util.spec_from_file_location("verificar_insumo_pinning", VERIFICADOR_INSUMO)
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


def _texto(valor, campo: str) -> str:
    s = " ".join(str(valor or "").split())
    if not s or "{{" in s:
        raise ValueError(f"insumo: {campo} vacío o con huecos")
    return s


def valores(insumo: dict) -> dict[str, str]:
    """Huecos de la sección «Modo Colombia», tomados del insumo. ValueError si falta un dato."""
    mc = insumo.get("modo_colombia") or {}
    if not (isinstance(mc, dict) and mc.get("aplica") is True):
        raise ValueError("insumo: modo_colombia.aplica no es true; este generador solo cubre el modo Colombia")
    claves = [str(c) for c in mc.get("claves_obligatorias_en_evento_curado") or []]
    if not claves:
        raise ValueError("insumo: modo_colombia.claves_obligatorias_en_evento_curado vacío")
    claves_txt = ", ".join(claves)
    if isinstance(mc.get("clave_opcional"), str) and mc["clave_opcional"].strip():
        claves_txt += f" y, opcional, {mc['clave_opcional'].strip()}"
    bloque = (insumo.get("claves_configuracion_pinning") or {}).get("bloque_oxygen_stage_pinning") or []
    reuse = entrada(insumo, "reusepin")
    if reuse is None or isinstance(reuse.get("valor_en_fuente"), bool):
        raise ValueError("insumo: reusePin sin valor_en_fuente de texto (es un dato de la fuente, HF-57)")
    parrafos: dict[str, list[str]] = {}
    for corta, ruta, hueco in ENTRADAS_CON_ESTADO:
        e = entrada(insumo, corta)
        if e is None:
            raise ValueError(f"insumo: falta la entrada {ruta} en claves_configuracion_pinning.bloque_oxygen_stage_pinning"
                             " (su estado va al VERIFICAR, HF-81)")
        lineas = [f"{ESTADO_EN_INSUMO} {_texto(e.get('estado'), ruta + '.estado')}"]
        if _plano(e.get("fuente")):
            lineas.append(f"Fuente en el insumo: {_plano(e.get('fuente'))}")
        parrafos[hueco] = lineas
    parrafos["ESTADO_REUSE_PIN"] += [_referencia(f"Pendiente en el insumo ({_plano(e.get('clave'))})",
                                                 _plano(e.get("estado"))) for e in pendientes_config(insumo, True)]
    parrafos["ESTADO_REUSE_PIN"] += [f"Escalamiento N4 en el insumo ({pid}): {texto}" for pid, texto in escalamientos(insumo)]
    parrafos["REFERENCIAS_INGESTA"] = referencias(insumo) or ["el insumo no trae precondiciones ni pendientes"]
    return {
        "INGESTA_NOMBRE": _texto((insumo.get("documento") or {}).get("ingesta"), "documento.ingesta").upper(),
        "IDENTIFYING_ATTRIBUTES_COMPARTIDO": _texto(mc.get("identifying_attributes_compartido"),
                                                    "modo_colombia.identifying_attributes_compartido"),
        "IDENTITY_MATCHER_COLOMBIA": _texto(mc.get("identity_matcher"), "modo_colombia.identity_matcher"),
        "CLAVES_EVENTO_CURADO": claves_txt,
        "REUSE_PIN_VALOR_FUENTE": _texto(reuse.get("valor_en_fuente"), "reusePin.valor_en_fuente"),
        **parrafos,
    }


def _plano(valor) -> str:
    return " ".join(str(valor or "").split())


def entrada(insumo: dict, corta: str) -> dict | None:
    """Entrada de claves_configuracion_pinning.bloque_oxygen_stage_pinning por el nombre corto de su clave."""
    bloque = (insumo.get("claves_configuracion_pinning") or {}).get("bloque_oxygen_stage_pinning") or []
    return next((e for e in bloque if isinstance(e, dict)
                 and str(e.get("clave", "")).rsplit(".", 1)[-1].lower() == corta), None)


def _lista(valor) -> list[dict]:
    return [e for e in (valor or []) if isinstance(e, dict)]


def precondiciones(insumo: dict) -> list[dict]:
    return _lista((insumo.get("alcance") or {}).get("precondiciones_bloqueantes"))


def pendientes_config(insumo: dict, de_reuse_pin: bool) -> list[dict]:
    """pendientes_de_configuracion que nombran reusePin (van a su VERIFICAR: disparador del escalamiento) o el resto."""
    todos = _lista((insumo.get("claves_configuracion_pinning") or {}).get("pendientes_de_configuracion"))
    return [e for e in todos if ("reusepin" in _plano(e.get("clave")).lower()) == de_reuse_pin]


def escalamientos(insumo: dict) -> list[tuple[str, str]]:
    """(id, texto) de cada precondición con hallazgo_N4_escalado: el disparador de escalamiento (HF-81)."""
    return [(_plano(p.get("id")) or "sin id", _plano(p.get("hallazgo_N4_escalado")))
            for p in precondiciones(insumo) if _plano(p.get("hallazgo_N4_escalado"))]


def referencias(insumo: dict) -> list[str]:
    """Líneas «Referencias de la ingesta», en el orden del insumo: id y estado (o descripción si no trae estado)."""
    out = []
    for etiqueta, entradas in (
            ("precondición", precondiciones(insumo)),
            ("pendiente del mapeo", _lista((insumo.get("mapeo_identidad") or {}).get("pendientes_del_mapeo")))):
        for e in entradas:
            texto = _plano(e.get("estado")) or _plano(e.get("descripcion"))
            out.append(_referencia(f"{_plano(e.get('id')) or 'sin id'} ({etiqueta})", texto))
    for e in pendientes_config(insumo, False):
        out.append(_referencia(f"Pendiente de configuración ({_plano(e.get('clave')) or 'sin clave'})",
                               _plano(e.get("estado"))))
    return out


def _referencia(clave: str, texto: str) -> str:
    return f"{clave}: {texto}" if texto else clave


def _parrafo(prefijo: str, lineas: list[str]) -> str:
    """Envuelve cada línea a ANCHO con el prefijo de comentario del hueco; sin cortar [PM] ni RG-nnn."""
    sangria = prefijo + "  "
    return "\n".join(textwrap.fill(l, ANCHO, initial_indent=prefijo, subsequent_indent=sangria,
                                    break_long_words=False, break_on_hyphens=False) for l in lineas)


def rellenar(seccion: str, datos: dict) -> str:
    faltan = sorted(set(HUECO.findall(seccion)) - set(datos))
    if faltan:
        raise ValueError(f"plantilla: huecos sin dato en el insumo: {faltan}")
    salida = []
    for linea in seccion.split("\n"):
        m = re.fullmatch(r"(#\s*)\{\{([A-Z_0-9]+)\}\}", linea)
        if m and m.group(2) in HUECOS_PARRAFO:
            valor = datos[m.group(2)]
            if valor:
                salida.append(_parrafo(m.group(1), valor))
            continue
        if any(f"{{{{{h}}}}}" in linea for h in HUECOS_PARRAFO):
            raise ValueError(f"plantilla: un hueco de párrafo va solo en su línea de comentario: {linea!r}")
        salida.append(HUECO.sub(lambda mm: datos[mm.group(1)], linea))
    return "\n".join(salida)


def _normal(texto: str) -> str:
    """Texto de comentarios sin «#» ni sangría, en una línea: los marcadores no dependen del ajuste de línea."""
    return " ".join(l.strip().lstrip("#/").strip() for l in texto.splitlines())


def marcadores(texto) -> list[str]:
    vistos: list[str] = []
    for m in MARCADOR.findall(_plano(texto)):
        if m not in vistos:
            vistos.append(m)
    return vistos


def parrafo_verificar(texto: str, ruta: str) -> str | None:
    """Párrafo de comentario del VERIFICAR de `ruta`: hasta el siguiente VERIFICAR o una línea «#» vacía."""
    lineas = texto.splitlines()
    patron = re.compile(r"^\s*#\s*VERIFICAR\s+" + re.escape(ruta) + r"(?![A-Za-z0-9_])")
    inicio = next((i for i, l in enumerate(lineas) if patron.search(l)), None)
    if inicio is None:
        return None
    fin = inicio + 1
    while fin < len(lineas) and not re.match(r"^\s*#\s*(VERIFICAR\s|$)", lineas[fin]):
        fin += 1
    return "\n".join(lineas[inicio:fin])


def seccion_referencias(texto: str) -> str:
    lineas = texto.splitlines()
    inicio = next((i for i, l in enumerate(lineas) if TITULO_REFERENCIAS in l and es_comentario(l)), None)
    if inicio is None:
        return ""
    fin = inicio + 1
    while fin < len(lineas) and lineas[fin].strip() not in ("#", ""):
        fin += 1
    return "\n".join(lineas[inicio + 1:fin])


def verificar_contra_insumo(texto: str, insumo: dict, sha256: str | None = None) -> list[str]:
    """HF-81, HF-82: el bloque lleva los marcadores y los riesgos que el insumo trae para sus claves y referencias."""
    out = []
    if sha256 is not None and sha256 not in texto:
        out.append(f"la cabecera no cita el sha256 del insumo ({sha256[:12]}…): el bloque no se generó con este insumo")
    for corta, ruta, _ in ENTRADAS_CON_ESTADO:
        e = entrada(insumo, corta)
        if e is None:
            continue
        exigidos = marcadores(e.get("estado"))
        if corta == "reusepin":
            extra = [_plano(p.get("estado")) for p in pendientes_config(insumo, True)]
            for dato in extra + [esc for _, esc in escalamientos(insumo)]:
                exigidos += [m for m in marcadores(dato) if m not in exigidos]
        parrafo = parrafo_verificar(texto, ruta)
        normal = _normal(parrafo or "")
        faltan = [m for m in exigidos if m not in normal]
        if parrafo is not None and faltan:
            out.append(f"`VERIFICAR {ruta}` no lleva {', '.join(faltan)} del insumo (estado"
                       f"{', pendiente o escalamiento' if corta == 'reusepin' else ''}; HF-81, references/03 §3e "
                       "regla 2)")
        if corta == "identitymatcher" and parrafo is not None and CITA_STEERING_MATCHER in normal \
                and CITA_STEERING_MATCHER not in _plano(e.get("estado")) + _plano(e.get("fuente")):
            out.append(f"`VERIFICAR {ruta}` cita «{CITA_STEERING_MATCHER}» como fuente del matcher y el insumo no "
                       "(HF-82: la fuente es la que declara el insumo)")
        if corta == "reusepin" and parrafo is not None:
            for pid, _ in escalamientos(insumo):
                if f"Escalamiento N4 en el insumo ({pid})" not in normal:
                    out.append(f"`VERIFICAR {ruta}` no lleva el escalamiento N4 de {pid} del insumo (HF-81)")
    refs = _normal(seccion_referencias(texto))
    for linea in referencias(insumo):
        clave, _, estado = linea.partition(": ")
        faltan = [m for m in marcadores(estado) if m not in refs]
        if clave not in refs:
            out.append(f"«{TITULO_REFERENCIAS}» no lleva {clave} del insumo (HF-81)")
        elif faltan:
            out.append(f"«{TITULO_REFERENCIAS}»: {clave} sin {', '.join(faltan)} del insumo (HF-81)")
    return out


def verificar_bloque(texto: str, dir_propuesta: Path | None = None, insumo: dict | None = None,
                     sha256: str | None = None) -> list[str]:
    """Hallazgos del bloque de modo Colombia (vacío = OK). Con `insumo`, también los marcadores (HF-81, HF-82)."""
    out = []
    for n, linea in activas(texto):
        clave = next((c for c in CLAVES_COMPARTIDAS if re.search(rf"\b{c}\b", linea, re.I)), None)
        motivo = f"clave compartida {clave}" if clave else "clave"
        out.append(f"línea {n} activa en modo Colombia ({motivo}: {linea.strip()[:60]!r}); el bloque solo lleva "
                   "comentarios VERIFICAR (EVAL-PIN-12, -14)")
    verificar = "\n".join(l for l in texto.splitlines() if es_comentario(l) and "VERIFICAR" in l)
    for clave in VERIFICAR_COLOMBIA:
        if not re.search(re.escape(clave) + r"(?![A-Za-z0-9_])", verificar):
            out.append(f"falta `VERIFICAR {clave}` en la lista del modo Colombia (HF-65)")
    for n, linea in activas(texto):
        if REFERENCIA.search(linea):
            out.append(f"línea {n}: matcher o lista de referencia como valor activo (EVAL-PIN-13, HF-58)")
    if HUECO.search(texto):
        out.append("quedan {{huecos}} sin resolver")
    for _, ruta, _ in ENTRADAS_CON_ESTADO:
        parrafo = parrafo_verificar(texto, ruta)
        if parrafo is not None and ESTADO_EN_INSUMO not in _normal(parrafo):
            out.append(f"`VERIFICAR {ruta}` sin «{ESTADO_EN_INSUMO}» (HF-81: el estado del insumo, con sus [PM] y "
                       "RG-nnn, va en el artefacto)")
    if insumo is not None:
        out += verificar_contra_insumo(texto, insumo, sha256)
    if dir_propuesta is not None:
        for f in sorted(dir_propuesta.rglob("*")):
            if f.is_file() and (f.name.endswith("IdentityMatcher.scala") or f.name == "identifying-attributes.json"):
                out.append(f"{f.name} en la propuesta: en modo Colombia se reutilizan el matcher y la lista (EVAL-PIN-14)")
    return out


def cargar_insumo(ruta: Path) -> tuple[dict, str]:
    """Insumo como dict y sha256 de sus bytes (para --verificar --insumo)."""
    try:
        import yaml
    except ImportError as exc:  # pragma: no cover - depende del equipo
        raise ErrorUso("falta PyYAML: pip install pyyaml") from exc
    if not ruta.is_file():
        raise ErrorUso(f"falta el insumo {ruta}")
    crudo = ruta.read_bytes()
    return yaml.safe_load(crudo.decode("utf-8")) or {}, hashlib.sha256(crudo).hexdigest()


def generar(insumo_ruta: Path, contrato_ruta: Path | None = None) -> str:
    """Bloque de modo Colombia desde el insumo. ValueError con los hallazgos si el insumo o el resultado fallan."""
    try:
        import yaml
    except ImportError as exc:  # pragma: no cover - depende del equipo
        raise ErrorUso("falta PyYAML: pip install pyyaml") from exc
    if not insumo_ruta.is_file():
        raise ErrorUso(f"falta el insumo {insumo_ruta}")
    if not PLANTILLA.is_file():
        raise ErrorUso(f"falta la plantilla {PLANTILLA}")
    crudo = insumo_ruta.read_bytes()
    texto = crudo.decode("utf-8")
    insumo = yaml.safe_load(texto) or {}
    contrato = None
    if contrato_ruta is not None:
        if not contrato_ruta.is_file():
            raise ErrorUso(f"falta el contrato {contrato_ruta}")
        contrato = contrato_ruta.read_text(encoding="utf-8")
    errores, _ = _modulo_insumo().verificar(insumo, texto, contrato)
    if errores:
        raise ValueError("el insumo no pasa tools/verificar-insumo-pinning.py:\n  - " + "\n  - ".join(errores))
    datos = valores(insumo)
    documento = insumo.get("documento") or {}
    cabecera = (
        "# ─────────────────────────────────────────────────────────────────────────\n"
        f"# Bloque de Pinning en modo Colombia — {datos['INGESTA_NOMBRE']} (sin claves)\n"
        "# ─────────────────────────────────────────────────────────────────────────\n"
        "# Generado por edf-pinning (scripts/render_bloque_colombia.py) desde la sección\n"
        "# «Modo Colombia» de assets/pinning-application-block.conf.template.\n"
        f"# Insumo: {insumo_ruta.name} (versión {documento.get('version', '?')}, sha256\n"
        f"# {hashlib.sha256(crudo).hexdigest()}).\n"
        "# Propuesta para revisión humana: no se copia a ningún conf.\n"
        "#\n"
    )
    bloque = cabecera + rellenar(seccion_colombia(PLANTILLA.read_text(encoding="utf-8")), datos)
    hallazgos = verificar_bloque(bloque, insumo=insumo, sha256=hashlib.sha256(crudo).hexdigest())
    if hallazgos:
        raise ValueError("el bloque generado no pasa la verificación:\n  - " + "\n  - ".join(hallazgos))
    return bloque


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--insumo", type=Path,
                    help="yaml-skill-pinning.yaml (genera; con --verificar, cruza marcadores y riesgos, HF-81)")
    ap.add_argument("--verificar", type=Path, help="bloque .conf de modo Colombia (verifica)")
    ap.add_argument("--contrato", type=Path, help="contrato-curation-pinning.md (cruza las claves del insumo)")
    ap.add_argument("--out", type=Path, help="archivo de salida; sin él, la salida estándar")
    ap.add_argument("--dir-propuesta", type=Path, help="con --verificar: carpeta propuestas/pinning/ (EVAL-PIN-14)")
    args = ap.parse_args(argv)
    if args.insumo is None and args.verificar is None:
        ap.error("hace falta --insumo (generar) o --verificar (verificar)")
    try:
        if args.verificar is not None:
            if not args.verificar.is_file():
                raise ErrorUso(f"falta {args.verificar}")
            insumo, sha = cargar_insumo(args.insumo) if args.insumo is not None else (None, None)
            hallazgos = verificar_bloque(args.verificar.read_text(encoding="utf-8"), args.dir_propuesta, insumo, sha)
            for h in hallazgos:
                print(f"  X {h}")
            print("BLOQUE OK (modo Colombia)" if not hallazgos else f"BLOQUE con {len(hallazgos)} hallazgo(s)")
            return 1 if hallazgos else 0
        bloque = generar(args.insumo, args.contrato)
    except ErrorUso as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    except ValueError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    if args.out is None:
        sys.stdout.write(bloque)
    else:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(bloque, encoding="utf-8", newline="\n")
        print(f"OK: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
