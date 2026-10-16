# Create a repeatable local sample for the variable-center flow editor.
param([string]$ApiBase = 'http://localhost:8081')
$ErrorActionPreference = 'Stop'

function Send-Json([string]$Method, [string]$Path, [object]$Payload) {
    $json = $Payload | ConvertTo-Json -Depth 20 -Compress
    $body = [Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Method $Method -Uri "$ApiBase$Path" -ContentType 'application/json; charset=utf-8' -Body $body
}

function New-Node([string]$Id, [string]$Kind, $Component, $Policy, $Config) {
    return @{ id = $Id; kind = $Kind; componentKey = $Component; failurePolicy = $Policy; config = $Config }
}

function New-Edge([string]$Id, [string]$Source, [string]$Target, $Branch) {
    return @{ id = $Id; source = $Source; target = $Target; branch = $Branch }
}

$null = Send-Json 'POST' '/api/demo/checkout-offer/run-fixture' @{ experimentId = 'variableReview' }
$locatorCode = 'experimentId'
try { $locator = Invoke-RestMethod -Uri "$ApiBase/api/variables/$locatorCode" }
catch {
    $locator = Send-Json 'POST' '/api/variables' @{
        variableCode = $locatorCode; variableName = 'Experiment ID'; valueType = 'STRING'
        sourceType = 'REQUEST_INPUT'; parameterNames = @(); categoryCode = 'CAMPAIGN'
    }
}
$variableCode = 'lowGroupUsersUnified'
try { $variable = Invoke-RestMethod -Uri "$ApiBase/api/variables/$variableCode" }
catch {
    $variable = Send-Json 'POST' '/api/variables' @{
        variableCode = $variableCode; variableName = 'Low group users'; valueType = 'DECIMAL'
        sourceType = 'HTTP_GET'; url = "$ApiBase/api/demo/checkout-offer/report"
        timeoutMs = 1000; parameterNames = @('experimentId')
        dependencyVersionIds = @{ experimentId = $locator.data.id }; resultPath = 'data.groups.LOW.users'
        categoryCode = 'CAMPAIGN'
    }
}
$variableId = $variable.data.id
$flowCode = 'variableCanvasUnifiedDemo'
try { $null = Invoke-RestMethod -Uri "$ApiBase/api/flows/$flowCode" }
catch { $null = Send-Json 'POST' '/api/flows' @{ flowCode = $flowCode; flowName = 'Variable center sample' } }

$graph = @{
    schemaVersion = 1
    requiredInputs = @('experimentId')
    nodes = @(
        (New-Node 'start' 'START' $null $null @{})
        (New-Node 'getUsers' 'COMPONENT' 'get-variable' 'FAIL_FAST' @{
            variableCode = $variableCode; versionId = $variableId
        })
        (New-Node 'check' 'CONDITION' 'compare-value' 'FAIL_FAST' @{
            sourceNodeId = 'getUsers'; valueType = 'DECIMAL'; operator = 'GTE'; expected = 10
        })
        (New-Node 'yes' 'RESULT' $null $null @{
            conclusion = 'ENOUGH'; reasonCode = 'SAMPLE_COUNT'; reason = 'Sample size meets threshold'
        })
        (New-Node 'no' 'RESULT' $null $null @{
            conclusion = 'NOT_ENOUGH'; reasonCode = 'SAMPLE_COUNT'; reason = 'Sample size is below threshold'
        })
        (New-Node 'join' 'CONDITION_JOIN' $null $null @{})
        (New-Node 'end' 'END' $null $null @{})
    )
    edges = @(
        (New-Edge 'a' 'start' 'getUsers' $null)
        (New-Edge 'b' 'getUsers' 'check' $null)
        (New-Edge 'c' 'check' 'yes' 'true')
        (New-Edge 'd' 'check' 'no' 'false')
        (New-Edge 'e' 'yes' 'join' $null)
        (New-Edge 'f' 'no' 'join' $null)
        (New-Edge 'g' 'join' 'end' $null)
    )
    layout = @{
        start = @{ x = 350; y = 10 }; getUsers = @{ x = 350; y = 100 }
        check = @{ x = 350; y = 190 }; yes = @{ x = 130; y = 300 }
        no = @{ x = 570; y = 300 }; join = @{ x = 350; y = 410 }
        end = @{ x = 350; y = 500 }
    }
}

$validated = Send-Json 'POST' "/api/flows/$flowCode/validate" $graph
if (-not $validated.data.valid) { throw ($validated.data.issues | ConvertTo-Json -Depth 6) }
try {
    $published = Invoke-RestMethod -Uri "$ApiBase/api/flows/$flowCode/published"
} catch { $published = $null }
if ($null -eq $published) {
    $draft = Send-Json 'PUT' "/api/flows/$flowCode/draft?expectedRevision=0" $graph
    $null = Send-Json 'POST' "/api/flows/$flowCode/publish" @{ expectedRevision = $draft.data.revision }
}

$sceneCode = 'variableCanvasUnifiedScene'
try { $null = Invoke-RestMethod -Uri "$ApiBase/api/scenes/$sceneCode" }
catch { $null = Send-Json 'POST' '/api/scenes' @{
    sceneCode = $sceneCode; sceneName = 'Variable center sample'; description = 'Read a typed variable'
} }
$null = Send-Json 'PUT' "/api/scenes/$sceneCode/entry-flow" @{ flowCode = $flowCode }
$decision = Send-Json 'POST' '/api/decisions/evaluate' @{
    sceneCode = $sceneCode; input = @{ experimentId = 'variableReview' }
}
Write-Output "sampleDecision=$($decision.data.status) conclusion=$($decision.data.businessResult.conclusion)"
