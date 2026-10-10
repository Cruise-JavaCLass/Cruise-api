<#
.SYNOPSIS
    Test execution runner script for Cruise API microservices.
.DESCRIPTION
    Runs JUnit 5 test suites across modules or selectively for specific modules/tests.
.PARAMETER Module
    Target microservice module: 'all', 'common', 'gateway', 'auth', 'itinerary', 'activity', 'pos'
.PARAMETER Test
    Specific test class or method pattern, e.g., 'UserRepositoryTest' or 'UserControllerTest#testCreateUser'
.PARAMETER Coverage
    Generates and displays JaCoCo code coverage report.
.PARAMETER Quick
    Executes tests using the 'quick' profile (skips JaCoCo agent for speed).
.EXAMPLE
    .\scripts\run-tests.ps1
    .\scripts\run-tests.ps1 -Module auth
    .\scripts\run-tests.ps1 -Test UserRepositoryTest
    .\scripts\run-tests.ps1 -Module auth -Coverage
    .\scripts\run-tests.ps1 -Quick
#>
param (
    [string]$Module = "all",
    [string]$Test = "",
    [switch]$Coverage,
    [switch]$Quick
)

$ErrorActionPreference = "Stop"

# Auto-detect JAVA_HOME if not set or invalid
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $candidate = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path "$candidate\bin\java.exe") {
        $env:JAVA_HOME = $candidate
    }
}

$moduleMap = @{
    "all" = ""
    "common" = "cruise-common"
    "gateway" = "cruise-gateway"
    "auth" = "cruise-auth-service"
    "itinerary" = "cruise-itinerary-service"
    "activity" = "cruise-activity-service"
    "pos" = "cruise-pos-service"
}

$targetModule = if ($moduleMap.ContainsKey($Module.ToLower())) { $moduleMap[$Module.ToLower()] } else { $Module }

$mvnArgs = @()

if ($Coverage) {
    $mvnArgs += "verify"
} else {
    $mvnArgs += "test"
}

if ($targetModule -and $targetModule -ne "") {
    $mvnArgs += "-pl"
    $mvnArgs += $targetModule
}

if ($Test) {
    $mvnArgs += "-Dtest=$Test"
}

if ($Quick) {
    $mvnArgs += "-Pquick"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Cruise API Test Runner" -ForegroundColor Green
Write-Host " Command: .\mvnw.cmd $($mvnArgs -join ' ')" -ForegroundColor Yellow
Write-Host " Target Module: $(if ($targetModule) { $targetModule } else { 'All Microservices' })" -ForegroundColor White
if ($Test) { Write-Host " Test Filter: $Test" -ForegroundColor White }
Write-Host "==========================================================" -ForegroundColor Cyan

$root = Split-Path -Parent $PSScriptRoot
$mvnw = Join-Path $root "mvnw.cmd"

& $mvnw @mvnArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n Tests completed successfully!" -ForegroundColor Green
    if ($Coverage) {
        Write-Host "`n Coverage Reports:" -ForegroundColor Cyan
        if ($targetModule) {
            Write-Host " - HTML Report: $root\$targetModule\target\site\jacoco\index.html" -ForegroundColor White
        } else {
            Write-Host " - Check target/site/jacoco/index.html in each microservice directory." -ForegroundColor White
        }
    }
} else {
    Write-Host "`n Tests failed with exit code $LASTEXITCODE." -ForegroundColor Red
    exit $LASTEXITCODE
}
