# Загрузка переменных из .env
Get-Content ..\.env | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]+)=(.*)$") {
        Set-Item -Path "env:$($matches[1])" -Value $matches[2].Trim('"').Trim("'")
    }
}

Write-Host "🔐 Устанавливаем SSH туннель к БД..." -ForegroundColor Cyan

# Проверяем, не занят ли порт
$portInUse = netstat -ano | Select-String ":$env:DB_SSH_TUNNEL_PORT\s"
if ($portInUse) {
    Write-Host "⚠️  Порт $env:DB_SSH_TUNNEL_PORT уже занят" -ForegroundColor Yellow
    Write-Host "💡 Возможно, туннель уже запущен" -ForegroundColor Yellow
    exit 1
}

# Создаём SSH туннель
ssh -f -N -L "${env:DB_SSH_TUNNEL_PORT}:localhost:${env:POSTGRES_PORT}" `
    -i $env:SSH_KEY_PATH `
    -o StrictHostKeyChecking=no `
    -o ServerAliveInterval=60 `
    -o ServerAliveCountMax=3 `
    "${env:SSH_USER}@${env:SSH_HOST}"

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ SSH туннель установлен: localhost:${env:DB_SSH_TUNNEL_PORT} -> ${env:SSH_HOST}:${env:POSTGRES_PORT}" -ForegroundColor Green
    Write-Host "📊 Подключиться в pgAdmin:" -ForegroundColor Green
    Write-Host "   Host: localhost" -ForegroundColor White
    Write-Host "   Port: ${env:DB_SSH_TUNNEL_PORT}" -ForegroundColor White
    Write-Host "   User: ${env:POSTGRES_USER}" -ForegroundColor White
    Write-Host "   DB: ${env:POSTGRES_DB}" -ForegroundColor White
} else {
    Write-Host "❌ Ошибка создания туннеля" -ForegroundColor Red
    exit 1
}