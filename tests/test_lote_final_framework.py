"""Regresión del lote final de defectos del framework de la corrida E2E SISBEN (`ejecuciones/sisben-e2e/
hallazgos-framework.md`): HF-42, HF-47 (edf-analisis y edf-validation), HF-65 a HF-67 (edf-pinning), HF-78 y HF-79
(edf-synthesis y transversal). Los de los generadores con suite propia (HF-13, HF-44, HF-45, HF-48 a HF-55) están en
`scripts/tests/test_lote_final.py` de cada skill (y en `test_render_templates.py` de edf-validation).

Los `.ps1` no se ejecutan (PowerShell excluido): se comprueba su texto y que sus listas coincidan con las de Python.
"""
import importlib.util
import re
import shutil
from pathlib import Path

import pytest
import yaml

RAIZ = Path(__file__).resolve().parent.parent
SKILLS = RAIZ / ".kiro" / "skills"
ANA = SKILLS / "edf-analisis"
PIN = SKILLS / "edf-pinning"
SYN = SKILLS / "edf-synthesis"
FIXTURE_COL = PIN / "evals" / "fixtures" / "yaml-skill-pinning-colombia-sintetico.yaml"


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8")


def cargar(ruta: Path, nombre: str):
    spec = importlib.util.spec_from_file_location(nombre, ruta)
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


@pytest.fixture(scope="module")
def rb():
    return cargar(PIN / "scripts" / "render_bloque_colombia.py", "render_bloque_colombia")


# ── HF-42: plantillas de la etapa 0 sin estado; interpretar-salida sin el customReason histórico ──

@pytest.mark.parametrize("tpl", ["contexto-ingesta.md.tpl", "mapeo-campos.md.tpl", "dependencias.md.tpl",
                                 "plan-de-ruta.md.tpl"])
def test_hf42_plantillas_etapa0_sin_estado_propuesto(tpl):
    texto = leer(ANA / "assets" / "templates" / tpl)
    assert "PROPUESTO" not in texto
    assert not re.search(r"^\| Estado \|", texto, re.M), tpl


def test_hf42_interpretar_salida_no_fija_custom_reason_true():
    texto = leer(SKILLS / "edf-validation" / "references" / "interpretar-salida.md")
    assert "`customReasonForAutomaticRejectionEnabled = true` |" not in texto
    fila = next(l for l in texto.splitlines() if l.startswith("| Rechazo automático"))
    assert "lo fija el insumo de Acceptance" in fila and "HF-42" in fila


# ── HF-47: [PM] = pendiente-manual (steering 05 §3) ──

def test_hf47_leyenda_de_marcadores_dice_pendiente_manual():
    steering = leer(RAIZ / ".kiro" / "steering" / "05-convenciones.md")
    assert "| `[PM]` | PENDIENTE-MANUAL" in steering
    skill = leer(ANA / "SKILL.md")
    assert "`[PM]` **pendiente-manual**" in skill and "«propuesta del modelo»" in skill and "HF-47" in skill
    contrato = leer(ANA / "assets" / "templates" / "contrato-curation-pinning.md.tpl")
    assert "`[PM]` pendiente-manual" in contrato
    for ruta in (ANA / "assets" / "templates").glob("*.tpl"):
        assert "propuesta del modelo" not in leer(ruta).lower(), ruta.name


# ── HF-65: lista VERIFICAR del bloque en modo Colombia ──

def seccion_colombia() -> str:
    texto = leer(PIN / "assets" / "pinning-application-block.conf.template")
    return texto[texto.index("── Modo Colombia"):texto.index("── Fuera de modo Colombia")]


def test_hf65_plantilla_nombra_las_rutas_que_lee_el_motor(rb):
    colombia = seccion_colombia()
    verificar = "\n".join(l for l in colombia.splitlines() if "VERIFICAR" in l)
    for clave in rb.VERIFICAR_COLOMBIA:
        assert clave in verificar, clave
    assert "VERIFICAR identifyingAttributesFile" not in colombia  # sin la ruta paths.
    for nombre in ("clusterSplitter", "INPUT_REF_DIR", "integración estática con HBase"):
        linea = colombia[colombia.index(f"VERIFICAR {nombre}"):]
        assert "[PM]" in linea[:linea.index("VERIFICAR", 10)], nombre
    assert "Mecanismo [P]" in colombia  # colapso: la construcción de Identity está en oxygen-core


