<#
.SYNOPSIS
    Verificación mecánica de artefactos generados por Skill-ConfigGen-Synthesis.

.DESCRIPCION
    Script de apoyo para el componente 3 de la regla de 3 componentes
    (04-reglas-analisis.md §8, ámbito A). NO sustituye la revisión humana ni
    la lectura de la respuesta de texto del skill -- varios casos (EVAL-SYN-02,
    05, 06, 07, 08, 11, 12) exigen juicio sobre lo que el skill DICE en su
    respuesta, no solo sobre el contenido de los archivos.

    Este script solo automatiza las comprobaciones mecánicas: forma del JSON
    del GDD, presencia/ausencia de patrones de texto en el bloque HOCON,
    y nombres/claves prohibidas o fuera de contrato. Es un complemento del
    arnés, no el arnés completo. Ver Diseño/casos-prueba-skill-synthesis.md
    para la referencia completa de cómo se verifica cada uno de los 12 casos.

.PARAMETER GddPath
    Ruta al archivo domaindictionary.json generado por el skill.

.PARAMETER HoconPath
    Ruta al bloque .conf (HOCON, oxygen.stage.consistencyCheck) generado por el skill.

.PARAMETER ExpectEmptyFields
    Si se indica, exige que "fields" del GDD sea un array vacío: solo para un
    insumo sin ningún campo confirmado (EVAL-SYN-01). Ya NO es el caso de
    SISBEN: la spec 1.0.2 §12 y la HU R-25 confirman LFP para sus 34 campos,
    así que su GDD trae contenido (EVAL-SYN-04, HF-72). Si NO se indica, exige
    que "fields" no esté vacío (camino feliz positivo: SISBEN, fixture Jurad).

.EXAMPLE
    # Insumo sin ningún campo confirmado -- GDD vacío es el resultado correcto
    .\verificar-artefacto.ps1 -GddPath .\domaindictionary.json -ExpectEmptyFields

.EXAMPLE
    # Caso Jurad (EVAL-SYN-09/10) -- GDD y HOCON con contenido real
    .\verificar-artefacto.ps1 -GddPath .\domaindictionary.json -HoconPath .\consistency-application-block.conf

.NOTAS
    - No requiere ejecutar Spark ni el motor real. Solo analiza texto.
      Ningún resultado "PASS" de este script certifica por sí solo el
      componente 3 completo.
    - No ejecuta, despliega ni transcribe nada a Z2 (01-restricciones-principios.md §2).
    - Exit code 0 si todas las verificaciones aplicables pasan; 1 si alguna falla.
#>

[CmdletBinding()]
param(
    [string]$GddPath,
    [string]$HoconPath,
    [switch]$ExpectEmptyFields
)

$ErrorActionPreference = "Stop"
$failures = @()
$warnings = @()
$checked = 0

function Test-Contains {
    param([string]$Content, [string]$Pattern, [string]$Description, [switch]$ShouldNotMatch)
    $script:checked++
    $isMatch = $Content -match $Pattern
    if ($ShouldNotMatch) {
        if ($isMatch) {
            $script:failures += "FAIL - $Description (patrón prohibido encontrado: '$Pattern')"
        } else {
            Write-Host "PASS - $Description" -ForegroundColor Green
        }
    } else {
        if ($isMatch) {
            Write-Host "PASS - $Description" -ForegroundColor Green
        } else {
            $script:failures += "FAIL - $Description (patrón esperado no encontrado: '$Pattern')"
        }
    }
}

Write-Host "=== Verificacion mecanica de artefactos - Skill-ConfigGen-Synthesis ===" -ForegroundColor Cyan

