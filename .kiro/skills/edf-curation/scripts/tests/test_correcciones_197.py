"""Regresión de los defectos del framework hallados en la auditoría de Curation de la corrida E2E SISBEN (1.9.7).

HF-34 (AUD-C-01) `original` sin recortar; HF-35 (AUD-C-05) gancho subscriberRelation; HF-36 (AUD-C-06)
supuestos_resueltos; HF-38 (AUD-C-09) código pendiente sin valor; HF-39 (AUD-C-10) nombre de la skill;
HF-40 (AUD-C-04) lineage del fixture. Las de texto (HF-40 documental, HF-41) están en
`tests/test_regresion_e2e_curation.py`. Datos sintéticos.
"""

import json

import pytest

import curation_reference as ref
import render_templates as rt
from conftest import SCALA_FUENTE, SCHEMA, SKILL, TEMPLATES
from test_render_templates import arbol, leer, spec_con_cambios

ARCHIVO = "SISBEN_EXPERIAN_202401.20240205103000-0500.txt"
ORIGINAL_DOC = {"destino": "numeroDocumentoOriginal", "origen": "NUM_DOCUMENTO",
                "transformacion": {"tipo": "original"}, "tipo_salida": "texto", "estado": "cerrado"}


# ------------------------------------------------------------------ HF-34 (AUD-C-01)
def test_hf34_original_conserva_el_valor_tal_cual():
    assert ref.original(" 0001000000101") == (" 0001000000101", None)
    assert ref.original("   ") == ("   ", None)
    assert ref.original(None) == (None, None)
    assert ref.texto(" 0001000000101") == ("0001000000101", None)  # texto sí recorta


def test_hf34_curar_publica_el_original_con_espacios(spec):
    """HU SISBEN §5.3: numeroDocumentoOriginal = " 0001000000101"; el estandarizado sigue sin espacios ni ceros."""
    spec["campos_curados"].append(ORIGINAL_DOC)
    r = ref.curar('{"TIP_DOCUMENTO": " 3", "NUM_DOCUMENTO": " 0001000000101"}', ARCHIVO, [], spec)
    assert r["numeroDocumentoOriginal"] == " 0001000000101"
    assert r["tipoDocumentoSisben"] == " 3"  # también original en el ejemplo
    assert r["numeroDocumento"] == "1000000101"
    assert r["tipoDocumento"] == 1  # homologación recorta


def test_hf34_prueba_scala_generada_espera_el_valor_con_espacio(tmp_path, spec):
    """El fixture del ejemplo trae NUM_DOCUMENTO " 0009990001 ": la prueba Scala generada lo espera tal cual."""
    copia = spec_con_cambios(tmp_path, spec, lambda s: s["campos_curados"].append(ORIGINAL_DOC))
    rt.render(copia, tmp_path / "out", TEMPLATES, SCHEMA)
    out = tmp_path / "out"
    curados = json.loads(leer(out / "expected/curated-events.json"))
    assert curados[0]["curatedRecordJson"]["numeroDocumentoOriginal"] == " 0009990001 "
    t = leer(out / "src/test/scala/com/experian/edf/oxygen/curation/source/sisben/SISBENCurationProcessorSpec.scala")
    assert '"numeroDocumentoOriginal":" 0009990001 "' in t
    assert '"numeroDocumentoOriginal":"0009990001"' not in t
    p = leer(out / SCALA_FUENTE / "processor/SISBENCurationProcessor.scala")
    assert "else if (m.transformacion == Transformacion.Original) registro.crudo(m.origen)" in p
    assert "case Transformacion.Original => Transformaciones.original(crudo)" in p
    assert "Transformacion.Texto | Transformacion.Original" not in p
    r = leer(out / SCALA_FUENTE / "SISBENAcceptedRecord.scala")
    assert "def crudo(campo: String): Option[String] = campos.get(campo.toUpperCase(Locale.ROOT))" in r
    ts = leer(out / "src/test/scala/com/experian/edf/oxygen/curation/source/common/TransformacionesSpec.scala")
    assert 'assert(Transformaciones.original(Some(" 0001000000101")) == Resultado.ok(" 0001000000101"))' in ts


# ------------------------------------------------------------------ HF-35 (AUD-C-05)
def test_hf35_replica_tolera_errorcodes_nulos_y_cura_una_vez(spec, monkeypatch):
    spec["extension_subscriber_relation"] = {"campos": ["tipoDocumento", "numeroDocumento"], "separador": "_"}
    llamadas = []
    original = ref.curar
    monkeypatch.setattr(ref, "curar", lambda *a, **k: llamadas.append(1) or original(*a, **k))
    evento = {"eventId": "e", "lineage": {"fileName": ARCHIVO}, "subscriberRelation": "ACEPTADO",
              "validatedRecordJson": '{"TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "0999"}',
              "errorCodes": [None, {"errorCodeValue": None, "errorLevel": "3"}]}
    assert ref.curar_evento(evento, spec)["subscriberRelation"] == "1_999"
    assert len(llamadas) == 1


