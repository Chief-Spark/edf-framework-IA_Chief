"""Parser HOCON mínimo + guardarraíles de portabilidad y seguridad del skill completo."""

import re

import pytest
import yaml

import hocon_check
from conftest import SKILL


def test_hocon_subconjunto():
    texto = '''
    # comentario
    a.b = 1
    a { c = "x\\\\y", d = [1, 2, ] }   // otro
    e = ${?BASE}"/ruta"
    f += { g = true }
    '''
    conf = hocon_check.parse(texto)
    assert conf["a"] == {"b": 1, "c": "x\\y", "d": [1, 2]}
    assert conf["e"] == "${?BASE}/ruta"
    assert conf["f"] == [{"g": True}]


@pytest.mark.parametrize("malo", ['a = "sin cerrar', "a { b = 1", "a = [1, 2", "= 1"])
def test_hocon_errores(malo):
    with pytest.raises(hocon_check.HoconError):
        hocon_check.parse(malo)


def test_main_informa_ok_y_fail(tmp_path, capsys):
    # CLI del paso 4 de SKILL.md: código 0 si todo parsea, 1 si algún archivo falla o no existe (ADR-04).
    bueno, malo = tmp_path / "bueno.conf", tmp_path / "malo.conf"
    bueno.write_text('a { b = "x" }\n', encoding="utf-8")
    malo.write_text("a { b = 1\n", encoding="utf-8")
    assert hocon_check.main([str(bueno)]) == 0
    assert hocon_check.main([str(bueno), str(malo), str(tmp_path / "no_existe.conf")]) == 1
    salida = capsys.readouterr().out
    assert salida.count("OK   ") == 2 and salida.count("FAIL ") == 2


def test_pyhocon_si_esta_disponible():
    pyhocon = pytest.importorskip("pyhocon")
    for f in (SKILL / "assets/examples/sisben/generated/fragmentos").glob("*.conf"):
        pyhocon.ConfigFactory.parse_string(f.read_text(encoding="utf-8"), resolve=False)


def _archivos_texto():
    # target/ es salida de Maven (ignorada por git): sus reportes llevan rutas de la máquina.
    ignorar = {"__pycache__", ".pytest_cache", "target"}
    for f in SKILL.rglob("*"):
        if f.is_file() and not ignorar & set(f.parts) and f.suffix != ".pyc":
            yield f


def test_yaml_del_skill_parsea():
    for f in SKILL.rglob("*.yaml"):
        yaml.safe_load(f.read_text(encoding="utf-8"))


# Patrones que delatarían rutas del workspace de origen o de un equipo concreto.
RUTAS_PROHIBIDAS = [
    r"[A-Za-z]:\\Users\\", r"/Users/[a-z0-9]", r"/home/[a-z]", r"Ingesta_y_limpieza", r"RentAdvisor",
    r"Discovery/", r"Discovery\\", r"aidlc-docs", r"\.agents/", r"Product-Definition/", r"\.kiro/",
]
# Patrones de credenciales.
SECRETOS = [
    r"<password>", r"AKIA[0-9A-Z]{16}", r"(?i)aws_secret_access_key\s*=", r"(?i)password\s*[:=]\s*\S+",
    r"-----BEGIN [A-Z ]*PRIVATE KEY-----", r"(?i)\btoken\s*[:=]\s*['\"][A-Za-z0-9]{16,}",
]


def test_sin_rutas_del_workspace_ni_credenciales():
    hallazgos = []
    propio = re.escape(__file__.replace("\\", "/").split("/")[-1])
    for f in _archivos_texto():
        if re.search(propio, f.as_posix()):
            continue  # este archivo contiene los patrones a propósito
        texto = f.read_text(encoding="utf-8", errors="ignore")
        for p in RUTAS_PROHIBIDAS + SECRETOS:
            if re.search(p, texto):
                hallazgos.append((f.relative_to(SKILL).as_posix(), p))
    assert not hallazgos, hallazgos


def test_no_incluye_settings_artifactory():
    assert not [f for f in SKILL.rglob("*") if f.name.lower() == "settings-artifactory.xml"]


def test_fixtures_marcados_como_sinteticos():
    fx = (SKILL / "assets/examples/sisben/fixture/accepted-events.input.yaml").read_text(encoding="utf-8")
    assert "SINTÉTICO" in fx
    # documentos sintéticos: el núcleo numérico (sin espacios, letras ni ceros a la izquierda) empieza con 999
    docs = re.findall(r"NUM_DOCUMENTO: \"([^\"]+)\"", fx)
    assert docs and all(re.sub(r"\D", "", d).lstrip("0").startswith("999") for d in docs)
    # lookups (plan #9, AC-30): la carpeta del fixture y la que se entrega al fork llevan la marca aparte
    for carpeta in ("assets/examples/sisben/fixture/lookups", "assets/examples/sisben/generated/src/test/resources/sisben/lookups"):
        lk = SKILL / carpeta
        assert list(lk.glob("*.csv")), carpeta
        assert "SINTÉTICO" in (lk / "LEEME.md").read_text(encoding="utf-8"), carpeta
