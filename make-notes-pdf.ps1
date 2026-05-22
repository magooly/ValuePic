param(
    [string]$PythonExe = "python"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $repoRoot
try {
    & $PythonExe -u ".\_make_notes_pdf.py"
    if ($LASTEXITCODE -ne 0) {
        throw "PDF generation script failed with exit code $LASTEXITCODE"
    }

    $expected = @(
        "RELEASE_NOTES.pdf",
        "RELEASE_NOTES_CLIENT.pdf",
        "app\src\main\assets\how_to.pdf"
    )

    $failed = $false
    foreach ($relativePath in $expected) {
        $fullPath = Join-Path $repoRoot $relativePath
        if (-not (Test-Path $fullPath)) {
            Write-Error "Missing PDF: $relativePath"
            $failed = $true
            continue
        }

        $file = Get-Item $fullPath
        if ($file.Length -le 0) {
            Write-Error "Empty PDF: $relativePath"
            $failed = $true
            continue
        }

        Write-Host "OK  $relativePath ($($file.Length) bytes)"
    }

    if ($failed) {
        throw "One or more PDFs failed verification."
    }

    Write-Host "All notes PDFs generated and verified successfully."
}
finally {
    Pop-Location
}

