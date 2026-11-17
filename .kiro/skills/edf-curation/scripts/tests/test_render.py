"""DEPRECADO (2026-10-20): prueban render_templates.py, la versión Python deprecada.

Las pruebas vigentes están en scripts/java/src/test (RenderTemplatesTest). Ver scripts/DEPRECADOS.md.
Renderizado: huecos declarados, rellenos, sin residuos; salida coherente y estable."""

import copy
import filecmp
import json
import re

import pytest

import hocon_check
import render_templates as rt
from conftest import EXPECTED, GENERATED, SCHEMA, SPEC, TEMPLATES

HUECO = re.compile(r"\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}")


def test_todo_hueco_de_plantilla_esta_declarado_en_el_esquema(schema):
    declarados = set(schema["x-placeholders"])
    usados = set()
    for tpl in TEMPLATES.glob("*.tpl"):
        usados |= set(HUECO.findall(tpl.read_text(encoding="utf-8")))
    assert usados <= declarados, usados - declarados
    assert declarados <= usados, f"declarados sin uso: {declarados - usados}"


def test_todo_hueco_declarado_se_rellena_desde_el_spec_sisben(spec, schema):
    fixture = rt.cargar_yaml(SPEC.parent / spec["fixture"]["entrada"])
    eventos = rt.construir_eventos(spec, fixture)
    curados, _ = rt.salida_esperada(spec, eventos)
    valores = rt.huecos(spec, fixture, eventos, curados)
    for nombre in schema["x-placeholders"]:
        assert nombre in valores, nombre
        assert str(valores[nombre]()).strip(), f"hueco vacío: {nombre}"


def test_spec_valida_contra_esquema(spec, schema):
    rt.validar_spec(spec, schema)


def test_plantillas_inventario_completo(spec):
    ids = [p[0] for p in rt.plan(spec)]
    assert sorted(ids) == sorted(f"CUR-TPL-{i:02d}" for i in range(1, 13))
    for _, tpl, _ in rt.plan(spec):
        assert (TEMPLATES / tpl).exists(), tpl


def test_sin_huecos_sin_resolver(rendered):
    out, manifest = rendered
    assert len(manifest["archivos"]) == 12
    for f in out.rglob("*"):
        if f.is_file():
            assert "{{" not in f.read_text(encoding="utf-8"), f


def test_salida_igual_a_la_versionada(rendered):
    """generated/ y expected/ versionados = lo que produce hoy el renderer (sin deriva)."""
    out, _ = rendered
    comparar = filecmp.dircmp(out, GENERATED)
    pendientes = [comparar]
    while pendientes:
        d = pendientes.pop()
        assert not d.left_only and not d.right_only and not d.diff_files, (d.left, d.left_only, d.right_only, d.diff_files)
        pendientes.extend(d.subdirs.values())
    for nombre in ("curated-events.json", "resumen.json"):
        assert (out / "expected" / nombre).read_text(encoding="utf-8") == (EXPECTED / nombre).read_text(encoding="utf-8")


def test_hocon_bloque_curation(rendered, spec):
    out, _ = rendered
    conf = hocon_check.parse((out / "fragmentos/curation-common-application-inner.sisben.conf").read_text(encoding="utf-8"))
    cfgs = conf["oxygen"]["stage"]["curation"]["configs"]
    assert len(cfgs) == 1 and set(cfgs[0]) == {"filePattern", "processor"}
    assert cfgs[0]["processor"] == "com.experian.edf.oxygen.curation.source.sisben.processor.SISBENCurationProcessor"
    assert cfgs[0]["filePattern"] == spec["archivo"]["file_pattern"]  # el escape HOCON se deshace bien
    pat = re.compile(cfgs[0]["filePattern"])
    assert all(pat.fullmatch(e) for e in spec["archivo"]["ejemplos_sinteticos"])
    prohibidas = {"runMode", "partitionSize", "partionSize", "enableSSRWrite", "includeQualityCodes", "includeErrorCodes", "excludes"}
    assert not prohibidas & set(conf["oxygen"]["stage"]["curation"])


