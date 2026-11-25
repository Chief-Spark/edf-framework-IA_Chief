"""Regresión documental de los defectos del framework hallados en la auditoría del insumo de State Calculation de la
corrida E2E SISBEN (1.9.11): HF-75 a HF-77 (`ejecuciones/sisben-e2e/auditorias/statecalc.md` §3).

Las herramientas (verificar-artefactos-etapa.py statecalc, verificar-claves.py; HF-73, HF-74 y la parte mecánica de
HF-75 y HF-77) tienen sus pruebas en test_verificar_artefactos_etapa.py y test_verificar_claves.py.
"""
import json
import re
from pathlib import Path

import yaml

RAIZ = Path(__file__).resolve().parent.parent
KIRO = RAIZ / ".kiro"
SC = KIRO / "skills" / "edf-statecalc"
ANA = KIRO / "skills" / "edf-analisis"
ORQ = KIRO / "skills" / "edf-orquestador"
SEED = ORQ / "templates" / "estado-ingesta.seed.json"
SCHEMA = ORQ / "templates" / "estado-ingesta.schema.json"
GOLDEN = SC / "evals" / "fixtures" / "golden"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


def plano(texto: str) -> str:
    return " ".join(texto.split())


def skill() -> str:
    return leer(SC / "SKILL.md")


def _statecalc(estado: dict) -> dict:
    return next(e for e in estado["etapas"] if e["id"] == "state_calculation")


# ── HF-75: el CALCULATED lee el registro Foundation; el DERIVED de origen va declarado ──

def test_hf75_textos_declaran_el_derived_antes_del_calculated():
    esquema = leer(SC / "references" / "esquema-foundation-calculation.md")
    logica = leer(ANA / "references" / "logica-statecalc.md")
    tpl = leer(SC / "assets" / "foundation-fields.json.template")
    calc = '{ "name": "personIdType", "dataType": "INTEGER", "type": "CALCULATED", "value": "return field.standardizedIdType" }'
    for texto in (esquema, logica):
        i = texto.index(calc)
        assert '"name": "standardizedIdType"' in texto[max(0, i - 200):i], "falta el DERIVED antes del CALCULATED"
        assert "registro Foundation" in texto and "FoundationStateCalculator.scala:52-63" in texto
    i = tpl.index('"value": "return field.standardizedIdType"')
    assert '{ "name": "standardizedIdType", "dataType": "INTEGER" }' in tpl[i - 900:i]
    assert "REGISTRO FOUNDATION" in tpl
    paso2 = plano(skill()[skill().index("### 2. Genera"):skill().index("### 3. Genera")])
    assert "**En un CALCULATED, `field` es el registro Foundation, no el evento curado**" in paso2
    assert "**El `name` de un DERIVED es la clave curada que lee**" in paso2


def test_hf75_golden_y_fixtures_declaran_el_derived():
    for p in sorted((SC / "evals" / "fixtures").rglob("statecalc-foundation-local.json")):
        texto = leer(p)
        if "return field.standardizedId" not in texto:
            continue
        for x in set(re.findall(r"return field\.(standardizedId\w+)", texto)):
            assert f'"name": "{x}"' in texto, (p, x)


def test_hf75_verificador_ps1_espera_los_4_campos_del_camino_feliz():
    ps1 = leer(SC / "evals" / "verificar-artefacto.ps1")
    assert "if ($campos.Count -ne 4)" in ps1 and "if ($campos.Count -ne 2)" not in ps1
    assert "$permitidos = @('standardizedIdType', 'standardizedIdNumber', 'personIdType', 'personIdNumber')" in ps1
    arnes = leer(SC / "evals" / "probar-arnes.ps1")
    assert "exactamente 4 campos" in arnes and "mismos 4 elemento" in arnes and "exactamente 2" not in arnes
    campos = json.loads(leer(GOLDEN / "sisben-camino-feliz" / "statecalc-foundation-local.json"))["fields"]
    assert len(campos) == 4


def test_hf75_hf77_evals_json_valido_con_los_casos_nuevos():
    ev = json.loads(leer(SC / "evals" / "evals.json"))
    assert ev["version"] == "1.2.0"
    casos = {c["id"]: c for c in ev["casos"]}
    assert "exactamente 4 entradas" in casos["EVAL-SC-04"]["expected_behavior"][0]
    assert "standardizedIdType" in casos["EVAL-SC-07"]["expected_behavior"][2]
    c14 = plano(json.dumps(casos["EVAL-SC-14"], ensure_ascii=False))
    for frase in ("MostRecentValue.scala:20-24", "QueryFunctions.scala:75-82", "ContextUtils.scala:15-19",
                  "deltaMode", "--modo colombia", "[A:OQ-TR-2]"):
        assert frase in c14, frase