def test_hf35_sin_extension_curate_event_no_cambia_de_contrato(rendered):
    out, _ = rendered
    p = leer(out / SCALA_FUENTE / "processor/SISBENCurationProcessor.scala")
    assert "private def recordarCurado(event: TaggedValidatedEvent, record: SISBENCuratedRecord): Unit = ()" in p
    assert "override def subscriberRelation" not in p


# ------------------------------------------------------------------ HF-36 (AUD-C-06)
RESUELTOS = [{"id": "CUR-PT05", "fuente": "spec-sisben.md §10.1"}, {"id": "F-12", "fuente": "spec-sisben.md §9.4"},
             {"id": "C-CUR-04", "fuente": "hu-sisben.md R-23"}, {"id": "C-CUR-03", "fuente": "spec-sisben.md §10.2"},
             {"id": "CUR-PA04", "fuente": "spec-sisben.md §1"}, {"id": "F-07", "fuente": "spec-sisben.md §2"},
             {"id": "D8", "fuente": "spec-sisben.md §2"}, {"id": "C-CUR-07", "fuente": "SISBENValidatedEvent aprobado"},
             {"id": "C-CUR-08", "fuente": "spec-sisben.md §10.1"}]


def test_hf36_supuestos_resueltos_cambian_la_marca_y_quitan_el_pedido(tmp_path, spec):
    copia = spec_con_cambios(tmp_path, spec, lambda s: s.update(supuestos_resueltos=RESUELTOS))
    manifest = rt.render(copia, tmp_path / "out", TEMPLATES, SCHEMA)
    assert manifest["supuestos_resueltos"] == sorted(RESUELTOS, key=lambda r: r["id"])
    ids = {r["id"] for r in RESUELTOS}
    lineas = [l for c in arbol(tmp_path / "out").values() for l in c.decode("utf-8").split("\n")]
    for linea in lineas:
        for m in rt.MARCA_RE.finditer(linea):
            assert m.group(1) not in ids, linea  # ninguna marca resuelta queda como ASSUMPTION
        if "RESUELTO " in linea:
            assert "confirmar" not in linea and "verificar" not in linea, linea
    cs = leer(tmp_path / "out" / SCALA_FUENTE / "config/SISBENCurationSpec.scala")
    assert "  /** Campo de advertencias de curación (D13). RESUELTO CUR-PT05 (spec-sisben.md §10.1): nombre del campo. */" in cs
    assert '"tipo_sisben"   // RESUELTO F-12 (spec-sisben.md §9.4)' in cs
    assert "  // --- Fechas (RF-CUR-06). RESUELTO C-CUR-04 (hu-sisben.md R-23): zona UTC." in cs
    p = leer(tmp_path / "out" / SCALA_FUENTE / "processor/SISBENCurationProcessor.scala")
    assert "lo usa. RESUELTO CUR-PA04 (spec-sisben.md §1)." in p
    assert "ASSUMPTION CUR-PT02" in p  # los no declarados siguen abiertos
    conf = leer(tmp_path / "out" / "fragmentos/curation-common-application-inner.sisben.conf")
    assert "# RESUELTO F-07 (spec-sisben.md §2): tipo de archivo por prefijo." in conf
    assert "# RESUELTO D8 (spec-sisben.md §2): convención de nombre del archivo." in conf


def test_hf36_sin_la_clave_nada_cambia(rendered):
    out, manifest = rendered
    assert "supuestos_resueltos" not in manifest
    assert all("RESUELTO" not in c.decode("utf-8") for c in arbol(out).values())


@pytest.mark.parametrize("resueltos, mensaje", [
    ([{"id": "C-CUR-01", "fuente": "spec"}], "sin marca ASSUMPTION"),
    ([{"id": "F-12", "fuente": "a"}, {"id": "F-12", "fuente": "b"}], "IDs repetidos"),
    ([{"id": "C-CUR-02", "fuente": "spec"}], "C-CUR-02"),
])
def test_hf36_errores_de_coherencia(tmp_path, spec, resueltos, mensaje):
    copia = spec_con_cambios(tmp_path, spec, lambda s: s.update(supuestos_resueltos=resueltos))
    with pytest.raises(rt.RenderError, match=mensaje):
        rt.verificar(copia, SCHEMA, TEMPLATES)


