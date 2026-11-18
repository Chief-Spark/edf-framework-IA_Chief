"""Generación: huecos declarados, rellenos, sin residuos; salida coherente y estable.

Traduce los casos de la prueba Java RenderTemplatesTest (deprecada, scripts/DEPRECADOS.md), incluido "la
salida es igual a la versionada", y agrega los de la 1.7.0 (concatenacion, opción B de severidad 3, todo o
nada). Las comprobaciones con Typesafe Config (el parser HOCON del motor) se hacen con hocon_check y, si está
instalado, pyhocon; la definitiva ocurre al compilar y probar en el fork, fuera de la skill.
"""

import copy
import json
import re
from pathlib import Path

import pytest
import yaml

import hocon_check
import render_templates as rt
from conftest import EXPECTED, GENERATED, SCALA_FUENTE, SCHEMA, SKILL, SPEC, TEMPLATES

ADVERTENCIA_SISBEN = ("ADVERTENCIA: zona_horaria_fechas (UTC) difiere de la zona de asOfDate de la etapa anterior "
                      "(America/Bogota) (C-CUR-04)")


def arbol(raiz: Path) -> dict[str, bytes]:
    return {f.relative_to(raiz).as_posix(): f.read_bytes() for f in sorted(raiz.rglob("*")) if f.is_file()}


