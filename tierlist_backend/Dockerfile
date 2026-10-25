# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Зависимости — с кэшем Maven, чтобы не качались каждый раз
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

# Исходники
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r app && useradd -r -g app app \
 && mkdir -p /app/uploads \
 && chown -R app:app /app

# Копируем ГОТОВЫЙ jar под понятным именем
COPY --from=builder /app/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]