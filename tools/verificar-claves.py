#!/usr/bin/env python3
"""Verifica que los nombres de campo curado sean coherentes entre los insumos de una ingesta.

Uso:
    python tools/verificar-claves.py <ingesta-id>                  # expedientes/<ingesta-id>/insumos de la raíz
    python tools/verificar-claves.py --dir <carpeta-con-los-insumos>
En una corrida fuera de la raíz (p. ej. ejecuciones/<corrida>/expedientes/<id>/insumos) usa --dir: con <ingesta-id> la
herramienta solo mira la raíz del framework (HF-70).

Lee (de expedientes/<ingesta-id>/insumos/ o de --dir):
  - source-spec-curation.yaml   -> claves publicadas: campos_curados[].destino
  - yaml-skill-pinning.yaml     -> modo_colombia.claves_obligatorias_en_evento_curado, clave_opcional
                                   y mapeo_identidad.atributos[].campo_curado_* con estado [C]
  - yaml-skill-synthesis.yaml   -> mapeo_campos_handler.campos_con_mapeo_confirmado[].campo_curado_* con estado [C]
  - yaml-skill-statecalc.yaml   -> mapeo_campos.campos[].campo_curado_*_origen con nivel_confianza "A"
  - contrato-curation-pinning.md -> primera columna (entre comillas invertidas) de la tabla del §1: solo la
                                   sección «## 1» hasta el siguiente «## » (sin esa sección, la primera
                                   tabla del archivo). HF-37: las demás tablas (§2 subscriber,
                                   subscriberRelation…) no son claves de curatedRecordJson

  - yaml-skill-synthesis.yaml   -> (1.9.0) nombre_en_gdd de las entradas [C] y los nombres de
                                   instruccion_generacion_gdd.contenido_a_escribir.campos_a_generar[].name
                                   (forma del GDD v2: name + description; HF-71). `nombre_en_gdd` en
                                   campos_a_generar se acepta como alias deprecado, con aviso
  - yaml-skill-statecalc.yaml   -> (1.9.0) value de los campos DERIVED y las referencias field.X de los scripts
                                   CALCULATED (mapeo nivel A e instruccion_generacion_foundation)

Regla (steering 11 §6 #5): toda clave citada como confirmada aguas abajo debe existir LITERAL en
campos_curados[].destino o en parametros.campo_advertencias (HF-37), o ser un campo que el motor inyecta,
como subscriber o dataSource (INYECTADOS, también en el contrato). Un nombre
errado no da error en el motor: Synthesis pierde el handler del campo y State Calculation devuelve el valor
por defecto (AUD-S2, AUD-SC1). Las claves declaradas en campos_abiertos se informan como pendientes, y las
citas de entradas no confirmadas ([P], nivel B) como avisos, sin cambiar la salida.
Salida: 0 si todo coincide (o si la carpeta de insumos existe y no hay nada que cruzar), 1 si hay claves
inexistentes, 2 si falta PyYAML, si no existe el expediente o la carpeta de insumos (HF-70: antes daba «nada que
cruzar» con 0, un falso verde) o si hay citas aguas abajo sin el source-spec de Curation para cruzarlas.
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
        nombre = nombre_a_generar(c)
        if concreto(nombre):
            out.append(("yaml-skill-synthesis.yaml (campos_a_generar)", nombre.strip(), True))
    return out


def nombre_a_generar(entrada):
    """HF-71: `name` (forma del GDD v2); `nombre_en_gdd` se acepta como alias deprecado."""
    if not isinstance(entrada, dict):
        return entrada
    return entrada.get("name") if entrada.get("name") is not None else entrada.get("nombre_en_gdd")


def avisos_synthesis(syn):
    """HF-71: entradas de campos_a_generar con el alias deprecado o sin nombre (no se cruzarían en silencio)."""
    gen = (((syn.get("instruccion_generacion_gdd") or {}).get("contenido_a_escribir") or {})
           .get("campos_a_generar") or [])
    out = []
    for i, c in enumerate(gen):
        if not isinstance(c, dict):
            continue
        if c.get("name") is None and c.get("nombre_en_gdd") is not None:
            out.append(f"campos_a_generar[{i}] usa `nombre_en_gdd` (deprecado): usar `name`, la forma del GDD v2 "
                       "(name + description)")
        elif not concreto(c.get("name")) and not concreto(c.get("nombre_en_gdd")) and any(
                str(v).strip() for v in c.values() if v is not None):
            out.append(f"campos_a_generar[{i}] sin `name`: la entrada no se cruza con Curation")
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


FILA_CLAVE = re.compile(r"^\|\s*`([A-Za-z_][A-Za-z0-9_]*)`\s*\|")
SECCION_1 = re.compile(r"^##\s+(?:§\s*)?1(?:[.)]|\s|$)")


def claves_contrato(texto):
    """HF-37 (AUD-C-08): primera columna (entre comillas invertidas) de la tabla del §1 del contrato.

    Solo la sección «## 1…» hasta el siguiente «## ». Sin esa sección, la primera tabla del archivo.
    """
    lineas = texto.splitlines()
    inicio = next((i + 1 for i, linea in enumerate(lineas) if SECCION_1.match(linea)), None)
    if inicio is not None:
        fin = next((j for j in range(inicio, len(lineas)) if lineas[j].startswith("## ")), len(lineas))
        bloque = lineas[inicio:fin]
    else:
        primera = next((i for i, linea in enumerate(lineas) if linea.lstrip().startswith("|")), len(lineas))
        fin = next((j for j in range(primera, len(lineas)) if not lineas[j].lstrip().startswith("|")), len(lineas))
        bloque = lineas[primera:fin]
    return [m.group(1) for m in map(FILA_CLAVE.match, bloque) if m]


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if not args:
        print(__doc__)
        return 2
    if args[0] == "--dir":
        if len(args) < 2:
            print(__doc__)
            return 2
        base = pathlib.Path(args[1])
        if not base.is_dir():
            print(f"No existe la carpeta de insumos {base}: no hay nada que verificar (no es un resultado verde).")
            return 2
    else:
        expediente = RAIZ / "expedientes" / args[0]
        base = expediente / "insumos"
        if not expediente.is_dir() or not base.is_dir():
            falta = expediente if not expediente.is_dir() else base
            print(f"No existe {falta}: <ingesta-id> busca en expedientes/<id>/insumos de la raíz del framework. "
                  "En una corrida fuera de la raíz usa --dir <expediente>/insumos (no es un resultado verde).")
            return 2

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
    # HF-37 (AUD-C-08): el campo de advertencias también es una clave publicada de curatedRecordJson (D13).
    advertencias = (spec.get("parametros") or {}).get("campo_advertencias")
    if isinstance(advertencias, str) and advertencias.strip():
        publicadas.add(advertencias.strip())
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
    avisos_forma = avisos_synthesis(syn) if syn else []
    for archivo, clave, obligatoria in (citas_synthesis(syn) if syn else []) + (citas_statecalc(sc) if sc else []):
        if clave in INYECTADOS:
            continue
        (citas if obligatoria else avisos_previos).append((archivo, clave))
    contrato = base / "contrato-curation-pinning.md"
    if contrato.is_file():
        for clave in claves_contrato(contrato.read_text(encoding="utf-8")):
            if clave not in INYECTADOS:  # HF-37: subscriber/subscriberRelation viven fuera de curatedRecordJson
                citas.append(("contrato-curation-pinning.md", clave))

    faltan, pendientes = [], []
    for archivo, clave in citas:
        if clave in publicadas:
            continue
        (pendientes if clave in abiertas else faltan).append((archivo, clave))

    print(f"Claves publicadas por Curation: {len(publicadas)} · citas revisadas: {len(citas)}")
    for aviso in avisos_forma:
        print(f"  ! AVISO      yaml-skill-synthesis.yaml: {aviso}")
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
