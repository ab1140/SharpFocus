# Package SharpFocus VS Code Extension
# This script builds and packages the extension into a .vsix file

param(
    [switch]$AllPlatforms,
    [string]$Runtime = "win-x64"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  SharpFocus Extension Packaging" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$extensionDir = Split-Path -Parent $PSScriptRoot
Set-Location $extensionDir

# Step 1: Compile TypeScript
Write-Host "[1/3] Compiling TypeScript..." -ForegroundColor Yellow
npm run compile

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: TypeScript compilation failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "  ✓ TypeScript compiled successfully" -ForegroundColor Green
Write-Host ""

# Step 2: Bundle language server
if ($AllPlatforms) {
    Write-Host "[2/3] Bundling language server for all platforms..." -ForegroundColor Yellow
    npm run bundle-all
} else {
    Write-Host "[2/3] Bundling language server for $Runtime..." -ForegroundColor Yellow
    pwsh -ExecutionPolicy Bypass -File "$PSScriptRoot\bundle-server.ps1" -Configuration Release -Runtime $Runtime
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Language server bundling failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "  ✓ Language server bundled successfully" -ForegroundColor Green
Write-Host ""

# Step 3: Package extension
Write-Host "[3/3] Packaging extension..." -ForegroundColor Yellow

npx @vscode/vsce package

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Extension packaging failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✓ Extension packaged successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# List the generated .vsix file
$vsixFiles = Get-ChildItem -Path $extensionDir -Filter "*.vsix" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($vsixFiles) {
    Write-Host "Package created:" -ForegroundColor Cyan
    Write-Host "  Name: $($vsixFiles.Name)" -ForegroundColor White
    Write-Host "  Size: $([math]::Round($vsixFiles.Length / 1MB, 2)) MB" -ForegroundColor White
    Write-Host "  Path: $($vsixFiles.FullName)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "To install:" -ForegroundColor Yellow
    Write-Host "  code --install-extension $($vsixFiles.Name)" -ForegroundColor White
}
