# PRGate Projects Service

Microservicio de cursos y proyectos académicos. Incluye admisión de alumnos,
participación en grupos, designación de líderes y vinculación de organizaciones
de GitHub en su modelo de dominio.

## Estructura

```text
projects-service/
├── pom.xml
├── .gitignore
├── README.md
├── docs/
└── src/
    ├── main/
    │   ├── java/com/prgate/projects/
    │   │   ├── ProjectsServiceApplication.java
    │   │   ├── domain/
    │   │   ├── application/
    │   │   ├── infrastructure/
    │   │   └── interfaces/
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    └── test/
        ├── java/com/prgate/projects/
        └── resources/
```

Arquitectura hexagonal con DDD ligera. Dominio y aplicación se mantienen
independientes de Spring, JPA y HTTP. Infraestructura implementa los puertos.

## Verificación

Requisitos: Java 21, Maven y Docker activo para las pruebas de integración.
Ejecutar desde esta carpeta:

```powershell
mvn test
mvn verify
```

`test` ejecuta pruebas unitarias y de arquitectura. `verify` incluye las pruebas
de integración con PostgreSQL temporal mediante Testcontainers y genera el JAR
en `target/`. Ese directorio se excluye de Git con el `.gitignore` del servicio.

## PostgreSQL

La aplicación utiliza `DB_USERNAME` y `DB_PASSWORD` como variables obligatorias.
`DB_URL` es opcional y por defecto apunta a
`jdbc:postgresql://localhost:5432/prgate_projects`.

Flyway gestiona las migraciones; Hibernate valida el esquema. Consultar
[persistencia de cursos](docs/course-persistence.md) para detalles y alcance.

## Estado de implementación

Están implementados el modelo de dominio, el caso de uso de creación de cursos
y su adaptador PostgreSQL. El controller, la autenticación, el adaptador HTTP
de Usuarios y la composición del caso de uso todavía están pendientes. Los
puertos se simulan en las pruebas de aplicación; aún no hay un endpoint para
crear cursos.

Todas las dependencias, configuraciones y pruebas propias de Proyectos deben
permanecer dentro de esta carpeta. Los demás microservicios se incorporarán como
carpetas hermanas en la raíz del monorepo.
