#requires -Version 7.0

[CmdletBinding()]
param(
    [switch]$AllowWorkingTreeChanges
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$currentStage = '初始化'
$startedAt = Get-Date
# These source/frozen hash pairs predate this guard. Any future change to either side must stop the release.
$approvedFrozenMigrationDifferences = @{
    '002_content.sql' = @{
        Source = '9c55ef837d2fd6a4df81437dab69dd811e025e24cdfda5decb0fd1547b874fb9'
        Frozen = '1a31ec7f899c8cf124889d95b0151c0383836a6adab122505d44c51b833e1276'
        FrozenCommit = '288659dc6f4360b6e030c0327f1a832368c3cfe6'
    }
    '006_constraints_indexes_seed.sql' = @{
        Source = '5c0fd49373ba0bbf46e7ab611b116c7a28fbd2a4ff49912f2b7c46cdc6b6f289'
        Frozen = 'ffe171839a35396a9aa2d3892b9ec0f3a99f2776a255f4d1201a17c71e57c802'
        FrozenCommit = 'ff8c2655809760a58dd3c91e1ce920524ce5c95d'
    }
    '20260724_enable-minio-oss.sql' = @{
        Source = 'f6793df2701aa18b2bd03b68780ae5e0bb4691463e38195d6a99f7a6b310eec1'
        Frozen = '2a35340b7ed906b94dc4cc5d574336b24438fcd273aabf812bf26c8014b79493'
        FrozenCommit = '898aa390729596a74ec1281475af4aef6ec23b55'
    }
    '20260806_cascade-qualification-version-delete.sql' = @{
        Source = 'f72cfa89013194d68e7f8ff7f256e6ad28ac57468aaa11223bfc15931bc6c24a'
        Frozen = 'cb924d19cb686291cb4667f4ebc6d66f23f67aeada982c256d8b570987a67fff'
        FrozenCommit = 'a509a2687312c4936661df7fa7e93c3c9ba95c8e'
    }
}

function Write-Stage {
    param([string]$Name)
    $script:currentStage = $Name
    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
}

function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter()][string[]]$Arguments = @(),
        [Parameter()][string]$WorkingDirectory = $projectRoot,
        [switch]$Capture
    )

    Push-Location $WorkingDirectory
    try {
        if ($Capture) {
            $output = & $Command @Arguments 2>&1
            $output | ForEach-Object { Write-Host $_ }
        } else {
            & $Command @Arguments
            $output = @()
        }
        if ($LASTEXITCODE -ne 0) {
            throw "命令执行失败（退出码 $LASTEXITCODE）：$Command $($Arguments -join ' ')"
        }
        return $output
    } finally {
        Pop-Location
    }
}

function Assert-Path {
    param([string]$Label, [string]$Path, [string]$PathType = 'Any')
    if (-not (Test-Path -LiteralPath $Path -PathType $PathType)) {
        throw "缺少${Label}：$Path"
    }
}

function Restore-FrozenMigration {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][hashtable]$Approved,
        [Parameter(Mandatory)][string]$Target
    )

    $gitObject = "$($Approved.FrozenCommit):backend/script/sql/postgres/certmuse/$Name"
    $temporaryTarget = "$Target.restore-$PID.tmp"
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'git'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    [void]$startInfo.ArgumentList.Add('cat-file')
    [void]$startInfo.ArgumentList.Add('-p')
    [void]$startInfo.ArgumentList.Add($gitObject)

    try {
        $process = [System.Diagnostics.Process]::Start($startInfo)
        $output = [System.IO.File]::Open($temporaryTarget, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try {
            $process.StandardOutput.BaseStream.CopyTo($output)
        } finally {
            $output.Dispose()
        }
        $errorOutput = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "无法从已验证的冻结提交恢复 $Name：$errorOutput"
        }

        $restoredHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $temporaryTarget).Hash.ToLowerInvariant()
        if ($restoredHash -ne $Approved.Frozen) {
            throw "冻结迁移恢复校验失败：$Name。期望 SHA-256=$($Approved.Frozen)，实际 SHA-256=$restoredHash"
        }
        [System.IO.File]::Move($temporaryTarget, $Target, $true)
    } finally {
        if (Test-Path -LiteralPath $temporaryTarget) {
            Remove-Item -LiteralPath $temporaryTarget -Force
        }
    }
}

