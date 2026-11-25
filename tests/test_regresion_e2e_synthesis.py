"""Regresión documental de los defectos del framework hallados en la auditoría del insumo de Synthesis de la corrida
E2E SISBEN (1.9.10): HF-68 a HF-72 (`ejecuciones/sisben-e2e/auditorias/synthesis.md` §2-§3).

Las herramientas (verificar-artefactos-etapa.py --modo, verificar-claves.py) tienen sus pruebas en
test_verificar_artefactos_etapa.py y test_verificar_claves.py.
"""
import json
import re
from pathlib import Path

import yaml

RAIZ = Path(__file__).resolve().parent.parent
KIRO = RAIZ / ".kiro"
SYN = KIRO / "skills" / "edf-synthesis"
ANA = KIRO / "skills" / "edf-analisis"
ORQ = KIRO / "skills" / "edf-orquestador"
SEED = ORQ / "templates" / "estado-ingesta.seed.json"
SCHEMA = ORQ / "templates" / "estado-ingesta.schema.json"
TPL_BLOQUE = SYN / "assets" / "consistency-application-block.conf.template"
TPL_INSUMO = ANA / "assets" / "templates" / "yaml-skill-synthesis.yaml.tpl"
LOGICA = ANA / "references" / "logica-synthesis.md"
DICCIONARIOS = SYN / "references" / "diccionarios-oxygen-synthesis.md"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


def plano(texto: str) -> str:
    return " ".join(texto.split())


def skill() -> str:
    return leer(SYN / "SKILL.md")


def modo_colombia_del_skill() -> str:
    s = skill()
    return plano(s[s.index("## Modo Colombia"):s.index("Este skill convierte")])


def _synthesis(estado: dict) -> dict:
    return next(e for e in estado["etapas"] if e["id"] == "synthesis")


# ── HF-68: modo Colombia de edf-synthesis (configuración compartida) ──

def test_hf68_skill_tiene_modo_colombia_sin_claves_compartidas():
    sec = modo_colombia_del_skill()
    assert "`SparkConsistencyChecker.scala:241-245`" in sec and "todo el HC de Colombia" in sec
    assert "**no se escribe ninguna clave compartida**" in sec
    for frase in ("**las entradas del GDD de la ingesta** (ART-061)", "Es la pieza que sí aporta la ingesta",
                  "(ART-062): solo comentarios", "`VERIFICAR <ruta HOCON completa>`",
                  "--modo colombia", "`modo_colombia.aplica`", "prompts/00-iniciar-ingesta.md"):
        assert frase in sec, frase
    for clave in ("`paths.dictionaryFile`", "`paths.customRulesFile`", "`dateType`", "`checkFileTagEnabled`",
                  "`eventTypeList`", "`fieldHandlerHashFunction`", "`orchestration`", "`datasetFormat`",
                  "`historicAttributeArgs`"):
        assert clave in sec, clave
    paso4 = plano(skill()[skill().index("### 4. Genera el bloque HOCON"):skill().index("### 5.")])
    assert "En modo Colombia usa solo la sección «Modo Colombia» de la plantilla" in paso4


def test_hf68_plantilla_del_bloque_tiene_seccion_colombia_solo_de_comentarios():
    texto = leer(TPL_BLOQUE)
    colombia = texto[texto.index("── Modo Colombia"):texto.index("── Fuera de modo Colombia")]
    assert all(not l.strip() or l.lstrip().startswith("#") for l in colombia.splitlines()[1:])
    assert "SparkConsistencyChecker.scala:241-245" in colombia and "--modo colombia" in colombia
    verificar = [l for l in colombia.splitlines() if "VERIFICAR oxygen.stage.consistencyCheck." in l]
    assert len(verificar) == 11
    fuera = texto[texto.index("── Fuera de modo Colombia"):]
    assert re.search(r"^oxygen\.stage\.consistencyCheck \{", fuera, re.M)


def test_hf68_semilla_trae_artefactos_de_synthesis_de_modo_colombia():
    import jsonschema
    seed = json.loads(leer(SEED))
    jsonschema.validate(seed, json.loads(leer(SCHEMA)))
    arts = {a["id"]: a for a in _synthesis(seed)["artefactos"]}
    assert all(arts[a]["obligatorio"] for a in ("ART-060", "ART-061", "ART-062"))
    assert all(arts[a]["ruta"] == "propuestas/synthesis/" for a in ("ART-061", "ART-062"))
    gdd, bloque = arts["ART-061"]["nombre"], arts["ART-062"]["nombre"]
    assert "integrar en el GDD compartido de Colombia" in gdd and "sin duplicar ni modificar" in gdd
    assert "lista de verificacion" in bloque and "VERIFICAR" in bloque and "rutas HOCON completas" in bloque
    assert "sin redefinirlas ni escribir claves" in bloque and arts["ART-062"]["riesgo"] == "alto"


