"""Pruebas de tools/verificar-claves.py (coherencia de nombres de campo curado entre insumos). Datos sintéticos."""
import importlib.util
import sys
from pathlib import Path

import pytest
import yaml

RAIZ = Path(__file__).resolve().parent.parent
_spec = importlib.util.spec_from_file_location("verificar_claves", RAIZ / "tools" / "verificar-claves.py")
vc = importlib.util.module_from_spec(_spec)
sys.modules["verificar_claves"] = vc
_spec.loader.exec_module(vc)

CURATION = {"campos_curados": [{"destino": "standardizedIdType"}, {"destino": "standardizedIdNumber"},
                               {"destino": "grupoSisben"}],
            "campos_abiertos": [{"destino": "puntajeSisben"}]}


def escribir(base: Path, nombre: str, datos) -> None:
    (base / nombre).write_text(yaml.safe_dump(datos, allow_unicode=True), encoding="utf-8")


@pytest.fixture
def base(tmp_path):
    escribir(tmp_path, "source-spec-curation.yaml", CURATION)
    return tmp_path


def correr(base, capsys):
    codigo = vc.main(["--dir", str(base)])
    return codigo, capsys.readouterr().out


def test_sin_insumos_no_hay_nada_que_cruzar(tmp_path, capsys):
    assert correr(tmp_path, capsys)[0] == 0


def test_citas_aguas_abajo_sin_curation(tmp_path, capsys):
    escribir(tmp_path, "yaml-skill-synthesis.yaml", {})
    codigo, out = correr(tmp_path, capsys)
    assert codigo == 2 and "yaml-skill-synthesis.yaml" in out


def test_synthesis_nombre_en_gdd_errado_falla(base, capsys):
    escribir(base, "yaml-skill-synthesis.yaml", {"mapeo_campos_handler": {"campos_con_mapeo_confirmado": [
        {"campo_curado_sisben": "grupoSisben", "nombre_en_gdd": "grupoSISBEN", "estado": "[C] confirmado"}]}})
    codigo, out = correr(base, capsys)
    assert codigo == 1 and "grupoSISBEN" in out and "nombre_en_gdd" in out


def test_synthesis_campos_a_generar_y_entradas_no_confirmadas(base, capsys):
    escribir(base, "yaml-skill-synthesis.yaml", {
        "mapeo_campos_handler": {"campos_con_mapeo_confirmado": [
            {"campo_curado_sisben": "grupoSisbenX", "nombre_en_gdd": "[PM]", "estado": "[P] propuesta"}]},
        "instruccion_generacion_gdd": {"contenido_a_escribir": {"campos_a_generar": [
            {"name": "grupoSisben", "handler": "LFP"}, {"name": "fileTag", "handler": "LAP"}]}}})
    codigo, out = correr(base, capsys)
    assert codigo == 0                      # la entrada [P] no se genera: solo aviso; fileTag lo inyecta el motor
    assert "AVISO" in out and "grupoSisbenX" in out


def test_statecalc_derived_y_scripts(base, capsys):
    escribir(base, "yaml-skill-statecalc.yaml", {
        "mapeo_campos": {"campos": [
            {"campo_curado_sisben_origen": "standardizedIdType", "valor_calculated": "return field.standardizedIdTipo",
             "nivel_confianza": "A"}]},
        "instruccion_generacion_foundation": {"contenido_a_escribir": {"campos_a_generar": [
            {"name": "grupo", "type": "DERIVED", "value": "grupoSisben"},
            {"name": "numero", "type": "DERIVED", "value": "numeroDoc"},
            {"name": "sub", "type": "DERIVED", "value": "subscriber"},
            {"name": "calc", "type": "CALCULATED", "value": "return field.standardizedIdNumber"}]}}})
    codigo, out = correr(base, capsys)
    assert codigo == 1
    assert "standardizedIdTipo" in out and "numeroDoc" in out
    assert "'subscriber'" not in out and "'grupoSisben'" not in out


def test_pendientes_y_contrato(base, capsys):
    (base / "contrato-curation-pinning.md").write_text(
        "| Clave | Uso |\n|---|---|\n| `standardizedIdType` | p1 |\n| `puntajeSisben` | x |\n", encoding="utf-8")
    escribir(base, "yaml-skill-pinning.yaml", {"modo_colombia": {
        "aplica": True, "claves_obligatorias_en_evento_curado": ["standardizedIdType", "standardizedIdNumber"],
        "clave_opcional": "standardizedIdName"}})
    codigo, out = correr(base, capsys)
    assert codigo == 1 and "standardizedIdName" in out and "PENDIENTE" in out


def test_ejemplos_de_analisis_con_la_spec_de_curation(tmp_path, capsys):
    """Hallazgo conocido (informe de auditoría): el ejemplo de edf-curation no publica standardizedId* a
    propósito (SKILL.md de Curation), y los ejemplos de Pinning y State Calculation sí los citan. El cruce lo
    detecta; si alguien alinea los ejemplos, esta prueba debe actualizarse."""
    ej = RAIZ / ".kiro/skills/edf-analisis/assets/examples/sisben"
    for f in ej.glob("yaml-skill-*.yaml"):
        (tmp_path / f.name).write_bytes(f.read_bytes())
    (tmp_path / "source-spec-curation.yaml").write_bytes(
        (RAIZ / ".kiro/skills/edf-curation/assets/examples/sisben/source-spec.sisben.yaml").read_bytes())
    codigo, out = correr(tmp_path, capsys)
    assert codigo == 1
    assert "'standardizedIdNumber' citada en yaml-skill-pinning.yaml" in out
    assert "(foundation field.X)" in out
