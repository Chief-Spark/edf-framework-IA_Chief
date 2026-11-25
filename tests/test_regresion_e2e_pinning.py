"""Regresión documental de los defectos del framework hallados en la auditoría del insumo de Pinning de la corrida E2E
SISBEN (1.9.8): HF-57 a HF-61 y, en la re-auditoría (1.9.9), HF-63 y HF-64 (`ejecuciones/sisben-e2e/auditorias/pinning.md`).
"""
import json
import re
from pathlib import Path

import yaml

RAIZ = Path(__file__).resolve().parent.parent
KIRO = RAIZ / ".kiro"
PIN = KIRO / "skills" / "edf-pinning"
ANA = KIRO / "skills" / "edf-analisis"
TPL_INSUMO = ANA / "assets" / "templates" / "yaml-skill-pinning.yaml.tpl"
TPL_BLOQUE = PIN / "assets" / "pinning-application-block.conf.template"
LOGICA = ANA / "references" / "logica-pinning.md"
STEERING_11 = KIRO / "steering" / "11-contrato-curation-pinning.md"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


def plano(texto: str) -> str:
    return " ".join(texto.split())


def modo_colombia_del_skill() -> str:
    skill = leer(PIN / "SKILL.md")
    return skill[skill.index("## Modo Colombia"):skill.index("Este skill convierte")]


def entrada_reuse_pin(insumo: dict) -> dict:
    bloque = insumo["claves_configuracion_pinning"]["bloque_oxygen_stage_pinning"]
    return next(e for e in bloque if e["clave"] == "oxygen.stage.pinning.reusePin")


# ── HF-57: reusePin es global; en modo Colombia nunca se escribe ──

def test_hf57_skill_no_manda_escribir_reuse_pin_false_en_modo_colombia():
    seccion = plano(modo_colombia_del_skill())
    assert "con `reusePin = false` en ingesta nueva" not in seccion
    assert "**`reusePin` en modo Colombia: nunca se escribe (HF-57).**" in seccion
    for cita in ("`SparkPinner.scala:223`", "(`:409`)", "(`:979-985`)", "(`:888-905`)"):
        assert cita in seccion, cita
    assert "**la ingesta no tiene generación previa propia**" in seccion
    assert "**N4**" in seccion and "se escala" in seccion


def test_hf57_plantilla_del_bloque_no_trae_reuse_pin_listo_en_modo_colombia():
    texto = leer(TPL_BLOQUE)
    assert "false = pinning dinámico (ingesta nueva" not in texto
    colombia = texto[texto.index("── Modo Colombia"):texto.index("── Fuera de modo Colombia")]
    assert "NO SE ESCRIBE (HF-57)" in colombia and "SparkPinner.scala:223" in colombia and "N4" in colombia
    assert "{{REUSE_PIN_VALOR_FUENTE}}" in colombia and "dato, no se genera" in colombia
    # En la sección de Colombia todo es comentario: ninguna clave activa.
    assert all(not l.strip() or l.lstrip().startswith("#") for l in colombia.splitlines()[1:])
    fuera = texto[texto.index("── Fuera de modo Colombia"):]
    reuse = fuera[fuera.index("Clave GLOBAL"):fuera.index("reusePin = {{REUSE_PIN}}")]
    assert "nunca en modo" in reuse and "HF-57" in reuse


def test_hf57_plantilla_del_insumo_registra_reuse_pin_como_dato():
    texto = leer(TPL_INSUMO)
    assert "false en ingesta nueva" not in texto
    insumo = yaml.safe_load(texto.replace("{{ingesta}}", "x"))
    entrada = entrada_reuse_pin(insumo)
    assert not isinstance(entrada["valor_a_escribir"], bool)
    assert "no se escribe en modo Colombia" in entrada["valor_a_escribir"]
    assert "valor_en_fuente" in entrada and not isinstance(entrada["valor_en_fuente"], bool)
    assert "HF-57" in texto and "N4" in texto


def test_hf57_logica_pinning_8_dice_que_la_clave_es_global():
    texto = leer(LOGICA)
    s8 = plano(texto[texto.index("## 8."):texto.index("### 8.1")])
    assert "### 8.0 `reusePin` es global de la aplicación, no de la ingesta (HF-57)" in s8
    for cita in ("`SparkPinner.scala:223`", "(`:409`)", "(`:979-985`)", "(`:888-905`)", "`PinningIOUtils.scala:23-31`"):
        assert cita in s8, cita
    assert "**la ingesta no tiene generación previa propia**" in s8 and "**nunca se escribe**" in s8
    fila = next(l for l in texto.splitlines() if l.startswith("| `oxygen.stage.pinning.reusePin`"))
    assert "**Global de la aplicación**" in fila


