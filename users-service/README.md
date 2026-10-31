# PRGate - Microservicio de Usuarios

Administra perfiles locales, el vínculo con Cognito (`cognitoSub`), roles
(`STUDENT`, `TEACHER`, `ADMIN`) y estado (`ACTIVE`, `INACTIVE`).

## Arranque

El servicio usa Spring Data JPA y PostgreSQL. Copia `.env.example` a `.env`
si aún no tienes ese archivo. Docker Compose inicia solo el backend y usa
las variables `SPRING_DATASOURCE_*` del archivo `.env`.

Para conectar desde Docker Desktop a PostgreSQL en tu PC, usa
`jdbc:postgresql://host.docker.internal:5432/PR-Gate` como URL y completa el
usuario y la contraseña de ese servidor. PostgreSQL debe estar iniciado.
Para ejecutar desde el IDE, usa `localhost` en lugar de `host.docker.internal`
y carga las variables en el proceso Java; Spring Boot no lee `.env` automáticamente.

Hibernate actualiza las tablas automáticamente para desarrollo (`ddl-auto=update`).
En producción, administra el esquema con migraciones y configura
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. Las pruebas de contrato usan H2
con el perfil `test` y no necesitan PostgreSQL.

```powershell
docker compose --env-file .env up --build
```

El servicio queda en `http://localhost:8080` y la documentación OpenAPI en
`http://localhost:8080/swagger-ui.html`.

Variables de entorno principales:

| Variable | Descripción | Default |
| --- | --- | --- |
| `SERVER_PORT` | Puerto HTTP | `8080` |
| `INTERNAL_SERVICE_TOKEN` | Token que Proyectos envía a `/internal/**` | `dev-only-internal-token` |

En entornos reales `INTERNAL_SERVICE_TOKEN` debe definirse explícitamente y
compartirse solo con el servicio de Proyectos. El servicio no arranca si el
token está vacío.

## Contrato interno para Proyectos

```http
GET /internal/v1/users/{userId}
Authorization: Bearer <INTERNAL_SERVICE_TOKEN>
```

Respuesta `200 OK`:

```json
{
  "id": "f66317a1-d467-4736-bc2e-e01f9499bc3f",
  "firstName": "Ana",
  "lastName": "Torres",
  "role": "TEACHER",
  "status": "ACTIVE"
}
```

Un usuario `INACTIVE` sigue devolviendo `200`; Proyectos debe revisar `status`.

| Código | Significado |
| --- | --- |
| `200` | Usuario existente; revisar `role` y `status`. |
| `400` | UUID con formato inválido. |
| `401` | Falta la credencial o el esquema no es `Bearer`. |
| `403` | La credencial presentada no está autorizada. |
| `404` | UUID válido, pero el usuario no existe. |
| `5xx` | Fallo del servicio; no interpretar como usuario inexistente. |

Ejemplos en `docs/demo.http`. El esquema OpenAPI generado por springdoc incluye
la ruta y el esquema de seguridad `serviceBearer`.

## Pruebas

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn test
```

`InternalUserContractTest` cubre usuario activo, inactivo, inexistente, UUID
inválido, credencial ausente y credencial no autorizada.

## Pendiente de acuerdo con Proyectos

- Autenticación de las rutas de personas con JWT de Cognito y resolución del
  usuario local por `sub` (hoy las rutas `/users` no validan tokens).
- Autorización `ADMIN` para cambiar rol y estado.
- Publicación de eventos de dominio.
