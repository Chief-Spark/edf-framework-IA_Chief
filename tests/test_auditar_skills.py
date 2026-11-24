"""Pruebas de tools/auditar-skills.py con skills sintéticas."""
import importlib.util
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
_spec = importlib.util.spec_from_file_location("auditar_skills", RAIZ / "tools" / "auditar-skills.py")
aud = importlib.util.module_from_spec(_spec)
sys.modules["auditar_skills"] = aud  # dataclasses lo necesita
_spec.loader.exec_module(aud)


def _skill(tmp_path: Path, archivos: dict[str, str]) -> Path:
    for rel, texto in archivos.items():
        f = tmp_path / ".kiro" / "skills" / "edf-x" / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(texto, encoding="utf-8")
    return tmp_path


def test_silencio_sin_marca_se_reporta_y_con_marca_no(tmp_path):
    raiz = _skill(tmp_path, {"assets/templates/A.scala.tpl": "\n".join([
        "val a = Try(f()).getOrElse(false)",
        "// SILENCIO-INTENCIONAL VAL-RC03: el nombre no coincide",
        "val b = Try(g()).toOption",
        "val c = Try(h()).toOption  // SILENCIO-INTENCIONAL D13",
        "} catch { case NonFatal(_) => vacio }",
        "val d = Option(x).getOrElse(\"\")",  # guarda de nulo: no es un silencio
    ])})
    hall = [h for h in aud.auditar(raiz) if h.barrido == "silencios"]
    assert [h.linea for h in hall] == [1, 5]
    assert aud.main(["--raiz", str(raiz)]) == 1


def test_las_pruebas_y_el_java_deprecado_no_cuentan(tmp_path):
    raiz = _skill(tmp_path, {
        "scripts/tests/test_x.py": "Try(f()).getOrElse(0)",
        "scripts/java/src/X.scala": "Try(f()).getOrElse(0)",
        "assets/examples/sisben/generated/Y.scala": "Try(f()).getOrElse(0)",
    })
    assert aud.auditar(raiz) == []


def test_referencias_obsoletas(tmp_path):
    raiz = _skill(tmp_path, {
        "SKILL.md": "\n".join([
            "Corre `java -jar scripts/java/target/render-templates.jar`.",
            "",
            "La carpeta `scripts/java/` es el generador anterior, deprecado.",
            "",
            "Nunca ejecutes `java -jar`.",
        ]),
        "references/x.md": "Ver `RenderTemplatesTest.java`.",
        "scripts/DEPRECADOS.md": "scripts/java y java -jar",
    })
    hall = [h for h in aud.auditar(raiz) if h.barrido == "obsoletas"]
    assert [(h.ruta, h.linea) for h in hall] == [("SKILL.md", 1), ("references/x.md", 1)]


def test_decisiones_huerfanas_son_informativas(tmp_path):
    raiz = _skill(tmp_path, {
        "references/decisiones-y-abiertos.md": "| ID | Tema |\n|---|---|\n| VAL-SP01 | algo |\n| ADR-01b | otro |\n",
        "assets/templates/A.scala.tpl": "// ver VAL-SP01",
    })
    hall = aud.auditar(raiz)
    assert [(h.barrido, h.detalle.split()[0]) for h in hall] == [("huerfanas", "ADR-01b")]
    assert aud.main(["--raiz", str(raiz)]) == 0


def test_filtro_por_skill_y_raiz_invalida(tmp_path):
    raiz = _skill(tmp_path, {"assets/templates/A.scala.tpl": "Try(f()).getOrElse(1)"})
    assert aud.auditar(raiz, "edf-otra") == []
    try:
        aud.auditar(tmp_path / "no-existe")
    except SystemExit as e:
        assert "no existe" in str(e)
    else:
        raise AssertionError("debía fallar")