# ── HF-58: colapso silencioso con el matcher o la lista de referencia ──

def test_hf58_advertencia_del_colapso_en_skill_plantilla_y_logica():
    seccion = plano(modo_colombia_del_skill())
    assert "**Colapso silencioso con el matcher o la lista de referencia (HF-58).**" in seccion
    for parte in ("`ISIdentityMatcher`", "`nationalId`, `firstName`, `cellPhone`", "`SparkPinner.scala:1756-1770`",
                  "(`:676`", "(`:719-723`)", "un solo PIN", "«arranque»"):
        assert parte in seccion, parte
    bloque = leer(TPL_BLOQUE)
    assert "COLAPSO SILENCIOSO (HF-58)" in bloque and "SparkPinner.scala:1756-1770" in bloque
    arranque = bloque[bloque.index("ARRANQUE heredado"):bloque.index('identityMatcher = "{{IDENTITY_MATCHER_FQCN}}"')]
    assert "Solo fuera de modo Colombia" in arranque and "HF-58" in arranque
    fila = next(l for l in leer(LOGICA).splitlines() if "(HF-58)" in l and l.startswith("|"))
    assert "**Fail-silent (colapso)**" in fila and "`SparkPinner.scala:1756-1770`" in fila
    assert "HF-58" in leer(TPL_INSUMO)


# ── HF-59: evals de modo Colombia y SKILL sin contradicción ──

def test_hf59_evals_json_valido_con_casos_de_modo_colombia_y_reuse_pin():
    evals = json.loads(leer(PIN / "evals" / "evals.json"))
    casos = {c["id"]: c for c in evals["casos"]}
    assert len(casos) == len(evals["casos"])
    for id_ in ("EVAL-PIN-12", "EVAL-PIN-13", "EVAL-PIN-14"):
        caso = casos[id_]
        assert {"id", "nombre", "prompt", "expected_behavior", "files_relevantes", "falla_si"} <= set(caso)
        assert "Colombia" in caso["nombre"]
        assert (PIN / caso["insumo"]).is_file()
    reuse = " ".join(casos["EVAL-PIN-12"]["expected_behavior"]) + casos["EVAL-PIN-12"]["falla_si"]
    assert "reusePin" in reuse and "N4" in reuse and "NO escribe" in reuse
    colapso = " ".join(casos["EVAL-PIN-13"]["expected_behavior"])
    assert "ISIdentityMatcher" in colapso and "1756-1770" in colapso
    assert "no contiene claves compartidas activas" in " ".join(casos["EVAL-PIN-14"]["expected_behavior"])


def test_hf59_skill_modo_colombia_no_redefine_ninguna_clave_compartida():
    seccion = modo_colombia_del_skill()
    assert "Referenciarlo en `identityMatcher`" not in seccion
    assert "Referenciarla en el bloque HOCON" not in seccion
    assert "**no se redefine ninguna clave compartida**" in seccion
    for clave in ("identifyingAttributesFile", "identityMatcher", "reusePin", "domainDictionaryFile",
                  "clusterSplitter", "nearNeighborBlockSize", "pinningVleRange", "groupSSR"):
        assert f"`{clave}`" in seccion, clave


# ── HF-60: steering 11 al día, verificador del insumo y diferencia umbral / matcher determinista ──

def test_hf60_steering_11_lector_en_oxygen_pinning():
    texto = leer(STEERING_11)
    s1 = plano(texto[texto.index("## 1."):texto.index("## 2.")])
    assert "`[A:lector-oxygen-pinning]`" not in texto
    for cita in ("`PinningIOUtils.scala:23-46`", "`OxygenIOReaderUtils.scala:72-75`", "`INPUT_REF_DIR`", "oxygen-core"):
        assert cita in s1, cita
    fila2 = next(l for l in texto.splitlines() if l.startswith("| 2 |"))
    assert "**Cerrado (HF-60):**" in fila2 and "oxygen-core" in fila2 and "`[A:oxygen-core]`" in fila2


def test_hf60_modo_2_documenta_el_verificador_del_insumo_de_pinning():
    skill = leer(ANA / "SKILL.md")
    modo2 = skill[skill.index("## Modo 2"):skill.index("## Referencias")]
    assert "`python3 tools/verificar-insumo-pinning.py <ingesta-id>`" in modo2
    assert (RAIZ / "tools" / "verificar-insumo-pinning.py").is_file()


def test_hf60_plantilla_registra_umbral_de_la_spec_frente_a_matcher_determinista():
    insumo = yaml.safe_load(leer(TPL_INSUMO).replace("{{ingesta}}", "x"))
    campo = insumo["modo_colombia"]["umbral_y_matcher_determinista"]
    assert set(campo) == {"texto_spec", "lectura_del_matcher", "diferencia", "estado"}
    assert "aplica" in insumo["reglas_matching_por_campo"]
    assert "umbral_y_matcher_determinista" in leer(TPL_INSUMO).split("reglas_matching_por_campo:")[1].split("\n")[1]