def leer(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def contexto(spec):
    fixture = rt.cargar_yaml(SPEC.parent / spec["fixture"]["entrada"])
    eventos = rt.construir_eventos(spec, fixture)
    curados, _ = rt.salida_esperada(spec, eventos)
    return rt.huecos(spec, fixture, eventos, curados)


def spec_con_cambios(tmp_path: Path, spec: dict, cambio) -> Path:
    """Copia la spec SISBEN y su fixture a tmp_path y escribe la spec con el cambio aplicado."""
    fx = SPEC.parent / "fixture"
    for p in fx.rglob("*"):
        if p.is_file():
            destino = tmp_path / "fixture" / p.relative_to(fx)
            destino.parent.mkdir(parents=True, exist_ok=True)
            destino.write_bytes(p.read_bytes())
    modificada = copy.deepcopy(spec)
    cambio(modificada)
    copia = tmp_path / "spec.yaml"
    copia.write_text(yaml.safe_dump(modificada, allow_unicode=True, sort_keys=False), encoding="utf-8")
    return copia


# ------------------------------------------------------------------ huecos y plan
def test_todo_hueco_de_plantilla_esta_declarado_en_el_esquema(schema):
    usados = set()
    for tpl in TEMPLATES.glob("*.tpl"):
        usados |= set(rt.HUECO.findall(leer(tpl)))
    declarados = set(schema["x-placeholders"])
    assert usados <= declarados, "sin declarar"
    assert declarados <= usados, "declarados sin uso"


def test_todo_hueco_declarado_se_rellena_desde_el_spec_sisben(spec, schema):
    valores = contexto(spec)
    for nombre in schema["x-placeholders"]:
        assert nombre in valores, nombre
        assert str(valores[nombre]()).strip(), f"hueco vacío: {nombre}"


def test_spec_valida_contra_esquema(spec, schema):
    rt.validar_spec(spec, schema)


def test_plantillas_inventario_completo(spec):
    ids = {p[0] for p in rt.plan(spec)}
    assert ids == {f"CUR-TPL-{i:02d}" for i in range(1, 13)}
    for _, tpl, _ in rt.plan(spec):
        assert (TEMPLATES / tpl).exists(), tpl


# ------------------------------------------------------------------ salida
def test_sin_huecos_sin_resolver(rendered):
    out, manifest = rendered
    assert len(manifest["archivos"]) == 12
    for f in out.rglob("*"):
        if f.is_file():
            assert "{{" not in leer(f), f


def test_salida_igual_a_la_versionada(rendered):
    """generated/ y expected/ versionados = lo que produce hoy el generador (sin deriva), byte a byte."""
    out, _ = rendered
    assert arbol(out) == arbol(GENERATED)
    for nombre in ("curated-events.json", "resumen.json"):
        assert (out / "expected" / nombre).read_bytes() == (EXPECTED / nombre).read_bytes()


def test_hocon_bloque_curation(rendered, spec):
    out, _ = rendered
    conf = hocon_check.parse(leer(out / "fragmentos/curation-common-application-inner.sisben.conf"))
    cfgs = conf["oxygen"]["stage"]["curation"]["configs"]
    assert len(cfgs) == 1
    assert set(cfgs[0]) == {"filePattern", "processor"}
    assert cfgs[0]["processor"] == "com.experian.edf.oxygen.curation.source.sisben.processor.SISBENCurationProcessor"
    assert cfgs[0]["filePattern"] == spec["archivo"]["file_pattern"]  # el escape HOCON se deshace bien
    for ej in spec["archivo"]["ejemplos_sinteticos"]:
        assert re.fullmatch(cfgs[0]["filePattern"], ej)
    prohibidas = {"runMode", "partitionSize", "partionSize", "enableSSRWrite", "includeQualityCodes",
                  "includeErrorCodes", "excludes"}
    assert not prohibidas & set(conf["oxygen"]["stage"]["curation"])


def test_hocon_lookups(rendered):
    out, _ = rendered
    conf = hocon_check.parse(leer(out / "fragmentos/oxygen-lookup-inner.sisben.conf"))
    lk = conf["oxygen"]["lookups"]
    assert [c["tableName"] for c in lk] == ["202_SISBEN_PROCESS_ID_TYPE_HO"]
    assert lk[0]["file"] == "${oxygen.application.paths.lookupHome}/sisben/202_SISBEN_PROCESS_ID_TYPE_HO.csv"
    assert lk[0]["keys"] == [{"index": "tipo_sisben"}]


def test_pyhocon_si_esta_disponible(rendered):
    pyhocon = pytest.importorskip("pyhocon")
    out, _ = rendered
    for f in (out / "fragmentos").glob("*.conf"):
        pyhocon.ConfigFactory.parse_string(leer(f), resolve=False)


def test_domain_dictionary_coincide_con_claves_del_evento(rendered):
    out, _ = rendered
    dd = json.loads(leer(out / "fragmentos/colombia-domain-dictionary-inner.sisben.json"))
    nombres = [f["name"] for f in dd["fields"]]
    assert len(nombres) == len(set(nombres))
    curados = json.loads(leer(out / "expected/curated-events.json"))
    for c in curados:
        assert set(c["curatedRecordJson"]) <= set(nombres)
    assert set(curados[0]["curatedRecordJson"]) == set(nombres)
    scala = leer(out / SCALA_FUENTE / "config/SISBENCurationSpec.scala")
    for n in nombres:
        assert f'"{n}"' in scala, n


def test_lookup_202_coincide_con_la_tabla_embebida(rendered, spec):
    out, _ = rendered
    csv = leer(out / "src/test/resources/sisben/lookups/202_SISBEN_PROCESS_ID_TYPE_HO.csv").split("\n")
    filas = {l.split(",")[0]: l.split(",")[1] for l in csv[1:] if l}
    tabla = {str(k): str(v) for k, v in spec["homologacion_tipo_documento"]["tabla"].items()}
    assert filas == tabla
    assert "7" not in filas  # salvoconducto: pendiente, sin código arbitrario


def test_fixture_es_jsonl_de_eventos_sinteticos(rendered):
    out, _ = rendered
    lineas = leer(out / "src/test/resources/sisben/accepted-events.json").strip().split("\n")
    assert len(lineas) == 8
    for l in lineas:
        ev = json.loads(l)
        assert ev["payload"]["fixture"] == "SINTETICO"
        json.loads(ev["validatedRecordJson"])


def test_lookups_generados_marcados_como_sinteticos(rendered, spec):
    """AC-30: los lookups que van al fork llevan la marca de sintético en un archivo aparte."""
    out, manifest = rendered
    rel = spec["rutas_destino"]["test_resources"] + "lookups/"
    lk = out / rel
    assert "SINTÉTICO" in leer(lk / rt.MARCA_LOOKUPS)
    assert manifest["marcas_sintetico"] == [rel + rt.MARCA_LOOKUPS]
    assert len(manifest["archivos"]) == 12  # la marca no es un artefacto de plantilla
    csv = list(lk.glob("*.csv"))
    assert len(csv) == len(spec["catalogos"])
    for c in csv:  # sin comentarios dentro del CSV: OxygenLookup lo carga con cabecera
        assert not any(l.startswith("#") for l in leer(c).splitlines()), c


def test_sin_marca_de_lookups_se_rechaza_sin_tocar_out(tmp_path):
    fx = SPEC.parent / "fixture"
    copia = tmp_path / "fixture"
    (copia / "lookups").mkdir(parents=True)
    (copia / "accepted-events.input.yaml").write_bytes((fx / "accepted-events.input.yaml").read_bytes())
    for p in (fx / "lookups").glob("*.csv"):
        (copia / "lookups" / p.name).write_bytes(p.read_bytes())
    (tmp_path / "spec.yaml").write_bytes(SPEC.read_bytes())
    out = tmp_path / "out"
    out.mkdir()
    (out / "previo.txt").write_text("x", encoding="utf-8")
    with pytest.raises(rt.RenderError, match="marca de sintético"):
        rt.render(tmp_path / "spec.yaml", out, TEMPLATES, SCHEMA)
    assert (out / "previo.txt").exists(), "todo o nada: --out no se toca si la generación falla"
    (copia / "lookups" / "2_DANE_DIVIPOLA_CITIES_CODES_NH.csv").unlink()
    with pytest.raises(rt.RenderError, match="falta el lookup sintético"):
        rt.render(tmp_path / "spec.yaml", out, TEMPLATES, SCHEMA)


def test_manifest_lista_todos_los_archivos(rendered, spec):
    """AC-25: todo archivo, salvo MANIFEST.json, está en exactamente una lista, y el sha256 coincide."""
    out, _ = rendered
    m = json.loads(leer(out / "MANIFEST.json"))
    listados = [a["archivo"] for a in m["archivos"]] + list(m["marcas_sintetico"])
    hashes = {o["ruta"]: o["sha256"] for o in m["otros_archivos"]}
    listados += list(hashes)
    assert len(listados) == len(set(listados)), "un archivo aparece en más de una lista"
    disco = set(arbol(out))
    disco.remove("MANIFEST.json")
    assert "MANIFEST.json" not in listados
    assert disco == set(listados)
    for ruta, h in hashes.items():
        assert rt.sha256(out / ruta) == h and re.fullmatch(r"[0-9a-f]{64}", h), ruta
    # SISBEN: 23 en disco = 12 + 1 + 9 + MANIFEST.json
    assert len(m["archivos"]) == 12 and len(m["marcas_sintetico"]) == 1
    assert len(m["otros_archivos"]) == len(spec["catalogos"]) + 2
    assert len(disco) + 1 == 23


# ------------------------------------------------------------------ zona (ADR-G4, AC-CUR-10)
def test_zona_distinta_de_asofdate_advierte(tmp_path, capsys, rendered):
    assert rt.main(["--spec", str(SPEC), "--out", str(tmp_path / "out")]) == 0
    cap = capsys.readouterr()
    assert cap.err.splitlines() == [ADVERTENCIA_SISBEN]
    stdout = cap.out.splitlines()
    assert len(stdout) == 1
    assert stdout[0].startswith("OK: 12 artefactos") and stdout[0].endswith("(compilación no_certificada)")
    m = json.loads(leer(tmp_path / "out/MANIFEST.json"))
    assert m["advertencias"] == [ADVERTENCIA_SISBEN]
    assert m["zona_horaria_fechas"] == {"valor": "UTC", "origen": "supuesto"}
    assert rendered[1]["advertencias"] == [ADVERTENCIA_SISBEN]  # también en render()


def test_zona_igual_no_advierte(tmp_path, capsys, spec):
    copia = spec_con_cambios(tmp_path, spec, lambda s: s["parametros"].update(zona_horaria_fechas="America/Bogota"))
    assert rt.main(["--spec", str(copia), "--out", str(tmp_path / "out")]) == 0
    cap = capsys.readouterr()
    assert cap.err == ""
    assert cap.out.startswith("OK: 12 artefactos")
    m = json.loads(leer(tmp_path / "out/MANIFEST.json"))
    assert m["advertencias"] == []
    assert m["zona_horaria_fechas"]["valor"] == "America/Bogota"
    # asOfDate no depende de zona_horaria_fechas: es el mismo con UTC y con Bogotá
    bogota = json.loads(leer(tmp_path / "out/expected/curated-events.json"))
    utc = json.loads(leer(EXPECTED / "curated-events.json"))
    assert bogota[0]["asOfDate"] == utc[0]["asOfDate"]


def test_parametros_de_zona_por_defecto(spec, schema):
    """ADR-G4: los dos parámetros son opcionales; sin ellos rigen America/Bogota y supuesto."""
    del spec["parametros"]["zona_asofdate_etapa_anterior"]
    del spec["parametros"]["zona_horaria_fechas_origen"]
    rt.validar_spec(spec, schema)
    assert rt.zona_asofdate(spec) == "America/Bogota"
    assert rt.origen_zona_fechas(spec) == "supuesto"
    assert rt.advertencias_zona(spec) == [ADVERTENCIA_SISBEN]
    spec["parametros"]["zona_horaria_fechas"] = "America/Bogota"
    assert rt.advertencias_zona(spec) == []
    spec["parametros"]["zona_horaria_fechas"] = "UTC"
    spec["parametros"]["zona_asofdate_etapa_anterior"] = "Etc/UTC"  # misma zona con otro nombre
    assert rt.advertencias_zona(spec) == []


# ------------------------------------------------------------------ marcas ASSUMPTION (AC-35)
ID_DECISION = re.compile(r"\b(C-CUR-\d{2}|CUR-P[AT]\d{2}|F-\d{2}|D\d{1,2}(?:-[a-z])?)\b")


def test_toda_marca_assumption_tiene_id_en_decisiones(rendered):
    out, _ = rendered
    en_decisiones = set(ID_DECISION.findall(leer(SKILL / "references/decisiones-y-abiertos.md")))
    marcas, sin_id, huerfanos = 0, [], set()
    for ruta, contenido in arbol(out).items():
        for linea in contenido.decode("utf-8").split("\n"):
            i = linea.find("ASSUMPTION")
            if i < 0:
                continue
            marcas += 1
            ids = ID_DECISION.findall(linea[i:])
            if not ids:
                sin_id.append(f"{ruta}: {linea.strip()}")
            huerfanos |= {f"{x} en {ruta}" for x in ids if x not in en_decisiones}
    assert marcas >= 30, marcas
    assert sin_id == [], "marcas ASSUMPTION sin ID"
    assert huerfanos == set(), "IDs que no están en decisiones-y-abiertos.md"


def test_processor_cumple_contrato(rendered):
    out, _ = rendered
    p = leer(out / SCALA_FUENTE / "processor/SISBENCurationProcessor.scala")
    assert "class SISBENCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context)" in p
    assert "override val outputEventType: Int = 30" in p
    assert "override def enrichEventWithPayload(event: TaggedValidatedEvent): Payload = event.payload" in p
    assert "@transient private lazy val logger" in p
    assert not ("println(" in p or "override def subscriberRelation" in p or "throw " in p)
    assert "case Transformacion.Concatenacion(campos, separador) =>" in p


# ------------------------------------------------------------------ E2E: F-FW-10, F-FW-11, F-FW-13, F-FW-14
def test_formato_iso_solo_fecha_es_valido_y_el_fixture_lo_entiende(spec, schema):
    spec["parametros"]["formatos_fecha"] = ["uuuu-MM-dd"]
    rt.validar_spec(spec, schema)
    assert rt.epoch_ms("31/01/2026", "America/Bogota") == rt.epoch_ms("2026-01-31", "America/Bogota")
    assert rt.epoch_ms("2026-01-31 00:00:00", "UTC") == rt.epoch_ms("2026-01-31", "UTC")
    assert rt.epoch_ms("2026-01-05", "UTC") == rt.epoch_ms("2026-1-5", "UTC")
    with pytest.raises(rt.RenderError):
        rt.epoch_ms("2026-02-30", "UTC")
    spec["parametros"]["formatos_fecha"] = ["uuuu/MM/dd"]  # fuera del enum: se sigue rechazando
    with pytest.raises(rt.RenderError, match="inválido"):
        rt.validar_spec(spec, schema)


def test_genera_con_formato_iso_y_lo_declara_en_la_spec_scala(tmp_path, spec):
    copia = spec_con_cambios(tmp_path, spec, lambda s: s["parametros"].update(formatos_fecha=["uuuu-MM-dd"]))
    rt.render(copia, tmp_path / "out", TEMPLATES, SCHEMA)
    assert 'val FormatosFecha: Seq[String] = Seq("uuuu-MM-dd")' in leer(
        tmp_path / "out" / SCALA_FUENTE / "config/SISBENCurationSpec.scala")


def test_listas_vacias_se_generan_como_set_vacio(tmp_path, spec):
    """F-FW-13: pendientes vacío (y niveles vacíos) se generan como Set() y no son un hueco sin valor."""
    def cambio(s):
        s["homologacion_tipo_documento"]["pendientes"] = []
        s["parametros"]["niveles_anular"] = []
        s["parametros"]["niveles_no_almacenar"] = []
    rt.render(spec_con_cambios(tmp_path, spec, cambio), tmp_path / "out", TEMPLATES, SCHEMA)
    cs = leer(tmp_path / "out" / SCALA_FUENTE / "config/SISBENCurationSpec.scala")
    assert "val Pendientes: Set[String] = Set()" in cs
    assert "val NivelesAnular: Set[String] = Set()" in cs
    assert "val NivelesNoAlmacenar: Set[String] = Set()" in cs
    with pytest.raises(rt.RenderError, match="valor vacío"):  # el resto de los huecos no puede quedar vacío
        rt.rellenar("{{campo_advertencias}}", {"campo_advertencias": lambda: ""}, {"campo_advertencias"}, "x.tpl")


def test_opcion_b_severidad3_publica_estandarizado(tmp_path, spec):
    """1.7.0 (PA-003 de SISBEN): niveles_no_almacenar vacío ⇒ ninguna clave se omite; el campo con severidad 3
    sale estandarizado y la advertencia queda en el registro."""
    copia = spec_con_cambios(tmp_path, spec, lambda s: s["parametros"].update(niveles_no_almacenar=[]))
    rt.render(copia, tmp_path / "out", TEMPLATES, SCHEMA)
    resumen = json.loads(leer(tmp_path / "out/expected/resumen.json"))
    assert resumen["claves_omitidas_por_severidad"] == {}
    base = json.loads(leer(EXPECTED / "resumen.json"))
    assert base["claves_omitidas_por_severidad"], "el ejemplo base sí omite (política por defecto)"
    curados = {c["eventId"]: c["curatedRecordJson"] for c in json.loads(leer(tmp_path / "out/expected/curated-events.json"))}
    for ident, omitidas in base["claves_omitidas_por_severidad"].items():
        for clave in omitidas:  # la clave se publica (en evt-0004 llega vacía: sale nula)
            assert clave in curados[ident], (ident, clave)


def test_concatenacion_genera_scala_y_salida_esperada(tmp_path, spec, schema):
    """1.7.0 (RG-013 de SISBEN): standardizedIdName = nombres estandarizados unidos, omitiendo vacíos."""
    def cambio(s):
        s["campos_abiertos"] = [a for a in s["campos_abiertos"] if a["destino"] != "nombreCompleto"]
        s["campos_curados"].append({
            "destino": "standardizedIdName", "origen": "", "tipo_salida": "texto", "estado": "cerrado",
            "transformacion": {"tipo": "concatenacion", "separador": " ",
                               "campos": ["primerNombre", "segundoNombre", "primerApellido", "segundoApellido"]}})
    copia = spec_con_cambios(tmp_path, spec, cambio)
    rt.render(copia, tmp_path / "out", TEMPLATES, SCHEMA)
    out = tmp_path / "out"
    cs = leer(out / SCALA_FUENTE / "config/SISBENCurationSpec.scala")
    assert ('FieldMapping("standardizedIdName", "", Transformacion.Concatenacion(Seq("primerNombre", "segundoNombre", '
            '"primerApellido", "segundoApellido"), " "), TipoSalida.Texto, None)') in cs
    for c in json.loads(leer(out / "expected/curated-events.json")):
        r = c["curatedRecordJson"]
        partes = [r.get(k) for k in ("primerNombre", "segundoNombre", "primerApellido", "segundoApellido")]
        esperado = " ".join(p for p in partes if p) or None
        assert r["standardizedIdName"] == esperado, c["eventId"]
    dd = json.loads(leer(out / "fragmentos/colombia-domain-dictionary-inner.sisben.json"))
    assert {"name": "standardizedIdName", "type": "string", "nullable": True} in dd["fields"]
    spec_test = leer(out / "src/test/scala/com/experian/edf/oxygen/curation/source/sisben/SISBENCurationProcessorSpec.scala")
    assert "standardizedIdName" in spec_test


def test_comentarios_de_parte_manual_siguen_la_spec(tmp_path, spec, rendered):
    """Los comentarios finales del processor y de su prueba dependen de la spec, no son texto fijo."""
    out, _ = rendered
    base = leer(out / SCALA_FUENTE / "processor/SISBENCurationProcessor.scala")
    assert "TODO(manual) CUR-PA01 / P2: nombre completo bloqueado" in base  # el ejemplo deja nombreCompleto abierto
    assert "TODO(manual) IN-06 / CUR-PT06" in base

    def cambio(s):  # como SISBEN 1.0.1: sin campos abiertos y con el nombre completo concatenado
        s["campos_abiertos"] = []
        s["campos_curados"].append({
            "destino": "standardizedIdName", "origen": "", "tipo_salida": "texto", "estado": "cerrado",
            "transformacion": {"tipo": "concatenacion", "separador": " ",
                               "campos": ["primerNombre", "segundoNombre", "primerApellido", "segundoApellido"]}})
    rt.render(spec_con_cambios(tmp_path, spec, cambio), tmp_path / "out", TEMPLATES, SCHEMA)
    p = leer(tmp_path / "out" / SCALA_FUENTE / "processor/SISBENCurationProcessor.scala")
    assert "TODO(manual)" not in p
    assert ('// standardizedIdName: concatenacion de primerNombre, segundoNombre, primerApellido, segundoApellido '
            'con separador " "; se genera desde el source-spec (no es parte manual).') in p
    t = leer(tmp_path / "out/src/test/scala/com/experian/edf/oxygen/curation/source/sisben/SISBENCurationProcessorSpec.scala")
    assert "TODO(manual)" not in t and "enrichEventWithPayload devuelve event.payload" in t
    assert rt.parte_manual_comentario({"campos_abiertos": [], "campos_curados": []}) == (
        "  // Sin parte manual: todos los campos de la spec se generan.")


def test_documento_en_mayusculas_no_cambia_la_salida_del_ejemplo():
    for c in json.loads(leer(EXPECTED / "curated-events.json")):
        n = c["curatedRecordJson"].get("numeroDocumento")
        if n is not None:
            assert n == n.upper()


def test_extension_subscriber_relation_genera_override_y_pruebas(tmp_path, spec):
    """F-FW-11 (D14): con extension_subscriber_relation el processor sobrescribe subscriberRelation y lo prueba."""
    ext = {"campos": ["tipoDocumento", "numeroDocumento"], "separador": "_"}
    copia = spec_con_cambios(tmp_path, spec, lambda s: s.update(extension_subscriber_relation=ext))
    rt.render(copia, tmp_path / "out", TEMPLATES, SCHEMA)
    out = tmp_path / "out"
    p = leer(out / SCALA_FUENTE / "processor/SISBENCurationProcessor.scala")
    assert "override def subscriberRelation(event: TaggedValidatedEvent): String = {" in p
    assert 'private val CamposRelacion: Seq[String] = Seq("tipoDocumento", "numeroDocumento")' in p
    assert 'private val SeparadorRelacion: String = "_"' in p
    assert ("SISBENCurationProcessor.relacion(event.validatedRecordJson, fileName, errores, homologacion, "
            "event.subscriberRelation)") in p
    assert "catch { case NonFatal(_) => copiado }" in p
    assert not ("println(" in p or "throw " in p or "{{" in p)
    t = leer(out / "src/test/scala/com/experian/edf/oxygen/curation/source/sisben/SISBENCurationProcessorSpec.scala")
    assert "SISBENCurationProcessor.relacion(" in t and "{{" not in t

    curados = json.loads(leer(out / "expected/curated-events.json"))
    base = json.loads(leer(EXPECTED / "curated-events.json"))
    assert len(curados) == len(base)
    unidos = 0
    for c, b in zip(curados, base):
        rec = c["curatedRecordJson"]
        tipo, num = rec.get("tipoDocumento"), rec.get("numeroDocumento")
        ambos = tipo is not None and num is not None and num.strip() != ""
        esperado = f"{tipo}_{num}" if ambos else b["subscriberRelation"]
        assert c["subscriberRelation"] == esperado, c["eventId"]
        unidos += ambos
        assert rec == b["curatedRecordJson"], "la extensión no cambia el registro curado"
    assert unidos > 0, "el fixture debe ejercitar la unión"


def test_sin_extension_no_hay_override_y_el_hueco_no_queda_vacio(rendered):
    out, _ = rendered
    p = leer(out / SCALA_FUENTE / "processor/SISBENCurationProcessor.scala")
    assert "override def subscriberRelation" not in p
    assert "// subscriberRelation no se sobrescribe" in p
    assert "CamposRelacion" not in p
    assert "// Sin extension_subscriber_relation" in leer(
        out / "src/test/scala/com/experian/edf/oxygen/curation/source/sisben/SISBENCurationProcessorSpec.scala")


# ------------------------------------------------------------------ spec incoherente
def _abierto(s):
    s["campos_curados"].append({"destino": "puntajeSisben", "origen": "", "estado": "abierto",
                                "transformacion": {"tipo": "constante", "valor": None}, "tipo_salida": "texto"})


def _concat(campos):
    def cambio(s):
        s["campos_curados"].append({"destino": "nombreUnido", "origen": "", "estado": "cerrado", "tipo_salida": "texto",
                                    "transformacion": {"tipo": "concatenacion", "campos": campos}})
    return cambio


INCOHERENTES = [
    (lambda s: s.update(extension_subscriber_relation={"campos": ["tipoDocumento", "noExiste"], "separador": "_"}),
     "no se publican"),
    (lambda s: s.update(extension_subscriber_relation={"campos": [], "separador": "_"}), "inválido"),
    (lambda s: s.update(extension_subscriber_relation={"campos": ["tipoDocumento"], "separador": ""}), "inválido"),
    (lambda s: s["parametros"].update(zona_asofdate_etapa_anterior="Marte/Olimpo"), "zona inválida"),
    (lambda s: s["parametros"].update(zona_horaria_fechas_origen="decidido"), "inválido"),
    (lambda s: s["campos_curados"].append(copy.deepcopy(s["campos_curados"][0])), "repetidos"),
    (lambda s: s["campos_curados"][0].update(origen="NO_EXISTE"), "campos_entrada"),
    (lambda s: s["archivo"].update(file_pattern=".*NADA"), "ejemplo"),
    (_abierto, "abiertos"),
    (lambda s: s["fixture"].update(sintetico=False), "inválido"),
    (lambda s: s["parametros"].update(campo_advertencias=s["campos_curados"][0]["destino"]), "campo de advertencias"),
    (lambda s: s["archivo"].update(contraejemplos_sinteticos=[s["archivo"]["ejemplos_sinteticos"][0]]), "contraejemplo"),
    (lambda s: s["codigos_error"].update(SISBEN_X=["NO_EXISTE"]), "inválido"),
    (lambda s: s["codigos_error"].update(SISBEN_X="NO_EXISTE"), "no está en campos_entrada"),
    (_concat(["primerNombre", "posterior"]), "campos anteriores"),
    (_concat([]), "inválido"),
]


@pytest.mark.parametrize("cambio,mensaje", INCOHERENTES)
def test_spec_incoherente_se_rechaza(spec, schema, cambio, mensaje):
    cambio(spec)
    with pytest.raises(rt.RenderError, match=mensaje):
        rt.validar_spec(spec, schema)


def test_reglas_de_transformacion_incoherentes(spec):
    primero = spec["campos_curados"][0]
    t = primero["transformacion"]
    t["tipo"] = "constante"
    with pytest.raises(rt.RenderError, match="no lleva origen"):
        rt.validar_coherencia(spec)
    t["tipo"] = "padding_izquierda"
    t.pop("longitud", None)
    with pytest.raises(rt.RenderError, match="requiere longitud"):
        rt.validar_coherencia(spec)
    t["tipo"] = "desde_archivo"
    primero["origen"] = ""
    with pytest.raises(rt.RenderError, match="requiere atributo"):
        rt.validar_coherencia(spec)
    t["tipo"] = "copia_de"
    t["campo"] = "posterior"
    with pytest.raises(rt.RenderError, match="campo anterior"):
        rt.validar_coherencia(spec)
    t["tipo"] = "concatenacion"
    with pytest.raises(rt.RenderError, match="requiere campos"):
        rt.validar_coherencia(spec)
    t["tipo"] = "texto"
    with pytest.raises(rt.RenderError, match="falta origen"):
        rt.validar_coherencia(spec)


def test_ningun_catalogo_registrado_se_rechaza(spec):
    for c in spec["catalogos"]:
        c["registrar_en_curation"] = False
    with pytest.raises(rt.RenderError, match="registrar_en_curation"):
        rt.lookups_hocon(spec)


def test_campos_abiertos_y_supuestos_comentario(spec):
    assert rt.abiertos({"campos_abiertos": []}) == "  // Sin campos abiertos."
    assert "nombreCompleto" in rt.abiertos(spec)
    texto = rt.supuestos(spec)
    assert "(CUR-PT01)" in texto and "(D8)" in texto
    m = dict(spec["campos_curados"][0], estado="propuesta_de_frontera")
    assert rt.mapeo_scala(m, True).endswith("  // propuesta_de_frontera")
    m = {"destino": "x", "origen": "", "estado": "cerrado", "tipo_salida": "texto",
         "transformacion": {"tipo": "constante", "valor": "V"}}
    assert 'Transformacion.Constante(Some("V"))' in rt.mapeo_scala(m, False)


def test_utilidades():
    assert rt.q('a"b') == '"a\\"b"'
    assert rt.q(None) == '""'
    with pytest.raises(rt.RenderError):
        rt.tq('x"""y')
    assert rt.stem("env/a/curation-common-application-inner.conf") == "curation-common-application-inner"
    assert rt.stem(".oculto") == ".oculto"
    # 2024-03-06 08:00 en -05:00 = 13:00 UTC
    assert rt.fecha_recepcion("X.20240306080000-0500.txt") == 1709730000000
    with pytest.raises(rt.RenderError):
        rt.fecha_recepcion("sin-marca.txt")
    with pytest.raises(rt.RenderError, match="no reconocida"):
        rt.fecha_recepcion("X.20241306080000-0500.txt")
    assert rt.epoch_ms("01/02/2024", "UTC") == rt.epoch_ms("1/2/2024", "UTC")
    assert rt.epoch_ms("2024-02-01 00:00:00", "UTC") == rt.epoch_ms("01/02/2024", "UTC")
    with pytest.raises(rt.RenderError):
        rt.epoch_ms("31/02/2024", "UTC")
    with pytest.raises(rt.RenderError, match="no declarados"):
        rt.rellenar("{{nuevo}}", {}, set(), "x.tpl")
    with pytest.raises(rt.RenderError, match="sin cálculo"):
        rt.rellenar("{{a}}", {}, {"a"}, "x.tpl")
    with pytest.raises(rt.RenderError, match="valor vacío"):
        rt.rellenar("{{a}}", {"a": lambda: " "}, {"a"}, "x.tpl")


def test_huecos_del_esquema_sin_calculo_se_rechazan(tmp_path, schema):
    malo = dict(schema)
    malo["x-placeholders"] = {**schema["x-placeholders"], "nuevo_hueco": {"origen": "x"}}
    ruta = tmp_path / "schema.yaml"
    ruta.write_text(yaml.safe_dump(malo, allow_unicode=True), encoding="utf-8")
    with pytest.raises(rt.RenderError, match="sin cálculo"):
        rt.render(SPEC, tmp_path / "out", TEMPLATES, ruta)


# ------------------------------------------------------------------ línea de comandos
def test_cli_devuelve_error_con_spec_invalido(tmp_path, capsys):
    malo = tmp_path / "spec.yaml"
    malo.write_text("schema_version: '1.0'\n", encoding="utf-8")
    assert rt.main(["--spec", str(malo), "--out", str(tmp_path / "out"), "--schema", str(SCHEMA)]) == 1
    assert capsys.readouterr().err.startswith("ERROR: source-spec inválido")


def test_cli_forma_inesperada(tmp_path, capsys):
    malo = tmp_path / "spec.yaml"
    malo.write_text("- una lista\n", encoding="utf-8")
    vacio = tmp_path / "schema.yaml"
    vacio.write_text("{}\n", encoding="utf-8")
    assert rt.main(["--spec", str(malo), "--out", str(tmp_path / "out"), "--schema", str(vacio)]) == 1
    assert capsys.readouterr().err.startswith("ERROR inesperado:")


def test_cli_ok_y_write_expected(tmp_path, capsys):
    assert rt.main(["--spec", str(SPEC), "--out", str(tmp_path / "out"), "--write-expected", str(tmp_path / "golden")]) == 0
    assert capsys.readouterr().out.startswith("OK: 12 artefactos")
    assert (tmp_path / "golden/resumen.json").read_bytes() == (EXPECTED / "resumen.json").read_bytes()


def test_cli_solo_verificar(tmp_path, capsys):
    assert rt.main(["--spec", str(SPEC), "--out", str(tmp_path / "out"), "--solo-verificar"]) == 0
    assert capsys.readouterr().out.strip() == ("source-spec OK (29 campos curados, 0 con concatenacion; "
                                               "12 plantillas y 7 eventos esperados calculados)")
    assert not (tmp_path / "out").exists(), "solo verifica: no escribe nada"
    malo = tmp_path / "spec.yaml"
    malo.write_text("schema_version: '1.0'\n", encoding="utf-8")
    assert rt.main(["--spec", str(malo), "--solo-verificar"]) == 1
    assert capsys.readouterr().err.startswith("ERROR: source-spec inválido")


def test_reglas_documento_por_tipo_vacio_genera(tmp_path, spec, capsys):
    """RG-014 de SISBEN: la misma regla para todos los tipos (por_tipo vacío) es válida y genera Map() vacío."""
    copia = spec_con_cambios(tmp_path, spec, lambda s: s["reglas_documento"].update(por_tipo={}))
    assert rt.main(["--spec", str(copia), "--solo-verificar"]) == 0
    rt.render(copia, tmp_path / "out", TEMPLATES, SCHEMA)
    cs = leer(tmp_path / "out" / SCALA_FUENTE / "config/SISBENCurationSpec.scala")
    assert ("val ReglasDocumento: Map[String, ReglaDocumento] = Map(\n"
            "    // Sin reglas por tipo: todos los tipos usan ReglaDocumentoPorDefecto.\n  )") in cs


def test_solo_verificar_detecta_fallas_de_generacion(tmp_path, spec, capsys):
    """--solo-verificar hace el mismo cálculo que --out: un fixture inexistente o un hueco vacío fallan igual."""
    copia = spec_con_cambios(tmp_path, spec, lambda s: s["fixture"].update(entrada="fixture/no-existe.yaml"))
    assert rt.main(["--spec", str(copia), "--solo-verificar"]) == 1
    assert "no existe el fixture" in capsys.readouterr().err


def test_cli_errores_de_uso():
    for args in (["--spec"], ["--otro", "x"]):
        with pytest.raises(SystemExit) as e:
            rt.main(args)
        assert e.value.code == 2
