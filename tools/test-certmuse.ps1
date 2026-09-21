[CmdletBinding()]
param(
    [switch]$SkipQualityGates,
    [switch]$SkipJsonlIntegration,
    [switch]$KeepEnvironment
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDirectory = Join-Path $root "work/e2e-reports/$runId"
$composeFiles = @(
    (Join-Path $root 'infra/docker/compose.yml'),
    (Join-Path $root 'infra/docker/compose.e2e.yml')
)
$composeArgs = @('-p', 'certmuse-e2e')
foreach ($file in $composeFiles) { $composeArgs += @('-f', $file) }
$composeUp = $false
$failed = $false

function Invoke-Checked {
    param([Parameter(Mandatory)] [string]$Name, [Parameter(Mandatory)] [scriptblock]$Action)
    Write-Host "`n== $Name ==" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE." }
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]]$Arguments)
    & docker compose @composeArgs @Arguments
}

function Assert-LastExitCode {
    param([Parameter(Mandatory)] [string]$Name)
    if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE." }
}

function Ensure-PlaywrightImage {
    $alias = 'certmuse-ci/playwright:v1.55.0-noble-amd64-56816c8b'
    & docker image inspect $alias *> $null
    if ($LASTEXITCODE -eq 0) { return }

    Write-Host "Preparing local E2E image alias $alias from the official Playwright image." -ForegroundColor Yellow
    & docker pull 'mcr.microsoft.com/playwright:v1.55.0-noble'
    if ($LASTEXITCODE -ne 0) { throw 'Unable to pull the official Playwright image required for Chromium E2E.' }
    & docker tag 'mcr.microsoft.com/playwright:v1.55.0-noble' $alias
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create the local Playwright E2E image alias.' }
}

New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
$previousReportDir = $env:CERTMUSE_E2E_HOST_REPORT_DIR
$previousResults = $env:CERTMUSE_TEST_RESULTS_DIR
$env:CERTMUSE_E2E_HOST_REPORT_DIR = "../../work/e2e-reports/$runId"
$env:CERTMUSE_TEST_RESULTS_DIR = (Join-Path $reportDirectory 'jsonl-integration')

try {
    Invoke-Checked 'E2E manifest validation' { node (Join-Path $root 'tests/e2e/validate-manifest.mjs') }
    Invoke-Checked 'Expected placeholder defect validation' { node (Join-Path $root 'tests/e2e/validate-placeholders.mjs') }
    Invoke-Checked 'Prepare Chromium E2E image' { Ensure-PlaywrightImage }

    if (-not $SkipQualityGates) {
        Invoke-Checked 'Admin typecheck, lint, unit tests and build' {
            pnpm --dir (Join-Path $root 'web/admin') run typecheck; Assert-LastExitCode 'Admin typecheck'
            pnpm --dir (Join-Path $root 'web/admin') run lint; Assert-LastExitCode 'Admin lint'
            pnpm --dir (Join-Path $root 'web/admin') run test:coverage; Assert-LastExitCode 'Admin coverage'
            pnpm --dir (Join-Path $root 'web/admin') run build; Assert-LastExitCode 'Admin build'
        }
        Invoke-Checked 'Student typecheck, lint, unit tests and build' {
            pnpm --dir (Join-Path $root 'web/student') run typecheck; Assert-LastExitCode 'Student typecheck'
            pnpm --dir (Join-Path $root 'web/student') run lint; Assert-LastExitCode 'Student lint'
            pnpm --dir (Join-Path $root 'web/student') run test:coverage; Assert-LastExitCode 'Student coverage'
            pnpm --dir (Join-Path $root 'web/student') run build; Assert-LastExitCode 'Student build'
        }
        Invoke-Checked 'Backend full Maven test suite' { Push-Location (Join-Path $root 'backend'); try { & .\mvnw.cmd '-Dmaven.test.skip=false' test } finally { Pop-Location } }
    }

    Invoke-Checked 'Isolated Compose configuration' { Invoke-Compose config --quiet }
    Invoke-Checked 'Start isolated Compose stack' { Invoke-Compose up --build --wait --wait-timeout 300 }
    $composeUp = $true

    if (-not $SkipJsonlIntegration) {
        New-Item -ItemType Directory -Force -Path $env:CERTMUSE_TEST_RESULTS_DIR | Out-Null
        Invoke-Checked 'Knowledge-point JSONL HTTP integration' { Invoke-Compose run --rm integration }
        $postgresId = (Invoke-Compose ps -q postgres).Trim()
        $minioId = (Invoke-Compose ps -q minio).Trim()
        if ([string]::IsNullOrWhiteSpace($postgresId) -or [string]::IsNullOrWhiteSpace($minioId)) {
            throw 'Isolated PostgreSQL or MinIO container was not found.'
        }
        Invoke-Checked 'Knowledge-point JSONL database integration' {
            & (Join-Path $root 'docs/testing/knowledge-point-jsonl/run-database-test.ps1') -DatabaseContainer $postgresId -MinioContainer $minioId -ResultsDirectory $env:CERTMUSE_TEST_RESULTS_DIR
        }
    }

    Invoke-Checked 'Chromium public, authentication, learner and admin E2E' { Invoke-Compose run --rm e2e }
}
catch {
    $failed = $true
    $_ | Out-String | Set-Content -LiteralPath (Join-Path $reportDirectory 'failure.txt') -Encoding utf8
    Write-Error $_
}
finally {
    if ($composeUp) {
        Invoke-Compose logs --no-color | Set-Content -LiteralPath (Join-Path $reportDirectory 'compose.log') -Encoding utf8
        if (-not $KeepEnvironment) { Invoke-Compose down --volumes --remove-orphans }
    }
    $env:CERTMUSE_E2E_HOST_REPORT_DIR = $previousReportDir
    $env:CERTMUSE_TEST_RESULTS_DIR = $previousResults
}

if ($failed) {
    throw "CertMuse test run failed. Evidence: $reportDirectory"
}
Write-Host "CertMuse test run passed. Evidence: $reportDirectory" -ForegroundColor Green
