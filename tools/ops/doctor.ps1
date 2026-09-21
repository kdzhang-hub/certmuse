[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtimeDir = Join-Path $projectRoot 'tools/ops/local'
$status = 0

function Test-RequiredPath {
    param([string]$Label, [string]$Path)
    if (Test-Path $Path) {
        Write-Host "[ok] ${Label}: $Path"
    } else {
        Write-Error "[missing] ${Label}: $Path"
        $script:status = 1
    }
}

$srvctl = Join-Path $runtimeDir 'srv-ops/bin/srvctl.exe'
$deployer = Join-Path $runtimeDir 'routeflow/tools/deployer/deployer.exe'
Test-RequiredPath 'srv-ops runtime' $srvctl
Test-RequiredPath 'RouteFlow runtime' $deployer

if (Test-Path (Join-Path $runtimeDir 'srv-ops/.srv-ops-local')) {
    Write-Host '[ok] srv-ops private profile is present'
} else {
    Write-Host '[notice] srv-ops private profile has not been imported'
}

$deployerConfig = Join-Path $runtimeDir 'routeflow/tools/deployer/certmuse.yml'
$hostProfile = Join-Path $runtimeDir 'routeflow/platform/linux/hosts/certmuse-app/host.yml'
if ((Test-Path $deployerConfig) -and (Test-Path $hostProfile)) {
    Write-Host '[ok] CertMuse Deployer private config and host profile are present'
} else {
    Write-Host '[notice] CertMuse Deployer private config or host profile has not been imported'
}

if (Test-Path $srvctl) {
    & $srvctl --help | Out-Null
    Write-Host '[ok] srvctl starts'
}

exit $status
