$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $root "ops\docker-compose.pg.yml"
$containerName = "autoproduction-postgres"

Write-Host "Starting PostgreSQL container..."
docker compose -f $composeFile up -d postgres | Out-Host

for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 2
  $status = docker inspect -f "{{.State.Health.Status}}" $containerName 2>$null
  if ($status -eq "healthy") {
    Write-Host "PostgreSQL is healthy."
    exit 0
  }
}

throw "PostgreSQL failed to become healthy in time."
