$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$mavenHome = "D:\ProjectPackage\AutoProduction\tools\apache-maven-3.9.9"
$jdkHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"

if (Test-Path $jdkHome) {
  $env:JAVA_HOME = $jdkHome
  $env:PATH = "$jdkHome\bin;$env:PATH"
}

if (Test-Path $mavenHome) {
  $env:PATH = "$mavenHome\bin;$env:PATH"
}

Push-Location $backend
mvn "spring-boot:run" "-Dspring-boot.run.profiles=local"
