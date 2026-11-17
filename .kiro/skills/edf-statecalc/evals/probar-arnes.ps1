<#
.SYNOPSIS
    Autoprueba del verificador verificar-artefacto.ps1 contra los fixtures sintéticos.

.DESCRIPCION
    Ejecuta verificar-artefacto.ps1 (en un proceso hijo de Windows PowerShell) contra
    los artefactos "golden" (deben pasar) y los "defectuosos" (deben fallar con el
    código de verificación esperado). Imprime una tabla y devuelve exit code 0 si
    TODAS las expectativas se cumplen, 1 si alguna no.

    Esto prueba el VERIFICADOR, no el skill. Los fixtures son sintéticos
    (evals\fixtures\golden\LEEME.md y evals\fixtures\defectuosos\LEEME.md).
    No escribe archivos, no ejecuta Spark ni toca Z2/Z3.

.EXAMPLE
    .\probar-arnes.ps1
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$verificador = Join-Path $aqui 'verificar-artefacto.ps1'
$fx = Join-Path $aqui 'fixtures'
$F = 'statecalc-foundation-local.json'
$C = 'statecalc-calculation-local.json'

function G($d) { Join-Path (Join-Path $fx 'golden') $d }
function D($d) { Join-Path (Join-Path $fx 'defectuosos') $d }
function Par($dir, $caso, [string[]]$extra) {
    $a = @('-FoundationPath', (Join-Path $dir $F), '-CalculationPath', (Join-Path $dir $C), '-Caso', $caso)
    if ($extra) { $a += $extra }
    return , $a
}

