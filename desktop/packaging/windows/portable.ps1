[CmdletBinding()]
param(
    [string]$RepositoryRoot,
    [string]$DistributionDir,
    [switch]$Build,
    [switch]$VerifyOnly,
    [switch]$SkipLaunchValidation,
    [string]$DataDir,
    [int]$LaunchTimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
}
if ([string]::IsNullOrWhiteSpace($DistributionDir)) {
    $DistributionDir = Join-Path $RepositoryRoot 'desktop\app\build\compose\binaries\main\app\Legado'
}

function Resolve-FullPath {
    param([Parameter(Mandatory)][string]$Path)

    return [System.IO.Path]::GetFullPath($Path)
}

function Assert-RequiredFile {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Portable distribution is missing required file: $Path"
    }
}

function Assert-RequiredDirectory {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "Portable distribution is missing required directory: $Path"
    }
}

function Assert-PortableDistribution {
    param([Parameter(Mandatory)][string]$Path)

    $resolvedPath = Resolve-FullPath $Path
    Assert-RequiredFile (Join-Path $resolvedPath 'Legado.exe')
    Assert-RequiredFile (Join-Path $resolvedPath 'portable.flag')
    Assert-RequiredDirectory (Join-Path $resolvedPath 'app')
    Assert-RequiredFile (Join-Path $resolvedPath 'runtime\bin\server\jvm.dll')
    Assert-RequiredDirectory (Join-Path $resolvedPath 'runtime')
    Assert-RequiredFile (Join-Path $resolvedPath 'runtime\release')
    Assert-RequiredFile (Join-Path $resolvedPath 'app\.jpackage.xml')
    Assert-RequiredFile (Join-Path $resolvedPath 'app\Legado.cfg')

    $applicationJar = Get-ChildItem -LiteralPath (Join-Path $resolvedPath 'app') -Filter '*.jar' -File | Select-Object -First 1
    if ($null -eq $applicationJar) {
        throw "Portable distribution is missing application JARs: $(Join-Path $resolvedPath 'app')"
    }

    return $resolvedPath
}

function Invoke-PortableBuild {
    param([Parameter(Mandatory)][string]$Root)

    Push-Location $Root
    try {
        & '.\gradlew.bat' ':desktop:app:createDistributable' '--no-daemon' '--max-workers=1'
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle createDistributable failed with exit code $LASTEXITCODE."
        }
        $distribution = Resolve-FullPath (Join-Path $Root 'desktop\app\build\compose\binaries\main\app\Legado')
        New-Item -ItemType File -Path (Join-Path $distribution 'portable.flag') -Force | Out-Null
    } finally {
        Pop-Location
    }
}

function Assert-PathWithin {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Root
    )

    $resolvedPath = (Resolve-FullPath $Path).TrimEnd('\') + '\'
    $resolvedRoot = (Resolve-FullPath $Root).TrimEnd('\') + '\'
    if (-not $resolvedPath.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to operate outside the intended Portable root. Path='$Path', Root='$Root'."
    }
}

function Get-PortableProcessSnapshot {
    param([Parameter(Mandatory)][string]$ExecutablePath)

    $normalizedPath = Resolve-FullPath $ExecutablePath
    return @(
        Get-Process -Name ([System.IO.Path]::GetFileNameWithoutExtension($normalizedPath)) -ErrorAction SilentlyContinue |
            Where-Object {
                try {
                    $_.Path -eq $normalizedPath
                } catch {
                    $false
                }
            } |
            ForEach-Object { $_.Id }
    )
}

function Stop-PortableProcesses {
    param(
        [Parameter(Mandatory)][string]$ExecutablePath,
        [int[]]$InitialProcessIds = @(),
        [int]$TimeoutMilliseconds = 10000
    )

    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMilliseconds)
    do {
        $remainingProcessIds = @(
            Get-PortableProcessSnapshot $ExecutablePath |
                Where-Object { $InitialProcessIds -notcontains $_ }
        )
        foreach ($processId in $remainingProcessIds) {
            & taskkill.exe /PID $processId /T /F 2>$null | Out-Null
        }
        if ($remainingProcessIds.Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)

    $remainingProcessIds = @(
        Get-PortableProcessSnapshot $ExecutablePath |
            Where-Object { $InitialProcessIds -notcontains $_ }
    )
    foreach ($processId in $remainingProcessIds) {
        & taskkill.exe /PID $processId /T /F 2>$null | Out-Null
    }

    $forceDeadline = [DateTime]::UtcNow.AddMilliseconds(5000)
    do {
        $remainingProcessIds = @(Get-PortableProcessSnapshot $ExecutablePath | Where-Object { $InitialProcessIds -notcontains $_ })
        if ($remainingProcessIds.Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $forceDeadline)

    throw "Portable executable processes did not exit: $($remainingProcessIds -join ', ')"
}

function Remove-PortableValidationRoot {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$TempRoot
    )

    Assert-PathWithin $Path $TempRoot
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        try {
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            return
        } catch {
            if ([DateTime]::UtcNow -ge $deadline) {
                throw "Unable to clean Portable validation directory '$Path': $($_.Exception.Message)"
            }
            Start-Sleep -Milliseconds 250
        }
    } while ($true)
}

