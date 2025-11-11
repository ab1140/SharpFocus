#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Bundles the SharpFocus Language Server for all platforms.

.DESCRIPTION
    This script builds the SharpFocus.LanguageServer project for multiple target platforms
    and copies the binaries to the Rider plugin's resources directory.

.PARAMETER Configuration
    The build configuration (Debug or Release). Default is Release.

.PARAMETER Platforms
    Array of target platforms to build for. Default is all supported platforms.

.EXAMPLE
    .\bundle-server.ps1
    Builds for all platforms in Release configuration.

.EXAMPLE
    .\bundle-server.ps1 -Configuration Debug -Platforms @("win-x64", "linux-x64")
    Builds only for Windows and Linux in Debug configuration.
#>

param(
    [Parameter()]
    [ValidateSet("Debug", "Release")]
    [string]$Configuration = "Release",
    
    [Parameter()]
    [string[]]$Platforms = @("win-x64", "linux-x64", "osx-x64", "osx-arm64")
)

$ErrorActionPreference = "Stop"

# Paths
$scriptDir = $PSScriptRoot
$riderPluginDir = Split-Path $scriptDir -Parent
$solutionRoot = Split-Path $riderPluginDir -Parent
$languageServerProject = Join-Path $solutionRoot "src\SharpFocus.LanguageServer\SharpFocus.LanguageServer.csproj"
$outputBase = Join-Path $riderPluginDir "src\rider\main\resources\server"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SharpFocus Language Server Bundler" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration: $Configuration" -ForegroundColor Yellow
Write-Host "Platforms: $($Platforms -join ', ')" -ForegroundColor Yellow
Write-Host ""

# Check if language server project exists
if (-not (Test-Path $languageServerProject)) {
    Write-Error "Language server project not found at: $languageServerProject"
    exit 1
}

# Clean output directory
if (Test-Path $outputBase) {
    Write-Host "Cleaning output directory..." -ForegroundColor Gray
    Remove-Item -Path $outputBase -Recurse -Force
}

New-Item -ItemType Directory -Path $outputBase -Force | Out-Null

# Build for each platform
foreach ($platform in $Platforms) {
    Write-Host ""
    Write-Host "Building for $platform..." -ForegroundColor Green
    
    $platformOutput = Join-Path $outputBase $platform
    
    # Build with dotnet publish
    # Using self-contained=true to bundle .NET runtime (no .NET SDK required on target machine)
    # Using PublishSingleFile=true to create a single executable (easier extraction and deployment)
    $publishArgs = @(
        "publish",
        $languageServerProject,
        "-c", $Configuration,
        "-r", $platform,
        "--self-contained", "true",
        "-o", $platformOutput,
        "/p:PublishSingleFile=true",
        "/p:EnableCompressionInSingleFile=true",
        "/p:DebugType=embedded"
    )
    
    Write-Host "  Running: dotnet $($publishArgs -join ' ')" -ForegroundColor Gray
    
    & dotnet @publishArgs
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed for $platform"
        exit $LASTEXITCODE
    }
    
    # Clean up unnecessary files
    $filesToRemove = @(
        "*.pdb"  # Remove PDB files (we use embedded debug info)
    )
    
    foreach ($pattern in $filesToRemove) {
        Get-ChildItem -Path $platformOutput -Filter $pattern -File | Remove-Item -Force
    }
    
    # Report size
    $totalSize = (Get-ChildItem -Path $platformOutput -Recurse -File | Measure-Object -Property Length -Sum).Sum
    $sizeMB = [math]::Round($totalSize / 1MB, 2)
    Write-Host "  Output size: $sizeMB MB" -ForegroundColor Gray
    Write-Host "  Output path: $platformOutput" -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Build Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Calculate total size
$totalSize = (Get-ChildItem -Path $outputBase -Recurse -File | Measure-Object -Property Length -Sum).Sum
$totalSizeMB = [math]::Round($totalSize / 1MB, 2)

Write-Host ""
Write-Host "✓ Successfully built for $($Platforms.Count) platform(s)" -ForegroundColor Green
Write-Host "  Total size: $totalSizeMB MB" -ForegroundColor Gray
Write-Host "  Output: $outputBase" -ForegroundColor Gray
Write-Host ""
Write-Host "Language server binaries are ready for plugin packaging." -ForegroundColor Green
Write-Host ""
