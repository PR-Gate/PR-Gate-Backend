# Recuperación local: Keycloak y vínculo GitHub

La imagen antigua de users-service no ofrece las rutas /api/me ni las rutas
GitHub. El frontend nuevo necesita reconstruirla con este código.

## Base existente, sin nuevas bases

El usuario confirmó en pgAdmin, en la base PR-Gate:

- users.users y public.users existen; no trasladar ni borrar ninguna.
- users.flyway_schema_history contiene V1 y V2 exitosas.
- Las columnas de users.users son compatibles con el modelo.

V3 conserva los UUID y usuarios existentes en users.users, permite que cognito_sub
sea nulo para identidades nuevas de otro proveedor y añade external_identities.
No copia ni elimina public.users. Los registros de esa tabla antigua no se
fusionan automáticamente: cualquier migración de datos históricos requiere
una revisión aparte. No se vinculan cuentas por correo.

## Ejecución por el usuario

1. Hacer un respaldo de PR-Gate en pgAdmin, formato Custom.
2. Mantener DB_URL, DB_USERNAME y DB_PASSWORD de users-service/.env apuntando
   a PR-Gate. No usar prgate_ai ni la base de Keycloak.
3. En ese mismo archivo privado, conservar las claves de la GitHub App y fijar:

       GITHUB_ENABLED=true
       GITHUB_REDIRECT_URI=http://localhost:8080/api/integrations/github/callback

   La callback URL registrada en la GitHub App debe coincidir exactamente.
   El Client ID de la App no es su App ID. No guardar secretos en el frontend.
4. Desde PR-Gate-Backend, ejecutar:

       docker compose up -d --build --no-deps users-service
       docker compose logs --tail=100 users-service

   Este arranque aplica V3 mediante Flyway en PR-Gate. El asistente no lo ejecuta.
   No ejecutar V3 manualmente además de Flyway, no usar baseline-on-migrate,
   clean ni borrar volúmenes. Si Flyway informa un error, detenerse y revisar
   el mensaje concreto, sin alterar su historial ni checksums.
5. Comprobar http://localhost:8080/actuator/health y Swagger.
   El documento http://localhost:8080/v3/api-docs debe incluir:
   POST /api/me/session, GET /api/me y las rutas de GitHub.
6. En PR-Gate-Frontend, reiniciar npm run dev y abrir
   http://localhost:5173/login. Mantener localhost, sin alternar con 127.0.0.1.
7. Crear cuenta desde /register, eligiendo Docente o Estudiante, y continuar
   con el formulario de Keycloak. POST /api/me/session consulta el perfil:
   no lo crea implícitamente. POST /api/me/registration autentica y valida
   la elección para crear el perfil inicial. Nunca permite ADMIN ni cambia
   el rol de una cuenta existente. Las identidades Keycloak ya creadas sin
   perfil académico pueden completar esa elección desde /account.
8. En /account, comprobar “Servicio de usuarios conectado”, pulsar
   “Vincular cuenta de GitHub” y autorizar en la otra pestaña. El callback
   actual devuelve JSON con login y linkedAt. Volver a PR Gate y pulsar
   “Actualizar estado”. Solo la respuesta GET del backend confirma el vínculo.

## Autenticación

compose.yml conserva local,keycloak en el arranque normal (puede configurarse
mediante USERS_AUTH_PROFILES). El overlay anterior sigue siendo compatible.
local permite cookies HTTP para localhost,
pero el resolvedor de identidad de GitHub sigue exigiendo Keycloak.
X-User-Id no sustituye el JWT en ese modo. Se valida RS256, issuer público,
expiración, audiencia prgate-api, azp prgate-frontend y tipo Bearer.
El JWKS se consulta desde Docker mediante host.docker.internal.

La relación externa usa issuer + subject. Un usuario nuevo recibe un UUID
académico independiente. Un perfil INACTIVE no puede abrir sesión ni vincular.
Las rutas antiguas /users quedan bloqueadas en el modo Keycloak porque no
deben permitir asignar identidades o roles sin autenticación; /internal
conserva su autenticación independiente.

El enlace solo verifica la identidad GitHub. No instala la App ni concede
acceso a repositorios, pipelines o métricas. Sus secretos permanecen en el backend.

## Validación sin bases de datos

Dockerfile.verify ejecuta únicamente las pruebas KeycloakIdentityServiceTest,
KeycloakJwtValidationTest, KeycloakConfigurationTest y AccountRegistrationContractTest, con repositorios
mockeados y un servidor JWKS local de prueba. No ejecuta las pruebas H2,
no importa .env ni conecta a PostgreSQL. Después compila el jar.

    docker build -f users-service/Dockerfile.verify -t prgate-users-keycloak-check users-service

Las pruebas completas de integración con base quedan fuera de esta validación.
El flujo real requiere la ejecución y autorización del usuario descritas arriba.
