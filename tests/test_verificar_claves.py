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


CONTRATO_HF37 = """# Contrato

## 1. Claves que Pinning consume

| Clave publicada | Tipo |
|---|---|
| `standardizedIdType` | entero |
| `standardizedIdNumber` | texto |

## 2. Lo que Pinning usa fuera de `curatedRecordJson`

| Elemento | Valor |
|---|---|
| `subscriberRelation` | `<tipo>_<numero>` |
| `subscriber` | 999722 |
| `otraClaveDeOtraTabla` | no es del §1 |

## 3. Notas

| `advertenciasCuracion` | lista |
"""


def test_contrato_solo_lee_la_tabla_del_paragrafo_1_hf37(base, capsys):
    """HF-37 (AUD-C-08): las tablas fuera del §1 no son citas; con la versión anterior daba rc=1 con 3 NO EXISTE."""
    (base / "contrato-curation-pinning.md").write_text(CONTRATO_HF37, encoding="utf-8")
    codigo, out = correr(base, capsys)
    assert codigo == 0, out
    assert "NO EXISTE" not in out and "citas revisadas: 2" in out
    assert vc.claves_contrato(CONTRATO_HF37) == ["standardizedIdType", "standardizedIdNumber"]


def test_contrato_exime_inyectados_en_el_paragrafo_1_hf37(base, capsys):
    (base / "contrato-curation-pinning.md").write_text(
        "## 1. Claves\n\n| Clave |\n|---|\n| `standardizedIdType` |\n| `subscriberRelation` |\n| `subscriber` |\n",
        encoding="utf-8")
    codigo, out = correr(base, capsys)
    assert codigo == 0 and "citas revisadas: 1" in out, out


def test_contrato_sin_seccion_1_lee_solo_la_primera_tabla_hf37():
    texto = "| `standardizedIdType` | a |\n|---|---|\n\nTexto.\n\n| `claveDeOtraTabla` | b |\n"
    assert vc.claves_contrato(texto) == ["standardizedIdType"]


def test_campo_advertencias_cuenta_como_clave_publicada_hf37(tmp_path, capsys):
    escribir(tmp_path, "source-spec-curation.yaml", {**CURATION, "parametros": {"campo_advertencias": "advertenciasCuracion"}})
    escribir(tmp_path, "yaml-skill-statecalc.yaml", {"mapeo_campos": {"campos": [
        {"campo_curado_sisben_origen": "advertenciasCuracion", "nivel_confianza": "A"}]}})
    codigo, out = correr(tmp_path, capsys)
    assert codigo == 0 and "Claves publicadas por Curation: 4" in out, out
    # Sin parametros.campo_advertencias la misma cita sí falla.
    escribir(tmp_path, "source-spec-curation.yaml", CURATION)
    codigo, out = correr(tmp_path, capsys)
    assert codigo == 1 and "'advertenciasCuracion'" in out


# ── HF-70 (AUD-S defecto 5): una carpeta de insumos inexistente no es «nada que cruzar» ──

def test_hf70_ingesta_sin_expediente_falla_con_2(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr(vc, "RAIZ", tmp_path)
    assert vc.main(["sisben"]) == 2
    out = capsys.readouterr().out
    assert "No existe" in out and "--dir" in out and "nada que cruzar" not in out


def test_hf70_expediente_sin_carpeta_de_insumos_falla_con_2(tmp_path, monkeypatch, capsys):
    """El caso de la corrida: expedientes/sisben/ existe en la raíz (fuentes/, referencia/) pero sin insumos/."""
    (tmp_path / "expedientes" / "sisben" / "fuentes").mkdir(parents=True)
    monkeypatch.setattr(vc, "RAIZ", tmp_path)
    assert vc.main(["sisben"]) == 2
    out = capsys.readouterr().out
    assert "insumos" in out and "nada que cruzar" not in out


def test_hf70_carpeta_de_insumos_vacia_es_nada_que_cruzar(tmp_path, monkeypatch, capsys):
    (tmp_path / "expedientes" / "sisben" / "insumos").mkdir(parents=True)
    monkeypatch.setattr(vc, "RAIZ", tmp_path)
    assert vc.main(["sisben"]) == 0
    assert "nada que cruzar" in capsys.readouterr().out


def test_hf70_dir_inexistente_falla_con_2(tmp_path, capsys):
    assert vc.main(["--dir", str(tmp_path / "no-existe")]) == 2
    assert "nada que cruzar" not in capsys.readouterr().out
    assert vc.main(["--dir"]) == 2


def test_hf70_los_dos_verificadores_documentan_dir_y_fallan_sin_insumo(tmp_path, monkeypatch, capsys):
    import importlib.util as iu
    spec = iu.spec_from_file_location("verificar_insumo_pinning_hf70", RAIZ / "tools" / "verificar-insumo-pinning.py")
    vp = iu.module_from_spec(spec)
    spec.loader.exec_module(vp)
    monkeypatch.setattr(vp, "RAIZ", tmp_path)
    assert vp.main(["sisben"]) == 2 and "--dir" in capsys.readouterr().out
    for mod in (vc, vp):
        assert "--dir <carpeta-con-los-insumos>" in mod.__doc__ and "fuera de la raíz" in mod.__doc__


# ── HF-71 (AUD-S defecto 4): forma de campos_a_generar (name + description) y alias deprecado ──

def test_hf71_campos_a_generar_acepta_nombre_en_gdd_como_alias_deprecado(base, capsys):
    escribir(base, "yaml-skill-synthesis.yaml", {"instruccion_generacion_gdd": {"contenido_a_escribir": {
        "campos_a_generar": [{"nombre_en_gdd": "grupoSISBEN", "handler": None},
                             {"name": "grupoSisben", "description": "grupo"}]}}})
    codigo, out = correr(base, capsys)
    # Antes la entrada con nombre_en_gdd se ignoraba sin aviso y el resultado era coherente (rc 0).
    assert codigo == 1 and "'grupoSISBEN'" in out and "(campos_a_generar)" in out
    assert "nombre_en_gdd` (deprecado)" in out and "campos_a_generar[0]" in out


def test_hf71_campos_a_generar_sin_name_avisa(base, capsys):
    escribir(base, "yaml-skill-synthesis.yaml", {"instruccion_generacion_gdd": {"contenido_a_escribir": {
        "campos_a_generar": [{"handler": "LAP", "description": "sin nombre"}, {"name": "", "description": "", "handler": None}]}}})
    codigo, out = correr(base, capsys)
    assert codigo == 0 and "campos_a_generar[0] sin `name`" in out and "campos_a_generar[1]" not in out


def test_hf71_fixture_jurad_usa_name_y_description():
    fixture = yaml.safe_load((RAIZ / ".kiro/skills/edf-synthesis/evals/fixtures/"
                              "yaml-skill-synthesis-jurad-sintetico.yaml").read_text(encoding="utf-8"))
    gen = fixture["instruccion_generacion_gdd"]["contenido_a_escribir"]["campos_a_generar"]
    assert [c["name"] for c in gen] == ["filingDate", "caseStatus", "caseNumber"]
    assert all(c.get("description") and "nombre_en_gdd" not in c for c in gen)
    assert vc.avisos_synthesis(fixture) == []
    assert {n for _, n, _ in vc.citas_synthesis(fixture)} >= {"filingDate", "caseStatus", "caseNumber"}
