param(
    [string]$ApkPath,
    [string]$ReleaseDir,
    [string]$Serial,
    [switch]$SkipDevice,
    [switch]$StrictExitCode
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$results = New-Object System.Collections.Generic.List[object]
$hadFailure = $false

function Add-CheckResult {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Details,
        [switch]$Warning
    )

    $status = if ($Passed) { "PASS" } elseif ($Warning) { "WARN" } else { "FAIL" }
    $color = switch ($status) {
        "PASS" { "Green" }
        "WARN" { "Yellow" }
        default { "Red" }
    }

    if (-not $Passed -and -not $Warning) {
        $script:hadFailure = $true
    }

    $entry = [pscustomobject]@{
        Status  = $status
        Name    = $Name
        Details = $Details
    }
    $script:results.Add($entry) | Out-Null
    Write-Host "[$status] $Name - $Details" -ForegroundColor $color
}

function Get-PropertyValue {
    param(
        [string]$FilePath,
        [string]$PropertyName
    )

    $content = [System.IO.File]::ReadAllText($FilePath)
    $regex = "(?m)^\s*{0}\s*=\s*(.+?)\s*$" -f [regex]::Escape($PropertyName)
    $match = [regex]::Match($content, $regex)
    if (-not $match.Success) {
        throw "Property '$PropertyName' not found in $FilePath"
    }
    return $match.Groups[1].Value.Trim()
}

function Get-FirstRegexGroup {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$Description
    )

    $match = [regex]::Match($Text, $Pattern)
    if (-not $match.Success) {
        throw "Could not find $Description"
    }
    return $match.Groups[1].Value
}

function Get-LatestReleaseArtifact {
    param([string]$RepoRoot)

    $releasesRoot = Join-Path $RepoRoot "releases"
    if (Test-Path $releasesRoot) {
        $releaseDir = Get-ChildItem -Path $releasesRoot -Directory |
            Where-Object { $_.Name -match '^\d{8}$' } |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($releaseDir) {
            $releaseApk = Join-Path $releaseDir.FullName "app-release.apk"
            if (Test-Path $releaseApk) {
                return [pscustomobject]@{
                    ApkPath    = $releaseApk
                    ArtifactDir = $releaseDir.FullName
                    Source     = "daily release folder"
                }
            }
        }
    }

    $fallbackApk = Join-Path $RepoRoot "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $fallbackApk) {
        return [pscustomobject]@{
            ApkPath    = $fallbackApk
            ArtifactDir = Split-Path -Parent $fallbackApk
            Source     = "app build output"
        }
    }

    throw "No release APK found in releases\\YYYYMMDD or app\\build\\outputs\\apk\\release"
}

function Resolve-AdbPath {
    param([string]$RepoRoot)

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

    throw "Could not find adb.exe. Install Android SDK Platform-Tools or add adb to PATH."
}

