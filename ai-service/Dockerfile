FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/data && chown app:app /app/data
COPY --from=build --chown=app:app /workspace/target/ai-service-*.jar /app/app.jar
USER app
EXPOSE 8081
ENV SERVER_PORT=8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]