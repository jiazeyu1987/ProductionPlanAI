param(
  [string]$TestHost = "172.30.30.58",
  [string]$TestUser = "root",
  [int]$SshPort = 22,
  [string]$RemoteDir = "/opt/autoproduction/stack",
  [string]$ComposeProjectName = "autoproduction-test",
  [string]$Version = (Get-Date -Format "yyyyMMdd_HHmmss"),
  [int]$AppHttpPort = 18080,
  [string]$DbName = "autoproduction",
  [string]$DbUsername = "autoproduction",
  [string]$DbPassword = "autoproduction",
  [int]$BackendPort = 5931,
  [string]$SpringProfile = "prod",
  [string]$Timezone = "Asia/Shanghai",
  [string]$ErpUseRealOrders = "false",
  [string]$ErpSqlitePath = "/data/erp/erp_recent_orders.db",
  [string]$CorsAllowedOrigins = "http://localhost:5932,http://127.0.0.1:5932,http://172.30.30.58:18080",
  [switch]$KeepArtifacts
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"
$opsDir = Join-Path $repoRoot "ops"
$composeFile = Join-Path $opsDir "docker-compose.test.yml"

if (-not (Test-Path $composeFile)) {
  throw "Compose file not found: $composeFile"
}

foreach ($cmd in @("docker", "ssh", "scp")) {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
    throw "Required command not found in PATH: $cmd"
  }
}

function Invoke-Checked {
  param(
    [string]$Command,
    [string[]]$Arguments,
    [string]$FailureMessage
  )

  & $Command @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "$FailureMessage (exit code: $LASTEXITCODE)"
  }
}

function Invoke-Remote {
  param([string]$RemoteCommand)
  $sshArgs = @("-p", "$SshPort", "$TestUser@$TestHost", $RemoteCommand)
  Invoke-Checked -Command "ssh" -Arguments $sshArgs -FailureMessage "Remote command failed"
}

$pushedLocations = 0
function Push-ScopedLocation {
  param([string]$Path)
  Push-Location $Path
  $script:pushedLocations++
}

$backendImage = "autoproduction-backend:$Version"
$frontendImage = "autoproduction-frontend:$Version"
$releaseName = "release_$Version"
$releaseLocalDir = Join-Path ([System.IO.Path]::GetTempPath()) ("autoproduction_{0}" -f [System.Guid]::NewGuid().ToString("N"))
$tarName = "autoproduction_images_$Version.tar"
$tarLocalPath = Join-Path $releaseLocalDir $tarName
$envLocalPath = Join-Path $releaseLocalDir ".env.test"
$composeLocalPath = Join-Path $releaseLocalDir "docker-compose.test.yml"
$remoteReleaseDir = "$RemoteDir/$releaseName"
$remoteTarPath = "$remoteReleaseDir/$tarName"
$remoteTarUploaded = $false

New-Item -ItemType Directory -Path $releaseLocalDir -Force | Out-Null

$envContent = @"
APP_VERSION=$Version
APP_HTTP_PORT=$AppHttpPort
BACKEND_PORT=$BackendPort
SPRING_PROFILES_ACTIVE=$SpringProfile
DB_NAME=$DbName
DB_USERNAME=$DbUsername
DB_PASSWORD=$DbPassword
TZ=$Timezone
ERP_USE_REAL_ORDERS=$ErpUseRealOrders
ERP_SQLITE_PATH=$ErpSqlitePath
CORS_ALLOWED_ORIGINS=$CorsAllowedOrigins
"@
Set-Content -Path $envLocalPath -Value $envContent -Encoding UTF8
Copy-Item -Path $composeFile -Destination $composeLocalPath -Force

Write-Host "== Release info =="
Write-Host "TEST: ${TestUser}@${TestHost}:$SshPort"
Write-Host "VERSION: $Version"
Write-Host "REMOTE DIR: $remoteReleaseDir"
Write-Host "PROJECT: $ComposeProjectName"
Write-Host ""