function Sync-Migrations {
    $sourceDir = Join-Path $projectRoot 'backend/script/sql/postgres/certmuse'
    $rootfsDir = Join-Path $projectRoot 'tools/ops/local/routeflow/platform/linux/rootfs/app/certmuse/backend/migrations'
    $migrationRunnerSource = Join-Path $projectRoot 'infra/deploy/scripts/certmuse-migrate-postgres.sh'
    $migrationRunnerTarget = Join-Path (Split-Path $rootfsDir -Parent) 'certmuse-migrate-postgres.sh'
    $configPath = Join-Path $projectRoot 'tools/ops/local/routeflow/tools/deployer/certmuse.yml'
    Assert-Path '源码迁移目录' $sourceDir 'Container'
    Assert-Path 'Deployer 迁移目录' $rootfsDir 'Container'
    Assert-Path '生产迁移器' $migrationRunnerSource 'Leaf'
    Assert-Path 'Deployer 配置' $configPath 'Leaf'

    $migrations = Get-ChildItem -LiteralPath $sourceDir -Filter '*.sql' -File |
        Where-Object { $_.Name -ne 'verify_local.sql' } |
        Sort-Object Name
    $existingRootfsNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    Get-ChildItem -LiteralPath $rootfsDir -Filter '*.sql' -File |
        ForEach-Object { [void]$existingRootfsNames.Add($_.Name) }

    foreach ($migration in $migrations) {
        if (-not $existingRootfsNames.Contains($migration.Name)) {
            continue
        }
        $target = Join-Path $rootfsDir $migration.Name
        $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $migration.FullName).Hash.ToLowerInvariant()
        $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToLowerInvariant()
        $approved = $approvedFrozenMigrationDifferences[$migration.Name]
        if ($sourceHash -eq $targetHash) {
            if ($null -ne $approved) {
                if ($approved.Source -ne $sourceHash -or [string]::IsNullOrWhiteSpace($approved.FrozenCommit)) {
                    throw "迁移 $($migration.Name) 的冻结恢复配置与源码不匹配。源码 SHA-256=$sourceHash"
                }
                Write-Warning "检测到 $($migration.Name) 的 rootfs 被当前源码覆盖；恢复已验证的发布冻结副本。"
                Restore-FrozenMigration -Name $migration.Name -Approved $approved -Target $target
                $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToLowerInvariant()
                if ($targetHash -ne $approved.Frozen) {
                    throw "迁移 $($migration.Name) 的冻结副本恢复后校验不一致。期望 SHA-256=$($approved.Frozen)，实际 SHA-256=$targetHash"
                }
            }
            continue
        }
        if ($null -eq $approved -or $approved.Source -ne $sourceHash -or $approved.Frozen -ne $targetHash) {
            throw "迁移 $($migration.Name) 与发布冻结副本出现未经确认的差异。禁止按修改时间覆盖已执行迁移；请把数据库变化写入新的幂等增量 SQL。源码 SHA-256=$sourceHash，冻结 SHA-256=$targetHash"
        }
        Write-Warning "已确认的历史差异，继续保留发布冻结副本：$($migration.Name)。新的数据库变化必须使用新迁移文件名。"
    }

    foreach ($migration in $migrations) {
        if ($existingRootfsNames.Contains($migration.Name)) {
            continue
        }
        $target = Join-Path $rootfsDir $migration.Name
        Copy-Item -LiteralPath $migration.FullName -Destination $target -Force
    }
    Copy-Item -LiteralPath $migrationRunnerSource -Destination $migrationRunnerTarget -Force

    $configLines = [System.Collections.Generic.List[string]](Get-Content -LiteralPath $configPath)
    $dataDirsIndex = $configLines.FindIndex([Predicate[string]] { param($line) $line -match '^\s{8}data_dirs:\s*$' })
    if ($dataDirsIndex -lt 0) {
        throw '无法在 certmuse.yml 中定位 data_dirs，未自动修改配置。'
    }

    $missingEntries = foreach ($migration in $migrations) {
        $manifestPath = "app/certmuse/backend/migrations/$($migration.Name)"
        if (-not ($configLines | Select-String -SimpleMatch -Quiet -Pattern "`"$manifestPath`"")) {
            "          - `"$manifestPath`""
        }
    }
    if ($missingEntries) {
        $configLines.InsertRange($dataDirsIndex, [string[]]$missingEntries)
    }

    $migrationRunnerPath = 'app/certmuse/backend/certmuse-migrate-postgres.sh'
    $runnerEntry = "          - `"$migrationRunnerPath`""
    $scriptsIndex = $configLines.FindIndex([Predicate[string]] { param($line) $line -match '^\s{8}scripts:\s*$' })
    if ($scriptsIndex -lt 0) {
        $dataDirsIndex = $configLines.FindIndex([Predicate[string]] { param($line) $line -match '^\s{8}data_dirs:\s*$' })
        $configLines.Insert($dataDirsIndex, '        scripts:')
        $configLines.Insert($dataDirsIndex + 1, $runnerEntry)
    } elseif (-not ($configLines | Select-String -SimpleMatch -Quiet -Pattern "`"$migrationRunnerPath`"")) {
        $configLines.Insert($scriptsIndex + 1, $runnerEntry)
    }
    Set-Content -LiteralPath $configPath -Value $configLines -Encoding utf8

    $configText = Get-Content -Raw -LiteralPath $configPath
    foreach ($migration in $migrations) {
        $target = Join-Path $rootfsDir $migration.Name
        $manifestPath = "app/certmuse/backend/migrations/$($migration.Name)"
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            throw "迁移未进入 Deployer rootfs：$($migration.Name)"
        }
        if ($configText -notmatch [regex]::Escape("`"$manifestPath`"")) {
            throw "迁移未登记到 certmuse.yml：$($migration.Name)"
        }
        if (-not $existingRootfsNames.Contains($migration.Name)) {
            $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $migration.FullName).Hash
            $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash
            if ($sourceHash -ne $targetHash) {
                throw "迁移同步后校验不一致：$($migration.Name)"
            }
        }
    }
    if (-not (Test-Path -LiteralPath $migrationRunnerTarget -PathType Leaf)) {
        throw '生产迁移器未进入 Deployer rootfs。'
    }
    if ($configText -notmatch [regex]::Escape("`"$migrationRunnerPath`"")) {
        throw '生产迁移器未登记到 certmuse.yml scripts。'
    }
    Write-Host "[通过] 已核对 $($migrations.Count) 个正式数据库迁移及受管迁移器；所有既有发布副本均未被覆盖。"
}

