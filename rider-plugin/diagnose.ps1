#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Analyzes SharpFocus plugin logs and diagnostics

.DESCRIPTION
    This script checks if the SharpFocus plugin is working correctly by:
    - Analyzing IDE logs for plugin activity
    - Checking if language server process is running
    - Verifying plugin installation
    - Providing diagnostic information

.EXAMPLE
    .\diagnose.ps1
    Run full diagnostics

.EXAMPLE
    .\diagnose.ps1 -ShowLogs
    Show recent log entries only
#>

param(
    [switch]$ShowLogs,
    [switch]$CheckProcess,
    [int]$LogLines = 50
)

$ErrorActionPreference = "Continue"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SharpFocus Plugin Diagnostics" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Function to find Rider log directory
function Find-RiderLogDir {
    $possibleDirs = @(
        "$env:APPDATA\JetBrains\Rider2025.1\system\log",
        "$env:APPDATA\JetBrains\Rider2024.3\system\log",
        "$env:APPDATA\JetBrains\Rider2024.2\system\log",
        "$env:LOCALAPPDATA\JetBrains\Rider2025.1\system\log"
    )
    
    foreach ($dir in $possibleDirs) {
        if (Test-Path $dir) {
            return $dir
        }
    }
    
    return $null
}

# Function to check plugin installation
function Test-PluginInstallation {
    Write-Host "🔍 Checking Plugin Installation..." -ForegroundColor Yellow
    
    $pluginDirs = @(
        "$env:APPDATA\JetBrains\Rider2025.1\plugins",
        "$env:APPDATA\JetBrains\Rider2024.3\plugins",
        "$env:LOCALAPPDATA\JetBrains\Rider2025.1\plugins"
    )
    
    foreach ($dir in $pluginDirs) {
        if (Test-Path $dir) {
            $sharpFocusDir = Get-ChildItem $dir -Filter "*sharpfocus*" -Directory -ErrorAction SilentlyContinue
            if ($sharpFocusDir) {
                Write-Host "  ✓ Plugin found at: $($sharpFocusDir.FullName)" -ForegroundColor Green
                
                # Check JAR file
                $jarFile = Get-ChildItem $sharpFocusDir.FullName -Recurse -Filter "*.jar" | Select-Object -First 1
                if ($jarFile) {
                    $sizeMB = [math]::Round($jarFile.Length / 1MB, 2)
                    Write-Host "  ✓ Plugin JAR: $($jarFile.Name) ($sizeMB MB)" -ForegroundColor Green
                }
                
                return $true
            }
        }
    }
    
    Write-Host "  ✗ Plugin not found in any known location" -ForegroundColor Red
    Write-Host "  → Install the plugin first from the ZIP file" -ForegroundColor Yellow
    return $false
}

# Function to check language server process
function Test-LanguageServerProcess {
    Write-Host ""
    Write-Host "🔍 Checking Language Server Process..." -ForegroundColor Yellow
    
    $processes = Get-Process -Name "*SharpFocus*" -ErrorAction SilentlyContinue
    
    if ($processes) {
        foreach ($proc in $processes) {
            Write-Host "  ✓ Language server running (PID: $($proc.Id))" -ForegroundColor Green
            Write-Host "    Process: $($proc.Name)" -ForegroundColor Gray
            Write-Host "    Memory: $([math]::Round($proc.WorkingSet64 / 1MB, 2)) MB" -ForegroundColor Gray
            
            # Check how long it's been running
            $uptime = (Get-Date) - $proc.StartTime
            Write-Host "    Uptime: $($uptime.ToString('hh\:mm\:ss'))" -ForegroundColor Gray
        }
        return $true
    } else {
        Write-Host "  ✗ Language server not running" -ForegroundColor Red
        Write-Host "  → Server may not have started or crashed" -ForegroundColor Yellow
        Write-Host "  → Check IDE logs for startup errors" -ForegroundColor Yellow
        return $false
    }
}

# Function to analyze logs
function Get-SharpFocusLogs {
    param([string]$LogDir, [int]$Lines)
    
    Write-Host ""
    Write-Host "🔍 Analyzing IDE Logs..." -ForegroundColor Yellow
    
    $logFile = Join-Path $LogDir "idea.log"
    
    if (-not (Test-Path $logFile)) {
        Write-Host "  ✗ Log file not found: $logFile" -ForegroundColor Red
        return
    }
    
    Write-Host "  📄 Log file: $logFile" -ForegroundColor Gray
    Write-Host "  📅 Last modified: $(Get-Item $logFile).LastWriteTime" -ForegroundColor Gray
    Write-Host ""
    
    # Search for SharpFocus entries
    $entries = Select-String -Path $logFile -Pattern "SharpFocus|sharpfocus" -ErrorAction SilentlyContinue
    
    if (-not $entries) {
        Write-Host "  ✗ No SharpFocus entries found in log!" -ForegroundColor Red
        Write-Host "  → Plugin may not be enabled or loaded" -ForegroundColor Yellow
        Write-Host "  → Check Settings → Plugins → SharpFocus is enabled" -ForegroundColor Yellow
        return
    }
    
    Write-Host "  ✓ Found $($entries.Count) SharpFocus-related entries" -ForegroundColor Green
    Write-Host ""
    
    # Show recent entries
    Write-Host "📋 Recent Log Entries (last $Lines):" -ForegroundColor Cyan
    Write-Host "----------------------------------------" -ForegroundColor Gray
    
    $recentEntries = $entries | Select-Object -Last $Lines
    foreach ($entry in $recentEntries) {
        $line = $entry.Line
        
        # Color code based on content
        if ($line -match "ERROR|error|Exception|Failed") {
            Write-Host $line -ForegroundColor Red
        } elseif ($line -match "WARN|warn|Warning") {
            Write-Host $line -ForegroundColor Yellow
        } elseif ($line -match "initialized|started|success|Found") {
            Write-Host $line -ForegroundColor Green
        } else {
            Write-Host $line -ForegroundColor White
        }
    }
    
    Write-Host ""
    
    # Check for critical events
    $hasInit = $entries | Where-Object { $_.Line -match "plugin initialized" }
    $hasServerStart = $entries | Where-Object { $_.Line -match "Language server started successfully" }
    $hasFocusRequest = $entries | Where-Object { $_.Line -match "Processing focus request|Received focus mode response" }
    $hasErrors = $entries | Where-Object { $_.Line -match "ERROR|Exception|Failed" }
    
    Write-Host "📊 Log Analysis:" -ForegroundColor Cyan
    Write-Host "  Plugin Initialized: $(if ($hasInit) { '✓ Yes' } else { '✗ No' })" -ForegroundColor $(if ($hasInit) { 'Green' } else { 'Red' })
    Write-Host "  Server Started: $(if ($hasServerStart) { '✓ Yes' } else { '✗ No' })" -ForegroundColor $(if ($hasServerStart) { 'Green' } else { 'Red' })
    Write-Host "  Focus Requests: $(if ($hasFocusRequest) { '✓ Yes' } else { '⚠ None' })" -ForegroundColor $(if ($hasFocusRequest) { 'Green' } else { 'Yellow' })
    Write-Host "  Errors Found: $(if ($hasErrors) { "⚠ $($hasErrors.Count)" } else { '✓ None' })" -ForegroundColor $(if ($hasErrors) { 'Yellow' } else { 'Green' })
}

