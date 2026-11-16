# Microservicios individuales con Docker

Ejecutar desde PR-Gate-Backend con Docker Desktop encendido. Cada comando
levanta solo el servicio indicado y su PostgreSQL, cuando corresponde.
Los contenedores que ya estaban encendidos siguen ejecutándose.
Si un servicio aún no tiene `.env`, crear uno a partir de su `.env.example` y
completarlo personalmente. No sobrescribir los archivos `.env` existentes.

| Servicio | Puerto externo | Comprobación |
|---|---|---|
| Users | 8080 | /actuator/health |
| IA | 8081 | /swagger-ui/index.html |
| CI/CD | 8082 | /actuator/health |
| Projects | 8083 | /actuator/health |
| Métricas | 8084 | Logs: Started MetricsServiceApplication |
| Keycloak | 8090 | /realms/prgate/.well-known/openid-configuration |

## Users y Projects

Conservan sus Compose y bases propios preparados anteriormente:

```sh
docker compose --env-file users-service/.env -f compose.yml -f compose.users-keycloak.yml up -d --build users-service
docker compose --env-file projects-service/.env -p prgate-projects -f projects-service/compose.yaml up -d --build projects
```

Users necesita también Keycloak para el login. Consultar users-service/DOCKER.md.
Projects sigue en perfil local con X-User-Id; consultar projects-service/DOCKER.md.

## CI/CD

En cicd-service/.env, configurar personalmente:

```properties
DB_PASSWORD=tu-clave-de-cicd
SERVER_PORT=8082
DOCKER_SONAR_URL=http://host.docker.internal:9000
```

Mantener los tokens y secretos de GitHub/SonarQube existentes. La imagen incluye
SonarScanner CLI; no usar dentro del contenedor la ruta Windows del scanner.

```sh
docker compose --env-file cicd-service/.env -p prgate-cicd -f cicd-service/compose.yaml up -d --build cicd
```

PostgreSQL crea prgate_cicd, usuario prgate_cicd, con volumen exclusivo.
Flyway crea el esquema cicd. SonarQube debe estar accesible para analizar archivos;
el servicio puede arrancar sin ejecutar un análisis.
Si el dashboardUrl de la prueba contiene host.docker.internal, abrirlo desde
la PC cambiando ese hostname por localhost.

## Métricas

En metrics-service/.env usar SERVER_PORT=8084 para evitar el puerto de CI/CD.

```sh
docker compose --env-file metrics-service/.env -p prgate-metrics -f metrics-service/compose.yaml up -d --build metrics
```

Métricas todavía no tiene endpoints ni un datasource implementado. Dockerizar
esta base permite arrancarla, pero no completa su funcionalidad. No se crea
una base que la aplicación aún no utiliza. Por ahora consultar los logs para
confirmar el arranque; no dispone de /actuator/health.

## IA

En ai-service/.env configurar DB_PASSWORD y SERVER_PORT=8081. Mantener
AI_PROVIDER, la clave y modelo del proveedor seleccionado e INTERNAL_SERVICE_TOKEN.
No es necesario configurar ambos proveedores si solo se utiliza uno.

```sh
docker compose --env-file ai-service/.env -p prgate-ai -f ai-service/compose.yaml up -d --build ai
```

Este Compose usa PostgreSQL propio (prgate_ai), en lugar del H2 del Compose
antiguo de IA. No borra ni migra ai-service-data ni bases existentes. SonarQube
se alcanza por DOCKER_SONAR_URL (por defecto, host.docker.internal:9000).
Abrir Swagger valida el arranque; generar contenido requiere claves reales y
acceso al proveedor. No levantar IA simultáneamente con su definición del Compose
raíz, pues compartirían el puerto 8081.

## Uso diario

- Primera construcción o cambio de Java: agregar --build.
- Sin cambios de código: el mismo comando sin --build.
- Cambio de .env: usar up -d --force-recreate para cargar las variables nuevas.
- Estado: sustituir up -d --build SERVICIO por ps.
- Logs: sustituirlo por logs -f --tail 100 SERVICIO.
- Parar sin borrar datos: sustituirlo por stop SERVICIO postgres.
  En Métricas usar solo stop metrics. En Users la base se llama users-db.
- No usar down -v ni borrar volúmenes si se quieren conservar las bases.

Los Compose de CI/CD e IA fijan conexiones internas PostgreSQL para evitar
heredar localhost o la base compartida de los .env anteriores. Las contraseñas
solo inicializan volúmenes nuevos; cambiarlas en el archivo no cambia el rol
PostgreSQL de un volumen ya inicializado. Ningún Compose copia los datos antiguos.

Levantar todos los contenedores no completa la integración entre servicios:
Projects/Users/Keycloak, el contrato de repositorios, los webhooks públicos y
RabbitMQ siguen requiriendo sus pasos de integración. No se han creado aún
contenedores para servicios que todavía no existen en la rama.
