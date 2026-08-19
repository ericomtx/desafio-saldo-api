# Build multi-stage: compila o módulo web (e o common, do qual depende)
# dentro do próprio container, sem precisar de JAR pré-construído.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY web/pom.xml web/pom.xml
COPY consumer/pom.xml consumer/pom.xml
# Baixa dependências antes de copiar o código — cache de camada Docker
RUN mvn -pl web -am dependency:go-offline -B
COPY common/src common/src
COPY web/src web/src
RUN mvn -pl web -am package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/web/target/saldo-api-web.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
