# Primer piloto con un repositorio GitHub real

Destino comprobado mediante la API de GitHub: PR-Gate/PR-Gate-Backend,
ID numérico 1432721468. Rama de análisis del piloto: feat/ci-cd-service.

Este piloto prepara únicamente el registro técnico y el proyecto SonarQube.
No instala la GitHub App, no publica workflows ni simula ejecuciones.
El UUID académico del ejemplo es una referencia sintética para esta prueba,
no un proyecto emitido por Projects. Usar solo una base local de pruebas.
No usar este UUID al registrar repositorios académicos reales.

Con CI/CD, su PostgreSQL y SonarQube ya disponibles, desde la raíz del backend:

```sh
docker compose --env-file cicd-service/.env -p prgate-cicd -f cicd-service/compose.yaml build cicd
docker compose --env-file cicd-service/.env -p prgate-cicd -f cicd-service/compose.yaml run --rm --no-deps --entrypoint java cicd -jar /app/app.jar --spring.profiles.active=repository-pilot --pilot.project-id=c04e8799-8ff9-450a-8c7b-110eb8f784ea --pilot.repository-id=1432721468 --pilot.owner=PR-Gate --pilot.repository=PR-Gate-Backend --pilot.branch=feat/ci-cd-service
```

El proceso termina tras preparar el registro. Debe imprimir
`REPOSITORY_PILOT_READY` y `SONAR_PROJECT_KEY`. Un fallo debe terminar con error;
no basta con que Docker diga Started. Repetir los mismos datos conserva la clave.
Cambiar la rama o el proyecto académico requiere un flujo explícito de actualización.

Conservar la clave Sonar: se usará en PRGATE_SONAR_PROJECT_KEY del workflow.
El token del backend debe tener los permisos necesarios para crear el proyecto.
El workflow utilizará un token de análisis propio para ese proyecto.

Después hay que instalar un workflow adaptado a este monorepo (Maven debe
ejecutarse dentro de cicd-service), elegir un runner que alcance SonarQube y
configurar la entrega de workflow_run al webhook firmado de CI/CD. La plantilla
existente aún exige un runner self-hosted prgate-analysis; no se ejecuta sin él.
GitHub no puede entregar webhooks a localhost:8082. La conectividad del piloto
se preparará antes del primer push. Para considerar la prueba completa hay que
verificar un run real y su análisis Sonar asociados al mismo commit e intento.
