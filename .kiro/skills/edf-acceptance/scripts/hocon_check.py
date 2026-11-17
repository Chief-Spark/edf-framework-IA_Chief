#!/usr/bin/env python3
"""Parser mínimo del subconjunto HOCON que emiten las plantillas de este skill.

Existe porque pyhocon / Typesafe Config pueden no estar disponibles sin red. Cubre: comentarios
`#` y `//`, objetos `{}`, listas `[]`, separador `=` o `:` (o ninguno antes de `{`), claves con
puntos, cadenas entre comillas con escapes JSON, true/false/null y números. NO soporta
sustituciones `${}`, concatenación ni cadenas multilínea: si aparecen, falla (y eso es lo deseado,
porque las plantillas no deben emitirlas). Si pyhocon está instalado, úselo además.

Uso: python hocon_check.py archivo.conf [...]   -> imprime JSON o sale con código 1.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

TOKEN_RE = re.compile(r'''
    (?P<ws>[ \t\r\n,]+)
  | (?P<comment>(\#|//)[^\n]*)
  | (?P<string>"(?:[^"\\\n]|\\.)*")
  | (?P<punct>[{}\[\]=:])
  | (?P<bare>[A-Za-z0-9_.+\-]+)
''', re.X)


class HoconError(ValueError):
    pass


def tokenize(text: str) -> list[tuple[str, str]]:
    tokens, pos = [], 0
    while pos < len(text):
        m = TOKEN_RE.match(text, pos)
        if not m:
            line = text.count("\n", 0, pos) + 1
            raise HoconError(f"carácter no soportado en línea {line}: {text[pos:pos + 20]!r}")
        kind = m.lastgroup
        if kind == "ws" and "\n" in m.group(0):
            tokens.append(("nl", "\n"))
        elif kind not in ("ws", "comment"):
            tokens.append((kind, m.group(0)))
        pos = m.end()
    return tokens


class Parser:
    def __init__(self, tokens: list[tuple[str, str]]):
        self.toks = [t for t in tokens]
        self.i = 0

    def _skip_nl(self) -> None:
        while self.i < len(self.toks) and self.toks[self.i][0] == "nl":
            self.i += 1

    def peek(self) -> tuple[str, str] | None:
        self._skip_nl()
        return self.toks[self.i] if self.i < len(self.toks) else None

    def take(self, value: str | None = None) -> tuple[str, str]:
        tok = self.peek()
        if tok is None or (value is not None and tok[1] != value):
            raise HoconError(f"se esperaba {value!r}, llegó {tok!r}")
        self.i += 1
        return tok

    def parse_root(self) -> dict:
        tok = self.peek()
        result = self.parse_object() if tok and tok[1] == "{" else self.parse_fields(end=None)
        if self.peek() is not None:
            raise HoconError(f"contenido sobrante: {self.peek()!r}")
        return result

    def parse_object(self) -> dict:
        self.take("{")
        fields = self.parse_fields(end="}")
        self.take("}")
        return fields

    def parse_fields(self, end: str | None) -> dict:
        obj: dict = {}
        while True:
            tok = self.peek()
            if tok is None or tok[1] == end:
                return obj
            kind, key = self.take()
            if kind == "string":
                key = json.loads(key)
            elif kind != "bare":
                raise HoconError(f"clave inválida: {key!r}")
            nxt = self.peek()
            if nxt and nxt[1] in ("=", ":"):
                self.take()
            elif not (nxt and nxt[1] == "{"):
                raise HoconError(f"falta '=' tras la clave {key!r}")
            value = self.parse_value()
            target = obj
            parts = key.split(".") if kind == "bare" else [key]
            for part in parts[:-1]:
                target = target.setdefault(part, {})
            last = parts[-1]
            if isinstance(value, dict) and isinstance(target.get(last), dict):
                target[last] = {**target[last], **value}
            else:
                target[last] = value

    def parse_value(self) -> Any:
        tok = self.peek()
        if tok is None:
            raise HoconError("valor ausente")
        if tok[1] == "{":
            return self.parse_object()
        if tok[1] == "[":
            self.take("[")
            items = []
            while self.peek() and self.peek()[1] != "]":
                items.append(self.parse_value())
            self.take("]")
            return items
        kind, raw = self.take()
        if kind == "string":
            return json.loads(raw)
        if raw in ("true", "false"):
            return raw == "true"
        if raw == "null":
            return None
        if re.fullmatch(r"-?\d+", raw):
            return int(raw)
        if re.fullmatch(r"-?\d+\.\d+", raw):
            return float(raw)
        raise HoconError(f"valor sin comillas no soportado: {raw!r}")


def parse_hocon(text: str) -> dict:
    return Parser(tokenize(text)).parse_root()


def main(argv: list[str]) -> int:
    status = 0
    for name in argv:
        try:
            print(json.dumps({name: parse_hocon(Path(name).read_text(encoding="utf-8"))}, ensure_ascii=False))
        except HoconError as exc:
            print(f"ERROR {name}: {exc}")
            status = 1
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
