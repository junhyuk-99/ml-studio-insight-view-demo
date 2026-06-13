param(
    [int]$Port = 8001
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "==== $Title ===="
}

Write-Section "LISTENER"
$connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (-not $connections) {
    Write-Host "No listening process on port $Port."
    exit 1
}

$owningPids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($processId in $owningPids) {
    Write-Host "PID: $processId"
    Get-CimInstance Win32_Process -Filter "ProcessId=$processId" |
        Select-Object ProcessId, Name, ExecutablePath, CommandLine, CreationDate |
        Format-List
}

Write-Section "OPENAPI CHECK"
$openapiUrl = "http://127.0.0.1:$Port/openapi.json"
try {
    $openapi = Invoke-RestMethod -Uri $openapiUrl -Method Get -TimeoutSec 10
    $paths = $openapi.paths.PSObject.Properties.Name | Sort-Object
    $paths | ForEach-Object { Write-Host $_ }

    $required = "/api/model/execute/random-forest"
    if ($paths -contains $required) {
        Write-Host "PASS: required path exists -> $required"
    }
    else {
        Write-Host "FAIL: required path missing -> $required"
        exit 2
    }
}
catch {
    Write-Host "FAIL: unable to call $openapiUrl"
    Write-Host $_.Exception.Message
    exit 3
}

Write-Section "HEALTH CHECK"
$healthUrl = "http://127.0.0.1:$Port/health"
try {
    $health = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 10
    $health | ConvertTo-Json -Depth 10
}
catch {
    Write-Host "WARN: unable to call $healthUrl"
    Write-Host $_.Exception.Message
}
