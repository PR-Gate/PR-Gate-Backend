# ai-service

Java 21 / Spring Boot 3.4.4. Arquitectura hexagonal y DDD.
Un unico contexto con domain, application, infrastructure y bootstrap.

Ver [arquitectura](docs/architecture.md). Estructura inicial sin endpoints ni
adaptadores OpenAI/SonarQube implementados. No realiza llamadas API.

Compilar: mvn verify
Ejecutar: mvn spring-boot:run (puerto 8081).

## Dominio
Implementados agregados, objetos de valor, politica de catalogo y contratos de repositorio.
Ver [modelo de dominio](docs/domain.md). Pruebas: mvn test (requiere Java 21 y Maven).
