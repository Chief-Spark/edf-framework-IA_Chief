#!/usr/bin/env python3
"""Verifica el insumo de Pinning (yaml-skill-pinning.yaml) que prepara edf-analisis (Modo 2) para edf-pinning (HF-60).

Uso:
    python3 tools/verificar-insumo-pinning.py <ingesta-id>
    python3 tools/verificar-insumo-pinning.py --dir <carpeta-con-los-insumos>
    python3 tools/verificar-insumo-pinning.py --archivo <yaml-skill-pinning.yaml> [--contrato <contrato-curation-pinning.md>]

Con <ingesta-id> lee expedientes/<ingesta-id>/insumos/ de la raíz del framework; con --dir, esa carpeta (úsalo en una
corrida fuera de la raíz, p. ej. ejecuciones/<corrida>/expedientes/<id>/insumos). Si el insumo no está, sale con 2. En los dos casos el contrato es
contrato-curation-pinning.md de la misma carpeta (si no existe, se avisa y no se cruza).

Errores (salida 1):
  1. secciones   Falta una sección de primer nivel de _cabecera-yaml-skill.yaml.tpl o de yaml-skill-pinning.yaml.tpl.
  2. cabecera    `documento` lleva estado o aprobador (HF-32: viven solo en estado-ingesta.json).
  3. generación  instruccion_generacion.contenido_a_escribir.valor_resultante incluye un slot que no es un atributo
                 [C] de mapeo_identidad.atributos, o depende de un [A:*] (edf-analisis SKILL.md Modo 2, paso 4).
  4. Colombia    Con modo_colombia.aplica = true (aplicación de Pinning compartida):
                 - una clave compartida de claves_configuracion_pinning lleva un valor listo para escribir. Su
                   valor_a_escribir debe EMPEZAR por «no se escribe» / «no se redefine» (un texto que empieza por un
                   valor, como "false (no se escribe …)", es un valor). El nombre de la clave se compara sin distinguir
                   mayúsculas (reusePIN = reusePin). reusePin nunca se escribe: es global de la aplicación y false
                   renumera los PIN de todas las fuentes (HF-57, HF-59, HF-64);
                 - el matcher o la lista de referencia (ISIdentityMatcher, RIIdentityMatcher, identifying-attributes.json,
                   slots nationalId/firstName/…/cellPhone): con eventos de Colombia colapsan todo en un PIN (HF-58);
                 - valor_resultante o reglas_matching_por_campo.reglas no vacíos (no se regenera la lista ni hay pesos).
  5. contrato    Una clave de identidad del insumo (modo_colombia.claves_obligatorias_en_evento_curado, clave_opcional
                 y campo_curado_* de los atributos [C]) no está en la tabla §1 de contrato-curation-pinning.md.
Avisos (no cambian la salida): falta el contrato; una clave del contrato no aparece en el insumo; reusePin sin
valor_en_fuente en modo Colombia; cita de EventPinner.scala (no se usa en el batch; la evidencia de «null lanza» es
ISIdentityMatcher.scala:46-54, HF-61).

Salida: 0 «insumo de Pinning OK», 1 con errores, 2 si falta PyYAML o el insumo.
"""
from __future__ import annotations

import importlib.util
import pathlib
import re
import sys

try:
    import yaml
except ImportError:
    print("Falta PyYAML: pip install pyyaml")
    sys.exit(2)

RAIZ = pathlib.Path(__file__).resolve().parent.parent
PLANTILLAS = RAIZ / ".kiro" / "skills" / "edf-analisis" / "assets" / "templates"
PLANTILLAS_INSUMO = ("_cabecera-yaml-skill.yaml.tpl", "yaml-skill-pinning.yaml.tpl")
SECCION = re.compile(r"^([a-z_]+):", re.M)

# Claves de oxygen.stage.pinning que, en modo Colombia, son de la aplicación compartida (edf-pinning SKILL.md).
CLAVES_COMPARTIDAS = ("identifyingAttributesFile", "identityMatcher", "reusePin", "domainDictionaryFile",
                      "clusterSplitter", "nearNeighborBlockSize", "pinningVleRange", "groupSSR")
NO_SE_ESCRIBE = re.compile(r"no\s+se\s+escribe|no\s+se\s+redefine|no\s+redefinir", re.I)
COMPARTIDAS_MIN = {c.lower(): c for c in CLAVES_COMPARTIDAS}
MATCHER_REFERENCIA = re.compile(r"\b(ISIdentityMatcher|RIIdentityMatcher)\b|(?<![-\w])identifying-attributes\.json")
SLOTS_REFERENCIA = {"nationalId", "firstName", "firstLastName", "middleName", "secondLastName", "cellPhone"}


