[CmdletBinding()]
param(
    [string]$DockerContainer = $env:CERTMUSE_PG_CONTAINER
)

$ErrorActionPreference = 'Stop'
$sqlRoot = $PSScriptRoot
$platformBaseline = Join-Path $sqlRoot '..\postgres_ry_vue.sql'
$manifest = Join-Path $sqlRoot 'local-migration-manifest.txt'
$foundationFiles = @(
    '001_foundation.sql',
    '002_content.sql',
    '003_learning.sql',
    '004_profile.sql',
    '005_support.sql',
    '006_constraints_indexes_seed.sql'
)
if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) {
    throw "Missing local migration manifest: $manifest"
}
$migrationFiles = $foundationFiles + @(
    Get-Content -LiteralPath $manifest |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith('#') }
)

foreach ($name in $migrationFiles + 'verify_local.sql') {
    $path = Join-Path $sqlRoot $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing required SQL file: $path"
    }
}
if (-not (Test-Path -LiteralPath $platformBaseline -PathType Leaf)) {
    throw "Missing required RuoYi PostgreSQL baseline: $platformBaseline"
}

$migrationText = ($migrationFiles | ForEach-Object {
    Get-Content -LiteralPath (Join-Path $sqlRoot $_) -Raw
}) -join "`n"
$withoutComments = [regex]::Replace($migrationText, '(?m)^\s*--.*$', '')

$tableMatches = [regex]::Matches($withoutComments, '(?im)^\s*CREATE\s+TABLE\s+(cm_[a-z0-9_]+)\s*\(')
$tableNames = @($tableMatches | ForEach-Object { $_.Groups[1].Value })
if ($tableNames.Count -ne 78 -or (@($tableNames | Sort-Object -Unique)).Count -ne 78) {
    throw "Static check failed: expected 78 distinct cm_* CREATE TABLE statements, got $($tableNames.Count)."
}
if ($withoutComments -notmatch '(?is)CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+cm_diagnostic_timer_lease\s*\(') {
    throw 'Static check failed: diagnostic timer lease table migration is missing.'
}

if ($withoutComments -match '(?is)CONSTRAINT\s+uk_cm_knowledge_point_syllabus_number\s+UNIQUE\s*\(\s*syllabus_number\s*\)') {
    throw 'Static check failed: cm_knowledge_point.syllabus_number must not be globally unique.'
}
if ($withoutComments -notmatch '(?is)CREATE\s+UNIQUE\s+INDEX\s+uk_cm_knowledge_point_active_number\s+ON\s+cm_knowledge_point\s*\(\s*syllabus_version_id\s*,\s*exam_subject_id\s*,\s*syllabus_number\s*\)\s*WHERE\s+del_flag\s*=\s*''0''') {
    throw 'Static check failed: active knowledge-point number unique index is missing.'
}

$idPkCount = [regex]::Matches($withoutComments, '(?im)PRIMARY\s+KEY\s*\(\s*id\s*\)').Count
if ($idPkCount -ne 74) {
    throw "Static check failed: expected 74 id primary keys, got $idPkCount."
}
if ($withoutComments -notmatch '(?is)CREATE\s+TABLE\s+cm_collection_current\s*\(.*?PRIMARY\s+KEY\s*\(\s*collection_id\s*\)') {
    throw 'Static check failed: cm_collection_current natural primary key is missing.'
}

$jsonbCount = [regex]::Matches($withoutComments, '(?im)^\s*[a-z][a-z0-9_]*\s+jsonb\b').Count
if ($jsonbCount -ne 59) {
    throw "Static check failed: expected 59 JSONB columns declared in CREATE TABLE statements, got $jsonbCount."
}

$banned = @(
    @{ Name='Identity'; Pattern='(?i)\bGENERATED\s+(?:ALWAYS|BY\s+DEFAULT)\s+AS\s+IDENTITY\b' },
    @{ Name='Serial'; Pattern='(?i)\b(?:smallserial|serial|bigserial)\b' },
    @{ Name='Sequence'; Pattern='(?i)\b(?:CREATE\s+SEQUENCE|nextval\s*\()' },
    @{ Name='tenant_id'; Pattern='(?i)\btenant_id\b' }
)
foreach ($rule in $banned) {
    if ($withoutComments -match $rule.Pattern) {
        throw "Static check failed: banned construct found: $($rule.Name)."
    }
}

function Invoke-CertMusePsqlFile([string]$Path) {
    if ($DockerContainer) {
        $dbName = if ($env:PGDATABASE) { $env:PGDATABASE } else { 'certmuse_local' }
        $dbUser = if ($env:PGUSER) { $env:PGUSER } else { 'postgres' }
        Get-Content -LiteralPath $Path -Raw |
            docker exec -i $DockerContainer psql -X --set=ON_ERROR_STOP=1 --username=$dbUser --dbname=$dbName
        if ($LASTEXITCODE -ne 0) { throw "psql failed for $Path" }
        return
    }

    if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
        throw 'psql was not found. Install/use PostgreSQL psql or set CERTMUSE_PG_CONTAINER to an existing local container.'
    }
    if (-not $env:PGDATABASE) {
        throw 'PGDATABASE must name an existing, dedicated local validation database (normally certmuse_local).'
    }
    & psql -X --set=ON_ERROR_STOP=1 --file=$Path
    if ($LASTEXITCODE -ne 0) { throw "psql failed for $Path" }
}

function Initialize-LocalPlatformRole {
    $bootstrapSql = "DO `$_`$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'certmuse_owner') THEN CREATE ROLE certmuse_owner NOLOGIN; END IF; END `$_`$;"
    if ($DockerContainer) {
        $dbName = if ($env:PGDATABASE) { $env:PGDATABASE } else { 'certmuse_local' }
        $dbUser = if ($env:PGUSER) { $env:PGUSER } else { 'postgres' }
        $bootstrapSql | docker exec -i $DockerContainer psql -X --set=ON_ERROR_STOP=1 --username=$dbUser --dbname=$dbName
        if ($LASTEXITCODE -ne 0) { throw 'psql failed while creating the local certmuse_owner role' }
        return
    }
    $bootstrapSql | & psql -X --set=ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'psql failed while creating the local certmuse_owner role' }
}

Write-Host 'Static checks passed: 78 base tables, 74 id PKs, 1 base natural PK, timer lease migration, 59 CREATE TABLE JSONB fields, no banned constructs.'
Initialize-LocalPlatformRole
Write-Host 'Executing RuoYi PostgreSQL baseline'
Invoke-CertMusePsqlFile $platformBaseline
foreach ($name in $migrationFiles) {
    Write-Host "Executing $name"
    Invoke-CertMusePsqlFile (Join-Path $sqlRoot $name)
}
Write-Host 'Executing verify_local.sql'
Invoke-CertMusePsqlFile (Join-Path $sqlRoot 'verify_local.sql')
Write-Host 'CertMuse local database validation completed successfully.'
