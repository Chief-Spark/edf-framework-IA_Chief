"""Pruebas de tools/verificar-insumo-pinning.py (HF-60). Datos sintéticos: el fixture de modo Colombia de
edf-pinning/evals y un contrato mínimo escrito en tmp_path."""
import copy
import importlib.util
import sys
from pathlib import Path

import pytest
import yaml

RAIZ = Path(__file__).resolve().parent.parent
_spec = importlib.util.spec_from_file_location("verificar_insumo_pinning", RAIZ / "tools" / "verificar-insumo-pinning.py")
vip = importlib.util.module_from_spec(_spec)
sys.modules["verificar_insumo_pinning"] = vip
_spec.loader.exec_module(vip)

FIXTURE = RAIZ / ".kiro" / "skills" / "edf-pinning" / "evals" / "fixtures" / "yaml-skill-pinning-colombia-sintetico.yaml"
CONTRATO = """# Contrato sintético

## 1. Claves que Pinning consume

| Clave publicada en `curatedRecordJson` | Tipo publicado | ¿Puede faltar? | Normalización | PII | Slot | Estado |
|---|---|---|---|---|---|---|
| `standardizedIdType` | entero | se omite | — | Sí | misma | `[C]` |
| `standardizedIdNumber` | texto | se omite | — | Sí | misma | `[C]` |
| `standardizedIdName` | texto | se omite | — | Sí | misma | `[C]` |

## 2. Lo que Pinning usa fuera de `curatedRecordJson`

| Elemento | Valor |
|---|---|
| `subscriberRelation` | x |
"""


@pytest.fixture
def base():
    return yaml.safe_load(FIXTURE.read_text(encoding="utf-8"))


def entrada(insumo, clave):
    return next(e for e in insumo["claves_configuracion_pinning"]["bloque_oxygen_stage_pinning"]
                if e["clave"].endswith("." + clave))


def correr(insumo, contrato=CONTRATO, texto=None):
    return vip.verificar(insumo, texto if texto is not None else yaml.safe_dump(insumo, allow_unicode=True), contrato)


def test_fixture_de_modo_colombia_pasa(base):
    errores, avisos = correr(base)
    assert errores == [] and avisos == []


def test_cli_ok_y_error(tmp_path, capsys, base):
    (tmp_path / "yaml-skill-pinning.yaml").write_text(yaml.safe_dump(base, allow_unicode=True), encoding="utf-8")
    (tmp_path / "contrato-curation-pinning.md").write_text(CONTRATO, encoding="utf-8")
    assert vip.main(["--dir", str(tmp_path)]) == 0
    assert "insumo de Pinning OK" in capsys.readouterr().out
    entrada(base, "reusePin")["valor_a_escribir"] = False
    (tmp_path / "yaml-skill-pinning.yaml").write_text(yaml.safe_dump(base, allow_unicode=True), encoding="utf-8")
    assert vip.main(["--dir", str(tmp_path)]) == 1
    assert vip.main(["--archivo", str(tmp_path / "no-existe.yaml")]) == 2


def test_hf57_reuse_pin_booleano_en_modo_colombia_es_error(base):
    for valor in (False, True, "false", "reusePin = false"):
        insumo = copy.deepcopy(base)
        entrada(insumo, "reusePin")["valor_a_escribir"] = valor
        errores, _ = correr(insumo)
        assert len(errores) == 1 and "HF-57" in errores[0] and "global" in errores[0], valor


def test_hf57_reuse_pin_sin_valor_en_fuente_es_aviso(base):
    del entrada(base, "reusePin")["valor_en_fuente"]
    errores, avisos = correr(base)
    assert errores == [] and any("valor_en_fuente" in a for a in avisos)


def test_hf57_fuera_de_modo_colombia_reuse_pin_se_puede_escribir(base):
    base["modo_colombia"]["aplica"] = False
    entrada(base, "reusePin")["valor_a_escribir"] = "false"
    assert correr(base)[0] == []


