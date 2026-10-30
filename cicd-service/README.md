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
`infrastructure` implementará PostgreSQL y adaptadores de GitHub/SonarQube;
`interfaces` expondrá la API y recibirá webhooks.

## Estado

El dominio inicial modela cada ejecución del workflow dedicado de evaluación,
su ciclo de vida y el resultado asociado de SonarQube. Los demás workflows del
repositorio quedan fuera del seguimiento. Todavía no se han implementado
persistencia, webhooks ni integraciones externas.
