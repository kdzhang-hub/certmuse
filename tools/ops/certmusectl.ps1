[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Command,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$ErrorActionPreference = 'Stop'

switch ($Command) {
    'install' {
        & (Join-Path $PSScriptRoot 'install-agent-tools.ps1')
    }
    'doctor' {
        & (Join-Path $PSScriptRoot 'doctor.ps1')
    }
    'deployer' {
        $deployerDir = Join-Path $PSScriptRoot 'local/routeflow/tools/deployer'
        Push-Location $deployerDir
        try {
            & (Join-Path $deployerDir 'deployer.exe') @Arguments
        } finally {
            Pop-Location
        }
    }
    'references' {
        & (Join-Path $PSScriptRoot 'sync-reference-material.ps1') @Arguments
    }
    default {
        throw 'Usage: .\tools\ops\certmusectl.ps1 {install|doctor|deployer <args...>|references <push|pull> <source|work|archive> <relative-file>}'
    }
}