try {
  Write-Host "[1/8] Build backend image: $backendImage"
  Push-ScopedLocation $backendDir
  Invoke-Checked `
    -Command "docker" `
    -Arguments @("build", "-f", "Dockerfile", "-t", $backendImage, ".") `
    -FailureMessage "Build backend image failed"
  Pop-Location
  $pushedLocations--

  Write-Host "[2/8] Build frontend image: $frontendImage"
  Push-ScopedLocation $frontendDir
  Invoke-Checked `
    -Command "docker" `
    -Arguments @("build", "-f", "Dockerfile", "--build-arg", "VITE_API_BASE=", "-t", $frontendImage, ".") `
    -FailureMessage "Build frontend image failed"
  Pop-Location
  $pushedLocations--

  Write-Host "[3/8] Export images -> $tarLocalPath"
  Invoke-Checked `
    -Command "docker" `
    -Arguments @("save", "-o", $tarLocalPath, $backendImage, $frontendImage) `
    -FailureMessage "Export images failed"

  if (-not (Test-Path $tarLocalPath)) {
    throw "Image tar was not generated: $tarLocalPath"
  }

  Write-Host "[4/8] Create remote release directory"
  Invoke-Remote "mkdir -p '$remoteReleaseDir'"

  Push-ScopedLocation $releaseLocalDir

  Write-Host "[5/8] Upload tar + compose + env"
  Invoke-Checked `
    -Command "scp" `
    -Arguments @("-P", "$SshPort", $tarName, "${TestUser}@${TestHost}:$remoteTarPath") `
    -FailureMessage "Upload image tar failed"
  $remoteTarUploaded = $true
  Invoke-Checked `
    -Command "scp" `
    -Arguments @("-P", "$SshPort", "docker-compose.test.yml", "${TestUser}@${TestHost}:$remoteReleaseDir/docker-compose.test.yml") `
    -FailureMessage "Upload compose file failed"
  Invoke-Checked `
    -Command "scp" `
    -Arguments @("-P", "$SshPort", ".env.test", "${TestUser}@${TestHost}:$remoteReleaseDir/.env.test") `
    -FailureMessage "Upload env file failed"

  Pop-Location
  $pushedLocations--

  Write-Host "[6/8] Load images on test server"
  Invoke-Remote "docker load -i '$remoteTarPath'"

  Write-Host "[7/8] Start stack with docker compose"
  Invoke-Remote "cd '$remoteReleaseDir' && docker compose -p '$ComposeProjectName' --env-file .env.test -f docker-compose.test.yml up -d"

  Write-Host "[8/8] Health check: http://127.0.0.1:$AppHttpPort/api/health"
  $healthy = $false
  for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    $checkCmd = "if command -v curl >/dev/null 2>&1; then curl -fsS 'http://127.0.0.1:$AppHttpPort/api/health'; else wget -qO- 'http://127.0.0.1:$AppHttpPort/api/health'; fi"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
      & ssh "-p" "$SshPort" "$TestUser@$TestHost" $checkCmd *> $null
    } finally {
      $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($LASTEXITCODE -eq 0) {
      $healthy = $true
      break
    }
  }

  if (-not $healthy) {
    throw "Health check failed: http://${TestHost}:$AppHttpPort/api/health"
  }

  Write-Host "Health check passed."
  Write-Host "Service URL: http://${TestHost}:$AppHttpPort"

  if (-not $KeepArtifacts) {
    Write-Host "Cleanup release image tar on test server"
    Invoke-Remote "rm -f '$remoteTarPath'"
    $remoteTarUploaded = $false
  }
} finally {
  if (-not $KeepArtifacts -and $remoteTarUploaded) {
    try {
      Invoke-Remote "rm -f '$remoteTarPath'"
    } catch {
      Write-Warning "Failed to cleanup remote tar: $remoteTarPath"
    }
  }
  while ($pushedLocations -gt 0) {
    Pop-Location
    $pushedLocations--
  }
  if (-not $KeepArtifacts -and (Test-Path $releaseLocalDir)) {
    Remove-Item -Path $releaseLocalDir -Recurse -Force -ErrorAction SilentlyContinue
  }
}
