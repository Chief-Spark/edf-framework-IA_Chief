"""Utilidades del source-spec para los scripts Python que siguen en Python (prevalidar_archivo.py).

Las reglas de verificación del spec viven en un solo lugar: el generador Java (scripts/java). Este módulo
las invoca con --solo-verificar en lugar de duplicarlas. Construir el jar una vez:
    mvn -q -f scripts/java/pom.xml package
"""
from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path
from typing import Any

import yaml

SCRIPTS_DIR = Path(__file__).resolve().parent
JAR_PATH = SCRIPTS_DIR / "java" / "target" / "render-templates.jar"
JAVA_DATE_TOKENS = {"dd": "%d", "MM": "%m", "yyyy": "%Y", "HH": "%H", "mm": "%M", "ss": "%S"}


class SpecError(Exception):
    """El source-spec no cumple el esquema o una regla semántica."""


def load_yaml(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as fh:
        return yaml.safe_load(fh)


def load_spec(path: Path) -> dict:
    data = load_yaml(path)
    if not isinstance(data, dict):
        raise SpecError(f"{path.name}: el source-spec debe ser un objeto YAML")
    return data


def field_by_name(spec: dict) -> dict[str, dict]:
    return {c["nombre"]: c for c in spec["campos"]}


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


def java_command() -> str:
    """java de JAVA_HOME si existe (el PATH puede tener un JDK anterior a 17), si no el del PATH."""
    home = os.environ.get("JAVA_HOME")
    if home:
        for name in ("java.exe", "java"):
            candidate = Path(home) / "bin" / name
            if candidate.is_file():
                return str(candidate)
    found = shutil.which("java")
    if not found:
        raise SpecError("no se encontró Java 17: defina JAVA_HOME o agregue java al PATH")
    return found


def verify_spec(spec_path: Path) -> list[str]:
    """Verifica el spec con el generador Java (--solo-verificar). Devuelve los errores encontrados."""
    if not JAR_PATH.is_file():
        raise SpecError(f"falta {JAR_PATH.relative_to(SCRIPTS_DIR.parent)}: ejecute "
                        "mvn -q -f scripts/java/pom.xml package")
    proc = subprocess.run([java_command(), "-jar", str(JAR_PATH), "--spec", str(spec_path), "--solo-verificar"],
                          capture_output=True, text=True, encoding="utf-8", check=False)
    if proc.returncode == 0:
        return []
    errors = [line[4:] for line in proc.stderr.splitlines() if line.startswith("  - ")]
    return errors or [proc.stderr.strip() or f"el verificador terminó con código {proc.returncode}"]
