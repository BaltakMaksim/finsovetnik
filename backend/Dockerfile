FROM eclipse-temurin:21-jdk
WORKDIR /app

# Копируем файлы сборки
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Проверяем, что скопировалось
RUN ls -la && ls -la .mvn

# Копируем исходники
COPY src src

# Делаем mvnw исполняемым
RUN chmod +x mvnw

# Собираем с выводом всех ошибок
RUN ./mvnw clean package -DskipTests -B

# Проверяем, что JAR создался
RUN ls -la target/

# Копируем JAR
RUN cp target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]