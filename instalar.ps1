<#
.SYNOPSIS
  Instala el framework edf-framework-IA en el workspace donde el ingeniero de datos va a trabajar con Kiro.

.DESCRIPTION
  Kiro lee la configuracion como `.kiro/` en la RAIZ del workspace abierto. El instalador copia (desde
  `.kiro/` del repo, o desde `kiro/` si una copia antigua la tiene visible):

    .kiro/steering/*     -> <destino>/.kiro/steering/
    .kiro/skills/*       -> <destino>/.kiro/skills/
    .kiro/hooks/*        -> <destino>/.kiro/hooks/   (con el interprete de Python detectado en este equipo)
    .kiro/specs/*        -> <destino>/.kiro/specs/
    tools/*              -> <destino>/tools/
    docs/*               -> <destino>/docs/edf-framework/
    (crea)               -> <destino>/expedientes/   (nunca toca expedientes existentes)

  Solo sobrescribe archivos del framework con el mismo nombre. No borra nada del workspace.

.PARAMETER Destino
  Ruta del workspace (carpeta que se abrira en Kiro). Debe existir.

.PARAMETER Forzar
  No pide confirmacion si el destino ya tiene `.kiro/`.

.EXAMPLE
  .\instalar.ps1 -Destino "C:\trabajo\ingesta-sisben"
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Destino,
  [switch]$Forzar
)
$ErrorActionPreference = 'Stop'

$Origen = $PSScriptRoot
if (-not (Test-Path -LiteralPath $Destino -PathType Container)) {
  Write-Error "El destino no existe o no es una carpeta: $Destino"
  exit 1
}
$Destino = (Resolve-Path -LiteralPath $Destino).Path
if ($Destino.TrimEnd('\') -ieq $Origen.TrimEnd('\')) {
  Write-Error "El destino es este mismo repo. Instala en el workspace donde vas a trabajar."
  exit 1
}

$version = (Get-Content -LiteralPath (Join-Path $Origen 'VERSION') -Raw).Trim()
$kiroDest = Join-Path $Destino '.kiro'

if ((Test-Path -LiteralPath $kiroDest) -and -not $Forzar) {
  Write-Host "AVISO: $kiroDest ya existe."
  Write-Host "       Se sobrescribiran los archivos del framework EDF con el mismo nombre"
  Write-Host "       (steering 00-11, skills edf-*, hooks/validar-expediente.json y compuerta-etapa0.json, specs/orquestador)."
  Write-Host "       El resto de tu .kiro/ no se toca."
  $r = Read-Host "       Continuar? [s/N]"
  if ($r -notin @('s', 'S')) { Write-Host 'Cancelado.'; exit 0 }
}

function Copiar-Contenido([string]$desde, [string]$hacia) {
  if (-not (Test-Path -LiteralPath $desde)) { return }
  New-Item -ItemType Directory -Force -Path $hacia | Out-Null
  Copy-Item -Path (Join-Path $desde '*') -Destination $hacia -Recurse -Force
}

# Origen de la configuracion: `.kiro/` (actual) o `kiro/` (copias antiguas). Antes solo se leia `kiro/`
# y, como el repo usa `.kiro/`, no se copiaba nada (AUD-O5).
$conf = Join-Path $Origen '.kiro'
if (-not (Test-Path -LiteralPath (Join-Path $conf 'skills'))) { $conf = Join-Path $Origen 'kiro' }
if (-not (Test-Path -LiteralPath (Join-Path $conf 'skills'))) {
  Write-Error "No se encontro la configuracion del framework (.kiro/ ni kiro/) en $Origen"
  exit 1
}

Copiar-Contenido (Join-Path $conf 'steering') (Join-Path $kiroDest 'steering')
Copiar-Contenido (Join-Path $conf 'skills')   (Join-Path $kiroDest 'skills')
Copiar-Contenido (Join-Path $conf 'hooks')    (Join-Path $kiroDest 'hooks')
Copiar-Contenido (Join-Path $conf 'specs')    (Join-Path $kiroDest 'specs')
Copiar-Contenido (Join-Path $Origen 'tools')         (Join-Path $Destino 'tools')
Copiar-Contenido (Join-Path $Origen 'docs')          (Join-Path $Destino 'docs\edf-framework')
New-Item -ItemType Directory -Force -Path (Join-Path $Destino 'expedientes') | Out-Null
Set-Content -LiteralPath (Join-Path $kiroDest 'edf-framework.version') -Value $version -Encoding ASCII

# Interprete de Python para los hooks (AUD-O2). En Windows `python` suele ser el acceso directo a la
# Microsoft Store: el hook no devuelve 2 y la compuerta de la etapa 0 falla abierta. Se prueba cada
# candidato ejecutando Python de verdad.
$py = $null
foreach ($c in @('py -3', 'python', 'python3')) {
  $partes = $c.Split(' ')
  try {
    $args2 = @($partes | Select-Object -Skip 1) + @('-c', 'import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)')
    & $partes[0] @args2 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $py = $c; break }
  } catch { }
}
if ($py) {
  foreach ($h in @('compuerta-etapa0.json', 'validar-expediente.json')) {
    $ruta = Join-Path $kiroDest "hooks\$h"
    if (Test-Path -LiteralPath $ruta) {
      $txt = Get-Content -LiteralPath $ruta -Raw -Encoding UTF8
      $txt = $txt.Replace('"python tools/', ('"' + $py + ' tools/')).Replace('`python tools/', ('`' + $py + ' tools/'))
      [System.IO.File]::WriteAllText($ruta, $txt, (New-Object System.Text.UTF8Encoding($false)))
    }
  }
  $pyMsg = "hooks configurados con '$py'"
} else {
  $pyMsg = "ATENCION: no se encontro Python 3; los hooks quedan con 'python' y la compuerta de la etapa 0 NO bloqueara"
}

$nSteering = (Get-ChildItem -LiteralPath (Join-Path $kiroDest 'steering') -Filter '*.md' | Measure-Object).Count
$skills = (Get-ChildItem -LiteralPath (Join-Path $kiroDest 'skills') -Directory -Filter 'edf-*' | ForEach-Object { $_.Name }) -join ', '

Write-Host ''
Write-Host "edf-framework-IA $version instalado en: $Destino"
Write-Host "  .kiro/steering/     $nSteering archivos"
Write-Host "  .kiro/skills/       $skills"
Write-Host "  .kiro/hooks/        validar-expediente.json, compuerta-etapa0.json ($pyMsg)"
Write-Host "  .kiro/specs/        orquestador (diseno, certificacion, tareas, trazabilidad)"
Write-Host "  tools/              validar-expediente.py, validar-expedientes.py, verificar-claves.py, verificar-insumos-etapa0.py"
Write-Host "  docs/edf-framework/ arquitectura y contratos (para personas)"
Write-Host "  expedientes/        aqui vive el expediente de cada ingesta"
Write-Host ''
Write-Host 'Requisitos: Python 3 con `pip install pyyaml jsonschema` (generadores, validador del expediente).'
Write-Host '            No se necesita Java ni Maven: ninguna skill compila.'
Write-Host ''
Write-Host "Abre Kiro en $Destino y arranca con:"
Write-Host '  "Orquestador: inicia el expediente de la ingesta <NOMBRE>"'
