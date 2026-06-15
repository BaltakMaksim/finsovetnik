# Этап 1: Сборка React
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Этап 2: Запуск статики через serve
FROM node:20-alpine
WORKDIR /app

# Устанавливаем serve глобально
RUN npm install -g serve

# Копируем dist прямо в рабочую директорию контейнера
COPY --from=builder /app/dist ./dist

EXPOSE 3000

# Теперь путь относительный и точно существует в WORKDIR
CMD ["serve", "-s", "dist", "-l", "3000"]