function Sync-Artifacts {
    $jarSource = Join-Path $projectRoot 'backend/ruoyi-admin/target/ruoyi-admin.jar'
    $jarTarget = Join-Path $projectRoot 'tools/ops/local/routeflow/platform/linux/rootfs/app/certmuse/backend/certmuse-admin.jar'
    $adminFrontendSource = Join-Path $projectRoot 'web/admin/dist'
    $studentFrontendSource = Join-Path $projectRoot 'web/student/dist'
    $frontendTarget = Join-Path $projectRoot 'tools/ops/local/routeflow/platform/linux/rootfs/app/certmuse/frontend'
    $nginxSource = Join-Path $projectRoot 'infra/deploy/nginx/certmuse.conf.example'
    $nginxTarget = Join-Path $projectRoot 'tools/ops/local/routeflow/platform/linux/hosts/certmuse-app/rootfs/etc/nginx/conf.d/certmuse.conf'
    $systemdSource = Join-Path $projectRoot 'infra/deploy/systemd/certmuse.service.example'
    $systemdTarget = Join-Path $projectRoot 'tools/ops/local/routeflow/platform/linux/hosts/certmuse-app/rootfs/etc/systemd/system/certmuse.service'

    Assert-Path '后端 JAR' $jarSource 'Leaf'
    Assert-Path '管理端构建目录' $adminFrontendSource 'Container'
    Assert-Path '学生端构建目录' $studentFrontendSource 'Container'
    Assert-Path '学生端入口文件' (Join-Path $studentFrontendSource 'index.html') 'Leaf'
    Assert-Path '生产 Nginx 模板' $nginxSource 'Leaf'
    Assert-Path '生产 systemd 模板' $systemdSource 'Leaf'
    Assert-Path 'RouteFlow 前端目标目录' $frontendTarget 'Container'

    $resolvedFrontendTarget = (Resolve-Path -LiteralPath $frontendTarget).Path
    $requiredSuffix = [IO.Path]::Combine('tools', 'ops', 'local', 'routeflow', 'platform', 'linux', 'rootfs', 'app', 'certmuse', 'frontend')
    if (-not $resolvedFrontendTarget.EndsWith($requiredSuffix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "拒绝清理非预期目录：$resolvedFrontendTarget"
    }

    Copy-Item -LiteralPath $jarSource -Destination $jarTarget -Force
    Get-ChildItem -Force -LiteralPath $frontendTarget | Remove-Item -Recurse -Force
    Copy-Item -Path (Join-Path $adminFrontendSource '*') -Destination $frontendTarget -Recurse -Force
    $studentAssetsTarget = Join-Path $frontendTarget 'student-assets'
    New-Item -ItemType Directory -Force -Path $studentAssetsTarget | Out-Null
    Copy-Item -Path (Join-Path $studentFrontendSource '*') -Destination $studentAssetsTarget -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $studentFrontendSource 'index.html') -Destination (Join-Path $frontendTarget 'student-index.html') -Force
    New-Item -ItemType Directory -Force -Path (Split-Path $nginxTarget -Parent) | Out-Null
    Copy-Item -LiteralPath $nginxSource -Destination $nginxTarget -Force
    New-Item -ItemType Directory -Force -Path (Split-Path $systemdTarget -Parent) | Out-Null
    Copy-Item -LiteralPath $systemdSource -Destination $systemdTarget -Force
    Write-Host '[通过] JAR、管理端 dist、学生端 dist、生产 Nginx 和 systemd 配置已同步到 RouteFlow rootfs。'
}

