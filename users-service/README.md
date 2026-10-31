# PRGate Users Service

Microservicio de perfiles, roles, estados y vínculo local con el identificador
`cognitoSub`. Usa Java 21, Spring Boot, PostgreSQL y Flyway.

## Ejecución local

1. Crea la base PostgreSQL `pr-gate` si todavía no existe.
2. Copia `.env.example` a `.env` y coloca tu usuario y contraseña de PostgreSQL.
   Sustituye `INTERNAL_SERVICE_TOKEN` por un valor secreto compartido únicamente
   con el futuro adaptador de Projects.
3. Desde esta carpeta ejecuta:

```powershell
mvn spring-boot:run
```

El servicio escucha en `http://localhost:8080`. Flyway crea el esquema `users`
y la tabla `users.users`; Hibernate valida la estructura y conserva los datos
entre reinicios. La documentación está en `/swagger-ui/index.html`.

La base `pr-gate` es la misma instancia local que usan los otros servicios,
pero cada servicio administra su propio esquema. Los datos de una instalación
anterior en otra base (por ejemplo `prgate_users`) no se trasladan automáticamente.

## Contrato interno

```http
GET /internal/v1/users/{userId}
Authorization: Bearer <INTERNAL_SERVICE_TOKEN>
```

Devuelve `id`, `firstName`, `lastName`, `role` y `status`. El token es obligatorio
al arrancar y no tiene un valor predeterminado. Projects todavía no consume esta
ruta; su integración se hará después.

## Docker

El `compose.yml` de la raíz construye este servicio. Si PostgreSQL corre en
Windows fuera de Docker, cambia `DB_URL` en `.env` a
`jdbc:postgresql://host.docker.internal:5432/pr-gate` antes de usar Compose.
Para ejecutar con `mvn spring-boot:run`, usa `localhost` en esa URL.

## Estado de seguridad

Las rutas `/users` todavía no validan identidad ni autorización. En particular,
el registro y los cambios de rol y estado requieren el mecanismo de autenticación
que el equipo aún no ha implementado. Mantén este servicio limitado al entorno
local hasta completar ese control. El contrato `/internal/**` sí exige su token.

## Pruebas

```powershell
mvn test
```

Las pruebas usan H2 y ejecutan la migración Flyway del esquema `users`.
