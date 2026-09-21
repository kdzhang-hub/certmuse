[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$lockFile = Join-Path $projectRoot 'infra/ops/agent-tools.lock'
$runtimeDir = Join-Path $projectRoot 'tools/ops/local'
$lock = @{}

Get-Content $lockFile | ForEach-Object {
    if ($_ -match '^([A-Z0-9_]+)=(.+)$') {
        $lock[$matches[1]] = $matches[2]
    }
}

function Get-VerifiedArchive {
    param(
        [string]$RelativePath,
        [string]$Archive,
        [string]$ExpectedSha
    )

    $downloads = Join-Path $runtimeDir 'downloads'
    $destination = Join-Path $downloads $Archive
    New-Item -ItemType Directory -Force -Path $downloads | Out-Null
    Invoke-WebRequest -Uri "$($lock.AGENT_TOOLS_BASE_URL)/$RelativePath/$($lock.AGENT_TOOLS_RELEASE)/$Archive" -OutFile $destination

    $actualSha = (Get-FileHash -Algorithm SHA256 -Path $destination).Hash.ToLowerInvariant()
    if ($actualSha -ne $ExpectedSha.ToLowerInvariant()) {
        Remove-Item -Force $destination
        throw "SHA-256 mismatch for $Archive"
    }
    Expand-Archive -Path $destination -DestinationPath $runtimeDir -Force
}

Get-VerifiedArchive 'srv-ops' $lock.SRV_OPS_WINDOWS_AMD64_ARCHIVE $lock.SRV_OPS_WINDOWS_AMD64_SHA256
Get-VerifiedArchive 'routeflow-deployer' $lock.ROUTEFLOW_WINDOWS_AMD64_ARCHIVE $lock.ROUTEFLOW_WINDOWS_AMD64_SHA256

Write-Host "Installed public Windows amd64 agent tools under $runtimeDir"
Write-Host 'Next: import the authorized srv-ops profile and RouteFlow private overlay into that ignored directory.'
