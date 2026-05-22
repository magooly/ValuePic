param(
    [switch]$SkipConnectedTests,
    [switch]$SkipUnitTests,
    [switch]$FailOnDirtyGit,
    [switch]$CopyArtifacts,
    [switch]$SkipVerify,
    [switch]$VerifyDevice,
    [string]$VerifySerial = $null
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkPath = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
$hashPath = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk.sha256.txt"
$timestampPath = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.built-at.txt"
$releasesRoot = Join-Path $repoRoot "releases"
$gradlePropertiesPath = Join-Path $repoRoot "gradle.properties"
$buildCounterProperty = "VALUEFINDER_BUILD_COUNTER"
$verifyScriptPath = Join-Path $repoRoot "verify-release.ps1"

function Get-BuildCounter {
    param(
        [string]$PropertiesPath,
        [string]$PropertyName
    )

    if (-not (Test-Path $PropertiesPath)) {
        throw "Missing gradle properties file: $PropertiesPath"
    }

    $content = [System.IO.File]::ReadAllText($PropertiesPath)
    $regex = "(?m)^\s*{0}\s*=\s*(\d+)\s*$" -f [regex]::Escape($PropertyName)
    $match = [regex]::Match($content, $regex)
    if (-not $match.Success) { return 1 }
    return [int]$match.Groups[1].Value
}

function Set-BuildCounter {
    param(
        [string]$PropertiesPath,
        [string]$PropertyName,
        [int]$Counter
    )

    $content = [System.IO.File]::ReadAllText($PropertiesPath)
    $regex = "(?m)^\s*{0}\s*=\s*\d+\s*$" -f [regex]::Escape($PropertyName)
    $replacement = "{0}={1}" -f $PropertyName, $Counter

    if ([regex]::IsMatch($content, $regex)) {
        $updatedContent = [regex]::Replace($content, $regex, $replacement, 1)
    } else {
        $separator = if ($content.EndsWith("`r`n") -or $content.EndsWith("`n")) { "" } else { [Environment]::NewLine }
        $updatedContent = "$content$separator$replacement$([Environment]::NewLine)"
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($PropertiesPath, $updatedContent, $utf8NoBom)
}

function Invoke-Gradle {
    param([string[]]$Arguments)

    & .\gradlew @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle command failed: gradlew $($Arguments -join ' ')"
    }
}

function Invoke-ReleaseVerification {
    param(
        [string]$VerifyScriptPath,
        [string]$RepoRoot,
        [string]$ApkPath,
        [string]$ReleaseCopyPath,
        [switch]$VerifyDevice,
        [string]$VerifySerial
    )

    if (-not (Test-Path $VerifyScriptPath)) {
        throw "Release verification script not found: $VerifyScriptPath"
    }

    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $VerifyScriptPath)
    if ($ReleaseCopyPath) {
        $arguments += @('-ReleaseDir', $ReleaseCopyPath)
    } else {
        $arguments += @('-ApkPath', $ApkPath)
    }

    if (-not $VerifyDevice) {
        $arguments += '-SkipDevice'
    }

    if ($VerifySerial) {
        $arguments += @('-Serial', $VerifySerial)
    }

    $arguments += '-StrictExitCode'

    & powershell @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Release verification failed."
    }
}

