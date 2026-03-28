$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$mavenHome = "D:\ProjectPackage\AutoProduction\tools\apache-maven-3.9.9"
$jdkHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$erpDefaultsScript = "D:\ProjectPackage\demo\other\demo_query_881_lists.py"

if (Test-Path $jdkHome) {
  $env:JAVA_HOME = $jdkHome
  $env:PATH = "$jdkHome\bin;$env:PATH"
}

if (Test-Path $mavenHome) {
  $env:PATH = "$mavenHome\bin;$env:PATH"
}

function Set-EnvIfEmpty {
  param(
    [string]$Name,
    [string]$Value
  )
  $current = [Environment]::GetEnvironmentVariable($Name, "Process")
  if ([string]::IsNullOrWhiteSpace($current) -and -not [string]::IsNullOrWhiteSpace($Value)) {
    [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
  }
}

if (Test-Path $erpDefaultsScript) {
  $constants = @{
    "BASE_URL" = ""
    "ACCT_ID" = ""
    "USERNAME" = ""
    "PASSWORD" = ""
    "LCID" = ""
  }

  foreach ($line in Get-Content -Path $erpDefaultsScript -Encoding UTF8) {
    if ($line -match '^(BASE_URL|ACCT_ID|USERNAME|PASSWORD)\s*=\s*"([^"]*)"') {
      $constants[$matches[1]] = $matches[2]
      continue
    }
    if ($line -match '^(LCID)\s*=\s*(\d+)') {
      $constants[$matches[1]] = $matches[2]
    }
  }

  Set-EnvIfEmpty -Name "ERP_BASE_URL" -Value $constants["BASE_URL"]
  Set-EnvIfEmpty -Name "ERP_ACCT_ID" -Value $constants["ACCT_ID"]
  Set-EnvIfEmpty -Name "ERP_USERNAME" -Value $constants["USERNAME"]
  Set-EnvIfEmpty -Name "ERP_PASSWORD" -Value $constants["PASSWORD"]
  Set-EnvIfEmpty -Name "ERP_LCID" -Value ($constants["LCID"])
  Set-EnvIfEmpty -Name "ERP_TIMEOUT" -Value "60"
  Set-EnvIfEmpty -Name "ERP_VERIFY_SSL" -Value "false"
}

$fallbackSqlitePath = "D:\ProjectPackage\demo\other\erp_recent_orders.db"
$defaultSqlitePath = "D:\ProjectPackage\demo\erp_demo\erp_recent_orders.db"
$configuredSqlitePath = [Environment]::GetEnvironmentVariable("ERP_SQLITE_PATH", "Process")
if ([string]::IsNullOrWhiteSpace($configuredSqlitePath)) {
  if ((Test-Path $defaultSqlitePath) -and ((Get-Item $defaultSqlitePath).Length -gt 0)) {
    [Environment]::SetEnvironmentVariable("ERP_SQLITE_PATH", $defaultSqlitePath, "Process")
  } elseif ((Test-Path $fallbackSqlitePath) -and ((Get-Item $fallbackSqlitePath).Length -gt 0)) {
    [Environment]::SetEnvironmentVariable("ERP_SQLITE_PATH", $fallbackSqlitePath, "Process")
  }
}

Push-Location $backend
mvn "spring-boot:run" "-Dspring-boot.run.profiles=local"
