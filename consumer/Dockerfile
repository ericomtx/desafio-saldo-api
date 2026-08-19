FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY web/pom.xml web/pom.xml
COPY consumer/pom.xml consumer/pom.xml
RUN mvn -pl consumer -am dependency:go-offline -B
COPY common/src common/src
COPY consumer/src consumer/src
RUN mvn -pl consumer -am package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/consumer/target/saldo-api-consumer.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
