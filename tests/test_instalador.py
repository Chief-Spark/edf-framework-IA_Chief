"""Prueba de instalar.sh (AUD-O2 y AUD-O5): copia desde `.kiro/` y deja los hooks con un intérprete que existe.

Ejecuta el comando del hook TAL COMO QUEDA ESCRITO en el workspace instalado (sin reemplazar el intérprete),
porque un intérprete inexistente hace que la compuerta de la etapa 0 falle abierta. instalar.ps1 hace lo mismo
en Windows (prueba `py -3`, `python` y `python3`), pero no se puede ejecutar en este entorno.
"""
import json
import os
import shutil
import subprocess
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parent.parent
pytestmark = pytest.mark.skipif(shutil.which("bash") is None or os.name == "nt", reason="requiere bash")


@pytest.fixture(scope="module")
def workspace(tmp_path_factory):
    ws = tmp_path_factory.mktemp("ws")
    r = subprocess.run(["bash", str(REPO / "instalar.sh"), str(ws)], capture_output=True, text=True)
    assert r.returncode == 0, r.stderr
    assert "hooks configurados con" in r.stdout
    return ws


def _comando(ws, trigger):
    datos = json.loads((ws / ".kiro/hooks/compuerta-etapa0.json").read_text(encoding="utf-8"))
    return next(h["action"]["command"] for h in datos["hooks"] if h["trigger"] == trigger)


def test_copia_la_configuracion_desde_punto_kiro(workspace):
    for d in ("steering", "skills", "hooks", "specs"):
        assert any((workspace / ".kiro" / d).iterdir()), d
    assert (workspace / "tools" / "validar-expediente.py").is_file()


def test_el_hook_instalado_usa_un_interprete_que_existe(workspace):
    lanzador = _comando(workspace, "PromptSubmit").split()[0]
    assert shutil.which(lanzador), lanzador
    texto = (workspace / ".kiro/hooks/validar-expediente.json").read_text(encoding="utf-8")
    assert f"`{lanzador} tools/validar-expedientes.py" in texto


def test_el_hook_instalado_bloquea_sin_fuentes_y_pasa_con_ellas(workspace):
    env = dict(os.environ, USER_PROMPT="inicia la ingesta demo")
    cmd = _comando(workspace, "PromptSubmit")
    r = subprocess.run(cmd, shell=True, cwd=workspace, env=env, capture_output=True, text=True)
    assert r.returncode == 2 and "Falta: HU, spec." in r.stderr
    fuentes = workspace / "expedientes/demo/fuentes"
    fuentes.mkdir(parents=True)
    (fuentes / "HU-DEMO.md").write_text("hu", encoding="utf-8")
    (fuentes / "spec-demo.md").write_text("spec", encoding="utf-8")
    assert subprocess.run(cmd, shell=True, cwd=workspace, env=env, capture_output=True).returncode == 0
