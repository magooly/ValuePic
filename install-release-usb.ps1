param(
    [string]$ApkPath = "app\build\outputs\apk\release\app-release.apk",
    [string]$AdbPath = $null,
    [string]$Serial = $null,
    [string]$PackageName = "com.example.valuefinder.unified",
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    param([string]$ConfiguredAdbPath, [string]$RepoRoot)

    if ($ConfiguredAdbPath) {
        $resolved = Resolve-Path -Path $ConfiguredAdbPath -ErrorAction SilentlyContinue
        if ($resolved) { return $resolved.Path }
        throw "Provided -AdbPath was not found: $ConfiguredAdbPath"
    }

    $adbFromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbFromPath) {
        return $adbFromPath.Source
    }

    $sdkDir = $null
    $localPropertiesPath = Join-Path $RepoRoot "local.properties"
    if (Test-Path $localPropertiesPath) {
        $line = Get-Content $localPropertiesPath | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $sdkDir = ($line -replace '^sdk\.dir=', '').Trim()
            $sdkDir = $sdkDir -replace '\\\\', '\'
            $sdkDir = $sdkDir -replace '\\:', ':'
        }
    }

    $candidateSdkRoots = @(
        $sdkDir,
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($root in $candidateSdkRoots) {
        $candidate = Join-Path $root "platform-tools\adb.exe"
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Could not find adb.exe. Install Android SDK Platform-Tools or pass -AdbPath explicitly."
}

function Get-ConnectedDeviceSerials {
    param([string]$Adb)

    $output = & $Adb devices
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed. Output:`n$($output -join [Environment]::NewLine)"
    }

    return $output |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '\tdevice$' } |
        ForEach-Object { ($_ -split "\t")[0].Trim() } |
        Where-Object { $_ }
}

function Prompt-ForDeviceSerial {
    param([string[]]$Serials)

    if ($Serials.Count -eq 1) {
        return $Serials[0]
    }

    Write-Host "Multiple devices detected. Select target device:"
    for ($i = 0; $i -lt $Serials.Count; $i++) {
        Write-Host ("  [{0}] {1}" -f ($i + 1), $Serials[$i])
    }

    while ($true) {
        $raw = Read-Host "Enter device number"
        $index = 0
        if ([int]::TryParse($raw, [ref]$index) -and $index -ge 1 -and $index -le $Serials.Count) {
            return $Serials[$index - 1]
        }
        Write-Host "Invalid selection. Enter a number between 1 and $($Serials.Count)."
    }
}

function Is-SignatureConflictInstallError {
    param([string[]]$InstallOutput)

    $joined = ($InstallOutput -join "`n").ToLowerInvariant()
    return $joined.Contains("install_failed_update_incompatible") -or
        $joined.Contains("package conflicts with an existing package") -or
        $joined.Contains("signatures do not match")
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkResolved = Resolve-Path -Path (Join-Path $repoRoot $ApkPath) -ErrorAction SilentlyContinue
if (-not $apkResolved) {
    throw "Release APK not found at: $(Join-Path $repoRoot $ApkPath). Build first with .\gradlew.bat :app:assembleRelease"
}
$apk = $apkResolved.Path
$adb = Resolve-AdbPath -ConfiguredAdbPath $AdbPath -RepoRoot $repoRoot

$adbPrefix = @()
if ($Serial) {
    $adbPrefix += "-s"
    $adbPrefix += $Serial
}

if ($DryRun) {
    Write-Host "[DRY RUN] adb path: $adb"
    Write-Host "[DRY RUN] apk path: $apk"
    Write-Host "[DRY RUN] package: $PackageName"
    if ($Serial) {
        Write-Host "[DRY RUN] target serial: $Serial"
    }
    Write-Host "[DRY RUN] command: `"$adb`" $($adbPrefix -join ' ') install -r `"$apk`""
    exit 0
}

$serials = @(Get-ConnectedDeviceSerials -Adb $adb)
if ($serials.Count -eq 0) {
    throw "No device detected in adb. Ensure USB debugging is enabled and device authorization is accepted."
}

if (-not $Serial) {
    $Serial = Prompt-ForDeviceSerial -Serials $serials
    $adbPrefix = @("-s", $Serial)
} elseif ($serials -notcontains $Serial) {
    throw "Requested serial '$Serial' is not connected. Connected devices: $($serials -join ', ')."
}

$installOutput = & $adb @adbPrefix install -r $apk
if ($LASTEXITCODE -eq 0) {
    Write-Host "Install completed successfully."
    Write-Host ($installOutput -join [Environment]::NewLine)
    exit 0
}

if (-not (Is-SignatureConflictInstallError -InstallOutput $installOutput)) {
    throw "Install failed. adb output:`n$($installOutput -join [Environment]::NewLine)"
}

Write-Host "Update failed due to package/signing conflict for $PackageName on $Serial."
Write-Host ($installOutput -join [Environment]::NewLine)
$confirm = (Read-Host "Uninstall existing app and reinstall now? (y/N)").Trim().ToLowerInvariant()
if ($confirm -notin @("y", "yes")) {
    throw "Install stopped by user. Existing app was not removed."
}

$uninstallOutput = & $adb @adbPrefix uninstall $PackageName
if ($LASTEXITCODE -ne 0) {
    throw "Uninstall failed for $PackageName on $Serial. adb output:`n$($uninstallOutput -join [Environment]::NewLine)"
}

Write-Host "Uninstall completed. Installing fresh APK..."
$reinstallOutput = & $adb @adbPrefix install $apk
if ($LASTEXITCODE -ne 0) {
    throw "Reinstall failed. adb output:`n$($reinstallOutput -join [Environment]::NewLine)"
}

Write-Host "Reinstall completed successfully."
Write-Host ($reinstallOutput -join [Environment]::NewLine)

