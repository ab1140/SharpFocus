#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Package SharpFocus Rider Plugin for JetBrains Marketplace

.DESCRIPTION
    Builds the plugin with all dependencies and language server binaries
    for distribution on JetBrains Marketplace.

.PARAMETER SkipTests
    Skip running tests during build

.PARAMETER Clean
    Clean build directories before building

.EXAMPLE
    .\package-plugin.ps1
    .\package-plugin.ps1 -Clean
#>

param(
    [switch]$SkipTests = $false,
    [switch]$Clean = $false
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SharpFocus Rider Plugin - Package" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check prerequisites
Write-Host "🔍 Checking prerequisites..." -ForegroundColor Yellow

# Check Java
$javaVersion = & java -version 2>&1 | Select-String "version" | ForEach-Object { $_ -replace '.*version "([^"]+)".*', '$1' }
if ($javaVersion -notmatch "^(21|2[2-9]|[3-9]\d)\.") {
    Write-Host "❌ Java 21 or higher required. Found: $javaVersion" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Java version: $javaVersion" -ForegroundColor Green

# Check .NET SDK
$dotnetVersion = & dotnet --version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ .NET SDK not found" -ForegroundColor Red
    exit 1
}
Write-Host "✓ .NET SDK version: $dotnetVersion" -ForegroundColor Green

Write-Host ""

# Clean if requested
if ($Clean) {
    Write-Host "🧹 Cleaning build directories..." -ForegroundColor Yellow
    & .\gradlew.bat clean
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Clean failed" -ForegroundColor Red
        exit 1
    }
    Write-Host "✓ Clean complete" -ForegroundColor Green
    Write-Host ""
}

# Build language server for all platforms
Write-Host "🔨 Building language server for all platforms..." -ForegroundColor Yellow
Write-Host "   This includes: Windows (x64, arm64), Linux (x64, arm64), macOS (x64, arm64)" -ForegroundColor Gray

$buildScript = Join-Path $PSScriptRoot "build-scripts\bundle-server.ps1"
& $buildScript -Configuration Release

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Language server build failed" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Language server bundled for all platforms" -ForegroundColor Green
Write-Host ""

# Build plugin
Write-Host "📦 Building plugin distribution..." -ForegroundColor Yellow

$gradleArgs = @("buildPlugin")
if ($SkipTests) {
    $gradleArgs += "-x", "test"
}

& .\gradlew.bat $gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Plugin build failed" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Plugin built successfully" -ForegroundColor Green
Write-Host ""

# Verify output
$distDir = Join-Path $PSScriptRoot "build\distributions"
$pluginZip = Get-ChildItem -Path $distDir -Filter "*sharpfocus*.zip" | Select-Object -First 1

if (-not $pluginZip) {
    Write-Host "❌ Plugin distribution not found in: $distDir" -ForegroundColor Red
    exit 1
}

$zipPath = $pluginZip.FullName
$zipSize = [math]::Round($pluginZip.Length / 1MB, 2)

Write-Host "========================================" -ForegroundColor Green
Write-Host "✅ Package Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "📦 Distribution Package:" -ForegroundColor Cyan
Write-Host "   Location: $zipPath" -ForegroundColor White
Write-Host "   Size: $zipSize MB" -ForegroundColor White
Write-Host ""

# Verify contents
Write-Host "📋 Package Contents:" -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)

    # Check for plugin.xml
    $pluginXml = $zip.Entries | Where-Object { $_.FullName -like "*/plugin.xml" }
    if ($pluginXml) {
        Write-Host "   ✓ plugin.xml" -ForegroundColor Green
    }

    # Check for server binaries
    $serverPlatforms = @("win-x64", "linux-x64", "osx-x64", "osx-arm64", "win-arm64", "linux-arm64")
    $foundPlatforms = @()

    foreach ($platform in $serverPlatforms) {
        $serverBinary = $zip.Entries | Where-Object { $_.FullName -like "*server/$platform/*" }
        if ($serverBinary) {
            $foundPlatforms += $platform
        }
    }

    if ($foundPlatforms.Count -gt 0) {
        Write-Host "   ✓ Language server binaries: $($foundPlatforms -join ', ')" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ No language server binaries found" -ForegroundColor Yellow
    }

    # Check for icons
    $icons = $zip.Entries | Where-Object { $_.FullName -like "*/icons/*.svg" }
    if ($icons) {
        Write-Host "   ✓ Icons ($($icons.Count) files)" -ForegroundColor Green
    }

    $zip.Dispose()
} catch {
    Write-Host "   ⚠ Could not verify package contents: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1. Test the plugin locally:" -ForegroundColor White
Write-Host "   - Open Rider → Settings → Plugins" -ForegroundColor Gray
Write-Host "   - Click ⚙️ → Install Plugin from Disk" -ForegroundColor Gray
Write-Host "   - Select: $zipPath" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Upload to JetBrains Marketplace:" -ForegroundColor White
Write-Host "   - Go to: https://plugins.jetbrains.com/" -ForegroundColor Gray
Write-Host "   - Sign in and click 'Upload Plugin'" -ForegroundColor Gray
Write-Host "   - Upload: $zipPath" -ForegroundColor Gray
Write-Host "   - Add screenshots from: rider-plugin\images\" -ForegroundColor Gray
Write-Host "   - Submit for review" -ForegroundColor Gray
Write-Host ""
Write-Host "📸 Screenshots available:" -ForegroundColor Cyan
$imagesDir = Join-Path $PSScriptRoot "images"
if (Test-Path $imagesDir) {
    Get-ChildItem -Path $imagesDir -Filter "*.png" | ForEach-Object {
        Write-Host "   - $($_.Name)" -ForegroundColor Gray
    }
}
Write-Host ""
Write-Host "🚀 Ready to publish!" -ForegroundColor Green
