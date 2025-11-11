#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Builds a single-platform version of the language server for development.

.DESCRIPTION
    Quickly builds the language server for the current platform for testing.

.PARAMETER Configuration
    The build configuration (Debug or Release). Default is Debug.

.EXAMPLE
    .\bundle-server-quick.ps1
    Builds for current platform in Debug configuration.
#>

param(
    [Parameter()]
    [ValidateSet("Debug", "Release")]
    [string]$Configuration = "Debug"
)

$ErrorActionPreference = "Stop"

# Detect current platform
$platform = if ($IsWindows -or $env:OS -match "Windows") {
    "win-x64"
} elseif ($IsLinux) {
    "linux-x64"
} elseif ($IsMacOS) {
    if ([System.Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture -eq [System.Runtime.InteropServices.Architecture]::Arm64) {
        "osx-arm64"
    } else {
        "osx-x64"
    }
} else {
    Write-Error "Unable to detect platform"
    exit 1
}

Write-Host "Building for current platform: $platform" -ForegroundColor Cyan
Write-Host ""

# Call the main bundler script with single platform
& $PSScriptRoot\bundle-server.ps1 -Configuration $Configuration -Platforms @($platform)
