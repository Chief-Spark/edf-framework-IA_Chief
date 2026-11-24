#!/usr/bin/env python3
"""Auditoría repetible de las skills del framework EDF (1.9.0).

Busca en `.kiro/skills/*` los patrones de defecto que encontró la auditoría de noviembre de 2026:

1. silencios   Errores tragados en el código generado: `Try(...).getOrElse(...)`, `Try(...).toOption`,
               `case NonFatal(_) =>`. Cada uno intencional debe llevar la marca
               `SILENCIO-INTENCIONAL <ID>` (ID de una decisión de la skill) en la misma línea o en el
               comentario inmediatamente anterior. Los que no la llevan se reportan.
2. obsoletas   Referencias al generador Java deprecado (`scripts/java`, `java -jar`, clases `*.java` del
               generador, `render-templates.jar`) fuera de `DEPRECADOS.md`, `NO-USAR.md` y de las líneas que
               lo declaran deprecado.
3. huerfanas   (informativo) IDs de `references/decisiones-y-abiertos.md` sin rastro en plantillas, scripts
               ni pruebas. No cambia el código de salida: muchas decisiones son de diseño y no de código.

Uso:
    python tools/auditar-skills.py                 # todas las skills
    python tools/auditar-skills.py --skill edf-curation
    python tools/auditar-skills.py --raiz <repo>   # otra copia del framework

Salida: 0 sin hallazgos de silencios ni obsoletas; 1 si hay alguno.
"""
from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

MARCA = re.compile(r"SILENCIO-INTENCIONAL\s+([A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*)")
SILENCIOS = [
    ("Try(...).getOrElse", re.compile(r"\bTry\s*[({].*\.getOrElse\s*\(")),
    ("Try(...).toOption", re.compile(r"\bTry\s*[({].*\.toOption\b")),
    ("catch NonFatal", re.compile(r"case\s+NonFatal\s*\(\s*_?\w*\s*\)\s*=>")),
]
OBSOLETAS = [
    ("scripts/java", re.compile(r"scripts/java\b")),
    ("java -jar", re.compile(r"\bjava\s+-jar\b")),
    ("render-templates.jar", re.compile(r"render-templates\.jar")),
    ("clase Java del generador",
     re.compile(r"\b(RenderTemplatesTest|ReferenciaTest|RenderTemplates|Referencia|Verificaciones|Contexto|Py)\.java\b")),
]
# Una línea que declara lo deprecado (la regla "nunca compilar") no es una referencia obsoleta.
DECLARA_DEPRECADO = re.compile(r"deprecad|hist[oó]ric|conservad|no se usa|nunca|no (se )?ejecut|no-usar|never", re.IGNORECASE)
ARCHIVOS_DEPRECADOS = {"DEPRECADOS.md", "NO-USAR.md"}
EXT_CODIGO = {".tpl", ".template", ".scala", ".py"}
EXT_TEXTO = EXT_CODIGO | {".md", ".yaml", ".yml", ".json", ".conf", ".ps1"}
ID_DECISION = re.compile(r"^\|\s*([A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+[a-z]?)\s*\|")


@dataclass
class Hallazgo:
    barrido: str
    skill: str
    ruta: str
    linea: int
    detalle: str

    def fila(self) -> str:
        return f"{self.barrido:<10} {self.skill:<16} {self.ruta}:{self.linea}  {self.detalle}"


def _archivos(skill: Path, extensiones: set[str]):
    for f in sorted(skill.rglob("*")):
        partes = set(f.relative_to(skill).parts)
        if not f.is_file() or f.suffix not in extensiones:
            continue
        if {"java", "__pycache__", ".pytest_cache", "target", "examples"} & partes:
            continue  # el generador deprecado, cachés y la salida generada de los ejemplos
        yield f


def _leer(f: Path) -> list[str]:
    return f.read_text(encoding="utf-8", errors="replace").splitlines()


