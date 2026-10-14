param([switch]$Verify)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$ideaRoot = Join-Path $env:ProgramFiles 'JetBrains'
$idea = $null
if (Test-Path -LiteralPath $ideaRoot) {
    $idea = Get-ChildItem -LiteralPath $ideaRoot -Directory -Filter 'IntelliJIdea*' |
        Sort-Object Name -Descending | Select-Object -First 1
}
if (-not $env:JAVA_HOME -and $idea) {
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
if ($Verify) {
    & $mavenPath '-Dstore.fx.tests=true' verify
} else {
    & $mavenPath javafx:run
}
exit $LASTEXITCODE