# Cada prueba: Nombre, Args, Exit esperado, Patrón esperado en la salida (vacío = no se exige)
$pruebas = @(
    # --- Golden: deben ser aceptados ---
    @{ N = 'golden sisben-camino-feliz / EVAL-SC-04';   A = (Par (G 'sisben-camino-feliz') 'EVAL-SC-04');   E = 0; P = 'P04: Foundation tiene exactamente 2 campos' },
    @{ N = 'golden sisben-camino-feliz / EVAL-SC-02';   A = (Par (G 'sisben-camino-feliz') 'EVAL-SC-02');   E = 0; P = 'P02: hay una secci' },
    @{ N = 'golden agregados-sintetico / EVAL-SC-03';   A = (Par (G 'agregados-sintetico') 'EVAL-SC-03');   E = 0; P = 'P03: hay un aggregate SUM' },
    @{ N = 'golden agregados-sintetico / GENERAL';      A = (Par (G 'agregados-sintetico') 'GENERAL');      E = 0; P = 'C08: todo contains' },
    @{ N = 'golden sin-confirmaciones / EVAL-SC-01';    A = (Par (G 'sin-confirmaciones') 'EVAL-SC-01');    E = 0; P = 'P01: Foundation no activa' },
    @{ N = 'sin artefactos / EVAL-SC-05 (permitido)';   A = @('-Caso', 'EVAL-SC-05', '-SinArtefactos');     E = 0; P = 'admite que el skill no entregue' },
    @{ N = 'comparar camino-feliz vs corrida2';         A = @('-Comparar', ((G 'sisben-camino-feliz') + ',' + (G 'sisben-camino-feliz-corrida2'))); E = 0; P = 'mismos 2 elemento' },
    @{ N = 'comparar camino-feliz vs si mismo';         A = @('-Comparar', ((G 'sisben-camino-feliz') + ',' + (G 'sisben-camino-feliz'))); E = 0; P = 'mismos 2 elemento' },
    @{ N = 'defectuoso 08 con -PermitirWhere';          A = (Par (D '08-where-sin-autorizacion') 'EVAL-SC-11' @('-PermitirWhere')); E = 0; P = 'autorizado por -PermitirWhere' },
    @{ N = 'golden sisben-camino-feliz / EVAL-SC-05';   A = (Par (G 'sisben-camino-feliz') 'EVAL-SC-05');   E = 0; P = 'P05: .scoreRiesgo. y .segmento.' },
    @{ N = 'golden sisben-camino-feliz / EVAL-SC-07';   A = (Par (G 'sisben-camino-feliz') 'EVAL-SC-07');   E = 0; P = 'P07: fields\[\] solo contiene' },
    @{ N = 'golden sisben-camino-feliz / EVAL-SC-08';   A = (Par (G 'sisben-camino-feliz') 'EVAL-SC-08');   E = 0; P = 'G02: Calculation no contiene' },
    @{ N = 'golden sisben-camino-feliz / EVAL-SC-10';   A = (Par (G 'sisben-camino-feliz') 'EVAL-SC-10');   E = 0; P = 'P10: Calculation no referencia' },
    @{ N = 'golden sisben-camino-feliz / EVAL-SC-13';   A = (Par (G 'sisben-camino-feliz') 'EVAL-SC-13');   E = 0; P = 'P04: Foundation tiene exactamente 2 campos' },
    @{ N = 'golden agregados-sintetico / EVAL-SC-11';   A = (Par (G 'agregados-sintetico') 'EVAL-SC-11');   E = 0; P = 'C07: ninguna secci' },
    @{ N = 'sin artefactos / EVAL-SC-08 (permitido)';   A = @('-Caso', 'EVAL-SC-08', '-SinArtefactos');     E = 0; P = 'admite que el skill no entregue' },

    # --- Defectuosos: deben ser rechazados con el motivo correcto ---
    @{ N = 'defectuoso 01 grouping anidado / SC-02';    A = (Par (D '01-grouping-anidado') 'EVAL-SC-02');   E = 1; P = 'FAIL \[C02\]' },
    @{ N = 'defectuoso 02 type UNKNOWN';                A = (Par (D '02-type-unknown') 'GENERAL');          E = 1; P = 'FAIL \[F03\].*UNKNOWN' },
    @{ N = 'defectuoso 03 CUSTOM + handler';            A = (Par (D '03-custom-con-handler') 'GENERAL');    E = 1; P = 'FAIL \[F03\].*CUSTOM' },
    @{ N = 'defectuoso 03 CUSTOM + handler (G01)';      A = (Par (D '03-custom-con-handler') 'GENERAL');    E = 1; P = 'FAIL \[G01\]' },
    @{ N = 'defectuoso 04 CUSTOM_ON_SECTION';           A = (Par (D '04-custom-on-section') 'GENERAL');     E = 1; P = 'FAIL \[C03\].*CUSTOM_ON_SECTION' },
    @{ N = 'defectuoso 05 campo base subscriber/pin';   A = (Par (D '05-campo-base-subscriber') 'EVAL-SC-07'); E = 1; P = 'FAIL \[F04\].*subscriber' },
    @{ N = 'defectuoso 06 function Sum / SC-03';        A = (Par (D '06-function-minusculas') 'EVAL-SC-03'); E = 1; P = 'FAIL \[C05\].*Sum' },
    @{ N = 'defectuoso 06 function median';             A = (Par (D '06-function-minusculas') 'GENERAL');   E = 1; P = 'FAIL \[C05\].*median' },
    @{ N = 'defectuoso 07 select telefono ausente';     A = (Par (D '07-select-campo-ausente') 'EVAL-SC-10'); E = 1; P = 'FAIL \[C06\].*telefono' },
    @{ N = 'defectuoso 08 where sin autorizacion';      A = (Par (D '08-where-sin-autorizacion') 'EVAL-SC-11'); E = 1; P = 'FAIL \[C07\]' },
    @{ N = 'defectuoso 09 contains a seccion posterior'; A = (Par (D '09-contains-a-seccion-posterior') 'EVAL-SC-11'); E = 1; P = 'FAIL \[C08\].*DESPU' },
    @{ N = 'defectuoso 09 contains a seccion inexistente'; A = (Par (D '09-contains-a-seccion-posterior') 'EVAL-SC-11'); E = 1; P = 'FAIL \[C08\].*no existe' },
    @{ N = 'defectuoso 10 qualityThreshold';            A = (Par (D '10-clave-quality-threshold') 'EVAL-SC-08'); E = 1; P = 'FAIL \[G02\]' },
    @{ N = 'defectuoso 11 bestName XPM';                A = (Par (D '11-xpm-bestname') 'EVAL-SC-08');       E = 1; P = 'FAIL \[G02\]' },
    @{ N = 'defectuoso 12 camino feliz con 3 campos';   A = (Par (D '12-camino-feliz-campo-extra') 'EVAL-SC-04'); E = 1; P = 'FAIL \[P04\].*exactamente 2' },
    @{ N = 'defectuoso 12 fields no vacio / SC-01';     A = (Par (D '12-camino-feliz-campo-extra') 'EVAL-SC-01'); E = 1; P = 'FAIL \[P01\]' },
    @{ N = 'defectuoso 13 JSON con comentarios';        A = @('-FoundationPath', (Join-Path (D '13-json-con-comentarios') $F), '-Caso', 'GENERAL'); E = 1; P = 'FAIL \[V01\]' },
    @{ N = 'defectuoso 14 value/dataType incorrectos';  A = (Par (D '14-value-incorrecto') 'EVAL-SC-04');   E = 1; P = 'FAIL \[P04\].*personIdType' },
    @{ N = 'defectuoso 12 campo extra / EVAL-SC-07';    A = (Par (D '12-camino-feliz-campo-extra') 'EVAL-SC-07'); E = 1; P = 'FAIL \[P07\].*primerNombre' },
    @{ N = 'defectuoso 15 scoreRiesgo en Foundation';   A = (Par (D '15-campos-pendientes-sustituidos') 'EVAL-SC-05'); E = 1; P = 'FAIL \[P05\].*scoreRiesgo' },
    @{ N = 'defectuoso 15 segmento en Calculation';     A = (Par (D '15-campos-pendientes-sustituidos') 'EVAL-SC-05'); E = 1; P = 'FAIL \[P05\].*de Calculation usa .segmento' },
    @{ N = 'defectuoso 14 value incorrecto / EVAL-SC-13'; A = (Par (D '14-value-incorrecto') 'EVAL-SC-13'); E = 1; P = 'FAIL \[P04\].*personIdType' },
    @{ N = 'sin artefactos no admitido en EVAL-SC-04';  A = @('-Caso', 'EVAL-SC-04', '-SinArtefactos');     E = 1; P = 'FAIL \[P00\]' },
    @{ N = 'sin artefactos no admitido en EVAL-SC-07';  A = @('-Caso', 'EVAL-SC-07', '-SinArtefactos');     E = 1; P = 'FAIL \[P00\]' },
    @{ N = 'sin artefactos no admitido en EVAL-SC-13';  A = @('-Caso', 'EVAL-SC-13', '-SinArtefactos');     E = 1; P = 'FAIL \[P00\]' },
    @{ N = 'comparar camino-feliz vs 12-campo-extra';   A = @('-Comparar', ((G 'sisben-camino-feliz') + ',' + (D '12-camino-feliz-campo-extra'))); E = 1; P = 'FAIL \[R0[23]\]' },
    @{ N = 'error de uso: sin argumentos';              A = @();                                            E = 1; P = 'Error de uso' }
)

