# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Сначала копируем только pom.xml (для кэширования зависимостей)
COPY pom.xml .

# Копируем весь исходный код
COPY . .

# Собираем проект
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копируем собранный jar
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]