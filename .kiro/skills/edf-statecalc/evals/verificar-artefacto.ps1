<#
.SYNOPSIS
    Verificación mecánica de artefactos generados por Skill-ConfigGen-StateCalculation.

.DESCRIPCION
    Script de apoyo para el componente 3 de la regla de 3 componentes
    (04-reglas-analisis.md §8, ámbito A) y, con -Comparar, para el componente 2
    (prompt reproducible). NO sustituye la revisión humana ni la lectura de la
    respuesta de texto del skill: los 10 casos (EVAL-SC-01, 02, 03, 04, 05, 07,
    08, 10, 11 y 13) exigen juicio sobre lo que el skill DICE, no solo sobre el
    contenido de los archivos (clase C = verificación automática + humana).

    Este script solo automatiza comprobaciones mecánicas sobre los dos JSON que
    el skill puede producir (statecalc-foundation-local.json y
    statecalc-calculation-local.json). No ejecuta Spark, no ejecuta el motor
    real, no escribe nada fuera del directorio de trabajo y no tiene
    dependencias externas (Windows PowerShell 5.1 o superior).

    Comprobaciones (IDs usados en los mensajes):
      V00-V02  archivo existe / JSON válido (sin comentarios //) / sin {{PLACEHOLDER}}
      F01-F07  Foundation: raíz, tipo de campo, CUSTOM/UNKNOWN prohibidos, campos base
               inyectados, dataType, CALCULATED con value, nombres duplicados
      C01-C09  Calculation: raíz, tipo de sección, grouping anidado prohibido,
               function en MAYÚSCULAS exactas, aggregates coherentes, referencias
               existentes en Foundation, where solo autorizado, contains solo a
               secciones declaradas antes
      G01-G02  sin handler/clazz/class en ningún nivel; sin claves de calidad ni de
               prioridad de fuentes XPM
      P**      perfil específico del caso (-Caso): P01 (fields vacío), P02 (groupBy plano),
               P03 (SUM exacto sobre balance), P04 (camino feliz 2/25; también se aplica a
               EVAL-SC-13), P05 (scoreRiesgo/segmento no sustituidos), P07 (solo los 2
               campos de nivel A), P10 (telefono ausente)

.PARAMETER FoundationPath
    Ruta a statecalc-foundation-local.json generado por el skill.

.PARAMETER CalculationPath
    Ruta a statecalc-calculation-local.json generado por el skill.

.PARAMETER Caso
    GENERAL (por defecto) o un ID EVAL-SC-xx. Aplica el perfil del caso encima de
    las comprobaciones generales. IDs vigentes: ver Diseño/casos-prueba-skill-statecalc.md §4.

.PARAMETER PermitirWhere
    Autoriza la presencia de "where" en secciones (solo cuando el usuario confirmó
    el predicado exacto en la conversación). Por defecto un "where" es un FAIL.

.PARAMETER SinArtefactos
    Declara que el skill no entregó ningún archivo. Es válido solo para los casos
    cuyo resultado correcto puede ser "no generar nada" (01, 05, 08, 10, 11). No es
    válido en 02, 03, 04, 07 y 13, cuyo resultado correcto incluye los JSON generados.

.PARAMETER Comparar
    Dos rutas (directorios que contengan los dos JSON canónicos) a comparar de forma
    estructural: fields[] y secciones, con los valores por defecto normalizados
    (type=DERIVED, dataType=STRING, nullable=true, enabled=true, transient=false,
    return=KEY_VALUE). Diferencias solo en "description" son WARN, no FAIL.
    Sirve al componente 2 (EVAL-SC-04: dos corridas, mismos 2 campos).

.EXAMPLE
    # Camino feliz SISBEN (EVAL-SC-04)
    .\verificar-artefacto.ps1 -FoundationPath .\statecalc-foundation-local.json `
        -CalculationPath .\statecalc-calculation-local.json -Caso EVAL-SC-04

.EXAMPLE
    # El skill se negó a generar archivos (EVAL-SC-05)
    .\verificar-artefacto.ps1 -Caso EVAL-SC-05 -SinArtefactos

.EXAMPLE
    # Reproducibilidad: dos corridas del mismo insumo
    .\verificar-artefacto.ps1 -Comparar .\corrida1, .\corrida2
    # Invocado con "powershell -File": pasar las dos rutas en un solo token -> -Comparar ".\corrida1,.\corrida2"

.NOTAS
    - Exit code 0 si todas las verificaciones aplicables pasan; 1 si alguna falla
      o si hay error de uso.
    - Un PASS de este script NO certifica el componente 3 completo (ver resumen final).
    - No ejecuta, despliega ni transcribe nada a Z2 (01-restricciones-principios.md §2).
    - Fixtures de prueba del propio verificador: evals\fixtures\golden y evals\fixtures\defectuosos.
#>

[CmdletBinding()]
param(
    [string]$FoundationPath,
    [string]$CalculationPath,
    [ValidateSet('GENERAL', 'EVAL-SC-01', 'EVAL-SC-02', 'EVAL-SC-03', 'EVAL-SC-04', 'EVAL-SC-05',
                 'EVAL-SC-07', 'EVAL-SC-08', 'EVAL-SC-10', 'EVAL-SC-11', 'EVAL-SC-13')]
    [string]$Caso = 'GENERAL',
    [switch]$PermitirWhere,
    [switch]$SinArtefactos,
    [string[]]$Comparar
)

$ErrorActionPreference = 'Stop'

# ─────────────────────────────────────────────────────────────
# Vocabulario verificado en las referencias del skill
# ─────────────────────────────────────────────────────────────
$camposBase       = @('dataSource', 'subscriber', 'subscriberRelation', 'asOfPeriod', 'pin', 'parentEventIds')
$tiposCampoOk     = @('DERIVED', 'CALCULATED')
$tiposProhibidos  = @('CUSTOM', 'CUSTOM_WITH_HISTORY', 'CUSTOM_ON_SECTION', 'UNKNOWN')
$tiposSeccionOk   = @('AGGREGATES', 'MOST_RECENT_VALUE', 'DISTINCT', 'ALL')
$funcionesOk      = @('SUM', 'MIN', 'MAX', 'COUNT', 'AVERAGE')
$dataTypesOk      = @('STRING', 'INTEGER', 'LONG', 'DOUBLE', 'BOOLEAN')
$clavesHandler    = @('handler', 'clazz', 'class')
$patronProhibido  = '(?i)quality|threshold|completeness|validity|uniqueness|bestName|bestIdentifications|bestPersonalInformation'

# ─────────────────────────────────────────────────────────────
# Perfiles por caso: clase de verificación (A auto / B texto / C ambos),
# si "no entregar archivos" es un resultado válido, y recordatorio humano.
# ─────────────────────────────────────────────────────────────
$perfiles = @{
    'GENERAL'    = @{ Clase = 'A'; SinArt = $false; Recordar = 'Solo comprobaciones generales; no hay perfil de caso.' }
    'EVAL-SC-01' = @{ Clase = 'C'; SinArt = $true;  Recordar = 'Revisar en el texto: (1) no hay nombre Foundation inferido desde el nombre XPM; (2) los 3 campos figuran como pendientes con el formato de nota; (3) la falta citada es la cadena de mapeo completa.' }
    'EVAL-SC-02' = @{ Clase = 'C'; SinArt = $false; Recordar = 'Revisar en el texto: (1) explica que el DTO real solo declara groupBy plano; (2) NO ofrece la forma anidada como alternativa ni pregunta cual usar.' }
    'EVAL-SC-03' = @{ Clase = 'C'; SinArt = $false; Recordar = 'Revisar en el texto: (1) avisa que corrigio el casing; (2) explica la diferencia type (case-insensitive) vs function (coincidencia exacta).' }
    'EVAL-SC-04' = @{ Clase = 'C'; SinArt = $false; Recordar = 'Revisar en el texto: (1) nombra los 23 campos pendientes; (2) usa el formato exacto "Pendiente de confirmar: <campo> - <que falta>. Fuente consultada: <cita o ninguna>"; (3) declara el nombre de la seccion como propuesta [P]; (4) pendiente de revision humana.' }
    'EVAL-SC-05' = @{ Clase = 'C'; SinArt = $true;  Recordar = 'Revisar en el texto: (1) explica que CUSTOM/CUSTOM_WITH_HISTORY/CUSTOM_ON_SECTION requieren una clase Scala que el skill no produce; (2) explica que UNKNOWN esta deprecado y falla en runtime; (3) propone omitir el campo, no un sustituto.' }
    'EVAL-SC-07' = @{ Clase = 'C'; SinArt = $false; Recordar = 'Revisar en el texto: explica que el motor inyecta dataSource/subscriber/subscriberRelation/asOfPeriod/pin/parentEventIds y que declararlos es error de generacion.' }
    'EVAL-SC-08' = @{ Clase = 'C'; SinArt = $true;  Recordar = 'Revisar en el texto: (1) la prioridad de fuentes XPM / bestName es frontera Hydration/XPM, fuera de alcance; (2) no existe mecanismo de quality threshold verificado en esta etapa.' }
    'EVAL-SC-10' = @{ Clase = 'C'; SinArt = $true;  Recordar = 'Revisar en el texto: explica que todo campo de Calculation debe existir antes en Foundation y que "telefono" es nivel B.' }
    'EVAL-SC-11' = @{ Clase = 'C'; SinArt = $true;  Recordar = 'Revisar en el texto: no inventa el predicado; pide el predicado exacto; no referencia secciones inexistentes con contains.' }
    'EVAL-SC-13' = @{ Clase = 'C'; SinArt = $false; Recordar = 'Revisar en el texto: la entrega se declara propuesta pendiente de revision humana en el workspace (Z1), sin afirmar despliegue ni transcripcion a un repositorio protegido (Z2) y sin sugerir comandos de transcripcion/despliegue.' }
}

$script:fallos = New-Object System.Collections.ArrayList
$script:avisos = New-Object System.Collections.ArrayList
$script:verificadas = 0

function Add-Pass { param([string]$Msg) $script:verificadas++; Write-Host "PASS - $Msg" -ForegroundColor Green }
function Add-Fail { param([string]$Id, [string]$Msg) $script:verificadas++; $t = "FAIL [$Id] - $Msg"; [void]$script:fallos.Add($t); Write-Host $t -ForegroundColor Red }
function Add-Warn { param([string]$Msg) $t = "WARN - $Msg"; [void]$script:avisos.Add($t); Write-Host $t -ForegroundColor Yellow }

function Get-Prop {
    param($Obj, [string]$Nombre)
    if ($null -eq $Obj) { return $null }
    $p = $Obj.PSObject.Properties[$Nombre]
    if ($null -eq $p) { return $null }
    return $p.Value
}

function Has-Prop {
    param($Obj, [string]$Nombre)
    if ($null -eq $Obj) { return $false }
    return ($null -ne $Obj.PSObject.Properties[$Nombre])
}

function Get-Lista {
    param($Obj, [string]$Nombre)
    $v = Get-Prop $Obj $Nombre
    if ($null -eq $v) { return @() }
    return @($v)
}

function Igual-Lista {
    param($A, $B)
    $A = @($A); $B = @($B)
    if ($A.Count -ne $B.Count) { return $false }
    for ($i = 0; $i -lt $A.Count; $i++) {
        if ([string]$A[$i] -cne [string]$B[$i]) { return $false }
    }
    return $true
}

function Read-Artefacto {
    param([string]$Ruta, [string]$Etiqueta)
    if (-not (Test-Path -LiteralPath $Ruta)) {
        Add-Fail 'V00' "$Etiqueta no existe: $Ruta"
        return $null
    }
    $abs = (Resolve-Path -LiteralPath $Ruta).ProviderPath
    $raw = [System.IO.File]::ReadAllText($abs, [System.Text.Encoding]::UTF8)
    if ($raw -match '\{\{[A-Za-z_0-9]+\}\}') {
        Add-Fail 'V02' "$Etiqueta conserva placeholders {{...}} sin resolver"
    } else {
        Add-Pass "$Etiqueta no conserva placeholders {{...}}"
    }
    try {
        $obj = ConvertFrom-Json -InputObject $raw -ErrorAction Stop
        Add-Pass "$Etiqueta es JSON válido"
        return $obj
    } catch {
        Add-Fail 'V01' "$Etiqueta no parsea como JSON (¿comentarios // residuales de la plantilla?): $($_.Exception.Message)"
        return $null
    }
}

# Aplana todas las claves y valores string de un árbol JSON (para G01/G02).
function Expandir-Nodo {
    param($Nodo, [string]$Ruta, [string]$ClaveCtx, $Lista)
    if ($null -eq $Nodo) { return }
    if ($Nodo -is [System.Management.Automation.PSCustomObject]) {
        foreach ($p in $Nodo.PSObject.Properties) {
            $r = "$Ruta.$($p.Name)"
            [void]$Lista.Add([pscustomobject]@{ Ruta = $r; Clave = $p.Name; Valor = $p.Value })
            Expandir-Nodo $p.Value $r $p.Name $Lista
        }
    } elseif (($Nodo -is [System.Collections.IEnumerable]) -and ($Nodo -isnot [string])) {
        $i = 0
        foreach ($e in $Nodo) {
            if ($e -is [string]) {
                [void]$Lista.Add([pscustomobject]@{ Ruta = "$Ruta[$i]"; Clave = $ClaveCtx; Valor = $e })
            }
            Expandir-Nodo $e "$Ruta[$i]" $ClaveCtx $Lista
            $i++
        }
    }
}

# ─────────────────────────────────────────────────────────────
# Foundation
# ─────────────────────────────────────────────────────────────
function Test-Foundation {
    param($F)
    $nombres = New-Object System.Collections.ArrayList
    if (-not (Has-Prop $F 'fields')) { Add-Fail 'F01' "Foundation no declara 'fields'"; return }
    if ([string]::IsNullOrWhiteSpace([string](Get-Prop $F 'domain'))) { Add-Fail 'F01' "Foundation no declara 'domain'" } else { Add-Pass "Foundation declara 'domain' y 'fields'" }

    $campos = @(Get-Lista $F 'fields')
    $malTipo = 0; $malBase = 0; $malDt = 0; $malCalc = 0; $malDup = 0
    foreach ($c in $campos) {
        $n = [string](Get-Prop $c 'name')
        if ([string]::IsNullOrWhiteSpace($n)) { Add-Fail 'F01' "un campo de fields[] no tiene 'name'"; continue }
        if ($nombres -contains $n) { Add-Fail 'F07' "nombre de campo duplicado en fields[]: '$n'"; $malDup++ }
        [void]$nombres.Add($n)

        $tipo = Get-Prop $c 'type'
        $tipoU = 'DERIVED'
        if ($null -ne $tipo) { $tipoU = ([string]$tipo).ToUpperInvariant() }
        if ($tiposProhibidos -contains $tipoU) {
            if ($tipoU -eq 'UNKNOWN') {
                Add-Fail 'F03' "campo '$n' usa type UNKNOWN (deprecado, siempre falla en runtime; la alternativa es omitir el campo)"
            } else {
                Add-Fail 'F03' "campo '$n' usa type $tipoU (requiere una clase Scala que el skill no produce)"
            }
            $malTipo++
        } elseif ($tiposCampoOk -notcontains $tipoU) {
            Add-Fail 'F02' "campo '$n' usa type '$tipo' fuera del vocabulario generable {DERIVED, CALCULATED}"
            $malTipo++
        } elseif (($null -ne $tipo) -and ([string]$tipo -cne $tipoU)) {
            Add-Warn "campo '$n': type '$tipo' no está en mayúsculas (convención FieldType: $tipoU)"
        }

        if ($camposBase -contains $n -or ($camposBase | Where-Object { $_ -ieq $n })) {
            Add-Fail 'F04' "campo base inyectado por el motor declarado en fields[]: '$n' (error de generación)"
            $malBase++
        }

        $dt = Get-Prop $c 'dataType'
        if (($null -ne $dt) -and ($dataTypesOk -cnotcontains [string]$dt)) {
            Add-Fail 'F05' "campo '$n': dataType '$dt' fuera de {STRING, INTEGER, LONG, DOUBLE, BOOLEAN}"
            $malDt++
        }

        $val = Get-Prop $c 'value'
        if ($tipoU -eq 'CALCULATED') {
            if ([string]::IsNullOrWhiteSpace([string]$val)) {
                Add-Fail 'F06' "campo '$n' es CALCULATED pero no trae 'value' (script Nashorn)"
                $malCalc++
            }
        } elseif ($null -ne $val) {
            Add-Warn "campo '$n': trae 'value' sin type CALCULATED (el motor lo ignora)"
        }
    }
    if ($malTipo -eq 0) { Add-Pass "F02/F03: todos los campos son DERIVED/CALCULATED (sin CUSTOM, CUSTOM_WITH_HISTORY ni UNKNOWN)" }
    if ($malBase -eq 0) { Add-Pass "F04: ningún campo base inyectado (dataSource/subscriber/subscriberRelation/asOfPeriod/pin/parentEventIds) está declarado" }
    if ($malDt -eq 0) { Add-Pass "F05: dataType dentro del vocabulario" }
    if ($malCalc -eq 0) { Add-Pass "F06: todo CALCULATED trae value" }
    if ($malDup -eq 0) { Add-Pass "F07: sin nombres duplicados" }
}

# ─────────────────────────────────────────────────────────────
# Calculation
# ─────────────────────────────────────────────────────────────
function Test-Calculation {
    param($C, $F, [bool]$TieneFoundation)
    if (-not (Has-Prop $C 'calculations')) { Add-Fail 'C01' "Calculation no declara 'calculations'"; return }
    if ([string]::IsNullOrWhiteSpace([string](Get-Prop $C 'domain'))) { Add-Fail 'C01' "Calculation no declara 'domain'" } else { Add-Pass "Calculation declara 'domain' y 'calculations'" }

    $nombresFoundation = @()
    if ($TieneFoundation) { $nombresFoundation = @(@(Get-Lista $F 'fields') | ForEach-Object { [string](Get-Prop $_ 'name') }) }

    $secciones = @(Get-Lista $C 'calculations')
    $declaradas = New-Object System.Collections.ArrayList
    $nTipo = 0; $nFun = 0; $nRef = 0; $nWhere = 0; $nCont = 0; $nGroup = 0; $nAgg = 0
    $avisoFoundation = $false

    for ($i = 0; $i -lt $secciones.Count; $i++) {
        $s = $secciones[$i]
        $sn = [string](Get-Prop $s 'name')
        if ([string]::IsNullOrWhiteSpace($sn)) { Add-Fail 'C01' "la sección #$i no tiene 'name'"; $sn = "#$i" }
        elseif ($declaradas -contains $sn) { Add-Fail 'C01' "nombre de sección duplicado: '$sn'" }

        # C02 grouping anidado
        if (Has-Prop $s 'grouping') {
            Add-Fail 'C02' "sección '$sn' usa 'grouping' anidado; el skill solo genera la clave plana 'groupBy' (regla dura, esquema-foundation-calculation.md §2.1)"
            $nGroup++
        }

        # C03 tipo
        $tipo = [string](Get-Prop $s 'type')
        $tipoU = $tipo.ToUpperInvariant()
        if ($tiposProhibidos -contains $tipoU) {
            Add-Fail 'C03' "sección '$sn' usa type $tipoU (requiere una clase Scala que el skill no produce)"
            $nTipo++
        } elseif ($tiposSeccionOk -notcontains $tipoU) {
            Add-Fail 'C03' "sección '$sn' usa type '$tipo' fuera del vocabulario generable {AGGREGATES, MOST_RECENT_VALUE, DISTINCT, ALL}"
            $nTipo++
        } elseif ($tipo -cne $tipoU) {
            Add-Warn "sección '$sn': type '$tipo' no está en mayúsculas (el motor lo resuelve case-insensitive; convención: $tipoU)"
        }

        # C04/C05 aggregates y function
        $aggs = @(Get-Lista $s 'aggregates')
        if ($tipoU -eq 'AGGREGATES') {
            if ($aggs.Count -eq 0) { Add-Fail 'C04' "sección '$sn' es AGGREGATES pero no trae aggregates[]"; $nAgg++ }
        } elseif ($aggs.Count -gt 0) {
            Add-Fail 'C04' "sección '$sn' trae aggregates[] sin ser type AGGREGATES"
            $nAgg++
        }
        foreach ($a in $aggs) {
            $fn = Get-Prop $a 'function'
            if ($null -eq $fn) { Add-Fail 'C05' "sección '$sn': un aggregate no declara 'function'"; $nFun++; continue }
            if ($funcionesOk -ccontains [string]$fn) { continue }
            if ($funcionesOk -contains [string]$fn) {
                Add-Fail 'C05' "sección '$sn': function '$fn' no está en MAYÚSCULAS exactas; el motor real exige coincidencia exacta (usar '$(([string]$fn).ToUpperInvariant())')"
            } else {
                Add-Fail 'C05' "sección '$sn': function '$fn' no pertenece a {SUM, MIN, MAX, COUNT, AVERAGE}"
            }
            $nFun++
        }

        # C06 referencias a campos
        $refs = New-Object System.Collections.ArrayList
        foreach ($x in (Get-Lista $s 'select')) { [void]$refs.Add([pscustomobject]@{ Campo = [string]$x; Donde = 'select' }) }
        foreach ($x in (Get-Lista $s 'groupBy')) { [void]$refs.Add([pscustomobject]@{ Campo = [string]$x; Donde = 'groupBy' }) }
        foreach ($a in $aggs) {
            $fld = Get-Prop $a 'field'
            if ($null -ne $fld) { [void]$refs.Add([pscustomobject]@{ Campo = [string]$fld; Donde = 'aggregates[].field' }) }
        }
        if ($TieneFoundation) {
            foreach ($r in $refs) {
                if ($nombresFoundation -ccontains $r.Campo) { continue }
                if ($camposBase | Where-Object { $_ -ceq $r.Campo }) {
                    Add-Warn "sección '$sn': $($r.Donde) referencia el campo base inyectado '$($r.Campo)' sin declararlo en Foundation (¿válido? [A:sin-clasificar], revisión humana)"
                    continue
                }
                Add-Fail 'C06' "sección '$sn': $($r.Donde) referencia '$($r.Campo)', que no existe en fields[] de Foundation"
                $nRef++
            }
        } elseif ($refs.Count -gt 0 -and -not $avisoFoundation) {
            Add-Warn "C06 omitida: no se suministró Foundation, no se puede comprobar que select/groupBy/aggregates[].field existan en fields[]"
            $avisoFoundation = $true
        }

        # C07 where
        if (Has-Prop $s 'where') {
            if ($PermitirWhere) {
                Add-Warn "sección '$sn': trae 'where'; autorizado por -PermitirWhere. Verificar a mano que el predicado exacto fue confirmado por el usuario"
            } else {
                Add-Fail 'C07' "sección '$sn': trae 'where' sin autorización (el skill no agrega where sin predicado confirmado; usar -PermitirWhere solo si el usuario lo confirmó)"
                $nWhere++
            }
        }

        # C08 contains solo a secciones declaradas antes
        foreach ($ct in (Get-Lista $s 'contains')) {
            $ref = [string](Get-Prop $ct 'section')
            if ([string]::IsNullOrWhiteSpace($ref)) { Add-Fail 'C08' "sección '$sn': un contains no declara 'section'"; $nCont++; continue }
            if ($declaradas -ccontains $ref) { continue }
            $despues = $false
            for ($j = $i + 1; $j -lt $secciones.Count; $j++) { if ([string](Get-Prop $secciones[$j] 'name') -ceq $ref) { $despues = $true } }
            if ($despues) {
                Add-Fail 'C08' "sección '$sn': contains apunta a '$ref', que se declara DESPUÉS en el array (debe declararse antes)"
            } else {
                Add-Fail 'C08' "sección '$sn': contains apunta a '$ref', que no existe en calculations[]"
            }
            $nCont++
        }
        [void]$declaradas.Add($sn)
    }
    if ($nGroup -eq 0) { Add-Pass "C02: ninguna sección usa 'grouping' anidado" }
    if ($nTipo -eq 0) { Add-Pass "C03: todas las secciones usan un type generable (sin CUSTOM_ON_SECTION)" }
    if ($nAgg -eq 0) { Add-Pass "C04: aggregates[] coherente con type AGGREGATES" }
    if ($nFun -eq 0) { Add-Pass "C05: function de aggregates[] en MAYÚSCULAS exactas dentro de {SUM, MIN, MAX, COUNT, AVERAGE}" }
    if ($TieneFoundation -and $nRef -eq 0) { Add-Pass "C06: todo campo de select/groupBy/aggregates[].field existe en Foundation" }
    if ($nWhere -eq 0 -and -not $PermitirWhere) { Add-Pass "C07: ninguna sección trae 'where'" }
    if ($nCont -eq 0) { Add-Pass "C08: todo contains apunta a una sección declarada antes" }
}

# ─────────────────────────────────────────────────────────────
# Generales sobre ambos árboles (G01/G02)
# ─────────────────────────────────────────────────────────────
function Test-Generales {
    param($Arbol, [string]$Etiqueta)
    $lista = New-Object System.Collections.ArrayList
    Expandir-Nodo $Arbol '$' '' $lista
    $h = @($lista | Where-Object { $clavesHandler -contains $_.Clave })
    if ($h.Count -gt 0) {
        foreach ($x in $h) { Add-Fail 'G01' "$Etiqueta contiene la clave '$($x.Clave)' en $($x.Ruta) (handler/clazz requiere clase Scala que el skill no produce)" }
    } else {
        Add-Pass "G01: $Etiqueta no contiene claves handler/clazz/class"
    }
    $malos = @($lista | Where-Object {
        ($_.Clave -match $patronProhibido) -or
        (($_.Valor -is [string]) -and (@('description', 'value', 'where') -notcontains $_.Clave) -and ($_.Valor -match $patronProhibido))
    })
    if ($malos.Count -gt 0) {
        foreach ($x in $malos) { Add-Fail 'G02' "$Etiqueta contiene semántica prohibida (calidad de datos o prioridad de fuentes XPM) en $($x.Ruta)" }
    } else {
        Add-Pass "G02: $Etiqueta no contiene claves de quality/threshold/completeness/validity/uniqueness ni de prioridad XPM (bestName/bestIdentifications)"
    }
}

# ─────────────────────────────────────────────────────────────
# Perfiles de caso
# ─────────────────────────────────────────────────────────────
function Test-PerfilCaso {
    param([string]$Id, $F, $C)

    # Perfil de camino feliz 2/25: EVAL-SC-04 y EVAL-SC-13 (ambos piden el paquete completo SISBEN tal cual).
    if ($Id -eq 'EVAL-SC-04' -or $Id -eq 'EVAL-SC-13') {
        if ($null -eq $F -or $null -eq $C) { Add-Fail 'P04' "$Id exige ambos artefactos (Foundation y Calculation)"; return }
        $campos = @(Get-Lista $F 'fields')
        if ($campos.Count -ne 2) {
            Add-Fail 'P04' "se esperaban exactamente 2 campos activos en Foundation y hay $($campos.Count): $((@($campos | ForEach-Object { Get-Prop $_ 'name' })) -join ', ')"
        } else {
            Add-Pass "P04: Foundation tiene exactamente 2 campos activos"
        }
        $esperados = @(
            @{ Name = 'personIdType';   DataType = 'INTEGER'; Value = 'return field.standardizedIdType' },
            @{ Name = 'personIdNumber'; DataType = 'STRING';  Value = 'return field.standardizedIdNumber' }
        )
        foreach ($e in $esperados) {
            $m = @($campos | Where-Object { [string](Get-Prop $_ 'name') -ceq $e.Name })
            if ($m.Count -ne 1) { Add-Fail 'P04' "falta el campo '$($e.Name)' (o está repetido)"; continue }
            $m = $m[0]
            $tipo = [string](Get-Prop $m 'type')
            $dt = Get-Prop $m 'dataType'
            if ($null -eq $dt) { $dt = 'STRING' }
            $ok = $true
            if ($tipo -cne 'CALCULATED') { Add-Fail 'P04' "'$($e.Name)': type '$tipo' (se esperaba CALCULATED)"; $ok = $false }
            if ([string]$dt -cne $e.DataType) { Add-Fail 'P04' "'$($e.Name)': dataType efectivo '$dt' (se esperaba $($e.DataType); STRING es el default y puede omitirse)"; $ok = $false }
            if ([string](Get-Prop $m 'value') -cne $e.Value) { Add-Fail 'P04' "'$($e.Name)': value '$(Get-Prop $m 'value')' (se esperaba exactamente '$($e.Value)')"; $ok = $false }
            if ($ok) { Add-Pass "P04: '$($e.Name)' es CALCULATED, $($e.DataType) y value exacto" }
        }
        $secs = @(Get-Lista $C 'calculations')
        if ($secs.Count -ne 1) {
            Add-Fail 'P04' "se esperaba exactamente 1 sección en Calculation y hay $($secs.Count)"
        } else {
            $s = $secs[0]
            $par = @('personIdType', 'personIdNumber')
            if ([string](Get-Prop $s 'type') -cne 'MOST_RECENT_VALUE') { Add-Fail 'P04' "la sección no es type MOST_RECENT_VALUE (es '$(Get-Prop $s 'type')')" } else { Add-Pass "P04: la sección es MOST_RECENT_VALUE" }
            if (-not (Igual-Lista (Get-Lista $s 'select') $par)) { Add-Fail 'P04' "select != [personIdType, personIdNumber] (es [$((Get-Lista $s 'select') -join ', ')])" } else { Add-Pass "P04: select = [personIdType, personIdNumber]" }
            if (-not (Igual-Lista (Get-Lista $s 'groupBy') $par)) { Add-Fail 'P04' "groupBy plano != [personIdType, personIdNumber] (es [$((Get-Lista $s 'groupBy') -join ', ')])" } else { Add-Pass "P04: groupBy plano = [personIdType, personIdNumber]" }
        }
    }

    if ($Id -eq 'EVAL-SC-02') {
        if ($null -eq $C) { Add-Fail 'P02' "EVAL-SC-02 exige el artefacto Calculation"; return }
        $par = @('personIdType', 'personIdNumber')
        $ok = @(@(Get-Lista $C 'calculations') | Where-Object {
            ([string](Get-Prop $_ 'type')).ToUpperInvariant() -eq 'MOST_RECENT_VALUE' -and (Igual-Lista (Get-Lista $_ 'groupBy') $par)
        })
        if ($ok.Count -ge 1) { Add-Pass "P02: hay una sección MOST_RECENT_VALUE con groupBy plano [personIdType, personIdNumber]" }
        else { Add-Fail 'P02' "no hay ninguna sección MOST_RECENT_VALUE con la clave plana groupBy = [personIdType, personIdNumber]" }
    }

    if ($Id -eq 'EVAL-SC-03') {
        if ($null -eq $C) { Add-Fail 'P03' "EVAL-SC-03 exige el artefacto Calculation"; return }
        $hay = $false
        foreach ($s in @(Get-Lista $C 'calculations')) {
            if (([string](Get-Prop $s 'type')).ToUpperInvariant() -ne 'AGGREGATES') { continue }
            foreach ($a in @(Get-Lista $s 'aggregates')) {
                if (([string](Get-Prop $a 'field') -ceq 'balance') -and ([string](Get-Prop $a 'function') -ceq 'SUM')) { $hay = $true }
            }
        }
        if ($hay) { Add-Pass "P03: hay un aggregate SUM (mayúsculas exactas) sobre 'balance'" }
        else { Add-Fail 'P03' "no hay ningún aggregate con function exactamente 'SUM' sobre el campo 'balance'" }
    }

    if ($Id -eq 'EVAL-SC-01') {
        $campos = @()
        if ($null -ne $F) { $campos = @(Get-Lista $F 'fields') }
        if ($campos.Count -gt 0) {
            Add-Fail 'P01' "se esperaba fields[] vacío (el prompt no aporta cadena de mapeo) y trae $($campos.Count): $((@($campos | ForEach-Object { Get-Prop $_ 'name' })) -join ', ')"
        } else {
            Add-Pass "P01: Foundation no activa ningún campo inferido desde nombres XPM"
        }
        if ($null -ne $C) {
            $n = 0
            foreach ($s in @(Get-Lista $C 'calculations')) { $n += @(Get-Lista $s 'select').Count + @(Get-Lista $s 'groupBy').Count }
            if ($n -gt 0) { Add-Fail 'P01' "Calculation referencia $n campo(s) sin que exista cadena de mapeo confirmada" } else { Add-Pass "P01: Calculation no referencia campos" }
        }
    }

    if ($Id -eq 'EVAL-SC-05') {
        # El prompt pide 'scoreRiesgo' (CUSTOM) y 'segmento' (UNKNOWN / CUSTOM_ON_SECTION): ninguno debe
        # aparecer sustituido por otro tipo inventado, ni en Foundation ni en Calculation.
        $pend = @('scoreRiesgo', 'segmento')
        $malo = $false
        if ($null -ne $F) {
            foreach ($c0 in @(Get-Lista $F 'fields')) {
                $n0 = [string](Get-Prop $c0 'name')
                if ($pend -ccontains $n0) { Add-Fail 'P05' "Foundation declara '$n0': debia quedar como pendiente, sin sustituirlo por otro tipo"; $malo = $true }
            }
        }
        if ($null -ne $C) {
            foreach ($s in @(Get-Lista $C 'calculations')) {
                $r = @(Get-Lista $s 'select') + @(Get-Lista $s 'groupBy') + @(@(Get-Lista $s 'aggregates') | ForEach-Object { Get-Prop $_ 'field' }) + @([string](Get-Prop $s 'name'))
                foreach ($p0 in $pend) {
                    if ($r -ccontains $p0) { Add-Fail 'P05' "la seccion '$(Get-Prop $s 'name')' de Calculation usa '$p0': debia quedar como pendiente"; $malo = $true }
                }
            }
        }
        if (-not $malo) { Add-Pass "P05: 'scoreRiesgo' y 'segmento' no aparecen en los artefactos (no se sustituyeron por otro tipo)" }
    }

    if ($Id -eq 'EVAL-SC-07') {
        # Debe quedar exactamente el contenido de nivel A: ningun nombre fuera de los 2 campos confirmados.
        $permitidos = @('personIdType', 'personIdNumber')
        if ($null -eq $F) { Add-Fail 'P07' "EVAL-SC-07 exige el artefacto Foundation"; return }
        $extra = @(@(Get-Lista $F 'fields') | ForEach-Object { [string](Get-Prop $_ 'name') } | Where-Object { $permitidos -cnotcontains $_ })
        if ($extra.Count -gt 0) { Add-Fail 'P07' "fields[] contiene nombres fuera de los 2 de nivel A: $($extra -join ', ')" }
        else { Add-Pass "P07: fields[] solo contiene campos de nivel A (personIdType, personIdNumber)" }
    }

    if ($Id -eq 'EVAL-SC-10') {
        if ($null -ne $C) {
            $malo = $false
            foreach ($s in @(Get-Lista $C 'calculations')) {
                $r = @(Get-Lista $s 'select') + @(Get-Lista $s 'groupBy') + @(@(Get-Lista $s 'aggregates') | ForEach-Object { Get-Prop $_ 'field' })
                if ($r -ccontains 'telefono') { Add-Fail 'P10' "la sección '$(Get-Prop $s 'name')' referencia 'telefono' (nivel B, sin cadena de mapeo ni campo en Foundation)"; $malo = $true }
            }
            if (-not $malo) { Add-Pass "P10: Calculation no referencia 'telefono'" }
        }
    }
}

# ─────────────────────────────────────────────────────────────
# Modo -Comparar (componente 2: reproducibilidad)
# ─────────────────────────────────────────────────────────────
function Get-Canon {
    param($N)
    if ($null -eq $N) { return 'null' }
    if ($N -is [string]) { return '"' + $N + '"' }
    if ($N -is [bool]) { return $N.ToString().ToLowerInvariant() }
    if ($N -is [System.Collections.IDictionary]) {
        $k = @($N.Keys | Sort-Object { [string]$_ } -CaseSensitive)
        return '{' + ((@($k | ForEach-Object { $_ + ':' + (Get-Canon $N[$_]) })) -join ',') + '}'
    }
    if ($N -is [System.Management.Automation.PSCustomObject]) {
        $k = @($N.PSObject.Properties.Name | Sort-Object -CaseSensitive)
        return '{' + ((@($k | ForEach-Object { $_ + ':' + (Get-Canon $N.PSObject.Properties[$_].Value) })) -join ',') + '}'
    }
    if (($N -is [System.Collections.IEnumerable])) {
        return '[' + ((@($N | ForEach-Object { Get-Canon $_ })) -join ',') + ']'
    }
    return [string]$N
}

function Normalizar-Elemento {
    param($E, [hashtable]$Defaults, [bool]$TipoMayus)
    $h = [ordered]@{}
    foreach ($p in $E.PSObject.Properties) {
        if ($p.Name -eq 'description') { continue }
        $h[$p.Name] = $p.Value
    }
    foreach ($k in $Defaults.Keys) { if (-not $h.Contains($k)) { $h[$k] = $Defaults[$k] } }
    if ($TipoMayus -and $h.Contains('type') -and $h['type'] -is [string]) { $h['type'] = $h['type'].ToUpperInvariant() }
    return $h
}

function Comparar-Corridas {
    param([string]$RutaA, [string]$RutaB)
    $tot = 0
    $dif = New-Object System.Collections.ArrayList
    $pares = @(
        @{ Archivo = 'statecalc-foundation-local.json'; Lista = 'fields';       Defaults = @{ type = 'DERIVED'; dataType = 'STRING'; nullable = $true }; Mayus = $true },
        @{ Archivo = 'statecalc-calculation-local.json'; Lista = 'calculations'; Defaults = @{ enabled = $true; transient = $false; return = 'KEY_VALUE' };  Mayus = $true }
    )
    foreach ($par in $pares) {
        $pa = Join-Path $RutaA $par.Archivo
        $pb = Join-Path $RutaB $par.Archivo
        $oa = Read-Artefacto $pa "[A] $($par.Archivo)"
        $ob = Read-Artefacto $pb "[B] $($par.Archivo)"
        if ($null -eq $oa -or $null -eq $ob) { continue }

        # raíz (excluye la lista principal)
        $ra = [ordered]@{}; foreach ($p in $oa.PSObject.Properties) { if ($p.Name -ne $par.Lista) { $ra[$p.Name] = $p.Value } }
        $rb = [ordered]@{}; foreach ($p in $ob.PSObject.Properties) { if ($p.Name -ne $par.Lista) { $rb[$p.Name] = $p.Value } }
        if ((Get-Canon $ra) -cne (Get-Canon $rb)) {
            Add-Fail 'R01' "$($par.Archivo): la raíz difiere. A=$(Get-Canon $ra) B=$(Get-Canon $rb)"
        } else {
            Add-Pass "$($par.Archivo): raíz idéntica (domain)"
        }

        $la = @(Get-Lista $oa $par.Lista); $lb = @(Get-Lista $ob $par.Lista)
        $ma = [ordered]@{}; foreach ($e in $la) { $ma[[string](Get-Prop $e 'name')] = $e }
        $mb = [ordered]@{}; foreach ($e in $lb) { $mb[[string](Get-Prop $e 'name')] = $e }
        $soloA = @($ma.Keys | Where-Object { -not $mb.Contains($_) })
        $soloB = @($mb.Keys | Where-Object { -not $ma.Contains($_) })
        foreach ($k in $soloA) { Add-Fail 'R02' "$($par.Lista): '$k' solo existe en la corrida A" }
        foreach ($k in $soloB) { Add-Fail 'R02' "$($par.Lista): '$k' solo existe en la corrida B" }
        $comunes = @($ma.Keys | Where-Object { $mb.Contains($_) })
        $difs = 0
        foreach ($k in $comunes) {
            $ca = Get-Canon (Normalizar-Elemento $ma[$k] $par.Defaults $par.Mayus)
            $cb = Get-Canon (Normalizar-Elemento $mb[$k] $par.Defaults $par.Mayus)
            if ($ca -cne $cb) { Add-Fail 'R03' "$($par.Lista): '$k' difiere entre corridas. A=$ca B=$cb"; $difs++ }
            elseif ([string](Get-Prop $ma[$k] 'description') -cne [string](Get-Prop $mb[$k] 'description')) {
                Add-Warn "$($par.Lista): '$k' solo difiere en 'description' (texto libre, no estructural)"
            }
        }
        if ($soloA.Count -eq 0 -and $soloB.Count -eq 0 -and $difs -eq 0) {
            Add-Pass "$($par.Lista): mismos $($comunes.Count) elemento(s) y mismo contenido estructural en ambas corridas"
            if (-not (Igual-Lista @($ma.Keys) @($mb.Keys))) { Add-Warn "$($par.Lista): mismo contenido pero distinto orden de elementos" }
        }
    }
}

# ═════════════════════════════════════════════════════════════
# Ejecución
# ═════════════════════════════════════════════════════════════
Write-Host "=== Verificacion mecanica de artefactos - Skill-ConfigGen-StateCalculation ===" -ForegroundColor Cyan

$perfil = $perfiles[$Caso]

# Con "powershell -File", -Comparar llega como un solo token "rutaA,rutaB": se divide aquí.
if ($Comparar -and $Comparar.Count -eq 1 -and $Comparar[0].Contains(',')) { $Comparar = $Comparar[0].Split(',') }

if ($Comparar -and $Comparar.Count -gt 0) {
    Write-Host "Modo -Comparar (componente 2: reproducibilidad)" -ForegroundColor Cyan
    if ($Comparar.Count -ne 2) {
        Write-Host "Error de uso: -Comparar requiere exactamente 2 rutas (directorios con los dos JSON canonicos)." -ForegroundColor Red
        exit 1
    }
    Comparar-Corridas $Comparar[0] $Comparar[1]
} else {
    Write-Host "Caso: $Caso (clase de verificacion: $($perfil.Clase); A=automatica, B=texto, C=ambas)" -ForegroundColor Cyan
    if ($SinArtefactos) {
        if (-not $perfil.SinArt) {
            Add-Fail 'P00' "$Caso no admite 'sin artefactos': el resultado correcto de este caso incluye los JSON generados"
        } else {
            Add-Pass "$Caso admite que el skill no entregue archivos; nada que verificar mecanicamente"
        }
    } elseif ([string]::IsNullOrWhiteSpace($FoundationPath) -and [string]::IsNullOrWhiteSpace($CalculationPath)) {
        Write-Host "Error de uso: indique -FoundationPath y/o -CalculationPath, -SinArtefactos, o -Comparar." -ForegroundColor Red
        exit 1
    } else {
        $f = $null; $c = $null
        if ($FoundationPath) {
            $f = Read-Artefacto $FoundationPath 'statecalc-foundation-local.json'
            if ($null -ne $f) { Test-Foundation $f; Test-Generales $f 'Foundation' }
        }
        if ($CalculationPath) {
            $c = Read-Artefacto $CalculationPath 'statecalc-calculation-local.json'
            if ($null -ne $c) { Test-Calculation $c $f ($null -ne $f); Test-Generales $c 'Calculation' }
        }
        if ($Caso -ne 'GENERAL') { Test-PerfilCaso $Caso $f $c }
    }
}

Write-Host ""
Write-Host "=== Resumen: $($script:verificadas) verificaciones mecanicas ejecutadas ===" -ForegroundColor Cyan
if ($script:avisos.Count -gt 0) {
    Write-Host "`nADVERTENCIAS ($($script:avisos.Count)):" -ForegroundColor Yellow
    $script:avisos | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}
if (-not ($Comparar -and $Comparar.Count -gt 0)) {
    Write-Host "`nVerificacion humana pendiente ($($perfil.Clase)): $($perfil.Recordar)" -ForegroundColor Yellow
}
if ($script:fallos.Count -gt 0) {
    Write-Host "`nFALLOS ($($script:fallos.Count)):" -ForegroundColor Red
    $script:fallos | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    Write-Host "`nRecordatorio: este script NO cubre la RESPUESTA DE TEXTO del skill. Ver Diseño/casos-prueba-skill-statecalc.md." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "`nTodas las verificaciones mecanicas aplicables pasaron." -ForegroundColor Green
    Write-Host "Recordatorio: esto NO certifica el componente 3 completo; faltan las comprobaciones sobre la respuesta de texto. Ver Diseño/casos-prueba-skill-statecalc.md." -ForegroundColor Yellow
    exit 0
}
