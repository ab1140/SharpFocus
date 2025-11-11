#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Cleans all build artifacts for the SharpFocus Rider plugin.

.DESCRIPTION
    Removes build directories, caches, and temporary files to ensure
    a fresh build environment.

.PARAMETER All
    Also clean Gradle caches and daemon (more thorough but slower)

.EXAMPLE
    .\clean.ps1
    Clean build artifacts

.EXAMPLE
    .\clean.ps1 -All
    Deep clean including Gradle caches
#>

param(
    [switch]$All
)

$ErrorActionPreference = "Stop"

# Script configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SharpFocus - Clean Build Artifacts" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Directories to clean
$cleanDirs = @(
    "build",
    "out",
    ".gradle",
    "src\rider\main\resources\server"
)

$totalSize = 0

# Clean directories
foreach ($dir in $cleanDirs) {
    $fullPath = Join-Path $ScriptDir $dir
    if (Test-Path $fullPath) {
        Write-Host "🧹 Removing: $dir" -ForegroundColor Yellow
        try {
            # Calculate size before deletion
            $size = (Get-ChildItem $fullPath -Recurse -ErrorAction SilentlyContinue | 
                     Measure-Object -Property Length -Sum -ErrorAction SilentlyContinue).Sum
            if ($size) {
                $totalSize += $size
            }
            
            Remove-Item $fullPath -Recurse -Force -ErrorAction Stop
            Write-Host "  ✓ Removed" -ForegroundColor Green
        }
        catch {
            Write-Host "  ✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# Clean Gradle daemon if requested
if ($All) {
    Write-Host ""
    Write-Host "🧹 Stopping Gradle daemon..." -ForegroundColor Yellow
    try {
        & .\gradlew.bat --stop 2>&1 | Out-Null
        Write-Host "  ✓ Gradle daemon stopped" -ForegroundColor Green
    }
    catch {
        Write-Host "  ⚠ Gradle daemon not running" -ForegroundColor Yellow
    }
    
    # Clean user-level Gradle cache
    $gradleUserHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { "$env:USERPROFILE\.gradle" }
    $gradleCaches = Join-Path $gradleUserHome "caches"
    if (Test-Path $gradleCaches) {
        Write-Host ""
        Write-Host "🧹 Cleaning Gradle caches..." -ForegroundColor Yellow
        try {
            $size = (Get-ChildItem $gradleCaches -Recurse -ErrorAction SilentlyContinue | 
                     Measure-Object -Property Length -Sum -ErrorAction SilentlyContinue).Sum
            if ($size) {
                $totalSize += $size
            }
            
            Remove-Item $gradleCaches -Recurse -Force -ErrorAction Stop
            Write-Host "  ✓ Cleaned" -ForegroundColor Green
        }
        catch {
            Write-Host "  ✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "✓ Clean complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

if ($totalSize -gt 0) {
    $sizeMB = [math]::Round($totalSize / 1MB, 2)
    $sizeGB = [math]::Round($totalSize / 1GB, 2)
    
    if ($sizeGB -ge 1) {
        Write-Host "Freed: $sizeGB GB" -ForegroundColor Cyan
    } else {
        Write-Host "Freed: $sizeMB MB" -ForegroundColor Cyan
    }
}

Write-Host ""
Write-Host "To rebuild:" -ForegroundColor Yellow
Write-Host "  .\build.ps1" -ForegroundColor White
Write-Host ""
