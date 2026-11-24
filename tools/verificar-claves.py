#!/usr/bin/env python3
"""Verifica que los nombres de campo curado sean coherentes entre los insumos de una ingesta.

Uso:
    python tools/verificar-claves.py <ingesta-id>
    python tools/verificar-claves.py --dir <carpeta-con-los-insumos>

Lee (de expedientes/<ingesta-id>/insumos/ o de --dir):
  - source-spec-curation.yaml   -> claves publicadas: campos_curados[].destino
  - yaml-skill-pinning.yaml     -> modo_colombia.claves_obligatorias_en_evento_curado, clave_opcional
                                   y mapeo_identidad.atributos[].campo_curado_* con estado [C]
  - yaml-skill-synthesis.yaml   -> mapeo_campos_handler.campos_con_mapeo_confirmado[].campo_curado_* con estado [C]
  - yaml-skill-statecalc.yaml   -> mapeo_campos.campos[].campo_curado_*_origen con nivel_confianza "A"
  - contrato-curation-pinning.md -> primera columna (entre comillas invertidas) de la tabla del §1

  - yaml-skill-synthesis.yaml   -> (1.9.0) nombre_en_gdd de las entradas [C] y los nombres de
                                   instruccion_generacion_gdd.contenido_a_escribir.campos_a_generar
  - yaml-skill-statecalc.yaml   -> (1.9.0) value de los campos DERIVED y las referencias field.X de los scripts
                                   CALCULATED (mapeo nivel A e instruccion_generacion_foundation)

Regla (steering 11 §6 #5): toda clave citada como confirmada aguas abajo debe existir LITERAL en
campos_curados[].destino (o ser un campo que el motor inyecta, como subscriber o dataSource). Un nombre
errado no da error en el motor: Synthesis pierde el handler del campo y State Calculation devuelve el valor
por defecto (AUD-S2, AUD-SC1). Las claves declaradas en campos_abiertos se informan como pendientes, y las
citas de entradas no confirmadas ([P], nivel B) como avisos, sin cambiar la salida.
Salida: 0 si todo coincide, 1 si hay claves inexistentes, 2 si falta PyYAML o hay citas aguas abajo sin el
source-spec de Curation para cruzarlas.
"""
import pathlib
import re
import sys

try:
    import yaml
except ImportError:
    print("Falta PyYAML: pip install pyyaml")
    sys.exit(2)

RAIZ = pathlib.Path(__file__).resolve().parent.parent


