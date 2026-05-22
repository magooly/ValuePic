# PowerShell script to monitor Android logcat for restore operations
# Usage: .\monitor_restore_logs.ps1

$adbPath = "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"

Write-Host "Starting logcat monitoring for restore operations..." -ForegroundColor Green
Write-Host "Press Ctrl+C to stop monitoring" -ForegroundColor Yellow

# Clear existing log buffer
& $adbPath logcat -c

# Start monitoring with filters for our app and restore-related errors
& $adbPath logcat | Where-Object {
    $_ -match "ValuePicsRepository|ValueFinder|AndroidRuntime|FATAL|ERROR|Exception" -or 
    $_ -match "restore|merge|backup"
} | ForEach-Object {
    $timestamp = Get-Date -Format "HH:mm:ss.fff"
    $color = if ($_ -match "FATAL|ERROR|Exception") { "Red" } 
             elseif ($_ -match "WARN") { "Yellow" } 
             else { "White" }
    
    Write-Host "[$timestamp] $_" -ForegroundColor $color
}
