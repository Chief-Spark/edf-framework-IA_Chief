"""Pruebas de hocon_check.py con HOCON sintéticos, válidos y malformados (plan #4, CP-ACC-S-06).

Recorren las ramas del parser mínimo que el HOCON del ejemplo no ejercita: errores de tokenización y de
sintaxis, claves con puntos o entre comillas, fusión de objetos, valores escalares y la línea de comandos.
Ejecutar desde la raíz del skill:  python -m pytest scripts/tests -q
"""
from __future__ import annotations

import json
import runpy
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS))

import hocon_check  # noqa: E402
from hocon_check import HoconError, parse_hocon  # noqa: E402


# ------------------------------------------------------------------ HOCON válidos

def test_objeto_raiz_con_llaves_comentarios_y_separadores():
    text = '{\n  # comentario\n  a = 1, b: "x" // otro\n  c { d = true }\n}\n'
    assert parse_hocon(text) == {"a": 1, "b": "x", "c": {"d": True}}


def test_clave_con_puntos_crea_objetos_anidados():
    assert parse_hocon("a.b.c = 1\na.b.d = 2") == {"a": {"b": {"c": 1, "d": 2}}}


def test_clave_entre_comillas_no_se_parte_por_los_puntos():
    assert parse_hocon('"a.b" = 1') == {"a.b": 1}


def test_objetos_repetidos_se_fusionan():
    assert parse_hocon("a { x = 1 }\na { y = 2 }") == {"a": {"x": 1, "y": 2}}


def test_valor_no_objeto_repetido_gana_el_ultimo():
    assert parse_hocon("a = 1\na = 2") == {"a": 2}


@pytest.mark.parametrize("raw, esperado", [
    ("null", None), ("true", True), ("false", False), ("-5", -5), ("42", 42), ("1.5", 1.5), ("-0.90", -0.9),
    ('"con \\"escape\\" y \\\\d"', 'con "escape" y \\d'),
])
def test_valores_escalares(raw, esperado):
    assert parse_hocon(f"v = {raw}") == {"v": esperado}


def test_listas_anidadas_y_vacias():
    assert parse_hocon('l = [ [1, 2], [], { k = "v" } ]') == {"l": [[1, 2], [], {"k": "v"}]}


def test_texto_vacio_da_objeto_vacio():
    assert parse_hocon("  \n# solo comentario\n") == {}


# ------------------------------------------------------------------ HOCON malformados

@pytest.mark.parametrize("text, mensaje", [
    ("a = 1\nb = ${a}", "carácter no soportado en línea 2"),       # sustitución: no se soporta
    ('a = "sin cerrar', "carácter no soportado en línea 1"),        # cadena sin cierre
    ("a = [1, 2", "se esperaba ']'"),                               # lista sin cerrar
    ("{ a = 1", "se esperaba '}'"),                                 # objeto sin cerrar
    ("{ a = 1 } b = 2", "contenido sobrante"),                      # algo después del objeto raíz
    ("= 1", "clave inválida"),                                      # falta la clave
    ("a 1", "falta '=' tras la clave 'a'"),                         # falta el separador
    ("a =", "valor ausente"),                                       # falta el valor
    ("a = abc", "valor sin comillas no soportado: 'abc'"),          # cadena sin comillas
    ("a = 1.2.3", "valor sin comillas no soportado"),               # número mal formado
    ("a = 1 }", "clave inválida"),                                  # llave de cierre sobrante
])
def test_hocon_malformado_falla_con_hocon_error(text, mensaje):
    with pytest.raises(HoconError, match=None) as exc:
        parse_hocon(text)
    assert mensaje in str(exc.value)


def test_take_sin_valor_esperado_en_el_final():
    parser = hocon_check.Parser(hocon_check.tokenize(""))
    with pytest.raises(HoconError, match="llegó None"):
        parser.take()


# ------------------------------------------------------------------ línea de comandos

def test_main_imprime_json_y_sale_con_cero(tmp_path, capsys):
    ok = tmp_path / "ok.conf"
    ok.write_text('oxygen.stage { name = "SINT" }\n', encoding="utf-8")
    assert hocon_check.main([str(ok)]) == 0
    assert json.loads(capsys.readouterr().out) == {str(ok): {"oxygen": {"stage": {"name": "SINT"}}}}


def test_main_informa_el_error_y_sale_con_uno(tmp_path, capsys):
    ok = tmp_path / "ok.conf"
    ok.write_text("a = 1\n", encoding="utf-8")
    malo = tmp_path / "malo.conf"
    malo.write_text("a = ${x}\n", encoding="utf-8")
    assert hocon_check.main([str(ok), str(malo)]) == 1
    lineas = capsys.readouterr().out.splitlines()
    assert json.loads(lineas[0]) == {str(ok): {"a": 1}}
    assert lineas[1].startswith(f"ERROR {malo}: carácter no soportado en línea 1")


def test_ejecutado_como_script(tmp_path, monkeypatch, capsys):
    malo = tmp_path / "malo.conf"
    malo.write_text("a\n", encoding="utf-8")
    monkeypatch.setattr(sys, "argv", ["hocon_check.py", str(malo)])
    with pytest.raises(SystemExit) as exc:
        runpy.run_path(str(SCRIPTS / "hocon_check.py"), run_name="__main__")
    assert exc.value.code == 1
    assert "falta '=' tras la clave 'a'" in capsys.readouterr().out