def test_hf65_cabecera_generica_solo_vale_fuera_de_modo_colombia():
    texto = leer(PIN / "assets" / "pinning-application-block.conf.template")
    antes = texto[:texto.index("── Fuera de modo Colombia")]
    assert "Cada clave trae su propio comentario" not in antes
    fuera = texto[texto.index("── Fuera de modo Colombia"):]
    assert "Cada clave trae su propio comentario" in fuera and "NO reemplaza pinning-application.conf" in fuera
    assert "HF-65" in antes


# ── HF-66: generador mínimo del bloque en modo Colombia ──

def test_hf66_genera_desde_el_insumo_y_es_reproducible(rb):
    bloque = rb.generar(FIXTURE_COL)
    assert bloque == rb.generar(FIXTURE_COL)  # mismo insumo → mismo archivo
    assert rb.verificar_bloque(bloque) == []
    assert "COLSINT" in bloque and "identifying-attributes-new.json" in bloque and "ColombiaIdentityMatcher" in bloque
    assert "reusePIN = false (spec sintética §11" in bloque and "{{" not in bloque
    assert not [l for l in bloque.splitlines() if l.strip() and not l.lstrip().startswith("#")]
    assert seccion_colombia().splitlines()[0].split("── Modo Colombia")[1] in bloque


def test_hf66_rechaza_insumo_fuera_de_modo_colombia_o_con_reuse_pin_para_escribir(rb, tmp_path):
    insumo = yaml.safe_load(leer(FIXTURE_COL))
    insumo["modo_colombia"]["aplica"] = False
    ruta = tmp_path / "yaml-skill-pinning.yaml"
    ruta.write_text(yaml.safe_dump(insumo, allow_unicode=True, sort_keys=False), encoding="utf-8")
    with pytest.raises(ValueError, match="modo_colombia.aplica"):
        rb.generar(ruta)
    insumo["modo_colombia"]["aplica"] = True
    reuse = next(e for e in insumo["claves_configuracion_pinning"]["bloque_oxygen_stage_pinning"]
                 if e["clave"].endswith("reusePin"))
    reuse["valor_a_escribir"] = "false"
    ruta.write_text(yaml.safe_dump(insumo, allow_unicode=True, sort_keys=False), encoding="utf-8")
    with pytest.raises(ValueError, match="verificar-insumo-pinning"):
        rb.generar(ruta)
    assert rb.main(["--insumo", str(ruta)]) == 1
    assert rb.main(["--insumo", str(tmp_path / "no-existe.yaml")]) == 2


def test_hf66_cli_escribe_y_verifica(rb, tmp_path, capsys):
    out = tmp_path / "propuestas" / "pinning" / "pinning-application-block.colsint.conf"
    assert rb.main(["--insumo", str(FIXTURE_COL), "--out", str(out)]) == 0
    assert rb.main(["--verificar", str(out), "--dir-propuesta", str(out.parent)]) == 0
    assert "BLOQUE OK (modo Colombia)" in capsys.readouterr().out


# ── HF-67: verificador del bloque (Python y .ps1) ──

def test_hf67_verificador_ignora_comentarios_y_marca_lo_activo(rb, tmp_path):
    bloque = rb.generar(FIXTURE_COL)
    assert "groupSSR = true (:676)" in bloque  # comentario obligatorio del colapso: no es un valor
    assert rb.verificar_bloque(bloque) == []
    activo = rb.verificar_bloque(bloque + "oxygen.stage.pinning.reusePin = false\n")
    assert any("clave compartida reusePin" in h for h in activo)
    matcher = rb.verificar_bloque(bloque + 'x = "com.experian.ISIdentityMatcher"\n')
    assert any("EVAL-PIN-13" in h for h in matcher)
    corto = bloque.replace("VERIFICAR oxygen.stage.curation.paths.curatedDir", "revisar curatedDir")
    assert any("oxygen.stage.curation.paths.curatedDir" in h for h in rb.verificar_bloque(corto))
    (tmp_path / "ColsintIdentityMatcher.scala").write_text("x", encoding="utf-8")
    (tmp_path / "identifying-attributes.json").write_text("[]", encoding="utf-8")
    hallazgos = rb.verificar_bloque(bloque, tmp_path)
    assert sum("EVAL-PIN-14" in h for h in hallazgos) == 2


