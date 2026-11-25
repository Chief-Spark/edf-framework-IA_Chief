"""Regresión del lote final de la corrida E2E SISBEN (etapa Curation): HF-45, HF-48 a HF-55. Datos sintéticos.

Ejecutar desde la raíz del skill:  python3 -m pytest scripts/tests -q
"""

import copy
import json
import re
from pathlib import Path

import pytest
import yaml

import curation_reference as ref
import render_templates as rt
from conftest import GENERATED, SCALA_FUENTE, SCHEMA, SKILL, SPEC, TEMPLATES

ARCHIVO = "SISBEN_EXPERIAN_202401.20240205103000-0500.txt"
SPEC_TEST = "src/test/scala/com/experian/edf/oxygen/curation/source/sisben/SISBENCurationProcessorSpec.scala"
TRANSF_SPEC = "src/test/scala/com/experian/edf/oxygen/curation/source/common/TransformacionesSpec.scala"
EXT = {"campos": ["tipoDocumento", "numeroDocumento"], "separador": "_"}


def leer(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def eventos_fixture(spec):
    fixture = rt.cargar_yaml(SPEC.parent / spec["fixture"]["entrada"])
    return rt.construir_eventos(spec, fixture)


def render_con(tmp_path, spec, cambio):
    fx = SPEC.parent / "fixture"
    for p in fx.rglob("*"):
        if p.is_file():
            d = tmp_path / "fixture" / p.relative_to(fx)
            d.parent.mkdir(parents=True, exist_ok=True)
            d.write_bytes(p.read_bytes())
    alt = copy.deepcopy(spec)
    cambio(alt)
    ruta = tmp_path / "spec.yaml"
    ruta.write_text(yaml.safe_dump(alt, allow_unicode=True, sort_keys=False), encoding="utf-8")
    out = tmp_path / "out"
    return rt.render(ruta, out, TEMPLATES, SCHEMA), out


# ------------------------------------------------------------------ HF-45 (AUD-C-14)
def test_hf45_resumen_separa_omision_por_severidad_y_por_nulas(spec):
    spec["campos_curados"].append({"destino": "standardizedIdSintetico", "origen": "", "tipo_salida": "texto",
                                   "estado": "cerrado", "transformacion": {"tipo": "constante", "valor": None}})
    _, resumen = rt.salida_esperada(spec, eventos_fixture(spec))
    por_sev, por_nulas = resumen["claves_omitidas_por_severidad"], resumen["claves_omitidas_por_nulas"]
    assert por_sev == {"evt-0004": ["segundoApellido"]}  # nivel 3 en niveles_no_almacenar (RF-CUR-10)
    assert por_nulas and all(v == ["standardizedIdSintetico"] for v in por_nulas.values())  # omitir_si_nulas
    assert not any("standardizedIdSintetico" in v for v in por_sev.values())


def test_hf45_sin_niveles_no_almacenar_nada_es_por_severidad(spec):
    spec["parametros"]["niveles_no_almacenar"] = []
    _, resumen = rt.salida_esperada(spec, eventos_fixture(spec))
    assert resumen["claves_omitidas_por_severidad"] == {} and resumen["claves_omitidas_por_nulas"] == {}
    base = json.loads(leer(GENERATED / "expected" / "resumen.json"))
    assert "claves_omitidas_por_nulas" in base


# ------------------------------------------------------------------ HF-48 (AUD-C-17)
def test_hf48_spec_scala_prueba_el_respaldo_de_subscriber_relation(tmp_path, spec):
    _, out = render_con(tmp_path, spec, lambda s: s.update(extension_subscriber_relation=EXT))
    t = leer(out / SPEC_TEST)
    bloque = t.split("caso sintético fijo, HF-48")[1].split("\n  }\n")[0]
    assert 'SISBENCurationProcessor.relacion(' in bloque and ', "COPIADO") == "COPIADO")' in bloque
    assert '.contains("subscriberRelation:COMPONENTE_FALTANTE"))' in bloque
    # El JSON sintético vacía un componente y la referencia confirma el respaldo.
    texto = re.search(r'relacion\("""(.*?)"""', bloque).group(1)
    curado = ref.curar(texto, ARCHIVO, [], spec | {"extension_subscriber_relation": EXT})
    assert "subscriberRelation:COMPONENTE_FALTANTE" in curado[spec["parametros"]["campo_advertencias"]]
    # No entra al fixture: la salida esperada no cambia de tamaño.
    assert len(json.loads(leer(out / "expected/curated-events.json"))) == len(
        json.loads(leer(GENERATED / "expected/curated-events.json")))


def test_hf48_sin_extension_no_hay_caso_sintetico(rendered):
    out, _ = rendered
    assert "HF-48" not in leer(out / SPEC_TEST)


# ------------------------------------------------------------------ HF-49, HF-50, HF-51
def test_hf49_comentario_de_severidad_sigue_la_configuracion():
    tpl = leer(TEMPLATES / "CurationProcessor.scala.tpl")
    assert "severidad 3 → no se almacena; severidad 0 → se anula" not in tpl
    assert "NivelesNoAlmacenar omite la clave y uno de NivelesAnular la anula" in tpl


def test_hf50_cita_del_runbook_existe():
    tpl = leer(TEMPLATES / "SourceProcessorSpec.scala.tpl")
    assert "§fork" not in tpl and "runbook-ejecucion.md §2" in tpl
    assert "Prueba del lado del fork (AUD-C9)" in leer(SKILL / "references" / "runbook-ejecucion.md")


def test_hf51_checklist_generico_de_output_event_type():
    checklist = leer(SKILL / "assets" / "checklist-entrega.md")
    assert "`outputEventType = 30` (`ASSUMPTION:`)" not in checklist
    assert "`RESUELTO CUR-PA04`" in checklist and "`ASSUMPTION CUR-PA04`" in checklist


# ------------------------------------------------------------------ HF-52 (AUD-C-21): NBSP
NBSP = " "


def test_hf52_referencia_recorta_como_java_trim():
    fmts = ["dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss"]
    assert ref.fecha_epoch("15/01/2024" + NBSP, fmts, "UTC") == (None, ref.FORMATO_INVALIDO)
    assert ref.fecha_epoch(" 15/01/2024\t", fmts, "UTC") == (1705276800000, None)  # ASCII sí se recorta
    assert ref.texto(NBSP + "X ") == (NBSP + "X", None)
    regla = {"solo_digitos": True, "quitar_ceros_izquierda": False}
    assert ref.documento("12" + NBSP + "34", regla) == ("1234", None)
    assert ref.documento("12" + NBSP + "34", {}) == ("12" + NBSP + "34", None)  # \s ASCII no quita el NBSP
    assert ref.padding_izquierda("١٢", 2) == (None, ref.FORMATO_INVALIDO)  # \d ASCII
    assert ref.convertir("١", "entero") == (None, ref.FORMATO_INVALIDO)


def test_hf52_suite_scala_trae_el_mismo_caso_nbsp(rendered):
    out, _ = rendered
    ts = leer(out / TRANSF_SPEC)
    assert 'Transformaciones.fechaEpoch(Some("15/01/2024\\u00a0"), fmts, utc) == Resultado.advertir(Motivos.FormatoInvalido)' in ts
    assert 'Transformaciones.texto(Some("\\u00a0X ")) == Resultado.ok("\\u00a0X")' in ts
    assert "private def limpio(v: Option[String]): Option[String] = v.map(_.trim).filter(_.nonEmpty)" in leer(
        TEMPLATES / "Transformations.scala.tpl")


# ------------------------------------------------------------------ HF-53 (AUD-C-22)
def test_hf53_nivel_desconocido_con_el_campo_curado(spec):
    adv = spec["parametros"]["campo_advertencias"]
    reg = '{"SEG_APELLIDO": "Ficticio", "TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "1"}'
    out = ref.curar(reg, ARCHIVO, [{"codigo": "SISBEN_INC_011", "nivel": None}], spec)
    assert out[adv] == ["segundoApellido:NIVEL_DESCONOCIDO"]
    destinos = {m["destino"] for m in spec["campos_curados"]}
    assert all(a.split(":")[0] in destinos for a in out[adv])


def test_hf53_motivos_defensivos_documentados():
    doc = leer(SKILL / "references" / "interpretar-salida.md")
    assert "**Motivos defensivos (HF-53, AUD-C-22).**" in doc
    assert "`<campo curado>:NIVEL_DESCONOCIDO`" in doc
    tpl = leer(TEMPLATES / "FieldMapping.scala.tpl")
    assert "son DEFENSIVOS (HF-53)" in tpl


# ------------------------------------------------------------------ HF-54 (AUD-C-23)
@pytest.mark.parametrize("skill", ["edf-curation", "edf-validation"])
def test_hf54_advertencia_de_lookup_duplicado(skill):
    raiz = SKILL.parent / skill
    assert "tabla de lookup duplicada (HF-54" in leer(raiz / "SKILL.md")
    assert "oxygen-lookup-inner.conf" in leer(raiz / "assets" / "checklist-entrega.md")
    assert "(HF-54, AUD-C-23)" in leer(raiz / "assets" / "checklist-entrega.md")


# ------------------------------------------------------------------ HF-55 (AUD-C-24)
def test_hf55_manifest_con_sha256_de_todo(rendered):
    out, _ = rendered
    m = json.loads(leer(out / "MANIFEST.json"))
    for a in m["archivos"]:
        assert a["sha256"] == rt.sha256(out / a["archivo"]), a["archivo"]
    for x in m["marcas_sintetico"]:
        assert x["ruta"].endswith("LEEME.md") and x["sha256"] == rt.sha256(out / x["ruta"])


def test_hf48_componente_por_copia_de_sigue_hasta_el_campo_de_entrada(tmp_path, spec):
    """Forma del insumo SISBEN e2e: subscriberRelation con standardizedId* que copian campos curados."""
    def cambio(s):
        s["campos_curados"] += [
            {"destino": "standardizedIdType", "origen": "", "tipo_salida": "entero", "estado": "cerrado",
             "transformacion": {"tipo": "copia_de", "campo": "tipoDocumento"}},
            {"destino": "standardizedIdNumber", "origen": "", "tipo_salida": "texto", "estado": "cerrado",
             "transformacion": {"tipo": "copia_de", "campo": "numeroDocumento"}}]
        s["extension_subscriber_relation"] = {"campos": ["standardizedIdType", "standardizedIdNumber"],
                                              "separador": "_"}
    _, out = render_con(tmp_path, spec, cambio)
    t = leer(out / SPEC_TEST)
    assert "componente faltante (standardizedIdType vacío)" in t and "con TIP_DOCUMENTO vacío" in t