# Function to check temp directory
function Test-TempDirectory {
    Write-Host ""
    Write-Host "🔍 Checking Temp Directory..." -ForegroundColor Yellow
    
    $tempDirs = Get-ChildItem $env:TEMP -Filter "sharpfocus-server-*" -Directory -ErrorAction SilentlyContinue
    
    if ($tempDirs) {
        Write-Host "  ✓ Found $($tempDirs.Count) extracted server(s)" -ForegroundColor Green
        foreach ($dir in $tempDirs) {
            Write-Host "    $($dir.FullName)" -ForegroundColor Gray
            
            # Check for exe file
            $exe = Get-ChildItem $dir.FullName -Filter "*.exe" -ErrorAction SilentlyContinue
            if ($exe) {
                $sizeMB = [math]::Round($exe.Length / 1MB, 2)
                Write-Host "      → $($exe.Name) ($sizeMB MB)" -ForegroundColor Gray
            }
        }
    } else {
        Write-Host "  ⚠ No extracted servers found" -ForegroundColor Yellow
        Write-Host "  → Server extraction may have failed" -ForegroundColor Yellow
        Write-Host "  → Check logs for extraction errors" -ForegroundColor Yellow
    }
}

# Main execution
try {
    # Check plugin installation
    $pluginInstalled = Test-PluginInstallation
    
    if (-not $pluginInstalled) {
        Write-Host ""
        Write-Host "⚠ Plugin is not installed!" -ForegroundColor Red
        Write-Host ""
        Write-Host "To install:" -ForegroundColor Yellow
        Write-Host "  1. Open Rider" -ForegroundColor White
        Write-Host "  2. Settings → Plugins" -ForegroundColor White
        Write-Host "  3. ⚙️ → Install Plugin from Disk" -ForegroundColor White
        Write-Host "  4. Select: build\distributions\rider-sharpfocus-0.1.0.zip" -ForegroundColor White
        Write-Host ""
        exit 1
    }
    
    # Check language server process
    if (-not $CheckProcess) {
        $serverRunning = Test-LanguageServerProcess
    }
    
    # Check temp directory
    Test-TempDirectory
    
    # Find and analyze logs
    $logDir = Find-RiderLogDir
    
    if ($logDir) {
        Get-SharpFocusLogs -LogDir $logDir -Lines $LogLines
    } else {
        Write-Host ""
        Write-Host "⚠ Could not find Rider log directory" -ForegroundColor Yellow
        Write-Host "  Rider may not be installed or version is different" -ForegroundColor Gray
    }
    
    # Summary and next steps
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "💡 Next Steps:" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    
    if ($pluginInstalled -and $serverRunning) {
        Write-Host "✓ Plugin and server are running!" -ForegroundColor Green
        Write-Host ""
        Write-Host "To test highlighting:" -ForegroundColor Yellow
        Write-Host "  1. Open a C# file in Rider" -ForegroundColor White
        Write-Host "  2. Click on a variable" -ForegroundColor White
        Write-Host "  3. Press Ctrl+Alt+F (or right-click → Show Focus Mode)" -ForegroundColor White
        Write-Host "  4. Check for highlights in the editor" -ForegroundColor White
    } elseif ($pluginInstalled -and -not $serverRunning) {
        Write-Host "⚠ Plugin installed but server not running" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Troubleshooting:" -ForegroundColor Yellow
        Write-Host "  1. Check logs above for server startup errors" -ForegroundColor White
        Write-Host "  2. Restart Rider and run this script again" -ForegroundColor White
        Write-Host "  3. Check if .NET runtime is available (self-contained should work)" -ForegroundColor White
        Write-Host "  4. Review DEBUGGING.md for detailed troubleshooting" -ForegroundColor White
    }
    
    Write-Host ""
    Write-Host "For detailed troubleshooting, see: DEBUGGING.md" -ForegroundColor Gray
    Write-Host ""
    
} catch {
    Write-Host ""
    Write-Host "✗ Error during diagnostics:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    exit 1
}
