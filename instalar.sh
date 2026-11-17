#!/usr/bin/env bash
# Instala el framework edf-framework-IA en el workspace donde el ingeniero de datos va a trabajar con Kiro.
#
# En este repo la configuracion vive en `kiro/` (visible, para navegarla y versionarla).
# Kiro solo la lee como `.kiro/` en la RAIZ del workspace abierto, asi que el instalador copia:
#   kiro/steering/*  -> <destino>/.kiro/steering/
#   kiro/skills/*    -> <destino>/.kiro/skills/
#   kiro/hooks/*     -> <destino>/.kiro/hooks/
#   kiro/specs/*     -> <destino>/.kiro/specs/
#   tools/*          -> <destino>/tools/
#   docs/*           -> <destino>/docs/edf-framework/
#   (crea)           -> <destino>/expedientes/   (nunca toca expedientes existentes)
# Solo sobrescribe archivos del framework con el mismo nombre. No borra nada del workspace.
#
# Uso:
#   ./instalar.sh <ruta-del-workspace> [--forzar]
set -euo pipefail

ORIGEN="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DESTINO="${1:-}"
FORZAR="${2:-}"

if [[ -z "$DESTINO" ]]; then
  echo "Uso: ./instalar.sh <ruta-del-workspace> [--forzar]" >&2
  exit 2
fi
if [[ ! -d "$DESTINO" ]]; then
  echo "El destino no existe o no es un directorio: $DESTINO" >&2
  exit 1
fi
DESTINO="$(cd "$DESTINO" && pwd)"
if [[ "$DESTINO" == "$ORIGEN" ]]; then
  echo "El destino es este mismo repo. Instala en el workspace donde vas a trabajar." >&2
  exit 1
fi

VERSION="$(tr -d '[:space:]' < "$ORIGEN/VERSION")"
KIRO="$DESTINO/.kiro"

if [[ -e "$KIRO" && "$FORZAR" != "--forzar" ]]; then
  echo "AVISO: $KIRO ya existe."
  echo "       Se sobrescribiran los archivos del framework EDF con el mismo nombre"
  echo "       (steering 00-11, skills edf-*, hooks/validar-expediente.json y compuerta-etapa0.json, specs/orquestador)."
  echo "       El resto de tu .kiro/ no se toca."
  read -r -p "       Continuar? [s/N] " r
  [[ "$r" == "s" || "$r" == "S" ]] || { echo "Cancelado."; exit 0; }
fi

copiar() {  # copiar <desde> <hacia>
  [[ -d "$1" ]] || return 0
  mkdir -p "$2"
  cp -R "$1/." "$2/"
}

copiar "$ORIGEN/kiro/steering" "$KIRO/steering"
copiar "$ORIGEN/kiro/skills"   "$KIRO/skills"
copiar "$ORIGEN/kiro/hooks"    "$KIRO/hooks"
copiar "$ORIGEN/kiro/specs"    "$KIRO/specs"
copiar "$ORIGEN/tools"         "$DESTINO/tools"
copiar "$ORIGEN/docs"          "$DESTINO/docs/edf-framework"
mkdir -p "$DESTINO/expedientes"
chmod +x "$DESTINO/tools/"*.py 2>/dev/null || true
echo "$VERSION" > "$KIRO/edf-framework.version"

N_STEERING="$(find "$KIRO/steering" -maxdepth 1 -name '*.md' | wc -l | tr -d ' ')"
SKILLS="$(cd "$KIRO/skills" && ls -d edf-* 2>/dev/null | tr '\n' ' ')"

echo
echo "edf-framework-IA $VERSION instalado en: $DESTINO"
echo "  .kiro/steering/     $N_STEERING archivos"
echo "  .kiro/skills/       $SKILLS"
echo "  .kiro/hooks/        validar-expediente.json, compuerta-etapa0.json"
echo "  .kiro/specs/        orquestador (diseno, certificacion, tareas, trazabilidad)"
echo "  tools/              validar-expediente.py, validar-expedientes.py, verificar-claves.py, verificar-insumos-etapa0.py"
echo "  docs/edf-framework/ arquitectura y contratos (para personas)"
echo "  expedientes/        aqui vive el expediente de cada ingesta"
echo
echo "Requisitos: Python 3 con 'pip install jsonschema' (validador del expediente)."
echo "            Java 17 + Maven 3.9 solo para los generadores de Validation, Acceptance y Curation."
echo
echo "Abre Kiro en $DESTINO y arranca con:"
echo "  \"Orquestador: inicia el expediente de la ingesta <NOMBRE>\""