function Get-Advice {
    param([string]$Stage)
    switch -Wildcard ($Stage) {
        '检查本地部署*' { '确认当前分支为 main 且工作区干净；脚本以当前本地提交为准，不会拉取或比较远程分支。' }
        '检查部署工具*' { '运行 .\tools\ops\certmusectl.ps1 doctor，根据 missing/notice 补齐本地私有工具或 profile。' }
        '管理端*' { '查看上方第一个 TypeScript、lint、测试或构建错误；修复后重新运行本脚本。' }
        '学生端*' { '查看上方第一个 TypeScript、lint、测试或构建错误；修复后重新运行本脚本。' }
        '后端*' { '查看 Maven 输出中的第一个编译或测试失败；不要跳过测试继续部署。' }
        '同步数据库迁移*' { '检查源码迁移、RouteFlow rootfs 和 certmuse.yml；已执行迁移不能修改，只能新增增量迁移。' }
        'Deployer dry-run*' { '检查网络、host profile、私有密钥以及迁移 checksum；确认计划正确前不要绕过。' }
        '正式部署*' { '查看最后一个 [ERROR] 或失败命令。迁移失败时服务不会重启；修复后重新运行脚本即可。' }
        '部署后验收*' { '先检查 certmuse.service 与 Nginx 状态，再查看后端日志；不要把页面可访问当成数据库迁移已成功。' }
        default { '保留上方完整错误信息，修复原因后重新运行脚本；不要使用跳过检查的方式强行部署。' }
    }
}

function Wait-HttpOk {
    param(
        [Parameter(Mandatory)][string]$Url,
        [int]$Attempts = 12,
        [int]$DelaySeconds = 5
    )

    $lastStatus = 'no response'
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 30 -SkipHttpErrorCheck
            $lastStatus = "HTTP $($response.StatusCode)"
            if ($response.StatusCode -eq 200) {
                return $response
            }
        } catch {
            $lastStatus = $_.Exception.Message
        }
        if ($attempt -lt $Attempts) {
            Write-Host "[等待] $Url 尚未就绪（$lastStatus），$DelaySeconds 秒后重试 $($attempt + 1)/$Attempts。"
            Start-Sleep -Seconds $DelaySeconds
        }
    }
    throw "$Url 在 $Attempts 次检查后仍未就绪，最后结果：$lastStatus"
}

