# Projects individual con Docker

El Compose arranca solo Projects y su PostgreSQL. No depende de GitHub, Users
ni Keycloak para iniciar. Projects sigue en perfil de desarrollo `local`:
sus rutas protegidas esperan `X-User-Id`, no un JWT de Keycloak todavía.

En tu archivo `projects-service/.env`, configura tú estos valores:

```properties
DB_PASSWORD=elige-una-clave-local
PROJECTS_PORT=8083
POSTGRES_PORT=5433
PROJECTS_LOCAL_USERS=
```

No copiar la plantilla encima de un `.env` existente. No se necesita compartir
ninguna contraseña. El Compose fija base y usuario `prgate_projects`, y la
conexión interna `postgres:5432`; tus valores de `DB_URL` de Windows no se usan.
El puerto 5433 se publica para consultar la base desde pgAdmin si lo necesitas.

Desde la raíz del backend:

```sh
docker compose --env-file projects-service/.env -p prgate-projects -f projects-service/compose.yaml up -d --build projects
```

Cuando termine:

- Salud: http://localhost:8083/actuator/health
- Swagger: http://localhost:8083/swagger-ui/index.html

```sh
docker compose --env-file projects-service/.env -p prgate-projects -f projects-service/compose.yaml logs -f projects
```

La base `prgate_projects` es nueva, tiene volumen propio y Flyway crea las tablas.
No borra ni importa los datos de `pr-gate` o del volumen anterior `projects-data`.
La contraseña inicializa volúmenes nuevos: si se cambia más adelante, hay que
actualizar también el rol PostgreSQL existente.

Para probar reglas de negocio con usuarios locales, completar
`PROJECTS_LOCAL_USERS` con entradas `UUID:ROLE:STATUS` separadas por `;`.
Ejemplo de formato: `UUID_DEL_DOCENTE:TEACHER:ACTIVE;UUID_DEL_ALUMNO:STUDENT:ACTIVE`.
Sustituir los identificadores por UUID reales. Enviar el UUID correspondiente
en `X-User-Id`. Este mecanismo no conecta los perfiles de Users con Projects.

Para siguientes arranques, omitir `--build` cuando no haya cambios de código.
Para detener conservando los datos:

```sh
docker compose --env-file projects-service/.env -p prgate-projects -f projects-service/compose.yaml stop projects postgres
```
