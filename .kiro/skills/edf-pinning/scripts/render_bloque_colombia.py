#!/usr/bin/env python3
"""Bloque de Pinning en modo Colombia: generador mínimo y verificador mecánico (HF-66, HF-67).

En modo Colombia (bureau.colombia_innersource) el bloque HOCON de la ingesta NO tiene claves: es la lista VERIFICAR
de la sección «Modo Colombia» de assets/pinning-application-block.conf.template contra el conf compartido. Este script
lo hace reproducible: dos generaciones con el mismo insumo dan el mismo archivo, byte a byte.

Uso (desde la raíz del framework):
    python3 .kiro/skills/edf-pinning/scripts/render_bloque_colombia.py --insumo <yaml-skill-pinning.yaml> \
        [--contrato <contrato-curation-pinning.md>] [--out <bloque.conf>]
    python3 .kiro/skills/edf-pinning/scripts/render_bloque_colombia.py --verificar <bloque.conf> [--dir-propuesta <dir>]

Generar:
  1. valida el insumo con tools/verificar-insumo-pinning.py (sin errores; con --contrato cruza las claves) y exige
     modo_colombia.aplica = true;
  2. toma la sección «Modo Colombia» de la plantilla tal cual y rellena sus huecos con datos del insumo:
     INGESTA_NOMBRE (documento.ingesta, en mayúsculas), IDENTIFYING_ATTRIBUTES_COMPARTIDO,
     IDENTITY_MATCHER_COLOMBIA, CLAVES_EVENTO_CURADO (obligatorias y opcional) y REUSE_PIN_VALOR_FUENTE
     (valor_en_fuente de la entrada reusePin: un dato, nunca un booleano para escribir);
  3. antepone una cabecera de procedencia (insumo, versión y sha256) y verifica el resultado antes de escribirlo.
  Sin --out escribe en la salida estándar.

Verificar (equivalente en Python de evals/verificar-artefacto.ps1 para modo Colombia, EVAL-PIN-12..14):
  - 0 líneas activas (todo comentario o en blanco): ninguna clave compartida, tampoco `reusePin = …` (EVAL-PIN-12, -14);
  - una línea `VERIFICAR` por cada entrada de VERIFICAR_COLOMBIA (rutas HOCON que lee el motor y los nombres [PM]);
  - ISIdentityMatcher, RIIdentityMatcher e identifying-attributes.json solo en comentarios (EVAL-PIN-13): sin líneas
    activas no pueden ser un valor;
  - ningún {{hueco}};
  - con --dir-propuesta: no hay *IdentityMatcher.scala ni identifying-attributes.json (EVAL-PIN-14).
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
    reuse = next((e for e in bloque if isinstance(e, dict)
                  and str(e.get("clave", "")).rsplit(".", 1)[-1].lower() == "reusepin"), None)
    if reuse is None or isinstance(reuse.get("valor_en_fuente"), bool):
        raise ValueError("insumo: reusePin sin valor_en_fuente de texto (es un dato de la fuente, HF-57)")
    return {
        "INGESTA_NOMBRE": _texto((insumo.get("documento") or {}).get("ingesta"), "documento.ingesta").upper(),
        "IDENTIFYING_ATTRIBUTES_COMPARTIDO": _texto(mc.get("identifying_attributes_compartido"),
                                                    "modo_colombia.identifying_attributes_compartido"),
        "IDENTITY_MATCHER_COLOMBIA": _texto(mc.get("identity_matcher"), "modo_colombia.identity_matcher"),
        "CLAVES_EVENTO_CURADO": claves_txt,
        "REUSE_PIN_VALOR_FUENTE": _texto(reuse.get("valor_en_fuente"), "reusePin.valor_en_fuente"),
    }


def rellenar(seccion: str, datos: dict[str, str]) -> str:
    faltan = sorted(set(HUECO.findall(seccion)) - set(datos))
    if faltan:
        raise ValueError(f"plantilla: huecos sin dato en el insumo: {faltan}")
    return HUECO.sub(lambda m: datos[m.group(1)], seccion)


def verificar_bloque(texto: str, dir_propuesta: Path | None = None) -> list[str]:
    """Hallazgos del bloque de modo Colombia (vacío = OK)."""
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
    if dir_propuesta is not None:
        for f in sorted(dir_propuesta.rglob("*")):
            if f.is_file() and (f.name.endswith("IdentityMatcher.scala") or f.name == "identifying-attributes.json"):
                out.append(f"{f.name} en la propuesta: en modo Colombia se reutilizan el matcher y la lista (EVAL-PIN-14)")
    return out


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
    hallazgos = verificar_bloque(bloque)
    if hallazgos:
        raise ValueError("el bloque generado no pasa la verificación:\n  - " + "\n  - ".join(hallazgos))
    return bloque


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    grupo = ap.add_mutually_exclusive_group(required=True)
    grupo.add_argument("--insumo", type=Path, help="yaml-skill-pinning.yaml (genera)")
    grupo.add_argument("--verificar", type=Path, help="bloque .conf de modo Colombia (verifica)")
    ap.add_argument("--contrato", type=Path, help="contrato-curation-pinning.md (cruza las claves del insumo)")
    ap.add_argument("--out", type=Path, help="archivo de salida; sin él, la salida estándar")
    ap.add_argument("--dir-propuesta", type=Path, help="con --verificar: carpeta propuestas/pinning/ (EVAL-PIN-14)")
    args = ap.parse_args(argv)
    try:
        if args.verificar is not None:
            if not args.verificar.is_file():
                raise ErrorUso(f"falta {args.verificar}")
            hallazgos = verificar_bloque(args.verificar.read_text(encoding="utf-8"), args.dir_propuesta)
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