def test_hf68_validador_acepta_synthesis_de_modo_colombia_aprobada():
    import importlib.util
    spec = importlib.util.spec_from_file_location("validar_expediente_hf68", RAIZ / "tools" / "validar-expediente.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    estado = json.loads(leer(SEED))
    syn = _synthesis(estado)
    syn["estado"] = "aprobada"
    for a in syn["artefactos"]:
        a["estado_artefacto"] = "aprobado"
    assert not any(f.startswith("7e.") and "ART-06" in f for f in mod.reglas_gobierno(estado))
    next(a for a in syn["artefactos"] if a["id"] == "ART-062")["estado_artefacto"] = "propuesto"
    assert any(f.startswith("7e.") and "ART-062" in f for f in mod.reglas_gobierno(estado))


def test_hf68_contrato_04_y_prompt_00_distinguen_los_dos_modos():
    c04 = leer(ORQ / "references" / "04-contratos-por-skill.md")
    etapa5 = plano(c04[c04.index("## Etapa 5"):c04.index("## Etapa 6")])
    for frase in ("**modo de Synthesis**", "Modo propio", "Modo Colombia", "**entradas del GDD de la ingesta**",
                  "**lista de verificación**", "**sin escribir claves** (`--modo colombia`)", "(`--modo propio`)",
                  "`SparkConsistencyChecker.scala:241-245`", "`modo_colombia.aplica`"):
        assert frase in etapa5, frase
    assert "| Out | Bloque HOCON `oxygen.stage.consistencyCheck` | ✅ |" not in etapa5
    p00 = plano(leer(ORQ / "prompts" / "00-iniciar-ingesta.md"))
    for frase in ("**Modo de Synthesis**", "pregunta abierta bloqueante de `synthesis`",
                  "ART-062 es la lista de verificación", "sin escribir claves compartidas",
                  "«bloque HOCON oxygen.stage.consistencyCheck con raíz y customRulesFile»"):
        assert frase in p00, frase


def test_hf68_plantilla_del_insumo_declara_el_modo():
    insumo = yaml.safe_load(leer(TPL_INSUMO))
    assert "aplica" in insumo["modo_colombia"]
    assert "modo_colombia" in leer(TPL_INSUMO).splitlines()[2]


# ── HF-69: eventTypeList guarda tipos de archivo; dateType y checkFileTagEnabled no agrupan por periodo ──

def test_hf69_logica_synthesis_describe_eventtypelist_y_date_type_bien():
    texto = leer(LOGICA)
    filas = {c: next(l for l in texto.splitlines() if l.startswith(f"| `oxygen.stage.consistencyCheck.{c}`"))
             for c in ("eventTypeList", "dateType", "checkFileTagEnabled")}
    assert "debe sustituirse con el tipo de evento real" not in filas["eventTypeList"]
    assert "**Tipos de archivo**" in filas["eventTypeList"] and "`listFileTypes`" in filas["eventTypeList"]
    assert "**No filtra eventos**" in filas["eventTypeList"]
    assert "**No agrupa por periodo.**" in filas["dateType"] and "**No agrupa por periodo**" in filas["checkFileTagEnabled"]
    assert "usado para agrupar por periodo" not in texto and "para agrupar eventos en periodos" not in texto


def test_hf69_skill_y_plantilla_del_bloque_no_tratan_eventtypelist_como_event_type():
    s = plano(skill())
    assert "agrega únicamente el `eventType` real" not in s
    assert "guarda **tipos de archivo** (`fileTag`), no el `eventType`" in s and "**No filtra eventos**" in s
    assert "**no agrupan por periodo**" in s
    tpl = leer(TPL_BLOQUE)
    assert '["{{EVENT_TYPE}}"]' not in tpl and "usado para agrupar por periodo" not in tpl
    assert "listFileTypes" in tpl and "NO filtra eventos" in tpl
    conv = plano(leer(SYN / "references" / "convenciones-oxygen-synthesis.md"))
    assert "el único valor que corresponde es el `eventType` real" not in conv and "**No filtra eventos**" in conv


def test_hf69_plantilla_del_insumo_no_prellena_event_type_list():
    tpl = leer(TPL_INSUMO)
    assert '["{{ingesta}}"]' not in tpl
    insumo = yaml.safe_load(tpl)
    claves = {c["clave"]: c for c in insumo["claves_configuracion_synthesis"]["claves"]}
    assert claves["oxygen.stage.consistencyCheck.eventTypeList"]["valor_a_escribir"] == ""
    bloque = tpl[tpl.index('"oxygen.stage.consistencyCheck.eventTypeList"'):tpl.index('"oxygen.stage.consistencyCheck.dateType"')]
    for frase in ("TIPOS DE ARCHIVO", "listFileTypes", "NO filtra eventos", "checkFileTagEnabled", "modo Colombia no se escribe"):
        assert frase in bloque, frase


# ── HF-71: GDD versión 2 en las references; forma de campos_a_generar ──

def test_hf71_references_alineadas_con_el_gdd_v2():
    for ruta in (LOGICA, DICCIONARIOS):
        texto = leer(ruta)
        assert '`{"domain": "<nombre>", "version": 2, "fields": [...]}`' in texto, ruta
        assert '"version": 1, "fields"' not in texto, ruta
        filas = [l for l in texto.splitlines() if re.match(r"^\| `(struct|singleValued)` \|", l)]
        assert filas == [], ruta
        for clave in ("complexTypeDefinition", "dataType", "description", "section", "enabled"):
            assert f"| `{clave}` |" in texto, (ruta, clave)
        assert "DomainDictionaryV2.java:46-56" in texto and "solo histórica" in texto, ruta
    assert "no se profundizó en las diferencias exactas" not in leer(LOGICA)


def test_hf71_plantilla_del_insumo_define_la_forma_de_campos_a_generar():
    insumo = yaml.safe_load(leer(TPL_INSUMO))
    entrada = insumo["instruccion_generacion_gdd"]["contenido_a_escribir"]["campos_a_generar"][0]
    assert set(entrada) == {"name", "description", "handler"}
    assert "nombre_en_gdd está deprecado" in leer(TPL_INSUMO)


# ── HF-72: EVAL-SYN-04, cita del README inexistente, reference de nivel 3 superada ──

def test_hf72_evals_validos_y_eval_syn_04_ya_no_espera_gdd_vacio():
    evals = json.loads(leer(SYN / "evals" / "evals.json"))
    casos = {c["id"]: c for c in evals["casos"]}
    assert evals["version"] == "1.2.0" and "EVAL-SYN-13" in casos
    e4 = casos["EVAL-SYN-04"]
    texto = json.dumps(e4, ensure_ascii=False)
    assert "vacío es el resultado correcto" not in texto and '\\"fields\\": []' not in texto
    assert "LFP" in e4["nombre"] and "34" in texto and "spec-sisben.md#12" in texto and "R-25" in texto
    assert "GDD generado queda vacío" in e4["falla_si"]
    assert "--modo colombia" in json.dumps(casos["EVAL-SYN-13"], ensure_ascii=False)
    assert "no filtra eventos" in json.dumps(casos["EVAL-SYN-10"], ensure_ascii=False)
    ps1 = leer(SYN / "evals" / "verificar-artefacto.ps1")
    assert "insumo SISBEN sin handler confirmado" not in ps1 and "Ya NO es el caso de" in ps1


def test_hf72_ninguna_cita_al_readme_inexistente():
    readme = "Diseño/templates-scala/synthesis/README.md"
    assert not (RAIZ / readme).exists()
    for ruta in SYN.rglob("*"):
        if ruta.is_file() and ruta.suffix in {".md", ".json", ".yaml", ".template", ".ps1"}:
            assert readme not in leer(ruta), ruta
    assert "(§12) confirma que no hay `SynthesisProcessor`" in plano(skill())
    assert "es un **ejemplo histórico (no es fuente)**" in plano(skill())


def test_hf72_reference_de_nivel_3_anotada_como_superada():
    texto = leer(ANA / "references" / "fuentes" / "curation-pinning-sisben.md")
    for seccion, siguiente in (("### 6. Contexto y Rol de la Etapa", "### 7."),
                               ("### 8. Reglas Específicas de Pinning SISBEN", "### 9.")):
        bloque = plano(texto[texto.index(seccion):texto.index(siguiente)])
        assert "**SUPERADO PARA SISBEN (RG-010, HF-72).**" in bloque, seccion
        assert "SISBEN **sí pasa por Synthesis**" in bloque and "`spec-sisben.md` §12" in bloque
        assert "Manda siempre la spec" in bloque