def barrido_silencios(skill: Path) -> list[Hallazgo]:
    out = []
    for f in _archivos(skill, EXT_CODIGO):
        if f.suffix == ".py" and "tests" in f.relative_to(skill).parts:
            continue  # las pruebas pueden provocar fallas a propósito
        lineas = _leer(f)
        for i, linea in enumerate(lineas):
            for nombre, patron in SILENCIOS:
                if not patron.search(linea):
                    continue
                # La marca va en la misma línea o en el comentario inmediatamente anterior.
                previa = lineas[i - 1].strip() if i > 0 else ""
                contexto = linea + ("\n" + previa if previa.startswith(("//", "*", "/*", "#")) else "")
                if not MARCA.search(contexto):
                    out.append(Hallazgo("silencios", skill.name, f.relative_to(skill).as_posix(), i + 1,
                                        f"{nombre} sin marca SILENCIO-INTENCIONAL <ID>"))
                break
    return out


def barrido_obsoletas(skill: Path) -> list[Hallazgo]:
    out = []
    for f in _archivos(skill, EXT_TEXTO):
        if f.name in ARCHIVOS_DEPRECADOS or f.name == Path(__file__).name:
            continue
        lineas = _leer(f)
        for i, linea in enumerate(lineas):
            # la declaración de deprecado puede venir en la línea vecina (un párrafo partido en dos líneas)
            if DECLARA_DEPRECADO.search("\n".join(lineas[max(0, i - 1):i + 2])):
                continue
            for nombre, patron in OBSOLETAS:
                if patron.search(linea):
                    out.append(Hallazgo("obsoletas", skill.name, f.relative_to(skill).as_posix(), i + 1,
                                        f"referencia al generador Java deprecado ({nombre})"))
                    break
    return out


def barrido_huerfanas(skill: Path) -> list[Hallazgo]:
    doc = skill / "references" / "decisiones-y-abiertos.md"
    if not doc.exists():
        return []
    codigo = "\n".join("\n".join(_leer(f)) for f in _archivos(skill, EXT_CODIGO))
    out = []
    for i, linea in enumerate(_leer(doc)):
        m = ID_DECISION.match(linea)
        if m and not re.search(rf"\b{re.escape(m.group(1))}\b", codigo):
            out.append(Hallazgo("huerfanas", skill.name, doc.relative_to(skill).as_posix(), i + 1,
                                f"{m.group(1)} sin rastro en plantillas, scripts ni pruebas (informativo)"))
    return out


def auditar(raiz: Path, solo_skill: str | None = None) -> list[Hallazgo]:
    skills_dir = raiz / ".kiro" / "skills"
    if not skills_dir.is_dir():
        raise SystemExit(f"no existe {skills_dir}")
    hallazgos: list[Hallazgo] = []
    for skill in sorted(p for p in skills_dir.iterdir() if p.is_dir()):
        if solo_skill and skill.name != solo_skill:
            continue
        hallazgos += barrido_silencios(skill) + barrido_obsoletas(skill) + barrido_huerfanas(skill)
    return hallazgos


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Auditoría repetible de las skills del framework EDF.")
    ap.add_argument("--raiz", type=Path, default=RAIZ, help="raíz del framework (contiene .kiro/skills)")
    ap.add_argument("--skill", help="solo esta skill (p. ej. edf-curation)")
    args = ap.parse_args(argv)
    hallazgos = auditar(args.raiz, args.skill)
    bloqueantes = [h for h in hallazgos if h.barrido != "huerfanas"]
    for h in hallazgos:
        print(h.fila())
    conteo = {b: sum(1 for h in hallazgos if h.barrido == b) for b in ("silencios", "obsoletas", "huerfanas")}
    print(f"\nsilencios sin marca: {conteo['silencios']} · referencias obsoletas: {conteo['obsoletas']} · "
          f"decisiones sin rastro (informativo): {conteo['huerfanas']}")
    print("AUDITORIA OK" if not bloqueantes else "AUDITORIA CON HALLAZGOS")
    return 1 if bloqueantes else 0


if __name__ == "__main__":
    sys.exit(main())
