<#
.SYNOPSIS
    Verificación mecánica de artefactos generados por Skill-ConfigGen-Pinning.

.DESCRIPCION
    Script de apoyo para el componente 3 de la regla de 3 componentes
    (04-reglas-analisis.md §8, ámbito A). NO sustituye la revisión humana ni
    la lectura de la respuesta de texto del skill (varios casos, como
    EVAL-PIN-02, EVAL-PIN-03, EVAL-PIN-09, EVAL-PIN-10 y EVAL-PIN-11, exigen
    juicio sobre lo que el skill DICE en su respuesta, no solo sobre el
    contenido de los archivos).

    Este script solo automatiza las comprobaciones mecánicas: forma del JSON,
    presencia/ausencia de patrones de texto, antipatrones léxicos y nombres
    prohibidos. Es un complemento del arnés, no el arnés completo. Ver
    Diseño/casos-prueba-skill-pinning.md para la referencia completa de cómo
    se verifica cada uno de los 11 casos.

.PARAMETER MatcherPath
    Ruta al archivo .scala del IdentityMatcher generado por el skill.

.PARAMETER AttributesJsonPath
    Ruta al archivo identifying-attributes.json generado por el skill.

.PARAMETER HoconPath
    Ruta al bloque .conf (HOCON) generado por el skill.

.PARAMETER Modo
    propio (default) | colombia (HF-67). En modo Colombia el bloque de la
    ingesta es solo comentarios (SKILL.md «Modo Colombia»): se exigen 0 líneas
    activas, la lista VERIFICAR completa (HF-65) y, con -PropuestaDir, que no
    haya IdentityMatcher.scala ni identifying-attributes.json (EVAL-PIN-12..14).
    Equivalente en Python: scripts/render_bloque_colombia.py --verificar.

.PARAMETER PropuestaDir
    Carpeta propuestas/pinning/ de la ingesta (solo con -Modo colombia, EVAL-PIN-14).

.EXAMPLE
    .\verificar-artefacto.ps1 -MatcherPath .\JuradIdentityMatcher.scala `
                               -AttributesJsonPath .\identifying-attributes.json `
                               -HoconPath .\pinning-application-block.conf

