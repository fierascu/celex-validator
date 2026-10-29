## Stage 1 – build
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn package -DskipTests -q

## Stage 2 – runtime
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd -r -u 1001 app

COPY --from=build --chown=app /build/target/quarkus-app/lib/           lib/
COPY --from=build --chown=app /build/target/quarkus-app/*.jar          ./
COPY --from=build --chown=app /build/target/quarkus-app/app/           app/
COPY --from=build --chown=app /build/target/quarkus-app/quarkus/       quarkus/

USER app
EXPOSE 8080
ENV QUARKUS_HTTP_HOST=0.0.0.0

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "quarkus-run.jar"]
