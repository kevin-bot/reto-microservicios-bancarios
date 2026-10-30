# Etapa 1: compilación. Las pruebas no se ejecutan aquí porque Testcontainers necesita Docker;
# se ejecutan con ./mvnw test (ver README).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Etapa 2: imagen de ejecución mínima, sin JDK ni Maven, con usuario no root.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/ms-clientes-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