function Get-ConnectedDeviceSerials {
    param([string]$Adb)

    $output = & $Adb devices
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed. Output:`n$($output -join [Environment]::NewLine)"
    }

    return @(
        $output |
            Select-Object -Skip 1 |
            Where-Object { $_ -match '\tdevice$' } |
            ForEach-Object { ($_ -split "\t")[0].Trim() } |
            Where-Object { $_ }
    )
}

try {
    $gradlePropertiesPath = Join-Path $repoRoot "gradle.properties"
    $buildGradlePath = Join-Path $repoRoot "app\build.gradle.kts"
    $stringsPath = Join-Path $repoRoot "app\src\main\res\values\strings.xml"

    $expectedBuildCounter = [int](Get-PropertyValue -FilePath $gradlePropertiesPath -PropertyName "VALUEFINDER_BUILD_COUNTER")
    $versionBase = Get-PropertyValue -FilePath $gradlePropertiesPath -PropertyName "VALUEFINDER_VERSION_BASE"
    $expectedVersionName = "$versionBase.$expectedBuildCounter"

    $buildGradleText = [System.IO.File]::ReadAllText($buildGradlePath)
    $packageName = Get-FirstRegexGroup -Text $buildGradleText -Pattern 'applicationId\s*=\s*"([^"]+)"' -Description "applicationId in app/build.gradle.kts"

    $stringsText = [System.IO.File]::ReadAllText($stringsPath)
    $appName = Get-FirstRegexGroup -Text $stringsText -Pattern '<string\s+name="app_name">([^<]+)</string>' -Description "app_name in strings.xml"
    $expectedUiVersionText = "$expectedVersionName (Build $expectedBuildCounter)"

    $artifact = if ($ApkPath) {
        $apkCandidatePath = if ([System.IO.Path]::IsPathRooted($ApkPath)) { $ApkPath } else { Join-Path $repoRoot $ApkPath }
        $resolvedApk = Resolve-Path -Path $apkCandidatePath
        [pscustomobject]@{
            ApkPath    = $resolvedApk.Path
            ArtifactDir = Split-Path -Parent $resolvedApk.Path
            Source     = "explicit apk path"
        }
    } elseif ($ReleaseDir) {
        $releaseDirCandidatePath = if ([System.IO.Path]::IsPathRooted($ReleaseDir)) { $ReleaseDir } else { Join-Path $repoRoot $ReleaseDir }
        $resolvedDir = Resolve-Path -Path $releaseDirCandidatePath
        $candidateApk = Join-Path $resolvedDir.Path "app-release.apk"
        if (-not (Test-Path $candidateApk)) {
            throw "app-release.apk not found in $($resolvedDir.Path)"
        }
        [pscustomobject]@{
            ApkPath    = $candidateApk
            ArtifactDir = $resolvedDir.Path
            Source     = "explicit release directory"
        }
    } else {
        Get-LatestReleaseArtifact -RepoRoot $repoRoot
    }

    $apk = $artifact.ApkPath
    $artifactDir = $artifact.ArtifactDir
    $hashPath = Join-Path $artifactDir "app-release.apk.sha256.txt"
    $metadataPath = Join-Path $artifactDir "app-release.built-at.txt"

    Add-CheckResult -Name "Artifact source" -Passed $true -Details "$($artifact.Source): $apk"

    if (Test-Path $apk) {
        $apkItem = Get-Item $apk
        Add-CheckResult -Name "APK exists" -Passed $true -Details ("{0} | modified {1}" -f $apkItem.FullName, $apkItem.LastWriteTime)
    } else {
        Add-CheckResult -Name "APK exists" -Passed $false -Details $apk
        throw "Release APK missing"
    }

    if (Test-Path $hashPath) {
        $computedHash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToUpperInvariant()
        $hashText = [System.IO.File]::ReadAllText($hashPath)
        $storedHash = Get-FirstRegexGroup -Text $hashText -Pattern 'SHA256\s+([A-Fa-f0-9]+)' -Description "SHA256 value in hash file"
        if ($storedHash.ToUpperInvariant() -eq $computedHash) {
            Add-CheckResult -Name "SHA-256 hash" -Passed $true -Details $computedHash
        } else {
            Add-CheckResult -Name "SHA-256 hash" -Passed $false -Details "Expected $computedHash but hash file contained $storedHash"
        }
    } else {
        Add-CheckResult -Name "SHA-256 hash file" -Passed $false -Details "Missing $hashPath"
    }

    if (Test-Path $metadataPath) {
        $metadataText = [System.IO.File]::ReadAllText($metadataPath)
        $hasLocalTimestamp = $metadataText -match 'Built at \(local\):'
        $hasExpectedCounter = $metadataText -match ("APK build counter:\s*{0}\b" -f [regex]::Escape($expectedBuildCounter.ToString()))
        if ($hasLocalTimestamp -and $hasExpectedCounter) {
            Add-CheckResult -Name "Build metadata" -Passed $true -Details $metadataPath
        } else {
            Add-CheckResult -Name "Build metadata" -Passed $false -Details "Metadata file exists but is missing expected timestamp/counter details"
        }
    } else {
        Add-CheckResult -Name "Build metadata" -Passed $false -Details "Missing $metadataPath"
    }

    Add-CheckResult -Name "Expected release version" -Passed $true -Details "$packageName $expectedVersionName (Build $expectedBuildCounter)"

    if (-not $SkipDevice) {
        try {
            $adb = Resolve-AdbPath -RepoRoot $repoRoot
            Add-CheckResult -Name "adb" -Passed $true -Details $adb

            $serials = @(Get-ConnectedDeviceSerials -Adb $adb)
            if ($Serial) {
                if ($serials -contains $Serial) {
                    $targetSerial = $Serial
                } else {
                    Add-CheckResult -Name "Connected device" -Passed $false -Details "Requested serial $Serial not connected"
                    $targetSerial = $null
                }
            } elseif ($serials.Count -eq 1) {
                $targetSerial = $serials[0]
            } elseif ($serials.Count -eq 0) {
                Add-CheckResult -Name "Connected device" -Passed $false -Details "No adb device connected" -Warning
                $targetSerial = $null
            } else {
                Add-CheckResult -Name "Connected device" -Passed $false -Details "Multiple devices connected: $($serials -join ', '). Re-run with -Serial." -Warning
                $targetSerial = $null
            }

            if ($targetSerial) {
                Add-CheckResult -Name "Connected device" -Passed $true -Details $targetSerial

                $installOutput = & $adb -s $targetSerial install -r $apk 2>&1
                if ($LASTEXITCODE -eq 0) {
                    Add-CheckResult -Name "Install APK to device" -Passed $true -Details (($installOutput | Out-String).Trim())
                } else {
                    Add-CheckResult -Name "Install APK to device" -Passed $false -Details (($installOutput | Out-String).Trim())
                }

                $packageDump = & $adb -s $targetSerial shell dumpsys package $packageName 2>&1
                if ($LASTEXITCODE -eq 0) {
                    $packageDumpText = $packageDump | Out-String
                    $deviceVersionCode = Get-FirstRegexGroup -Text $packageDumpText -Pattern 'versionCode=(\d+)' -Description "installed versionCode"
                    $deviceVersionName = Get-FirstRegexGroup -Text $packageDumpText -Pattern 'versionName=([^\s\r\n]+)' -Description "installed versionName"
                    $versionOk = ($deviceVersionCode -eq $expectedBuildCounter.ToString()) -and ($deviceVersionName -eq $expectedVersionName)
                    if ($versionOk) {
                        Add-CheckResult -Name "Installed package version" -Passed $true -Details "versionName=$deviceVersionName versionCode=$deviceVersionCode"
                    } else {
                        Add-CheckResult -Name "Installed package version" -Passed $false -Details "Expected versionName=$expectedVersionName versionCode=$expectedBuildCounter but found versionName=$deviceVersionName versionCode=$deviceVersionCode"
                    }
                } else {
                    Add-CheckResult -Name "Installed package version" -Passed $false -Details (($packageDump | Out-String).Trim())
                }

                $launchOutput = & $adb -s $targetSerial shell am start -n "$packageName/.MainActivity" 2>&1
                if ($LASTEXITCODE -eq 0) {
                    Add-CheckResult -Name "Launch app" -Passed $true -Details (($launchOutput | Out-String).Trim())
                } else {
                    Add-CheckResult -Name "Launch app" -Passed $false -Details (($launchOutput | Out-String).Trim())
                }

                Start-Sleep -Seconds 2
                $remoteUiDump = "/sdcard/valuepics_verify_ui.xml"
                $localUiDump = Join-Path $repoRoot "build\valuepics_verify_ui.xml"
                New-Item -ItemType Directory -Path (Split-Path -Parent $localUiDump) -Force | Out-Null

                $dumpCommand = '"{0}" -s {1} shell uiautomator dump {2} 2>&1' -f $adb, $targetSerial, $remoteUiDump
                $dumpOutput = & cmd.exe /d /c $dumpCommand
                $dumpExitCode = $LASTEXITCODE
                $pullCommand = '"{0}" -s {1} pull "{2}" "{3}" 2>&1' -f $adb, $targetSerial, $remoteUiDump, $localUiDump
                $pullOutput = & cmd.exe /d /c $pullCommand
                $pullExitCode = $LASTEXITCODE
                if ($dumpExitCode -eq 0 -and $pullExitCode -eq 0 -and (Test-Path $localUiDump)) {
                    $uiText = [System.IO.File]::ReadAllText($localUiDump)
                    $hasAppName = $uiText.Contains("text=`"$appName`"")
                    $hasVersionText = $uiText.Contains("text=`"$expectedUiVersionText`"")

                    if ($hasAppName) {
                        Add-CheckResult -Name "UI title text" -Passed $true -Details $appName
                    } else {
                        Add-CheckResult -Name "UI title text" -Passed $false -Details "Could not find '$appName' in UI dump"
                    }

                    if ($hasVersionText) {
                        Add-CheckResult -Name "UI version/build text" -Passed $true -Details $expectedUiVersionText
                    } else {
                        Add-CheckResult -Name "UI version/build text" -Passed $false -Details "Could not find '$expectedUiVersionText' in UI dump"
                    }
                } else {
                    $combinedUiOutput = (($dumpOutput | Out-String).Trim() + "`n" + ($pullOutput | Out-String).Trim()).Trim()
                    Add-CheckResult -Name "UI dump" -Passed $false -Details $combinedUiOutput
                }
            }
        } catch {
            Add-CheckResult -Name "Device verification" -Passed $false -Details $_.Exception.Message -Warning
        }
    } else {
        Add-CheckResult -Name "Device verification" -Passed $true -Details "Skipped by -SkipDevice"
    }

    Write-Host ""
    Write-Host "Verification summary" -ForegroundColor Cyan
    $results | ForEach-Object {
        Write-Host (" - [{0}] {1}: {2}" -f $_.Status, $_.Name, $_.Details)
    }

    if ($hadFailure) {
        throw "Release verification failed. Review the failed checks above."
    }

    Write-Host ""
    Write-Host "Release verification passed." -ForegroundColor Green
    if ($StrictExitCode) {
        exit 0
    }
    [Environment]::ExitCode = 0
    return
} catch {
    if (-not $hadFailure) {
        Write-Host "[FAIL] Verification aborted - $($_.Exception.Message)" -ForegroundColor Red
    }
    if ($StrictExitCode) {
        exit 1
    }
    [Environment]::ExitCode = 1
    return
}

