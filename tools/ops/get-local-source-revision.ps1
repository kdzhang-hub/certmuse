[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$pathScopes = @(
    'backend',
    'web/admin',
    'infra/docker',
    'tools/ops/get-local-source-revision.ps1',
    'tools/ops/migrate-local-database.ps1',
    'tools/ops/verify-local-startup.ps1',
    'start.bat'
)

$previousOutputEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $files = @(& git -C $projectRoot -c core.quotepath=false ls-files --cached --others --exclude-standard -- @pathScopes)
} finally {
    $OutputEncoding = $previousOutputEncoding
}
if ($LASTEXITCODE -ne 0 -or $files.Count -eq 0) {
    throw 'Unable to enumerate local CertMuse source files.'
}

$hash = [System.Security.Cryptography.IncrementalHash]::CreateHash(
    [System.Security.Cryptography.HashAlgorithmName]::SHA256
)
try {
    foreach ($relativePath in @($files | Sort-Object -Unique)) {
        $normalizedPath = $relativePath.Replace('\', '/')
        $absolutePath = Join-Path $projectRoot $relativePath
        if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) { continue }

        $hash.AppendData([System.Text.Encoding]::UTF8.GetBytes($normalizedPath))
        $hash.AppendData([byte[]]@(0))
        $hash.AppendData([System.IO.File]::ReadAllBytes($absolutePath))
        $hash.AppendData([byte[]]@(0))
    }
    ([System.BitConverter]::ToString($hash.GetHashAndReset())).Replace('-', '').ToLowerInvariant()
} finally {
    $hash.Dispose()
}
