Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Path $PSScriptRoot -Parent
Set-Location -Path $projectRoot

Write-Host "[ML_STUDIO_AI_SERVER] projectRoot=$projectRoot"
Write-Host "[ML_STUDIO_AI_SERVER] starting: python -m uvicorn main:app --host 0.0.0.0 --port 8001 --app-dir $projectRoot"

python -m uvicorn main:app --host 0.0.0.0 --port 8001 --app-dir $projectRoot
