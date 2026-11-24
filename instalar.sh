#!/usr/bin/env bash
# Instala el framework edf-framework-IA en el workspace donde el ingeniero de datos va a trabajar con Kiro.
#
# Kiro lee la configuracion como `.kiro/` en la RAIZ del workspace abierto. El instalador copia (desde
# `.kiro/` del repo, o desde `kiro/` si una copia antigua la tiene visible):
#   .kiro/steering/* -> <destino>/.kiro/steering/
#   .kiro/skills/*   -> <destino>/.kiro/skills/
#   .kiro/hooks/*    -> <destino>/.kiro/hooks/   (con el interprete de Python detectado en este equipo)
#   .kiro/specs/*    -> <destino>/.kiro/specs/
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

# Origen de la configuracion: `.kiro/` (actual) o `kiro/` (copias antiguas). Antes solo se leia `kiro/`
# y, como el repo usa `.kiro/`, no se copiaba nada (AUD-O5).
if [[ -d "$ORIGEN/.kiro" ]]; then CONF="$ORIGEN/.kiro"; else CONF="$ORIGEN/kiro"; fi
if [[ ! -d "$CONF/skills" ]]; then
  echo "No se encontro la configuracion del framework (.kiro/ ni kiro/) en $ORIGEN" >&2
  exit 1
fi

copiar "$CONF/steering" "$KIRO/steering"
copiar "$CONF/skills"   "$KIRO/skills"
copiar "$CONF/hooks"    "$KIRO/hooks"
copiar "$CONF/specs"    "$KIRO/specs"
copiar "$ORIGEN/tools"         "$DESTINO/tools"
copiar "$ORIGEN/docs"          "$DESTINO/docs/edf-framework"
mkdir -p "$DESTINO/expedientes"
chmod +x "$DESTINO/tools/"*.py 2>/dev/null || true
echo "$VERSION" > "$KIRO/edf-framework.version"

# Interprete de Python para los hooks (AUD-O2). Un comando que no existe hace que la compuerta de la etapa 0
# falle abierta: el hook no devuelve 2 y Kiro deja pasar el prompt.
PY=""
for c in python3 python; do
  if command -v "$c" >/dev/null 2>&1 && "$c" -c "import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)" >/dev/null 2>&1; then
    PY="$c"; break
  fi
done
if [[ -n "$PY" ]]; then
  for h in "$KIRO/hooks/compuerta-etapa0.json" "$KIRO/hooks/validar-expediente.json"; do
    [[ -f "$h" ]] && sed -i.bak -e "s#\"python tools/#\"$PY tools/#g" -e "s#\`python tools/#\`$PY tools/#g" "$h" && rm -f "$h.bak"
  done
  PY_MSG="hooks configurados con '$PY'"
else
  PY_MSG="ATENCION: no se encontro Python 3; los hooks quedan con 'python' y la compuerta de la etapa 0 NO bloqueara"
fi

N_STEERING="$(find "$KIRO/steering" -maxdepth 1 -name '*.md' | wc -l | tr -d ' ')"
SKILLS="$(cd "$KIRO/skills" && ls -d edf-* 2>/dev/null | tr '\n' ' ')"

echo
echo "edf-framework-IA $VERSION instalado en: $DESTINO"
echo "  .kiro/steering/     $N_STEERING archivos"
echo "  .kiro/skills/       $SKILLS"
echo "  .kiro/hooks/        validar-expediente.json, compuerta-etapa0.json ($PY_MSG)"
echo "  .kiro/specs/        orquestador (diseno, certificacion, tareas, trazabilidad)"
echo "  tools/              validar-expediente.py, validar-expedientes.py, verificar-claves.py, verificar-insumos-etapa0.py"
echo "  docs/edf-framework/ arquitectura y contratos (para personas)"
echo "  expedientes/        aqui vive el expediente de cada ingesta"
echo
echo "Requisitos: Python 3 con 'pip install pyyaml jsonschema' (generadores, validador del expediente)."
echo "            No se necesita Java ni Maven: ninguna skill compila."
echo
echo "Abre Kiro en $DESTINO y arranca con:"
echo "  \"Orquestador: inicia el expediente de la ingesta <NOMBRE>\""