function Invoke-PortableLaunchValidation {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Root,
        [int]$TimeoutSeconds
    )

    $validationRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("legado-portable-validation-" + [guid]::NewGuid().ToString('N'))
    $usesTemporaryDataDir = [string]::IsNullOrWhiteSpace($DataDir)
    $validationDataDir = if ($usesTemporaryDataDir) { Join-Path $validationRoot 'data' } else { Resolve-FullPath $DataDir }
    New-Item -ItemType Directory -Path $validationDataDir -Force | Out-Null
    if ($usesTemporaryDataDir) {
        Assert-PathWithin $validationDataDir $validationRoot
    }

    $process = $null
    try {
        $executablePath = Join-Path $Path 'Legado.exe'
        $initialProcessIds = @(Get-PortableProcessSnapshot $executablePath)
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = $executablePath
        $startInfo.WorkingDirectory = $Path
        $startInfo.UseShellExecute = $false
        $startInfo.Arguments = '--validate-startup'
        $startInfo.EnvironmentVariables['LEGADO_DATA_DIR'] = $validationDataDir
        $process = [System.Diagnostics.Process]::Start($startInfo)

        $databasePath = Join-Path $validationDataDir 'legado.db'
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 250
            if (Test-Path -LiteralPath $databasePath -PathType Leaf) {
                break
            }
            if ($process.HasExited) {
                throw "Portable executable exited before creating its database (exit code $($process.ExitCode))."
            }
        } while ([DateTime]::UtcNow -lt $deadline)

        if (-not (Test-Path -LiteralPath $databasePath -PathType Leaf)) {
            throw "Portable executable did not create $databasePath within $TimeoutSeconds seconds."
        }
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            throw "Portable executable did not exit after startup validation within $TimeoutSeconds seconds."
        }
        if ($process.ExitCode -ne 0) {
            throw "Portable executable failed startup validation with exit code $($process.ExitCode)."
        }
    } finally {
        if ($null -ne $process) {
            $process.WaitForExit(1000) | Out-Null
        }
        if ($null -ne $executablePath) {
            Stop-PortableProcesses `
                -ExecutablePath $executablePath `
                -InitialProcessIds $initialProcessIds
        }

        if ($usesTemporaryDataDir -and (Test-Path -LiteralPath $validationRoot)) {
            Remove-PortableValidationRoot `
                -Path $validationRoot `
                -TempRoot ([System.IO.Path]::GetTempPath())
        }
    }
}

$RepositoryRoot = Resolve-FullPath $RepositoryRoot
$DistributionDir = Resolve-FullPath $DistributionDir

if (-not $VerifyOnly -and $Build) {
    Invoke-PortableBuild $RepositoryRoot
}

$resolvedDistribution = Assert-PortableDistribution $DistributionDir

if (-not $SkipLaunchValidation) {
    Invoke-PortableLaunchValidation -Path $resolvedDistribution -Root $RepositoryRoot -TimeoutSeconds $LaunchTimeoutSeconds
}

Write-Output "Portable distribution verified: $resolvedDistribution"
