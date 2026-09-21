[CmdletBinding()]
param(
    [string]$ComposeDirectory,
    [string]$Database = $(if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'certmuse' }),
    [string]$DatabaseUser = $(if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'certmuse' })
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ComposeDirectory = if ($ComposeDirectory) { $ComposeDirectory } else { Join-Path $projectRoot 'infra\docker' }
$migrationRoot = Join-Path $projectRoot 'backend\script\sql\postgres\certmuse'
$migrationManifest = Join-Path $migrationRoot 'local-migration-manifest.txt'
if (-not (Test-Path -LiteralPath $migrationManifest -PathType Leaf)) {
    throw "Missing local database migration manifest: $migrationManifest"
}
$migrations = Get-Content -LiteralPath $migrationManifest -Encoding utf8 |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith('#') }

# 20260817_add-textbook-original-pdf.sql was briefly committed without its
# permission-id conflict guard, then restored. The two versions
# have the same schema effect, so local databases that recorded that transient
# revision can safely retain the applied migration and normalize its checksum.
# All other checksum differences remain blocking to protect immutable migrations.
$knownChecksumRepairs = @{
    '20260817_add-textbook-original-pdf.sql' = @{
        Canonical = '0230e90ed76a2d171b6042a26d106fc7f7172e13351de32532c6a48f9a4bf680'
        Legacy = @('b2bb6327f82825e600abd70ae0552a38e4ba5263722c975b1df3437a13e49f33')
    }
}

foreach ($name in $migrations) {
    $path = Join-Path $migrationRoot $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing local database migration: $path"
    }
}

$containerId = (@(& docker compose --project-directory $ComposeDirectory ps -q postgres) -join '').Trim()
if ($LASTEXITCODE -ne 0 -or -not $containerId) {
    throw 'The CertMuse PostgreSQL container is not running.'
}