def _verificar_claves():
    spec = importlib.util.spec_from_file_location("verificar_claves", RAIZ / "tools" / "verificar-claves.py")
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


def secciones_plantilla() -> list[str]:
    out: list[str] = []
    for nombre in PLANTILLAS_INSUMO:
        for s in SECCION.findall((PLANTILLAS / nombre).read_text(encoding="utf-8")):
            if s not in out:
                out.append(s)
    return out


def confirmado(valor) -> bool:
    return isinstance(valor, str) and valor.strip().startswith("[C]")


def campo_curado(item: dict) -> str | None:
    for k, v in (item or {}).items():
        if k.startswith("campo_curado") and isinstance(v, str) and v.strip():
            return v.strip()
    return None


def nombre_clave(clave: str) -> str:
    """Último segmento de la clave, con la grafía canónica si es compartida (sin distinguir mayúsculas, HF-64)."""
    nombre = str(clave or "").rsplit(".", 1)[-1].strip()
    return COMPARTIDAS_MIN.get(nombre.lower(), nombre)


def no_se_escribe(valor) -> bool:
    """El valor es el texto de no escribir: empieza por «no se escribe» / «no se redefine» (HF-64)."""
    return isinstance(valor, str) and NO_SE_ESCRIBE.match(valor.strip()) is not None


def verificar(insumo: dict, texto: str, contrato: str | None) -> tuple[list[str], list[str]]:
    errores: list[str] = []
    avisos: list[str] = []

    # 1. Secciones de la plantilla.
    for s in secciones_plantilla():
        if s not in insumo:
            errores.append(f"secciones: falta la sección «{s}» de la plantilla")

    # 2. Cabecera sin estado ni aprobador (HF-32).
    documento = insumo.get("documento") or {}
    for k in ("estado", "aprobador"):
        if isinstance(documento, dict) and k in documento:
            errores.append(f"cabecera: documento.{k} no va en el insumo (HF-32: vive en estado-ingesta.json)")

    atributos = [a for a in ((insumo.get("mapeo_identidad") or {}).get("atributos") or []) if isinstance(a, dict)]
    slots_c = {a.get("atributo_motor") for a in atributos if confirmado(a.get("estado"))}

    # 3. instruccion_generacion: solo atributos [C], nada que dependa de un [A:*].
    contenido = ((insumo.get("instruccion_generacion") or {}).get("contenido_a_escribir") or {})
    resultante = contenido.get("valor_resultante") or []
    for slot in resultante:
        if "[A:" in str(slot):
            errores.append(f"generación: valor_resultante «{slot}» depende de un [A:*]")
        elif slot not in slots_c:
            errores.append(f"generación: valor_resultante «{slot}» no es un atributo [C] de mapeo_identidad.atributos")
    if resultante and "[A:" in str(contenido.get("estado") or ""):
        errores.append("generación: contenido_a_escribir.estado depende de un [A:*] y valor_resultante no está vacío")

    # 4. Modo Colombia: aplicación compartida.
    mc = insumo.get("modo_colombia") or {}
    colombia = isinstance(mc, dict) and mc.get("aplica") is True
    bloque = ((insumo.get("claves_configuracion_pinning") or {}).get("bloque_oxygen_stage_pinning") or [])
    if colombia:
        for entrada in bloque:
            if not isinstance(entrada, dict):
                continue
            clave = nombre_clave(entrada.get("clave"))
            valor = entrada.get("valor_a_escribir")
            if clave in CLAVES_COMPARTIDAS and not no_se_escribe(valor):
                motivo = ("reusePin es global de la aplicación (SparkPinner.scala:223, :409): false renumera los PIN de "
                          "todas las fuentes; cambiarla es N4 y se escala (HF-57)" if clave == "reusePin"
                          else "clave compartida de la aplicación de Colombia: solo se verifica (HF-59)")
                errores.append(f"Colombia: {entrada.get('clave')} = {valor!r} está listo para escribir; debe decir "
                               f"«no se escribe en modo Colombia». {motivo}")
            if clave == "reusePin" and not str(entrada.get("valor_en_fuente") or "").strip():
                avisos.append("Colombia: reusePin sin valor_en_fuente (el valor de la spec se registra como dato, HF-57)")
            if isinstance(valor, str) and MATCHER_REFERENCIA.search(valor) and not no_se_escribe(valor):
                errores.append(f"Colombia: {entrada.get('clave')} hereda el matcher o la lista de referencia (HF-58)")
        if MATCHER_REFERENCIA.search(str(mc.get("identity_matcher") or "")):
            errores.append("Colombia: modo_colombia.identity_matcher es el matcher de referencia; con eventos de Colombia "
                           "todas las identidades colapsan en un PIN (HF-58)")
        for a in atributos:
            if confirmado(a.get("estado")) and a.get("atributo_motor") in SLOTS_REFERENCIA:
                errores.append(f"Colombia: atributo_motor «{a['atributo_motor']}» es un slot de la lista de referencia; "
                               "con eventos de Colombia vale \"\" en todas las identidades (HF-58)")
        if resultante:
            errores.append("Colombia: instruccion_generacion.valor_resultante debe ser [] (se reutiliza la lista compartida)")
        if (insumo.get("reglas_matching_por_campo") or {}).get("reglas"):
            errores.append("Colombia: reglas_matching_por_campo.reglas debe ser [] (matcher determinista, sin pesos)")

    # 5. Coherencia con el contrato Curation → Pinning.
    citadas: list[tuple[str, str]] = []
    if colombia:
        for k in mc.get("claves_obligatorias_en_evento_curado") or []:
            citadas.append(("modo_colombia.claves_obligatorias_en_evento_curado", str(k)))
        if isinstance(mc.get("clave_opcional"), str) and mc["clave_opcional"].strip():
            citadas.append(("modo_colombia.clave_opcional", mc["clave_opcional"].strip()))
    for a in atributos:
        if confirmado(a.get("estado")) and campo_curado(a):
            citadas.append(("mapeo_identidad.atributos", campo_curado(a)))
    if contrato is None:
        avisos.append("contrato: no hay contrato-curation-pinning.md; no se cruzaron las claves")
    else:
        del_contrato = set(_verificar_claves().claves_contrato(contrato))
        for origen, clave in citadas:
            if clave not in del_contrato:
                errores.append(f"contrato: «{clave}» ({origen}) no está en la tabla §1 de contrato-curation-pinning.md")
        for clave in sorted(del_contrato - {c for _, c in citadas}):
            avisos.append(f"contrato: «{clave}» está en el contrato y no la cita el insumo")

    if "EventPinner" in texto:
        avisos.append("cita de EventPinner.scala: no se usa en el batch (PinningIOUtils.scala:23); la evidencia de «null "
                      "lanza» es ISIdentityMatcher.scala:46-54 (HF-61)")
    return errores, avisos


