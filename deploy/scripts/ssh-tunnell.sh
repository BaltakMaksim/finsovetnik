#!/bin/bash

# Загрузка переменных окружения
source ../.env

echo "🔐 Устанавливаем SSH туннель к БД..."

# Создаём SSH туннель
ssh -f -N -L ${DB_SSH_TUNNEL_PORT}:localhost:${POSTGRES_PORT} \
  -i ${SSH_KEY_PATH} \
  -o StrictHostKeyChecking=no \
  -o ServerAliveInterval=60 \
  -o ServerAliveCountMax=3 \
  ${SSH_USER}@${SSH_HOST}

echo "✅ SSH туннель установлен: localhost:${DB_SSH_TUNNEL_PORT} -> ${SSH_HOST}:${POSTGRES_PORT}"
echo "📊 Подключиться можно через: psql -h localhost -p ${DB_SSH_TUNNEL_PORT} -U ${POSTGRES_USER} -d ${POSTGRES_DB}"