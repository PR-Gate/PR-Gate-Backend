# PRGate CI/CD Service

Microservicio para automatizar el seguimiento de pipelines de GitHub Actions,
registrar resultados y evidencias técnicas y exponer el resultado de los Quality
Gates de SonarQube. GitHub Actions ejecutará compilaciones y pruebas; SonarQube
analizará el código. El servicio conservará la trazabilidad de cada ejecución.

La primera versión se enfoca en GitHub Actions y SonarQube. Los pipelines se
iniciarán con eventos de push o pull request. La integración entre microservicios
mediante RabbitMQ se implementará después.

## Arquitectura

DDD ligero con arquitectura hexagonal. `domain` contiene el modelo de las
ejecuciones y resultados; `application` define casos de uso y puertos;
`infrastructure` implementa PostgreSQL y adaptadores de GitHub/SonarQube;
`interfaces` expondrá la API y recibirá webhooks.

## Webhook de GitHub

El endpoint receptor es `POST /api/v1/webhooks/github`. El filtro valida
`X-Hub-Signature-256` sobre el cuerpo original usando `GITHUB_WEBHOOK_SECRET`
antes de que la petición llegue al controlador. En la configuración del webhook
de GitHub se debe habilitar el evento `workflow_run` y usar el mismo secreto.
El servicio registra únicamente eventos `push` y `pull_request` del workflow
indicado por `GITHUB_EVALUATION_WORKFLOW_NAME`.

## Estado

El dominio inicial modela cada ejecución del workflow dedicado de evaluación,
su ciclo de vida y el resultado asociado de SonarQube. Cada reintento de GitHub
Actions queda como intento independiente para conservar el historial de fallos.
Los demás workflows del repositorio quedan fuera del seguimiento. La capa de
aplicación procesa eventos normalizados e implementa idempotencia por entrega.
PostgreSQL/Flyway y el endpoint del webhook con validación de firma ya están
conectados. La integración para obtener resultados de SonarQube todavía está
pendiente.

## Consultas de ejecuciones

GET /api/v1/pipeline-runs/{id}: detalle por UUID local.
GET /api/v1/pipeline-runs?repositoryId=123&branch=develop&page=0&size=20: listado por repositorio y rama opcional. Cada intento aparece como registro independiente; tamaño máximo 100.

Las tablas y el historial de Flyway usan el esquema cicd dentro de pr-gate. Las consultas todavía requieren incorporar autorización antes de exponerlas públicamente.

## Evaluación centralizada

PRGate administrará una instancia de SonarQube Community Build. Los alumnos autorizarán la GitHub App desde la web; no necesitan cuentas de SonarQube Cloud. Community Build analizará una rama configurada por repositorio. El agregado EvaluationRepository vincula el UUID del proyecto académico con GitHub y una clave Sonar estable. Modela los estados PENDING, READY y FAILED; todavía falta implementar su registro persistente y el aprovisionamiento remoto. READY significa configuración completada, no Quality Gate aprobado.