def main(argv=None) -> int:
    args = sys.argv[1:] if argv is None else list(argv)
    if not args:
        print(__doc__)
        return 2
    contrato_ruta = None
    if args[0] == "--archivo" and len(args) > 1:
        ruta = pathlib.Path(args[1])
        if len(args) > 3 and args[2] == "--contrato":
            contrato_ruta = pathlib.Path(args[3])
    else:
        base = pathlib.Path(args[1]) if args[0] == "--dir" and len(args) > 1 else RAIZ / "expedientes" / args[0] / "insumos"
        ruta = base / "yaml-skill-pinning.yaml"
        contrato_ruta = base / "contrato-curation-pinning.md"
    if not ruta.is_file():
        # HF-70: rc 2 (nunca un verde). Con <ingesta-id> solo se mira la raíz del framework.
        print(f"Falta el insumo {ruta}. Con <ingesta-id> se busca en expedientes/<id>/insumos de la raíz; en una "
              "corrida fuera de la raíz usa --dir <expediente>/insumos.")
        return 2
    texto = ruta.read_text(encoding="utf-8")
    insumo = yaml.safe_load(texto) or {}
    contrato = contrato_ruta.read_text(encoding="utf-8") if contrato_ruta and contrato_ruta.is_file() else None

    errores, avisos = verificar(insumo, texto, contrato)
    print(f"Insumo: {ruta}")
    for a in avisos:
        print(f"  ! AVISO  {a}")
    for e in errores:
        print(f"  X ERROR  {e}")
    if errores:
        print(f"RESULTADO: insumo de Pinning con {len(errores)} error(es).")
        return 1
    print("RESULTADO: insumo de Pinning OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