$ok = 0; $mal = 0
$filas = New-Object System.Collections.ArrayList
$i = 0
foreach ($t in $pruebas) {
    $i++
    $args2 = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $verificador) + @($t.A)
    $salida = (& powershell.exe @args2 2>&1 | Out-String)
    $codigo = $LASTEXITCODE
    $patronOk = $true
    if ($t.P) { $patronOk = ($salida -match $t.P) }
    $pasa = (($codigo -eq $t.E) -and $patronOk)
    if ($pasa) { $ok++ } else { $mal++ }
    $motivo = ''
    if (-not $pasa) { $motivo = "exit=$codigo (esperado $($t.E)); patron '$($t.P)' encontrado=$patronOk" }
    [void]$filas.Add([pscustomobject]@{ '#' = $i; Prueba = $t.N; 'Exit esp.' = $t.E; 'Exit real' = $codigo; Resultado = $(if ($pasa) { 'OK' } else { 'NO-OK' }); Detalle = $motivo })
}

$filas | Format-Table -AutoSize -Wrap | Out-String -Width 200 | Write-Host
Write-Host "Pruebas del arnes: $ok OK / $mal NO-OK de $($pruebas.Count)" -ForegroundColor $(if ($mal -eq 0) { 'Green' } else { 'Red' })
if ($mal -gt 0) { exit 1 } else { exit 0 }
