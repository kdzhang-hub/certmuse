[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ComposeDirectory,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$SourceRevision,
    [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'
$ComposeDirectory = (Resolve-Path -LiteralPath $ComposeDirectory).Path

function Invoke-DockerText([string[]]$Arguments) {
    $output = @(& docker @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    return ($output -join '').Trim()
}

function Assert-ServiceUsesLatestImage([string]$Service) {
    $containerId = Invoke-DockerText @('compose', '--project-directory', $ComposeDirectory, 'ps', '-q', $Service)
    if (-not $containerId) {
        throw "The $Service container is not running."
    }

    $runningImage = Invoke-DockerText @('inspect', $containerId, '--format', '{{.Image}}')
    $expectedImage = Invoke-DockerText @('image', 'inspect', "certmuse-$($Service):latest", '--format', '{{.Id}}')
    if ($runningImage -ne $expectedImage) {
        throw "The $Service container still uses $runningImage instead of the newly built $expectedImage image."
    }

    $imageJson = @(& docker image inspect "certmuse-$($Service):latest" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the $Service image revision.`n$($imageJson -join [Environment]::NewLine)"
    }
    $imageRevision = (($imageJson -join [Environment]::NewLine) | ConvertFrom-Json)[0].Config.Labels.'org.opencontainers.image.revision'
    if ($imageRevision -ne $SourceRevision) {
        throw "The $Service image contains source revision $imageRevision instead of $SourceRevision."
    }

    Write-Host "[OK] $Service uses image $expectedImage built from source revision $SourceRevision"
}

Assert-ServiceUsesLatestImage 'backend'
Assert-ServiceUsesLatestImage 'frontend'

$publishedPort = Invoke-DockerText @('compose', '--project-directory', $ComposeDirectory, 'port', 'frontend', '80')
if ($publishedPort -notmatch ':(\d+)\s*$') {
    throw "Unable to determine the frontend port from: $publishedPort"
}

$applicationUrl = "http://localhost:$($Matches[1])/?build=$SourceRevision"
$reachable = $false
for ($attempt = 1; $attempt -le 10; $attempt++) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $applicationUrl -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            $reachable = $true
            break
        }
    } catch {
        if ($attempt -eq 10) { throw }
        Start-Sleep -Seconds 1
    }
}

if (-not $reachable) {
    throw "The frontend did not return HTTP 200 at $applicationUrl"
}

Write-Host "[OK] Frontend returned HTTP 200 at $applicationUrl"
if ($OpenBrowser) {
    Start-Process $applicationUrl
    Write-Host "[OK] Opened $applicationUrl in the default browser."
}