def test_hf59_clave_compartida_redefinida_en_modo_colombia_es_error(base):
    entrada(base, "identifyingAttributesFile")["valor_a_escribir"] = '["identifying-attributes-new.json"]'
    errores, _ = correr(base)
    assert len(errores) == 1 and "HF-59" in errores[0]


def test_hf58_matcher_o_lista_de_referencia_en_modo_colombia_es_error(base):
    insumo = copy.deepcopy(base)
    insumo["modo_colombia"]["identity_matcher"] = "com.experian.edf.oxygen.pinning.extension.ISIdentityMatcher"
    assert any("HF-58" in e for e in correr(insumo)[0])
    insumo = copy.deepcopy(base)
    entrada(insumo, "identityMatcher")["valor_a_escribir"] = "com.experian.edf.oxygen.pinning.extension.ISIdentityMatcher"
    assert any("HF-58" in e for e in correr(insumo)[0])
    insumo = copy.deepcopy(base)
    insumo["mapeo_identidad"]["atributos"].append({"atributo_motor": "nationalId",
                                                    "campo_curado_colsint": "standardizedIdNumber", "estado": "[C]"})
    assert any("HF-58" in e and "nationalId" in e for e in correr(insumo)[0])


def test_colombia_no_regenera_lista_ni_pesos(base):
    base["instruccion_generacion"]["contenido_a_escribir"]["valor_resultante"] = ["standardizedIdType"]
    base["reglas_matching_por_campo"]["reglas"] = [{"campo": "standardizedIdType", "peso_doesMatch": 0.5}]
    errores = correr(base)[0]
    assert any("valor_resultante debe ser []" in e for e in errores)
    assert any("reglas_matching_por_campo.reglas" in e for e in errores)


def test_valor_resultante_solo_atributos_c_y_sin_supuestos_abiertos(base):
    base["modo_colombia"]["aplica"] = False
    base["instruccion_generacion"]["contenido_a_escribir"]["valor_resultante"] = [
        "standardizedIdType", "otroSlot", "[A:PA-1] slot"]
    errores = correr(base)[0]
    assert any("«otroSlot» no es un atributo [C]" in e for e in errores)
    assert any("depende de un [A:*]" in e for e in errores)
    assert not any("«standardizedIdType»" in e for e in errores)


def test_secciones_y_cabecera(base):
    del base["comportamiento_del_motor_a_tener_en_cuenta"]
    base["documento"]["estado"] = "PROPUESTO"
    errores = correr(base)[0]
    assert any("comportamiento_del_motor_a_tener_en_cuenta" in e for e in errores)
    assert any("documento.estado" in e and "HF-32" in e for e in errores)
    assert set(vip.secciones_plantilla()) >= {"documento", "modo_colombia", "claves_configuracion_pinning"}


def test_coherencia_con_el_contrato(base):
    contrato = CONTRATO.replace("| `standardizedIdName` | texto | se omite | — | Sí | misma | `[C]` |\n", "")
    errores = correr(base, contrato=contrato)[0]
    assert any("«standardizedIdName»" in e and "contrato" in e for e in errores)
    contrato = CONTRATO.replace("## 2.", "| `bandera` | texto | x | x | No | x | `[C]` |\n\n## 2.")
    errores, avisos = correr(base, contrato=contrato)
    assert errores == [] and any("«bandera»" in a for a in avisos)
    errores, avisos = correr(base, contrato=None)
    assert errores == [] and any("no hay contrato" in a for a in avisos)


def test_hf61_cita_de_event_pinner_es_aviso(base):
    texto = yaml.safe_dump(base, allow_unicode=True) + "# null lanza: EventPinner.scala:64-65\n"
    errores, avisos = correr(base, texto=texto)
    assert errores == [] and any("ISIdentityMatcher.scala:46-54" in a for a in avisos)