function Invoke-PsqlCommand([string]$Sql, [switch]$TuplesOnly) {
    $arguments = @('exec', '-i', $containerId, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', $DatabaseUser, '-d', $Database)
    if ($TuplesOnly) { $arguments += @('-A', '-t') }
    $output = $Sql | & docker @arguments
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL migration command failed.' }
    return $output
}

function Get-Sha256([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try {
            return ([System.BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
        } finally {
            $sha.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Invoke-PsqlFile([string]$Path) {
    $previousOutputEncoding = $OutputEncoding
    try {
        # PostgreSQL containers expect UTF-8 input; Windows PowerShell otherwise emits text using the active code page.
        $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
        Get-Content -LiteralPath $Path -Raw -Encoding utf8 |
            & docker exec -i $containerId psql -X -v ON_ERROR_STOP=1 -U $DatabaseUser -d $Database
        if ($LASTEXITCODE -ne 0) { throw "Migration failed: $(Split-Path -Leaf $Path)" }
    } finally {
        $OutputEncoding = $previousOutputEncoding
    }
}

function Get-ContainerEnvironmentValue([string]$Name) {
    $line = & docker exec $containerId printenv $Name
    if ($LASTEXITCODE -ne 0 -or -not $line) {
        throw "PostgreSQL container environment variable is missing: $Name"
    }
    return (@($line) -join '').Trim()
}

function Sync-LocalMinioConfiguration {
    $minioAccessKey = Get-ContainerEnvironmentValue 'MINIO_ROOT_USER'
    $minioSecretKey = Get-ContainerEnvironmentValue 'MINIO_ROOT_PASSWORD'
    $minioBucket = Get-ContainerEnvironmentValue 'MINIO_BUCKET'
    $minioEndpoint = Get-ContainerEnvironmentValue 'MINIO_INTERNAL_ENDPOINT'
    $arguments = @(
        'exec', '-i', $containerId, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
        '-v', "oss_access_key=$minioAccessKey",
        '-v', "oss_secret_key=$minioSecretKey",
        '-v', "oss_bucket=$minioBucket",
        '-v', "oss_endpoint=$minioEndpoint",
        '-U', $DatabaseUser, '-d', $Database
    )
    @'
UPDATE sys_oss_config
SET access_key = :'oss_access_key',
    secret_key = :'oss_secret_key',
    bucket_name = :'oss_bucket',
    endpoint = :'oss_endpoint',
    is_https = 'N',
    access_policy = '0',
    status = 'Y',
    update_time = now()
WHERE config_key = 'minio';
'@ | & docker @arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to synchronize the local MinIO configuration.' }
    Write-Host '[OK] Local MinIO configuration synchronized.'
}

function Sync-LocalRegistrationConfiguration {
    $registrationEnabled = Get-ContainerEnvironmentValue 'LOCAL_PUBLIC_REGISTRATION'
    if ($registrationEnabled -notin @('true', 'false')) {
        throw 'LOCAL_PUBLIC_REGISTRATION must be true or false.'
    }
    if ($registrationEnabled -eq 'false') {
        Write-Host '[SKIP] Local public registration remains disabled.'
        return
    }
    Invoke-PsqlCommand @'
UPDATE sys_config
SET config_value = 'true', update_time = now()
WHERE config_key = 'sys.account.registerUser';

UPDATE sys_config
SET config_value = COALESCE((
    SELECT string_agg(client_id, ',' ORDER BY client_id)
    FROM sys_client
    WHERE status = '0'
      AND ('password' = ANY(string_to_array(grant_type, ',')))
), ''), update_time = now()
WHERE config_key = 'sys.account.registerClientIds';
'@ | Out-Null
    Write-Host '[OK] Local public registration configuration synchronized.'
}

Invoke-PsqlCommand @'
CREATE SCHEMA IF NOT EXISTS certmuse_meta;
CREATE TABLE IF NOT EXISTS certmuse_meta.schema_migration (
    migration_name varchar(255) PRIMARY KEY,
    sha256 char(64) NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);
'@ | Out-Null

foreach ($name in $migrations) {
    $path = Join-Path $migrationRoot $name
    $checksum = Get-Sha256 $path
    $recorded = (@(Invoke-PsqlCommand "SELECT sha256 FROM certmuse_meta.schema_migration WHERE migration_name='$name';" -TuplesOnly) -join '').Trim()
    if ($recorded) {
        if ($recorded -ne $checksum) {
            $repair = $knownChecksumRepairs[$name]
            if ($repair -and $checksum -eq $repair.Canonical -and $recorded -in $repair.Legacy) {
                Invoke-PsqlCommand "UPDATE certmuse_meta.schema_migration SET sha256='$checksum' WHERE migration_name='$name' AND sha256='$recorded';" | Out-Null
                Write-Host "[REPAIR] $name"
                continue
            }
            throw "Applied migration checksum changed: $name"
        }
        Write-Host "[SKIP] $name"
        continue
    }

    Write-Host "[APPLY] $name"
    $containerSqlPath = '/tmp/certmuse-local-migration.sql'
    & docker cp $path "$($containerId):$containerSqlPath"
    if ($LASTEXITCODE -ne 0) { throw "Unable to copy migration into PostgreSQL container: $name" }
    & docker exec $containerId psql -X -v ON_ERROR_STOP=1 -U $DatabaseUser -d $Database -f $containerSqlPath
    $psqlExitCode = $LASTEXITCODE
    & docker exec $containerId rm -f $containerSqlPath | Out-Null
    if ($psqlExitCode -ne 0) { throw "Migration failed: $name" }

    Invoke-PsqlCommand "INSERT INTO certmuse_meta.schema_migration(migration_name,sha256) VALUES ('$name','$checksum');" | Out-Null
    Write-Host "[OK] $name"
}

Sync-LocalMinioConfiguration
Sync-LocalRegistrationConfiguration
Write-Host 'Local database migrations are up to date.'
