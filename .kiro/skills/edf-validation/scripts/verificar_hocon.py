#!/usr/bin/env python3
"""Parse-check OPCIONAL de los fragmentos HOCON generados (requiere pyhocon; si falta, sale con código 3).

Uso:
    python verificar_hocon.py --generado CARPETA_GENERADA --fuente sisben [--archivo-ejemplo NOMBRE]

No sustituye el parse-check con el parser real de Oxygen (ValidationConfigs); verifica sintaxis HOCON,
claves esperadas, ausencia de claves que el parser ignora en silencio y que filePattern acepte la ruta.

Estructura (plan de corrección #5, P-1 a, 2026-10-21): el parseo HOCON (cargar_parser, pyhocon con import
diferido) está separado de la evaluación de reglas (evaluar_reglas, función pura sobre dict/list), que
se prueba sin pyhocon. Reglas: AC-29a (REQUIRED_KEYS), AC-29b (IGNORED_KEYS), AC-30
(dupKeyCheckEnabled ⇒ computeMetrics), AC-31 (validate[] con fileName y header), AC-32 (lookups con
tableName), filePattern con .* y que acepte la ruta del archivo de ejemplo.

1.9.2 (corrida E2E SISBEN): cada elemento de validate[] y de metrics[] lleva su filePattern, porque el motor lo lee
con data.filePattern.get (SparkValidator.getValidateInfo/getMetricsInfo; AUD-V-01), y cada lookup usa
${oxygen.application.paths.lookupHome} en `file` (AUD-V-06). Para parsear, leer_fragmentos define esa ruta con
un valor sintético (LOOKUP_HOME_SINTETICO).
"""
from __future__ import annotations

import argparse
import re
import sys
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path

IGNORED_KEYS = ("numberOfHeaderRows", "numberOfFooterRows", "historicAttributeArg", "rejectedDir")
REQUIRED_KEYS = ("filePattern", "processor", "headerRows", "footerRows", "computeMetrics", "validate")
CARPETA_PROPUESTA = Path("env") / "application-conf" / "innersource" / "propuesta"
LOOKUP_HOME_SINTETICO = "/lookupHome-sintetico"  # valor de ${oxygen.application.paths.lookupHome} al parsear


def cargar_parser() -> Callable[[str], Mapping]:
    """Devuelve el parser HOCON de pyhocon. Lanza ImportError si pyhocon no está instalado."""
    from pyhocon import ConfigFactory  # import diferido: dependencia opcional

    return ConfigFactory.parse_string


def evaluar_reglas(block: Mapping, lookups: Sequence[Mapping], sample_name: str | None) -> list[str]:
    """Reglas sobre el bloque de validation y los lookups ya parseados. Pura: no lee archivos ni importa pyhocon."""
    problems: list[str] = []
    problems += [f"falta la clave {k}" for k in REQUIRED_KEYS if k not in block]  # AC-29a
    problems += [f"clave ignorada por el parser: {k}" for k in IGNORED_KEYS if k in block]  # AC-29b
    file_pattern = str(block.get("filePattern", ""))
    if not file_pattern.startswith(".*"):
        problems.append("filePattern debe empezar con .*")
    if block.get("dupKeyCheckEnabled", None) and not block.get("computeMetrics", None):  # AC-30
        problems.append("dupKeyCheckEnabled exige computeMetrics = true")
    validate = (block.get("validate", None) or [{}])[0]
    if "fileName" not in validate or "header" not in validate:  # AC-31
        problems.append("validate[] sin expresión fileName/header: el motor no emitiría marcadores (F-11)")
    if sample_name and not re.fullmatch(file_pattern, "/ruta/sintetica/" + sample_name):
        problems.append("filePattern no acepta la ruta completa del archivo de ejemplo")
    for lista in ("validate", "metrics"):  # AUD-V-01: el motor hace data.filePattern.get en cada elemento
        for i, item in enumerate(block.get(lista, None) or []):
            # get con default: ConfigTree de pyhocon lanza si la clave falta y no hay default.
            patron = item.get("filePattern", None) if isinstance(item, Mapping) else None
            if patron is None:
                problems.append(f"{lista}[{i}] sin filePattern: el motor hace data.filePattern.get "
                                "(NoSuchElementException en cada archivo)")
            elif sample_name and not re.fullmatch(str(patron), "/ruta/sintetica/" + sample_name):
                problems.append(f"{lista}[{i}].filePattern no acepta la ruta completa del archivo de ejemplo")
    for item in lookups:
        if "tableName" not in item:  # AC-32
            problems.append("lookup sin tableName (clave obligatoria)")
        if not str(item.get("file", "")).startswith(LOOKUP_HOME_SINTETICO + "/"):  # AUD-V-06
            problems.append(f"lookup {item.get('tableName', '?')}: file sin ${{oxygen.application.paths.lookupHome}}")
    return problems


def leer_fragmentos(generated: Path, fuente: str,
                    parse: Callable[[str], Mapping]) -> tuple[Mapping, list[Mapping]]:
    """Lee y parsea validation-configs-<fuente>.conf y lookups-<fuente>.conf de la carpeta generada."""
    folder = generated / CARPETA_PROPUESTA
    block_text = (folder / f"validation-configs-{fuente}.conf").read_text(encoding="utf-8")
    block = parse("configs = [\n" + block_text + "\n]")["configs"][0]
    lookups_text = (folder / f"lookups-{fuente}.conf").read_text(encoding="utf-8")
    lookups = list(parse("lookups = " + lookups_text + "\noxygen.application.paths.lookupHome = "
                         f'"{LOOKUP_HOME_SINTETICO}"\n')["lookups"])
    return block, lookups


def check(generated: Path, fuente: str, sample_name: str | None) -> list[str]:
    parse = cargar_parser()  # primero: sin pyhocon sale con código 3 aunque falten archivos (como antes)
    block, lookups = leer_fragmentos(generated, fuente, parse)
    problems = evaluar_reglas(block, lookups, sample_name)
    print(f"bloque validation: filePattern={block.get('filePattern', None)} "
          f"headerRows={block.get('headerRows', None)} footerRows={block.get('footerRows', None)} "
          f"deDupVersion={block.get('deDupVersion', '')!r}")
    print(f"lookups: {len(lookups)} tablas")
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--generado", required=True, type=Path)
    parser.add_argument("--fuente", required=True)
    parser.add_argument("--archivo-ejemplo")
    args = parser.parse_args(argv)
    try:
        problems = check(args.generado, args.fuente, args.archivo_ejemplo)
    except ImportError:
        print("AVISO: pyhocon no está disponible; parse-check HOCON omitido.", file=sys.stderr)
        return 3
    except Exception as exc:  # error de sintaxis HOCON o archivo faltante: se informa sin traza
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    for p in problems:
        print(f"  - {p}", file=sys.stderr)
    print("HOCON OK" if not problems else "HOCON con problemas")
    return 0 if not problems else 2


if __name__ == "__main__":
    sys.exit(main())
