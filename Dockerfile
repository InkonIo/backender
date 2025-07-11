# Стадия сборки: используем Maven с JDK 17
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Копируем pom.xml и скачиваем зависимости
COPY pom.xml ./
RUN mvn dependency:go-offline

# Копируем остальной код
COPY . .

# Собираем проект без тестов
RUN mvn clean package -DskipTests

# Стадия запуска: JRE для лёгкости
FROM eclipse-temurin:17-jre

WORKDIR /app

# Копируем собранный JAR из билдера
COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]

EXPOSE 8080
