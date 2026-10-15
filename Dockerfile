# Etapa 1: Build de la aplicacion
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copiar wrapper de Gradle y archivos de dependencias para aprovechar cache de capas
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copiar codigo fuente y compilar artefacto (las pruebas ya fueron validadas en el paso de CI)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Etapa 2: Runtime ligero
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios para mayor seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]