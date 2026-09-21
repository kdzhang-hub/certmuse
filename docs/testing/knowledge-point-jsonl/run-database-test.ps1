param(
    [string]$DatabaseContainer = "certmuse-postgres-1",
    [string]$MinioContainer = "certmuse-minio-1",
    [string]$ResultsDirectory = ""
)

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ResultsDirectory)) {
    $ResultsDirectory = Join-Path $here "results"
}
$integrationPath = Join-Path $ResultsDirectory "integration-result.json"
$integration = Get-Content -LiteralPath $integrationPath -Raw | ConvertFrom-Json

if (-not $integration.passed) {
    throw "Integration result must pass before database assertions."
}

$validId = $integration.batches.valid.id
$edgeId = $integration.batches.edge.id
$mappingId = $integration.batches.mappingUnknown.id
$beforeCount = [int64]$integration.database.beforeKnowledgeCount
$ids = "$validId,$edgeId,$mappingId"

$sql = @"
select json_build_object(
  'knowledgePointCount', (select count(*) from cm_knowledge_point),
  'batches', (select json_agg(row_to_json(x) order by x.id) from (
    select id,status,current_stage,valid_count,warning_count,failed_count,source_file_size
    from cm_import_batch where id in ($ids)
  ) x),
  'records', (select json_agg(row_to_json(x) order by x.batch_id) from (
    select batch_id,count(*) as total,
           count(*) filter(where status='success') as success,
           count(*) filter(where status='failed') as failed
    from cm_import_record where batch_id in ($ids) group by batch_id
  ) x),
  'issues', (select json_agg(row_to_json(x) order by x.import_batch_id) from (
    select import_batch_id,count(*) as total,count(distinct issue_code) as distinct_codes
    from cm_import_issue where import_batch_id in ($ids) group by import_batch_id
  ) x),
  'idempotency', (select json_agg(row_to_json(x) order by x.resource_id) from (
    select resource_id,status,response_status from cm_idempotency_record
    where resource_id in ($ids)
  ) x)
);
"@

$json = docker exec $DatabaseContainer psql -U certmuse -d certmuse -tA -c $sql
if ($LASTEXITCODE -ne 0) { throw "PostgreSQL assertion query failed." }
$database = ($json -join "`n") | ConvertFrom-Json

$assertions = [System.Collections.Generic.List[object]]::new()
function Assert-Database([bool]$condition, [string]$name, $details) {
    $assertions.Add([ordered]@{ name = $name; passed = $condition; details = $details })
    if (-not $condition) { throw "$name failed: $($details | ConvertTo-Json -Compress -Depth 8)" }
}

$validBatch = $database.batches | Where-Object id -eq $validId
$edgeBatch = $database.batches | Where-Object id -eq $edgeId
$validRecords = $database.records | Where-Object batch_id -eq $validId
$edgeRecords = $database.records | Where-Object batch_id -eq $edgeId
$edgeIssues = $database.issues | Where-Object import_batch_id -eq $edgeId

Assert-Database ($database.knowledgePointCount -eq $beforeCount) "D1 precheck does not mutate cm_knowledge_point" @{
    before = $beforeCount; after = $database.knowledgePointCount
}
Assert-Database ($validBatch.status -eq "waiting_confirm" -and $validBatch.valid_count -eq 1001 -and $validBatch.failed_count -eq 0) `
    "valid batch counters persisted" $validBatch
Assert-Database ($validRecords.total -eq 1001 -and $validRecords.success -eq 1001 -and $validRecords.failed -eq 0) `
    "valid records persisted exactly" $validRecords
Assert-Database ($edgeBatch.valid_count -eq $integration.batches.edge.progress.validCount -and
                 $edgeBatch.failed_count -eq $integration.batches.edge.progress.failedCount) `
    "edge batch API and database counters agree" $edgeBatch
Assert-Database (($edgeRecords.total + ($edgeBatch.failed_count - $edgeRecords.failed)) -eq 1069) `
    "edge physical lines reconcile with persisted and file-level failures" @{
        physicalLines = 1069; records = $edgeRecords.total; recordFailures = $edgeRecords.failed; batchFailures = $edgeBatch.failed_count
    }
Assert-Database ($edgeIssues.total -eq $integration.batches.edge.issues.Count -and $edgeIssues.distinct_codes -ge 15) `
    "edge issues persisted and match API pagination" $edgeIssues
Assert-Database ($database.idempotency.Count -eq 3 -and @($database.idempotency | Where-Object {
    $_.status -ne "succeeded" -or $_.response_status -ne 200
}).Count -eq 0) "idempotency records persisted successfully" $database.idempotency

foreach ($id in @($validId, $edgeId, $mappingId)) {
    docker exec $MinioContainer mc stat "test/certmuse/imports/knowledge-point/$id/source.jsonl" *> $null
    Assert-Database ($LASTEXITCODE -eq 0) "MinIO source object exists for batch $id" @{ batchId = $id }
}

$result = [ordered]@{
    startedFromIntegration = $integration.finishedAt
    finishedAt = [DateTimeOffset]::Now.ToString("o")
    passed = $true
    assertions = $assertions
    database = $database
}
$result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $ResultsDirectory "database-result.json") -Encoding utf8
Write-Output ($result | ConvertTo-Json -Compress -Depth 4)
