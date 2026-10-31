# Contrato propuesto Projects → CI/CD (v1)

Projects es propietario del proyecto académico, los grupos, las autorizaciones
y la asociación de repositorios. CI/CD recibe referencias y administra únicamente
la configuración de evaluación, los recursos técnicos en SonarQube y los pipelines.
Registrar un recurso en SonarQube no crea un proyecto académico.

## Datos de entrada

El futuro adaptador de integración invocará `ConfigureRepositoryEvaluationUseCase`
con `ConfigureRepositoryEvaluationCommand`:

```json
{
  "projectId": "5db3edfe-8367-4f21-93a3-5cbe6c57a6fb",
  "githubRepositoryId": 123456,
  "githubOwner": "grupo-01",
  "githubName": "backend",
  "analysisBranch": "develop"
}
```

- `projectId`: UUID existente emitido por Projects; se guarda como referencia externa,
  sin clave foránea hacia la base de datos de otro microservicio.
- `githubRepositoryId`: ID numérico de GitHub, no UUID de usuario ni URL.
- `githubOwner` y `githubName`: metadatos observados por Projects.
- `analysisBranch`: rama elegida para evaluación. Community Build mantiene una
  rama principal de análisis por proyecto técnico; no debe asumirse siempre `main`.

Este contrato es una propuesta para coordinar con Projects; no se ha comprobado
contra su implementación porque está en otra rama. Projects debe validar permisos,
existencia del proyecto y asociación del repositorio antes de emitir estos datos.
La futura instalación de GitHub App debe verificar el acceso real al repositorio.

## Procesamiento

1. Registrar la configuración técnica en PostgreSQL y confirmar esa transacción.
2. Crear o verificar el recurso remoto con su clave estable en SonarQube.
3. Guardar `READY` si existe o `FAILED` si falla. `READY` no confirma que haya
   corrido el scanner ni que el Quality Gate esté aprobado.

Repetir los mismos datos conserva el identificador y la clave Sonar. Un error de
aprovisionamiento se propaga para permitir reintentar. Cambiar el proyecto o la
rama de un registro existente se rechaza: requiere un flujo de actualización
explícito, todavía pendiente. Los cambios de nombre y la desvinculación tampoco
están implementados; no deben tratarse como nuevos registros.

## Transporte pendiente

RabbitMQ, exchange, routing key y consumidor se definirán al integrar los servicios.
El mensaje deberá incluir identificador de evento, versión y fecha de ocurrencia
en su envoltura. El adaptador deberá autenticar al productor, validar versión y
manejar reintentos y mensajes fallidos. No existe un endpoint público para crear
proyectos o registrar repositorios arbitrarios en CI/CD.

Los webhooks GitHub buscan una configuración por ID de repositorio. Si no existe,
responden `IGNORED_UNREGISTERED_REPOSITORY` sin guardar ni reclamar la entrega.
Esto permite redeliverar el evento después de recibir el registro desde Projects.
Cada ejecución conserva una referencia a la configuración y al UUID académico.
Se conservan pipelines de todas las ramas admitidas, aunque Sonar Community Build
solo analice la rama configurada. El estado de Sonar no bloquea el seguimiento CI.
La comprobación de instalación/organización de la GitHub App sigue pendiente;
la firma compartida del webhook no sustituye esa comprobación.

La migración V3 enlaza ejecuciones históricas cuando coincide el ID GitHub y deja
sin asociación las que no tienen registro conocido. Las consultas exponen
`academicProjectId` y `evaluationRepositoryId`, con valores nulos en ese caso legado.
