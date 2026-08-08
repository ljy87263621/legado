param(
    [string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

$portableScript = Join-Path $PSScriptRoot 'portable.ps1'
$existingDistribution = Join-Path $RepositoryRoot 'desktop\app\build\compose\binaries\main\app\Legado'
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("legado-portable-test-" + [guid]::NewGuid().ToString('N'))
$incompleteDistribution = Join-Path $temporaryRoot 'incomplete'
$validationDirectoriesBefore = @(
    Get-ChildItem ([System.IO.Path]::GetTempPath()) -Directory -Filter 'legado-portable-validation-*' -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
)

try {
    New-Item -ItemType Directory -Path (Join-Path $incompleteDistribution 'app') -Force | Out-Null
    New-Item -ItemType File -Path (Join-Path $incompleteDistribution 'Legado.exe') -Force | Out-Null
    New-Item -ItemType File -Path (Join-Path $incompleteDistribution 'portable.flag') -Force | Out-Null
    New-Item -ItemType File -Path (Join-Path $incompleteDistribution 'app\.jpackage.xml') -Force | Out-Null
    New-Item -ItemType File -Path (Join-Path $incompleteDistribution 'app\Legado.cfg') -Force | Out-Null
    New-Item -ItemType File -Path (Join-Path $incompleteDistribution 'app\app.jar') -Force | Out-Null

    try {
        $incompleteOutput = & $portableScript `
            -RepositoryRoot $RepositoryRoot `
            -DistributionDir $incompleteDistribution `
            -VerifyOnly `
            2>&1

        throw "Incomplete Portable directory was accepted: $($incompleteOutput -join [Environment]::NewLine)"
    } catch {
        Assert-True (
            $_.Exception.Message -match 'runtime[\\/]bin[\\/]server[\\/]jvm\.dll'
        ) "Incomplete Portable directory did not report the missing bundled JVM: $($_.Exception.Message)"
    }
Assert-True (Test-Path (Join-Path $existingDistribution 'Legado.exe')) 'The built Portable executable is missing.'
Assert-True (Test-Path (Join-Path $existingDistribution 'portable.flag')) 'The Portable mode marker is missing.'
Assert-True (Test-Path (Join-Path $existingDistribution 'runtime\bin\server\jvm.dll')) 'The built bundled JVM is missing.'
$runtimeRelease = Get-Content -LiteralPath (Join-Path $existingDistribution 'runtime\release') -Raw
Assert-True ($runtimeRelease -match '(?m)^MODULES=.*(?:^|\s)java\.net\.http(?:\s|$)') 'The bundled JVM is missing the java.net.http module.'

& $portableScript `
    -RepositoryRoot $RepositoryRoot `
    -DistributionDir $existingDistribution `
    -VerifyOnly `
    -SkipLaunchValidation

$directoryVerificationSucceeded = $?
if (-not $directoryVerificationSucceeded) {
    throw 'Complete Portable directory verification failed.'
}

$launchOutput = & $portableScript `
    -RepositoryRoot $RepositoryRoot `
    -DistributionDir $existingDistribution `
    -VerifyOnly `
    -LaunchTimeoutSeconds 45 `
    2>&1

$launchVerificationSucceeded = $?
if (-not $launchVerificationSucceeded) {
    throw "Complete Portable launch verification failed: $($launchOutput -join [Environment]::NewLine)"
}

$validationDirectoriesAfter = @(
    Get-ChildItem ([System.IO.Path]::GetTempPath()) -Directory -Filter 'legado-portable-validation-*' -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
)
$newValidationDirectories = @($validationDirectoriesAfter | Where-Object { $validationDirectoriesBefore -notcontains $_ })
Assert-True ($newValidationDirectories.Count -eq 0) "Portable launch validation left temporary directories: $($newValidationDirectories -join ', ')"

Write-Output 'Portable packaging verification tests passed.'
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
