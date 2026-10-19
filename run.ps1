param([switch]$Verify)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$ideaRoot = Join-Path $env:ProgramFiles 'JetBrains'
$idea = $null
if (Test-Path -LiteralPath $ideaRoot) {
    $idea = Get-ChildItem -LiteralPath $ideaRoot -Directory -Filter 'IntelliJIdea*' |
        Sort-Object Name -Descending | Select-Object -First 1
}
if ($idea) {
    $javaHome = Join-Path $idea.FullName 'jbr'
    if (Test-Path (Join-Path $javaHome 'bin\javac.exe')) {
        $env:JAVA_HOME = $javaHome
    }
}
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) {
    $mavenPath = $maven.Source
} elseif ($idea) {
    $mavenPath = Join-Path $idea.FullName 'plugins\maven\lib\maven3\bin\mvn.cmd'
} else {
    throw 'Maven 3.9+ не найден. Установите Maven или откройте проект в IDEA.'
}
if (-not (Test-Path -LiteralPath $mavenPath)) {
    throw 'Maven не найден. Проверьте установку IntelliJ IDEA или PATH.'
}
if (-not (Test-Path -LiteralPath '.env')) {
    throw 'Создайте .env по .env.example и укажите настройки PostgreSQL КР №1.'
}

$docker = Get-Command docker.exe -ErrorAction SilentlyContinue
if (-not $docker) {
    throw 'Docker не найден. Установите Docker Desktop.'
}

function Test-DockerReady {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $docker.Source info --format '{{.ServerVersion}}' 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

if (-not (Test-DockerReady)) {
    $dockerDesktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        throw 'Docker не запущен. Запустите Docker Desktop и повторите команду.'
    }
    Write-Host 'Запускаю Docker Desktop...'
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Seconds 2
        if (Test-DockerReady) { break }
    }
    if (-not (Test-DockerReady)) {
        throw 'Docker Desktop не стал доступен за 2 минуты. Проверьте его состояние.'
    }
}

Write-Host 'Запускаю PostgreSQL и жду готовности...'
& $docker.Source compose up -d --wait
if ($LASTEXITCODE -ne 0) {
    throw 'Не удалось запустить PostgreSQL через Docker Compose.'
}

if ($Verify) {
    & $mavenPath '-Dstore.fx.tests=true' verify
} else {
    & $mavenPath javafx:run
}
exit $LASTEXITCODE
