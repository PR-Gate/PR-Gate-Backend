# Users con Docker

Desde la raíz del backend, con Docker Desktop y el Keycloak existente encendidos:

```sh
docker compose --env-file users-service/.env -f compose.yml -f compose.users-keycloak.yml up -d --build users-service
```

Arranca únicamente Users y su PostgreSQL. No arranca IA ni otros microservicios.
Keycloak continúa en su stack `compose.auth.yml`, accesible desde la PC en 8090.
El frontend se ejecuta por separado como hasta ahora.

El archivo `users-service/.env` conserva las credenciales de GitHub y el token
interno. Configurar `DB_PASSWORD` para PostgreSQL; por compatibilidad se acepta
`SPRING_DATASOURCE_PASSWORD` si `DB_PASSWORD` no está definido. El overlay fuerza
la conexión a `users-db:5432/prgate_users`, con usuario `prgate_users`, aunque el
archivo conserve la conexión antigua. Las claves GitHub existentes no cambian.

Esta es una base nueva: Flyway crea sus tablas. No contiene automáticamente
los usuarios ni vínculos GitHub de la base anterior. La migración de esos datos
se hará aparte; mantener también la clave original de cifrado de tokens si se
trasladan vínculos GitHub. No se borra la base anterior ni el volumen de Keycloak.
La contraseña configura volúmenes nuevos; cambiarla después exige actualizar
también el rol PostgreSQL existente.

Comprobar estado y logs:

```sh
docker compose --env-file users-service/.env -f compose.yml -f compose.users-keycloak.yml ps
docker compose --env-file users-service/.env -f compose.yml -f compose.users-keycloak.yml logs --tail=100 users-service
```

Salud: http://localhost:8080/actuator/health (con SERVER_PORT sin modificar).
Si el puerto 8080 ya está ocupado por Users con Maven, detener ese proceso primero.

Para siguientes arranques, omitir `--build` si no cambió el código.
Para parar conservando datos:

```sh
docker compose --env-file users-service/.env -f compose.yml -f compose.users-keycloak.yml stop users-service users-db
```