def test_hocon_lookups(rendered):
    out, _ = rendered
    conf = hocon_check.parse((out / "fragmentos/oxygen-lookup-inner.sisben.conf").read_text(encoding="utf-8"))
    lk = conf["oxygen"]["lookups"]
    assert [x["tableName"] for x in lk] == ["202_SISBEN_PROCESS_ID_TYPE_HO"]
    assert lk[0]["file"] == "${oxygen.application.paths.lookupHome}/sisben/202_SISBEN_PROCESS_ID_TYPE_HO.csv"
    assert lk[0]["keys"] == [{"index": "tipo_sisben"}]


def test_domain_dictionary_coincide_con_claves_del_evento(rendered, spec):
    out, _ = rendered
    dd = json.loads((out / "fragmentos/colombia-domain-dictionary-inner.sisben.json").read_text(encoding="utf-8"))
    nombres = [f["name"] for f in dd["fields"]]
    assert len(nombres) == len(set(nombres))
    curados = json.loads((out / "expected/curated-events.json").read_text(encoding="utf-8"))
    for c in curados:
        assert set(c["curatedRecordJson"]) <= set(nombres)
    assert set(curados[0]["curatedRecordJson"]) == set(nombres)
    scala = (out / "src/main/scala/com/experian/edf/oxygen/curation/source/sisben/config/SISBENCurationSpec.scala").read_text(encoding="utf-8")
    for n in nombres:
        assert f'"{n}"' in scala


def test_lookup_202_coincide_con_la_tabla_embebida(rendered, spec):
    out, _ = rendered
    csv = (out / "src/test/resources/sisben/lookups/202_SISBEN_PROCESS_ID_TYPE_HO.csv").read_text(encoding="utf-8").splitlines()
    filas = {l.split(",")[0]: l.split(",")[1] for l in csv[1:]}
    assert filas == spec["homologacion_tipo_documento"]["tabla"]
    assert "7" not in filas  # salvoconducto: pendiente, sin código arbitrario


def test_fixture_es_jsonl_de_eventos_sinteticos(rendered):
    out, _ = rendered
    lineas = (out / "src/test/resources/sisben/accepted-events.json").read_text(encoding="utf-8").strip().splitlines()
    assert len(lineas) == 8
    for l in lineas:
        ev = json.loads(l)
        assert ev["payload"] == {"fixture": "SINTETICO"}
        json.loads(ev["validatedRecordJson"])


def test_processor_cumple_contrato(rendered):
    out, _ = rendered
    p = (out / "src/main/scala/com/experian/edf/oxygen/curation/source/sisben/processor/SISBENCurationProcessor.scala").read_text(encoding="utf-8")
    assert "class SISBENCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context)" in p
    assert "override val outputEventType: Int = 30" in p
    assert "override def enrichEventWithPayload(event: TaggedValidatedEvent): Payload = event.payload" in p
    assert "@transient private lazy val logger" in p
    assert "println(" not in p
    assert "override def subscriberRelation" not in p
    assert "throw " not in p


@pytest.mark.parametrize("cambio,mensaje", [
    (lambda s: s["campos_curados"].append(copy.deepcopy(s["campos_curados"][0])), "repetidos"),
    (lambda s: s["campos_curados"][0].update(origen="NO_EXISTE"), "campos_entrada"),
    (lambda s: s["archivo"].update(file_pattern=".*NADA"), "ejemplo"),
    (lambda s: s["campos_curados"].append({"destino": "puntajeSisben", "origen": "", "estado": "abierto",
                                           "transformacion": {"tipo": "constante", "valor": None}, "tipo_salida": "texto"}), "abiertos"),
    (lambda s: s["fixture"].update(sintetico=False), "inválido"),
])
def test_spec_incoherente_se_rechaza(spec, schema, cambio, mensaje):
    malo = copy.deepcopy(spec)
    cambio(malo)
    with pytest.raises(rt.RenderError, match=mensaje):
        rt.validar_spec(malo, schema)


def test_cli_devuelve_error_con_spec_invalido(tmp_path):
    malo = tmp_path / "spec.yaml"
    malo.write_text("schema_version: '1.0'\n", encoding="utf-8")
    assert rt.main(["--spec", str(malo), "--out", str(tmp_path / "out"), "--schema", str(SCHEMA)]) == 1


def test_cli_ok(tmp_path):
    assert rt.main(["--spec", str(SPEC), "--out", str(tmp_path / "out")]) == 0