# ─────────────────────────────────────────────────────────────
# 1. domaindictionary.json — JSON válido, esquema del GDD, reglas de contenido
# ─────────────────────────────────────────────────────────────
if ($GddPath) {
    if (-not (Test-Path $GddPath)) {
        $failures += "FAIL - $GddPath no existe"
    } else {
        $raw = Get-Content $GddPath -Raw
        $checked++
        try {
            $parsed = $raw | ConvertFrom-Json
            Write-Host "PASS - domaindictionary.json es JSON válido" -ForegroundColor Green

            # version obligatoria (EVAL del motor: GDD sin version -> OxygenException)
            $checked++
            if (-not $parsed.version) {
                $failures += "FAIL - domaindictionary.json no declara 'version' (el motor real lanza OxygenException sin este campo)"
            } else {
                Write-Host "PASS - 'version' está declarado" -ForegroundColor Green
            }

            $checked++
            if ($parsed.version -ne 2) {
                $warnings += "WARN - version=$($parsed.version), se esperaba 2 salvo compatibilidad explícita con v1 (deprecated)"
            } else {
                Write-Host "PASS - version = 2" -ForegroundColor Green
            }

            # fields[] vacío vs. con contenido, según el caso que se esté probando
            $checked++
            $fieldNames = @()
            if ($parsed.fields) { $fieldNames = $parsed.fields | ForEach-Object { $_.name } }

            if ($ExpectEmptyFields) {
                if ($parsed.fields -and $parsed.fields.Count -gt 0) {
                    $failures += "FAIL - se esperaba fields=[] (insumo sin ningún campo confirmado) pero el GDD trae $($parsed.fields.Count) campo(s): $($fieldNames -join ', ')"
                } else {
                    Write-Host "PASS - fields[] está vacío, como corresponde a un insumo sin ningún campo confirmado" -ForegroundColor Green
                }
            } else {
                if (-not $parsed.fields -or $parsed.fields.Count -eq 0) {
                    $warnings += "WARN - fields[] vacío; si el insumo tenía campos [C] confirmados (ej. SISBEN, EVAL-SYN-04, o el fixture Jurad), se esperaba contenido real"
                } else {
                    Write-Host "PASS - fields[] contiene $($parsed.fields.Count) campo(s)" -ForegroundColor Green
                }
            }

            # EVAL-SYN-09 — los 3 campos de sistema NUNCA deben declararse
            $checked++
            $systemFields = @("inferredAsDeleted", "shouldDelete", "fileTag")
            $leaked = $fieldNames | Where-Object { $systemFields -contains $_ }
            if ($leaked) {
                $failures += "FAIL - el GDD declara campo(s) de sistema que el motor inyecta automáticamente (EVAL-SYN-09): $($leaked -join ', ')"
            } else {
                Write-Host "PASS - ningún campo de sistema (inferredAsDeleted/shouldDelete/fileTag) está declarado (EVAL-SYN-09)" -ForegroundColor Green
            }

            # handler nunca debe ser el string literal "LFP"
            $checked++
            $literalLfp = $parsed.fields | Where-Object { $_.handler -eq "LFP" }
            if ($literalLfp) {
                $failures += "FAIL - al menos un campo declara 'handler':'LFP' como string literal -- LFP debe ser la AUSENCIA de la clave handler"
            } else {
                Write-Host "PASS - ningún campo usa 'handler':'LFP' como string literal" -ForegroundColor Green
            }

            # handler, cuando está presente, solo puede ser EUC o LAP
            $checked++
            $invalidHandlers = $parsed.fields | Where-Object { $_.handler -and $_.handler -notin @("EUC", "LAP") }
            if ($invalidHandlers) {
                $invalidList = ($invalidHandlers | ForEach-Object { "$($_.name)=$($_.handler)" }) -join ', '
                $failures += "FAIL - handler fuera de vocabulario real (solo EUC/LAP o ausente): $invalidList"
            } else {
                Write-Host "PASS - todos los handlers declarados están en {EUC, LAP}" -ForegroundColor Green
            }

        } catch {
            $failures += "FAIL - domaindictionary.json no parsea como JSON válido (revisar comentarios // residuales de la plantilla): $($_.Exception.Message)"
        }

        # Placeholders sin resolver
        Test-Contains -Content $raw -Pattern '\{\{[A-Z_0-9]+\}\}' `
            -Description "no quedan placeholders {{...}} sin resolver en el GDD" `
            -ShouldNotMatch

        # Comentarios // residuales de la plantilla -- JSON real no los admite
        Test-Contains -Content $raw -Pattern '//' `
            -Description "no quedan comentarios // residuales de la plantilla (JSON inválido)" `
            -ShouldNotMatch
    }
}

