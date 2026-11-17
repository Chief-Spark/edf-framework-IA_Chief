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

Regla (steering 11 §6 #5): toda clave citada como confirmada aguas abajo debe existir LITERAL en
campos_curados[].destino. Las claves declaradas en campos_abiertos se informan como pendientes.
Salida: 0 si todo coincide, 1 si hay claves inexistentes, 2 si falta un archivo obligatorio o PyYAML.
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


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2
    base = pathlib.Path(args[1]) if args[0] == "--dir" and len(args) > 1 else RAIZ / "expedientes" / args[0] / "insumos"

    spec = cargar(base / "source-spec-curation.yaml")
    if spec is None:
        print(f"Falta {base / 'source-spec-curation.yaml'}: es la fuente de las claves publicadas.")
        return 2
    publicadas = {c.get("destino") for c in spec.get("campos_curados", []) if c.get("destino")}
    abiertas = {str(c.get("destino")) for c in spec.get("campos_abiertos", []) if c.get("destino")}

    citas = []  # (archivo, clave)
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
    if syn:
        for c in ((syn.get("mapeo_campos_handler") or {}).get("campos_con_mapeo_confirmado") or []):
            if confirmado(c.get("estado")) and campo_curado(c):
                citas.append(("yaml-skill-synthesis.yaml", campo_curado(c)))
    sc = cargar(base / "yaml-skill-statecalc.yaml")
    if sc:
        for c in ((sc.get("mapeo_campos") or {}).get("campos") or []):
            if str(c.get("nivel_confianza")) == "A" and campo_curado(c, "_origen"):
                citas.append(("yaml-skill-statecalc.yaml", campo_curado(c, "_origen")))
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
