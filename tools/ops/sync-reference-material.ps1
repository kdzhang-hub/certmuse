[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('push', 'pull')]
    [string]$Operation,
    [Parameter(Mandatory = $true, Position = 1)]
    [ValidateSet('source', 'work', 'archive')]
    [string]$Scope,
    [Parameter(Mandatory = $true, Position = 2)]
    [string]$RelativeFile
)

$ErrorActionPreference = 'Stop'
$scriptDir = $PSScriptRoot
$projectDir = Split-Path -Parent (Split-Path -Parent $scriptDir)
$srvctl = Join-Path $scriptDir 'local/srv-ops/bin/srvctl.exe'
$config = Join-Path $scriptDir 'local/srv-ops/.srv-ops-local/config.yaml'
$envFile = Join-Path $scriptDir 'local/srv-ops/.srv-ops-local/env/passwords.env'
$server = 'certmuse-app'
$remoteRoot = '/app/certmuse-references'

if ([IO.Path]::IsPathRooted($RelativeFile) -or $RelativeFile -match '(^|[\\/])\.\.([\\/]|$)') {
    throw 'RelativeFile must stay below its selected scope.'
}
if (-not (Test-Path -LiteralPath $srvctl -PathType Leaf)) {
    throw 'srvctl is not installed; run .\tools\ops\certmusectl.ps1 doctor'
}
if (-not (Test-Path -LiteralPath $config -PathType Leaf)) {
    throw 'No local CertMuse srv-ops profile is available.'
}

$localPath = Join-Path $projectDir (Join-Path "docs/references/$Scope" $RelativeFile)
$remoteFile = $RelativeFile -replace '\\', '/'
$remotePath = "$remoteRoot/$Scope/$remoteFile"

if ($Operation -eq 'push') {
    if (-not (Test-Path -LiteralPath $localPath -PathType Leaf)) {
        throw "Local regular file not found: $localPath"
    }
    & $srvctl -config $config -env $envFile policy check $server --write $remotePath
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $srvctl -config $config -env $envFile upload $server $localPath $remotePath
    exit $LASTEXITCODE
}

& $srvctl -config $config -env $envFile policy check $server --read $remotePath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $localPath) | Out-Null
& $srvctl -config $config -env $envFile download $server $remotePath $localPath
exit $LASTEXITCODE