try {
    Set-Location $projectRoot

    Write-Stage '检查本地部署提交'
    $branch = (Invoke-Native 'git' @('branch', '--show-current') -Capture | Select-Object -Last 1).ToString().Trim()
    if ($branch -ne 'main') { throw "当前分支是 $branch，生产部署只允许 main。" }
    if (-not $AllowWorkingTreeChanges) {
        $status = Invoke-Native 'git' @('status', '--porcelain') -Capture
        if ($status) { throw '工作区存在未提交修改。请先提交或保存这些修改，避免把本地内容混入生产包。' }
    }
    $head = (Invoke-Native 'git' @('rev-parse', 'HEAD') -Capture | Select-Object -Last 1).ToString().Trim()
    Write-Host "[通过] 本地部署提交：$head（未拉取或比较远程分支）"

    Write-Stage '检查部署工具'
    & (Join-Path $PSScriptRoot 'doctor.ps1')
    if ($LASTEXITCODE -ne 0) { throw '部署工具自检失败。' }

    Write-Stage '管理端完整质量门禁'
    Invoke-Native 'pnpm' @('run', 'check:admin')

    Write-Stage '学生端完整质量门禁'
    Invoke-Native 'pnpm' @('run', 'check:student')

    Write-Stage '后端完整测试'
    Invoke-Native (Join-Path $projectRoot 'backend/mvnw.cmd') @('-Dmaven.test.skip=false', 'test') (Join-Path $projectRoot 'backend')

    Write-Stage '后端正式构建'
    Invoke-Native (Join-Path $projectRoot 'backend/mvnw.cmd') @('clean', 'package') (Join-Path $projectRoot 'backend')

    Write-Stage '同步数据库迁移'
    Sync-Migrations

    Write-Stage '同步发布产物'
    Sync-Artifacts

    $deployerArgs = @('-config', 'certmuse.yml', '-platform', 'linux', '-host', 'certmuse-app', '-components', 'certmuse')
    Write-Stage 'Deployer dry-run'
    Invoke-Native (Join-Path $PSScriptRoot 'certmusectl.ps1') (@('deployer') + $deployerArgs + @('-dry-run'))

    Write-Stage '正式部署（包含数据库迁移）'
    Invoke-Native (Join-Path $PSScriptRoot 'certmusectl.ps1') (@('deployer') + $deployerArgs)

    Write-Stage '部署后验收'
    $homeResponse = Wait-HttpOk 'https://cm.jklin.me/'
    $learningResponse = Wait-HttpOk 'https://cm.jklin.me/learning/home'
    if ($learningResponse.Content -notmatch '/student-assets/') {
        throw '学习端入口未引用 /student-assets/，生产 Nginx 可能仍在返回管理端 index.html。'
    }
    $api = Wait-HttpOk 'https://cm.jklin.me/prod-api/auth/code'
    $srvctl = Join-Path $projectRoot 'tools/ops/local/srv-ops/bin/srvctl.exe'
    $srvConfig = Join-Path $projectRoot 'tools/ops/local/srv-ops/.srv-ops-local/config.yaml'
    $srvEnv = Join-Path $projectRoot 'tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env'
    Invoke-Native $srvctl @('-config', $srvConfig, '-env', $srvEnv, 'exec', 'certmuse-app', 'systemctl is-active certmuse.service')

    Write-Stage '最终一致性 dry-run'
    $finalOutput = Invoke-Native (Join-Path $PSScriptRoot 'certmusectl.ps1') (@('deployer') + $deployerArgs + @('-dry-run')) -Capture
    if (($finalOutput -join "`n") -notmatch 'No changes detected, everything is up to date') {
        throw '最终 dry-run 未报告完全一致，可能仍有文件或配置未同步。'
    }

    $elapsed = New-TimeSpan -Start $startedAt -End (Get-Date)
    Write-Host "`n部署成功：代码、数据库迁移、服务和页面均已通过检查。耗时 $([math]::Round($elapsed.TotalMinutes, 1)) 分钟。" -ForegroundColor Green
} catch {
    $advice = Get-Advice $currentStage
    Write-Host "`n部署已停止，没有继续执行后续步骤。" -ForegroundColor Red
    Write-Host "失败阶段：$currentStage" -ForegroundColor Red
    Write-Host "错误原因：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host "处理建议：$advice" -ForegroundColor Yellow
    exit 1
}