def cargar(ruta):
    if not ruta.is_file():
        return None
    with ruta.open(encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def confirmado(valor):
    return isinstance(valor, str) and valor.strip().startswith("[C]")


def campo_curado(item, sufijo=""):
    for k, v in (item or {}).items():
        if k.startswith("campo_curado") and k.endswith(sufijo) and isinstance(v, str):
            return v
    return None


# Campos que el motor agrega al evento sin que Curation los publique (StateCalculator, DomainDictionaryInternal).
INYECTADOS = {"dataSource", "subscriber", "subscriberRelation", "asOfPeriod", "asOfDate", "fileTag",
              "subscriberRelationStartDate", "subscriberRelationEndDate", "firstEventDate"}
IDENT = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
REF_FIELD = re.compile(r"\bfield\.([A-Za-z_][A-Za-z0-9_]*)")


def concreto(valor):
    """Un nombre de campo real (no vacío ni un marcador como [PM])."""
    return isinstance(valor, str) and bool(IDENT.match(valor.strip()))


def citas_synthesis(syn):
    """(archivo, clave, obligatoria) de yaml-skill-synthesis.yaml."""
    out = []
    for c in ((syn.get("mapeo_campos_handler") or {}).get("campos_con_mapeo_confirmado") or []):
        ok = confirmado(c.get("estado"))
        if campo_curado(c) and concreto(campo_curado(c)):
            out.append(("yaml-skill-synthesis.yaml", campo_curado(c).strip(), ok))
        if concreto(c.get("nombre_en_gdd")):
            out.append(("yaml-skill-synthesis.yaml (nombre_en_gdd)", c["nombre_en_gdd"].strip(), ok))
    gen = (((syn.get("instruccion_generacion_gdd") or {}).get("contenido_a_escribir") or {})
           .get("campos_a_generar") or [])
    for c in gen:
        nombre = c.get("name") if isinstance(c, dict) else c
        if concreto(nombre):
            out.append(("yaml-skill-synthesis.yaml (campos_a_generar)", nombre.strip(), True))
    return out


def citas_statecalc(sc):
    """(archivo, clave, obligatoria) de yaml-skill-statecalc.yaml."""
    out = []
    for c in ((sc.get("mapeo_campos") or {}).get("campos") or []):
        ok = str(c.get("nivel_confianza")) == "A"
        origen = campo_curado(c, "_origen")
        if origen and concreto(origen):
            out.append(("yaml-skill-statecalc.yaml", origen.strip(), ok))
        for ref in REF_FIELD.findall(str(c.get("valor_calculated") or "")):
            out.append(("yaml-skill-statecalc.yaml (field.X)", ref, ok))
    gen = (((sc.get("instruccion_generacion_foundation") or {}).get("contenido_a_escribir") or {})
           .get("campos_a_generar") or [])
    for c in gen:
        if not isinstance(c, dict):
            continue
        valor = str(c.get("value") or "")
        if str(c.get("type", "")).upper() == "DERIVED" and concreto(valor):
            out.append(("yaml-skill-statecalc.yaml (foundation DERIVED)", valor.strip(), True))
        for ref in REF_FIELD.findall(valor):
            out.append(("yaml-skill-statecalc.yaml (foundation field.X)", ref, True))
    return out


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if not args:
        print(__doc__)
        return 2
    base = pathlib.Path(args[1]) if args[0] == "--dir" and len(args) > 1 else RAIZ / "expedientes" / args[0] / "insumos"

    spec = cargar(base / "source-spec-curation.yaml")
    if spec is None:
        aguas_abajo = [n for n in ("yaml-skill-pinning.yaml", "yaml-skill-synthesis.yaml", "yaml-skill-statecalc.yaml",
                                   "contrato-curation-pinning.md") if (base / n).is_file()]
        if not aguas_abajo:
            print("Aún no hay source-spec-curation.yaml ni insumos aguas abajo: nada que cruzar.")
            return 0
        print(f"Falta {base / 'source-spec-curation.yaml'}: es la fuente de las claves publicadas y hacen falta "
              f"para cruzar {', '.join(aguas_abajo)}.")
        return 2
    publicadas = {c.get("destino") for c in spec.get("campos_curados", []) if c.get("destino")}
    abiertas = {str(c.get("destino")) for c in spec.get("campos_abiertos", []) if c.get("destino")}

    citas = []  # (archivo, clave)
    avisos_previos = []  # (archivo, clave) de entradas no confirmadas
    pin = cargar(base / "yaml-skill-pinning.yaml")
    if pin:
        mc = pin.get("modo_colombia") or {}
        if mc.get("aplica"):
            for k in mc.get("claves_obligatorias_en_evento_curado", []) or []:
                citas.append(("yaml-skill-pinning.yaml (obligatoria)", k))
            if mc.get("clave_opcional"):
                citas.append(("yaml-skill-pinning.yaml (opcional)", mc["clave_opcional"]))
        for a in ((pin.get("mapeo_identidad") or {}).get("atributos") or []):
            if confirmado(a.get("estado")) and campo_curado(a):
                citas.append(("yaml-skill-pinning.yaml", campo_curado(a)))
    syn = cargar(base / "yaml-skill-synthesis.yaml")
    sc = cargar(base / "yaml-skill-statecalc.yaml")
    for archivo, clave, obligatoria in (citas_synthesis(syn) if syn else []) + (citas_statecalc(sc) if sc else []):
        if clave in INYECTADOS:
            continue
        (citas if obligatoria else avisos_previos).append((archivo, clave))
    contrato = base / "contrato-curation-pinning.md"
    if contrato.is_file():
        for linea in contrato.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^\|\s*`([A-Za-z_][A-Za-z0-9_]*)`\s*\|", linea)
            if m:
                citas.append(("contrato-curation-pinning.md", m.group(1)))

    faltan, pendientes = [], []
    for archivo, clave in citas:
        if clave in publicadas:
            continue
        (pendientes if clave in abiertas else faltan).append((archivo, clave))

    print(f"Claves publicadas por Curation: {len(publicadas)} · citas revisadas: {len(citas)}")
    for archivo, clave in avisos_previos:
        if clave not in publicadas:
            print(f"  ! AVISO      {clave!r} citada en {archivo} (entrada no confirmada): no está en campos_curados[].destino")
    for archivo, clave in pendientes:
        print(f"  ! PENDIENTE  {clave!r} citada en {archivo}: está en campos_abiertos (Curation aún no la publica)")
    for archivo, clave in faltan:
        print(f"  X NO EXISTE  {clave!r} citada en {archivo}: no está en campos_curados[].destino")
    if faltan:
        print("RESULTADO: claves incoherentes. Corregir el insumo o marcar la cita como [A:mapeo-claves-curation-pinning].")
        return 1
    print("RESULTADO: claves coherentes" + (" (con pendientes)." if pendientes else "."))
    return 0


if __name__ == "__main__":
    sys.exit(main())