# ── HF-61: la evidencia de «null lanza» en Pinning batch es ISIdentityMatcher, no EventPinner ──

def test_hf61_framework_no_cita_event_pinner_como_evidencia():
    for ruta in KIRO.rglob("*"):
        if ruta.is_file() and ruta.suffix in {".md", ".yaml", ".tpl", ".template", ".json", ".conf"}:
            for linea in leer(ruta).splitlines():
                if re.search(r"EventPinner\.scala:\d", linea):
                    assert "No se cita" in linea, f"{ruta}: {linea}"
    s3 = plano(leer(STEERING_11))
    assert "(`ISIdentityMatcher.scala:46-54`): falla la ejecución. Esa es la evidencia de «un `null` lanza»" in s3
    assert "no se usa en el batch" in s3 and "HF-61" in s3


# ── HF-63 (AUD-P-14): artefactos de Pinning por modo en el Orquestador ──

ORQ = KIRO / "skills" / "edf-orquestador"
SEED = ORQ / "templates" / "estado-ingesta.seed.json"
SCHEMA = ORQ / "templates" / "estado-ingesta.schema.json"


def _validador():
    import importlib.util
    spec = importlib.util.spec_from_file_location("validar_expediente_hf63", RAIZ / "tools" / "validar-expediente.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _pinning(estado: dict) -> dict:
    return next(e for e in estado["etapas"] if e["id"] == "pinning")


def test_hf63_semilla_trae_artefactos_de_pinning_de_modo_colombia():
    import jsonschema
    seed = json.loads(leer(SEED))
    jsonschema.validate(seed, json.loads(leer(SCHEMA)))
    arts = {a["id"]: a for a in _pinning(seed)["artefactos"]}
    assert arts["ART-050"]["obligatorio"] and arts["ART-053"]["obligatorio"]
    for aid in ("ART-051", "ART-052"):
        assert arts[aid]["obligatorio"] is False and "no aplica en modo Colombia" in arts[aid]["nombre"], aid
    bloque = arts["ART-053"]["nombre"]
    assert "sin claves compartidas" in bloque and "sin redefinirlas ni escribir reusePin" in bloque
    assert "reusePIN = false" not in leer(SEED)


def test_hf63_validador_acepta_pinning_de_modo_colombia_aprobada_sin_matcher_propio():
    mod = _validador()
    estado = json.loads(leer(SEED))
    pin = _pinning(estado)
    pin["estado"] = "aprobada"
    for a in pin["artefactos"]:
        if a["obligatorio"]:
            a["estado_artefacto"] = "aprobado"
    assert not any(f.startswith("7e.") for f in mod.reglas_gobierno(estado))
    # En modo propio el matcher es obligatorio: la misma etapa sin matcher aprobado no pasa la regla 7e.
    next(a for a in pin["artefactos"] if a["id"] == "ART-051")["obligatorio"] = True
    assert any(f.startswith("7e.") and "ART-051" in f for f in mod.reglas_gobierno(estado))


def test_hf63_contrato_04_y_prompt_00_distinguen_los_dos_modos():
    c04 = leer(ORQ / "references" / "04-contratos-por-skill.md")
    etapa4 = plano(c04[c04.index("## Etapa 4"):c04.index("## Etapa 5")])
    for frase in ("Modo propio", "Modo Colombia", "`obligatorio: false`", "sin escribir `reusePin`", "**sin claves compartidas**"):
        assert frase in etapa4, frase
    assert "**`false` en ingesta nueva**" not in etapa4
    p00 = plano(leer(ORQ / "prompts" / "00-iniciar-ingesta.md"))
    assert "si sí, `reusePIN = false`" not in p00
    for frase in ("**Modo de Pinning**", "**modo Colombia**", "**modo propio**", "matcher compartido de Colombia",
                  "pregunta abierta bloqueante de `pinning`", "`edf-pinning` nunca lo escribe (HF-57)"):
        assert frase in p00, frase


# ── HF-64 (AUD-P-16): marcador del umbral frente al matcher determinista ──

def test_hf64_plantilla_distingue_a_de_pm_con_verificacion_diferida():
    tpl = leer(TPL_INSUMO)
    bloque = plano(tpl[tpl.index("umbral_y_matcher_determinista:"):tpl.index("mapeo_identidad:")])
    assert "[A:<ID>] mientras la pregunta <ID> siga abierta" in bloque
    assert '"[PM] (RG-nnn)"' in bloque and "§3e" in bloque
