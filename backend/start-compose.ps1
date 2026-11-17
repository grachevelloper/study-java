$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
docker compose --env-file (Join-Path $root ".env") -f (Join-Path $PSScriptRoot "docker-compose.yml") up -d --build --wait
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