@pytest.mark.parametrize("item", [{"id": "X-1", "fuente": "spec"}, {"id": "F-12", "fuente": "a */ b"},
                                  {"id": "F-12"}, {"id": "F-12", "fuente": "s", "otra": 1}])
def test_hf36_esquema_rechaza_entradas_mal_formadas(spec, schema, item):
    spec["supuestos_resueltos"] = [item]
    with pytest.raises(rt.RenderError, match="supuestos_resueltos"):
        rt.validar_spec(spec, schema)


def test_hf36_resolver_conserva_el_pedido_si_queda_otra_marca_abierta():
    texto, usados = rt.resolver_supuestos("// ASSUMPTION F-07 / ASSUMPTION D8; confirmar algo.", {"F-07": "s §2"})
    assert texto == "// RESUELTO F-07 (s §2) / ASSUMPTION D8; confirmar algo." and usados == {"F-07"}


# ------------------------------------------------------------------ HF-38 (AUD-C-09)
def test_hf38_pendiente_sin_valor_no_cambia_el_comportamiento(tmp_path, spec, rendered):
    """Con y sin "7": null en la tabla, la salida curada y el código Scala son iguales; solo cambia el CSV."""
    assert spec["homologacion_tipo_documento"]["tabla"]["7"] is None
    copia = spec_con_cambios(tmp_path, spec, lambda s: s["homologacion_tipo_documento"]["tabla"].pop("7"))
    rt.render(copia, tmp_path / "out", TEMPLATES, SCHEMA)
    con, sin = arbol(rendered[0]), arbol(tmp_path / "out")
    distintos = sorted(k for k in con if con[k] != sin.get(k))
    assert distintos == ["MANIFEST.json", "src/test/resources/sisben/lookups/202_SISBEN_PROCESS_ID_TYPE_HO.csv"]
    assert ref.curar('{"TIP_DOCUMENTO": "7"}', ARCHIVO, [], spec)["tipoDocumento"] is None
    assert "tipoDocumento:HOMOLOGACION_PENDIENTE" in ref.curar('{"TIP_DOCUMENTO": "7"}', ARCHIVO, [], spec)["advertenciasCuracion"]


def test_hf38_valor_vacio_exige_estar_en_pendientes(spec, schema):
    spec["homologacion_tipo_documento"]["tabla"]["9"] = ""
    with pytest.raises(rt.RenderError, match=r"sin valor que no están en pendientes: \['9'\]"):
        rt.validar_spec(spec, schema)
    spec["homologacion_tipo_documento"]["pendientes"].append("9")
    rt.validar_spec(spec, schema)
    assert "9" not in ref.tabla_homologacion(spec)


def test_hf38_esquema_sigue_rechazando_valores_no_numericos(spec, schema):
    spec["homologacion_tipo_documento"]["tabla"]["3"] = "CC"
    with pytest.raises(rt.RenderError, match="homologacion_tipo_documento/tabla/3"):
        rt.validar_spec(spec, schema)


# ------------------------------------------------------------------ HF-39 (AUD-C-10)
def test_hf39_nombre_de_la_skill(rendered):
    out, manifest = rendered
    assert manifest["skill"] == "edf-curation" == rt.SKILL_NAME
    for tpl in TEMPLATES.glob("*.tpl"):
        assert "edf-curation-stage" not in leer(tpl), tpl.name
    assert all(b"edf-curation-stage" not in c for c in arbol(out).values())
    assert "generado por el skill edf-curation v" in leer(TEMPLATES / "CurationProcessor.scala.tpl").replace("{{version_skill}}", "")


# ------------------------------------------------------------------ HF-40 (AUD-C-04)
def test_hf40_lineage_del_fixture_como_lo_emite_validation(spec):
    fixture = rt.cargar_yaml(SKILL / "assets/examples/sisben/fixture/accepted-events.input.yaml")
    eventos = rt.construir_eventos(spec, fixture)
    por_archivo = {e["lineage"]["fileName"]: e for e in eventos}
    regular = por_archivo["SISBEN_EXPERIAN_202401.20240205103000-0500.txt"]
    # último día de 2024-01, 00:00 America/Bogota (zona_asofdate_etapa_anterior), no la fecha de recepción
    assert regular["lineage"]["fileAsOfDate"] == 1706677200000 != regular["lineage"]["receivedDate"]
    assert regular["fileTag"] == "REGULAR"
    rt_ = next(e for n, e in por_archivo.items() if n.startswith("RT_"))
    assert rt_["fileTag"] == "RT"
    assert rt.metadatos_archivo(spec, "OTRA_FUENTE_202401.txt") == (0, "")
