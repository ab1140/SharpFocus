#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Quick build for Rider plugin development - skips language server bundling.
    
.DESCRIPTION
    This script quickly builds the Rider plugin without rebuilding the language server,
    which saves significant time during development when only Kotlin code changes.
    
    The language server must have been built at least once before using this script.
    
.EXAMPLE
    .\quick-build.ps1
    
.EXAMPLE
    .\quick-build.ps1 -Clean
#>

param(
    [switch]$Clean = $false
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SharpFocus Quick Build (Frontend Only)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Set Java environment
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH

Write-Host "Using Java: $env:JAVA_HOME" -ForegroundColor Gray
Write-Host ""

# Check if language server exists
$serverPath = ".\src\rider\main\resources\server"
if (-not (Test-Path $serverPath)) {
    Write-Host "ERROR: Language server not found at $serverPath" -ForegroundColor Red
    Write-Host "Please run full build first: .\gradlew.bat buildPlugin" -ForegroundColor Yellow
    exit 1
}

# Clean if requested
if ($Clean) {
    Write-Host "Cleaning build directory..." -ForegroundColor Yellow
    .\gradlew.bat clean --no-daemon
    Write-Host ""
}

# Build plugin without rebuilding server
Write-Host "Building plugin (skipping language server bundling)..." -ForegroundColor Green
Write-Host ""

$startTime = Get-Date

.\gradlew.bat buildPlugin --no-daemon --exclude-task bundleLanguageServer

$endTime = Get-Date
$duration = $endTime - $startTime

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "Build completed successfully!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "Time taken: $($duration.ToString('mm\:ss'))" -ForegroundColor Gray
    Write-Host ""
    
    $zipFile = Get-Item ".\build\distributions\rider-sharpfocus-*.zip" | Select-Object -First 1
    if ($zipFile) {
        $sizeInMB = [math]::Round($zipFile.Length / 1MB, 2)
        Write-Host "Plugin package: $($zipFile.Name) ($sizeInMB MB)" -ForegroundColor Cyan
        Write-Host "Location: $($zipFile.FullName)" -ForegroundColor Gray
        Write-Host ""
        Write-Host "Install in Rider:" -ForegroundColor Yellow
        Write-Host "  1. File → Settings → Plugins" -ForegroundColor Gray
        Write-Host "  2. Click gear icon → Install Plugin from Disk" -ForegroundColor Gray
        Write-Host "  3. Select: $($zipFile.FullName)" -ForegroundColor Gray
        Write-Host "  4. Restart Rider" -ForegroundColor Gray
    }
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "Build failed!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit $LASTEXITCODE
}