.EXAMPLE
    .\verificar-artefacto.ps1 -Modo colombia -HoconPath .\pinning-application-block.sisben.conf `
                               -PropuestaDir .\propuestas\pinning

.NOTAS
    - No requiere compilar Scala. Solo analiza texto. Ningún resultado "PASS"
      de este script certifica por sí solo el componente 3 completo.
    - No ejecuta, despliega ni transcribe nada a Z2 (01-restricciones-principios.md §2).
    - Exit code 0 si todas las verificaciones aplicables pasan; 1 si alguna falla.
#>

[CmdletBinding()]
param(
    [string]$MatcherPath,
    [string]$AttributesJsonPath,
    [string]$HoconPath,
    [ValidateSet("propio", "colombia")]
    [string]$Modo = "propio",
    [string]$PropuestaDir
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

Write-Host "=== Verificacion mecanica de artefactos - Skill-ConfigGen-Pinning ===" -ForegroundColor Cyan

# ─────────────────────────────────────────────────────────────
# 1. identifying-attributes.json — debe ser JSON válido, array plano de strings
# ─────────────────────────────────────────────────────────────
if ($AttributesJsonPath) {
    if (-not (Test-Path $AttributesJsonPath)) {
        $failures += "FAIL - $AttributesJsonPath no existe"
    } else {
        $raw = Get-Content $AttributesJsonPath -Raw
        $checked++
        try {
            $parsed = $raw | ConvertFrom-Json
            Write-Host "PASS - identifying-attributes.json es JSON válido" -ForegroundColor Green

            $checked++
            if ($parsed -isnot [System.Array]) {
                $failures += "FAIL - identifying-attributes.json no es un array plano"
            } else {
                Write-Host "PASS - identifying-attributes.json es un array" -ForegroundColor Green
            }

            $checked++
            $nonString = $parsed | Where-Object { $_ -isnot [string] }
            if ($nonString) {
                $failures += "FAIL - identifying-attributes.json contiene elementos no-string"
            } else {
                Write-Host "PASS - todos los elementos son strings" -ForegroundColor Green
            }
        } catch {
            $failures += "FAIL - identifying-attributes.json no parsea como JSON válido (revisar comentarios // residuales de la plantilla, EVAL-PIN-04): $($_.Exception.Message)"
        }

        # El array activo de SISBEN jamás debe incluir middleName/secondLastName/cellPhone
        Test-Contains -Content $raw -Pattern '"(middleName|secondLastName|cellPhone)"' `
            -Description "identifying-attributes.json no incluye atributos no confirmados (middleName/secondLastName/cellPhone) para SISBEN" `
            -ShouldNotMatch
    }
}

# ─────────────────────────────────────────────────────────────
# 2. IdentityMatcher.scala — convenciones, antipatrones, contrato
# ─────────────────────────────────────────────────────────────
if ($MatcherPath) {
    if (-not (Test-Path $MatcherPath)) {
        $failures += "FAIL - $MatcherPath no existe"
    } else {
        $code = Get-Content $MatcherPath -Raw

        # EVAL-PIN-06 — MUST CALL SUPER
        Test-Contains -Content $code -Pattern 'super\.doesMatch\s*\(' `
            -Description "doesMatch invoca super.doesMatch (EVAL-PIN-06)"

        # EVAL-PIN-05 — verificación aritmética documentada
        Test-Contains -Content $code -Pattern '(?i)similarityThreshold|umbral' `
            -Description "el código menciona el umbral de similitud (contexto para EVAL-PIN-05)"

        # EVAL-PIN-07 — no debe existir un verbo pin...() como nombre de método
        Test-Contains -Content $code -Pattern '(?i)def\s+pin[A-Z]\w*\s*\(' `
            -Description "no existe un método con verbo 'pin...()' (EVAL-PIN-07)" `
            -ShouldNotMatch

        # EVAL-PIN-08 — antipatrones confirmados (references/convenciones-oxygen-pinning.md §5)
        Test-Contains -Content $code -Pattern 'logger\.error\s*\(' `
            -Description "no usa logger.error para mensajes informativos (EVAL-PIN-08)" `
            -ShouldNotMatch

        Test-Contains -Content $code -Pattern '(?<!//.*)println\s*\(' `
            -Description "no usa println (EVAL-PIN-08)" `
            -ShouldNotMatch

        Test-Contains -Content $code -Pattern '==\s*None\b|\bNone\s*==' `
            -Description "no compara Option con == None (EVAL-PIN-08)" `
            -ShouldNotMatch

        # Placeholders sin resolver — ningún {{...}} debe sobrevivir en el artefacto final
        Test-Contains -Content $code -Pattern '\{\{[A-Z_0-9]+\}\}' `
            -Description "no quedan placeholders {{...}} sin resolver" `
            -ShouldNotMatch

        # EVAL-PIN-02 — no debe implementar IdentityImpl como si fuera verificado
        Test-Contains -Content $code -Pattern '(?i)class\s+IdentityImpl\b' `
            -Description "no define una clase IdentityImpl como implementación verificada (EVAL-PIN-02)" `
            -ShouldNotMatch
    }
}

# ─────────────────────────────────────────────────────────────
# 3. Bloque HOCON — groupSSR nunca resuelto por iniciativa propia
# ─────────────────────────────────────────────────────────────
if ($HoconPath) {
    if (-not (Test-Path $HoconPath)) {
        $failures += "FAIL - $HoconPath no existe"
    } else {
        $hocon = Get-Content $HoconPath -Raw
        # HF-67 (AUD-P-23): las reglas sobre valores se evalúan FUERA de comentarios. El comentario obligatorio
        # del colapso («groupSSR = true (:676)») no es un valor y daba un falso positivo seguro en modo Colombia.
        $lineasHocon = $hocon -split "`r?`n"
        $esComentario = { param($l) $l.Trim().StartsWith("#") -or $l.Trim().StartsWith("//") }
        $activasHocon = @($lineasHocon | Where-Object { $_.Trim() -and -not (& $esComentario $_) })
        $hoconActivo = $activasHocon -join "`n"

        # EVAL-PIN-03 / EVAL-PIN-10 variante — groupSSR debe quedar como TODO, no true/false fijo
        $checked++
        if ($hoconActivo -match 'groupSSR\s*=\s*(true|false)\b' -and $hocon -notmatch '(?i)TODO.*groupSSR|groupSSR.*TODO') {
            $failures += "FAIL - groupSSR tiene un valor fijo (true/false) sin TODO asociado (EVAL-PIN-03)"
        } else {
            Write-Host "PASS - groupSSR no fue resuelto sin TODO explícito (EVAL-PIN-03)" -ForegroundColor Green
        }

        # Placeholders sin resolver
        Test-Contains -Content $hocon -Pattern '\{\{[A-Z_0-9]+\}\}' `
            -Description "no quedan placeholders {{...}} sin resolver en el HOCON" `
            -ShouldNotMatch

        if ($Modo -eq "colombia") {
            # EVAL-PIN-12 / EVAL-PIN-14 — ninguna línea activa (tampoco reusePin = ...)
            $checked++
            if ($activasHocon.Count -gt 0) {
                $failures += "FAIL - modo Colombia: $($activasHocon.Count) línea(s) activa(s); el bloque solo lleva comentarios VERIFICAR (EVAL-PIN-12, -14). Primera: '$($activasHocon[0].Trim())'"
            } else {
                Write-Host "PASS - modo Colombia: 0 líneas activas, ningún reusePin ni clave compartida escrita (EVAL-PIN-12, -14)" -ForegroundColor Green
            }
            # EVAL-PIN-13 — matcher o lista de referencia nunca como valor activo
            Test-Contains -Content $hoconActivo -Pattern '\b(ISIdentityMatcher|RIIdentityMatcher)\b|(?<![-\w])identifying-attributes\.json' `
                -Description "modo Colombia: ni ISIdentityMatcher/RIIdentityMatcher ni identifying-attributes.json como valor activo (EVAL-PIN-13)" `
                -ShouldNotMatch
            # HF-65 — la lista VERIFICAR nombra lo que lee el motor (misma lista que VERIFICAR_COLOMBIA del .py)
            $verificarColombia = @(
                "oxygen.stage.pinning.paths.identifyingAttributesFile",
                "oxygen.stage.pinning.identityMatcher",
                "oxygen.stage.pinning.reusePin",
                "oxygen.stage.pinning.paths.domainDictionaryFile",
                "oxygen.stage.pinning.nearNeighborBlockSize",
                "oxygen.stage.pinning.pinningVleRange",
                "oxygen.stage.pinning.groupSSR",
                "oxygen.stage.pinning.paths.pinnedReferenceDir",
                "oxygen.stage.curation.paths.curatedDir",
                "integración estática con HBase",
                "clusterSplitter",
                "INPUT_REF_DIR",
                "*-common-application.conf"
            )
            $verificar = ($lineasHocon | Where-Object { (& $esComentario $_) -and $_ -match 'VERIFICAR' }) -join "`n"
            foreach ($clave in $verificarColombia) {
                Test-Contains -Content $verificar -Pattern ([regex]::Escape($clave) + '(?![A-Za-z0-9_])') `
                    -Description "modo Colombia: 'VERIFICAR $clave' está en la lista (HF-65)"
            }
        }
    }
}

# ─────────────────────────────────────────────────────────────
# 4. Modo Colombia — no se generan matcher ni lista (EVAL-PIN-14)
# ─────────────────────────────────────────────────────────────
if ($Modo -eq "colombia" -and $PropuestaDir) {
    $checked++
    $prohibidos = @(Get-ChildItem -Path $PropuestaDir -Recurse -File |
        Where-Object { $_.Name -like "*IdentityMatcher.scala" -or $_.Name -eq "identifying-attributes.json" })
    if ($prohibidos.Count -gt 0) {
        $failures += "FAIL - modo Colombia: la propuesta trae $($prohibidos.Name -join ', '); se reutilizan el matcher y la lista compartidos (EVAL-PIN-14)"
    } else {
        Write-Host "PASS - modo Colombia: la propuesta no trae IdentityMatcher.scala ni identifying-attributes.json (EVAL-PIN-14)" -ForegroundColor Green
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
    Write-Host "(EVAL-PIN-02, 03, 09, 10, 11; de EVAL-PIN-12..14, la explicación y el escalamiento N4) ni la" -ForegroundColor Yellow
    Write-Host "corrección de negocio de fondo. Verificar esos manualmente" -ForegroundColor Yellow
    Write-Host "contra Diseño/casos-prueba-skill-pinning.md." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "`nTodas las verificaciones mecánicas aplicables pasaron." -ForegroundColor Green
    Write-Host "Recordatorio: esto NO certifica el componente 3 completo -- faltan los casos que" -ForegroundColor Yellow
    Write-Host "dependen de juicio sobre la respuesta de texto del skill. Ver Diseño/casos-prueba-skill-pinning.md." -ForegroundColor Yellow
    exit 0
}
