"""Pruebas del hook .kiro/hooks/compuerta-etapa0.json (formato v1).

Ejecuta el comando exacto de cada hook, con la entrada que le da Kiro
(variable USER_PROMPT para PromptSubmit, JSON por STDIN para PreToolUse),
contra un workspace temporal (se agrega --raiz solo para aislar la prueba).

Uso:  python -m unittest discover -s tests -v
"""
import json
import os
import pathlib
import shlex
import subprocess
import sys
import tempfile
import unittest

REPO = pathlib.Path(__file__).resolve().parent.parent
HOOK = REPO / ".kiro" / "hooks" / "compuerta-etapa0.json"


class TestEsquemaDelHook(unittest.TestCase):
    def setUp(self):
        self.datos = json.loads(HOOK.read_text(encoding="utf-8"))

    def test_formato_v1(self):
        self.assertEqual(self.datos["version"], "v1")
        self.assertIsInstance(self.datos["hooks"], list)
        triggers = {h["trigger"] for h in self.datos["hooks"]}
        self.assertEqual(triggers, {"PromptSubmit", "PreToolUse"})

    def test_campos_de_cada_hook(self):
        for h in self.datos["hooks"]:
            self.assertTrue(h["name"])
            self.assertEqual(h["action"]["type"], "command")  # command: el codigo de salida bloquea
            self.assertIn("verificar-insumos-etapa0.py", h["action"]["command"])
            self.assertGreater(h["timeout"], 0)

    def test_pretool_cubre_las_herramientas_de_escritura(self):
        h = next(h for h in self.datos["hooks"] if h["trigger"] == "PreToolUse")
        for herramienta in ("fs_write", "str_replace", "fs_append"):
            self.assertIn(herramienta, h["matcher"])


class TestComandosDelHook(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.raiz = pathlib.Path(self._tmp.name)
        (self.raiz / "expedientes").mkdir()
        datos = json.loads(HOOK.read_text(encoding="utf-8"))
        self.cmd = {h["trigger"]: h["action"]["command"] for h in datos["hooks"]}

    def ejecutar(self, trigger, entrada=None, prompt=None):
        partes = shlex.split(self.cmd[trigger])
        partes[0] = sys.executable
        partes += ["--raiz", str(self.raiz)]
        env = dict(os.environ)
        env.pop("USER_PROMPT", None)
        if prompt is not None:
            env["USER_PROMPT"] = prompt
        return subprocess.run(
            partes, cwd=REPO, env=env, input=json.dumps(entrada) if entrada is not None else "",
            capture_output=True, text=True, encoding="utf-8",
        )

    def poner_fuentes(self, ingesta="demo"):
        d = self.raiz / "expedientes" / ingesta / "fuentes"
        d.mkdir(parents=True)
        (d / "HU-DEMO-001.md").write_text("hu", encoding="utf-8")
        (d / "spec-confluence.md").write_text("spec", encoding="utf-8")

    def test_prompt_iniciar_sin_fuentes_se_bloquea(self):
        r = self.ejecutar("PromptSubmit", prompt="inicia la ingesta demo")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("Falta: HU, spec.", r.stderr)

    def test_prompt_iniciar_con_fuentes_pasa(self):
        self.poner_fuentes()
        r = self.ejecutar("PromptSubmit", prompt="inicia la ingesta demo")
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_prompt_ajeno_pasa(self):
        r = self.ejecutar("PromptSubmit", prompt="resume el steering 04")
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_escritura_en_expediente_sin_fuentes_se_bloquea(self):
        evento = {"hook_event_name": "preToolUse", "tool_name": "fs_write",
                  "tool_input": {"path": "expedientes/demo/analisis/mapeo-campos.md", "text": "x"}}
        r = self.ejecutar("PreToolUse", entrada=evento)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("Falta: HU, spec.", r.stderr)

    def test_escritura_en_fuentes_se_permite(self):
        evento = {"tool_name": "fs_write",
                  "tool_input": {"path": "expedientes/demo/fuentes/rutas-fuentes.json", "text": "{}"}}
        self.assertEqual(self.ejecutar("PreToolUse", entrada=evento).returncode, 0)

    def test_escritura_en_expediente_con_fuentes_se_permite(self):
        self.poner_fuentes()
        evento = {"tool_name": "fs_append", "tool_input": {"path": "expedientes/demo/bitacora.md", "text": "x"}}
        self.assertEqual(self.ejecutar("PreToolUse", entrada=evento).returncode, 0)

    def test_escritura_fuera_de_expedientes_se_permite(self):
        evento = {"tool_name": "str_replace", "tool_input": {"path": "docs/arquitectura.md"}}
        self.assertEqual(self.ejecutar("PreToolUse", entrada=evento).returncode, 0)

    def test_sin_stdin_no_rompe(self):
        self.assertEqual(self.ejecutar("PreToolUse").returncode, 0)


if __name__ == "__main__":
    unittest.main()