Push-Location $repoRoot
try {
    $step = 1
    $totalSteps = 2
    if (-not $SkipUnitTests) { $totalSteps += 1 }
    if (-not $SkipConnectedTests) { $totalSteps += 1 }
    if (-not $SkipVerify) { $totalSteps += 1 }

    if ((Get-Command git -ErrorAction SilentlyContinue) -and (Test-Path (Join-Path $repoRoot ".git"))) {
        & git --no-pager rev-parse --is-inside-work-tree *> $null
        if ($LASTEXITCODE -eq 0) {
            $statusOutput = & git --no-pager status --porcelain 2>$null
            if ($statusOutput) {
                if ($FailOnDirtyGit) {
                    throw "Git working tree is dirty. Commit/stash changes or rerun without -FailOnDirtyGit."
                }
                Write-Warning "Git working tree has uncommitted changes."
            }
        }
    }

    if (-not $SkipUnitTests) {
        Write-Host "[$step/$totalSteps] Running unit tests..."
        Invoke-Gradle -Arguments @(':app:testDebugUnitTest')
        $step += 1
    } else {
        Write-Host "[skip] Unit tests skipped"
    }

    if (-not $SkipConnectedTests) {
        Write-Host "[$step/$totalSteps] Running instrumentation tests..."
        Invoke-Gradle -Arguments @(':app:connectedDebugAndroidTest')
        $step += 1
    } else {
        Write-Host "[skip] Instrumentation tests skipped"
    }

    Write-Host "[$step/$totalSteps] Building release APK..."
    $currentBuildCounter = Get-BuildCounter -PropertiesPath $gradlePropertiesPath -PropertyName $buildCounterProperty
    $nextBuildCounter = $currentBuildCounter + 1
    Write-Host "Using APK build counter: $nextBuildCounter"
    Invoke-Gradle -Arguments @("-P$buildCounterProperty=$nextBuildCounter", 'assembleRelease')
    $step += 1

    Set-BuildCounter -PropertiesPath $gradlePropertiesPath -PropertyName $buildCounterProperty -Counter $nextBuildCounter

    if (-not (Test-Path $apkPath)) {
        throw "Release APK not found: $apkPath"
    }

    Write-Host "[$step/$totalSteps] Calculating SHA-256..."
    $hash = (Get-FileHash $apkPath -Algorithm SHA256).Hash
    "SHA256 $hash" | Set-Content -Path $hashPath -Encoding UTF8
    $builtAtLocal = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
    $builtAtUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd HH:mm:ss 'UTC'")
    $apkLastWrite = (Get-Item $apkPath).LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss zzz")
    @(
        "Release build metadata"
        "Built at (local): $builtAtLocal"
        "Built at (UTC):   $builtAtUtc"
        "APK last write:   $apkLastWrite"
        "APK path:         $apkPath"
        "APK build counter: $nextBuildCounter"
    ) | Set-Content -Path $timestampPath -Encoding UTF8
    $step += 1

    $releaseCopyPath = $null
    if ($CopyArtifacts) {
        # Keep one release snapshot per day; same-day runs overwrite this folder.
        $dayStamp = Get-Date -Format "yyyyMMdd"
        $releaseDir = Join-Path $releasesRoot $dayStamp
        New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
        Copy-Item -Path $apkPath -Destination (Join-Path $releaseDir "app-release.apk") -Force
        Copy-Item -Path $hashPath -Destination (Join-Path $releaseDir "app-release.apk.sha256.txt") -Force
        Copy-Item -Path $timestampPath -Destination (Join-Path $releaseDir "app-release.built-at.txt") -Force
        $releaseCopyPath = $releaseDir
    }

    if (-not $SkipVerify) {
        Write-Host "[$step/$totalSteps] Verifying release artifacts..."
        Invoke-ReleaseVerification `
            -VerifyScriptPath $verifyScriptPath `
            -RepoRoot $repoRoot `
            -ApkPath $apkPath `
            -ReleaseCopyPath $releaseCopyPath `
            -VerifyDevice:$VerifyDevice `
            -VerifySerial $VerifySerial
        $step += 1
    } else {
        Write-Host "[skip] Release verification skipped"
    }

    Write-Host "Done."
    Write-Host "APK: $apkPath"
    Write-Host "SHA256: $hash"
    Write-Host "Saved hash file: $hashPath"
    Write-Host "Saved build timestamp file: $timestampPath"
    Write-Host "APK build counter: $nextBuildCounter"
    if ($releaseCopyPath) {
        Write-Host "Copied release artifacts to: $releaseCopyPath"
    }
}
finally {
    Pop-Location
}

