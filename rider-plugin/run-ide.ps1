#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Runs the SharpFocus Rider plugin in a development sandbox.

.DESCRIPTION
    This script automatically detects JDK 21, sets up the environment,
    and launches a Rider IDE sandbox with the plugin pre-loaded.

.PARAMETER Debug
    Build and run in Debug configuration (faster compilation)

.PARAMETER SkipServerBundle
    Skip bundling the language server (fast mode for Kotlin-only changes)

.EXAMPLE
    .\run-ide.ps1
    Run the plugin in sandbox (fast mode, skips server bundling)

.EXAMPLE
    .\run-ide.ps1 -SkipServerBundle:$false
    Run with full server bundling
#>

param(
    [switch]$Debug,
    [bool]$SkipServerBundle = $true
)

$ErrorActionPreference = "Stop"

# Script configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SharpFocus Rider Plugin - Dev Sandbox" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Function to find JDK 21
function Find-JDK21 {
    Write-Host "🔍 Searching for JDK 21..." -ForegroundColor Yellow

    # Check if JAVA_HOME is already set and points to JDK 21
    if ($env:JAVA_HOME) {
        $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaExe) {
            $versionOutput = & $javaExe -version 2>&1 | Select-String "version" | Select-Object -First 1
            if ($versionOutput -match 'version "21\.') {
                Write-Host "✓ Found JDK 21 at: $env:JAVA_HOME" -ForegroundColor Green
                return $env:JAVA_HOME
            }
        }
    }

    # Search common installation locations
    $searchPaths = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\OpenJDK",
        "C:\Program Files\Microsoft",
        "C:\Program Files (x86)\Eclipse Adoptium",
        "C:\Program Files (x86)\Java"
    )

    foreach ($basePath in $searchPaths) {
        if (Test-Path $basePath) {
            $jdkDirs = Get-ChildItem $basePath -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'jdk-?21' -or $_.Name -match 'java-21' }

            foreach ($dir in $jdkDirs) {
                $javaExe = Join-Path $dir.FullName "bin\java.exe"
                if (Test-Path $javaExe) {
                    $versionOutput = & $javaExe -version 2>&1 | Select-String "version" | Select-Object -First 1
                    if ($versionOutput -match 'version "21\.') {
                        Write-Host "✓ Found JDK 21 at: $($dir.FullName)" -ForegroundColor Green
                        return $dir.FullName
                    }
                }
            }
        }
    }

    # JDK 21 not found
    Write-Host "✗ JDK 21 not found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install JDK 21 from:" -ForegroundColor Yellow
    Write-Host "  https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Or set JAVA_HOME manually:" -ForegroundColor Yellow
    Write-Host '  $env:JAVA_HOME = "C:\Path\To\JDK21"' -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

# Function to verify .NET SDK
function Test-DotNetSDK {
    Write-Host "🔍 Checking .NET SDK..." -ForegroundColor Yellow

    try {
        $dotnetVersion = & dotnet --version 2>&1
        Write-Host "✓ Found .NET SDK: $dotnetVersion" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "⚠ .NET SDK not found!" -ForegroundColor Yellow
        Write-Host "The language server requires .NET SDK to run." -ForegroundColor Yellow
        Write-Host "Download from: https://dotnet.microsoft.com/download" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Continuing anyway (plugin will load but server may not start)..." -ForegroundColor Yellow
        return $false
    }
}

# Function to run IDE
function Invoke-RunIDE {
    param(
        [string]$JavaHome
    )

    # Set environment variables
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\bin;$env:PATH"

    # Build Gradle command
    $gradleCmd = ".\gradlew.bat"
    $gradleArgs = @("runIde")

    # Add skip server bundle flag if specified
    if ($SkipServerBundle) {
        $gradleArgs += "-PskipServerBundle=true"
    }

    # Execute runIde
    Write-Host ""
    Write-Host "🚀 Starting Rider sandbox..." -ForegroundColor Yellow
    if ($SkipServerBundle) {
        Write-Host "⚡ Fast mode: Skipping server bundling" -ForegroundColor Green
    }
    Write-Host "Command: $gradleCmd $($gradleArgs -join ' ')" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Tips:" -ForegroundColor Cyan
    Write-Host "  • The sandbox runs in a separate config (won't affect main Rider)" -ForegroundColor White
    Write-Host "  • Plugin is pre-installed and enabled" -ForegroundColor White
    Write-Host "  • Logs: build\idea-sandbox\system\log\idea.log" -ForegroundColor White
    Write-Host "  • Press Ctrl+C to stop the sandbox" -ForegroundColor White
    Write-Host ""
    Write-Host "Waiting for sandbox to start..." -ForegroundColor Yellow
    Write-Host ""

    & $gradleCmd @gradleArgs

    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "✗ Sandbox failed to start!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Check logs at: build\idea-sandbox\system\log\idea.log" -ForegroundColor Yellow
        exit $LASTEXITCODE
    }
}

# Main execution
try {
    # Find JDK 21
    $jdkPath = Find-JDK21

    # Verify .NET SDK (warning only)
    Test-DotNetSDK | Out-Null

    # Check if plugin needs building
    $buildDir = Join-Path $ScriptDir "build"
    if (-not (Test-Path $buildDir)) {
        Write-Host ""
        Write-Host "⚠ Plugin not built yet. Building first..." -ForegroundColor Yellow
        & .\build.ps1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "✗ Build failed!" -ForegroundColor Red
            exit $LASTEXITCODE
        }
    }

    # Run IDE
    Invoke-RunIDE -JavaHome $jdkPath

    Write-Host ""
    Write-Host "Sandbox closed." -ForegroundColor Gray
}
catch {
    Write-Host ""
    Write-Host "✗ Failed to start sandbox:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    exit 1
}