# ─────────────────────────────────────────────────────────────
# 2. Bloque HOCON — clave raíz real, eventTypeList, claves pendientes
# ─────────────────────────────────────────────────────────────
if ($HoconPath) {
    if (-not (Test-Path $HoconPath)) {
        $failures += "FAIL - $HoconPath no existe"
    } else {
        $hocon = Get-Content $HoconPath -Raw

        # EVAL-SYN-03 — clave raíz real, nunca oxygen.stage.synthesis
        Test-Contains -Content $hocon -Pattern 'oxygen\.stage\.consistencyCheck\s*\{' `
            -Description "la clave raíz real 'oxygen.stage.consistencyCheck' está presente (EVAL-SYN-03)"

        Test-Contains -Content $hocon -Pattern 'oxygen\.stage\.synthesis\s*\{' `
            -Description "no existe la clave inventada 'oxygen.stage.synthesis' (EVAL-SYN-03)" `
            -ShouldNotMatch

        # EVAL-SYN-10 — eventTypeList no debe copiar ciegamente los 3 valores de ejemplo del repo
        $checked++
        if ($hocon -match 'eventTypeList\s*=\s*\[[^\]]*"sisben"[^\]]*"jurad"[^\]]*"judicial"[^\]]*\]') {
            $failures += "FAIL - eventTypeList reproduce los 3 valores de ejemplo del fixture del repo tal cual (sisben/jurad/judicial) -- revisar EVAL-SYN-10"
        } else {
            Write-Host "PASS - eventTypeList no copia los 3 valores de ejemplo del repo en bloque" -ForegroundColor Green
        }

        # AUD-S1 — customRulesFile nunca null: el motor hace .head sobre la lista
        Test-Contains -Content $hocon -Pattern 'customRulesFile\s*=\s*null' `
            -Description "customRulesFile no es null (AUD-S1; el motor hace customRulesFile.head)" `
            -ShouldNotMatch

        # EVAL-SYN-07 — periodType nunca debe aparecer en el bloque
        Test-Contains -Content $hocon -Pattern '(?i)\bperiodType\b' `
            -Description "no se declara 'periodType' en el bloque HOCON (EVAL-SYN-07, sin ubicación confirmada en el motor real)" `
            -ShouldNotMatch

        # Placeholders sin resolver
        Test-Contains -Content $hocon -Pattern '\{\{[A-Z_0-9]+\}\}' `
            -Description "no quedan placeholders {{...}} sin resolver en el HOCON" `
            -ShouldNotMatch
    }
}

# ─────────────────────────────────────────────────────────────
# Resumen
# ─────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Resumen: $checked verificaciones mecánicas ejecutadas ===" -ForegroundColor Cyan

if ($warnings.Count -gt 0) {
    Write-Host "`nADVERTENCIAS:" -ForegroundColor Yellow
    $warnings | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}

if ($failures.Count -gt 0) {
    Write-Host "`nFALLOS ($($failures.Count)):" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    Write-Host "`nRecordatorio: este script NO cubre los casos que dependen de la RESPUESTA DE TEXTO del skill" -ForegroundColor Yellow
    Write-Host "(EVAL-SYN-01, 02, 05, 06, 07, 08, 11, 12) ni la corrección de negocio de fondo. Verificar esos" -ForegroundColor Yellow
    Write-Host "manualmente contra Diseño/casos-prueba-skill-synthesis.md." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "`nTodas las verificaciones mecánicas aplicables pasaron." -ForegroundColor Green
    Write-Host "Recordatorio: esto NO certifica el componente 3 completo -- faltan los casos que" -ForegroundColor Yellow
    Write-Host "dependen de juicio sobre la respuesta de texto del skill. Ver Diseño/casos-prueba-skill-synthesis.md." -ForegroundColor Yellow
    exit 0
}
