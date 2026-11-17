"""Parser HOCON mínimo para verificar los bloques generados (sin dependencias externas).

Cubre el subconjunto que emiten las plantillas: objetos, listas, claves con puntos, comentarios
`#` y `//`, cadenas entre comillas con escapes, valores sin comillas (números, true/false/null),
sustituciones `${ruta}` / `${?ruta}` concatenadas con cadenas, separadores `=`, `:` y `+=`.
No resuelve sustituciones (las conserva como texto "${...}"). Si `pyhocon` está instalado se usa
además como segunda opinión (ver tests). No es el parser de Typesafe: un parse-check contra
oxygen-config sigue siendo la verificación definitiva (F-01).
"""

from __future__ import annotations

import json
import sys
from typing import Any


class HoconError(ValueError):
    pass


class _P:
    def __init__(self, texto: str):
        self.s, self.i = texto, 0

    # -- utilidades
    def _ws(self, saltos: bool = True) -> None:
        while self.i < len(self.s):
            c = self.s[self.i]
            if c == "#" or self.s.startswith("//", self.i):
                while self.i < len(self.s) and self.s[self.i] != "\n":
                    self.i += 1
            elif c in " \t\r" or (saltos and c == "\n"):
                self.i += 1
            else:
                break

    def _err(self, msg: str) -> HoconError:
        linea = self.s.count("\n", 0, self.i) + 1
        return HoconError(f"línea {linea}: {msg}")

    # -- gramática
    def documento(self) -> dict[str, Any]:
        self._ws()
        if self.i < len(self.s) and self.s[self.i] == "{":
            self.i += 1
            obj = self._miembros("}")
        else:
            obj = self._miembros(None)
        self._ws()
        if self.i != len(self.s):
            raise self._err("contenido sobrante")
        return obj

    def _miembros(self, cierre: str | None) -> dict[str, Any]:
        obj: dict[str, Any] = {}
        while True:
            self._ws()
            if self.i >= len(self.s):
                if cierre:
                    raise self._err(f"falta '{cierre}'")
                return obj
            if cierre and self.s[self.i] == cierre:
                self.i += 1
                return obj
            if self.s[self.i] == ",":
                self.i += 1
                continue
            ruta = self._clave()
            self._ws(saltos=False)
            anexar = False
            if self.s.startswith("+=", self.i):
                anexar, self.i = True, self.i + 2
            elif self.i < len(self.s) and self.s[self.i] in "=:":
                self.i += 1
            elif self.i < len(self.s) and self.s[self.i] == "{":
                pass
            else:
                raise self._err(f"se esperaba '=' tras la clave {'.'.join(ruta)}")
            self._ws()
            valor = self._valor()
            self._asignar(obj, ruta, valor, anexar)

    def _asignar(self, obj: dict[str, Any], ruta: list[str], valor: Any, anexar: bool) -> None:
        for k in ruta[:-1]:
            obj = obj.setdefault(k, {})
            if not isinstance(obj, dict):
                raise self._err(f"la clave {k} no es un objeto")
        k = ruta[-1]
        if anexar:
            obj.setdefault(k, []).append(valor)
        elif isinstance(valor, dict) and isinstance(obj.get(k), dict):
            _fusionar(obj[k], valor)
        else:
            obj[k] = valor

    def _clave(self) -> list[str]:
        partes, actual = [], ""
        while self.i < len(self.s):
            c = self.s[self.i]
            if c == '"':
                actual += self._cadena()
                continue
            if c == ".":
                partes.append(actual)
                actual, self.i = "", self.i + 1
                continue
            if c in " \t=:{+\n\r":
                break
            actual += c
            self.i += 1
        if not actual:
            raise self._err("clave vacía")
        partes.append(actual)
        return partes

    def _cadena(self) -> str:
        if self.s.startswith('"""', self.i):
            fin = self.s.find('"""', self.i + 3)
            if fin < 0:
                raise self._err("cadena triple sin cerrar")
            val, self.i = self.s[self.i + 3 : fin], fin + 3
            return val
        fin, j = None, self.i + 1
        while j < len(self.s):
            if self.s[j] == "\\":
                j += 2
                continue
            if self.s[j] == '"':
                fin = j
                break
            if self.s[j] == "\n":
                break
            j += 1
        if fin is None:
            raise self._err("cadena sin cerrar")
        crudo = self.s[self.i : fin + 1]
        self.i = fin + 1
        try:
            return json.loads(crudo)
        except ValueError as exc:
            raise self._err(f"escape inválido en {crudo!r}") from exc

    def _valor(self) -> Any:
        c = self.s[self.i] if self.i < len(self.s) else ""
        if c == "{":
            self.i += 1
            return self._miembros("}")
        if c == "[":
            self.i += 1
            return self._lista()
        partes: list[Any] = []
        while self.i < len(self.s):
            c = self.s[self.i]
            if c == '"':
                partes.append(self._cadena())
            elif self.s.startswith("${", self.i):
                fin = self.s.find("}", self.i)
                if fin < 0:
                    raise self._err("sustitución sin cerrar")
                partes.append(self.s[self.i : fin + 1])
                self.i = fin + 1
            elif c in ",}]\n#" or self.s.startswith("//", self.i):
                break
            elif c in " \t\r":
                self.i += 1
                continue
            else:
                j = self.i
                while j < len(self.s) and self.s[j] not in ',}]\n#" \t\r$':
                    j += 1
                if j == self.i:
                    raise self._err(f"carácter inesperado {c!r}")
                partes.append(_escalar(self.s[self.i : j]))
                self.i = j
        if not partes:
            raise self._err("valor vacío")
        if len(partes) == 1:
            return partes[0]
        return "".join(str(p) for p in partes)

    def _lista(self) -> list[Any]:
        out: list[Any] = []
        while True:
            self._ws()
            if self.i >= len(self.s):
                raise self._err("falta ']'")
            if self.s[self.i] == "]":
                self.i += 1
                return out
            if self.s[self.i] == ",":
                self.i += 1
                continue
            out.append(self._valor())


def _escalar(tok: str) -> Any:
    if tok in ("true", "false"):
        return tok == "true"
    if tok == "null":
        return None
    try:
        return int(tok)
    except ValueError:
        pass
    try:
        return float(tok)
    except ValueError:
        return tok


def _fusionar(base: dict[str, Any], nuevo: dict[str, Any]) -> None:
    for k, v in nuevo.items():
        if isinstance(v, dict) and isinstance(base.get(k), dict):
            _fusionar(base[k], v)
        else:
            base[k] = v


def parse(texto: str) -> dict[str, Any]:
    return _P(texto).documento()


def main(rutas: list[str]) -> int:
    rc = 0
    for ruta in rutas:
        try:
            with open(ruta, encoding="utf-8") as fh:
                parse(fh.read())
            print(f"OK   {ruta}")
        except (OSError, HoconError) as exc:
            print(f"FAIL {ruta}: {exc}")
            rc = 1
    return rc


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
