$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $root "ops\docker-compose.pg.yml"

Write-Host "Stopping PostgreSQL container..."
docker compose -f $composeFile down | Out-Host
Write-Host "PostgreSQL stopped."
