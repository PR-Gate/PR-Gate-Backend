FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip \
    && curl -fsSL https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-8.1.0.6389.zip -o /tmp/scanner.zip \
    && unzip -q /tmp/scanner.zip -d /opt \
    && mv /opt/sonar-scanner-8.1.0.6389 /opt/sonar-scanner \
    && chmod +x /opt/sonar-scanner/bin/sonar-scanner \
    && rm -f /tmp/scanner.zip \
    && rm -rf /var/lib/apt/lists/*
ENV SONAR_SCANNER_HOME=/opt/sonar-scanner
ENV SONAR_USER_HOME=/tmp/sonar
RUN groupadd --system prgate && useradd --system --gid prgate --home-dir /app prgate
WORKDIR /app
COPY --from=build --chown=prgate:prgate /workspace/target/cicd-service-0.0.1-SNAPSHOT.jar app.jar
USER prgate
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
