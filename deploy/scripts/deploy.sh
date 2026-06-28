#!/bin/bash

set -e

echo "🚀 Начинаем деплой ФинСоветник..."

# Переходим в папку deploy
cd "$(dirname "$0")/.."

# Проверяем наличие .env
if [ ! -f .env ]; then
    echo "❌ Файл .env не найден!"
    echo "📋 Скопируйте .env.example в .env и заполните переменные:"
    echo "   cp .env.example .env"
    exit 1
fi

# Загружаем переменные окружения
set -a
source .env
set +a

echo "📦 Собираем образы..."
docker compose -f docker-compose.prod.yml build

echo "🛑 Останавливаем старые контейнеры..."
docker compose -f docker-compose.prod.yml down

echo "▶️  Запускаем сервисы..."
docker compose -f docker-compose.prod.yml up -d

echo " Ждём запуска сервисов..."
sleep 10

echo "📊 Статус контейнеров:"
docker compose -f docker-compose.prod.yml ps

echo "✅ Деплой завершён!"
echo "🌐 Сайт доступен: https://${DOMAIN}"
echo "🔐 SSH туннель к БД: ./scripts/ssh-tunnel.sh"