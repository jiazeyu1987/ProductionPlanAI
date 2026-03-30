param(
    [string]$Python = "python",
    [switch]$SkipBackendTests
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

Push-Location $RepoRoot
try {
    & $Python "scripts/check_artifacts.py"
    & $Python "scripts/validate_erp_extract.py" --generate-template (Join-Path $env:TEMP "erp_extract_template_ci.xlsx")

    if (-not $SkipBackendTests) {
        $backendDir = Join-Path $RepoRoot "code\backend"
        if (Test-Path (Join-Path $backendDir "pom.xml")) {
            Write-Host "Running backend cross-module gate tests..."
            Push-Location $backendDir
            try {
                mvn "-Dtest=MvpApiTest,MvpStoreServiceOrderPoolMaterialsTest,ErpDataManagerTest,ErpSqliteOrderLoaderTest,SchedulerBenchmarkSmokeTest" test
            }
            finally {
                Pop-Location
            }
        }
        else {
            Write-Host "Backend project not found, skipping backend tests."
        }
    }
    else {
        Write-Host "SkipBackendTests enabled, backend gate tests skipped."
    }

    Write-Host "P0 quality gate passed."
}
finally {
    Pop-Location
}
