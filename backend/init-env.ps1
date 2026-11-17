$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"

if (Test-Path $envFile) {
    $values = Get-Content $envFile
} else {
    $values = Get-Content (Join-Path $root ".env.example")
}

function New-Secret($length) {
    $bytes = New-Object byte[] $length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    } finally {
        $rng.Dispose()
    }
}

$secrets = @{
    JWT_SECRET_BASE64 = (New-Secret 32)
    BOOTSTRAP_ADMIN_PASSWORD = ("Adm1!" + [Guid]::NewGuid().ToString("N"))
}
foreach ($name in $secrets.Keys) {
    if (-not ($values -match "^$name=.+$")) {
        $values = @($values -notmatch "^$name=") + "$name=$($secrets[$name])"
    }
}

$values | Set-Content -Encoding Ascii $envFile
Write-Host "Параметры запуска и временный пароль находятся в .env"
