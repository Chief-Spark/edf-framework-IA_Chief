#!/usr/bin/env python3
"""Valida todos los expedientes del workspace (lo usa el hook `validar-expediente`).

Uso:
    python tools/validar-expedientes.py            # todos los expedientes/<id>/
    python tools/validar-expedientes.py sisben     # solo expedientes/sisben/

Para cada `expedientes/<id>/estado-ingesta.json` ejecuta `validar-expediente.py`
(con `--cruzado bitacora.md` si la bitacora existe).
Salida: 0 si todos pasan (o no hay expedientes), 1 si alguno tiene hallazgos.
"""
import pathlib
import subprocess
import sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
VALIDADOR = pathlib.Path(__file__).resolve().parent / "validar-expediente.py"


def main():
    base = RAIZ / "expedientes"
    if not base.is_dir():
        print("No hay carpeta expedientes/: nada que validar.")
        return 0
    filtro = set(sys.argv[1:])
    estados = sorted(base.glob("*/estado-ingesta.json"))
    if filtro:
        estados = [e for e in estados if e.parent.name in filtro]
    if not estados:
        print("No hay expedientes con estado-ingesta.json: nada que validar.")
        return 0

    con_hallazgos = []
    for estado in estados:
        cmd = [sys.executable, str(VALIDADOR), str(estado)]
        bitacora = estado.parent / "bitacora.md"
        if bitacora.is_file():
            cmd += ["--cruzado", str(bitacora)]
        print(f"=== {estado.parent.name} ===")
        r = subprocess.run(cmd)
        if r.returncode != 0:
            con_hallazgos.append(estado.parent.name)
        print()

    if con_hallazgos:
        print("EXPEDIENTES CON HALLAZGOS: " + ", ".join(con_hallazgos))
        return 1
    print(f"OK: {len(estados)} expediente(s) validos.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
