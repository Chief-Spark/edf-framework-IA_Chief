"""Pruebas de tools/verificar-insumos-etapa0.py (compuerta de entrada de la etapa 0).

Uso:  python -m unittest discover -s tests -v
"""
import importlib.util
import json
import pathlib
import tempfile
import unittest

TOOL = pathlib.Path(__file__).resolve().parent.parent / "tools" / "verificar-insumos-etapa0.py"
spec = importlib.util.spec_from_file_location("verificar_insumos_etapa0", TOOL)
vi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vi)


class Base(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.raiz = pathlib.Path(self._tmp.name)
        (self.raiz / "expedientes").mkdir()

    def fuentes(self, ingesta="demo"):
        d = self.raiz / "expedientes" / ingesta / "fuentes"
        d.mkdir(parents=True, exist_ok=True)
        return d

    def escribir(self, ruta, texto="contenido"):
        ruta.parent.mkdir(parents=True, exist_ok=True)
        ruta.write_text(texto, encoding="utf-8")
        return ruta


class TestFaltantes(Base):
    def test_ambos_presentes(self):
        d = self.fuentes()
        self.escribir(d / "HU-DEMO-001.md")
        self.escribir(d / "spec-confluence.md")
        self.assertEqual(vi.verificar(self.raiz, "demo")[0], 0)

    def test_falta_hu(self):
        self.escribir(self.fuentes() / "spec-confluence.md")
        codigo, msg = vi.verificar(self.raiz, "demo")
        self.assertEqual(codigo, 2)
        self.assertIn("Falta: HU.", msg)

    def test_falta_spec(self):
        self.escribir(self.fuentes() / "HU-DEMO-001.md")
        codigo, msg = vi.verificar(self.raiz, "demo")
        self.assertEqual(codigo, 2)
        self.assertIn("Falta: spec.", msg)

    def test_faltan_ambos_sin_carpeta(self):
        codigo, msg = vi.verificar(self.raiz, "demo")
        self.assertEqual(codigo, 2)
        self.assertIn("Falta: HU, spec.", msg)
        self.assertIn("expedientes/demo/fuentes/", msg)

    def test_archivo_vacio_no_cuenta(self):
        d = self.fuentes()
        self.escribir(d / "HU-DEMO-001.md", "")
        self.escribir(d / "spec.md")
        self.assertIn("Falta: HU.", vi.verificar(self.raiz, "demo")[1])

    def test_mayusculas_y_extension(self):
        d = self.fuentes()
        self.escribir(d / "hu_demo.docx")
        self.escribir(d / "SPEC Confluence.pdf")
        self.assertEqual(vi.verificar(self.raiz, "demo")[0], 0)

    def test_prefijo_exacto(self):
        d = self.fuentes()
        self.escribir(d / "Especificacion.md")  # no empieza por "spec"
        self.escribir(d / "Hu.md")
        self.assertIn("Falta: spec.", vi.verificar(self.raiz, "demo")[1])

    def test_mensaje_dice_arranca_o_continua(self):
        self.assertIn("no arranca", vi.verificar(self.raiz, "demo")[1])
        self.escribir(self.raiz / "expedientes" / "demo" / "estado-ingesta.json", "{}")
        self.assertIn("no continua", vi.verificar(self.raiz, "demo")[1])

    def test_manifiesto_con_rutas_validas(self):
        hu = self.escribir(self.raiz / "otra" / "HU-X.md")
        sp = self.escribir(self.raiz / "otra" / "spec-x.md")
        self.escribir(self.fuentes() / "rutas-fuentes.json", json.dumps({"hu": str(hu), "spec": str(sp)}))
        self.assertEqual(vi.verificar(self.raiz, "demo")[0], 0)

    def test_manifiesto_con_ruta_relativa(self):
        self.escribir(self.raiz / "Entendimiento" / "HU-X.md")
        self.escribir(self.raiz / "Entendimiento" / "spec-x.md")
        self.escribir(self.fuentes() / "rutas-fuentes.json",
                      json.dumps({"hu": "Entendimiento/HU-X.md", "spec": "Entendimiento/spec-x.md"}))
        self.assertEqual(vi.verificar(self.raiz, "demo")[0], 0)

    def test_manifiesto_con_ruta_inexistente(self):
        sp = self.escribir(self.raiz / "otra" / "spec-x.md")
        self.escribir(self.fuentes() / "rutas-fuentes.json",
                      json.dumps({"hu": str(self.raiz / "no-existe.md"), "spec": str(sp)}))
        codigo, msg = vi.verificar(self.raiz, "demo")
        self.assertEqual(codigo, 2)
        self.assertIn("Falta: HU.", msg)
        self.assertIn("no existe", msg)

    def test_manifiesto_con_bom(self):
        hu = self.escribir(self.raiz / "otra" / "HU-X.md")
        sp = self.escribir(self.raiz / "otra" / "spec-x.md")
        manifiesto = self.fuentes() / "rutas-fuentes.json"
        manifiesto.write_text(json.dumps({"hu": str(hu), "spec": str(sp)}), encoding="utf-8-sig")
        self.assertEqual(vi.verificar(self.raiz, "demo")[0], 0)

    def test_manifiesto_invalido(self):
        self.escribir(self.fuentes() / "rutas-fuentes.json", "no es json")
        codigo, msg = vi.verificar(self.raiz, "demo")
        self.assertEqual(codigo, 2)
        self.assertIn("rutas-fuentes.json", msg)

    def test_carpeta_y_manifiesto_se_combinan(self):
        sp = self.escribir(self.raiz / "otra" / "spec-x.md")
        d = self.fuentes()
        self.escribir(d / "HU-1.md")
        self.escribir(d / "rutas-fuentes.json", json.dumps({"spec": str(sp)}))
        self.assertEqual(vi.verificar(self.raiz, "demo")[0], 0)


class TestPrompt(Base):
    def test_prompt_sin_relacion(self):
        self.assertEqual(vi.evento_prompt(self.raiz, "explícame qué es Pinning"), (0, ""))
        self.assertEqual(vi.evento_prompt(self.raiz, "qué es una ingesta?"), (0, ""))

    def test_iniciar_ingesta_sin_fuentes_bloquea(self):
        codigo, msg = vi.evento_prompt(self.raiz, "inicia la ingesta demo")
        self.assertEqual(codigo, 2)
        self.assertIn("Falta: HU, spec.", msg)
        self.assertIn("`demo`", msg)

    def test_iniciar_con_fuentes_pasa(self):
        d = self.fuentes()
        self.escribir(d / "HU-1.md")
        self.escribir(d / "spec-1.md")
        self.assertEqual(vi.evento_prompt(self.raiz, "inicia la ingesta demo")[0], 0)

    def test_continuar_expediente_existente(self):
        (self.raiz / "expedientes" / "sisben-e2e").mkdir()
        codigo, msg = vi.evento_prompt(self.raiz, "continúa con sisben-e2e")
        self.assertEqual(codigo, 2)
        self.assertIn("`sisben-e2e`", msg)

    def test_etapa_0(self):
        codigo, _ = vi.evento_prompt(self.raiz, "analiza la ingesta Demo, etapa 0")
        self.assertEqual(codigo, 2)

    def test_intencion_sin_id_deja_pasar_con_aviso(self):
        codigo, msg = vi.evento_prompt(self.raiz, "quiero iniciar una ingesta")
        self.assertEqual(codigo, 0)
        self.assertIn("sin indicar su id", msg)

    def test_rutas_en_el_prompt_cuentan(self):
        hu = self.escribir(self.raiz / "otra" / "HU-X.md")
        sp = self.escribir(self.raiz / "otra" / "spec-x.md")
        prompt = f'inicia la ingesta demo. HU: "{hu}" y spec: "{sp}"'
        self.assertEqual(vi.evento_prompt(self.raiz, prompt)[0], 0)

    def test_ruta_en_prompt_solo_una(self):
        hu = self.escribir(self.raiz / "otra" / "HU-X.md")
        codigo, msg = vi.evento_prompt(self.raiz, f'inicia la ingesta demo con la HU "{hu}"')
        self.assertEqual(codigo, 2)
        self.assertIn("Falta: spec.", msg)


class TestPreTool(Base):
    def test_escritura_en_analisis_sin_fuentes_bloquea(self):
        evento = {"tool_name": "fs_write",
                  "tool_input": {"path": "expedientes/demo/analisis/contexto-ingesta.md", "text": "x"}}
        codigo, msg = vi.evento_pretool(self.raiz, evento)
        self.assertEqual(codigo, 2)
        self.assertIn("Falta: HU, spec.", msg)

    def test_crear_estado_ingesta_bloquea(self):
        evento = {"toolInput": {"file_path": "C:\\ws\\expedientes\\demo\\estado-ingesta.json"}}
        self.assertEqual(vi.evento_pretool(self.raiz, evento)[0], 2)

    def test_fuentes_esta_exento(self):
        for ruta in ("expedientes/demo/fuentes/rutas-fuentes.json",
                     "C:\\ws\\expedientes\\demo\\fuentes\\HU-1.md"):
            self.assertEqual(vi.evento_pretool(self.raiz, {"tool_input": {"path": ruta}}), (0, ""))

    def test_con_fuentes_permite(self):
        d = self.fuentes()
        self.escribir(d / "HU-1.md")
        self.escribir(d / "spec-1.md")
        evento = {"tool_input": {"path": "expedientes/demo/bitacora.md"}}
        self.assertEqual(vi.evento_pretool(self.raiz, evento)[0], 0)

    def test_fuera_de_expedientes_no_aplica(self):
        for ruta in ("tools/verificar-insumos-etapa0.py", "docs/arquitectura.md"):
            self.assertEqual(vi.evento_pretool(self.raiz, {"tool_input": {"path": ruta}}), (0, ""))

    def test_nombres_de_campo_distintos(self):
        for campo in ("path", "file_path", "filePath", "targetFile", "filename"):
            evento = {"tool_input": {campo: "expedientes/demo/bitacora.md"}}
            self.assertEqual(vi.evento_pretool(self.raiz, evento)[0], 2, campo)

    def test_sin_clave_conocida_usa_cadenas_cortas(self):
        evento = {"args": ["expedientes/demo/bitacora.md"]}
        self.assertEqual(vi.evento_pretool(self.raiz, evento)[0], 2)

    def test_contenido_con_ruta_no_dispara(self):
        evento = {"tool_input": {"path": "docs/nota.md",
                                 "text": "ver expedientes/demo/analisis/x.md\nlinea 2"}}
        self.assertEqual(vi.evento_pretool(self.raiz, evento), (0, ""))

    def test_str_replace_con_texto_de_una_linea_no_dispara(self):
        evento = {"tool_input": {"path": "docs/nota.md", "oldStr": "a", "newStr": "expedientes/demo/x.md"}}
        self.assertEqual(vi.evento_pretool(self.raiz, evento), (0, ""))


if __name__ == "__main__":
    unittest.main()
