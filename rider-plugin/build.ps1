#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Builds the SharpFocus Rider plugin with automatic JDK detection.

.DESCRIPTION
    This script automatically detects JDK 21, sets up the environment,
    and builds the plugin distribution package.

.PARAMETER Clean
    Perform a clean build (deletes build artifacts first)

.PARAMETER NoDaemon
    Run Gradle without daemon (useful for CI environments)

.PARAMETER Configuration
    Build configuration: Release (default) or Debug

.EXAMPLE
    .\build.ps1
    Standard build

.EXAMPLE
    .\build.ps1 -Clean
    Clean build

.EXAMPLE
    .\build.ps1 -NoDaemon
    Build without Gradle daemon (for CI)
#>

param(
    [switch]$Clean,
    [switch]$NoDaemon,
    [ValidateSet('Release', 'Debug')]
    [string]$Configuration = 'Release'
)

$ErrorActionPreference = "Stop"

# Script configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SharpFocus Rider Plugin Builder" -ForegroundColor Cyan
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
        Write-Host "✗ .NET SDK not found!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please install .NET SDK from:" -ForegroundColor Yellow
        Write-Host "  https://dotnet.microsoft.com/download" -ForegroundColor Cyan
        Write-Host ""
        return $false
    }
}

# Function to run Gradle
function Invoke-GradleBuild {
    param(
        [string]$JavaHome,
        [switch]$CleanFirst,
        [switch]$NoDaemon
    )
    
    # Set environment variables
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\bin;$env:PATH"
    
    # Build Gradle command
    $gradleCmd = ".\gradlew.bat"
    $gradleArgs = @()
    
    if ($CleanFirst) {
        Write-Host ""
        Write-Host "🧹 Cleaning build artifacts..." -ForegroundColor Yellow
        & $gradleCmd clean
        if ($LASTEXITCODE -ne 0) {
            Write-Host "✗ Clean failed!" -ForegroundColor Red
            exit $LASTEXITCODE
        }
        Write-Host "✓ Clean complete" -ForegroundColor Green
    }
    
    $gradleArgs += "buildPlugin"
    
    if ($NoDaemon) {
        $gradleArgs += "--no-daemon"
    }
    
    # Execute build
    Write-Host ""
    Write-Host "🔨 Building plugin..." -ForegroundColor Yellow
    Write-Host "Command: $gradleCmd $($gradleArgs -join ' ')" -ForegroundColor Gray
    Write-Host ""
    
    & $gradleCmd @gradleArgs
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "✗ Build failed!" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    
    return $true
}

# Main execution
try {
    # Find JDK 21
    $jdkPath = Find-JDK21
    
    # Verify .NET SDK
    if (-not (Test-DotNetSDK)) {
        exit 1
    }
    
    # Run build
    Write-Host ""
    $buildSuccess = Invoke-GradleBuild -JavaHome $jdkPath -CleanFirst:$Clean -NoDaemon:$NoDaemon
    
    if ($buildSuccess) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "✓ Build successful!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        
        $distPath = Join-Path $ScriptDir "build\distributions"
        if (Test-Path $distPath) {
            $zipFiles = Get-ChildItem $distPath -Filter "*.zip"
            if ($zipFiles) {
                Write-Host "📦 Plugin package created:" -ForegroundColor Cyan
                foreach ($file in $zipFiles) {
                    $sizeMB = [math]::Round($file.Length / 1MB, 2)
                    Write-Host "   $($file.Name) ($sizeMB MB)" -ForegroundColor White
                }
                Write-Host ""
                Write-Host "📍 Location: $distPath" -ForegroundColor Cyan
                Write-Host ""
                Write-Host "To install in Rider:" -ForegroundColor Yellow
                Write-Host "  1. Open Rider" -ForegroundColor White
                Write-Host "  2. Settings → Plugins → ⚙️ → Install Plugin from Disk" -ForegroundColor White
                Write-Host "  3. Select the ZIP file above" -ForegroundColor White
                Write-Host "  4. Restart Rider" -ForegroundColor White
                Write-Host ""
            }
        }
    }
}
catch {
    Write-Host ""
    Write-Host "✗ Build failed with error:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    exit 1
}
