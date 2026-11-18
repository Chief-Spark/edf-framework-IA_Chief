"""Utilidades del source-spec para prevalidar_archivo.py.

Las reglas de verificación del spec viven en un solo lugar: el generador Python render_templates.py. Este
módulo las reutiliza en lugar de duplicarlas. No requiere Java ni compilar nada.
"""
from __future__ import annotations

from pathlib import Path

from render_templates import SpecError, field_by_name, java_to_py_date, load_spec, load_yaml
from render_templates import verify_spec as _verify_spec_dict

__all__ = ["SpecError", "field_by_name", "java_to_py_date", "load_spec", "load_yaml", "verify_spec"]


def verify_spec(spec_path: Path) -> list[str]:
    """Verifica el spec del archivo dado (esquema + reglas semánticas). Devuelve los errores encontrados."""
    return _verify_spec_dict(load_spec(Path(spec_path)))