# ── HF-76: el patrón con flag queda condicionado a OQ-TR-2; migrate.md es antecedente ──

def test_hf76_steering_07_condiciona_el_patron_isSisben_a_oq_tr_2():
    t = leer(KIRO / "steering" / "07-contratos-frontera.md")
    sec = plano(t[t.index("### 5.7 State Calculation"):t.index("## 6.")])
    assert "un flag aditivo por ingesta (patrón `isSisben`)." not in sec
    assert "**Patrón «flag aditivo por ingesta» (`isSisben`): condicionado a `[A:OQ-TR-2]`.**" in sec
    assert "Mientras OQ-TR-2 siga abierta, el patrón no se genera" in sec
    assert "**No generar configuración que dependa de Nashorn sin resolverlo** `[A:OQ-TR-2]`" in sec
    assert "Antecedente, no cierre: `Motor-Oxigen/oxygen-statecalculation/migrate.md:12-16`" in sec
    assert "**el JDK del runtime del fork de Colombia**" in sec and "**la decisión de Experian**" in sec
    assert "solo DERIVED** no depende de Nashorn" in sec
    t06 = leer(KIRO / "steering" / "06-riesgo-y-aprobaciones.md")
    assert "migrate.md:12-16" in t06 and "patrón de flag `isSisben`" in t06


# ── HF-77: modo Colombia de edf-statecalc ──

def test_hf77_skill_tiene_modo_colombia():
    s = skill()
    sec = plano(s[s.index("## Modo Colombia"):s.index("Este skill convierte")])
    for frase in ("**sin repetir un `name` (ni alias) existente**", "el último gana para **todas** las fuentes",
                  "`ContextUtils.scala:15-19`", "**Toda sección filtra por fuente**", "`MostRecentValue.scala:20-24`",
                  "`[A:OQ-TR-2]`", "nunca se entrega sin filtro", "`QueryFunctions.scala:75-82`", "**sin `#`**",
                  "**modo delta**", "`SparkStateCalculator.scala:187-188`", "(`:207-213`)", "`:246-271`",
                  "**conserva su XPM anterior**", "--modo colombia", "--campos-existentes", "--secciones-existentes",
                  "`modo_colombia.aplica`", "prompts/00-iniciar-ingesta.md", "(ART-071)", "(ART-072)", "ART-073"):
        assert frase in sec, frase
    assert "`references/modo-colombia.md`" in s
    ref = leer(SC / "references" / "modo-colombia.md")
    for frase in ("ContextUtils.scala:15-19", "MostRecentValue.scala:16-29", "QueryFunctions.scala:75-82",
                  "SparkStateCalculator.scala:187-188", "getAndSaveUnchangedV2", "un PIN sin eventos nuevos conserva su XPM anterior",
                  "CalculationStateCalculator.scala:123-143", "--modo colombia"):
        assert frase in ref, frase


def test_hf77_sin_afirmacion_del_passthrough_ni_cita_inexistente():
    assert not (RAIZ / "Diseño" / "templates-scala" / "statecalc" / "README.md").exists()
    for p in [SC / "SKILL.md", *sorted((SC / "references").glob("*.md")), *sorted((SC / "assets").iterdir()),
              ORQ / "references" / "04-contratos-por-skill.md"]:
        t = plano(leer(p))
        assert "ya se confirmó que el passthrough" not in t, p
        assert "confirmado para SISBEN)" not in t, p
        # la cita solo puede aparecer en la nota que explica que no existe
        for m in re.finditer(r"templates-scala/statecalc/README\.md", t):
            assert "no existe" in t[m.start():m.start() + 200], p
    desc = skill().split("---")[1]
    assert "el passthrough `MOST_RECENT_VALUE` evita" not in desc and "modo Colombia" in desc
    ejemplo = leer(ANA / "assets" / "examples" / "sisben" / "yaml-skill-statecalc.yaml")
    assert "SUPERADO (HF-77)" in ejemplo


