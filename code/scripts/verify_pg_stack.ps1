$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$mavenHome = "D:\ProjectPackage\AutoProduction\tools\apache-maven-3.9.9"
$jdkHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$jarPath = Join-Path $backend "target\autoproduction-mvp-backend-1.0.0.jar"
$stdoutLog = Join-Path $backend "target\verify-pg-backend.out.log"
$stderrLog = Join-Path $backend "target\verify-pg-backend.err.log"
$backendProcess = $null

if (Test-Path $jdkHome) {
  $env:JAVA_HOME = $jdkHome
  $env:PATH = "$jdkHome\bin;$env:PATH"
}

if (Test-Path $mavenHome) {
  $env:PATH = "$mavenHome\bin;$env:PATH"
}

$env:DB_URL = "jdbc:postgresql://localhost:5432/autoproduction"
$env:DB_USERNAME = "autoproduction"
$env:DB_PASSWORD = "autoproduction"

powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "run_postgres.ps1")

Push-Location $backend

try {
  mvn -DskipTests package
  if (-not (Test-Path $jarPath)) {
    throw "Backend jar not found: $jarPath"
  }

  if (Test-Path $stdoutLog) { Remove-Item $stdoutLog -Force }
  if (Test-Path $stderrLog) { Remove-Item $stderrLog -Force }

  $backendProcess = Start-Process `
    -FilePath "java" `
    -ArgumentList @("-jar", $jarPath) `
    -WorkingDirectory $backend `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

  $ok = $false
  for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 2
    if ($backendProcess.HasExited) {
      throw "Backend process exited unexpectedly. See logs: $stdoutLog, $stderrLog"
    }
    try {
      $health = Invoke-RestMethod -Uri "http://localhost:5931/api/health"
      if ($health.status -eq "ok") {
        $ok = $true
        break
      }
    } catch {}
  }
  if (-not $ok) {
    throw "Backend did not become ready."
  }

  $pool = Invoke-RestMethod -Headers @{ Authorization = "Bearer test-token" } -Uri "http://localhost:5931/internal/v1/internal/order-pool"
  if ($null -eq $pool.total) {
    throw "Order pool API validation failed."
  }

  Write-Host ("PG stack verification passed. orderPoolTotal={0}" -f $pool.total)
} finally {
  if ($backendProcess -and -not $backendProcess.HasExited) {
    Stop-Process -Id $backendProcess.Id -Force
  }
  Pop-Location
  powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "stop_postgres.ps1")
}
