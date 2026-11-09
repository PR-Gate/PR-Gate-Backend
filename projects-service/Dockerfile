FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system prgate && useradd --system --gid prgate --home-dir /app prgate
WORKDIR /app
COPY --from=build --chown=prgate:prgate /workspace/target/projects-service-0.0.1-SNAPSHOT.jar app.jar
USER prgate
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