def test_hf77_caso_historico_rotulado():
    s = plano(skill())
    assert "En el ejemplo histórico del piloto SISBEN (no es fuente" in s
    assert "Para la ingesta de referencia de este proyecto, de 25 campos" not in s
    for ref in ("decisiones-pendientes.md", "esquema-foundation-calculation.md"):
        t = plano(leer(SC / "references" / ref))
        assert "ingesta de referencia de este proyecto (SISBEN), de 25" not in t, ref
        assert "ejemplo histórico del piloto SISBEN" in t, ref


def test_hf77_plantillas_de_la_skill():
    calc = leer(SC / "assets" / "calculation-section.json.template")
    assert "MODO COLOMBIA (HF-77" in calc and "aggregated_where_<name>" in calc and "MostRecentValue.scala:20-24" in calc
    found = leer(SC / "assets" / "foundation-fields.json.template")
    assert "MODO COLOMBIA (HF-77" in found and "ContextUtils.scala:15-19" in found


def test_hf77_semilla_trae_artefactos_de_statecalc_de_modo_colombia():
    import jsonschema
    seed = json.loads(leer(SEED))
    jsonschema.validate(seed, json.loads(leer(SCHEMA)))
    arts = {a["id"]: a for a in _statecalc(seed)["artefactos"]}
    assert all(arts[a]["obligatorio"] for a in ("ART-070", "ART-071", "ART-072", "ART-073"))
    f, c, ca1 = arts["ART-071"]["nombre"], arts["ART-072"]["nombre"], arts["ART-073"]["nombre"]
    assert "integrar en el statecalc-foundation compartido de Colombia" in f and "sin repetir un name ni alias" in f
    assert "where de filtro por fuente" in c and "sin #" in c and "[A:OQ-TR-2]" in c and arts["ART-072"]["riesgo"] == "critico"
    assert "XPM compartido" in ca1 and "modo delta" in ca1 and "conserva su XPM anterior" in ca1
    assert arts["ART-073"]["riesgo"] == "critico"


def test_hf77_validador_acepta_state_calculation_de_modo_colombia_aprobada():
    import importlib.util
    spec = importlib.util.spec_from_file_location("validar_expediente_hf77", RAIZ / "tools" / "validar-expediente.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    estado = json.loads(leer(SEED))
    sc = _statecalc(estado)
    sc["estado"] = "aprobada"
    for a in sc["artefactos"]:
        a["estado_artefacto"] = "aprobado"
    assert not any(f.startswith("7e.") and "ART-07" in f for f in mod.reglas_gobierno(estado))
    next(a for a in sc["artefactos"] if a["id"] == "ART-072")["estado_artefacto"] = "propuesto"
    assert any(f.startswith("7e.") and "ART-072" in f for f in mod.reglas_gobierno(estado))


def test_hf77_contrato_04_y_prompt_00_distinguen_los_dos_modos():
    c04 = leer(ORQ / "references" / "04-contratos-por-skill.md")
    etapa6 = plano(c04[c04.index("## Etapa 6"):c04.index("## Diferencias")])
    for frase in ("**modo de State Calculation**", "Modo propio", "Modo Colombia",
                  "**entradas `fields[]` de la ingesta**", "**sin repetir un `name` ni alias existente**",
                  "**cada una con `where` de filtro por fuente**", "sin `#`", "(`--modo colombia`)", "**modo delta**",
                  "**lista de verificación del fork**", "`modo_colombia.aplica`", "`[A:CQ-006]`",
                  "«fields[] de statecalc-foundation-local.json»"):
        assert frase in etapa6, frase
    assert "| Out | Bloque `fields[]` de `statecalc-foundation-local.json` | ✅ |" not in etapa6
    p00 = plano(leer(ORQ / "prompts" / "00-iniciar-ingesta.md"))
    for frase in ("**Modo de State Calculation**", "pregunta abierta bloqueante de `state_calculation`",
                  "ART-072 las secciones con `where` de filtro por fuente", "incluido el modo delta",
                  "«CalculationSection de statecalc-calculation-local.json»"):
        assert frase in p00, frase


def test_hf77_plantilla_del_insumo_declara_el_modo():
    tpl = leer(ANA / "assets" / "templates" / "yaml-skill-statecalc.yaml.tpl")
    insumo = yaml.safe_load(tpl)
    assert {"aplica", "filtro_fuente", "nombres_existentes_verificados", "modo_delta"} <= set(insumo["modo_colombia"])
    assert "modo_colombia" in tpl.splitlines()[2]
