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

Están implementados el modelo de dominio, los casos de uso de creación de cursos
y grupos/proyectos, la solicitud de ingreso y su aprobación o rechazo, la
incorporación de alumnos admitidos a grupos, y los
adaptadores PostgreSQL para guardar y consultar cursos, proyectos e inscripciones.
Las inscripciones incluyen protección contra duplicados y decisiones
concurrentes, conservando el historial de rechazos. Las pruebas unitarias simulan
los puertos; las de integración usan repositorios reales y PostgreSQL temporal.

El repositorio de participaciones todavía está simulado en las pruebas de
aplicación; su tabla y su adaptador corresponden al siguiente incremento.

El controller, la autenticación, el adaptador HTTP de Usuarios y la composición
de los casos de uso están pendientes. La integración real con Usuarios se deja
para el final; sus consultas siguen simuladas en las pruebas de aplicación,
manteniendo las comprobaciones de rol y estado. Aún no existen endpoints para
crear cursos o proyectos.

Consultar [creación de proyectos](docs/create-project.md) para el flujo y sus
reglas de autorización.

Consultar [admisión al curso](docs/course-enrollment.md) para el flujo de
solicitud, aprobación, rechazo y nueva solicitud tras un rechazo.

Consultar [incorporación al proyecto](docs/join-project.md) para las condiciones
de admisión y exclusividad de grupo dentro de cada curso.

Todas las dependencias, configuraciones y pruebas propias de Proyectos deben
permanecer dentro de esta carpeta. Los demás microservicios se incorporarán como
carpetas hermanas en la raíz del monorepo.