def lista_ps1(texto: str, variable: str) -> list[str]:
    cuerpo = texto[texto.index(f"${variable} = @("):]
    cuerpo = cuerpo[:cuerpo.index(")")]
    return re.findall(r'"([^"]+)"', cuerpo)


def test_hf67_ps1_de_pinning_evalua_fuera_de_comentarios_y_cubre_eval_pin_12_a_14(rb):
    ps1 = leer(PIN / "evals" / "verificar-artefacto.ps1")
    assert "$hocon -match 'groupSSR" not in ps1 and "$hoconActivo -match 'groupSSR" in ps1
    assert '[ValidateSet("propio", "colombia")]' in ps1 and "[string]$PropuestaDir" in ps1
    for caso in ("EVAL-PIN-12", "EVAL-PIN-13", "EVAL-PIN-14"):
        assert caso in ps1, caso
    assert lista_ps1(ps1, "verificarColombia") == list(rb.VERIFICAR_COLOMBIA)


# ── HF-78: verificador de Synthesis con modo; nunca handler: null ──

def test_hf78_ps1_de_synthesis_tiene_modo_y_no_exige_la_raiz_en_colombia():
    ps1 = leer(SYN / "evals" / "verificar-artefacto.ps1")
    assert '[ValidateSet("propio", "colombia")]' in ps1 and '[string]$Modo = "propio"' in ps1
    colombia = ps1[ps1.index('if ($Modo -eq "colombia") {'):ps1.index("} else {", ps1.index('if ($Modo -eq "colombia") {'))]
    assert "consistencyCheck\\s*\\{" not in colombia  # la raíz solo se exige en modo propio
    raiz = ps1.index("oxygen\\.stage\\.consistencyCheck\\s*\\{")
    assert ps1.rfind("} else {", 0, raiz) > ps1.index('if ($Modo -eq "colombia") {')
    verif = cargar(RAIZ / "tools" / "verificar-artefactos-etapa.py", "verificar_artefactos_etapa")
    assert lista_ps1(ps1, "clavesColombia") == list(verif.CLAVES_VERIFICAR_COLOMBIA)
    assert '"handler"\\s*:\\s*null' in ps1


def test_hf78_handler_null_prohibido_en_skill_y_plantilla():
    skill = leer(SYN / "SKILL.md")
    paso3 = skill[skill.index("### 3. Genera el GDD"):skill.index("### 4.")]
    assert "**Nunca `\"handler\": null` en el JSON** (HF-78)" in paso3
    assert 'NUNCA "handler": null (HF-78)' in leer(SYN / "assets" / "domaindictionary.json.template")


# ── HF-79: precondición por condición, python3, cita de oxygen-config-default-local.conf ──

def test_hf79_cabecera_pide_nombrar_la_condicion_no_su_valor():
    cabecera = leer(ANA / "assets" / "templates" / "_cabecera-yaml-skill.yaml.tpl")
    alcance = cabecera[cabecera.index("precondiciones_bloqueantes"):cabecera.index("marcado_heredado")]
    assert "nombra la CONDICIÓN" in alcance and "HF-79" in alcance


def test_hf79_python3_en_las_skills():
    patron = re.compile(r"(?<![\w-])python (?=(scripts/|tools/|-m |\.kiro/))")
    for skill in ("edf-analisis", "edf-validation", "edf-acceptance", "edf-curation", "edf-pinning", "edf-synthesis"):
        for ruta in (SKILLS / skill).rglob("*.md"):
            if ".pytest_cache" in ruta.parts:
                continue
            assert not patron.search(leer(ruta)), ruta
    assert "`python3 tools/verificar-claves.py <ingesta>`" in leer(SYN / "SKILL.md")


def test_hf79_cita_de_consistency_check_en_el_conf_por_defecto():
    plantilla = leer(SYN / "assets" / "consistency-application-block.conf.template")
    assert "oxygen-config-default-local.conf:459-468" in plantilla and ":465-468" in plantilla
    conf = leer(RAIZ / "Motor-Oxigen" / "oxygen-synthesis" / "src" / "main" / "resources" / "local"
                / "oxygen-config-default-local.conf").splitlines()
    assert conf[459].strip() == "orchestration {" and conf[464].strip() == "datasetFormat {"
    assert conf[467].strip() == "}" and "consistencyCheck" in "\n".join(conf[440:459])
