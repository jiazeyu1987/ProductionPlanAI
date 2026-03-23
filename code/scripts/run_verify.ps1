$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$frontend = Join-Path $root "frontend"
$mavenHome = "D:\ProjectPackage\AutoProduction\tools\apache-maven-3.9.9"
$jdkHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"

if (Test-Path $jdkHome) {
  $env:JAVA_HOME = $jdkHome
  $env:PATH = "$jdkHome\bin;$env:PATH"
}

if (Test-Path $mavenHome) {
  $env:PATH = "$mavenHome\bin;$env:PATH"
}

Write-Host "== Backend: test =="
Push-Location $backend
mvn test

Write-Host "== Backend: package =="
mvn -DskipTests package
Pop-Location

Write-Host "== Frontend: install/test/build =="
Push-Location $frontend
npm install
npm test
npm run build
Pop-Location

Write-Host "All verification steps passed."
