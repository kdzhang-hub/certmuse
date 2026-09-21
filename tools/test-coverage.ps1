[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Invoke-Checked {
    param(
        [Parameter(Mandatory)] [string] $Title,
        [Parameter(Mandatory)] [string] $Directory,
        [Parameter(Mandatory)] [scriptblock] $Command
    )

    Write-Host "`n== $Title ==" -ForegroundColor Cyan
    Push-Location $Directory
    try {
        & $Command
        if ($LASTEXITCODE -ne 0) {
            throw "$Title failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

Invoke-Checked 'Admin frontend tests and coverage' (Join-Path $root 'web/admin') { pnpm test:coverage }
Invoke-Checked 'Student frontend tests and coverage' (Join-Path $root 'web/student') { pnpm test:coverage }
Invoke-Checked 'CertMuse backend tests and coverage' (Join-Path $root 'backend') {
    & .\mvnw.cmd '-Dmaven.test.skip=false' clean test
}

Write-Host "`nAll CertMuse test suites passed." -ForegroundColor Green
Write-Host "Admin coverage:    $root\web\admin\coverage\index.html"
Write-Host "Student coverage:  $root\web\student\coverage\index.html"
Write-Host "Backend coverage:  $root\backend\ruoyi-modules\ruoyi-certmuse\target\site\jacoco\index.html"
