param(
    [string]$StoreFile = (Join-Path $PSScriptRoot "valuefinder-release-new.jks"),
    [Parameter(Mandatory = $true)]
    [string]$StorePassword,
    [string]$KeyAlias = "valuefinder",
    [string]$KeyPassword,
    [switch]$PersistUser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($KeyPassword)) {
    $KeyPassword = $StorePassword
}

if (-not (Test-Path $StoreFile)) {
    throw "Keystore file not found: $StoreFile"
}

$vars = @{
    "VALUEFINDER_STORE_FILE" = $StoreFile
    "VALUEFINDER_STORE_PASSWORD" = $StorePassword
    "VALUEFINDER_KEY_ALIAS" = $KeyAlias
    "VALUEFINDER_KEY_PASSWORD" = $KeyPassword
}

foreach ($entry in $vars.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
    if ($PersistUser) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "User")
    }
}

Write-Host "Signing variables set for current process."
if ($PersistUser) {
    Write-Host "Signing variables also saved at User scope."
}

Write-Host "STORE_FILE=$StoreFile"
Write-Host "KEY_ALIAS=$KeyAlias"
Write-Host "STORE_PASSWORD_SET=$(-not [string]::IsNullOrWhiteSpace($StorePassword))"
Write-Host "KEY_PASSWORD_SET=$(-not [string]::IsNullOrWhiteSpace($KeyPassword))"

